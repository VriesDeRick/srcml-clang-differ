package nl.utwente.astdiff.srcml

import org.apache.commons.text.StringEscapeUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.Attributes
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

// Converted from https://stackoverflow.com/a/57427125
object XmlDom {
    const val LINE_NUM_START_ATTR = "startLineNumber"
    const val LINE_NUM_END_ATTR = "endLineNumber"


    @Throws(IOException::class, SAXException::class)
    fun readXML(`is`: InputStream?): Document {
        val doc: Document
        val parser: SAXParser
        try {
            val factory = SAXParserFactory.newInstance()
            parser = factory.newSAXParser()
            val docBuilderFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docBuilderFactory.newDocumentBuilder()
            doc = docBuilder.newDocument()
        } catch (e: ParserConfigurationException) {
            throw RuntimeException("Can't create SAX parser / DOM builder.", e)
        }
        val elementStack: Deque<Element> = ArrayDeque()
        val textBuffer = StringBuilder()
        val handler: DefaultHandler = object : DefaultHandler() {
            private var locator: Locator? = null
            override fun setDocumentLocator(locator: Locator) {
                this.locator = locator //Save the locator, so that it can be used later for line tracking when traversing nodes.
            }

            @Throws(SAXException::class)
            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                addTextIfNeeded()
                val el = doc.createElement(qName)
                for (i in 0 until attributes.length) el.setAttribute(attributes.getQName(i), attributes.getValue(i))
                el.setUserData(LINE_NUM_START_ATTR, locator!!.lineNumber.toString(), null)
                elementStack.push(el)
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                addTextIfNeeded()
                val closedEl = elementStack.pop()
                closedEl.setUserData(LINE_NUM_END_ATTR, locator!!.lineNumber.toString(), null)
                if (closedEl.tagName == "decl_stmt") {
                    // Copy type-tag to all decls inside decl_stmt
                    val type = closedEl["decl"]["type"]
                    closedEl.childTags().drop(1).forEach { decl ->
                        decl.childTags().filter { it.tagName == "type" }.forEach { decl.removeChild(it) }
                        decl.appendChild(type.cloneNode(true))
                    }
                }
                if (closedEl.tagName !in SrcMLParser.SKIPPED_TAGS) {
                    if (elementStack.isEmpty()) { // Is this the root element?
                        doc.appendChild(closedEl)
                    } else {
                        val parentEl = elementStack.peek()
                        // Labels need to be made parent of the statement they're labelling, as opposed to being siblings.
                        // To do that, find sibling Element that was last added to the parent.
                        // For that, we need a loop, since childNodes also contains text nodes and such
                        var i = parentEl.childNodes.length - 1
                        var sibling: Element? = null
                        while (i > 0) {
                            val node = parentEl.childNodes.item(i)
                            if (node.nodeType == Node.ELEMENT_NODE) {
                                sibling = node as Element
                                break
                            }
                            i--
                        }
                        // Move closedEl from parent-child to sibling-child if no node was moved previously (size-check)
                        if (sibling != null && sibling.tagName == "label" && sibling.childTags().size < 2) {
                            sibling.appendChild(closedEl)
                        } else {
                            parentEl.appendChild(closedEl)
                        }
                    }
                }
            }

            @Throws(SAXException::class)
            override fun characters(ch: CharArray, start: Int, length: Int) {
                textBuffer.append(ch, start, length)
            }

            // Outputs text accumulated under the current node
            private fun addTextIfNeeded() {
                if (textBuffer.isNotEmpty()) {
                    val el = elementStack.peek()
                    val textNode: Node = doc.createTextNode(StringEscapeUtils.unescapeJava(textBuffer.toString()))
                    el.appendChild(textNode)
                    textBuffer.delete(0, textBuffer.length)
                }
            }
        }
        parser.parse(`is`, handler)
        return doc
    }
}

operator fun Element.get(name: String): Element {
    return this.getOrNull(name) ?: let {
        val tool = SrcMLTree(this, 0)
        println("(srcML) Could not find node with name '${name}' inside '${this.tagName}' on line ${tool.preprocessedStartLine} (after preprocessing)")
        this
    }

}

fun Element.getOrNull(name: String): Element? {
    val nodes = this.childNodes
    var i = 0
    while (i < nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            if (element.tagName == name) {
                return element
            }
        }
        i++
    }
    return null
}

fun Element.firstChildTag() : Element {
    return this.firstChildTagOrNull()
        ?: throw NoSuchElementException("No child tag for this element")
}

fun Element.firstChildTagOrNull() : Element? {
    val nodes = this.childNodes
    var i = 0
    while (i < nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            return node as Element
        }
        i++
    }
    return null
}

fun Element.childTags() : List<Element> {
    val nodes = this.childNodes
    val result = mutableListOf<Element>()
    var i = 0
    while (i < nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            result.add(node as Element)
        }
        i++
    }
    return result
}
