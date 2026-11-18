package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.*

class WicketFragmentTest : WicketProjectPsiTestCase() {
    private fun report(body: String, html: String, members: String = ""): PageReport {
        val file = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.panel.Fragment;
            public class ExamplePage extends WebPage { public ExamplePage() { $body } $members }
        """) as PsiJavaFile
        companion(html = html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun matches(report: PageReport) = report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }

    fun testDefinitionChildrenAreProjectedIntoTheAttachmentScope() {
        val r = report("Fragment f = new Fragment(\"slot\", \"content\", this); f.add(new Label(\"title\")); add(f);", """
            <div wicket:id="slot">preview</div>
            <wicket:fragment wicket:id="content"><strong wicket:id="title"></strong></wicket:fragment>
        """)
        assertEquals(r.toString(), 2, matches(r).size)
        val fragment = r.nodes.single { it.wicketId == "slot" }
        assertEquals("content", fragment.fragment!!.definitionId)
        assertEquals(r.rootNodeId, fragment.fragment.providerNodeId)
        assertNotNull(fragment.fragment.definitionLocation)
        assertFalse(r.markup!!.elements.single { it.tagName == "wicket:fragment" }.participatesInComponentTree)
        assertFalse(r.nodes.any { it.wicketId == "content" })
        assertEquals(fragment.nodeId, r.nodes.single { it.wicketId == "title" }.parentNodeId)
    }

    fun testRepeatedUseHasSeparateContextsAndOriginalSourceLocations() {
        val r = report("""
            Fragment a = new Fragment("first", "shared", this); a.add(new Label("title")); add(a);
            Fragment b = new Fragment("second", "shared", this); b.add(new Label("title")); add(b);
        """, "<div wicket:id='first'></div><div wicket:id='second'></div><wicket:fragment wicket:id='shared'><b wicket:id='title'></b></wicket:fragment>")
        assertEquals(r.toString(), 4, matches(r).size)
        val children = r.nodes.filter { it.wicketId == "title" }
        assertEquals(1, children.map { it.markupLocation }.distinct().size)
        assertEquals(2, matches(r).filter { it.javaNodeId in children.map { n -> n.nodeId } }.map { it.markupNodeId }.distinct().size)
        assertTrue(children.all { !it.idRenameSafe })
    }

    fun testUnusedDefinitionDoesNotCreateMarkupOnlyComponents() {
        val r = report("add(new Label(\"plain\"));", "<span wicket:id='plain'></span><wicket:fragment wicket:id='unused'><b wicket:id='missing'></b></wicket:fragment>")
        assertEquals(1, r.matching!!.findings.size)
        assertEquals(1, matches(r).size)
    }

    fun testDuplicateDefinitionsDoNotGuessChildren() {
        val r = report("Fragment f = new Fragment(\"slot\", \"content\", this); f.add(new Label(\"title\")); add(f);", """
            <div wicket:id='slot'></div>
            <wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>
            <wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>
        """)
        assertEquals(1, matches(r).size)
        assertNull(r.fragmentUses.single().definitionLocation)
        assertTrue(r.nodes.single { it.wicketId == "title" }.confidence == Confidence.UNRESOLVED)
    }

    fun testUninvokedFactoryHasDefinitionProvenanceWithoutAnInventedInstance() {
        val r = report("add(new Label(\"plain\"));", "<span wicket:id='plain'></span><wicket:fragment wicket:id='row'><b wicket:id='title'></b></wicket:fragment>", """
            private Fragment row(String id) { Fragment f = new Fragment(id, "row", this); f.add(new Label("title")); return f; }
        """)
        assertEquals(1, matches(r).size)
        val use = r.fragmentUses.single()
        assertEquals("row", use.definitionId)
        assertNotNull(use.definitionLocation)
        assertTrue(use.instanceNodeIds.isEmpty())
    }
    fun testInheritedDefinitionsRetainTheirActualResourceAndHookLocations() {
        myFixture.addFileToProject("src/main/java/example/Base.java", """
            package example; public class Base extends org.apache.wicket.markup.html.WebPage {
                @Override protected void onInitialize() { super.onInitialize();
                    org.apache.wicket.markup.html.panel.Fragment f = new org.apache.wicket.markup.html.panel.Fragment("slot", "baseContent", this);
                    f.add(new org.apache.wicket.markup.html.basic.Label("title")); add(f);
                }
            }
        """)
        val baseHtml = myFixture.addFileToProject("src/main/resources/example/Base.html", "<div wicket:id='slot'></div><wicket:child/><wicket:fragment wicket:id='baseContent'><b wicket:id='title'></b></wicket:fragment>")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", "package example; public class ExamplePage extends Base {}") as PsiJavaFile
        companion(html = "<wicket:extend><span>Page</span></wicket:extend>")
        val r = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(r.toString(), 2, matches(r).size)
        assertEquals(baseHtml.virtualFile.path, r.fragmentUses.single().definitionLocation!!.file)
        assertTrue(r.markup!!.sourceFiles.contains(baseHtml.virtualFile.path))
    }

    fun testNestedInstancesAreProjectedInTheirActualAttachmentContexts() {
        val r = report("""
            Fragment a = new Fragment("slot", "outer", this);
            Fragment b = new Fragment("inside", "inner", this); b.add(new Label("title"));
            a.add(b); add(a);
        """, "<div wicket:id='slot'></div><wicket:fragment wicket:id='outer'><div wicket:id='inside'></div></wicket:fragment><wicket:fragment wicket:id='inner'><b wicket:id='title'></b></wicket:fragment>")
        assertEquals(r.toString(), 3, matches(r).size)
    }

    fun testAnUnprovedProviderDoesNotBorrowTheRootDefinition() {
        val r = report("""
            org.apache.wicket.markup.html.WebMarkupContainer provider = new org.apache.wicket.markup.html.WebMarkupContainer("provider");
            Fragment f = new Fragment("slot", "content", provider); f.add(new Label("title")); add(provider, f);
        """, "<div wicket:id='provider'></div><div wicket:id='slot'></div><wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>")
        assertEquals(2, matches(r).size)
        assertNull(r.fragmentUses.single().definitionLocation)
    }

    fun testUnknownRootCallCannotPromoteFragmentChildren() {
        val r = report("""
            Fragment f = new Fragment("slot", getClass().getSimpleName(), this); f.add(new Label("title")); add(f, new Label("plain"));
        """, "<div wicket:id='slot'></div><span wicket:id='plain'></span><wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>")
        assertTrue(matches(r).none { it.javaNodeId == r.nodes.singleOrNull { n -> n.wicketId == "title" }?.nodeId })
    }

    fun testCustomProviderMarkupLookupIsNotTreatedAsConventional() {
        val r = report("Fragment f = new Fragment(\"slot\", \"content\", this); f.add(new Label(\"title\")); add(f);",
            "<div wicket:id='slot'></div><wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>",
            "@Override public org.apache.wicket.markup.IMarkupFragment getMarkup() { return null; }")
        assertNull(r.fragmentUses.single().definitionLocation)
        assertTrue(matches(r).none { it.javaNodeId == r.nodes.single { n -> n.wicketId == "title" }.nodeId })
    }

    fun testUnsupportedDefinitionMarkupDoesNotInvalidateAnIndependentSibling() {
        val r = report("Fragment f = new Fragment(\"slot\", \"content\", this); f.add(new Label(\"title\")); add(f, new Label(\"plain\"));",
            "<div wicket:id='slot'></div><span wicket:id='plain'></span><wicket:fragment wicket:id='content'><wicket:container><b wicket:id='title'></b></wicket:container></wicket:fragment>")
        assertEquals(2, matches(r).size)
    }

    fun testAttachmentPreviewIsNotConfusedWithTheDefinitionBody() {
        val r = report("Fragment f = new Fragment(\"slot\", \"content\", this); f.add(new Label(\"title\")); add(f);",
            "<div wicket:id='slot'><b wicket:id='wrong'></b></div><wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>")
        assertEquals(2, matches(r).size)
        assertFalse(r.matching!!.findings.any { it.markupLocation == r.markup!!.elements.single { e -> e.wicketId == "wrong" }.location })
    }

    fun testCancellationDoesNotReturnFragmentFacts() {
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        val r = report("add(new Label(\"x\"));", "<span wicket:id='x'></span>")
        val java = myFixture.findFileInTempDir("src/main/java/example/ExamplePage.java")
            .let { com.intellij.psi.PsiManager.getInstance(project).findFile(it) as PsiJavaFile }
        try {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess({
                indicator.cancel()
                dev.wicketlens.analysis.WicketFragmentAnalyzer().analyze(java.classes.single(), r)
            }, indicator)
            fail("Expected cancellation")
        } catch (_: com.intellij.openapi.progress.ProcessCanceledException) { }
    }

    fun testFourArgumentConstructorAndProviderAliasUseTheSameDefinitionScope() {
        val r = report("""
            org.apache.wicket.MarkupContainer provider = this;
            Fragment f = new Fragment("slot", "content", provider, org.apache.wicket.model.Model.of("data"));
            f.add(new Label("title")); add(f);
        """, "<div wicket:id='slot'></div><wicket:fragment wicket:id='content'><b wicket:id='title'></b></wicket:fragment>")
        assertEquals(r.toString(), 2, matches(r).size)
    }

    fun testDiscoveryLimitWithholdsDefinitionLookups() {
        val calls = (0..1025).joinToString(" ") { "new Fragment(\"slot\", \"content\", this);" }
        val r = report("add(new Label(\"plain\"));", "<span wicket:id='plain'></span><wicket:fragment wicket:id='content'></wicket:fragment>", "private void unused() { $calls }")
        assertTrue(r.fragmentUses.isNotEmpty())
        assertTrue(r.fragmentUses.size <= 1024)
        assertTrue(r.fragmentUses.all { it.definitionLocation == null && it.diagnostics.any { d -> d.message.contains("bound") } })
    }

    fun testProjectionDepthLimitDoesNotPromoteDeeperChildren() {
        val allocations = (0..17).joinToString(" ") { i -> "Fragment f$i = new Fragment(\"${if (i == 0) "slot" else "inside"}\", \"d$i\", this);" }
        val attachments = (1..17).joinToString(" ") { i -> "f${i-1}.add(f$i);" }
        val definitions = (0..17).joinToString(" ") { i -> "<wicket:fragment wicket:id='d$i'>${if (i == 17) "<b wicket:id='title'></b>" else "<div wicket:id='inside'></div>"}</wicket:fragment>" }
        val r = report("$allocations $attachments f17.add(new Label(\"title\")); add(f0);", "<div wicket:id='slot'></div>$definitions")
        assertTrue(r.nodes.flatMap { it.diagnostics }.any { it.message.contains("16-level") })
        assertTrue(matches(r).none { it.javaNodeId == r.nodes.single { n -> n.wicketId == "title" }.nodeId })
    }

    fun testProjectionElementLimitKeepsTheAttachmentAndWithholdsContents() {
        val body = (0..4096).joinToString("") { "<span>text</span>" }
        val r = report("add(new Fragment(\"slot\", \"content\", this));", "<div wicket:id='slot'></div><wicket:fragment wicket:id='content'>$body</wicket:fragment>")
        assertEquals(1, matches(r).size)
        assertTrue(r.nodes.flatMap { it.diagnostics }.any { it.message.contains("4096-element") })
        assertTrue(r.markup!!.elements.size < 4200)
    }

    fun testCustomFragmentSubclassesDoNotMatchTheCallerPreviewAsTheirContents() {
        myFixture.addFileToProject("src/main/java/example/CustomFragment.java", """
            package example; public class CustomFragment extends org.apache.wicket.markup.html.panel.Fragment {
                public CustomFragment(String id) { super(id, "content", null); }
            }
        """)
        val r = report("CustomFragment f = new CustomFragment(\"slot\"); f.add(new Label(\"title\")); add(f);",
            "<div wicket:id='slot'><b wicket:id='title'></b></div><wicket:fragment wicket:id='content'><b wicket:id='other'></b></wicket:fragment>")
        assertEquals(r.toString(), 1, matches(r).size)
        assertTrue(r.nodes.single { it.wicketId == "title" }.confidence == Confidence.UNRESOLVED)
    }

    fun testOneHelperConstructionRetainsDifferentDefinitionContexts() {
        val r = report("add(make(\"first\", \"left\"), make(\"second\", \"right\"));",
            "<div wicket:id='first'></div><div wicket:id='second'></div><wicket:fragment wicket:id='left'><b wicket:id='title'></b></wicket:fragment><wicket:fragment wicket:id='right'><strong wicket:id='title'></strong></wicket:fragment>",
            "private Fragment make(String id, String definition) { Fragment f = new Fragment(id, definition, this); f.add(new Label(\"title\")); return f; }")
        assertEquals(r.toString(), 4, matches(r).size)
        assertEquals(setOf("left", "right"), r.fragmentUses.map { it.definitionId }.toSet())
        assertEquals(1, r.fragmentUses.map { it.javaLocation }.distinct().size)
        for (node in r.nodes.filter { it.fragment != null }) {
            assertEquals(if (node.wicketId == "first") "left" else "right", node.fragment!!.definitionId)
            assertEquals(listOf(node.nodeId), node.fragment.instanceNodeIds)
        }
    }

    fun testEqualDefinitionIdsForwardedThroughOneHelperRetainBothCallerOrigins() {
        val r = report("add(make(\"first\", \"shared\"), make(\"second\", \"shared\"));",
            "<div wicket:id='first'></div><div wicket:id='second'></div><wicket:fragment wicket:id='shared'><b wicket:id='title'></b></wicket:fragment>",
            "private Fragment make(String id, String definition) { Fragment f = new Fragment(id, definition, this); f.add(new Label(\"title\")); return f; }")
        assertEquals(4, matches(r).size)
        assertEquals(2, r.fragmentUses.size)
        assertEquals(2, r.fragmentUses.map { it.definitionIdOrigin }.distinct().size)
        assertTrue(r.fragmentUses.all { it.instanceNodeIds.size == 1 })
        val java = myFixture.findFileInTempDir("src/main/java/example/ExamplePage.java")
            .let { com.intellij.psi.PsiManager.getInstance(project).findFile(it)!! }
        for (use in r.fragmentUses) {
            val literal = com.intellij.psi.util.PsiTreeUtil.getParentOfType(java.findElementAt(use.definitionIdOrigin!!.offset), com.intellij.psi.PsiLiteralExpression::class.java, false)!!
            val reference = literal.references.filterIsInstance<dev.wicketlens.editor.WicketIdReference>().single()
            assertNotNull(reference.resolve())
        }
    }

}
