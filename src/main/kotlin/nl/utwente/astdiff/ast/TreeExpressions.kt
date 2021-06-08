package nl.utwente.astdiff.ast

class CallExpr(val target: TreeNode, val args: List<TreeNode>, tool: ToolTree) :
    TreeNode(tool, listOf(target) + args)
class InitListExpr(val exprs: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, exprs)
class RefExpr(val target: String, tool: ToolTree) : TreeNode(tool, emptyList(), listOf(target))
class LambdaExpr(val closureVars: List<TreeNode>, val args: List<TreeNode>, val body: TreeNode, tool: ToolTree) : TreeNode(tool, closureVars + args + listOf(body))
class LiteralExpr(val value: String, tool: ToolTree) : TreeNode(tool, emptyList(), listOf(value))
class Operator(val symbol: String, tool: ToolTree) : TreeNode(tool, emptyList(), listOf(symbol)) {
    companion object {
        val IGNORABLE_SYMBOLS = emptyList<String>() // TODO: Remove if still empty
    }
}
class SizeOfExpr(val exprOrType: TreeNode, tool: ToolTree) : TreeNode(tool, listOf(exprOrType))
class TernaryExpr(val cond: TreeNode, val thenExpr: TreeNode, val elseExpr: TreeNode, tool: ToolTree) :
    TreeNode(tool, listOf(cond, thenExpr, elseExpr))
class ThrowExpr(val expr: TreeNode?, tool: ToolTree) : TreeNode(tool, listOf(expr))
class CastExpr(val kind: CastKind, val expr: TreeNode, tool: ToolTree) : TreeNode(tool, listOf(expr), listOf(kind))
class TypeIdExpr(val typeOrExpr: TreeNode, tool: ToolTree) : TreeNode(tool, listOf(typeOrExpr))

/**
 * Used to encapsulate expressions within array index brackets (e.g. \[expr\]). Result of `a\[b\]` should be the
 * conversion of a, the conversion of b wrapped into ArrayAccessExpr, and both of these outer nodes wrapped into a
 * FlattenedNode
 */
class ArrayAccessExpr(val index: TreeNode, tool: ToolTree) : TreeNode(tool, listOf(index), emptyList())

enum class CastKind {
    STATIC,
    DYNAMIC,
    CONST,
    REINTERPRET
}

