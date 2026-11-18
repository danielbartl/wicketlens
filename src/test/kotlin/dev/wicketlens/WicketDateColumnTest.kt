package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketReportFormatter

class WicketDateColumnTest : WicketProjectPsiTestCase() {
    private fun change(before:String,after:String,name:String="DateColumn") {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String) {
        val java=WicketDateColumnFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
    }
    fun testReviewedJdkEnumDefaultsRemainOpaqueColumnData() {
        val java=WicketDateColumnFixture.install(myFixture);val before=java.text to java.modificationStamp
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(WicketReportFormatter().format(report),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(listOf("title"),report.nodes.mapNotNull {it.wicketId})
        assertEquals(report.warnings.toString(),2,report.warnings.count {"Reviewed JDK FormatStyle enum storage" in it.message})
        assertTrue(report.javaSourceFiles.any {it.endsWith("/java/time/format/FormatStyle.class")})
        assertTrue(report.javaSourceFiles.any {it.endsWith("/java/lang/Enum.class")})
        assertEquals(before,java.text to java.modificationStamp)
    }
    fun testAllFourCompiledConstantsRequireTheReviewedJdkIdentity() {
        WicketDateColumnFixture.install(myFixture)
        val facade=JavaPsiFacade.getInstance(project);val scope=GlobalSearchScope.allScope(project)
        val type=facade.findClass("java.time.format.FormatStyle",scope)!!
        val constants=type.fields.filterIsInstance<PsiEnumConstant>()
        assertEquals(setOf("FULL","LONG","MEDIUM","SHORT"),constants.map {it.name}.toSet())
        assertTrue(constants.all(WicketColumnConstruction::reviewedEnum))
        assertFalse(WicketColumnConstruction.reviewedEnum(facade.findClass("java.time.DayOfWeek",scope)!!.fields.filterIsInstance<PsiEnumConstant>().first()))
    }
    fun testStaticImportsRetainPhysicalStorageProvenance() {
        val java=WicketDateColumnFixture.install(myFixture)
        change("import java.time.format.FormatStyle;","import java.time.format.FormatStyle;import static java.time.format.FormatStyle.*;")
        change("=FormatStyle.MEDIUM;","=MEDIUM;");change("=FormatStyle.SHORT;","=SHORT;")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(2,report.warnings.count {"Reviewed JDK FormatStyle enum storage" in it.message})
    }
    fun testOtherReviewedConstantsDoNotExposeFormattingValues() {
        val java=WicketDateColumnFixture.install(myFixture)
        change("FormatStyle.MEDIUM","FormatStyle.FULL");change("FormatStyle.SHORT","FormatStyle.LONG")
        assertEquals(1,WicketPageAnalyzer().analyze(java.classes.single())!!.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
    }
    fun testInstanceQualifiedConstantCannotEvaluateABorrowedReceiver()=withheld("=FormatStyle.MEDIUM;","=((FormatStyle)null).MEDIUM;")
    fun testReceiverFactoriesRemainUnreviewed() {
        val java=WicketDateColumnFixture.install(myFixture)
        change("private FormatStyle dateStyle=FormatStyle.MEDIUM;","private static FormatStyle receiver(){System.setProperty(\"style\",\"effect\");return FormatStyle.MEDIUM;}private FormatStyle dateStyle=receiver().MEDIUM;")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testValueOfDoesNotAcquireAnExecutionContract()=withheld("=FormatStyle.MEDIUM;","=FormatStyle.valueOf(\"MEDIUM\");")
    fun testValuesDoesNotAcquireAnExecutionContract()=withheld("=FormatStyle.MEDIUM;","=FormatStyle.values()[0];")
    fun testFormatterCreationRemainsOutsideStorage()=withheld("private FormatStyle dateStyle=FormatStyle.MEDIUM;","private java.time.format.DateTimeFormatter formatter=java.time.format.DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);private FormatStyle dateStyle=FormatStyle.MEDIUM;")
    fun testEnumMethodsCannotBeInvokedDuringConstruction()=withheld("private FormatStyle dateStyle=FormatStyle.MEDIUM;","private String text=FormatStyle.MEDIUM.name();private FormatStyle dateStyle=FormatStyle.MEDIUM;")
    fun testLocaleLookupsRemainUnreviewed()=withheld("private FormatStyle dateStyle=FormatStyle.MEDIUM;","private java.util.Locale locale=java.util.Locale.getDefault();private FormatStyle dateStyle=FormatStyle.MEDIUM;")
    fun testSourceEnumsWithMatchingConstantNamesRemainUnreviewed() {
        val java=WicketDateColumnFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/FormatStyle.java","package example;enum FormatStyle {FULL,LONG,MEDIUM,SHORT;static {System.setProperty(\"style\",\"effect\");}}")
        change("import java.time.format.FormatStyle;","import example.FormatStyle;")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testSourceDeclarationCannotSpoofTheJdkClassName() {
        val source=myFixture.addFileToProject("src/main/java/java/time/format/FormatStyle.java","package java.time.format;public enum FormatStyle {FULL,LONG,MEDIUM,SHORT;}") as PsiJavaFile
        assertTrue(source.classes.single().fields.filterIsInstance<PsiEnumConstant>().all { !WicketColumnConstruction.reviewedEnum(it) })
    }
    fun testEnumNotesSurviveALaterUnsupportedPageStatementWithoutAPartialGraph() {
        val java=WicketDateColumnFixture.install(myFixture)
        change("add(new org.apache.wicket.markup.html.basic.Label","removeAll();add(new org.apache.wicket.markup.html.basic.Label","DatePage")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
        assertEquals(2,report.warnings.count {"Reviewed JDK FormatStyle enum storage" in it.message})
    }
    fun testCancellationDuringTheReviewedEnumDependencyScanPropagates() {
        WicketDateColumnFixture.install(myFixture)
        val type=JavaPsiFacade.getInstance(project).findClass("example.DateColumn",GlobalSearchScope.projectScope(project))!!
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at -> if(at is PsiClass && at.qualifiedName=="java.lang.Enum"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            WicketColumnConstruction(tick,tick,{null},{false},{_,_->},{_,message->throw AssertionError(message)}).inspect(type)
        },indicator)}
        assertTrue(reached)
    }
}
