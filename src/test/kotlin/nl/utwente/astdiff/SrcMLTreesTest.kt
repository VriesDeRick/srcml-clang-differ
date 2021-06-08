package nl.utwente.astdiff

import org.junit.Test

class SrcMLTreesTest : ASTPredefinedTestSuite(ExecutableParser.SRCML) {

    @Test
    fun testExpressions() {
        testFile("predefined/expressions.cpp", simpleExpressionTree())
    }

    @Test
    fun testArrays() {
        testFile("predefined/arrays.cpp", arraysTree())
    }

    @Test
    fun testStatements() {
        testFile("predefined/statements.cpp", statementsTree())
    }

    @Test
    fun testModifiers() {
        testFile("predefined/modifiers.cpp", modifiersTree())
    }

    @Test
    fun testClasses() {
        testFile("predefined/classes.cpp", classesTree())
    }

    @Test
    fun testTemplates() {
        testFile("predefined/templates.cpp", templatesTree())
    }

}