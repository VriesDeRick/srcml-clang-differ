package nl.utwente.astdiff.ast

import nl.utwente.astdiff.CompareReport
import java.text.DecimalFormat

class TranslationUnit(val elements: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, elements)
class EmptyNode(toolTree: ToolTree) : TreeNode(toolTree, emptyList())
class OmittedNode(toolTree: ToolTree) : TreeNode(toolTree, emptyList())

open class IncomparableNode(toolTree: ToolTree, val reason: String = "") : TreeNode(toolTree, emptyList(), listOf(reason)) {
    override fun compareSubtrees(other: TreeNode): CompareReport {
        return CompareReport(false, CompareReport.MatchKind.FULL, emptyList(), emptyList(), emptyList(), this, other)
    }
}

val floatNumberFormat = DecimalFormat("##0.#####E0")