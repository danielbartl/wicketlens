package dev.wicketlens.testing

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketSourceFiles
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.routes.WicketRouteApplication
import dev.wicketlens.routes.WicketRoutesSnapshot
import dev.wicketlens.routes.MountStatus
import dev.wicketlens.routes.WicketRouteMappers
import dev.wicketlens.routes.WicketMount
import dev.wicketlens.routes.WicketRoutesRequest
import dev.wicketlens.spring.WicketSpringSetup
import dev.wicketlens.spring.PreparedSpringTest
import dev.wicketlens.spring.SpringTestConstructor
import dev.wicketlens.spring.SpringTestPlan
import dev.wicketlens.spring.WicketSpringTestFiles

data class WicketMountedTestSelection(val routePath: String, val applicationClass: String? = null,
    val routeParameters: Map<String, String> = emptyMap())

data class WicketMountedRequest(
    val requestPath: String,
    val parameters: Map<String, String>,
    val absentParameters: List<String>,
)

data class WicketMountedResource(val expectedContentType: String? = null, val expectedBodySha256: String? = null) {
    companion object {
        private val MIME = Regex("[A-Za-z0-9!#&^_.+-]+/[A-Za-z0-9!#&^_.+-]+")
        private val SHA256 = Regex("[0-9a-fA-F]{64}")
        fun validContentType(value: String?) = value == null || value.length <= 128 && MIME.matches(value)
        fun validBodySha256(value: String?) = value == null || SHA256.matches(value)
    }
}

internal data class PreparedMountedResource(val test: PreparedSpringTest, val moduleName: String, val sourceSet: String)

data class WicketMountedTest(
    val applicationClass: String,
    val declarationPath: String,
    val requestPath: String,
    val parameters: Map<String, String>,
    val registration: SourceLocation,
    val withoutOptionalParameters: WicketMountedRequest? = null,
    val resource: WicketMountedResource? = null,
    val session: WicketTestSession? = null,
)

internal object WicketMountedTests {
    private val literal = Regex("[A-Za-z0-9._~-]+")
    private val placeholder = Regex("([#$])\\{([A-Za-z_][A-Za-z0-9_-]{0,63})}")
    private val parameterName = Regex("[A-Za-z_][A-Za-z0-9_-]{0,63}")
    private val unreviewedNotes = listOf(WicketRouteMappers.EXPLICIT_MAPPER_CONFIGURATION, "wraps this declaration", "wraps the root")

    fun supports(mount: WicketMount): Boolean {
        if (mount.status != MountStatus.DECLARED || mount.conflict != null ||
            mount.notes.any { note -> unreviewedNotes.any { note.contains(it, ignoreCase = true) } }) return false
        val requests = requests(mount.path) ?: return false
        return if (mount.nonPageTarget == "Resource") requests.first.parameters.isEmpty() else mount.pageSource != null
    }

    fun prepare(project: Project, subject: TestSubject, selection: WicketMountedTestSelection):
        Pair<WicketMountedTest, WicketRoutesSnapshot> {
        val index = ProjectFileIndex.getInstance(project)
        val file = subject.file.virtualFile
        val module = index.getModuleForFile(file)?.name ?: throw AnalysisUnavailable("The mounted page has no source module.")
        val sourceSet = if (index.isInTestSourceContent(file)) "test" else "main"
        val snapshot = captureRoutes(project, module, selection.applicationClass, sourceSet)
        val candidates = snapshot.routes.applications.flatMap { application ->
            application.mounts.filter {
                it.path == selection.routePath && it.pageClass == subject.type.qualifiedName && it.pageSource?.file == file.path
            }.map { application to it }
        }
        if (candidates.size > 1)
            throw AnalysisUnavailable("The mount is ambiguous. Select a unique path/application in Mounted URLs or supply applicationClass.")
        val (application, mount) = candidates.singleOrNull()
            ?: throw AnalysisUnavailable("No matching page mount was found in the selected module and source set.")
        requireMount(application, mount, "Choose an unconditional, nonconflicting page mount with fixed or supported parameter segments and the standard mapper.")
        val (_, type) = applicationFile(project, application, subject.file.packageName)
        val (full, omitted) = requestedExamples(selection.routePath, selection.routeParameters)
        return WicketMountedTest(application.className, selection.routePath, full.requestPath, full.parameters,
            mount.registration, omitted, session = WicketTestSessions.discover(type, subject.type)) to snapshot
    }

    fun prepareResource(project: Project, applicationClass: String, path: String, module: String?, sourceSet: String,
        resource: WicketMountedResource): PreparedMountedResource {
        if (!WicketMountedResource.validContentType(resource.expectedContentType))
            throw AnalysisUnavailable("Enter a concrete media type such as image/svg+xml, without parameters.")
        if (!WicketMountedResource.validBodySha256(resource.expectedBodySha256))
            throw AnalysisUnavailable("Enter a SHA-256 hash of 64 hexadecimal characters.")
        val snapshot = captureRoutes(project, module, applicationClass, sourceSet)
        val application = snapshot.routes.applications.singleOrNull()
            ?: throw AnalysisUnavailable("Select the module and source set for one unambiguous resource application.")
        val mount = application.mounts.singleOrNull { it.path == path && it.nonPageTarget == "Resource" }
            ?: throw AnalysisUnavailable("Select a unique mounted resource path in the named application.")
        requireMount(application, mount, "Choose an unconditional, nonconflicting resource mount with a fixed path and the standard mapper.")
        val (appFile, type) = applicationFile(project, application)
        val sourceUrl = appFile.virtualFile.url
        val request = requireNotNull(requests(mount.path)).first
        val route = WicketMountedTest(application.className, path, request.requestPath, request.parameters, mount.registration,
            resource = resource)
        val plan = SpringTestPlan(appFile.packageName, applicationClass, "${type.name}MountedResourceTest",
            SpringTestConstructor.MOUNTED_RESOURCE, emptyList(), emptyList(), listOf(
                "Requests $path through the selected source application and checks HTTP 200 and Content-Type.",
                "Review application startup and service configuration before running. Use your project's WicketTester and JUnit Jupiter dependencies.",
            ), spring = false, mounted = route)
        val prepared = PreparedSpringTest(plan, sourceUrl, snapshot.version, WicketSpringTestFiles.roots(project, sourceUrl, appFile.packageName),
            snapshot.structureStamp)
        return PreparedMountedResource(prepared, application.moduleName, application.sourceSet)
    }

