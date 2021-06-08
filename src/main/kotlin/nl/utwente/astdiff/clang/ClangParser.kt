package nl.utwente.astdiff.clang

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import nl.utwente.astdiff.ast.*
import org.apache.commons.text.StringEscapeUtils
import java.lang.IllegalStateException
import java.math.BigDecimal

class ClangParser(private val offset: Int) {
    companion object {
        private val OMITTED_NODES = listOf("ImplicitValueInitExpr", "CXXDefaultArgExpr", "DefinitionData", "TemplateArgument", "CXXDefaultInitExpr")
        private val OMITTED_INTERMEDIATE_NODES = listOf("ConstantExpr")
        private val OMITTED_TERMINAL_NODES = listOf("CXXConstructExpr", "ParenListExpr", "PragmaCommentDecl", "UnusedAttr", "VisibilityAttr")

        fun parseClang(input: String): TreeNode {
            val mapper = ObjectMapper().registerModule(KotlinModule()).enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            val root = mapper.readValue(input, ClangNode::class.java)
            val filteredInner = mutableListOf<ClangNode>()
            val iterator = root.inner.listIterator(root.inner.size) // Start iterator after last element
            while (iterator.hasPrevious()) {
                val node = iterator.previous()
                if (node.range?.begin?.includedFrom != null) {
                    // Last node to include
                    break
                }
                filteredInner.add(node)
            }
            val filteredRoot = root.copy(inner = filteredInner.reversed())
            val parser = ClangParser(filteredRoot.firstKnownLineNum() ?: 0)
            return parser.jsonToTree(filteredRoot)
        }
    }

