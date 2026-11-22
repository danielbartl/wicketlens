package dev.wicketlens.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.spring.PreparedSpringTest
import dev.wicketlens.spring.WicketSpringTestGenerator
import dev.wicketlens.testing.WicketTestGenerator
import dev.wicketlens.testing.WicketTestAuthentication

/** Project the existing deterministic recipe; never resolve production beans or create a test. */
object WicketSpringTestPlanExport {
    fun render(prepared: PreparedSpringTest): JsonObject = WicketTestPlanExport.render(prepared)
}

object WicketTestPlanExport {
    fun render(prepared: PreparedSpringTest): JsonObject = JsonObject().apply {
        val plan = prepared.plan
        fun strings(values: List<String>) = JsonArray().apply { values.forEach { ProgressManager.checkCanceled(); add(it) } }
        fun source(at: SourceLocation) = JsonObject().apply { addProperty("file", at.file); addProperty("offset", at.offset) }
        addProperty("schemaVersion", 1)
        addProperty("kind", when {
            plan.mounted?.resource != null -> "mounted_resource_wicket_tester_scaffold"
            plan.mounted != null -> "mounted_wicket_tester_scaffold"
            plan.spring -> "isolated_spring_wicket_tester_scaffold"
            else -> "isolated_wicket_tester_scaffold"
        })
        plan.mounted?.let { route ->
            add("mountedRoute", JsonObject().apply {
                addProperty("applicationClass", route.applicationClass)
                addProperty("declarationPath", route.declarationPath)
                addProperty("requestPath", route.requestPath)
                add("parameters", JsonObject().apply { route.parameters.forEach { (key, value) -> addProperty(key, value) } })
                route.withoutOptionalParameters?.let { omitted ->
                    add("withoutOptionalParameters", JsonObject().apply {
                        addProperty("requestPath", omitted.requestPath)
                        add("parameters", JsonObject().apply { omitted.parameters.forEach { (key, value) -> addProperty(key, value) } })
                        add("absentParameters", strings(omitted.absentParameters))
                    })
                }
                route.resource?.expectedContentType?.let { addProperty("expectedContentType", it) }
                route.resource?.expectedBodySha256?.let { addProperty("expectedBodySha256", it.lowercase()) }
                add("registration", source(route.registration))
                route.session?.let { session -> add("session", JsonObject().apply {
                    addProperty("className", session.className)
                    add("declaration", source(session.declaration))
                    add("registration", source(session.registration))
                    session.authentication?.let { auth -> add("authentication", JsonObject().apply {
                        addProperty("signInPage", auth.signInPage)
                        add("signInPageDeclaration", source(auth.signInPageDeclaration))
                        add("signInPageRegistration", source(auth.signInPageRegistration))
                        add("declaredRoles", strings(auth.roles))
                        add("authorization", source(auth.authorization))
                        addProperty("usernameEnvironmentVariable", WicketTestAuthentication.USERNAME_ENV)
                        addProperty("passwordEnvironmentVariable", WicketTestAuthentication.PASSWORD_ENV)
                    }) }
                }) }
            })
        }
        addProperty("runtimeValidityVerified", false)
        addProperty("dependencyAvailabilityChecked", false)
        addProperty("packageName", plan.packageName)
        addProperty("targetClass", plan.target)
        addProperty("suggestedTestName", plan.testName)
        addProperty("constructorStrategy", plan.constructor.name)
        add("mocks", JsonArray().apply {
            plan.mocks.forEach { mock ->
                ProgressManager.checkCanceled()
                add(JsonObject().apply { addProperty("beanName", mock.name); addProperty("type", mock.type) })
            }
        })
        add("manualBindings", strings(plan.manual))
        plan.dependencyGraph?.let { graph -> add("dependencyGraph", JsonObject().apply {
            addProperty("discoveryLimited", graph.discoveryLimited)
            add("owners", JsonArray().apply { graph.owners.forEach { owner ->
                ProgressManager.checkCanceled()
                add(JsonObject().apply {
                    addProperty("nodeId", owner.nodeId)
                    addProperty("componentPath", owner.path)
                    addProperty("componentType", owner.type)
                    add("location", source(owner.location))
                    add("fields", JsonArray().apply { owner.fields.forEach { field ->
                        ProgressManager.checkCanceled()
                        add(JsonObject().apply {
                            addProperty("id", field.id)
                            addProperty("name", field.name)
                            addProperty("type", field.type)
                            addProperty("declaringClass", field.declaringClass)
                            addProperty("lookup", field.lookup.name)
                            add("location", source(field.location))
                            field.injectorRegistration?.let { add("injectorRegistration", source(it)) }
                            addProperty("sourceCandidateCount", field.sourceCandidateCount)
                            addProperty("candidatesTruncated", field.candidatesTruncated)
                            addProperty("testBinding", if (field.mock == null) "MANUAL" else "MOCK")
                            field.mock?.let { addProperty("mockName", it.name) }
                        })
                    } })
                })
            } })
        }) }
        add("notes", strings(plan.notes))
        add("requiredDependencies", strings(if (plan.spring) WicketSpringTestGenerator.REQUIRED_DEPENDENCIES else WicketTestGenerator.requiredDependencies(plan.mounted)))
        add("scopeNotes", strings(listOf(
            if (plan.mounted != null) "The generated test exercises the selected source application through WicketTester. Review initialization and sample URL values before running."
            else if (plan.spring) "Existing deterministic Spring test scaffold, not a runnable-test or runtime-wiring guarantee. Review all TODOs, mock behavior and constructor/application setup."
            else "Deterministic plain Wicket test scaffold, not a runnable-test guarantee. Review constructor arguments, application/session setup and any dependency injection outside the recognized Spring subset.",
            if (plan.spring) "Source production bean candidates and declared profiles do not select bindings in the isolated test context."
            else "No Spring or Mockito setup is included. Classpath presence alone does not select the Spring recipe.",
            "Test-root paths are suggestions from existing writable non-generated Java test roots in the selected module; name collisions and current writability must be checked before any separate file creation.",
            "No build files, sources, roots, inspection settings, sidebar or clipboard are changed. No test, application, Spring context or model is executed."
        )))
        add("testRoots", JsonArray().apply {
            prepared.roots.forEach { root ->
                ProgressManager.checkCanceled()
                add(JsonObject().apply { addProperty("path", root.path); addProperty("packagePrefix", root.prefix) })
            }
        })
        addProperty("suggestedFileName", "${plan.testName}.java")
        addProperty("language", "java")
        addProperty("source", WicketSpringTestGenerator().render(plan))
    }
}
