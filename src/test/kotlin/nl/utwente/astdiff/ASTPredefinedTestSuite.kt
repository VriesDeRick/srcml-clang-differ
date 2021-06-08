package nl.utwente.astdiff

import com.xenomachina.argparser.ArgParser
import junit.framework.Assert
import nl.utwente.astdiff.ast.*

open class ASTPredefinedTestSuite(val parser: ExecutableParser) {

    private val tool = DummyToolTree()
    private val intType = TypeInfo("int", emptyList(), tool)

    fun simpleExpressionTree(): TreeNode {
        return TranslationUnit(listOf(
                FuncDecl(intType, "main", emptyList(), emptySet(), BlockStmt(listOf(
                    VarDecl(intType, "a",
                            LiteralExpr("100E0", tool), tool),
                    VarDecl(TypeInfo("char", emptyList(), tool), "b",
                            LiteralExpr("'b'", tool), tool),
                    VarDecl(TypeInfo("bool", emptyList(), tool), "c",
                            TernaryExpr(FlattenedNode(listOf(LiteralExpr("1", tool), Operator(">", tool),
                                    LiteralExpr("2", tool)), tool),
                                LiteralExpr("true", tool),
                                LiteralExpr("false", tool), tool), tool),
                    VarDecl(intType, "d",
                            CallExpr(RefExpr("main", tool), emptyList(), tool), tool),
                    ArrayDecl(intType, "array", InitListExpr(listOf(
                        LiteralExpr("1", tool),
                        FlattenedNode(listOf(
                            LiteralExpr("1", tool),
                            Operator("+", tool),
                            LiteralExpr("1", tool)), tool),
                        FlattenedNode(listOf(
                            LiteralExpr("1", tool),
                            Operator("*", tool),
                            LiteralExpr("1", tool)), tool),
                        CallExpr(RefExpr("main", tool), emptyList(), tool),
                        RefExpr("a", tool)
                    ), tool), tool),
                    VarDecl(TypeInfo("auto", emptyList(), tool), "f1", LambdaExpr(
                        listOf(RefExpr("a", tool), RefExpr("b", tool)),
                        listOf(FuncDeclParam(intType, "argumentToLambda", tool)),
                        BlockStmt(listOf(
                            ReturnStmt(FlattenedNode(listOf(RefExpr("a", tool), Operator("+", tool),
                                LiteralExpr("1", tool)), tool), tool)
                        ), tool), tool), tool),
                    VarDecl(TypeInfo("auto", emptyList(), tool), "f2", LambdaExpr(
                        emptyList(),
                        emptyList(),
                        BlockStmt(listOf(
                            EmptyStmt(tool),
                            ReturnStmt(LiteralExpr("1", tool), tool)
                        ), tool), tool), tool),
                    FlattenedNode(listOf(Operator("(", tool), RefExpr("int", tool),
                        Operator(")", tool), LiteralExpr("1.5E0", tool)), tool),
                    FlattenedNode(listOf(Operator("(", tool), RefExpr("int", tool),
                        Operator("*", tool), Operator(")", tool), LiteralExpr("200", tool)), tool),
                    FlattenedNode(listOf(Operator("(", tool), RefExpr("int", tool),
                        Operator("*", tool), Operator(")", tool), LiteralExpr("300", tool)), tool),
                    SizeOfExpr(FlattenedNode.parenthesised(RefExpr("int", tool), tool), tool),
                    SizeOfExpr(FlattenedNode.parenthesised(LiteralExpr("'\n'", tool), tool), tool),
                    TypeIdExpr(RefExpr("int", tool), tool),
                    TypeIdExpr(LiteralExpr("1", tool), tool),
                    ReturnStmt(LiteralExpr("0", tool), tool)
            ), tool), tool)
        ), tool)
    }

    fun arraysTree(): TreeNode {
        return TranslationUnit(listOf(
            FuncDecl(intType, "main", emptyList(), emptySet(), BlockStmt(listOf(
                ArrayDecl(intType, "intInitList", InitListExpr(listOf(
                    LiteralExpr("1", tool), LiteralExpr("2", tool), LiteralExpr("3", tool)
                ), tool), tool),
                ArrayDecl(TypeInfo("char", emptyList(), tool), "charStringInit", LiteralExpr("\"abc\"", tool), tool),
                ArrayDecl(intType, "intNoInit", null, tool),
                FlattenedNode(listOf(
                    RefExpr("intInitList", tool),
                    ArrayAccessExpr(LiteralExpr("1", tool), tool)), tool),
            ), tool), tool)
        ), tool)
    }

