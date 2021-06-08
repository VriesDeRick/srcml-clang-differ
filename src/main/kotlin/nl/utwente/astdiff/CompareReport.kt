package nl.utwente.astdiff

import nl.utwente.astdiff.ast.TreeNode
import org.apache.commons.text.StringEscapeUtils
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi

data class CompareReport(val equalProps: Boolean, val kind: MatchKind,
                         val subReports: List<CompareReport>,
                         val firstUnmatchedChildren: List<TreeNode>,
                         val secondUnmatchedChildren: List<TreeNode>,
                         val firstTree: TreeNode, val secondTree: TreeNode,
                         val skippedNode: TreeNode? = null
) {

    companion object {
        private const val format = "|- %s (%s)%n"
    }

    enum class MatchKind {
        UNMATCHED_SUBTREE,
        ORDER_SWAPPED,
        CHILD_ERROR,
        FULL;

        fun worstOf(other: MatchKind) = if (other < this) other else this
    }

    fun asPrettyPrint(indent: Int): String {
        val rootStr = when {
            !equalProps -> formatUnequalPropsRootStr(indent).colored(Ansi.Color.RED)
            skippedNode != null -> formatRootStr(firstTree, indent + 1)
            else -> formatRootStr(firstTree, indent)
        }
        val usedIndent = if (skippedNode == null) indent else indent + 1
        val prefix = if (skippedNode == null) "" else formatSkippedRootStr(indent).colored(Ansi.Color.MAGENTA)

        return prefix + when (kind) {
            MatchKind.FULL -> (rootStr + formatSubReports(usedIndent)).colored(Ansi.Color.GREEN)
            MatchKind.CHILD_ERROR -> (rootStr + formatSubReports(usedIndent)).colored(Ansi.Color.YELLOW)
            MatchKind.ORDER_SWAPPED -> (rootStr + formatSubReports(usedIndent)).colored(Ansi.Color.BLUE)
            MatchKind.UNMATCHED_SUBTREE -> rootStr.colored(Ansi.Color.GREEN) + formatUnequalSubtrees(usedIndent + 1)
        }
    }

    private fun formatSkippedRootStr(indent: Int): String {
        /*
        Example:
        SKIPPED: NodeWithChildren (TOOL1)
            - Subtree1
            - Subtree2
         - MatchedReportN
         */
        val line = System.lineSeparator()
        val skippedHeader = "${formatRootStr(skippedNode!!, indent).removeSuffix(line)} (EXTRA IN ${skippedNode.toolTree.producer.name}, ${skippedNode.toolTree.originalStartLine}):$line"
        val skippedSubtrees = skippedNode.subTrees.filter { it != firstTree }.joinToString("") { formatRootStr(it, indent + 1) }
        return (skippedHeader + skippedSubtrees)
    }

    private fun formatUnequalSubtrees(indent: Int): String {
        /* Example:
        - Report1
        - Report2
        UNMATCHED (TOOL1):
        - Tree1
        - Tree2
        UNMATCHED (TOOL2):
        - Tree3
        - Tree4
         */
        val matchedReports = subReports.joinToString("") { it.asPrettyPrint(indent) }
        val header1 = asIndent(indent) + "UNMATCHED (${firstTree.toolTree.producer.name}, ${firstTree.toolTree.originalStartLine}):" + System.lineSeparator()
        val header2 = asIndent(indent) + "UNMATCHED (${secondTree.toolTree.producer.name}, ${secondTree.toolTree.originalStartLine}):" + System.lineSeparator()

        return matchedReports +
                (header1 + firstUnmatchedChildren.joinToString("") { formatTree(it, indent) }).colored(Ansi.Color.RED) +
                (header2 + secondUnmatchedChildren.joinToString("") { formatTree(it, indent) }).colored(Ansi.Color.RED)
    }

    private fun formatUnequalPropsRootStr(indent: Int): String {
        /*
        Example:
        - Classname (prop1, prop2) (tool1)
        UNEQUAL PROP:
        - Classname (p1, p2) (tool2)
         */
        val firstLine = formatRootStr(firstTree, indent).dropLastWhile { it != ')'} +
                " (${firstTree.toolTree.producer.name})" + System.lineSeparator()
        val lastLine = formatRootStr(secondTree, indent).dropLastWhile { it != ')'} +
                " (${secondTree.toolTree.producer.name})" + System.lineSeparator()
        val middleLine = asIndent(indent) + "UNEQUAL PROPS:" + System.lineSeparator()
        return firstLine + middleLine + lastLine
    }

    private fun formatSubReports(indent: Int): String {
       return subReports.joinToString("") { it.asPrettyPrint(indent + 1) }
    }

    private fun formatTree(tree: TreeNode, indent: Int): String {
        return formatRootStr(tree, indent) + tree.subTrees.joinToString("") { formatTree(it, indent + 1) }
    }

    private fun formatRootStr(tree: TreeNode, indent: Int): String {
        return asIndent(indent) + String.format(format, tree::class.simpleName, tree.props.joinToString { StringEscapeUtils.escapeJava(it.toString()) } )
    }

    private fun asIndent(indent: Int): String {
        return "  ".repeat(indent)
    }
}

private fun String.colored(color: Ansi.Color): String {
    return ansi().fg(color).a(this).reset().toString()
}
