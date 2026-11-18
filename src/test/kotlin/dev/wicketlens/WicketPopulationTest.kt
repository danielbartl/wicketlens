package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.analysis.WicketPopulationAnalyzer
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter

class WicketPopulationTest : WicketProjectPsiTestCase() {
    private fun report(java:PsiJavaFile)=WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun change(name:String,before:String,after:String) {
        val file=myFixture.findFileInTempDir("src/main/java/example/$name.java")
        val psi=PsiManager.getInstance(project).findFile(file)!!;assertTrue(before,psi.text.contains(before))
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(psi)!!.setText(psi.text.replace(before,after));PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }
    private fun rejects(before:String,after:String) { val java=WicketPopulationFixture.install(myFixture);change("MenuPage",before,after);val r=report(java);assertTrue(r.warnings.toString(),r.populationContexts.isEmpty()) }
    fun testLeafAndGroupPopulationHaveSeparateConditionalSourceGraphs() {
        val java=WicketPopulationFixture.install(myFixture)
        val r=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(r.warnings.toString(),2,r.populationContexts.size)
        assertEquals(listOf(false,true),r.populationContexts.map { it.populationTemplate!!.conditions.single().result })
        assertEquals(listOf(4,7),r.populationContexts.map { c -> c.matching!!.findings.count { it.code==DiagnosticCode.MATCHED && it.javaNodeId in c.populationTemplate!!.ownedNodeIds } })
        assertEquals(9,r.matching!!.findings.count { it.code==DiagnosticCode.UNRESOLVED_BRANCH })
        assertEquals(2,r.matching.findings.count { it.code==DiagnosticCode.MATCHED })
        assertTrue(r.populationContexts.all { c -> c.nodes.all { it.template && !it.idRenameSafe } && c.matching!!.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED } })
        val text=WicketReportFormatter().formatWithLinks(r)
        assertTrue(text.text.contains("Population templates: 2"));assertTrue(text.text.contains("entry.isGroup() = false"));assertTrue(text.text.contains("entry.isGroup() = true"))
        assertTrue(text.links.all { it.start>=0 && it.end<=text.text.length && it.end>it.start })
    }
    fun testPlainFrameworkContainersNeedNoDecoratedSourceClass() { val j=WicketPopulationFixture.install(myFixture);change("MenuPage","new Sidebar(\"sidebar\")","new WebMarkupContainer(\"sidebar\")");assertEquals(2,report(j).populationContexts.size) }
    fun testHiddenRowsStillRetainDeclaredChildren() { val j=WicketPopulationFixture.install(myFixture);change("MenuPage","item.setVisible(true);","item.setVisible(false);");val r=report(j);assertEquals(2,r.populationContexts.size);assertEquals(listOf(4,7),r.populationContexts.map { it.matching!!.findings.count { it.code==DiagnosticCode.MATCHED } }) }
    fun testNestedRowsHaveSymbolicIdsAndPreserveCallbackScopes() { val j=WicketPopulationFixture.install(myFixture);val c=report(j).populationContexts.last();assertEquals(2,c.nodes.count { it.templateItem });assertEquals(2,c.populationTemplate!!.callbackLocations.size);assertTrue(c.nodes.filter { it.templateItem }.all { it.wicketId==null }) }
    fun testHiddenPlaceholderBodiesAreProjectedPerSourceBranch() { val j=WicketPopulationFixture.install(myFixture);val contexts=report(j).populationContexts;assertEquals(listOf(1,1),contexts.map { it.populationTemplate!!.inactiveBodyLocations.size });assertEquals(listOf(4,7),contexts.map { it.matching!!.findings.size }) }
    fun testBothBranchesMustBeSupportedBeforePublishingEither()=rejects("item.add(group(\"group\",entry));","item.add(group(\"group\",entry)); item.removeAll();")
    fun testCapturedPageCannotReceiveAChild()=rejects("item.add(link);","add(link);")
    fun testBorrowedComponentCannotSupplyAFactoryResult()=rejects("return new BookmarkablePageLink<Void>(id,MenuPage.class);","return (AbstractLink)get(\"sidebar\");")
    fun testFactoryMustForwardIdProvenance()=rejects("new BookmarkablePageLink<Void>(id,MenuPage.class)","new BookmarkablePageLink<Void>(\"link\",MenuPage.class)")
    fun testDuplicateAttachmentIsWithheld()=rejects("item.add(link);","item.add(link); item.add(link);")
    fun testComponentReassignmentIsWithheld()=rejects("item.add(link);","link=newLink(\"other\",entry); item.add(link);")
    fun testModelCannotCaptureTheItem()=rejects("item.setVisible(true);","item.setDefaultModel(new Model<Object>(item));")
    fun testFactoryWithForeignReceiverIsWithheld()=rejects("AbstractLink link=newLink(\"link\",entry);","AbstractLink link=new MenuPage().newLink(\"link\",entry);")
    fun testUnboundedPopulationLoopIsWithheld()=rejects("item.add(link);","while(System.nanoTime()>0) { item.add(link); }")
    fun testRecursiveHelperIsWithheld()=rejects("Entry entry=item.getModelObject();\n                AbstractLink link", "populateLink(item); Entry entry=item.getModelObject();\n                AbstractLink link")
    fun testNestedCustomItemFactoryIsWithheld()=rejects("@Override protected void populateItem(ListItem<Entry> item) { populateLink(item); }","@Override protected ListItem<Entry> newItem(int index,IModel<Entry> model) { return new ListItem<Entry>(index,model); } @Override protected void populateItem(ListItem<Entry> item) { populateLink(item); }")
    fun testNestedPopulationLifecycleOverrideIsWithheld()=rejects("@Override protected void populateItem(ListItem<Entry> item) { populateLink(item); }","@Override protected void onBeginPopulateItem(ListItem<Entry> item) { item.add(new Label(\"unexpected\")); } @Override protected void populateItem(ListItem<Entry> item) { populateLink(item); }")
    fun testConfigurationCannotChangeKnownParentStructure()=rejects("link.add(AttributeModifier.remove(\"aria-current\"));","link.removeAll();")
    fun testConcreteOverrideSuppliesItsActualSourceFactory() {
        WicketPopulationFixture.install(myFixture)
        val child=myFixture.addFileToProject("src/main/java/example/ChildPage.java","package example; public class ChildPage extends MenuPage { @Override protected org.apache.wicket.markup.html.link.AbstractLink newLink(String id,Entry entry) { return new org.apache.wicket.ajax.markup.html.AjaxLink<Void>(id) { @Override public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) {} }; } }") as PsiJavaFile
        val r=report(child);assertEquals(r.warnings.toString(),2,r.populationContexts.size)
        assertTrue(r.populationContexts.flatMap { it.nodes }.filter { it.wicketId=="link" && it.componentType!="org.apache.wicket.markup.html.WebMarkupContainer" }.all { it.componentType=="org.apache.wicket.ajax.markup.html.AjaxLink" })
    }
    fun testContextBudgetWithholdsAllPopulationGraphs()=bound(WicketPopulationAnalyzer.Limits(contexts=1))
    fun testNodeBudgetWithholdsAllPopulationGraphs()=bound(WicketPopulationAnalyzer.Limits(nodes=3))
    fun testStepBudgetWithholdsAllPopulationGraphs()=bound(WicketPopulationAnalyzer.Limits(steps=2))
    fun testSourceBudgetWithholdsAllPopulationGraphs()=bound(WicketPopulationAnalyzer.Limits(sources=1))
    fun testRepeaterNestingBudgetWithholdsAllPopulationGraphs()=bound(WicketPopulationAnalyzer.Limits(repeaters=1))
    fun testDepthBudgetWithholdsAllPopulationGraphs()=bound(WicketPopulationAnalyzer.Limits(depth=1))
    private fun bound(limits:WicketPopulationAnalyzer.Limits) { val j=WicketPopulationFixture.install(myFixture);val initial=report(j);val r=WicketPopulationAnalyzer(limits).analyze(j.classes.single(),initial);assertFalse(r.populationDiscoveryComplete);assertTrue(r.populationContexts.isEmpty());assertTrue(r.warnings.any { it.code==DiagnosticCode.PARTIAL_ANALYSIS && "Population discovery exceeded" in it.message }) }
    fun testCancellationPropagates() { val j=WicketPopulationFixture.install(myFixture);val initial=report(j);val indicator=EmptyProgressIndicator();assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketPopulationAnalyzer().analyze(j.classes.single(),initial) },indicator) } }
}
