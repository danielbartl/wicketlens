package dev.wicketlens.testing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.PageReport
import dev.wicketlens.spring.*
import dev.wicketlens.ui.ReportVersion

/** Local to the committed read action; prepared recipes retain values only. */
internal data class TestSubject(val file: PsiJavaFile, val type: PsiClass, val constructor: SpringTestConstructor)

internal object WicketTestSubjects {
    fun select(project: Project, report: PageReport, target: EditorPageTarget): TestSubject {
        val virtual = VirtualFileManager.getInstance().findFileByUrl(target.sourceUrl)
            ?: throw AnalysisUnavailable("The Java source is unavailable.")
        val file = PsiManager.getInstance(project).findFile(virtual) as? PsiJavaFile
            ?: throw AnalysisUnavailable("Select a Java Wicket source.")
        val type = PsiTreeUtil.findChildrenOfType(file, PsiClass::class.java).singleOrNull { it.qualifiedName == report.qualifiedClassName }
            ?: throw AnalysisUnavailable("The selected class is unavailable.")
        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null)
            throw AnalysisUnavailable("Fix Java syntax errors before generating the test scaffold.")
        if (type.hasModifierProperty(PsiModifier.ABSTRACT) || type.containingClass != null || type.typeParameters.isNotEmpty())
            throw AnalysisUnavailable("Select a concrete, top-level Wicket class without type parameters for this test scaffold.")
        val page = InheritanceUtil.isInheritor(type, "org.apache.wicket.markup.html.WebPage")
        val constructors = type.constructors.filterNot { it.hasModifierProperty(PsiModifier.PRIVATE) }
        fun accepts(name: String) = constructors.any { it.parameterList.parameters.singleOrNull()?.type?.canonicalText == name }
        val strategy = when {
            page && (type.constructors.isEmpty() || constructors.any { it.parameterList.isEmpty }) -> SpringTestConstructor.PAGE
            page && accepts("org.apache.wicket.request.mapper.parameter.PageParameters") -> SpringTestConstructor.PARAMETERS
            !page && accepts("java.lang.String") -> SpringTestConstructor.COMPONENT
            page -> SpringTestConstructor.MANUAL_PAGE
            else -> SpringTestConstructor.MANUAL_COMPONENT
        }
        return TestSubject(file, type, strategy)
    }
}

/** General planning: plain Wicket by default, existing Spring recipe only with recognized intent. */
class WicketTestGenerator {
    fun prepare(project: Project, sourceUrl: String, offset: Int): PreparedSpringTest {
        val target = WicketEditorTargetResolver().resolve(project, sourceUrl, offset)
        val report = WicketPageSelection().analyze(project, target.sourceUrl, target.caretOffset, target.qualifiedClassName, target.markupSuffix)
        return prepare(project, report, target)
    }

    fun prepare(project: Project, report: PageReport, target: EditorPageTarget): PreparedSpringTest {
        val subject = WicketTestSubjects.select(project, report, target)
        val dependencies = WicketDependencyAnalyzer().analyze(project, report)
        if (dependencies.fieldCount > 0 || WicketSpringSetup.forFile(subject.file).registered)
            return WicketSpringTestGenerator().prepare(project, report, target, dependencies)
        val notes = mutableListOf(
            "Uses a separate test WebApplication. Review application sessions, authentication, routes, resources, dependency injection and constructor behavior before running.",
            "Required test dependencies: ${REQUIRED_DEPENDENCIES.joinToString()}. Use the project's Wicket version and a JUnit Jupiter runner; availability is not checked and build files are not changed.")
        if (target.markupSuffix.isNotEmpty()) notes += "A markup variant was selected. Configure locale/style in the test to select that variant."
        if (subject.constructor in setOf(SpringTestConstructor.MANUAL_PAGE, SpringTestConstructor.MANUAL_COMPONENT))
            notes += "Implement createSubject() with the required constructor arguments."
        val plan = SpringTestPlan(subject.file.packageName, report.qualifiedClassName, "${subject.type.name}Test",
            subject.constructor, emptyList(), emptyList(), notes, spring = false)
        return PreparedSpringTest(plan, target.sourceUrl,
            ReportVersion.capture(project, report, target.sourceUrl, 0, dependencies.sourceFiles),
            WicketSpringTestFiles.roots(project, target.sourceUrl, subject.file.packageName))
    }

    companion object {
        val REQUIRED_DEPENDENCIES = listOf("org.apache.wicket:wicket-tester", "jakarta.servlet:jakarta.servlet-api", "org.junit.jupiter:junit-jupiter")
    }
}