    fun statementsTree(): TreeNode {
        return TranslationUnit(listOf(
            FuncDecl(
                TypeInfo("void", emptyList(), tool), "b", listOf(FuncDeclParam(intType, "z", tool)),
                setOf(FunctionProps.VARIADIC), BlockStmt(listOf(ReturnStmt(null, tool)
            ), tool),tool),
            FuncDecl(intType, "main", emptyList(), emptySet(), BlockStmt(listOf(
                LabelStmt("start", ForStmt(EmptyNode(tool), EmptyNode(tool), EmptyNode(tool),
                    VarDecl(intType, "a", LiteralExpr("5", tool), tool), tool), tool),
                ForStmt(VarDecl(intType, "i", LiteralExpr("0", tool), tool),
                    FlattenedNode(listOf(RefExpr("i", tool), Operator("<", tool),
                        LiteralExpr("1", tool), Operator("+", tool), LiteralExpr("2", tool)), tool),
                    FlattenedNode(listOf(Operator("++", tool), RefExpr("i", tool)), tool),
                    VarDecl(intType, "a", LiteralExpr("5", tool), tool), tool),
                ArrayDecl(intType, "array1", InitListExpr(listOf(LiteralExpr("1", tool)), tool), tool),
                ForRangeStmt(RefExpr("array1", tool), BlockStmt(listOf(FlattenedNode(listOf(
                    RefExpr("a", tool), Operator("++", tool)), tool)), tool), tool),

                WhileStmt(FlattenedNode(listOf(
                    LiteralExpr("1", tool), Operator("==", tool), LiteralExpr("5", tool)
                ), tool), LiteralExpr("1", tool),tool),
                WhileStmt(FlattenedNode(listOf(
                    LiteralExpr("1", tool), Operator("<", tool), LiteralExpr("5", tool)
                ), tool), BlockStmt(listOf(
                    ContinueStmt(tool),
                    VarDecl(intType, "loop", LiteralExpr("5", tool), tool)
                ), tool),tool),
                VarDecl(TypeInfo("long", emptyList(), tool), "a",
                    CastExpr(CastKind.STATIC, LiteralExpr("0", tool), tool), tool),
                FlattenedNode(listOf(RefExpr("a", tool), Operator("=", tool), LiteralExpr("1", tool)), tool),
                SwitchStmt(RefExpr("a", tool), BlockStmt(listOf(
                    CaseStmt(LiteralExpr("1", tool), tool),
                    FlattenedNode(listOf(RefExpr("a", tool), Operator("=", tool), LiteralExpr("4", tool)), tool),
                    FlattenedNode(listOf(RefExpr("a", tool), Operator("=", tool), LiteralExpr("3", tool)), tool),
                    BreakStmt(tool),
                    CaseStmt(LiteralExpr("0", tool), tool),
                    CaseStmt(LiteralExpr("2", tool), tool),
                    FlattenedNode(listOf(RefExpr("a", tool), Operator("=", tool), LiteralExpr("5", tool)), tool),
                    DefaultStmt(tool),
                    FlattenedNode(listOf(RefExpr("a", tool), Operator("=", tool), LiteralExpr("6", tool)), tool),
                    ), tool), tool),
                IfStmt(IfPart(RefExpr("a", tool), FlattenedNode(listOf(RefExpr("a", tool),
                    Operator("=", tool), LiteralExpr("2", tool)), tool), tool), null, tool),
                IfStmt(IfPart(RefExpr("a", tool), BlockStmt(emptyList(), tool), tool), BlockStmt(listOf(
                    FlattenedNode(listOf(RefExpr("a", tool), Operator("=", tool), LiteralExpr("3", tool)), tool)
                ), tool), tool),
                IfStmt(IfPart(RefExpr("a", tool), ReturnStmt(LiteralExpr("0", tool), tool), tool),
                    IfStmt(IfPart(FlattenedNode(listOf(Operator("!", tool), RefExpr("a", tool)), tool),
                        ReturnStmt(LiteralExpr("1", tool), tool), tool),
                        IfStmt(IfPart(RefExpr("a", tool), BlockStmt(listOf(ReturnStmt(LiteralExpr("2", tool), tool)), tool), tool),
                            BlockStmt(listOf(ReturnStmt(LiteralExpr("3", tool), tool)), tool), tool),
                        tool), tool),
                DoWhileStmt(BlockStmt(listOf(VarDecl(intType, "a", null, tool)), tool), LiteralExpr("1", tool), tool),
                DoWhileStmt(LiteralExpr("3", tool), LiteralExpr("true", tool), tool),
                TryStmt(BlockStmt(listOf(LiteralExpr("1", tool)), tool), listOf(
                    CatchStmt(VarDecl(TypeInfo("overflow_error", listOf("const"), tool), "e",
                        null, tool), BlockStmt(listOf(LiteralExpr("2", tool)), tool), tool),
                    CatchStmt(VarDecl(intType, null, null, tool),
                        BlockStmt(listOf(ThrowExpr(null, tool)), tool), tool),
                    CatchStmt(EmptyNode(tool), BlockStmt(listOf(EmptyStmt(tool)), tool), tool)
                ), tool)
                ), tool), tool)
        ), tool)
    }

