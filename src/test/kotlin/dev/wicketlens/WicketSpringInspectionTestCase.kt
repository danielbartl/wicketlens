package dev.wicketlens

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.spring.WicketSpringInjectionInspection
import dev.wicketlens.spring.WicketSpringSerializationInspection

abstract class WicketSpringInspectionTestCase : WicketSpringPsiTestCase() {
    override fun setUp() {
        super.setUp()
        source("Service", "public class Service {}")
    }

    protected fun source(name: String, declaration: String, directory: String = "src/main/java") =
        myFixture.addFileToProject("$directory/example/$name.java", "package example; $declaration") as PsiJavaFile

    protected fun page(members: String, body: String = "", base: String = "org.apache.wicket.markup.html.WebPage", name: String = "ExamplePage") =
        source(name, """
            import org.apache.wicket.spring.injection.annot.SpringBean;
            import jakarta.inject.Inject;
            public class $name extends $base {
                $members
                public $name() { $body }
            }
        """.trimIndent())

    protected fun application(name: String = "SpringApplication", arguments: String = "this, new org.springframework.context.support.GenericApplicationContext()",
        statements: String? = null, extra: String = "", directory: String = "src/main/java") = source(name, """
            public class $name extends org.apache.wicket.protocol.http.WebApplication {
                @Override protected void init() {
                    ${statements ?: "super.init(); getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector($arguments));"}
                }
                @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return org.apache.wicket.markup.html.WebPage.class; }
                $extra
            }
        """.trimIndent(), directory)

    protected fun injection(file: PsiFile) = problems(file, WicketSpringInjectionInspection())
    protected fun serialization(file: PsiFile) = problems(file, WicketSpringSerializationInspection())
    protected fun problems(file: PsiFile, inspection: LocalInspectionTool) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        inspection.buildVisitor(it, false).visitFile(file)
    }.results

    protected fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
