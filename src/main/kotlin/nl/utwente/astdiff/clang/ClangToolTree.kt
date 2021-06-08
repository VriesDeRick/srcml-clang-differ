package nl.utwente.astdiff.clang

import nl.utwente.astdiff.ast.ToolTree
import nl.utwente.astdiff.ast.TreeProducer

class ClangToolTree(private val node: ClangNode, private val offset: Int) : ToolTree {
    override val originalStartLine: Int?
        get() = preprocessedStartLine?.let { it - offset }
    override val originalEndLine: Int?
        get() = preprocessedEndLine?.let { it - offset }
    override val preprocessedStartLine: Int?
        get() = node.range?.begin?.line
    override val preprocessedEndLine: Int?
        get() = node.range?.end?.line
    override val rootName: String
        get() = node.kind
    override val producer: TreeProducer
        get() = TreeProducer.CLANG
}