    fun modifiersTree(): TreeNode {
        return TranslationUnit(listOf(
            NamespaceDecl("N1", listOf(
                NamespaceDecl("N2", listOf(
                    RecordDecl(RecordType.CLASS, "Nested", emptyList(), emptyList(), tool),
                    FuncDecl(intType, "nf", emptyList(), emptySet(), BlockStmt(listOf(
                        ReturnStmt(LiteralExpr("42", tool), tool)
                    ), tool), tool)
                ), tool)
            ), tool),
            RecordDecl(RecordType.CLASS, "B", emptyList(), listOf(
                SpecifiedAccess(AccessSpecifier.PUBLIC, listOf(
                    VarDecl(intType, "z", null, tool)
                ), tool)
            ), tool),
            RecordDecl(RecordType.CLASS, "A", emptyList(), listOf(
                SpecifiedAccess(AccessSpecifier.PUBLIC, listOf(
                    VarDecl(intType, "a", null, tool),
                    VarDecl(TypeInfo("B", emptyList(), tool), "b", null, tool),
                    FuncDecl(TypeInfo("void", emptyList(), tool), "elseWhere", listOf(
                        FuncDeclParam(TypeInfo("Nested", listOf("volatile"), tool), null, tool)
                    ), emptySet(), null, tool),
                    FuncDecl(intType, "f", emptyList(), emptySet(), BlockStmt(listOf(
                        ReturnStmt(LiteralExpr("5", tool), tool)
                    ), tool), tool)
                ), tool)
            ), tool),
            FuncDecl(
                TypeInfo("void", emptyList(), tool), "elseWhere", listOf(FuncDeclParam(
                    TypeInfo("Nested", listOf("volatile"), tool), "arg", tool)), emptySet(),
                BlockStmt(listOf(CallExpr(RefExpr("Nested", tool), listOf(InitListExpr(emptyList(), tool)), tool)
            ), tool), tool),
            VarDecl(TypeInfo("Nested", listOf("extern"), tool), "externalThing", null, tool),
            FuncDecl(intType, "main", emptyList(), emptySet(), BlockStmt(listOf(
                ArrayDecl(intType, "array1", null, tool),
                VarDecl(TypeInfo("A", emptyList(), tool), "obj", LiteralExpr("nullptr", tool), tool),
                VarDecl(TypeInfo("B", emptyList(), tool), "b", InitListExpr(listOf(), tool), tool),
                VarDecl(intType, "namespace_result",
                    CallExpr(RefExpr("nf", tool), emptyList(), tool), tool),
                VarDecl(intType, "ptiptr", FlattenedNode(
                    listOf(Operator("&", tool), RefExpr("z", tool)), tool), tool),
                VarDecl(TypeInfo("A", emptyList(), tool), "local", null, tool),
                FlattenedNode(listOf(RefExpr("obj", tool), Operator("=", tool),
                    Operator("&", tool), RefExpr("local", tool)), tool),
                FlattenedNode(listOf(RefExpr("array1", tool),
                    ArrayAccessExpr(LiteralExpr("0", tool), tool)), tool),
                FlattenedNode(listOf(Operator("&", tool), RefExpr("array1", tool)), tool),
                FlattenedNode(listOf(RefExpr("b", tool), Operator(".", tool), RefExpr("z", tool)), tool),
                FlattenedNode(listOf(RefExpr("obj", tool), Operator("->", tool), RefExpr("b", tool)), tool),
                FlattenedNode(listOf(RefExpr("b", tool), Operator(".*", tool),
                    RefExpr("ptiptr", tool), Operator("=", tool), LiteralExpr("10", tool)), tool),
                FlattenedNode(listOf(Operator("(", tool), RefExpr("b", tool),
                    Operator("&", tool), Operator(")", tool), Operator("->*", tool),
                    RefExpr("ptiptr", tool), Operator("=", tool), LiteralExpr("11", tool)), tool),
                ReturnStmt(LiteralExpr("0", tool), tool)
            ), tool), tool)
            ), tool)
    }

