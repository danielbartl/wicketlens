package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.spring.WicketSpringSetup
import java.nio.file.Files
import java.nio.file.Path

class WicketSpringDemoTest : WicketSpringPsiTestCase() {
    fun testBeanNameDemoUsesAliasesAndReportsOnePositiveSourceTypeMismatch() {
        val root = Path.of(System.getProperty("wicketlens.test.springDemo"))
        val files = Files.list(root.resolve("main/java/example")).use { paths -> paths.filter { it.toString().endsWith(".java") }.toList() }
            .associate { path -> path.fileName.toString() to myFixture.addFileToProject("src/main/java/example/${path.fileName}", Files.readString(path)) }
        val java = files.getValue("BeanNamesPage.java") as PsiJavaFile
        val holder = com.intellij.codeInspection.ProblemsHolder(com.intellij.codeInspection.InspectionManager.getInstance(project), java, false)
        dev.wicketlens.spring.WicketSpringBeanTypeInspection().buildVisitor(holder, false).visitFile(java)
        assertEquals("\"message\"", holder.results.single().psiElement.text)
        val alias = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java, com.intellij.psi.PsiLiteralExpression::class.java).single { it.value == "orders" }
        val target = alias.references.filterIsInstance<dev.wicketlens.spring.WicketSpringBeanReference>().single().resolve() as com.intellij.psi.PsiMethod
        assertEquals("orderService", target.name)
        companion("BeanNamesPage", Files.readString(root.resolve("main/resources/example/BeanNamesPage.html")))
        assertEquals(DiagnosticCode.MATCHED, WicketPageAnalyzer().analyze(java.classes.single())!!.matching!!.findings.single().code)
    }

    fun testAnnotationDemoSeparatesMissingInjectionFromAnExplicitlyWiredModel() {
        val root = Path.of(System.getProperty("wicketlens.test.springDemo"))
        val files = Files.list(root.resolve("main/java/example")).use { paths -> paths.filter { it.toString().endsWith(".java") }.toList() }
            .associate { path -> path.fileName.toString() to myFixture.addFileToProject("src/main/java/example/${path.fileName}", Files.readString(path)) }
        val anchors = listOf("AnnotationExamplesPage", "MissingInjectionModel", "MissingInjectionSession", "InjectedOrderModel").map { name ->
            val java = files.getValue("$name.java")
            val holder = com.intellij.codeInspection.ProblemsHolder(com.intellij.codeInspection.InspectionManager.getInstance(project), java, false)
            dev.wicketlens.spring.WicketSpringAnnotationInspection().buildVisitor(holder, false).visitFile(java)
            holder.results.map { it.psiElement.text }
        }
        assertEquals(listOf(listOf("Autowired"), listOf("SpringBean"), listOf("SpringBean"), emptyList<String>()), anchors)
        companion("AnnotationExamplesPage", Files.readString(root.resolve("main/resources/example/AnnotationExamplesPage.html")))
        val report = WicketPageAnalyzer().analyze((files.getValue("AnnotationExamplesPage.java") as PsiJavaFile).classes.single())!!
        assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
    }

    fun testSeparateDemoHasFourExpectedInspectionFindingsAndAnUnaffectedBinding() {
        val root = Path.of(System.getProperty("wicketlens.test.springDemo"))
        val files = listOf("OrderService", "FinalAuditService", "DemoSpringConfig", "SpringDemoApplication", "SpringDemoPage").associateWith { name ->
            myFixture.addFileToProject("src/main/java/example/$name.java", Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("SpringDemoPage", Files.readString(root.resolve("main/resources/example/SpringDemoPage.html")))
        val java = files.getValue("SpringDemoPage") as PsiJavaFile
        assertEquals(WicketSpringSetup.Proxies.WRAPPED, WicketSpringSetup.forFile(java).proxies)
        val injection = dev.wicketlens.spring.WicketSpringInjectionInspection()
        val serialization = dev.wicketlens.spring.WicketSpringSerializationInspection()
        val anchors = listOf(injection, serialization).map { tool ->
            val holder = com.intellij.codeInspection.ProblemsHolder(com.intellij.codeInspection.InspectionManager.getInstance(project), java, false)
            tool.buildVisitor(holder, false).visitFile(java)
            holder.results.map { it.psiElement.text }
        }
        assertEquals(listOf("null", "orders"), anchors[0])
        assertEquals(listOf("archivedOrders", "FinalAuditService"), anchors[1])
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(listOf("title"), report.children().map { it.wicketId })
        assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
        assertFalse(report.nodes.any { it.wicketId == "orders" })
    }
}
