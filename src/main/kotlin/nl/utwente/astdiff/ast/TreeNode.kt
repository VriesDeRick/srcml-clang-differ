package nl.utwente.astdiff.ast

import nl.utwente.astdiff.CompareReport

abstract class TreeNode(val toolTree: ToolTree, providedTrees: List<TreeNode?>, val props: List<Any?> = emptyList()) {

    val subTrees: List<TreeNode> = providedTrees.filterNotNull().filter { it !is OmittedNode }

    fun compareWith(other: TreeNode): CompareReport {
        // Check other properties beside subtrees
        val internal = compareInternalProps(other)
        // Check subtrees identical
        val subTreesReport = compareSubtrees(other)
        // Modify report if only not equal due to internal props
        return subTreesReport.copy(equalProps = internal)
    }

    protected open fun compareInternalProps(other: TreeNode): Boolean {
        if (this::class != other::class) {
            return false
        }
        if (props.size != other.props.size) {
            return false
        }
        if (props.isEmpty() && subTrees.size != other.subTrees.size) {
            return false
        }
        for (i in props.indices) {
            val thisProp = props[i]
            val otherProp = other.props[i]
            if (thisProp != otherProp) {
                return false
            }
        }
        return true
    }

    protected open fun compareSubtrees(other: TreeNode): CompareReport {
        val ownTrees = this.subTrees.toMutableList()
        val ownIterator = ownTrees.iterator()
        val ownMismatches = mutableListOf<TreeNode>()
        val otherTrees = other.subTrees.toMutableList()
        val reports = mutableListOf<CompareReport>()
        var subTreesMatch = CompareReport.MatchKind.FULL
        outer@while (ownIterator.hasNext()) {
            val ownTree = ownIterator.next()
            ownIterator.remove()
            var bestMatch: CompareReport? = null
            inner@for (otherTree in otherTrees) {
                if (bestMatch?.kind == CompareReport.MatchKind.FULL) {
                    break@inner
                }
                if (bestMatch != null) {
                    subTreesMatch = subTreesMatch.worstOf(CompareReport.MatchKind.ORDER_SWAPPED)
                }
                val propsEqual = ownTree.compareInternalProps(otherTree)
                if (propsEqual) {
                    val report = ownTree.compareWith(otherTree)
                    if (bestMatch == null || bestMatch.kind < report.kind) {
                        bestMatch = report
                    }
                }
            }
            if (bestMatch == null) {
                // If we came here, we couldn't find a match for ownTree within otherTrees
                ownMismatches.add(ownTree)
            } else {
                val success = otherTrees.remove(bestMatch.secondTree)
                if (!success) {
                    println("HELP")
                }
                reports.add(bestMatch)
                if (bestMatch.kind < CompareReport.MatchKind.FULL) {
                    subTreesMatch = subTreesMatch.worstOf(CompareReport.MatchKind.CHILD_ERROR)
                }
            }

        }
        // Try to do some "fuzzy" matching by seeing if skipping a node results in a match
        var firstTrees = ownMismatches
        var secondTrees = otherTrees
        for (ignored in (0..1)) {
            val firstIterator = firstTrees.iterator()
            outer@while (firstIterator.hasNext()) {
                val skippedNode = firstIterator.next()
                var used = false
                middle@for (newParent in skippedNode.subTrees) {
                    val secondIterator = secondTrees.iterator()
                    inner@while (secondIterator.hasNext()) {
                        val possibleMatch = secondIterator.next()
                        val report = newParent.compareWith(possibleMatch)
                        if (report.equalProps && report.kind > CompareReport.MatchKind.UNMATCHED_SUBTREE) {
                            // Add this "match" to reports
                            reports.add(report.copy(
                                skippedNode = skippedNode
                            ))
                            // Remove both trees from existing lists
                            used = true
                            secondIterator.remove()
                            continue@middle // Try to match next previously unmatched tree
                        }
                    }
                }
                if (used) {
                    firstIterator.remove()
                }
            }

            val temp = firstTrees
            firstTrees = secondTrees
            secondTrees = temp
        }
        // If either ownMismatches or otherTrees still contains nodes, produce a failing report
        if (ownMismatches.isNotEmpty() || otherTrees.isNotEmpty()) {
            subTreesMatch = subTreesMatch.worstOf(CompareReport.MatchKind.UNMATCHED_SUBTREE)
        }
        return CompareReport(true, subTreesMatch,
            reports, ownMismatches, otherTrees, this, other)
    }

}