    fun classesTree(): TreeNode {
        return TranslationUnit(listOf(
            RecordDecl(RecordType.CLASS, "Base", emptyList(), emptyList(), tool),
            FuncDecl(intType, "main", emptyList(), emptySet(), null, tool),
            EnumDecl("SmallEnum", null, listOf(
                EnumConstant("FIRST", null, tool),
                EnumConstant("SECOND", null, tool)
            ), tool),
            EnumDecl("LargeEnum", "int", listOf(
                EnumConstant("ONE", LiteralExpr("1", tool), tool),
                EnumConstant("TWO", LiteralExpr("'b'", tool), tool)
            ), tool),
            NamespaceDecl("A", listOf(
                VarDecl(TypeInfo("int", emptyList(), tool), "withinA", null, tool),
                RecordDecl(RecordType.STRUCT, "StructA", emptyList(), emptyList(), tool)
            ), tool),
            RecordDecl(RecordType.CLASS, "Base", emptyList(), listOf(
                VarDecl(TypeInfo("int", emptyList(), tool), "implicitPrivate", null, tool),
                FriendDecl(FuncDecl(intType, "main", emptyList(), emptySet(), null, tool), tool),
                SpecifiedAccess(AccessSpecifier.PROTECTED, emptyList(), tool),
                SpecifiedAccess(AccessSpecifier.PUBLIC, listOf(
                    FuncDecl(TypeInfo("int", emptyList(), tool), "a", emptyList(), emptySet(), null, tool),
                    FuncDecl(
                        TypeInfo("void", listOf("inline"), tool), "Base", listOf(
                            FuncDeclParam(TypeInfo("int", emptyList(), tool), "c", tool)
                        ), setOf(FunctionProps.CONSTRUCTOR), BlockStmt(listOf(
                            CallExpr(FlattenedNode(listOf(
                                RefExpr("this", tool),
                                Operator("->", tool),
                                RefExpr("a", tool)
                            ), tool), emptyList(), tool)
                        ), tool), tool
                    ),
                    FuncDecl(TypeInfo("void", listOf("virtual"), tool), "~Base", emptyList(),
                        setOf(FunctionProps.DESTRUCTOR), BlockStmt(emptyList(), tool), tool)
                ), tool)
            ), tool),
            RecordDecl(RecordType.CLASS, "Child", listOf(InheritanceBaseRef("Base", AccessSpecifier.PROTECTED)), listOf(
                UsingDecl("a", false, tool),
                // FriendDecl(RecordDecl(RecordType.CLASS, "Base", emptyList(), emptyList(), tool), tool), // Somehow breaks srcML
                SpecifiedAccess(AccessSpecifier.PRIVATE, listOf(
                    VarDecl(intType, "y", null, tool),
                    VarDecl(intType, "z", null, tool),
                    FuncDecl(TypeInfo("void", listOf("virtual"), tool), "~Child", emptyList(),
                        setOf(FunctionProps.DESTRUCTOR), BlockStmt(emptyList(), tool), tool)
                ), tool),
                SpecifiedAccess(AccessSpecifier.PUBLIC, listOf(
                    FuncDecl(TypeInfo("void", emptyList(), tool), "Child", emptyList(),
                        setOf(FunctionProps.CONSTRUCTOR), BlockStmt(emptyList(), tool), tool, memberInits = listOf(
                            CtorMemberInitializer("Base", CallExpr(RefExpr("Base", tool), listOf(LiteralExpr("1", tool)), tool), tool),
                            CtorMemberInitializer("y", InitListExpr(emptyList(), tool), tool),
                            CtorMemberInitializer("z", RefExpr("withinA", tool), tool)
                        ))
                ), tool)
            ), tool),
            FuncDecl(TypeInfo("int", emptyList(), tool), "main", emptyList(), emptySet(), BlockStmt(listOf(
                UsingDecl("A", true, tool),
                UsingDecl("withinA", false, tool)
            ), tool), tool)
        ), tool)
    }

