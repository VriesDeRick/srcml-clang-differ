package nl.utwente.astdiff

import nl.utwente.astdiff.ast.ToolTree
import nl.utwente.astdiff.ast.TreeProducer

class DummyToolTree : ToolTree {
    override val originalStartLine: Int
        get() = -1
    override val originalEndLine: Int
        get() = -1
    override val preprocessedStartLine: Int
        get() = -1
    override val preprocessedEndLine: Int
        get() = -1
    override val rootName: String
        get() = "DUMMY"
    override val producer: TreeProducer
        get() = TreeProducer.DUMMY
}