package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter

class WicketContentFactoryTest : WicketProjectPsiTestCase() {
    private lateinit var java: PsiJavaFile
    override fun setUp() { super.setUp(); java = WicketContentFactoryFixture.install(myFixture) }
    private fun change(from: String, to: String, name: String = "FactoryPage") {
        val file = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(file.text, from in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(from, to)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun contexts(limits: WicketReplacementContexts.Limits = WicketReplacementContexts.Limits()): WicketReplacementContexts.Result {
        val page = java.classes.single()
        return WicketReplacementContexts(limits).analyze(page, WicketPageAnalyzer().analyze(page)!!)
    }
    private fun withheld() {
        val result = contexts()
        assertTrue(result.toString(), result.reports.isEmpty())
        assertTrue(result.toString(), result.diagnostics.isNotEmpty())
    }
    fun testImmediateRegistrationProjectsFragmentAndNestedFormWithoutChangingPrimary() {
        val primary = WicketPageAnalyzer().analyze(java.classes.single())!!
        val result = contexts()
        assertEquals(result.diagnostics.toString(), 1, result.reports.size)
        val report = result.reports.single()
        assertEquals("table:toolbar", report.lifecycle!!.componentPath)
        assertNotNull(report.lifecycle.contentFactory)
        assertEquals(4, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.all { it.code == DiagnosticCode.MATCHED && it.confidence == Confidence.INFERRED })
        assertTrue(report.nodes.filter { it.wicketId != null }.all { it.template && !it.idRenameSafe })
        assertFalse(report.scopeComplete)
        assertTrue(primary.nodes.none { it.wicketId in setOf("searchForm", "search") })
        assertNull(primary.lifecycle)
        assertTrue(result.sources.any { it.endsWith("SearchField.java") })
        assertEquals(1, report.lifecycle.contentFactory!!.callbackLocations.size)
        assertTrue(report.lifecycle.contentFactory.configurationLocations.isNotEmpty())
        val text = WicketReportFormatter().formatWithLinks(report)
        assertTrue(text.text, "source content-factory template at table:toolbar" in text.text)
        assertEquals(4, Regex("MATCHED / INFERRED").findAll(text.text).count())
        assertTrue(text.links.any { it.javaLocation?.file?.endsWith("SearchField.java") == true || it.markupLocation?.file?.endsWith("TableHost.html") == true })
    }
    fun testNativeLambdaIdAndDefinitionNavigationUsesTheTemplate() {
        val lambda = PsiTreeUtil.findChildrenOfType(java, PsiLambdaExpression::class.java).single()
        val targets = WicketReplacementNavigation.targets(lambda)
        assertEquals(1, targets.size); assertEquals("TableHost.html", targets.single().containingFile.name)
        assertNull(WicketIdRenameTarget.at(targets.single().containingFile, targets.single().textOffset+1))
        assertTrue(targets.single().references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element == lambda })
        for (id in listOf("searchForm", "search", "toolbarFragment")) {
            val literal = PsiTreeUtil.findChildrenOfType(java, PsiLiteralExpression::class.java).single { it.value == id }
            val references = literal.references.filterIsInstance<PsiPolyVariantReference>()
            assertTrue(id, references.isNotEmpty())
            val bindings = references.flatMap { it.multiResolve(false).toList() }
            assertEquals(id, 1, bindings.size)
            assertTrue(id, bindings.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element == literal })
        }
        val report = contexts().reports.single()
        assertTrue(report.nodes.filter { it.wicketId != null }.none { it.idRenameSafe })
    }
    fun testSetterWithoutOptionalGuardUsesTheSameProtocol() {
        change("if (content==null || !TOOLBAR.equals(content.getId())) { throw new IllegalArgumentException(\"Bad id: \"+(content==null ? \"null\" : content.getId())); }", "", "TableHost")
        assertEquals(1, contexts().reports.size)
    }
    fun testWrongLiteralIdIsNotForwarding() { change("Fragment(id,", "Fragment(\"toolbar\","); withheld() }
    fun testReassignedIdIsNotForwarding() { change("table.setToolbar(id -> {", "table.setToolbar(id -> { id=\"toolbar\";"); withheld() }
    fun testIdCannotEscape() { change("return toolbar;", "id.toString(); return toolbar;"); withheld() }
    fun testBorrowedComponentIsNotFresh() { change("new org.apache.wicket.markup.html.form.Form<>(\"searchForm\")", "(org.apache.wicket.markup.html.form.Form<Void>)get(\"form\")"); withheld() }
    fun testFactoryLocalAliasIsNotAnAllocation() { change("toolbar.add(form);", "org.apache.wicket.markup.html.form.Form<Void> alias=form; toolbar.add(alias);"); withheld() }
    fun testFreshLocalCannotEscapeToUnknownCalls() { change("toolbar.add(form);", "System.out.println(form); toolbar.add(form);"); withheld() }
    fun testFreshLocalCannotEscapeIntoCallback() { change("target.add(table);", "target.add(form);"); withheld() }
    fun testRepeatedAttachmentIsNotAUniqueParent() { change("toolbar.add(form);", "toolbar.add(form); toolbar.add(form);"); withheld() }
    fun testUnattachedLocalIsNotPartOfTheReturnedGraph() { change("toolbar.add(form);", ""); withheld() }
    fun testRootCannotBeAttachedToItsOwnChild() { change("toolbar.add(form);", "toolbar.add(form); form.add(toolbar);"); withheld() }
    fun testDuplicateSiblingIdsAreUnresolved() { change("toolbar.add(form);", "toolbar.add(form); toolbar.add(new org.apache.wicket.markup.html.form.Form<>(\"searchForm\"));"); withheld() }
    fun testStructuralBranchIsNotFlattened() { change("toolbar.add(form);", "if(System.nanoTime()>0) { toolbar.add(form); }"); withheld() }
    fun testStructuralMutationIsNotConfiguration() { change("toolbar.add(form);", "toolbar.add(form); toolbar.removeAll();"); withheld() }
    fun testEarlyReturnIsNotAUniqueFactoryGraph() { change("toolbar.add(form);", "if(System.nanoTime()>0) return toolbar; toolbar.add(form);"); withheld() }
    fun testBehaviorInitializerIsNotADeferredCallback() { change("@Override protected void onUpdate", "{ table.removeAll(); } @Override protected void onUpdate"); withheld() }
    fun testUnprovedFluentReturnIsNotTheAllocatedComponent() { change("return self();", "return new SearchField(\"other\");", "SearchField"); withheld() }
    fun testFactoryResultEscapeIsRejected() { change("replace(content);", "System.out.println(content); replace(content);", "TableHost"); withheld() }
    fun testFactoryEscapeIsRejected() { change("replace(content);", "System.out.println(factory); replace(content);", "TableHost"); withheld() }
    fun testConditionalFactoryCallIsRejected() { change("content=factory.apply(TOOLBAR)", "content=System.nanoTime()>0 ? factory.apply(TOOLBAR) : null", "TableHost"); assertTrue(contexts().reports.isEmpty()) }
    fun testConditionalReplacementIsRejected() { change("replace(content);", "if(content!=null) { replace(content); }", "TableHost"); withheld() }
    fun testUnknownFactoryIdIsRejected() { change("factory.apply(TOOLBAR)", "factory.apply(System.getProperty(\"id\"))", "TableHost"); withheld() }
    fun testMarkupAloneDoesNotSupplyAConstructorPlaceholder() { change("add(new org.apache.wicket.markup.html.WebMarkupContainer(TOOLBAR).setVisible(false));", "", "TableHost"); withheld() }
    fun testConditionalPlaceholderDoesNotSupplyAConstructorSlot() { change("add(new org.apache.wicket.markup.html.WebMarkupContainer(TOOLBAR).setVisible(false));", "if(System.nanoTime()>0) add(new org.apache.wicket.markup.html.WebMarkupContainer(TOOLBAR).setVisible(false));", "TableHost"); withheld() }
    fun testWrongPlaceholderIdDoesNotEstablishTheReplacementSlot() { change("WebMarkupContainer(TOOLBAR)", "WebMarkupContainer(\"other\")", "TableHost"); withheld() }
    fun testRemovedConstructorPlaceholderIsRejected() { change("super(id); add(", "super(id); removeAll(); add(", "TableHost"); withheld() }
    fun testDuplicateConstructorPlaceholdersAreNotReconciled() { change("super(id); add(", "super(id); add(new org.apache.wicket.markup.html.WebMarkupContainer(TOOLBAR)); add(", "TableHost"); withheld() }
    fun testDelegatingConstructorCanDeclareItsPlaceholder() {
        change("public TableHost(String id) { super(id);", "public TableHost(String id) { this(id,\"opaque\"); } private TableHost(String id, String ignored) { super(id);", "TableHost")
        assertEquals(1,contexts().reports.size)
    }
    fun testGuardCannotRunArbitraryCode() { change("!TOOLBAR.equals(content.getId())", "Boolean.getBoolean(\"anything\")", "TableHost"); withheld() }
    fun testGuardThatAlwaysThrowsDoesNotCreateAnUnreachableTemplate() { change("content==null || !TOOLBAR.equals(content.getId())", "true", "TableHost"); withheld() }
    fun testGuardRejectingANonNullResultDoesNotCreateATemplate() { change("content==null || !TOOLBAR.equals(content.getId())", "content!=null", "TableHost"); withheld() }
    fun testGuardRequiringTheWrongIdDoesNotCreateATemplate() { change("!TOOLBAR.equals(content.getId())", "!\"wrong\".equals(content.getId())", "TableHost"); withheld() }
    fun testGuardCannotImplicitlyInvokeTheComponentsToString() { change("\"null\" : content.getId()", "\"null\" : content", "TableHost"); withheld() }
    fun testGuardCannotAttachTheResult() { change("throw new IllegalArgumentException(\"Bad id: \"+(content==null ? \"null\" : content.getId()));", "add(content); throw new IllegalArgumentException();", "TableHost"); withheld() }
    fun testForeignReplacementReceiverIsRejected() { change("replace(content);", "getPage().replace(content);", "TableHost"); withheld() }
    fun testMultipleRegistrationsDoNotGuessTheLatest() { change("return toolbar;\n                });", "return toolbar;\n                }); table.setToolbar(id -> { org.apache.wicket.markup.html.panel.Fragment toolbar=new org.apache.wicket.markup.html.panel.Fragment(id,\"toolbarFragment\",this); toolbar.add(new org.apache.wicket.markup.html.form.Form<>(\"searchForm\")); return toolbar; });"); withheld() }
    fun testConditionalRegistrationHasNoUnconditionalRoute() { change("table.setToolbar(id -> {", "if(System.nanoTime()>0) table.setToolbar(id -> {"); withheld() }
    fun testReassignedHostIsRejected() { change("add(table);", "add(table); table=new TableHost(\"other\");"); withheld() }
    fun testDuplicateHostAttachmentIsRejected() { change("add(table);", "add(table); add(table);"); withheld() }
    fun testKnownHostMutationInvalidatesTheRoute() { change("add(table);", "add(table); table.removeAll();"); withheld() }
    fun testKnownPageMutationInvalidatesTheRoute() { change("add(table);", "add(table); removeAll();"); withheld() }
    fun testHostCannotEscapeToUnknownDirectCalls() { change("add(table);", "add(table); System.out.println(table);"); withheld() }
    fun testAnonymousHostDispatchIsNotAssumed() { change("new TableHost(\"table\")", "new TableHost(\"table\") {}"); withheld() }
    fun testStepBudgetWithholdsAllTemplates() {
        val result = contexts(WicketReplacementContexts.Limits(steps=1))
        assertFalse(result.complete); assertTrue(result.reports.isEmpty()); assertTrue(result.diagnostics.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS })
    }
    fun testContextBudgetWithholdsAllTemplates() {
        val result = contexts(WicketReplacementContexts.Limits(contexts=0))
        assertFalse(result.complete); assertTrue(result.reports.isEmpty())
    }
    fun testSourceBudgetWithholdsAllTemplates() {
        val result = contexts(WicketReplacementContexts.Limits(sources=1))
        assertFalse(result.complete); assertTrue(result.reports.isEmpty())
    }
    fun testNodeBudgetWithholdsTheWholeTemplate() {
        val children = (1..130).joinToString(" ") { "toolbar.add(new org.apache.wicket.markup.html.basic.Label(\"child$it\"));" }
        change("toolbar.add(form);", "toolbar.add(form); $children")
        val result = contexts(WicketReplacementContexts.Limits(steps=30000))
        assertFalse(result.complete); assertTrue(result.reports.isEmpty())
    }
    fun testCancellationPropagates() {
        val page = java.classes.single(); val primary = WicketPageAnalyzer().analyze(page)!!; val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable {
            indicator.cancel(); WicketReplacementContexts().analyze(page, primary)
        }, indicator) }
    }
}
