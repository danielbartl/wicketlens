package dev.wicketlens.testing

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.spring.DependencyLookup
import dev.wicketlens.spring.InjectedField
import dev.wicketlens.spring.SpringTestMock
import dev.wicketlens.spring.WicketDependencySnapshot

data class WicketTestDependencyGraph(val owners: List<WicketTestDependencyOwner>, val discoveryLimited: Boolean) {
    companion object {
        internal fun from(snapshot: WicketDependencySnapshot, mocks: Map<String, SpringTestMock> = emptyMap()): WicketTestDependencyGraph? {
            if (snapshot.owners.isEmpty() && !snapshot.truncated) return null
            val owners = snapshot.owners.map { owner ->
                ProgressManager.checkCanceled()
                WicketTestDependencyOwner(owner.id, owner.path, owner.type, owner.location, owner.fields.map { field ->
                    ProgressManager.checkCanceled()
                    WicketTestDependencyField(field.id, field.name, field.type, field.declaringClass,
                        field.lookup, field.location, field.injectorLocation, field.candidates.size, field.candidatesTruncated, mocks[field.id])
                })
            }
            return WicketTestDependencyGraph(owners,
                snapshot.truncated || snapshot.owners.any { it.partial || it.fields.any(InjectedField::candidatesTruncated) })
        }

        internal fun externalSetupNotes(snapshot: WicketDependencySnapshot): List<String> = snapshot.owners.flatMap { it.fields }
            .filter { it.injectorLocation != null }.distinctBy { it.location }.map { field ->
                "${field.declaringClass}.${field.name} requires an explicit test Guice binding and injector setup."
            }
    }
}

data class WicketTestDependencyOwner(val nodeId: String, val path: String, val type: String, val location: SourceLocation,
    val fields: List<WicketTestDependencyField>)

data class WicketTestDependencyField(val id: String, val name: String, val type: String, val declaringClass: String,
    val lookup: DependencyLookup, val location: SourceLocation, val injectorRegistration: SourceLocation?,
    val sourceCandidateCount: Int, val candidatesTruncated: Boolean, val mock: SpringTestMock?)
