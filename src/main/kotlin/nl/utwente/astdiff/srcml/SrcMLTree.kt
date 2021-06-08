package nl.utwente.astdiff.srcml

import nl.utwente.astdiff.ast.ToolTree
import nl.utwente.astdiff.ast.TreeProducer
import org.w3c.dom.Element

class SrcMLTree(private val element: Element, private val offset: Int) : ToolTree {
    override val originalStartLine: Int
        get() = preprocessedStartLine - offset
    override val originalEndLine: Int
        get() = preprocessedEndLine - offset
    override val preprocessedStartLine: Int
        get() = (element.getUserData(XmlDom.LINE_NUM_START_ATTR) as String).toInt()
    override val preprocessedEndLine: Int
        get() = (element.getUserData(XmlDom.LINE_NUM_END_ATTR) as String).toInt()
    override val rootName: String
        get() = element.tagName
    override val producer: TreeProducer
        get() = TreeProducer.SRCML
}