    fun templatesTree(): TreeNode {
        val structAType = TypeInfo("A", emptyList(), tool)
        return TranslationUnit(listOf(
            TemplatedDecl(listOf(
                TemplateParameter(intType, "f", tool),
                TemplateParameter(intType, "g", tool)
            ), FuncDecl(intType, "fTemplate", emptyList(), emptySet(), BlockStmt(listOf(
                ReturnStmt(RefExpr("f", tool), tool)
            ), tool), tool), tool),
            TemplatedDecl(listOf(
                TemplateParameter(TypeInfo("class", emptyList(), tool), "T1", tool),
                TemplateParameter(TypeInfo("typename", emptyList(), tool), "T2", tool)
            ), RecordDecl(RecordType.CLASS, "withType", emptyList(), listOf(
                SpecifiedAccess(AccessSpecifier.PUBLIC, listOf(
                    FuncDecl(TypeInfo("void", emptyList(), tool), "withType",
                        emptyList(), setOf(FunctionProps.CONSTRUCTOR), null, tool)
                ), tool)
            ), tool), tool),
            TemplatedDecl(listOf(
                TemplateParameter(TypeInfo("bool", emptyList(), tool), null, tool)
            ), RecordDecl(RecordType.STRUCT, "A", emptyList(), listOf(
                FuncDecl(TypeInfo("void", emptyList(), tool), "A",
                    listOf(FuncDeclParam(TypeInfo("bool", emptyList(), tool), null, tool)),
                    setOf(FunctionProps.CONSTRUCTOR), BlockStmt(emptyList(), tool), tool),
                FuncDecl(TypeInfo("void", emptyList(), tool), "A",
                    listOf(FuncDeclParam(TypeInfo("void", emptyList(), tool), null, tool)),
                    setOf(FunctionProps.CONSTRUCTOR), BlockStmt(emptyList(), tool), tool),
            ), tool), tool),
            FuncDecl(intType, "main", emptyList(), emptySet(), BlockStmt(listOf(
                CallExpr(RefExpr("fTemplate", tool), emptyList(), tool),
                VarDecl(structAType, "called", CallExpr(RefExpr("A", tool),
                    listOf(LiteralExpr("true", tool)), tool), tool),
                VarDecl(structAType, "d", LiteralExpr("0", tool), tool),
                VarDecl(TypeInfo("int", listOf("const"), tool), "b", LiteralExpr("1", tool), tool),
                FlattenedNode(listOf(
                    Operator("new", tool),
                    CallExpr(RefExpr("A", tool), listOf(LiteralExpr("1", tool)), tool)
                ), tool)
            ), tool), tool)
        ), tool)
    }


    private fun treeFromTestFile(resource: String): TreeNode {
        val args = ClIArgs(ArgParser(arrayOf("src/test/resources/$resource")))
        return produceToolTree(parser, args)
    }

    fun testFile(filename: String, tree: TreeNode) {
        val parsed = treeFromTestFile(filename)
        val report = parsed.compareWith(tree)
        if (report.kind != CompareReport.MatchKind.FULL) {
            println(report.asPrettyPrint(0))
            Assert.fail()
        }

    }

}