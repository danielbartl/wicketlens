package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketNestedContextTest : WicketProjectPsiTestCase() {
    private fun nested(members: String = "") = myFixture.addFileToProject("src/main/java/example/Outer.java", """
        package example;
        public class Outer extends org.apache.wicket.markup.html.WebPage {
            public Outer() { add(new org.apache.wicket.markup.html.basic.Label("outer")); }
            public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                public Fields(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("field")); }
            }
            $members
        }
    """) as PsiJavaFile

    private fun fields(java: PsiJavaFile) = java.classes.single().innerClasses.single { it.name == "Fields" }
    private fun markup(name: String = "Outer\$Fields", id: String = "field") = companion(name, "<wicket:panel><b wicket:id='$id'></b></wicket:panel>")
    private fun matches(r: PageReport) = r.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
    private fun attribute(file: PsiFile, id: String) = PsiTreeUtil.findChildrenOfType(file, XmlAttributeValue::class.java).single { it.value == id }

    fun testNamedMemberPairsWithBinaryResourceAndAnalyzesItsOwnScope() {
        val java = nested(); companion("Outer", "<span wicket:id='outer'></span>"); val html = markup()
        val type = fields(java)
        assertEquals(html.virtualFile.path, WicketPageResolver().resolve(type).markupPath)
        val r = WicketPageAnalyzer().analyze(type)!!
        assertEquals("example.Outer.Fields", r.qualifiedClassName)
        assertEquals(listOf("field"), matches(r).map { f -> r.nodes.single { it.nodeId == f.javaNodeId }.wicketId })
        assertEquals(setOf("example.Outer", "example.Outer.Fields"), WicketEditorAnalysis.pages(java).map { it.report.qualifiedClassName }.toSet())
        assertEquals("example.Outer.Fields", WicketEditorAnalysis.pages(html).single().report.qualifiedClassName)
        assertEquals(html.virtualFile.path, WicketPageSelection().analyze(project, java.virtualFile.url, type.textOffset).markupPath)
        val literal = PsiTreeUtil.findChildrenOfType(type, PsiLiteralExpression::class.java).single { it.value == "field" }
        assertEquals(attribute(html, "field"), literal.references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(literal, attribute(html, "field").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertNotNull(WicketIdRenameTarget.at(html, attribute(html, "field").textOffset + 1))
    }

    fun testNestedCompanionDoesNotBorrowOuterOrShortNameResources() {
        val java = nested(); companion("Outer"); companion("Fields")
        assertEquals(MarkupResolutionStatus.MISSING, WicketPageResolver().resolve(fields(java)).status)
    }

    fun testDeepMemberBinaryNameAndVariantRoundTrip() {
        val java = nested("public static class Group { public static class Deep extends org.apache.wicket.markup.html.panel.Panel { public Deep(String id) { super(id); } } }")
        val type = java.classes.single().innerClasses.single { it.name == "Group" }.innerClasses.single()
        val html = markup("Outer\$Group\$Deep_fr")
        val target = WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0)
        assertEquals("example.Outer.Group.Deep", target.qualifiedClassName)
        assertEquals("_fr", target.markupSuffix)
        assertEquals(html.virtualFile.path, WicketPageResolver().resolve(type, "_fr").markupPath)
    }

    fun testNonstaticNamedMemberHasStableResourceIdentity() {
        val java = nested("public class Inner extends org.apache.wicket.markup.html.panel.Panel { public Inner(String id) { super(id); } }")
        val html = markup("Outer\$Inner")
        assertEquals(html.virtualFile.path, WicketPageResolver().resolve(java.classes.single().innerClasses.single { it.name == "Inner" }).markupPath)
        assertEquals("example.Outer.Inner", WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0).qualifiedClassName)
    }

    fun testAmbiguousNestedCompanionIsNotSelectedInEitherDirection() {
        val java = nested(); val html = markup()
        myFixture.addFileToProject("src/main/java/example/Outer\$Fields.html", html.text)
        assertEquals(MarkupResolutionStatus.AMBIGUOUS, WicketPageResolver().resolve(fields(java)).status)
        assertThrows(AnalysisUnavailable::class.java) { WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0) }
    }

    fun testBinaryNameCollisionWithATopLevelDollarNameDoesNotChooseAClass() {
        nested()
        myFixture.addFileToProject("src/main/java/example/Outer\$Fields.java", "package example; public class Outer\$Fields extends org.apache.wicket.markup.html.panel.Panel { public Outer\$Fields(String id) { super(id); } }")
        val html = markup()
        assertThrows(AnalysisUnavailable::class.java) { WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0) }
    }

    private fun callers(): List<PsiFile> {
        val java = myFixture.addFileToProject("src/main/java/example/Shell.java", """
            package example;
            public class Shell {
                public static final class Box extends org.apache.wicket.markup.html.panel.Panel {
                    public Box(String id, java.util.function.Function<String, org.apache.wicket.Component> factory) {
                        super(id); add(factory.apply("child"));
                    }
                }
            }
        """)
        val html = companion("Shell\$Box", "<wicket:panel><b wicket:id='child'></b></wicket:panel>")
        val a = pageFile("PageA", "add(new Shell.Box(\"left\", id -> new Label(\"child\"))); add(new Shell.Box(\"right\", id -> new Label(\"child\")));")
        companion("PageA", "<div wicket:id='left'></div><div wicket:id='right'></div>")
        val b = pageFile("PageB", "add(new Shell.Box(\"box\", id -> new Label(\"child\")));")
        companion("PageB", "<div wicket:id='box'></div>")
        return listOf(java, html, a, b)
    }

    fun testOwnedNestedResourceNavigatesToDistinctProvedCallerOrigins() {
        val files = callers(); val value = attribute(files[1], "child")
        val refs = value.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).map { it.element }
        assertEquals(3, refs.size)
        assertEquals(setOf(files[2], files[3]), refs.map { it!!.containingFile }.toSet())
        assertEquals(setOf("example.Shell.Box", "example.PageA", "example.PageB"), WicketEditorAnalysis.navigationPages(files[1]).map { it.report.qualifiedClassName }.toSet())
        assertNull(WicketIdPsi.pair(value))
        assertNull(WicketIdRenameTarget.at(files[1], value.textOffset + 1))
    }

    fun testOptionalMcpDiscoveryReusesTheSameBindingsWithoutChangingDefaultStructure() {
        val files = callers(); val query = WicketPageAnalysisRequest()
        val text = files.map { it.text }; val stamps = files.map { it.modificationStamp }
        val original = JsonParser.parseString(query.render(query.capture(project, "example.Shell.Box"))).asJsonObject
        assertFalse(original.has("navigationContexts"))
        val snapshot = query.capture(project, "example.Shell.Box", includeNavigationContexts = true)
        val response = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(original["analysis"], response["analysis"])
        val contexts = response["navigationContexts"].asJsonObject
        assertTrue(contexts["discoveryComplete"].asBoolean)
        val callers = contexts["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString == "CALLER" }
        assertEquals(2, callers.size)
        assertEquals(3, callers.sumOf { it.asJsonObject["bindings"].asJsonArray.size() })
        assertEquals(setOf("left:child", "right:child", "box:child"), callers.flatMap { it.asJsonObject["bindings"].asJsonArray.map { b -> b.asJsonObject["componentPath"].asString } }.toSet())
        assertTrue(snapshot.version.sources.any { it.path == files[2].virtualFile.path })
        query.requireCurrent(project, snapshot)
        assertEquals(text, files.map { it.text }); assertEquals(stamps, files.map { it.modificationStamp })
    }

    fun testUnsavedCallerMutationInvalidatesNativeAndMcpContexts() {
        val files = callers(); val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.Shell.Box", includeNavigationContexts = true)
        assertEquals(3, attribute(files[1], "child").references.filterIsInstance<WicketIdReference>().single().multiResolve(false).size)
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) { manager.getDocument(files[2])!!.setText(files[2].text.replace("id -> new Label(\"child\")", "id -> { removeAll(); return new Label(\"child\"); }")) }
        try { query.requireCurrent(project, snapshot); fail("Expected stale snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        manager.commitAllDocuments()
        val targets = attribute(files[1], "child").references.filterIsInstance<WicketIdReference>().single().multiResolve(false)
        assertEquals(1, targets.size)
        assertEquals(files[3], targets.single().element!!.containingFile)
    }

    fun testDiscoveryBoundsKeepGraphFactsAndMarkCallerCoveragePartial() {
        val files = callers(); val type = (files[0] as PsiJavaFile).classes.single().innerClasses.single()
        val report = WicketPageAnalyzer().analyze(type)!!
        val nav = WicketNavigationContextsAnalyzer(WicketNavigationContextsAnalyzer.Limits(references = 0)).analyze(type, report)
        assertFalse(nav.complete)
        assertTrue(nav.diagnostics.any { it.contains("bound") })
        val query = WicketPageAnalysisRequest(); val snapshot = query.capture(project, "example.Shell.Box").copy(navigationContexts = nav)
        val response = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertTrue(response["truncated"].asBoolean)
        assertEquals(JsonParser.parseString(query.render(query.capture(project, "example.Shell.Box"))).asJsonObject["analysis"], response["analysis"])
    }

    fun testContextDiscoveryPropagatesCancellation() {
        val files = callers(); val type = (files[0] as PsiJavaFile).classes.single().innerClasses.single()
        val report = WicketPageAnalyzer().analyze(type)!!
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        try {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketNavigationContextsAnalyzer().analyze(type, report)
            }, indicator)
            fail("Expected cancellation")
        } catch (_: com.intellij.openapi.progress.ProcessCanceledException) { }
    }

    fun testContextMetadataRetainsTheSharedUtf8ResponseLimit() {
        callers(); val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.Shell.Box", includeNavigationContexts = true)
        val huge = snapshot.copy(navigationContexts = snapshot.navigationContexts!!.copy(diagnostics = listOf("界".repeat(180000))))
        try { query.render(huge); fail("Expected response bound") }
        catch (e: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", e.code) }
    }

    fun testInheritedFragmentResourceNavigatesToBothConcreteSourceContexts() {
        val base = myFixture.addFileToProject("src/main/java/example/Base.java", """
            package example; public abstract class Base extends org.apache.wicket.markup.html.WebPage {
                public Base() { add(content()); }
                protected abstract org.apache.wicket.Component content();
            }
        """)
        val html = companion("Base", "<div wicket:id='slot'></div><wicket:fragment wicket:id='shared'><b wicket:id='child'></b></wicket:fragment>")
        val children = (1..2).map { number -> myFixture.addFileToProject("src/main/java/example/Child$number.java", """
            package example; public final class Child$number extends Base {
                @Override protected org.apache.wicket.Component content() {
                    org.apache.wicket.markup.html.panel.Fragment f = new org.apache.wicket.markup.html.panel.Fragment("slot", "shared", this);
                    f.add(new org.apache.wicket.markup.html.basic.Label("child")); return f;
                }
            }
        """) }
        val targets = attribute(html, "child").references.filterIsInstance<WicketIdReference>().single().multiResolve(false).map { it.element!!.containingFile }.toSet()
        assertEquals(children.toSet(), targets)
        val definitions = attribute(html, "shared").references.filterIsInstance<WicketIdReference>().single().multiResolve(false).map { it.element!!.containingFile }.toSet()
        assertEquals(children.toSet(), definitions)
        val type = (base as PsiJavaFile).classes.single()
        val contexts = WicketNavigationContextsAnalyzer().analyze(type, WicketPageAnalyzer().analyze(type)!!)
        assertTrue(contexts.complete)
        assertEquals(2, contexts.contexts.count { it.relationship == "INHERITED" })
        assertNull(WicketIdRenameTarget.at(html, attribute(html, "child").textOffset + 1))
    }

    fun testTestSourceCallersAreNotMixedIntoMainSourceContexts() {
        val files = callers()
        com.intellij.testFramework.PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        myFixture.addFileToProject("src/test/java/example/TestCaller.java", """
            package example; public class TestCaller extends org.apache.wicket.markup.html.WebPage {
                public TestCaller() { add(new Shell.Box("box", id -> new org.apache.wicket.markup.html.basic.Label("child"))); }
            }
        """)
        myFixture.addFileToProject("src/test/java/example/TestCaller.html", "<div wicket:id='box'></div>")
        val snapshot = WicketPageAnalysisRequest().capture(project, "example.Shell.Box", includeNavigationContexts = true)
        assertTrue(snapshot.navigationContexts!!.contexts.none { it.report.qualifiedClassName == "example.TestCaller" })
        assertEquals(3, attribute(files[1], "child").references.filterIsInstance<WicketIdReference>().single().multiResolve(false).size)
    }

    fun testLocalClassesDoNotAcquireANamedMemberCompanion() {
        val java = myFixture.addFileToProject("src/main/java/example/Local.java", """
            package example; public class Local { void make() { class Fields extends org.apache.wicket.markup.html.panel.Panel { Fields(String id) { super(id); } } } }
        """) as PsiJavaFile
        markup("Local\$Fields")
        val local = PsiTreeUtil.findChildrenOfType(java, PsiClass::class.java).single { it.name == "Fields" }
        assertEquals(MarkupResolutionStatus.UNSUPPORTED, WicketPageResolver().resolve(local).status)
    }

    fun testSelectedNestedVariantKeepsItsOwnNavigationContext() {
        val java = nested(); markup()
        val french = markup("Outer\$Fields_fr")
        val entry = WicketEditorAnalysis.pages(french).single()
        assertEquals("_fr", entry.report.markupSuffix)
        val nav = WicketNavigationContextsAnalyzer().analyze(fields(java), entry.report)
        assertFalse(nav.complete)
        assertEquals(listOf("_fr"), nav.contexts.map { it.report.markupSuffix })
        assertEquals(french.virtualFile.path, WicketEditorAnalysis.navigationPages(french).single().report.markupPath)
    }

    fun testReverseLookupWithholdsAResultWhenNamedClassDiscoveryIsExhausted() {
        val members = (1..256).joinToString(" ") { "static class Other$it {}" }
        nested(members)
        val html = markup()
        assertThrows(AnalysisUnavailable::class.java) { WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0) }
    }

    fun testContextGraphsRetainThePerGraphResponseLimit() {
        callers(); val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.Shell.Box", includeNavigationContexts = true)
        val entry = snapshot.navigationContexts!!.contexts.first()
        val hugeReport = entry.report.copy(nodes = List(5001) { entry.report.nodes.first() })
        val huge = snapshot.copy(navigationContexts = snapshot.navigationContexts.copy(contexts = listOf(entry.copy(report = hugeReport))))
        try { query.render(huge); fail("Expected graph bound") }
        catch (e: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", e.code) }
    }

    fun testAClassReferenceWithoutAProvedScopeRemainsAnExplicitCandidate() {
        callers()
        myFixture.addFileToProject("src/main/java/example/Unused.java", """
            package example; public class Unused extends org.apache.wicket.markup.html.WebPage {
                private Shell.Box unused;
            }
        """)
        companion("Unused", "<html></html>")
        val snapshot = WicketPageAnalysisRequest().capture(project, "example.Shell.Box", includeNavigationContexts = true)
        val candidate = snapshot.navigationContexts!!.contexts.single { it.report.qualifiedClassName == "example.Unused" }
        assertEquals("CANDIDATE", candidate.relationship)
        assertTrue(candidate.scopeNodeIds.isEmpty() && candidate.bindingNodeIds.isEmpty())
    }

    fun testIdArgumentsProvedOnlyThroughCallersNavigateInBothDirections() {
        val files = callers()
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            for (file in files.drop(2)) manager.getDocument(file)!!.setText(file.text.replace("new Label(\"child\")", "new Label(id)"))
        }
        manager.commitAllDocuments()
        val origin = PsiTreeUtil.findChildrenOfType(files[0], PsiLiteralExpression::class.java).single { it.value == "child" }
        val html = attribute(files[1], "child")
        assertNull(WicketIdPsi.javaId(origin))
        assertEquals(origin, html.references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(html, origin.references.filterIsInstance<WicketIdReference>().single().resolve())
        assertNull(WicketIdPsi.pair(origin))
        assertNull(WicketIdRenameTarget.at(files[0], origin.textOffset + 1))
    }
}
