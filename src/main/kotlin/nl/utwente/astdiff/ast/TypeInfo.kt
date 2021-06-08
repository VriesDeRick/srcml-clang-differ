package nl.utwente.astdiff.ast

class TypeInfo private constructor (val unqualifiedTypeName: String, val specifiers: List<String>, toolTree: ToolTree) :
    TreeNode(toolTree, emptyList(), listOf(unqualifiedTypeName, specifiers)) {

    companion object {
        operator fun invoke(unqualifiedTypeName: String, givenSpecifiers: List<String>, toolTree: ToolTree): TypeInfo {
            val specifiers = givenSpecifiers.sorted().toMutableList()
            // Numerical specifier normalization, also see https://stackoverflow.com/a/18971763
            val normalizedType = if (unqualifiedTypeName in listOf("long", "unsigned", "short")) {
                specifiers.add(unqualifiedTypeName)
                "int"
            } else unqualifiedTypeName
            return TypeInfo(normalizedType, specifiers, toolTree)
        }

        val LEGAL_SPECIFIERS = setOf(
            // Type specifiers
            "const",
            "volatile",
            "signed",
            "unsigned",
            "short",
            "long",

            // Storage classes
            "register",
            "static",
            "thread_local",
            "extern",
            "mutable",

            "virtual",
        )
    }
}