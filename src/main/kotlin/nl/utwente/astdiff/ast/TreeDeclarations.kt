package nl.utwente.astdiff.ast

// Things seen within classes/functions
class ArrayDecl(val type: TypeInfo, val name: String, val init: TreeNode?, toolTree: ToolTree) :
        TreeNode(toolTree, listOf(type, init), listOf(name)) // No size since Clang doesn't parse array size
class FuncDecl(val returnType: TypeInfo, val name: String, val parameters: List<TreeNode>, val properties: Set<FunctionProps>, val body: TreeNode?,
               toolTree: ToolTree, val memberInits: List<TreeNode> = emptyList()) :
    TreeNode(toolTree, listOf(returnType) + parameters + memberInits + listOf(body), listOf(name, properties))
// Parameter name for function can be null when no name exists, e.g. function prototype "void f(int)"
class FuncDeclParam(val type: TypeInfo, val name: String?, tree: ToolTree) : TreeNode(tree, listOf(type), listOf(name))
class CtorMemberInitializer(val name: String, val expr: TreeNode, tree: ToolTree) : TreeNode(tree, listOf(expr), listOf(name))
class VarDecl(val type: TypeInfo, val name: String?, val init: TreeNode?, toolTree: ToolTree) : TreeNode(toolTree, listOf(type, init), listOf(name))

// Declarations of classes/unions/namespaces themselves
class EnumDecl(val name: String?, val underlyingType: String?, val members: List<TreeNode>, tool: ToolTree) :
    TreeNode(tool, members, listOf(name, underlyingType))
class EnumConstant(val name: String, val init: TreeNode?, toolTree: ToolTree) : TreeNode(toolTree, listOf(init), listOf(name))
class FriendDecl(val friend: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, listOf(friend))
class RecordDecl(val type: RecordType, val name: String?, val bases: List<InheritanceBaseRef>, val members: List<TreeNode>, toolTree: ToolTree) :
    TreeNode(toolTree, members, listOf(type, name, bases))
data class InheritanceBaseRef(val name: String, val access: AccessSpecifier?, val isVirtual: Boolean = false)
class SpecifiedAccess(val access: AccessSpecifier, val members: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, members, listOf(access))
class NamespaceDecl(val name: String?, val decls: List<TreeNode>, toolTree: ToolTree) : TreeNode(toolTree, decls, listOf(name))
class UsingDecl(val varOrNamespaceName: String, val isNamespace: Boolean, toolTree: ToolTree) : TreeNode(toolTree, emptyList(), listOf(varOrNamespaceName, isNamespace))
class TemplatedDecl(val parameters: List<TreeNode>, val decl: TreeNode, toolTree: ToolTree) : TreeNode(toolTree, parameters + listOf(decl), emptyList())
class TemplateParameter(val parmType: TypeInfo, val parmName: String?, toolTree: ToolTree) : TreeNode(toolTree, listOf(parmType), listOf(parmName))

enum class RecordType {
    ENUM,
    CLASS,
    UNION,
    STRUCT,
    TYPENAME
}

enum class AccessSpecifier {
    PUBLIC,
    PRIVATE,
    PROTECTED,
}

enum class FunctionProps {
    DESTRUCTOR,
    CONSTRUCTOR,
    VARIADIC,
}
