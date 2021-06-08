package nl.utwente.astdiff.clang

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.IOException


open class ClangSkipDeserializer : JsonDeserializer<List<ClangNode>>() {

    companion object {
        val SKIPPED_TERMINAL_NODES = emptyList<String>() // listOf("ImplicitValueInitExpr", "CXXConstructExpr", "CXXDefaultArgExpr")
    }

    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<ClangNode> {
        val objectCodec = p.codec
        val listOrObjectNode = objectCodec.readTree<JsonNode>(p)
        val result: MutableList<ClangNode> = mutableListOf()
        val addObj: (jsonNode: ObjectNode) -> Unit = { jsonNode ->
            val clangNode = objectCodec.treeToValue(jsonNode, ClangNode::class.java)
            jsonNode.remove("inner") // Prevent excessive memory usage by not duplicating children/leaves exponentially
            clangNode.json = jsonNode
            if (!(clangNode.inner.isEmpty() && clangNode.kind in SKIPPED_TERMINAL_NODES)) {
                result.add(clangNode)
            }
        }
        if (listOrObjectNode.isArray) {
            for (node in listOrObjectNode) {
                if (node.isObject && (!node.fields().hasNext() || !node.has("kind"))) {
                    result.add(ClangNode.createNullNode())
                    continue
                }
                if (node.has("inner")) {
                    val children = node.get("inner")
                    // Re-organise AccessSpecDecls to have nesting instead of being siblings
                    val reworkedInner = ctxt.nodeFactory.arrayNode()
                    var currentAccessSpec: JsonNode? = null
                    for (childNode in children) {
                        val child = childNode as ObjectNode
                        if (child.has("kind") && child["kind"].textValue() == "AccessSpecDecl") {
                            if (currentAccessSpec != null) {
                                reworkedInner.add(currentAccessSpec)
                            }
                            child.set<ArrayNode>("inner", ctxt.nodeFactory.arrayNode())
                            currentAccessSpec = child
                            continue
                        }
                        if (currentAccessSpec == null) {
                            reworkedInner.add(childNode)
                        } else {
                            val inner = currentAccessSpec["inner"] as ArrayNode
                            inner.add(childNode)
                        }
                    }
                    if (currentAccessSpec != null) {
                        reworkedInner.add(currentAccessSpec)
                    }
                    (node as ObjectNode).replace("inner", reworkedInner)

                    // Add line numbers for children, since they're missing from Clang output
                    for (child in reworkedInner) {
                        if (child.has("range")) {
                            val childBegin = child.get("range").get("begin") as ObjectNode
                            if (!childBegin.has("line")) {
                                // Add line either from "loc" sibling object or line-property of parent
                                if (child.has("loc") && child.get("loc").has("line")) {
                                    childBegin.put("line", child.get("loc").get("line").intValue())
                                } else {
                                    val begin = node.get("range")?.get("begin")?.get("line")
                                    if (begin != null) {
                                        childBegin.put("line", begin.intValue())
                                    }
                                }
                            }
                        }
                    }
                }

                addObj(node as ObjectNode)
            }
        } else if (listOrObjectNode.isObject) {
            val objNode = listOrObjectNode as ObjectNode
            addObj(objNode)
        } else {
            TODO()
        }
        return result
    }
}
