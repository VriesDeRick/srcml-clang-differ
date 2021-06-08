package nl.utwente.astdiff.ast

class AsmStmt(toolTree: ToolTree) : TreeNode(toolTree, emptyList())
class BlockStmt(val stmts: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, stmts)
class BreakStmt(toolTree: ToolTree) : TreeNode(toolTree, emptyList());
class CaseStmt(val expr: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(expr))
// TemporaryStmt purely used for handling Clang tree: should always fail if used for comparisons
class TemporaryCaseStmt(val expr: TreeNode, val stmt: TreeNode?, toolTree: ToolTree) : IncomparableNode(toolTree)
class TemporaryDefaultStmt(val stmt: TreeNode?, toolTree: ToolTree) : IncomparableNode(toolTree)
class ContinueStmt(toolTree: ToolTree) : TreeNode(toolTree, emptyList())
class DeclStmt(val decls: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, decls)
class DefaultStmt(toolTree: ToolTree) : TreeNode(toolTree, emptyList())
class DoWhileStmt(val block: TreeNode, val condition: TreeNode, toolTree: ToolTree) :
        TreeNode(toolTree, listOf(block, condition))
class EmptyStmt(toolTree: ToolTree) : TreeNode(toolTree, emptyList())
// ExprStmt left out
class ForStmt(val init: TreeNode, val cond: TreeNode, val incr: TreeNode, val block: TreeNode, toolTree: ToolTree) :
        TreeNode(toolTree, listOf(init, cond, incr, block))
class ForRangeStmt(val collection: TreeNode, val body: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(collection, body))
// TODO: ForeverStmt if it ever shows up
class GotoStmt(toolTree: ToolTree) : TreeNode(toolTree, emptyList())
class IfStmt(val ifPart: TreeNode, val elsePart: TreeNode?, toolTree: ToolTree) :
        TreeNode(toolTree, listOf(ifPart, elsePart))
class IfPart(val expr: TreeNode, val stmt: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(expr, stmt), emptyList())

class LabelStmt(val label: String, val inner: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(inner), listOf(label))
class ReturnStmt(val expr: TreeNode?, toolTree: ToolTree) : TreeNode(toolTree, listOf(expr))
class SwitchStmt(val expr: TreeNode, val block: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(expr, block))

class TryStmt(val stmt: TreeNode, val catches: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, listOf(stmt) + catches)
// exceptionDecl is EmptyNode if (...) is used for catch-clause
class CatchStmt(val exceptionDecl: TreeNode, val stmt: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(exceptionDecl, stmt))
class WhileStmt(val cond: TreeNode, val block: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(cond, block))
