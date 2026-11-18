package dev.wicketlens

import com.google.gson.JsonParser

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.mcp.*
import java.io.File

class WicketMcpPageStructureModulesTest : JavaCodeInsightFixtureTestCase() {
    override fun setUp() { WicketTreeFixture.initializeDefaultProjectAnnotations(); super.setUp() }
    override fun toAddSourceRoot() = false
    private fun modules() {
        val own = myFixture.tempDirFixture.findOrCreateDir("own")
        PsiTestUtil.addContentRoot(module, own)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("own/java"))
        val other = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "other", myFixture.tempDirFixture.findOrCreateDir("other"))
        PsiTestUtil.addSourceRoot(other, myFixture.tempDirFixture.findOrCreateDir("other/java"))
        val sdk = com.intellij.openapi.projectRoots.JavaSdk.getInstance().createJdk("MCP fixture SDK", System.getProperty("java.home"), false)
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
        }
        for (target in listOf(module, other)) {
            ModuleRootModificationUtil.setModuleSdk(target, sdk)
            (System.getProperty("wicketlens.test.libraries").split(File.pathSeparator) +
                System.getProperty("wicketlens.test.springLibraries").split(File.pathSeparator)).distinct().forEachIndexed { i, path ->
                val jar = File(path)
                PsiTestUtil.addLibrary(testRootDisposable, target, "MCP fixture $i", jar.parent, jar.name)
            }
        }
        for (directory in listOf("own", "other")) myFixture.addFileToProject("$directory/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
              @org.apache.wicket.spring.injection.annot.SpringBean(name="$directory") Object ${directory}Dependency = null;
              public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label("$directory")); }
            }
        """.trimIndent())
        for (directory in listOf("own", "other")) myFixture.addFileToProject("$directory/java/example/Config.java", """
            package example; @org.springframework.context.annotation.Configuration public class Config {
              @org.springframework.context.annotation.Bean("$directory") String service(){return "$directory";}
            }
        """.trimIndent())
    }
    fun testDuplicateClassesAcrossModulesAreRejected() {
        modules()
        try { WicketPageStructureRequest().capture(project, "example.ExamplePage"); fail("Expected ambiguity") }
        catch (failure: PageStructureFailure) { assertEquals("AMBIGUOUS_CLASS", failure.code) }
    }
    fun testExactModuleSelectsItsOwnDeclaration() {
        modules()
        val result = WicketPageStructureRequest().capture(project, "example.ExamplePage", "other")
        assertEquals("other", result.moduleName)
        assertEquals("other", result.report.nodes.single { it.wicketId != null }.wicketId)
    }
    fun testSourceLayoutConstructorFactsRemainWithinTheSelectedModule() {
        modules()
        for (directory in listOf("own", "other")) {
            myFixture.addFileToProject("$directory/java/example/Decorated.java", """
                package example; public class Decorated extends org.apache.wicket.markup.html.WebMarkupContainer {
                    public Decorated(String ignored) { super("$directory"); add(new org.apache.wicket.behavior.Behavior()); }
                }
            """.trimIndent())
            val java = myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText("""
                    package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                        public ExamplePage() { add(new Decorated("ignored")); }
                    }
                """.trimIndent())
            }
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html", "<div wicket:id='$directory'></div>")
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        val query = WicketPageAnalysisRequest()
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val report = query.capture(project, "example.ExamplePage", selected).report
            val component = report.nodes.single { it.wicketId != null }
            assertEquals(directory, component.wicketId)
            assertFalse(component.idRenameSafe)
            assertEquals(dev.wicketlens.model.DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
            val excluded = if (directory == "own") "other" else "own"
            assertTrue(report.javaSourceFiles.none { it.contains("/$excluded/java/") })
        }
    }
    fun testDataLoopBoundsAndRecordsStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { target ->
            ModuleRootModificationUtil.updateModel(target) { model ->
                model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
            }
        }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/Limits.java","package example;public class Limits {public static final int COUNT=3;}")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText("""
                    package example;public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                        private final java.util.List<Row> rows=new java.util.ArrayList<>();record Row(long id,String text){}
                        public ExamplePage(){for(int i=0;i<Limits.COUNT;i++){rows.add(new Row(i,"$directory"));}add(new org.apache.wicket.markup.html.basic.Label("$directory"));}
                    }
                """.trimIndent())
            }
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html","<b wicket:id='$directory'></b>")
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),1,report.matching!!.findings.count { it.code==dev.wicketlens.model.DiagnosticCode.MATCHED })
            assertEquals(directory,report.nodes.single { it.wicketId!=null }.wicketId)
            assertTrue(report.javaSourceFiles.any { it.endsWith("/$directory/java/example/Limits.java") })
            assertTrue(report.javaSourceFiles.none { it.contains(if(directory=="own") "/other/" else "/own/") })
        }
    }
    fun testFluentStorageProofStaysInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketFluentConfigurationFixture.shell else WicketFluentConfigurationFixture.shell.replace(".setStyle(Appearance.Style.GHOST)",".setStyle(Appearance.Style.values()[0])")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/Button.java",WicketFluentConfigurationFixture.button)
            myFixture.addFileToProject("$directory/java/example/Appearance.java",WicketFluentConfigurationFixture.appearance)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><a wicket:id='clear'>Clear</a></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed fresh inline component fluent configuration" in it.message})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testOwnedAccessorProofStaysInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketOwnedAccessorFixture.shell else WicketOwnedAccessorFixture.shell.replace("chooser.getTrigger().add","add(chooser);chooser.getTrigger().add")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/Popover.java",WicketOwnedAccessorFixture.popover)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><div wicket:id='tools'></div></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed owned component accessor behavior decoration" in it.message})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testNavigationFactoryStaysInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketNavigationToolbarFixture.shell else WicketNavigationToolbarFixture.shell.replace("new Pager(id,table)","new Pager(id+\"other\",table)")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed fresh-table navigation-toolbar construction" in it.message})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testToolbarRegistrationStaysInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketToolbarRegistrationFixture.shell else WicketToolbarRegistrationFixture.shell.replace("super(table,locator);","super(table,locator);TableShell.this.removeAll();")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed owned DataTable toolbar registration" in it.message})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testDataTableConstructionAndOverridesStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketDataTableFixture.shell else WicketDataTableFixture.shell.replace("RowsTable(String id","protected org.apache.wicket.model.IModel<String> getCaptionModel(){return null;}RowsTable(String id")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed DataTable constructor" in it.message})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testProviderConstructionAndQueriesStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val page=if(directory=="own") WicketProviderFixture.page else WicketProviderFixture.page.replace("@Override public java.util.Iterator","Rows(){size();}@Override public java.util.Iterator")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(page.replace("ProviderPage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketProviderFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed source data-provider construction" in it.message})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testDateColumnEnumDefaultsStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val column=if(directory=="own") WicketDateColumnFixture.column else WicketDateColumnFixture.column.replace("=FormatStyle.MEDIUM;","=FormatStyle.valueOf(\"MEDIUM\");")
            myFixture.addFileToProject("$directory/java/example/DateColumn.java",column)
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDateColumnFixture.page.replace("DatePage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDateColumnFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed JDK FormatStyle enum storage" in it.message})
            assertTrue(report.javaSourceFiles.any {it.endsWith("/$directory/java/example/DateColumn.java")})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testLambdaColumnCallbackInterfacesStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/BadgeColumn.java",WicketLambdaColumnFixture.column)
            val callback=if(directory=="own") WicketLambdaColumnFixture.function else WicketLambdaColumnFixture.function.replace(" {}"," {int TOKEN=seed();static int seed(){System.setProperty(\"lambda\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
            myFixture.addFileToProject("$directory/java/example/RowFunction.java",callback)
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketLambdaColumnFixture.page.replace("LambdaPage","ExamplePage").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketLambdaColumnFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed LambdaColumn constructor storage" in it.message})
            assertTrue(report.javaSourceFiles.any {it.endsWith("/$directory/java/example/RowFunction.java")})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testColumnConstructorContractsStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val column=if(directory=="own") WicketColumnConstructionFixture.column else WicketColumnConstructionFixture.column.replace("this.key=key;","this.key=key;model.getObject();")
            myFixture.addFileToProject("$directory/java/example/StorageColumn.java",column)
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketColumnConstructionFixture.page.replace("ColumnPage","ExamplePage").replace("\"title\"","\"$directory\""))
            }
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketColumnConstructionFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertEquals(report.warnings.toString(),directory=="own",report.warnings.any {"Reviewed AbstractColumn constructor storage" in it.message})
            assertTrue(report.javaSourceFiles.any {it.endsWith("/$directory/java/example/StorageColumn.java")})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testConstructorContentAndInheritedSlotIdsStayInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { target ->
            ModuleRootModificationUtil.updateModel(target) { model ->
                model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
            }
        }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/DialogBox.java",WicketConfirmContentFixture.base.replace("CONTENT_ID=\"content\"","CONTENT_ID=\"$directory-message\""))
            myFixture.addFileToProject("$directory/java/example/ConfirmBox.java",WicketConfirmContentFixture.confirmation)
            myFixture.addFileToProject("$directory/java/example/DialogBox.html",WicketInitialContentFixture.hostMarkup.replace("'content'","'$directory-message'"))
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketConfirmContentFixture.page.replace("ConfirmPage","ExamplePage"))
            }
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketConfirmContentFixture.markup)
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val report=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected).report
            assertNotNull(report.warnings.toString(),report.initialContentSetup)
            assertEquals("$directory-message",report.initialContentSetup!!.hosts.single().contentId)
            assertTrue(report.matching!!.findings.all {it.code==dev.wicketlens.model.DiagnosticCode.MATCHED})
            assertTrue(report.javaSourceFiles.none {it.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testFragmentDefinitionsDoNotLeakAcrossModulesWithIdenticalPageNames() {
        modules()
        for (directory in listOf("own", "other")) {
            val java = myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText("""
                    package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                        public ExamplePage() {
                            org.apache.wicket.markup.html.panel.Fragment f = new org.apache.wicket.markup.html.panel.Fragment("slot", "shared", this);
                            f.add(new org.apache.wicket.markup.html.basic.Label("$directory")); add(f);
                        }
                    }
                """.trimIndent())
            }
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",
                "<div wicket:id='slot'></div><wicket:fragment wicket:id='shared'><b wicket:id='$directory'></b></wicket:fragment>")
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val report = WicketPageAnalysisRequest().capture(project, "example.ExamplePage", selected).report
            assertEquals(2, report.matching!!.findings.count { it.code == dev.wicketlens.model.DiagnosticCode.MATCHED })
            assertTrue(report.fragmentUses.single().definitionLocation!!.file.contains("/$directory/"))
            assertTrue(report.javaSourceFiles.none { it.contains(if (directory == "own") "/other/" else "/own/") })
        }
    }
    fun testFactoryOwnedMarkupAndCallbackIdsStayInTheSelectedModule() {
        modules()
        for (directory in listOf("own", "other")) {
            myFixture.addFileToProject("$directory/java/example/Box.java", """
                package example; public class Box extends org.apache.wicket.markup.html.panel.Panel {
                    public Box(String id, java.util.function.Function<String,org.apache.wicket.Component> factory) {
                        super(id); add(factory.apply("$directory"));
                    }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/Box.html", "<wicket:panel><span wicket:id='$directory'></span></wicket:panel>")
            val java = myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText("""
                    package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                        public ExamplePage() { add(new Box("box", id -> new org.apache.wicket.markup.html.basic.Label(id))); }
                    }
                """)
            }
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html", "<div wicket:id='box'></div>")
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val snapshot = WicketPageAnalysisRequest().capture(project, "example.ExamplePage", selected)
            assertEquals(2, snapshot.report.matching!!.findings.count { it.code == dev.wicketlens.model.DiagnosticCode.MATCHED })
            assertTrue(snapshot.report.nodes.single { it.sourceSetupExpanded }.ownedMarkupPath!!.contains("/$directory/"))
            val excluded = if (directory == "own") "/other/" else "/own/"
            assertTrue(snapshot.report.javaSourceFiles.none { it.contains(excluded) })
            assertTrue(snapshot.report.markup!!.sourceFiles.none { it.contains(excluded) })
        }
    }
    fun testDependencyToolSelectsOnlyItsOwnModulesBeanCandidates() {
        modules()
        val snapshot = WicketPageAnalysisRequest().capture(project, "example.ExamplePage", "other", view=WicketMcpReport.DEPENDENCIES)
        assertEquals(listOf("other"), snapshot.dependencies!!.owners.single().fields.single().candidates.single().names)
    }

    fun testNestedCompanionsAndCallerContextsRespectConflictingModuleDeclarations() {
        modules()
        for (directory in listOf("own", "other")) {
            myFixture.addFileToProject("$directory/java/example/Shell.java", """
                package example; public class Shell {
                    public static final class Box extends org.apache.wicket.markup.html.panel.Panel {
                        public Box(String id, java.util.function.Function<String,org.apache.wicket.Component> factory) {
                            super(id); add(factory.apply("$directory"));
                        }
                    }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/Shell\$Box.html", "<wicket:panel><span wicket:id='$directory'></span></wicket:panel>")
            myFixture.addFileToProject("$directory/java/example/Caller.java", """
                package example; public class Caller extends org.apache.wicket.markup.html.WebPage {
                    public Caller() { add(new Shell.Box("box", id -> new org.apache.wicket.markup.html.basic.Label("$directory"))); }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/Caller.html", "<div wicket:id='box'></div>")
        }
        val query = WicketPageAnalysisRequest()
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val snapshot = query.capture(project, "example.Shell.Box", selected, includeNavigationContexts = true)
            val nav = snapshot.navigationContexts!!
            assertTrue(nav.complete)
            assertTrue(snapshot.report.markupPath!!.contains("/$directory/"))
            val caller = nav.contexts.single { it.relationship == "CALLER" }
            assertEquals(selected, caller.moduleName)
            assertEquals(1, caller.bindingNodeIds.size)
            val excluded = if (directory == "own") "/other/" else "/own/"
            assertTrue(nav.sourceFiles.none { it.contains(excluded) })
            query.requireCurrent(project, snapshot)
            val selectedSnapshot=query.capture(project,"example.Shell.Box",selected,view=WicketMcpReport.NAVIGATION)
            val index=JsonParser.parseString(query.render(selectedSnapshot,WicketMcpReport.NAVIGATION)).asJsonObject["navigationContexts"].asJsonObject
            assertTrue(index["contexts"].asJsonArray.all {it.asJsonObject["moduleName"].asString==selected})
            assertTrue(index["sourceFiles"].asJsonArray.none {it.asString.contains(excluded)})
            val foreign=query.capture(project,"example.Shell.Box",if(selected==module.name) "other" else module.name,view=WicketMcpReport.NAVIGATION)
            val id=index["contexts"].asJsonArray.first().asJsonObject["contextId"].asString
            try {query.render(foreign,WicketMcpReport.NAVIGATION,contextId=id);fail("Expected a module-bound navigation selector")}
            catch(failure:dev.wicketlens.mcp.PageAnalysisFailure) {assertEquals("STALE_CONTEXT_SELECTION",failure.code)}

        }
    }
    fun testDependencyToolRejectsAmbiguousModuleSelection() {
        modules()
        try { WicketPageAnalysisRequest().capture(project,"example.ExamplePage",view=WicketMcpReport.DEPENDENCIES); fail("Expected ambiguity") }
        catch (failure: PageAnalysisFailure) { assertEquals("AMBIGUOUS_CLASS", failure.code) }
    }
    fun testSpringInspectionScopeDoesNotIncludeTheOtherModulesFindings() {
        modules()
        myFixture.enableInspections(dev.wicketlens.spring.WicketSpringInjectionInspection())
        val snapshot = WicketPageAnalysisRequest().capture(project,"example.ExamplePage","other",view=WicketMcpReport.SPRING_INSPECTIONS)
        val result = snapshot.springInspections!!
        assertEquals(result.toString(),1,result.findings.size)
        assertTrue(result.findings.single().location.file.contains("/other/java/"))
        assertTrue(result.sourceFiles.all { it.contains("/other/java/") })
    }
    fun testSpringTestPlanUsesTheSelectedModulesSubjectAndTestRoots() {
        modules()
        val other = com.intellij.openapi.module.ModuleManager.getInstance(project).modules.single { it.name == "other" }
        val ownTests = myFixture.tempDirFixture.findOrCreateDir("own/tests")
        val otherTests = myFixture.tempDirFixture.findOrCreateDir("other/tests")
        PsiTestUtil.addSourceRoot(module, ownTests, org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        PsiTestUtil.addSourceRoot(other, otherTests, org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        val snapshot = WicketPageAnalysisRequest().capture(project,"example.ExamplePage","other",view=WicketMcpReport.SPRING_TEST_PLAN)
        val prepared = snapshot.springTest!!
        assertTrue(prepared.plan.manual.single().contains("otherDependency"))
        assertFalse(prepared.plan.manual.single().contains("ownDependency"))
        assertEquals(listOf(otherTests.path), prepared.roots.map { it.path })
    }
    fun testSpringTestPlanRejectsAmbiguousSubjectsRatherThanUsingEditorSelection() {
        modules()
        try { WicketPageAnalysisRequest().capture(project,"example.ExamplePage",view=WicketMcpReport.SPRING_TEST_PLAN); fail("Expected ambiguity") }
        catch (failure: PageAnalysisFailure) { assertEquals("AMBIGUOUS_CLASS", failure.code) }
    }
    fun testGeneralPlannerKeepsTheSelectedModulesInjectionIntentAndFieldOrigins() {
        modules()
        val snapshot=WicketPageAnalysisRequest().capture(project,"example.ExamplePage","other",view=WicketMcpReport.TEST_PLAN)
        assertTrue(snapshot.testPlan!!.plan.spring)
        assertTrue(snapshot.testPlan!!.plan.manual.single().contains("otherDependency"))
        assertFalse(snapshot.testPlan!!.plan.manual.single().contains("ownDependency"))
    }

    fun testOwnedFormCacheProofsRemainWithinTheSelectedModule() {
        modules()
        for (directory in listOf("own", "other")) {
            myFixture.addFileToProject("$directory/java/example/Field.java", """
                package example; public class Field extends org.apache.wicket.markup.html.panel.Panel {
                    private org.apache.wicket.markup.html.form.TextField<String> input;
                    public Field(String ignored) { super("$directory"); }
                    public org.apache.wicket.markup.html.form.TextField<String> getInput() {
                        if (input == null) { input = create(); add(input); }
                        return input;
                    }
                    private org.apache.wicket.markup.html.form.TextField<String> create() { return new org.apache.wicket.markup.html.form.TextField<>("input"); }
                    public Field required() { getInput().setRequired(true); return this; }
                    @Override protected void onInitialize() { super.onInitialize(); getInput(); }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/FormPage.java", """
                package example; public class FormPage extends org.apache.wicket.markup.html.WebPage {
                    public FormPage() { add(new Field("ignored").required()); }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/FormPage.html", "<div wicket:id='$directory'></div>")
        }
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val snapshot = WicketPageAnalysisRequest().capture(project, "example.FormPage", selected)
            val node = snapshot.report.nodes.single { it.wicketId != null }
            assertEquals(directory, node.wicketId)
            assertEquals(1, snapshot.report.matching!!.findings.count { it.code == dev.wicketlens.model.DiagnosticCode.MATCHED })
            val excluded = if (directory == "own") "/other/" else "/own/"
            assertTrue(snapshot.report.javaSourceFiles.none { it.contains(excluded) })
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }
    fun testIdOriginUseAuditDoesNotShareHelperProofsBetweenModules() {
        modules()
        for (directory in listOf("own", "other")) {
            val extra = if (directory == "other") "org.apache.wicket.model.Model.of(id);" else ""
            myFixture.addFileToProject("$directory/java/example/Decorated.java", """
                package example; public class Decorated extends org.apache.wicket.markup.html.WebMarkupContainer {
                    public Decorated(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); $extra }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/AuditedPage.java", """
                package example; public class AuditedPage extends org.apache.wicket.markup.html.WebPage {
                    public AuditedPage() { add(new Decorated("title")); }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/AuditedPage.html", "<div wicket:id='title'></div>")
        }
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val snapshot = WicketPageAnalysisRequest().capture(project, "example.AuditedPage", selected)
            assertEquals(directory == "own", snapshot.report.nodes.single { it.wicketId == "title" }.idRenameSafe)
            assertEquals(dev.wicketlens.model.DiagnosticCode.MATCHED, snapshot.report.matching!!.findings.single().code)
            val excluded = if (directory == "own") "/other/" else "/own/"
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }

    fun testCompositeFormInputContextsStayWithinTheSelectedModule() {
        modules()
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/FieldBase.java",WicketFormInputFixture.base.replace("\"input\"","\"${directory}Input\""))
            myFixture.addFileToProject("$directory/java/example/Field.java",WicketFormInputFixture.field)
            myFixture.addFileToProject("$directory/java/example/Field.html",WicketFormInputFixture.markup.replace("'input'","'${directory}Input'"))
            myFixture.addFileToProject("$directory/java/example/Field\$Input.html",WicketFormInputFixture.inputMarkup)
        }
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.Field",selected,includeNavigationContexts=true)
            val context=snapshot.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }
            assertEquals("${directory}Input",context.report.formTemplate!!.inputComponentPath)
            assertEquals(6,context.bindingNodeIds.size)
            assertTrue(snapshot.version.sources.none { it.path.contains(if(directory=="own") "/other/" else "/own/") })
        }
    }

    fun testPopulationHelpersAndPredicatesStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/Entry.java",WicketPopulationFixture.model)
            myFixture.addFileToProject("$directory/java/example/MenuPage.java",WicketPopulationFixture.page.replace("\"badge\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/MenuPage.html",WicketPopulationFixture.markup.replace("'badge'","'$directory'"))
        }
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.MenuPage",selected)
            assertEquals(snapshot.report.warnings.toString(),2,snapshot.report.populationContexts.size)
            assertTrue(snapshot.report.populationContexts.all { it.nodes.any { node -> node.wicketId==directory } })
            val excluded=if(directory=="own") "/other/" else "/own/"
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }

    fun testBaseLayoutConstructorReconciliationStaysInTheSelectedModule() {
        modules()
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/Html.java",WicketBaseLayoutFixture.htmlClass)
            myFixture.addFileToProject("$directory/java/example/Shell.java",WicketBaseLayoutFixture.layout.replace("\"name\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/Shell.html",WicketBaseLayoutFixture.markup.replace("'name'","'$directory'"))
        }
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.Shell",selected)
            assertNotNull(snapshot.report.warnings.toString(),snapshot.report.baseLayoutTemplate)
            assertTrue(snapshot.report.nodes.any { it.wicketId==directory });assertEquals(5,snapshot.report.matching!!.findings.count { it.code==dev.wicketlens.model.DiagnosticCode.MATCHED })
            val excluded=if(directory=="own") "/other/" else "/own/"
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }

    fun testRegisteredFormContextKeepsItsCallerOwnerAcrossADependencyModule() {
        modules()
        val other=com.intellij.openapi.module.ModuleManager.getInstance(project).findModuleByName("other")!!
        ModuleRootModificationUtil.addDependency(module,other)
        for(selected in listOf(module,other)) ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }
        myFixture.addFileToProject("other/java/example/FieldBase.java",WicketFormInputFixture.base)
        myFixture.addFileToProject("other/java/example/CustomField.java",WicketRegisteredFormFixture.wrapper)
        myFixture.addFileToProject("other/java/example/CustomField.html",WicketFormInputFixture.markup)
        myFixture.addFileToProject("own/java/example/PhoneInput.java",WicketRegisteredFormFixture.input)
        myFixture.addFileToProject("own/java/example/PhoneInput.html",WicketRegisteredFormFixture.inputMarkup)
        myFixture.addFileToProject("own/java/example/ContactPage.java",WicketRegisteredFormFixture.page)
        myFixture.addFileToProject("own/java/example/ContactPage.html","<div wicket:id='phone'></div>")
        for((name,selected) in listOf("example.ContactPage" to module.name,"example.CustomField" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,name,selected,includeNavigationContexts=true)
            val context=snapshot.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }
            assertEquals("example.ContactPage",context.report.qualifiedClassName);assertEquals(module.name,context.moduleName)
            assertEquals("example.CustomField",context.report.formTemplate!!.wrapperClassName)
            assertEquals("example.CustomField",context.report.nodes.single { it.nodeId==context.report.rootNodeId }.componentType)
            assertEquals(6,context.bindingNodeIds.size)
            assertTrue(snapshot.version.sources.any { "/own/java/example/PhoneInput.java" in it.path })
            assertTrue(snapshot.version.sources.any { "/other/java/example/CustomField.java" in it.path })
        }
    }

    fun testRegisteredFormTemplatesStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for(directory in listOf("own","other")) {
            for((name,text) in listOf("FieldBase" to WicketFormInputFixture.base,"CustomField" to WicketRegisteredFormFixture.wrapper,"PhoneInput" to WicketRegisteredFormFixture.input.replace("\"number\"","\"$directory\""),"ContactPage" to WicketRegisteredFormFixture.page))
                myFixture.addFileToProject("$directory/java/example/$name.java",text)
            myFixture.addFileToProject("$directory/java/example/CustomField.html",WicketFormInputFixture.markup)
            myFixture.addFileToProject("$directory/java/example/PhoneInput.html",WicketRegisteredFormFixture.inputMarkup.replace("'number'","'$directory'"))
            myFixture.addFileToProject("$directory/java/example/ContactPage.html","<div wicket:id='phone'></div>")
        }
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.ContactPage",selected,includeNavigationContexts=true)
            val form=snapshot.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }.report
            assertEquals(setOf("code",directory),form.nodes.filter { it.parentNodeId==form.formTemplate!!.inputNodeId }.mapNotNull { it.wicketId }.toSet())
            val excluded=if(directory=="own") "/other/" else "/own/"
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }

    fun testTreeTemplatesStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for(directory in listOf("own","other")) {
            for((name,text) in listOf("Branch" to WicketTreeFixture.node,"Nested" to WicketTreeFixture.nested,"Tabular" to WicketTreeFixture.table,"Fields" to WicketTreeFixture.fields))
                myFixture.addFileToProject("$directory/java/example/$name.java",text)
            myFixture.addFileToProject("$directory/java/example/TreePage.java",WicketTreeFixture.page.replace("ExamplePage","TreePage").replace("\"folders\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/TreePage.html",WicketTreeFixture.pageMarkup.replace("'folders'","'$directory'"))
            myFixture.addFileToProject("$directory/java/example/Fields.html",WicketTreeFixture.fieldsMarkup)
        }
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.TreePage",selected,includeNavigationContexts=true)
            val contexts=snapshot.navigationContexts!!.contexts.filter { it.relationship=="TREE_TEMPLATE" }
            assertEquals(snapshot.navigationContexts.lifecycleDiagnostics.toString(),4,contexts.size)
            assertEquals(setOf(directory,"accounts"),contexts.map { it.report.treeTemplate!!.hostComponentPath }.toSet())
            val excluded=if(directory=="own") "/other/" else "/own/"
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }

    fun testEventCompletionCandidatesStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/DialogBox.java",WicketEventContentFixture.host)
            myFixture.addFileToProject("$directory/java/example/DrawerBox.java",WicketInitialContentFixture.drawer)
            myFixture.addFileToProject("$directory/java/example/DetailList.java",WicketEventContentFixture.list)
            myFixture.addFileToProject("$directory/java/example/EventPage.java",WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
            myFixture.addFileToProject("$directory/java/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
            myFixture.addFileToProject("$directory/java/example/DetailList.html","<wicket:panel>Details</wicket:panel>")
            myFixture.addFileToProject("$directory/java/example/EventPage.html",WicketEventContentFixture.markup.replace("'list'","'$directory'"))
        }
        for(directory in listOf("own","other")) {
            val java=com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("$directory/java/example/EventPage.java"))!!
            val hole=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,com.intellij.psi.PsiLiteralExpression::class.java).single { it.value=="" }
            val choices=dev.wicketlens.editor.WicketIdCompletionContributor().suggestions(hole)
            assertEquals(choices.toString(),listOf(directory),choices.map { it.id })
            assertTrue(choices.single().counterparts.all { "/$directory/" in it.file })
        }
    }

    fun testTransparentCompletionFallbackUsesOnlyTheSelectedModulesCounterpart() {
        modules()
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/Html.java","package example;public class Html extends org.apache.wicket.markup.html.TransparentWebMarkupContainer { public Html(String id) { super(id); } }")
            myFixture.addFileToProject("$directory/java/example/ScopePage.java","package example;public class ScopePage extends org.apache.wicket.markup.html.WebPage { public ScopePage() { add(new Html(\"shell\"),new org.apache.wicket.markup.html.basic.Label(\"\")); } }")
            myFixture.addFileToProject("$directory/java/example/ScopePage.html","<div wicket:id='shell'><b wicket:id='$directory'></b></div>")
        }
        for(directory in listOf("own","other")) {
            val java=com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("$directory/java/example/ScopePage.java"))!!
            val literal=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,com.intellij.psi.PsiLiteralExpression::class.java).single { it.value=="" }
            val choices=dev.wicketlens.editor.WicketIdCompletionContributor().suggestions(literal)
            assertEquals(choices.toString(),listOf(directory),choices.map { it.id })
            assertTrue(choices.single().counterparts.all { "/$directory/" in it.file })
        }
    }

    fun testEventContentTemplatesStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/DialogBox.java",WicketEventContentFixture.host)
            myFixture.addFileToProject("$directory/java/example/DrawerBox.java",WicketInitialContentFixture.drawer)
            myFixture.addFileToProject("$directory/java/example/DetailList.java",WicketEventContentFixture.list)
            myFixture.addFileToProject("$directory/java/example/EventPage.java",WicketEventContentFixture.page.replace("\"list\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
            myFixture.addFileToProject("$directory/java/example/DetailList.html","<wicket:panel>Details</wicket:panel>")
            myFixture.addFileToProject("$directory/java/example/EventPage.html",WicketEventContentFixture.markup.replace("'list'","'$directory'"))
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.EventPage",selected,includeNavigationContexts=true)
            val event=snapshot.navigationContexts!!.contexts.single { it.report.lifecycle?.directContent!=null }.report
            assertTrue(event.nodes.any { it.wicketId==directory })
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            assertTrue(event.nodes.all { "/$directory/" in it.javaLocation.file })
            query.requireCurrent(project,snapshot)
        }
    }

    fun testInitialContentDeclarationsStayWithinTheSelectedModule() {
        modules()
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/DialogBox.java",WicketInitialContentFixture.host)
            myFixture.addFileToProject("$directory/java/example/DrawerBox.java",WicketInitialContentFixture.drawer)
            myFixture.addFileToProject("$directory/java/example/ContentPage.java",WicketInitialContentFixture.page.replace("\"name\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
            myFixture.addFileToProject("$directory/java/example/ContentPage.html",WicketInitialContentFixture.markup.replace("'name'","'$directory'"))
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.ContentPage",selected)
            assertEquals(2,snapshot.report.initialContentSetup!!.hosts.size)
            assertTrue(snapshot.report.nodes.any { it.wicketId==directory })
            assertTrue(snapshot.report.initialContentSetup.hosts.all { "/$directory/" in it.constructionLocation.file })
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            query.requireCurrent(project,snapshot)
        }
    }

    fun testSubmissionRoutesAndRefreshTargetsStayWithinTheSelectedModule() {
        modules()
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/DialogBox.java",WicketSubmissionFixture.host)
            myFixture.addFileToProject("$directory/java/example/SubmitPage.java",WicketSubmissionFixture.page.replace("\"people\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
            myFixture.addFileToProject("$directory/java/example/SubmitPage.html",WicketSubmissionFixture.markup.replace("'people'","'$directory'"))
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.SubmitPage",selected,includeNavigationContexts=true)
            val submissions=snapshot.navigationContexts!!.contexts.filter { it.relationship=="SUBMISSION_TEMPLATE" }.map { it.report }
            assertEquals(2,submissions.size)
            assertEquals(listOf(directory),submissions.flatMap { it.submissionTemplate!!.effects }.filter { it.kind=="AJAX_REFRESH" }.map { it.targetComponentPath })
            assertTrue(submissions.all { report -> report.nodes.all { "/$directory/" in it.javaLocation.file } })
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            query.requireCurrent(project,snapshot)
        }
    }

    fun testOrdinaryFormSubmissionDeclarationsStayWithinTheSelectedModule() {
        modules()
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/SubmitButton.java",WicketFormSubmissionFixture.button)
            myFixture.addFileToProject("$directory/java/example/FormPage.java",WicketFormSubmissionFixture.page.replace("\"invoiceForm\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/FormPage.html",WicketFormSubmissionFixture.markup.replace("'invoiceForm'","'$directory'"))
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.FormPage",selected,includeNavigationContexts=true)
            val contexts=snapshot.navigationContexts!!.contexts.filter { it.report.submissionTemplate?.formCallback!=null }
            assertEquals(2,contexts.size)
            assertTrue(contexts.all { it.report.submissionTemplate!!.formComponentPath==directory && it.report.nodes.all { n->"/$directory/" in n.javaLocation.file } })
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            query.requireCurrent(project,snapshot)
        }
    }

    fun testRegisteredListFactoriesAndCallbacksStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/Decoration.java",WicketRegisteredListFixture.behavior)
            myFixture.addFileToProject("$directory/java/example/DecoratedList.java",WicketRegisteredListFixture.list)
            myFixture.addFileToProject("$directory/java/example/CallbackPage.java",WicketRegisteredListFixture.page.replace("\"body\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/CallbackPage.html",WicketRegisteredListFixture.markup.replace("'body'","'$directory'"))
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.CallbackPage",selected)
            assertEquals(snapshot.report.warnings.toString(),1,snapshot.report.populationContexts.size)
            assertTrue(snapshot.report.populationContexts.single().nodes.any { it.wicketId==directory })
            assertTrue(snapshot.report.nodes.single { it.wicketId=="sections" }.registeredListPopulation!!.callbackLocation.file.contains("/$directory/"))
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            query.requireCurrent(project,snapshot)
        }
    }

    fun testLazyTableToolbarSourcesRemainInTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected -> ModuleRootModificationUtil.updateModel(selected) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        } }
        for (directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/TableHost.java",WicketTableToolbarFixture.host.replace("\"content\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/TableHost.html",WicketTableToolbarFixture.hostMarkup)
            myFixture.addFileToProject("$directory/java/example/TableHost\$EmptyToolbar.html",WicketTableToolbarFixture.toolbarMarkup.replace("'content'","'$directory'"))
            myFixture.addFileToProject("$directory/java/example/EmptyView.java",WicketTableToolbarFixture.content)
            myFixture.addFileToProject("$directory/java/example/EmptyView.html",WicketTableToolbarFixture.contentMarkup)
            myFixture.addFileToProject("$directory/java/example/EmptyPage.java",WicketTableToolbarFixture.page)
            myFixture.addFileToProject("$directory/java/example/EmptyPage.html",WicketTableToolbarFixture.pageMarkup)
        }
        val query=WicketPageAnalysisRequest()
        for ((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.EmptyPage",selected,includeNavigationContexts=true)
            val templates=snapshot.navigationContexts!!.contexts.filter { it.relationship=="TABLE_TOOLBAR_TEMPLATE" }
            assertEquals(snapshot.navigationContexts.lifecycleDiagnostics.toString(),1,templates.size)
            val template=templates.single();assertEquals(selected,template.moduleName)
            assertEquals(4,template.bindingNodeIds.size);assertTrue(template.report.nodes.any { it.wicketId==directory })
            assertEquals("example.EmptyPage",template.report.qualifiedClassName)
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            assertTrue(template.report.toolbarTemplate!!.cacheLocation.file.contains("/$directory/"))
            query.requireCurrent(project,snapshot)
        }
    }

    fun testImmediateFactorySourcesAndSlotsStayWithinTheSelectedModule() {
        modules()
        for (directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/TableHost.java",WicketContentFactoryFixture.host.replace("\"toolbar\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/TableHost.html",WicketContentFactoryFixture.hostMarkup.replace("'toolbar'","'$directory'"))
            myFixture.addFileToProject("$directory/java/example/SearchField.java",WicketContentFactoryFixture.input)
            myFixture.addFileToProject("$directory/java/example/FactoryPage.java",WicketContentFactoryFixture.page)
            myFixture.addFileToProject("$directory/java/example/FactoryPage.html",WicketContentFactoryFixture.pageMarkup)
        }
        val query=WicketPageAnalysisRequest()
        for ((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.FactoryPage",selected,includeNavigationContexts=true)
            val templates=snapshot.navigationContexts!!.contexts.filter { it.relationship=="FACTORY_TEMPLATE" }
            assertEquals(snapshot.navigationContexts.lifecycleDiagnostics.toString(),1,templates.size)
            val template=templates.single()
            assertEquals(selected,template.moduleName)
            assertEquals("table:$directory",template.report.lifecycle!!.componentPath)
            assertEquals(4,template.bindingNodeIds.size)
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.none { "/$excluded/" in it.path })
            assertTrue(template.report.lifecycle.contentFactory!!.idLocation.file.contains("/$directory/"))
            query.requireCurrent(project,snapshot)
        }
    }

    fun testSelectionContextsStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach { selected ->
            ModuleRootModificationUtil.updateModel(selected) { model ->
                model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = com.intellij.pom.java.LanguageLevel.JDK_25
            }
        }
        for(directory in listOf("own","other")) {
            myFixture.addFileToProject("$directory/java/example/Flow.java",WicketSelectionFixture.flow.replace("\"body\"","\"$directory\""))
            myFixture.addFileToProject("$directory/java/example/Flow.html",WicketSelectionFixture.flowMarkup.replace("'body'","'$directory'"))
            myFixture.addFileToProject("$directory/java/example/ExamplePage.java",WicketSelectionFixture.page)
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketSelectionFixture.pageMarkup)
        }
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected,includeNavigationContexts=true)
            val selections=snapshot.navigationContexts!!.contexts.filter { it.relationship=="SELECTION_TEMPLATE" }
            assertEquals(snapshot.navigationContexts.lifecycleDiagnostics.toString(),2,selections.size); assertTrue(selections.all { it.report.lifecycle!!.componentPath=="flow:form:$directory" })
            val excluded=if(directory=="own") "/other/" else "/own/"
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }

    fun testReplacementEventRoutesAndOwnedMarkupRemainWithinTheSelectedModule() {
        modules()
        for (directory in listOf("own", "other")) {
            myFixture.addFileToProject("$directory/java/example/Host.java", """
                package example; public class Host extends org.apache.wicket.markup.html.panel.Panel {
                    private final org.apache.wicket.markup.html.form.Form<Void> form;
                    private java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> factory;
                    public Host(String id) { super(id); form=new org.apache.wicket.markup.html.form.Form<>("form"); add(form); form.add(new org.apache.wicket.markup.html.WebMarkupContainer("$directory")); }
                    public Host setFields(java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> factory) { this.factory=factory; return this; }
                    public void edit(org.apache.wicket.model.IModel<String> model) { org.apache.wicket.Component fields=factory.apply("$directory", model); open(fields); }
                    private void open(org.apache.wicket.Component content) { form.addOrReplace(content); }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/Host.html", "<wicket:panel><form wicket:id='form'><div wicket:id='$directory'></div></form></wicket:panel>")
            myFixture.addFileToProject("$directory/java/example/EventPage.java", """
                package example; public class EventPage extends org.apache.wicket.markup.html.WebPage {
                    public EventPage() { Host host=new Host("host").setFields(Fields::new); add(host); }
                    public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                        public Fields(String id, org.apache.wicket.model.IModel<String> model) { super(id,model); add(new org.apache.wicket.markup.html.basic.Label("field")); }
                    }
                }
            """)
            myFixture.addFileToProject("$directory/java/example/EventPage.html", "<div wicket:id='host'></div>")
            myFixture.addFileToProject("$directory/java/example/EventPage\$Fields.html", "<wicket:panel><b wicket:id='field'></b></wicket:panel>")
        }
        for ((selected, directory) in listOf(module.name to "own", "other" to "other")) {
            val snapshot = WicketPageAnalysisRequest().capture(project, "example.EventPage", selected, includeNavigationContexts = true)
            val event = snapshot.navigationContexts!!.contexts.single { it.relationship == "EVENT_TEMPLATE" }
            assertEquals("host:form:$directory", event.report.lifecycle!!.componentPath)
            val excluded = if (directory == "own") "/other/" else "/own/"
            assertTrue(event.report.javaSourceFiles.none { it.contains(excluded) })
            assertTrue(snapshot.version.sources.none { it.path.contains(excluded) })
        }
    }


    fun testToolbarCacheProofStaysWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketToolbarCacheFixture.shell else WicketToolbarCacheFixture.shell.replace("cache==null","cache!=null")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("new TableShell(\"shell\")","new TableShell(\"shell\").configure()").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected)
            assertEquals(snapshot.report.warnings.toString(),directory=="own",snapshot.report.warnings.any {"Reviewed source null-guarded owned toolbar cache" in it.message})
            assertEquals(snapshot.report.warnings.toString(),directory=="other",snapshot.report.warnings.any {it.message.startsWith("Source layout setup stopped:")})
            assertTrue(snapshot.version.sources.none {it.path.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testColumnsReadProofStaysWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketTableColumnsReadFixture.shell else WicketTableColumnsReadFixture.shell.replace("return columns.size();","columns.clear();return 0;")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("new TableShell(\"shell\")","new TableShell(\"shell\").configure()").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected)
            assertTrue(snapshot.report.warnings.any {"Reviewed owned DataTable columns getter" in it.message})
            assertEquals(snapshot.report.warnings.toString(),directory=="other",snapshot.report.warnings.any {it.message.startsWith("Source layout setup stopped:")})
            assertTrue(snapshot.version.sources.none {it.path.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testOwnedTableForwardingProofStaysWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {target ->
            ModuleRootModificationUtil.updateModel(target) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        }
        for(directory in listOf("own","other")) {
            val shell=if(directory=="own") WicketOwnedTableForwardingFixture.shell else WicketOwnedTableForwardingFixture.shell.replace("super(table);cell=","super(table);table.removeAll();cell=")
            myFixture.addFileToProject("$directory/java/example/TableShell.java",shell)
            myFixture.addFileToProject("$directory/java/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
            val java=myFixture.findFileInTempDir("$directory/java/example/ExamplePage.java")
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(java)!!.setText(WicketDataTableFixture.page.replace("TablePage","ExamplePage").replace("new TableShell(\"shell\")","new TableShell(\"shell\").configure()").replace("\"title\"","\"$directory\""))}
            myFixture.addFileToProject("$directory/java/example/ExamplePage.html",WicketDataTableFixture.markup.replace("title",directory))
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=WicketPageAnalysisRequest().capture(project,"example.ExamplePage",selected)
            assertEquals(snapshot.report.warnings.toString(),directory=="own",snapshot.report.warnings.any {"Reviewed owned DataTable reference forwarding" in it.message})
            assertTrue(snapshot.version.sources.none {it.path.contains(if(directory=="own") "/other/" else "/own/")})
        }
    }
    fun testSelectionClearHelperAndGetterStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {selected -> ModuleRootModificationUtil.updateModel(selected) {model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }}
        for(directory in listOf("own","other")) {
            val files=mapOf("DialogBox.java" to WicketConfirmationFixture.base,"ConfirmBox.java" to WicketConfirmationFixture.host,
                "Actions.java" to WicketConfirmationFixture.actions,"TableBox.java" to WicketSelectionClearFixture.table,
                "SelectionBox.java" to if(directory=="own") WicketSelectionClearFixture.column else WicketSelectionClearFixture.column.replace("return selection;","System.gc();return selection;"),
                "ConfirmationPage.java" to WicketSelectionClearFixture.page,"DialogBox.html" to WicketInitialContentFixture.hostMarkup,"ConfirmationPage.html" to WicketConfirmationFixture.markup)
            files.forEach {(name,text)->myFixture.addFileToProject("$directory/java/example/$name",text)}
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val snapshot=query.capture(project,"example.ConfirmationPage",selected,view=WicketMcpReport.NAVIGATION)
            val bulk=snapshot.navigationContexts!!.contexts.filter {it.report.submissionTemplate?.confirmation?.selectionClears?.isNotEmpty()==true}
            assertEquals(directory,if(directory=="own") 1 else 0,bulk.size)
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(snapshot.version.sources.toString(),snapshot.version.sources.none {"/$excluded/" in it.path})
            bulk.forEach {context ->val operation=context.report.submissionTemplate!!.confirmation!!.selectionClears.single()
                assertTrue("/$directory/" in operation.helperLocation.file);assertTrue("/$directory/" in operation.getterLocation.file)
            }
            query.requireCurrent(project,snapshot)
        }
    }
    fun testRecordActionHelpersAndEnumArgumentsStayWithinTheSelectedModule() {
        modules()
        com.intellij.openapi.module.ModuleManager.getInstance(project).modules.forEach {selected -> ModuleRootModificationUtil.updateModel(selected) {model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }}
        for(directory in listOf("own","other")) {
            val page=if(directory=="own") WicketRecordActionFixture.page else WicketRecordActionFixture.page.replace("{return new Row","{System.gc();return new Row")
            mapOf("DialogBox.java" to WicketConfirmationFixture.base,"ConfirmBox.java" to WicketConfirmationFixture.host,
                "Actions.java" to WicketConfirmationFixture.actions,"TableBox.java" to WicketSelectionClearFixture.table,
                "SelectionBox.java" to WicketSelectionClearFixture.column,"ConfirmationPage.java" to page,
                "DialogBox.html" to WicketInitialContentFixture.hostMarkup,"ConfirmationPage.html" to WicketConfirmationFixture.markup).forEach {(name,text)->myFixture.addFileToProject("$directory/java/example/$name",text)}
        }
        val query=WicketPageAnalysisRequest()
        for((selected,directory) in listOf(module.name to "own","other" to "other")) {
            val s=query.capture(project,"example.ConfirmationPage",selected,view=WicketMcpReport.NAVIGATION)
            val actions=s.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}
            assertEquals(directory,if(directory=="own") 2 else 0,actions.size)
            val excluded=if(directory=="own") "other" else "own"
            assertTrue(s.version.sources.toString(),s.version.sources.none {"/$excluded/" in it.path})
            actions.forEach {context ->val update=context.report.actionTemplate!!.updates.single()
                assertTrue("/$directory/" in update.methodLocation.file);assertTrue("/$directory/" in update.transformMethodLocation.file)
                assertTrue(update.componentArguments.all {"/$directory/" in it.declarationLocation.file})
            }
            query.requireCurrent(project,s)
        }
    }
}
