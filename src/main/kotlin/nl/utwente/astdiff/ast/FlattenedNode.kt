package nl.utwente.astdiff.ast

import nl.utwente.astdiff.CompareReport

class FlattenedNode private constructor(val children: List<TreeNode>, tool: ToolTree) : TreeNode(tool, children) {

    companion object {
        operator fun invoke(childrenNodes: List<TreeNode>, tool: ToolTree): TreeNode {
            // Do "flattening": flatten children into current node where possible and filter ignorable symbols
            val childFlattens = childrenNodes.filterIsInstance<FlattenedNode>()
            val flattened = ((childrenNodes - childFlattens) + childFlattens.map { it.children }.flatten())
                .filter { !(it is Operator && it.symbol in Operator.IGNORABLE_SYMBOLS) }
                .toMutableList()
            // Convert chains of form A::B::v or ::v to just "v"
            var i = 1
            // First catch case of ::v, since loop below skips that
            if (flattened.isNotEmpty()) {
                val first = flattened.first()
                if (first is Operator && first.symbol == "::") {
                    flattened.removeFirst()
                }
            }
            while (i < flattened.size) {
                val previous = flattened[i - 1]
                val current = flattened[i]
                if (current is Operator && current.symbol == "::") {
                    flattened.removeAt(i) // Remove ::-node
                    // Also delete preceding node if it's a "direct" name
                    if (previous is RefExpr) {
                        flattened.removeAt(i - 1)
                    }
                    continue // Don't increment
                }
                i += 1
            }
            return when (flattened.size) {
                0 -> EmptyNode(tool)
                1 -> flattened[0]
                else -> FlattenedNode(flattened, tool)
            }
        }

        fun parenthesised(inner: TreeNode, tool: ToolTree): TreeNode {
            return FlattenedNode.invoke(listOf(Operator("(", tool), inner, Operator(")", tool)), tool)
        }

        fun parenthesised(inner: List<TreeNode>, tool: ToolTree): TreeNode {
            return FlattenedNode.invoke(listOf(Operator("(", tool), Operator(")", tool)) + inner, tool)
        }
    }

    override fun compareSubtrees(other: TreeNode): CompareReport {
        val report = super.compareSubtrees(other)
        val subTreeKind = when (report.kind) {
            CompareReport.MatchKind.ORDER_SWAPPED -> {
            // Old value either FULL or CHILD_ERROR, find out which
                if (report.subReports.all { it.kind == CompareReport.MatchKind.FULL }) CompareReport.MatchKind.FULL else CompareReport.MatchKind.CHILD_ERROR
            }
            else -> report.kind
        }
        return report.copy(kind = subTreeKind)
    }
}