package nl.utwente.astdiff.ast

enum class TreeProducer {
    DUMMY,
    SRCML,
    CLANG,
}

interface ToolTree {
    val originalStartLine: Int?
    val originalEndLine: Int?
    val preprocessedStartLine: Int?
    val preprocessedEndLine: Int?
    val rootName: String
    val producer: TreeProducer
}