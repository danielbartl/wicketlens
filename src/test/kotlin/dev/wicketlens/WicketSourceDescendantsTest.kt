package dev.wicketlens

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketSourceDescendants

class WicketSourceDescendantsTest : WicketProjectPsiTestCase() {
    private fun sample(path: String = "src/main/java/example/Declarations.java") = myFixture.addFileToProject(path, """
        package example;
        class Declarations {
            Object value;
            void configure() {
                value = new Object();
                Runnable deferred = () -> { value = null; };
                Object nested = new Object() { void change() { value = null; } };
                if (value == null) throw new IllegalArgumentException("unchanged literal");
            }
        }
    """.trimIndent()) as PsiJavaFile
    private fun expected(root: PsiElement): List<PsiElement> {
        val result = linkedSetOf<PsiElement>()
        root.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiWhiteSpace || element is PsiJavaToken || element is PsiIdentifier || element is PsiComment) return
                result += element
                super.visitElement(element)
            }
        })
        return result.toList()
    }
    fun testSemanticSnapshotRetainsNestedWritesCallbacksAndThrowingGuards() {
        val file = sample()
        val result = WicketSourceDescendants {}.descendants(file)
        assertEquals(expected(file), result)
        assertEquals(3, result.filterIsInstance<PsiAssignmentExpression>().size)
        assertEquals(1, result.filterIsInstance<PsiLambdaExpression>().size)
        assertEquals(1, result.filterIsInstance<PsiThrowStatement>().size)
        assertTrue(result.filterIsInstance<PsiLiteralExpression>().any { it.value == "unchanged literal" })
    }
    fun testParentSnapshotServesSubtreesWithoutAnotherSemanticTraversal() {
        val file = sample(); val visited = mutableListOf<PsiElement>()
        val index = WicketSourceDescendants { visited += it }
        index.descendants(file)
        val method = file.classes.single().findMethodsByName("configure", false).single()
        val before = visited.size
        assertEquals(expected(method), index.descendants(method))
        assertEquals(before + 1, visited.size)
    }
    fun testEarlierChildSnapshotIsReusedWhenTheEnclosingFileIsRequested() {
        val file = sample(); val visited = mutableListOf<PsiElement>()
        val index = WicketSourceDescendants { visited += it }
        val method = file.classes.single().findMethodsByName("configure", false).single()
        val child = index.descendants(method)
        val nestedWrite = child.filterIsInstance<PsiAssignmentExpression>().last()
        val before = visited.count { it == nestedWrite }
        assertEquals(expected(file), index.descendants(file))
        assertEquals(before, visited.count { it == nestedWrite })
        assertEquals(expected(method), index.descendants(method))
    }
    fun testRepeatedRootQueriesStillCheckTheCallerDeadline() {
        val file = sample(); var cancelled = false
        val index = WicketSourceDescendants { if (cancelled) throw ProcessCanceledException() }
        index.descendants(file); cancelled = true
        assertThrows(ProcessCanceledException::class.java) { index.descendants(file) }
    }
    fun testRepeatedSubtreeQueriesStillChargeTheCallersBudget() {
        val file = sample(); var remaining = 1000
        val index = WicketSourceDescendants { if (--remaining < 0) throw IllegalStateException("budget") }
        index.descendants(file); remaining = 0
        assertThrows(IllegalStateException::class.java) { index.descendants(file.classes.single()) }
    }
    fun testCancelledTraversalDoesNotPublishPartialSubtrees() {
        val file = sample(); var remaining = 12; var cancel = true; val visited = mutableListOf<PsiElement>()
        val index = WicketSourceDescendants { visited += it; if (cancel && --remaining == 0) throw ProcessCanceledException() }
        assertThrows(ProcessCanceledException::class.java) { index.descendants(file) }
        val first = visited.toList(); cancel = false; visited.clear()
        assertEquals(expected(file), index.descendants(file))
        assertTrue(first.drop(1).all { it in visited })
    }
    fun testSameNamedClassesInDifferentFilesHaveSeparateSnapshots() {
        val first = sample("one/Declarations.java"); val second = sample("two/Declarations.java")
        val index = WicketSourceDescendants {}
        val a = index.descendants(first); val b = index.descendants(second)
        assertTrue(a.none { it in b })
        assertTrue(a.all { it.containingFile == first }); assertTrue(b.all { it.containingFile == second })
    }
    fun testOversizedSemanticSourceStillExceedsTheCallersBudget() {
        val file = myFixture.addFileToProject("src/main/java/example/Large.java", "class Large {void f(){" + "int x=0;".repeat(6000) + "}}")
        var remaining = 20000
        assertThrows(IllegalStateException::class.java) { WicketSourceDescendants { if (--remaining < 0) throw IllegalStateException("budget") }.descendants(file) }
    }
    fun testDocumentationAndPunctuationDoNotConsumeSemanticSteps() {
        val file = myFixture.addFileToProject("src/main/java/example/Trivia.java", "class Trivia {" + "/** declaration comment */\n".repeat(6000) + "void f(){}}")
        var steps = 0
        val result = WicketSourceDescendants { steps++ }.descendants(file)
        assertTrue("$steps", steps < 100)
        assertEquals(expected(file), result)
    }
}
