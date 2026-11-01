package dev.wicketlens.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.spring.PreparedSpringTest
import dev.wicketlens.spring.WicketSpringTestGenerator
import dev.wicketlens.testing.WicketTestGenerator

/** Project the existing deterministic recipe; never resolve production beans or create a test. */
object WicketSpringTestPlanExport {
    fun render(prepared: PreparedSpringTest): JsonObject = WicketTestPlanExport.render(prepared)
}

object WicketTestPlanExport {
    fun render(prepared: PreparedSpringTest): JsonObject = JsonObject().apply {
        val plan = prepared.plan
        fun strings(values: List<String>) = JsonArray().apply { values.forEach { ProgressManager.checkCanceled(); add(it) } }
        addProperty("schemaVersion", 1)
        addProperty("kind", if (plan.spring) "isolated_spring_wicket_tester_scaffold" else "isolated_wicket_tester_scaffold")
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
        add("notes", strings(plan.notes))
        add("requiredDependencies", strings(if (plan.spring) WicketSpringTestGenerator.REQUIRED_DEPENDENCIES else WicketTestGenerator.REQUIRED_DEPENDENCIES))
        add("scopeNotes", strings(listOf(
            if (plan.spring) "Existing deterministic Spring test scaffold, not a runnable-test or runtime-wiring guarantee. Review all TODOs, mock behavior and constructor/application setup."
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
