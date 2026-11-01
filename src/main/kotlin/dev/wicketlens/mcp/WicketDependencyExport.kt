package dev.wicketlens.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.spring.WicketDependencySnapshot

/** Explicit projection of the sidebar's value snapshot. No PSI, Spring discovery, or reflection here. */
internal object WicketDependencyExport {
    fun render(snapshot: WicketDependencySnapshot) = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("evidence", "source_candidates")
        addProperty("runtimeBindingsVerified", false)
        addProperty("profileActivationEvaluated", false)
        addProperty("registeredApplication", snapshot.application)
        addProperty("truncated", snapshot.truncated)
        addProperty("fieldCount", snapshot.fieldCount)
        addProperty("discoveryLimited", snapshot.truncated || snapshot.owners.any { it.partial || it.fields.any { field -> field.candidatesTruncated } })
        add("scopeNotes", strings(listOf(
            "Only injection fields on the analyzed page and discovered source component types are inspected.",
            "Bean candidates come from the analyzed page's module and production/test source set.",
            "An empty result does not establish Spring absence or a missing bean. Runtime selection, conversions and availability are not verified.",
            "Profile expressions are declared conditions, not resolved active profiles; FactoryBean products use generic source types when known.",
        )))
        add("owners", array(snapshot.owners) { owner ->
            JsonObject().apply {
                addProperty("nodeId", owner.id)
                addProperty("displayPath", owner.path)
                addProperty("componentType", owner.type)
                add("location", location(owner.location))
                addProperty("partial", owner.partial)
                add("fields", array(owner.fields) { field ->
                    JsonObject().apply {
                        addProperty("id", field.id)
                        addProperty("name", field.name)
                        addProperty("type", field.type)
                        addProperty("declaringClass", field.declaringClass)
                        addProperty("annotation", field.annotation)
                        addProperty("lookup", field.lookup.name)
                        addProperty("beanName", field.beanName)
                        addProperty("required", field.required)
                        add("location", location(field.location))
                        add("notes", strings(field.notes))
                        addProperty("candidatesTruncated", field.candidatesTruncated)
                        add("candidates", array(field.candidates) { candidate ->
                            JsonObject().apply {
                                add("names", strings(candidate.names))
                                addProperty("type", candidate.type)
                                addProperty("declaringClass", candidate.declaringClass)
                                add("location", location(candidate.location))
                                addProperty("compatible", candidate.compatible)
                                addProperty("conditional", candidate.conditional)
                                addProperty("factory", candidate.factory)
                                addProperty("factoryLookup", candidate.factoryLookup)
                                addProperty("declaredFactoryType", candidate.declaredFactoryType)
                                add("profiles", strings(candidate.profiles))
                                addProperty("profilesUnknown", candidate.profilesUnknown)
                            }
                        })
                    }
                })
            }
        })
    }

    private fun location(value: SourceLocation) = JsonObject().apply {
        addProperty("file", value.file)
        addProperty("offset", value.offset)
    }
    private fun strings(values: List<String>) = JsonArray().apply { values.forEach(::add) }
    private fun <T> array(values: List<T>, convert: (T) -> JsonElement) = JsonArray().apply {
        values.forEach { ProgressManager.checkCanceled(); add(convert(it)) }
    }
}