    private fun captureRoutes(project: Project, module: String?, applicationClass: String?, sourceSet: String): WicketRoutesSnapshot {
        val snapshot = try {
            WicketRoutesRequest().capture(project, module, applicationClass, sourceSet)
        } catch (failure: PageAnalysisFailure) {
            throw AnalysisUnavailable(failure.message ?: "The selected application is unavailable.")
        }
        if (snapshot.routes.truncated)
            throw AnalysisUnavailable("Mounted URL discovery reached its limits. Narrow the test to an exact application/module and try again.")
        return snapshot
    }

    private fun requireMount(application: WicketRouteApplication, mount: WicketMount, message: String) {
        if (!supports(mount) || application.mounts.count { it.path == mount.path } != 1) throw AnalysisUnavailable(message)
    }

    private fun applicationFile(project: Project, application: WicketRouteApplication, testPackage: String? = null): Pair<PsiJavaFile, PsiClass> {
        val appFile = WicketSourceFiles.find(project, application.source.file)
            ?.let { PsiManager.getInstance(project).findFile(it) as? PsiJavaFile }
            ?: throw AnalysisUnavailable("The selected application source is unavailable.")
        val type = appFile.classes.singleOrNull { it.qualifiedName == application.className }
        if (type == null || !constructible(type, appFile, testPackage ?: appFile.packageName))
            throw AnalysisUnavailable("The mounted test needs a named top-level source application with an accessible no-argument constructor.")
        if (WicketSpringSetup.forFile(appFile).registered)
            throw AnalysisUnavailable("This application uses Spring setup. Mounted test scaffolding currently covers plain Wicket applications.")
        return appFile to type
    }

    private fun constructible(type: PsiClass, file: PsiJavaFile, testPackage: String): Boolean {
        val samePackage = file.packageName == testPackage
        if (type.typeParameters.isNotEmpty() ||
            !samePackage && (file.packageName.isEmpty() || !type.hasModifierProperty(PsiModifier.PUBLIC))) return false
        return type.constructors.isEmpty() || type.constructors.any {
            it.parameterList.isEmpty && !it.hasModifierProperty(PsiModifier.PRIVATE) &&
                (samePackage || it.hasModifierProperty(PsiModifier.PUBLIC))
        }
    }

    fun validParameterValue(value: String): Boolean =
        value.length in 1..64 && literal.matches(value) && value != "." && value != ".."

    fun validParameterInput(values: Map<String, String>): Boolean = values.size <= 12 && values.all { (name, value) ->
        parameterName.matches(name) && validParameterValue(value)
    }

    fun withExamples(route: WicketMountedTest, values: Map<String, String>): WicketMountedTest {
        val (full, omitted) = requestedExamples(route.declarationPath, values)
        return route.copy(requestPath = full.requestPath, parameters = full.parameters, withoutOptionalParameters = omitted)
    }

    private fun requestedExamples(path: String, values: Map<String, String>): Pair<WicketMountedRequest, WicketMountedRequest?> {
        if (!validParameterInput(values))
            throw AnalysisUnavailable("Route examples must be nonempty URL-safe path segments of up to 64 characters.")
        val (full, omitted) = requests(path, values)
            ?: throw AnalysisUnavailable("Route examples must differ from fixed path segments and fit the mounted path limit.")
        if (values.keys.any { it !in full.parameters })
            throw AnalysisUnavailable("Route examples must name placeholders declared on the selected mount.")
        return full to omitted
    }

    private fun requests(path: String?, examples: Map<String, String> = emptyMap()): Pair<WicketMountedRequest, WicketMountedRequest?>? {
        if (path == null || path.length !in 1..512) return null
        val segments = path.removePrefix("/").split('/')
        if (segments.size > 24) return null
        val fixed = segments.filter(literal::matches).toSet()
        if (examples.values.any { it in fixed }) return null
        val parameters = linkedMapOf<String, String>()
        val optional = mutableListOf<String>()
        val full = mutableListOf<String>()
        val omitted = mutableListOf<String>()
        var nextSample = 42
        for (segment in segments) {
            if (segment in fixed && segment != "." && segment != "..") {
                full += segment
                omitted += segment
                continue
            }
            val match = placeholder.matchEntire(segment) ?: return null
            val (token, name) = match.destructured
            if (name in parameters || parameters.size >= 12) return null
            val example = examples[name] ?: run {
                while (nextSample.toString() in fixed || nextSample.toString() in examples.values) nextSample++
                (nextSample++).toString()
            }
            parameters[name] = example
            full += example
            if (token == "#") optional += name else omitted += example
        }
        val withAll = WicketMountedRequest(full.joinToString("/"), parameters, emptyList())
        if (withAll.requestPath.length > 512) return null
        if (optional.isEmpty()) return withAll to null
        if (omitted.isEmpty()) return null
        return withAll to WicketMountedRequest(omitted.joinToString("/"), parameters - optional.toSet(), optional)
    }
}
