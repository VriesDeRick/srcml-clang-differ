package nl.utwente.astdiff.srcml

import nl.utwente.astdiff.ast.*
import nl.utwente.astdiff.clang.skipQualifier
import org.apache.commons.text.StringEscapeUtils
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.math.BigInteger

class SrcMLParser(private val offset: Int) {
    companion object {
        val SKIPPED_TAGS = listOf("comment", "cpp:empty", "cpp:pragma")

        fun parseSrcML(input: String): TreeNode {
            // Preserve first two lines (coming from headers) and everything included by bottom part
            val lines = input.lines()
            val intendedFileInput = lines.takeLastWhile { !Regex("<cpp:empty># \\d+ \\S+ 2.*").matches(it) }
            val header = lines.take(2)
            val xmlToProcess = if (intendedFileInput.size < lines.size) (header + intendedFileInput) else
                intendedFileInput
            val xmlStr = xmlToProcess.joinToString("\n")
            File("pp_srcml.xml").writeText(xmlStr)
            val xml = XmlDom.readXML(xmlStr.byteInputStream())
//        val xml = XmlDom.readXML(input.byteInputStream()) // Uncomment in case of testing full file instead
            val parser = SrcMLParser(1) // Result from XML parser already has preprocessed parts cut out
            return parser.elementToTreeNode(xml.documentElement)
        }
    }




