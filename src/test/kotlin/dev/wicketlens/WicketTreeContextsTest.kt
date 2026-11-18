package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketTreeContextsTest:WicketProjectPsiTestCase() {
    private fun setup()=WicketTreeFixture.install(myFixture)
    private fun result(java:PsiJavaFile,limits:WicketTreeContexts.Limits=WicketTreeContexts.Limits())=WicketTreeContexts(limits).analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
    private fun change(path:String,before:String,after:String) { val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }; PsiDocumentManager.getInstance(project).commitAllDocuments() }
    fun testBothTreeKindsHaveNodeRelativeScopesAndSeparateParentPreconditions() {
        val java=setup();val primary=WicketPageAnalyzer().analyze(java.classes.single())!!;val r=result(java)
        assertTrue(r.diagnostics.toString(),r.complete);assertEquals(r.diagnostics.toString(),4,r.reports.size)
        assertEquals(setOf("NESTED_TREE","TABLE_TREE"),r.reports.map { it.treeTemplate!!.frameworkKind }.toSet())
        r.reports.forEach { report ->
            assertEquals(8,report.matching!!.findings.count { it.code==DiagnosticCode.MATCHED });assertTrue(report.matching.findings.all { it.confidence==Confidence.INFERRED })
            assertTrue(report.nodes.all { it.template && !it.idRenameSafe });assertFalse(report.scopeComplete)
            val root=report.nodes.single { it.nodeId==report.rootNodeId };assertNull(root.wicketId)
            assertEquals(setOf("junction","content"),report.nodes.filter { it.parentNodeId==root.nodeId }.mapNotNull { it.wicketId }.toSet())
            val label=report.nodes.single { it.wicketId=="label" && it.componentType.endsWith(".Label") }
            val parent=report.nodes.single { it.nodeId==label.parentNodeId }
            assertEquals(if(report.treeTemplate!!.conditions.single().nullness=="NON_NULL") "link" else "plain",parent.wicketId)
            val icon=report.nodes.single { it.possibleComponentTypes.isNotEmpty() };assertEquals("org.apache.wicket.Component",icon.componentType);assertEquals(2,icon.possibleComponentTypes.size)
        }
        assertEquals(primary,WicketPageAnalyzer().analyze(java.classes.single()))
    }
    fun testFactoryIdOriginCanComeFromALaterArgument() {
        val java=setup()
        change("src/main/java/example/Fields.java","marker(\"icon\",model.getObject())","marker(\"data\",\"icon\",model.getObject())")
        change("src/main/java/example/Fields.java","marker(String id,String value)","marker(String data,String id,String value)")
        val reports=result(java).reports;assertEquals(4,reports.size)
        val fields=com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/Fields.java"))!!
        reports.forEach { report -> val icon=report.nodes.single { it.possibleComponentTypes.isNotEmpty() }
            val literal=com.intellij.psi.util.PsiTreeUtil.getParentOfType(fields.findElementAt(icon.idLocation!!.offset),PsiLiteralExpression::class.java,false)!!
            assertEquals("icon",literal.value)
        }
    }
    fun testLiteralNodeIdIsNotForwardingEvenWhenItMatchesTheAnalysisMarker() { val java=setup();change("src/main/java/example/Tabular.java","new Branch(id,this,model)","new Branch(\"node\",this,model)");assertEquals(2,result(java).reports.size) }
    fun testLiteralContentIdIsNotForwarding() { val java=setup();change("src/main/java/example/Nested.java","new Fields(id,model,null)","new Fields(\"content\",model,null)");assertEquals(2,result(java).reports.size) }
    fun testLiteralJunctionIdIsNotForwarding() { val java=setup();change("src/main/java/example/Branch.java","AjaxLink<Void>(id)","AjaxLink<Void>(\"junction\")");assertTrue(result(java).reports.isEmpty()) }
    fun testNodeMustUseItsOwnTree() { val java=setup();change("src/main/java/example/Nested.java","new Branch(id,this,model)","new Branch(id,new Nested(\"foreign\",null),model)");assertTrue(result(java).reports.none { it.treeTemplate!!.hostComponentPath=="folders" }) }
    fun testNodeMustForwardTheSuppliedModel() { val java=setup();change("src/main/java/example/Nested.java","new Branch(id,this,model)","new Branch(id,this,org.apache.wicket.model.Model.of(\"other\"))");assertEquals(2,result(java).reports.size) }
    fun testAnonymousNodeInitializationIsWithheld() { val java=setup();change("src/main/java/example/Nested.java","new Branch(id,this,model) {","new Branch(id,this,model) { { removeAll(); }");assertEquals(2,result(java).reports.size) }
    fun testFactoryIdReassignmentWithholdsThatHost() { val java=setup();change("src/main/java/example/Nested.java","Branch node=","id=\"wrong\"; Branch node=");assertEquals(2,result(java).reports.size) }
    fun testReturnedNodeEscapeWithholdsThatHost() { val java=setup();change("src/main/java/example/Nested.java","node.setOutputMarkupId(true);","java.util.Objects.requireNonNull(node); node.setOutputMarkupId(true);");assertEquals(2,result(java).reports.size) }
    fun testCustomNodeInitializationWithholdsBothHosts() { val java=setup();change("src/main/java/example/Branch.java","@Override protected org.apache.wicket.MarkupContainer", "@Override protected void onInitialize() { super.onInitialize(); removeAll(); } @Override protected org.apache.wicket.MarkupContainer");assertTrue(result(java).reports.isEmpty()) }
    fun testConditionalHostAttachmentDoesNotBecomeADeclaredPlacement() { val java=setup();change("src/main/java/example/ExamplePage.java","add(new Nested", "if(System.nanoTime()>0) add(new Nested");assertEquals(2,result(java).reports.size) }
    fun testUnknownParentGuardDoesNotInventOneContext() { val java=setup();change("src/main/java/example/Fields.java","action!=null ?","System.nanoTime()>0 ?");assertTrue(result(java).reports.isEmpty()) }
    fun testContentFactoryMustForwardItsId() { val java=setup();change("src/main/java/example/Nested.java","new Fields(id,","new Fields(\"wrong\",");assertEquals(2,result(java).reports.size) }
    fun testNodeFactoryMustForwardItsId() { val java=setup();change("src/main/java/example/Nested.java","new Branch(id,","new Branch(\"wrong\",");assertEquals(2,result(java).reports.size) }
    fun testFactoryChoicesCannotChangeTheAttachmentId() { val java=setup();change("src/main/java/example/Fields.java","new org.apache.wicket.markup.html.WebMarkupContainer(id)","new org.apache.wicket.markup.html.WebMarkupContainer(\"wrong\")");assertTrue(result(java).reports.isEmpty()) }
    fun testReassignmentDoesNotRetainTheOldParent() { val java=setup();change("src/main/java/example/Fields.java","holder.add(","holder=plain; holder.add(");assertTrue(result(java).reports.isEmpty()) }
    fun testKnownContentMutationWithholdsBothCases() { val java=setup();change("src/main/java/example/Fields.java","super(id,model);","super(id,model); removeAll();");assertTrue(result(java).reports.isEmpty()) }
    fun testMissingContentResourceDoesNotInventBindings() { val java=setup();change("src/main/resources/example/Fields.html","wicket:id='label'","wicket:id='other'");assertTrue(result(java).reports.all { it.matching!!.findings.any { f->f.code!=DiagnosticCode.MATCHED } }) }
    fun testContextLimitClearsTheWholeDiscovery() { val java=setup();val r=result(java,WicketTreeContexts.Limits(contexts=3));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testNodeLimitClearsTheWholeDiscovery() { val java=setup();val r=result(java,WicketTreeContexts.Limits(nodes=4));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testCancellationPropagates() { val java=setup();val primary=WicketPageAnalyzer().analyze(java.classes.single())!!;val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketTreeContexts().analyze(java.classes.single(),primary) },indicator) } }
}
