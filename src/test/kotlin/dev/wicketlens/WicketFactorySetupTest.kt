package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.*

class WicketFactorySetupTest : WicketProjectPsiTestCase() {
    fun testImmediateFactoryReturnsACapturedUnattachedComponentIntoOwnedMarkup() {
        myFixture.addFileToProject("src/main/java/example/Split.java", """
            package example;
            public class Split extends org.apache.wicket.markup.html.panel.Panel {
                public Split(String id, java.util.function.Function<String,org.apache.wicket.Component> factory) {
                    super(id); org.apache.wicket.markup.html.WebMarkupContainer pane = new org.apache.wicket.markup.html.WebMarkupContainer("pane");
                    pane.add(factory.apply("child")); add(pane);
                }
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Split.html", "<wicket:panel><div wicket:id='pane'><span wicket:id='child'></span></div></wicket:panel>")
        val java = pageFile(body = "org.apache.wicket.Component child = new Label(\"child\"); add(new Split(\"split\", id -> child));") as PsiJavaFile
        companion(html = "<div wicket:id='split'><span wicket:id='wrong'></span></div>")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.toString(), 3, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.nodes.single { it.wicketId == "split" }.sourceSetupExpanded)
        assertNotNull(report.nodes.single { it.wicketId == "split" }.ownedMarkupPath)
        assertTrue(report.markup!!.sourceFiles.any { it.endsWith("/Split.html") })
    }
    private fun rows(body: String, members: String = "", iteration: String = "items.add(factory.apply(items.newChildId(), rows.next()));"): PageReport {
        myFixture.addFileToProject("src/main/java/example/Rows.java", """
            package example;
            public class Rows extends org.apache.wicket.markup.html.panel.Panel {
                public Rows(String id, java.util.function.BiFunction<String,Object,org.apache.wicket.Component> factory) {
                    super(id); org.apache.wicket.markup.repeater.RepeatingView items = new org.apache.wicket.markup.repeater.RepeatingView("items"); add(items);
                    java.util.Iterator<?> rows = java.util.List.of("data").iterator();
                    while (rows.hasNext()) { $iteration }
                }
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Rows.html", "<wicket:panel><div wicket:id='items'></div></wicket:panel>")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.panel.Fragment;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { $body } $members
            }
        """) as PsiJavaFile
        companion(html = "<div wicket:id='rows'></div><span wicket:id='plain'></span><wicket:fragment wicket:id='row'><b wicket:id='title'></b></wicket:fragment>")
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }

    private fun matches(report: PageReport) = report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
    private fun noMatches(report: PageReport) = assertTrue(report.warnings.toString(), matches(report).isEmpty())

    fun testRepeatedFactoryCreatesASymbolicFragmentAndKeepsRealIdsOutOfThePath() {
        val r = rows("add(new Rows(\"rows\", this::row));", "private Fragment row(String id, Object data) { Fragment f = new Fragment(id, \"row\", this); f.add(new Label(\"title\")); return f; }")
        assertEquals(r.warnings.toString(), 3, matches(r).size)
        val template = r.nodes.single { it.templateItem }
        assertNull(template.wicketId)
        assertEquals(Repetition.REPEATING_VIEW, r.nodes.single { it.wicketId == "items" }.repetition)
        assertTrue(template.template)
        assertEquals("row", template.fragment!!.definitionId)
        val title = r.nodes.single { it.wicketId == "title" }
        assertEquals(Confidence.INFERRED, matches(r).single { it.javaNodeId == title.nodeId }.confidence)
        assertFalse(title.idRenameSafe)
    }

    fun testRepeatedFactoryCannotReturnACapturedComponent() {
        noMatches(rows("org.apache.wicket.Component c = new Label(\"fixed\"); add(new Rows(\"rows\", (id, data) -> c));"))
    }

    fun testRepeatedFactoryCannotIgnoreItsGeneratedId() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> new Label(\"fixed\")));"))
    }

    fun testFactoryMutationOfTheCapturedPageBlocksPromotion() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> { removeAll(); return new Label(id); }));"))
    }

    fun testFactoryCannotChangeACapturedComponentField() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> { stored = new Label(\"changed\"); return new Label(id); }));", "private org.apache.wicket.Component stored;"))
    }

    fun testRepeatedBodyCannotMutateItsEnclosingPanel() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> new Label(id)));", iteration = "add(new Label(\"unsafe\")); items.add(factory.apply(items.newChildId(), rows.next()));"))
    }

    fun testKnownUnsafeEventCannotHideBehindAnOtherwiseValidFactory() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new org.apache.wicket.markup.html.link.Link<Void>("plain") { @Override public void onClick() { ExamplePage.this.removeAll(); } });
        """))
    }

    fun testDeferredComponentAliasCannotEraseItsTypeIntoObject() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new org.apache.wicket.markup.html.link.Link<Void>("plain") { @Override public void onClick() {
                Object alias = ExamplePage.this; ((org.apache.wicket.MarkupContainer)alias).removeAll();
            } });
        """))
    }

    fun testReadOnlyFeedbackAndAjaxRenderDoNotEraseAProvedTemplate() {
        val r = rows("""
            add(new Rows("rows", this::row));
            add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("plain") { @Override public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) {
                success("done"); target.add(ExamplePage.this);
            } });
        """, "private Fragment row(String id, Object data) { Fragment f = new Fragment(id, \"row\", this); f.add(new Label(\"title\")); return f; }")
        assertEquals(r.warnings.toString(), 4, matches(r).size)
    }

    fun testRecursionStaysBoundedAndDoesNotPublishFactoryBindings() {
        noMatches(rows("add(new Rows(\"rows\", this::row));", "private org.apache.wicket.Component row(String id, Object data) { return row(id, data); }"))
    }

    fun testDeferredBlockCannotExposeThePageAsAModelValue() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new Label("plain", (org.apache.wicket.model.IModel<Object>) () -> { return ExamplePage.this; }));
        """))
    }

    fun testConditionalComponentAliasStaysBorrowedAcrossAnEffectBranch() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new org.apache.wicket.markup.html.link.Link<Void>("plain") { @Override public void onClick() {
                Object alias = System.nanoTime() > 0 ? ExamplePage.this : new org.apache.wicket.markup.html.WebMarkupContainer("fresh");
                ((org.apache.wicket.MarkupContainer)alias).removeAll();
            } });
        """))
    }

    fun testLaterLocalAssignmentCannotEraseAPossibleCapturedAlias() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new org.apache.wicket.markup.html.link.Link<Void>("plain") { @Override public void onClick() {
                org.apache.wicket.MarkupContainer alias = new org.apache.wicket.markup.html.WebMarkupContainer("local");
                if (System.nanoTime() > 0) alias = ExamplePage.this; else alias = new org.apache.wicket.markup.html.WebMarkupContainer("other");
                alias.removeAll();
            } });
        """))
    }

    fun testOpaqueArrayCannotCarryACapturedPageReference() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new org.apache.wicket.markup.html.link.Link<Void>("plain") { @Override public void onClick() {
                Object[] aliases = new Object[] {ExamplePage.this};
                java.util.List.of(aliases);
            } });
        """))
    }

    fun testDataInitializerCannotHideAComponentMutation() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> new Label(id))); new Hidden();",
            "private class Hidden { { ExamplePage.this.removeAll(); } }"))
    }

    fun testSourceDataToStringCannotMutateThePageDuringSetup() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> new Label(id))); Object text = \"x\" + new Hidden();",
            "private class Hidden { @Override public String toString() { ExamplePage.this.removeAll(); return \"bad\"; } }"))
    }

    fun testCapturedScalarWritesThroughAHelperInvalidateBranchSelectors() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            java.util.List.of(1).forEach(value -> flip());
            if (flag) add(new Label("plain"));
        """, "private boolean flag = true; private void flip() { flag = false; }"))
    }

    fun testSourceAjaxBindingOverrideCannotHideHierarchyEffects() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new Label("plain").add(new Unsafe()));
        """, "private class Unsafe extends org.apache.wicket.ajax.AbstractDefaultAjaxBehavior { protected void respond(org.apache.wicket.ajax.AjaxRequestTarget t) {} @Override protected void onBind() { ExamplePage.this.removeAll(); } }"))
    }

    fun testConstructorFactoryReferencesRemainExplicitlyUnsupported() {
        noMatches(rows("add(new Rows(\"rows\", Row::new));", "private static class Row extends Label { Row(String id, Object data) { super(id); } }"))
    }

    fun testSourceAjaxStatelessHintCannotHideSynchronousWicket10BindingEffects() {
        noMatches(rows("""
            add(new Rows("rows", (id, data) -> new Label(id)));
            add(new Label("plain").add(new Unsafe()));
        """, "private class Unsafe extends org.apache.wicket.ajax.AbstractDefaultAjaxBehavior { protected void respond(org.apache.wicket.ajax.AjaxRequestTarget t) {} @Override public boolean getStatelessHint(org.apache.wicket.Component host) { ExamplePage.this.removeAll(); return false; } }"))
    }

    fun testUnknownIterationControlFlowDoesNotInventARepeatedTemplate() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> new Label(id)));", iteration = "if (System.nanoTime() > 0) items.add(factory.apply(items.newChildId(), rows.next()));"))
    }

    fun testKnownVoidReturnDoesNotInventLaterConstructorHelperAttachments() {
        val r = rows("add(new Rows(\"rows\", (id, data) -> new Label(id))); setup();",
            "private void setup() { if (true) return; add(new Label(\"plain\")); }")
        assertFalse(r.nodes.any { it.wicketId == "plain" })
        assertEquals(r.warnings.toString(), 2, matches(r).size)
    }

    fun testEarlyExitFromAnUnknownPopulationDoesNotPublishFollowingSetup() {
        noMatches(rows("add(new Rows(\"rows\", (id, data) -> new Label(id)));", iteration = "items.add(factory.apply(items.newChildId(), rows.next())); return;"))
    }

    fun testFactoryInvocationLimitDoesNotPublishEarlierPartialBindings() {
        val iteration = (0..64).joinToString(" ") { "items.add(factory.apply(items.newChildId(), rows.next()));" }
        val report = rows("add(new Rows(\"rows\", (id, data) -> new Label(id)));", iteration = iteration)
        noMatches(report)
        assertTrue(report.warnings.toString(), report.warnings.any { it.message.contains("bound") })
    }

    fun testCancellationPropagatesFromTheFactoryEnabledAnalyzer() {
        rows("add(new Rows(\"rows\", (id, data) -> new Label(id)));")
        val file = myFixture.findFileInTempDir("src/main/java/example/ExamplePage.java")
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(file) as PsiJavaFile
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        try {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                dev.wicketlens.analysis.WicketLayoutSetupAnalyzer(factories = true).analyze(java.classes.single(), "page")
            }, indicator)
            fail("Cancellation must propagate")
        } catch (_: com.intellij.openapi.progress.ProcessCanceledException) { }
    }

}
