package nl.utwente.astdiff.clang

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.utwente.astdiff.ast.AccessSpecifier
import nl.utwente.astdiff.ast.RecordType
import nl.utwente.astdiff.ast.TypeInfo

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClangNode(
    val id: String?,
    val kind: String,
    val range: ClangNodeRange?,
    val opcode: String?,
    @JsonDeserialize(using = ClangSkipDeserializer::class)
    @JsonAlias("array_filler") // Used within InitListExpr
    val inner: List<ClangNode> = emptyList(),
    @JsonAlias("member")
    val name: String?,
    @JsonAlias("implicit")
    val isImplicit: Boolean = false,
    val value: String?,
    val type: Map<String, String> = emptyMap(),
    val isArrow: Boolean?,
    val referencedDecl: Map<String, Any>?,
    val nominatedNamespace: Map<String, String> = emptyMap(),
    val baseInit: Map<String, String> = emptyMap(),
    val fixedUnderlyingType: Map<String, String> = emptyMap(),
    val typeArg: Map<String, String> = emptyMap(),
    val anyInit: Map<String, Any> = emptyMap(),
    val castKind: String?,
    val storageClass: String?,
    @JsonAlias("isVariadic")
    val variadic: Boolean = false,
    val elidable: Boolean = false,
    val virtual: Boolean = false,
    val inline: Boolean = false,
    val argType: Map<String, String> = emptyMap(),
    @JsonAlias("tagUsed")
    val recordType: RecordType? = null,
    val access: AccessSpecifier? = null,
    val bases: List<ClangNodeInheritanceBase> = emptyList(),
    var json: ObjectNode? = null
    ) {
        companion object {
        const val NULL_KIND = "NullNode"

        fun createNullNode() : ClangNode {
            return ClangNode(
                id = null,
                kind = NULL_KIND,
                name = null,
                isArrow = null,
                opcode = null,
                range = null,
                referencedDecl = null,
                value = null,
                castKind = null,
                storageClass = null,
            )
        }
    }

    fun kindChildren(kind: String): List<ClangNode> {
        return inner.filter { it.kind == kind }
    }

    fun kindChild(kind: String): ClangNode {
        return kindChildren(kind).first()
    }

    fun kindChildOrNull(kind: String): ClangNode? {
        return kindChildren(kind).firstOrNull()
    }

    fun depthFirstChild(vararg kind: String): ClangNode? {
        inner.forEach { child ->
            if (child.kind in kind) {
                return child
            }
            val recursive = child.depthFirstChild(*kind)
            if (recursive != null) {
                return recursive
            }
        }
        return null
    }

    fun extractType(offset: Int): TypeInfo {
        val qualType = type.getValue("qualType")
        if (qualType.contains("lambda")) {
            return TypeInfo("auto", emptyList(), ClangToolTree(this, offset))
        }
        val droppedModifiers = Regex("[a-zA-Z0-9_:\\s]+").find(
                listOfNotNull(storageClass, qualType).joinToString(" ")
            )!!.value.trim().split(" ")
            .dropLastWhile { it.endsWith("::") }
        //val parts = listOfNotNull(storageClass) + type.getValue("qualType").split(" ").map { it.trim() }
        // Specifiers are part before typename (last actual word). Type possibly trailed by * or & (possibly multiple).
        // When provided type is of a function, the return type should be returned, but parameter types are included inside parenthesis
        // When provided type is of an array, the size of the array is included after the type name
        //val droppedModifiers = parts.dropLastWhile { it.contains("*") || it.contains("&") || it.contains("(") || it.contains("[") }
        val typeName = droppedModifiers.last().skipQualifier()
        val specifiers = droppedModifiers.dropLast(1).filter { it !in listOf("struct") }.toMutableList()
        if (specifiers.any { !TypeInfo.LEGAL_SPECIFIERS.contains(it) }) {
            println("(Clang) Unrecognized modifier(s) ${specifiers.filterNot { TypeInfo.LEGAL_SPECIFIERS.contains(it) }.joinToString()}")
        }
        if (virtual) {
            specifiers.add("virtual")
        }
        if (inline) {
            specifiers.add("inline")
        }

        return TypeInfo(typeName, specifiers, ClangToolTree(this, offset))
    }

    fun firstKnownLineNum(): Int?  {
        if (this.range != null && this.range.begin.line != null) {
            return this.range.begin.line
        }
        inner.forEach { child ->
            val line = child.firstKnownLineNum()
            if (line != null) {
                return line
            }
        }
        return null
    }

}
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClangNodeRange(val begin: ClangNodeLocation, val end: ClangNodeLocation)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClangNodeLocationInclusion(val file: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClangNodeLocation(val offset: Int, val line: Int?, val col: Int, val tokLen: Int, val includedFrom: ClangNodeLocationInclusion?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClangNodeInheritanceBase(val access: AccessSpecifier, val type: Map<String, String>, val writtenAccess: String, val isVirtual: Boolean = false)

fun String.skipQualifier(): String {
    // Skips qualifier(s) and strips possibly present template-brackets (and contents)
    // Test string: apache::thrift::transport::TVirtualTransport<apache::thrift::transport::TFDTransport, apache::thrift::transport::TTransportDefaults>
    // Should yield TVirtualTransport in group 5
    val match = Regex("((([A-Za-z0-9_]+)|([A-Za-z0-9_]+<[A-Za-z0-9,_]+>))::)*([A-Za-z0-9_\\s*+/\\-]+)(<[A-Z-az0-9_<>,]+>)?").find(this) ?:
        throw IllegalAccessException("No qualifier within this String")
    return match.groups[5]!!.value
}