    private fun jsonToTree(node: ClangNode): TreeNode {
        val tool = ClangToolTree(node, offset)
        if (node.isImplicit || node.kind in OMITTED_NODES || (node.inner.isEmpty() && node.kind in OMITTED_TERMINAL_NODES)) {
            return OmittedNode(tool)
        }
        if (node.kind in OMITTED_INTERMEDIATE_NODES) {
            for (child in node.inner) {
                val converted = jsonToTree(child)
                if (converted !is OmittedNode) {
                    return converted
                }
            }
            return OmittedNode(tool)
        }

        return when (node.kind) {
            "TranslationUnitDecl" -> TranslationUnit(node.inner.filter { !it.isImplicit }.map { jsonToTree(it) }, tool)

            // Classes, namespaces and likes
            "ClassTemplateDecl", "FunctionTemplateDecl" -> {
                val converted = node.inner.map { jsonToTree(it) }
                val parameters = converted.takeWhile { it is TemplateParameter }
                // For templated entity, take node after the parameters: nodes after that one contain instantiations
                TemplatedDecl(parameters, converted[parameters.size], tool)
            }
            "NonTypeTemplateParmDecl" -> TemplateParameter(node.extractType(offset), node.name, tool)
            "TemplateTypeParmDecl" -> TemplateParameter(
                TypeInfo(node.recordType.toString().toLowerCase(), emptyList(), tool),
                node.name,
                tool
            )
            "CXXRecordDecl", "ClassTemplateSpecializationDecl" -> {
                if (node.recordType == null) {
                    return OmittedNode(tool)
                }
                val bases = node.bases.map { base ->
                    val access = if (base.writtenAccess == "none") null else base.access
                    InheritanceBaseRef(base.type.getValue("qualType")
                        .takeWhile { it != '<' }.skipQualifier(), // Don't include generic arguments
                        access, isVirtual = base.isVirtual)
                }
                RecordDecl(node.recordType, node.name?.skipQualifier(), bases, node.inner.map { jsonToTree(it) }, tool)
            }
            "EnumDecl" -> EnumDecl(node.name, node.fixedUnderlyingType["qualType"]?.skipQualifier(),
                node.inner.map { jsonToTree(it) }, tool)
            "EnumConstantDecl" -> EnumConstant(node.name!!, node.inner.firstOrNull()?.let { jsonToTree(it) }, tool)
            "FriendDecl" -> {
                val inner = if (node.inner.isNotEmpty()) jsonToTree(node.inner.first()) else {
                    // References class/struct/etc: does not have inner node
                    val tag = node.type.getValue("qualType").split(" ").first()
                    val name = node.type.getValue("desugaredQualType").skipQualifier()
                    RecordDecl(RecordType.valueOf(tag.toUpperCase()), name, emptyList(), emptyList(), tool)
                }
                FriendDecl(inner, tool)
            }
            "NamespaceDecl" -> NamespaceDecl(node.name, node.inner.map { jsonToTree(it) }, tool)
            "UsingDecl" -> UsingDecl(node.name!!.skipQualifier(), false, tool)
            "UsingDirectiveDecl" -> UsingDecl(node.nominatedNamespace.getValue("name").skipQualifier(), true, tool)

            // Declarations
            "AccessSpecDecl" -> SpecifiedAccess(node.access!!, node.inner.map { jsonToTree(it) }, tool)
            "EmptyDecl" -> EmptyStmt(tool)
            "CXXMethodDecl", "FunctionDecl", "CXXConstructorDecl", "CXXDestructorDecl" -> {
                val props = mutableSetOf<FunctionProps>()
                // Filter out "operator" prefix, stuff between template brackets and possible extra whitespace
                val nameMatch = Regex("([A-Za-z]*\\s+)?([^\\s<]+)(<.*>)?").find(node.name!!)
                if (node.variadic) {
                    props.add(FunctionProps.VARIADIC)
                }
                when (node.kind) {
                    "CXXConstructorDecl" -> props.add(FunctionProps.CONSTRUCTOR)
                    "CXXDestructorDecl" -> props.add(FunctionProps.DESTRUCTOR)
                }
                FuncDecl(
                    node.extractType(offset),
                    nameMatch!!.groupValues[2].removePrefix("operator"),
                    node.kindChildren("ParmVarDecl").map { jsonToTree(it) }, props,
                    node.kindChildOrNull("CompoundStmt")?.let { jsonToTree(it) }, tool,
                    memberInits = node.kindChildren("CXXCtorInitializer").map { jsonToTree(it) })
            }
            "ParmVarDecl" -> FuncDeclParam(node.extractType(offset), node.name, tool)
            "TypedefDecl" -> OmittedNode(tool)
            "CXXCtorInitializer" -> {
                val name = if (node.baseInit.isEmpty()) node.anyInit.getValue("name").toString() else
                    node.baseInit.getValue("qualType").skipQualifier()
                val inner = jsonToTree(node.inner.first())
                // If this node is an initializer for a field with default constructor, it's likely not explicitly in the source code
                // Omit such nodes from tree, despite Clang not marking them as implicit
                if (inner is OmittedNode) OmittedNode(tool) else CtorMemberInitializer(name, inner, tool)
            }
            "VarDecl", "FieldDecl" -> {
                val typeInfo = node.extractType(offset)
                if (node.type.getValue("qualType").endsWith("]")) {
                    // Array declaration
                    val init = node.inner.firstOrNull()?.let { jsonToTree(it) }
                    ArrayDecl(typeInfo, node.name!!, init, tool)
                } else {
                    // Variable declaration
                    VarDecl(typeInfo, node.name,
                            node.inner.firstOrNull()?.let { jsonToTree(it) }, tool)
                }
            }

            // Switch-case
            "SwitchStmt" -> {
                var body = jsonToTree(node.inner[1])
                // TemporaryStmts need to be removed, still
                if (body is BlockStmt) {
                    // "Post-process" the TemporaryCaseStmts into proper/split CaseStmts and body-Stmt
                    fun flattenCase(stmt: TreeNode): List<TreeNode> {
                        return when (stmt) {
                            // 3 types of stmts possible: case, default or a label preceding case/default
                            is LabelStmt -> {
                                val inner = flattenCase(stmt.inner)
                                listOf(LabelStmt(stmt.label, inner.first(), stmt.toolTree)) + inner.drop(1)
                            }
                            is TemporaryCaseStmt -> listOfNotNull(
                                    listOf(CaseStmt(stmt.expr, stmt.toolTree)),
                                    stmt.stmt?.let { flattenCase(it) }).flatten()
                            is TemporaryDefaultStmt -> listOfNotNull(DefaultStmt(stmt.toolTree), stmt.stmt)
                            else -> listOf(stmt)
                        }
                    }
                    body = BlockStmt(body.stmts.map { flattenCase(it) }.flatten(), tool)
                }
                SwitchStmt(jsonToTree(node.inner[0]), body, tool)
            }
            "CaseStmt" -> {
                val body = if (node.inner.size > 1) node.inner[1] else null
                TemporaryCaseStmt(jsonToTree(node.inner[0]), body?.let { jsonToTree(it) }, tool)
            }
            "DefaultStmt" -> TemporaryDefaultStmt(node.inner.firstOrNull()?.let { jsonToTree(it) }, tool)

            // Other statements
            "BreakStmt" -> BreakStmt(tool)
            "CompoundStmt" -> BlockStmt(node.inner.map { jsonToTree(it) }, tool)
            "ContinueStmt" -> ContinueStmt(tool)
            "DeclStmt" -> DeclStmt(node.inner.map { jsonToTree(it) }, tool)
            "DoStmt" -> DoWhileStmt(jsonToTree(node.inner[0]), jsonToTree(node.inner[1]), tool)
            "ForStmt" -> {
                ForStmt(jsonToTree(node.inner[0]), jsonToTree(node.inner[2]), jsonToTree(node.inner[3]), jsonToTree(node.inner[4]), tool)
            }
            "GotoStmt" -> GotoStmt(tool)
            "CXXForRangeStmt" -> ForRangeStmt(jsonToTree(node.depthFirstChild("DeclRefExpr", "MemberExpr")!!),
                jsonToTree(node.inner.last()), tool)
            "IfStmt" -> {
                /* Child structure
                - Condition (possibly a declaration)
                - Expression used for condition (if condition is a statement)
                - If-block
                - Else-block (if present)
                 */
                val elseNode = if (node.inner.size > 2) jsonToTree(node.inner.last()) else null
                // If-part is last node if else is not there, but second-last if else-stmt is there.
                val ifBody = if (elseNode == null) node.inner.last() else node.inner[node.inner.size - 2]
                IfStmt(IfPart(jsonToTree(node.inner[0]), jsonToTree(ifBody), tool), elseNode, tool)
            }
            "ReturnStmt" -> ReturnStmt(node.inner.firstOrNull()?.let { jsonToTree(it) }, tool)
            "NullStmt" -> EmptyStmt(tool)
            "WhileStmt" -> WhileStmt(jsonToTree(node.inner.first()), jsonToTree(node.inner.last()), tool)
            ClangNode.NULL_KIND -> EmptyNode(tool)

            // Expressions
            "ArraySubscriptExpr" -> FlattenedNode(listOf(jsonToTree(node.inner[0]),
                ArrayAccessExpr(jsonToTree(node.inner[1]), tool)), tool)
            "CallExpr", "CXXMemberCallExpr" -> {
                // First part of inner is referenced function, later parts are call args
                val functionJson = node.inner.first()
                val argsJson = node.inner.drop(1)
                CallExpr(jsonToTree(functionJson), argsJson.map { jsonToTree(it) }, tool)
            }
            "ConditionalOperator" -> TernaryExpr(jsonToTree(node.inner[0]), jsonToTree(node.inner[1]),
                jsonToTree(node.inner[2]), tool)
            "CStyleCastExpr" -> {
                // Recognized as collection of parenthesis, names and operators in srcML
                FlattenedNode(listOf(Operator("(", tool),
                    splitTypeName(node.type.getValue("qualType").skipQualifier(), tool),
                    Operator(")", tool), jsonToTree(node.inner.first())), tool)
            }
            "CXXCatchStmt" -> CatchStmt(jsonToTree(node.inner.first()), jsonToTree(node.inner.last()), tool)
            "CXXConstructExpr" -> {
                val type = node.type.getValue("qualType").skipQualifier()
                // Skip copy-constructors (i.e. only one argument of same type) and elidable constructors
                if (node.elidable || node.extractType(offset).unqualifiedTypeName == node.inner.first().extractType(offset).unqualifiedTypeName) {
                    return jsonToTree(node.inner.first())
                }
                var arguments = node.inner.map { jsonToTree(it) }
                // If init-list was used, "unpack" arguments
                if (arguments.size == 1) {
                    val argument = arguments.first()
                    if (argument is InitListExpr) {
                        arguments = argument.exprs
                    }
                }
                CallExpr(RefExpr(type, tool), arguments, tool)
            }
            "CXXFunctionalCastExpr", "CXXTemporaryObjectExpr" -> {
                if (node.castKind in listOf("ConstructorConversion")) {
                    // "Skip" node
                    jsonToTree(node.inner.first())
                } else {
                    // Act as if it's call with type as function name
                    val typeInfo = node.extractType(offset)
                    CallExpr(RefExpr(typeInfo.unqualifiedTypeName, tool), node.inner.map { jsonToTree(it) }, tool)
                }
            }
            "CXXNewExpr" -> FlattenedNode(listOf(Operator("new", tool), jsonToTree(node.inner.first())), tool)
            "CXXDeleteExpr" ->  FlattenedNode(listOf(Operator("delete", tool), jsonToTree(node.inner.first())), tool)
            "CXXThisExpr" -> RefExpr("this", tool)
            "CXXTryStmt" -> TryStmt(jsonToTree(node.inner.first()), node.inner.drop(1).map { jsonToTree(it) }, tool)
            "CXXTypeidExpr" -> {
                val inner = if (node.inner.isEmpty()) {
                    // Type inside
                    RefExpr(node.typeArg.getValue("qualType").skipQualifier(), tool)
                } else {
                    jsonToTree(node.inner.first())
                }
                TypeIdExpr(inner, tool)
            }
            "DeclRefExpr" -> {
                val name = node.referencedDecl!!.getValue("name") as String
                 if (name.contains("operator")) {
                    Operator(name.removePrefix("operator"), tool)
                } else {
                    RefExpr(name, tool)
                }
            }
            "BinaryOperator", "CompoundAssignOperator" ->
                FlattenedNode(listOf(jsonToTree(node.inner[0]), jsonToTree(node.inner[1]), Operator(node.opcode!!, tool)), tool)
            "UnaryOperator" -> FlattenedNode(listOf(jsonToTree(node.inner[0]), Operator(node.opcode!!, tool)), tool)
            "IntegerLiteral" -> LiteralExpr(node.value!!, tool)
            "InitListExpr" -> InitListExpr(node.inner.map { jsonToTree(it) }, tool)
            "CharacterLiteral" -> LiteralExpr("'${node.value!!.toInt().toChar()}'", tool)
            "CXXBoolLiteralExpr" -> LiteralExpr(node.value!!, tool)
            "CXXNullPtrLiteralExpr", "GNUNullExpr" -> LiteralExpr("nullptr", tool)
            "CXXOperatorCallExpr" -> FlattenedNode(node.inner.map { jsonToTree(it) }, tool)
            "CXXStaticCastExpr", "CXXConstCastExpr", "CXXDynamicCastExpr", "CXXReinterpretCastExpr" -> {
                val kind = when (node.kind) {
                    "CXXStaticCastExpr" -> CastKind.STATIC
                    "CXXConstCastExpr" -> CastKind.CONST
                    "CXXDynamicCastExpr" -> CastKind.DYNAMIC
                    "CXXReinterpretCastExpr" -> CastKind.REINTERPRET
                    else -> throw IllegalStateException("Unsupported cast kind")
                }
                CastExpr(kind, jsonToTree(node.inner.first()), tool)
            }
            "CXXThrowExpr" -> ThrowExpr(node.inner.firstOrNull()?.let { jsonToTree(it) }, tool)
            "FloatingLiteral" -> LiteralExpr(floatNumberFormat.format(BigDecimal(node.value!!)), tool)
            "GCCAsmStmt" -> AsmStmt(tool)
            "StringLiteral" -> LiteralExpr(StringEscapeUtils.unescapeJava(node.value!!), tool)
            "LabelStmt" -> LabelStmt(node.name!!, jsonToTree(node.inner.first()), tool)
            "LambdaExpr" -> {
                val method = node.inner.first { it.kind == "CXXRecordDecl" }.inner.first { it.kind == "CXXMethodDecl" }
                val args = method.inner.dropLast(1).map { jsonToTree(it) }
                val closure = node.inner.drop(1).dropLast(1).map { jsonToTree(it) } // Everything but block and implicit class info
                val body = jsonToTree(node.inner.last())
                LambdaExpr(closure, args, body, tool)
            }
            "MemberExpr", "CXXDependentScopeMemberExpr" -> {
                // LHS is the target of the member expr (in inner-field), RHS is the name (always a string)
                val symbol = if (node.isArrow!!) "->" else "."
                var target = node.inner.firstOrNull()?.let { jsonToTree(it) } // Target null if static reference via ::-notation
                // Remove "->" if it's first child, which means that -> was overloaded operator (and would cause duplicate)
                if (target is FlattenedNode) {
                    val firstChild = target.children.first()
                    if (firstChild is Operator && firstChild.symbol == "->") {
                        target = FlattenedNode(target.children.drop(1), target.toolTree)
                    }
                }
                val refExpr = RefExpr(node.name!!, tool)
                if (target == null || target is OmittedNode) {
                    // Target was implicit or non-existent, so no operator
                    refExpr
                } else {
                    FlattenedNode(listOf(target, Operator(symbol, tool), refExpr), tool)
                }
            }
            "ParenExpr" -> FlattenedNode(listOf(Operator("(", tool),
                jsonToTree(node.inner.first()), Operator(")", tool)), tool)
            "UnaryExprOrTypeTraitExpr" -> {
                if (node.name!! != "sizeof") {
                    println("(Clang) Detected unknown UnaryExprOrTypeTraitExpr with name '${node.name}'")
                    return EmptyStmt(tool)
                }
                if (node.inner.isEmpty()) {
                    SizeOfExpr(FlattenedNode.parenthesised(RefExpr(node.argType.getValue("qualType").skipQualifier(), tool), tool), tool)
                } else {
                    SizeOfExpr(jsonToTree(node.inner.first()), tool)
                }
            }

            // Skipped parts, only taking children
            "ImplicitCastExpr" -> {
                val child = node.inner.first()
                if (node.castKind !in listOf("UserDefinedConversion", "UserDefinedConversion")) {
                    return jsonToTree(child)
                }
                // ImplicitCast for which we assume/suspect direct child is reference to target of implicit (conversion) call.
                // Filter out this target and only present its argument to parent, which was element actually found in source code
                jsonToTree(when (child.kind) {
                    "CXXConstructExpr" -> child.inner.first()
                    "CXXMemberCallExpr" -> {
                        // Expect to find MemberExpr inside which refs the call target, and what we need is given as argument to that
                        val callTarget = child.inner.first()
                        if (callTarget.kind != "MemberExpr") {
                            println("(Clang) Skipping node with tag ${callTarget.kind} due to being hidden inside ImplicitCast")
                        }
                        callTarget.inner.first()
                    }
                    else -> child
                })
            }
            "ExprWithCleanups", "MaterializeTemporaryExpr", "ConstantExpr", "LinkageSpecDecl",
            "SubstNonTypeTemplateParmExpr", "CXXBindTemporaryExpr" -> jsonToTree(node.inner.first())

            "UnresolvedUsingValueDecl", "UnresolvedLookupExpr", "UnresolvedMemberExpr", "OpaqueValueExpr",
            "DependentScopeDeclRefExpr", "CXXUnresolvedConstructExpr"
                -> IncomparableNode(tool, "TAG:${node.kind}")
            else -> {

                println("Skipping ${node.kind} (Clang)")
                if (node.inner.isNotEmpty()) {
                    return jsonToTree(node.inner.first())
                }
                TODO("Unprocessed terminal node ${node.kind}")
            }
        }
    }

    private fun splitTypeName(name: String, tool: ToolTree): TreeNode {
        val matches = Regex("([A-Za-z0-9_]+|[*&])\\s*").findAll(name)
        val nodes = matches.map { match ->
            when (val value = match.groupValues[1]) { // Part without whitespace
                "*", "&" -> Operator(value, tool)
                else -> RefExpr(value, tool)
            }
        }.toList()
        return FlattenedNode(nodes, tool)
    }
}

/*
TODO list:
x- do-while loops
x- try/catch/exceptions
- assert-statements
x- friend declaration
x- labels/goto
x- Add marker for function being constructor/destructor or not
x- Ternary expressions
x- continue-stmt
x- CastExpr
- Lambda functions?
x- Testing on files with imports
 */