    private fun elementToTreeNode(e: Element): TreeNode {
        val tool = SrcMLTree(e, offset)
        return when (e.tagName) {
            "unit" -> TranslationUnit(e.childTags().map { elementToTreeNode(it) }, tool)

            // Classes
            "class", "class_decl", "struct", "struct_decl", "union", "union_decl" -> {
                val type = RecordType.valueOf(e.tagName.takeWhile { it.isLetter() }.toUpperCase())
                val bases = e.getOrNull("super_list")?.let { node ->
                    node.childTags().map { child ->
                        val name = child["name"].textContent.skipQualifier()
                        var isVirtual = false
                        // Specifiers contains both access tags and possibly the virtual-tag
                        val specifiers = child.childTags().filter { it.tagName == "specifier" }.mapNotNull { specifier ->
                            val text = specifier.textContent.trim().toUpperCase()
                            if (text == "VIRTUAL") {
                                isVirtual = true
                                null
                            } else {
                                AccessSpecifier.valueOf(text)
                            }
                        }
                        InheritanceBaseRef(name, specifiers.firstOrNull(), isVirtual)
                    }
                } ?: emptyList()
                // Take out "default"-specified blocks in public/private/protected-tags since those are implicit in source code
                val children = e.getOrNull("block")?.childTags()?.map {
                    if (it.getAttribute("type") == "default") it.childTags() else listOf(it)
                }?.flatten() ?: emptyList()
                val record = RecordDecl(type, e.getOrNull("name")?.textContent?.skipQualifier(), bases, children.map { elementToTreeNode(it) }, tool)
                val template = e.getOrNull("template")
                if (template == null) {
                    record
                } else {
                    extractTemplate(template, record)
                }
            }
            "friend" -> FriendDecl(elementToTreeNode(e.childTags().first()), tool)
            "public", "private", "protected" -> {
                val specifier = AccessSpecifier.valueOf(e.tagName.toUpperCase())
                SpecifiedAccess(specifier, e.childTags().map { elementToTreeNode(it) }, tool)
            }
            "using" -> {
                val namespace = e.getOrNull("namespace")
                val name = if (namespace == null) e["name"].textContent.skipQualifier() else namespace["name"].textContent.skipQualifier()
                UsingDecl(name, namespace != null, tool)
            }
            "typedef" -> OmittedNode(tool)
            "extern" -> IncomparableNode(tool, "EXTERN")
            "macro" -> IncomparableNode(tool, "MACRO")
            "namespace" -> NamespaceDecl(e.getOrNull("name")?.textContent, e["block"].childTags().map { elementToTreeNode(it) }, tool)

            // Statements
            "asm" -> AsmStmt(tool)
            "block" -> {
                if (e.getAttribute("type") == "pseudo") {
                    elementToTreeNode(e["block_content"].firstChildTag()) // Only single statement
                } else {
                    BlockStmt(e["block_content"].childTags().map { elementToTreeNode(it) }, tool)
                }
            }
            "break" -> BreakStmt(tool)
            "case" -> CaseStmt(elementToTreeNode(e["expr"]), tool)
            "catch" -> {
                val declElem = e["parameter_list"]["parameter"]["decl"]
                val declNode = if (declElem.textContent.contains("...")) EmptyNode(SrcMLTree(declElem, offset)) else
                    elementToTreeNode(declElem)
                CatchStmt(declNode, elementToTreeNode(e["block"]), tool)
            }
            "condition" -> e.getOrNull("expr")?.let { elementToTreeNode(it) } ?:
                            e.childTags().firstOrNull()?.let {
                                println("(srcML) Condition does not contain expression. Attempting statement instead.")
                                elementToTreeNode(it)
                            } ?: EmptyNode(tool) // Skip "condition" and only parse if it's there
            "continue" -> ContinueStmt(tool)
            "default" -> DefaultStmt(tool)
            "do" -> DoWhileStmt(elementToTreeNode(e["block"]), elementToTreeNode(e["condition"]), tool)
            "empty_stmt" -> EmptyStmt(tool)
            "for" -> {
                val control = e["control"]
                val init = control["init"]
                val condition = control.getOrNull("condition")
                val incr = control.getOrNull("incr")
                val body = elementToTreeNode(e["block"])
                if (condition == null || incr == null) {
                    val collectionExpr = init["decl"]["range"]["expr"]
                    ForRangeStmt(elementToTreeNode(collectionExpr), body, tool)
                } else {
                    val initNode = FlattenedNode(init.childTags().map { elementToTreeNode(it) }, SrcMLTree(control, offset))
                    val incrNode = FlattenedNode(incr.childTags().map { elementToTreeNode(it) }, SrcMLTree(incr, offset))
                    ForStmt(initNode,
                        elementToTreeNode(condition),
                        incrNode,
                        body, tool)
                }
            }
            "goto" -> GotoStmt(tool)
            "if_stmt" -> {
                // Dropped element is if-tag
                val elseNodes = e.childTags().drop(1).map { elementToTreeNode(it) }
                // Do artificial nesting of else-if statements
                val indices = elseNodes.indices.reversed()
                var elseNode: TreeNode? = null
                for (i in indices) {
                    val node = elseNodes[i]
                    if (node is IfPart) {
                        elseNode = IfStmt(node, elseNode, node.toolTree)
                    } else {
                        if (i != indices.first) {
                            // Only else-block allowed as alternative, and only at last place of statement. Print warning
                            println("srcML: Recognized non-<if> element inside if-statement")
                        }
                        elseNode = node
                    }

                }
                IfStmt(elementToTreeNode(e["if"]), elseNode, tool)
            }
            "if" -> IfPart(elementToTreeNode(e["condition"]), elementToTreeNode(e["block"]), tool)
            "label" -> LabelStmt(e["name"].textContent, elementToTreeNode(e.childTags().last()), tool)
            "return" -> ReturnStmt(e.getOrNull("expr")?.let { elementToTreeNode(it) }, tool)
            "switch" -> SwitchStmt(elementToTreeNode(e["condition"]), elementToTreeNode(e["block"]), tool)
            "throw" -> ThrowExpr(e.childTags().firstOrNull()?.let { elementToTreeNode(it) }, tool)
            "try" -> {
                val stmt = elementToTreeNode(e["block"])
                val clauses = e.childTags().drop(1).map { elementToTreeNode(it) }
                TryStmt(stmt, clauses, tool)
            }
            "while" -> WhileStmt(elementToTreeNode(e["condition"]), elementToTreeNode(e["block"]), tool)

            // Declarations
            "function", "function_decl", "constructor", "constructor_decl", "destructor", "destructor_decl" -> {
                // Only take f of function name A::B::f
                val name = (e["name"].childTags().lastOrNull { it.tagName == "name" }?.textContent ?: e["name"].textContent).removePrefix("operator")
                var parameters = e["parameter_list"].childTags()
                val props = mutableSetOf<FunctionProps>()
                if (parameters.any { it.textContent.contains("...") }) {
                    parameters = parameters.filter { !it.textContent.contains("...") }
                    props.add(FunctionProps.VARIADIC)
                }
                when (e.tagName.takeWhile { it.isLetter() }) {
                    "constructor" -> props.add(FunctionProps.CONSTRUCTOR)
                    "destructor" -> props.add(FunctionProps.DESTRUCTOR)
                }
                val type = if (e.tagName.contains("function")) extractType(e["type"]) else extractType(e, "void") // void type for constructor, destructor, etc.
                val memberInitListElem = e.getOrNull("member_init_list")
                val memberInits = if (e.tagName != "constructor" || memberInitListElem == null) emptyList<TreeNode>() else {
                    memberInitListElem.childTags().map { memberInit ->
                        val memberTool = SrcMLTree(memberInit, offset)
                        if (memberInit.tagName != "call") {
                            throw IllegalStateException("(srcML) Unrecognized tag '${memberInit.tagName}' used inside " +
                                    "ctor member initializer list at line ${memberTool.originalStartLine}.")
                        }
                        val fieldClassName = memberInit["name"].textContent
                        val fieldClassArg = if (fieldClassName.first().isUpperCase()) {
                            // Class instantiation
                            exprToTreeNode(memberInit) // Converted to CallExpr
                        } else {
                            // Way of above would turn into call to the name of the field, which we don't want
                            // Two possible initializations: using round or curly parenthesis
                            val argList = memberInit["argument_list"]
                            if (argList.textContent.startsWith("{")) {
                                exprToTreeNode(argList) // Will be recognized as InitListExpr
                            } else {
                                argList.getOrNull("argument")?.let { exprToTreeNode(it) } ?: OmittedNode(memberTool)
                            }
                        }
                        CtorMemberInitializer(fieldClassName, fieldClassArg, memberTool)
                    }
                }
                val function = FuncDecl(
                    type, name,
                    parameters.map { elementToTreeNode(it) }, props, e.getOrNull("block")?.let { elementToTreeNode(it) },
                    tool, memberInits = memberInits)
                val template = e.getOrNull("template")
                if (template == null) {
                    function
                } else {
                    extractTemplate(template, function)
                }

            }
            "parameter" -> {
                val decl = e.getOrNull("decl") ?: e["function_decl"]
                val type = extractType(decl["type"])
                val name = decl.getOrNull("name")?.textContent
                FuncDeclParam(type, name, SrcMLTree(decl, offset))
            }
            "decl" ->  {
                val fullName = e.getOrNull("name") // Of form a::b::c[2] at worst: "c" is variable name, missing in case of implicit names (e.g. catch-clauses)
                val varName = fullName?.childTags()?.lastOrNull { it.tagName == "name" }?.textContent ?: fullName?.textContent
                val type = extractType(e["type"])
                // initialization provided either by "T a = ..."-syntax or using "T a(...)" (ctor-call) or using "T a{...}" (init-list)
                val init = e.getOrNull("init")?.let { exprToTreeNode(it.firstChildTag()) }
                    ?: e.getOrNull("argument_list")?.let { list ->
                        val listToolTree = SrcMLTree(list, offset)
                        if (list.textContent.startsWith("{")) {
                            InitListExpr(list.childTags().map { elementToTreeNode(it["expr"]) }, listToolTree)
                        } else {
                            CallExpr(RefExpr(type.unqualifiedTypeName, SrcMLTree(list, offset)),
                                list.childTags().map { exprToTreeNode(it) }, listToolTree) }
                        }
                if (fullName != null && fullName.textContent.contains("[")) {
                    // Array declaration
                    ArrayDecl(type, varName!!,
                            init,
                            tool) // init is block of expressions
                } else {
                    VarDecl(type, varName, init, tool)
                }

            }
            "decl_stmt" -> DeclStmt(e.childTags().map { elementToTreeNode(it) }, tool)
            "enum", "enum_decl" -> {
                val name = e.getOrNull("name")?.textContent
                val type = e.getOrNull("type")?.textContent?.skipQualifier()
                val members = e.getOrNull("block")?.let { block ->
                    val decls = block.childTags()
                    decls.map { decl -> EnumConstant(decl["name"].textContent,
                        decl.getOrNull("init")?.let { exprToTreeNode(it["expr"]) }, SrcMLTree(decl, offset)) }
                } ?: emptyList()
                EnumDecl(name, type, members, tool)
            }

            // "Skipped" nodes, leave out intermediate node and go directly to child
            "expr_stmt", "then", "else" -> elementToTreeNode(e.firstChildTag())


            // Expressions
            // Operators sometimes occur outside of expr-tags, unfortunately
            "expr", "operator" -> exprToTreeNode(e)
            else -> {
                println("Skipped ${e.tagName}")
                var i = 0
                while (i < e.childNodes.length) {
                    val child = e.childNodes.item(i)
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        return elementToTreeNode(child as Element)
                    }
                    i++
                }
                TODO("Skipped terminal node")
            }
        }
    }

    private fun exprToTreeNode(e: Element): TreeNode {
        if (e.tagName == "expr") {
            val children = e.childTags()
            if (children.size > 1) {
                // expr-node with multiple children. Flatten and handle separately
                val converted = children.map { exprToTreeNode(it) }
                return FlattenedNode(converted, SrcMLTree(e, offset))
            } else {
                // expr-node with only one child. Treat as if expr-node wasn't there in first place
                return singleExprToTreeNode(e.firstChildTag())
            }
        }
        return singleExprToTreeNode(e)

    }

    private fun singleExprToTreeNode(e: Element): TreeNode {
        val tool = SrcMLTree(e, offset)
        return when (e.tagName) {
            "argument" -> elementToTreeNode(e["expr"]) // "skip" argument, only care about inner expr
            "argument_list", "block" -> InitListExpr(e.childTags()
                .filter { it.tagName == "expr" }.map { elementToTreeNode(it) }, tool)
            "call" -> {
                val argsElement = e["argument_list"]
                val args = if (argsElement.textContent.startsWith("{")) {
                    listOf(singleExprToTreeNode(argsElement)) // Will be parsed as InitListExpr
                } else {
                    argsElement.childTags().map { exprToTreeNode(it) }
                }
                CallExpr(exprToTreeNode(e.firstChildTag()), args, tool)
            }
            "cast" -> CastExpr(CastKind.valueOf(e.getAttribute("type").toUpperCase()),
                // Take second (thus last) argument_list elem, and take only (thus first) inner argument elem, convert that
                exprToTreeNode(e.childTags().last().childTags().first()), tool)
            "index" -> ArrayAccessExpr(exprToTreeNode(e.firstChildTag()), tool)
            "lambda" -> {
                val closure = e["capture"].childTags().map { exprToTreeNode(it.firstChildTag()) }
                val args = e["parameter_list"].childTags().map { elementToTreeNode(it) }
                val body = elementToTreeNode(e["block"])
                LambdaExpr(closure, args, body, tool)
            }
            "literal" -> {
                val isNumber = e.getAttribute("type") == "number"
                val isHex = e.textContent.startsWith("0x")
                val raw = if (isNumber)
                    // Filter out long/float modifiers at back of number, but do not filter f if it's hexadecimal
                    e.textContent.toLowerCase().dropLastWhile { it in listOfNotNull('u', 'l', if (isHex) null else 'f') } else e.textContent
                val text = when { // 0x6A09E667
                    isNumber && isHex ->
                        BigInteger(raw.drop(2), 16).toString()
                    isNumber && (listOf(".", "e").any { raw.contains(it, ignoreCase = true) }) ->
                        floatNumberFormat.format(BigDecimal(raw))
                    else -> raw
                }
                LiteralExpr(text, tool)
            }
            "operator" -> Operator(StringEscapeUtils.unescapeHtml4(e.textContent), tool)
            "name" -> {
                val children = e.childTags()
                val childNodes = mutableListOf<TreeNode>()
                for (i in children.indices) {
                    val child = children[i]
                    if (child.tagName == "argument_list" && child.getAttribute("type") == "generic") {
                        continue
                        // Uncomment below and remove continue-statement to re-include template arguments within target name
//                        val oldExpr = childNodes.removeLast()
//                        if (oldExpr !is RefExpr) {
//                            throw IllegalStateException("(SrcML) Argument list for variable without RefExpr in front")
//                        }
//                        childNodes.add(RefExpr((oldExpr.target + child.textContent).filter { !it.isWhitespace() }, oldExpr.toolTree))
                    } else {
                        childNodes.add(exprToTreeNode(child))
                    }
                }
                if (children.isEmpty()) {
                    RefExpr(e.textContent, tool)
                } else {
                    FlattenedNode(childNodes, tool)
                }
            }
            "sizeof" -> SizeOfExpr(FlattenedNode.parenthesised(e["argument_list"].childTags().map { elementToTreeNode(it["expr"]) }, tool), tool)
            "ternary" -> TernaryExpr(elementToTreeNode(e["condition"]), elementToTreeNode(e["then"]),
                elementToTreeNode(e["else"]), tool)
            "typeid" -> TypeIdExpr(exprToTreeNode(e["argument_list"]["argument"]), tool)
            else -> {
                println("(srcML) Unrecognized terminal expression ${e.tagName} at line ${tool.originalStartLine}")
                IncomparableNode(tool, "UNUSABLE TAG (${e.tagName})")
            }
        }
    }

    private fun extractType(e: Element, fixedName: String? = null): TypeInfo {
        val children = e.childTags()
        val specifiers = (children.filter { it.tagName == "specifier" }.map { it.textContent }.filter { it != "explicit" } +
                children.filter { it.tagName == "name" && it.textContent in TypeInfo.LEGAL_SPECIFIERS }.map { it.textContent }).toMutableList()
        val name = fixedName ?: run {
            val nameElem = children.lastOrNull { it.tagName == "name" } // Might be null when "auto" specifier used
            val nameChildren = nameElem?.childTags()
            when {
                nameChildren == null && specifiers.contains("auto") -> {
                    specifiers.remove("auto")
                    "auto"
                }
                nameChildren == null ->
                    throw IllegalStateException("No name for type found")
                nameChildren.isEmpty() -> nameElem.textContent
                else -> nameChildren.last { it.tagName == "name" }.textContent
            }
        }
        return TypeInfo(name, specifiers, SrcMLTree(e, offset))
    }

    private fun extractTemplate(e: Element, innerEntity: TreeNode): TreeNode {
        if (e.tagName != "template") {
            throw IllegalArgumentException("Expected element with tag 'template', but got '${e.tagName}'")
        }
        val parameters = e["parameter_list"].childTags().map {
            TemplateParameter(extractType(it["type"]), it.getOrNull("name")?.textContent, SrcMLTree(it, offset))
        }
        return if (parameters.isEmpty()) {
            innerEntity
        } else {
            TemplatedDecl(parameters, innerEntity, innerEntity.toolTree)
        }
    }
}