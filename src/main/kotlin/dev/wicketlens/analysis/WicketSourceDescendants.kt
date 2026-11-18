package dev.wicketlens.analysis

import com.intellij.psi.*

/** Declaration structure for one analysis session. No effect or receiver result is cached. */
internal class WicketSourceDescendants(private val tick: (PsiElement) -> Unit) {
    private val snapshots = mutableMapOf<PsiElement, List<PsiElement>>()

    fun descendants(root: PsiElement): List<PsiElement> {
        tick(root)
        snapshots[root]?.let { return it }
        val elements = ArrayList<PsiElement>()
        val starts = mutableMapOf<PsiElement, Int>()
        val ranges = linkedMapOf<PsiElement, IntRange>()
        val seen = mutableSetOf<PsiElement>()
        root.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiWhiteSpace || element is PsiJavaToken || element is PsiIdentifier || element is PsiComment) return
                if (!seen.add(element)) return
                tick(element)
                snapshots[element]?.let { cached ->
                    elements += element
                    cached.drop(1).forEach { if (seen.add(it)) elements += it }
                    return
                }
                starts[element] = elements.size
                elements += element
                super.visitElement(element)
            }
            override fun elementFinished(element: PsiElement) {
                starts[element]?.let { ranges[element] = it until elements.size }
                super.elementFinished(element)
            }
        })
        // Publish only complete traversals. Subtree views reuse one bounded preorder list.
        ranges.forEach { (element, range) -> snapshots[element] = elements.subList(range.first, range.last + 1) }
        return snapshots[root] ?: emptyList()
    }
}
