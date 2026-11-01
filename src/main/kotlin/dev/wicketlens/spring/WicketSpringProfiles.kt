package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*

/** Declared conditions only. Runtime active/default profiles are deliberately not guessed. */
internal object WicketSpringProfiles {
    private const val PROFILE = "org.springframework.context.annotation.Profile"
    data class Conditions(val conditions: List<String>, val unknown: Boolean)

    fun read(owners: List<PsiModifierListOwner>): Conditions {
        val conditions = linkedSetOf<String>()
        val ownerConditions = linkedSetOf<String>()
        var unknown = false
        var visited = 0
        fun visit(annotation: PsiAnnotation, path: Set<String>) {
            ProgressManager.checkCanceled()
            val type = annotation.resolveAnnotationType() ?: run { unknown = true; return }
            val name = type.qualifiedName ?: return
            if (name.startsWith("java.lang.annotation.") || name in path) return
            if (path.size >= 8 || ++visited > 64) { unknown = true; return }
            if (name == PROFILE) {
                val value = annotation.findAttributeValue("value")
                val values = if (value is PsiArrayInitializerMemberValue) value.initializers.toList() else listOfNotNull(value)
                val expressions = values.map { member ->
                    (member as? PsiExpression)?.let { JavaPsiFacade.getInstance(it.project).constantEvaluationHelper.computeConstantExpression(it) } as? String
                }
                if (expressions.isEmpty() || expressions.size > 32 || expressions.any { it.isNullOrBlank() || it.length > 512 }) unknown = true
                else ownerConditions += if (expressions.size == 1) "(${expressions.single()})"
                    else expressions.joinToString(" OR ", "(", ")") { "($it)" }
            } else {
                // Composed conditions are visible, even though composed bean naming remains unsupported.
                val before = ownerConditions.toSet()
                for (meta in type.modifierList?.annotations.orEmpty()) visit(meta, path + name)
                if (ownerConditions.any { it !in before } && annotation.parameterList.attributes.isNotEmpty() && type.methods.any { method ->
                        method.modifierList.annotations.any { it.qualifiedName == "org.springframework.core.annotation.AliasFor" }
                    }) unknown = true // Declared meta-values are visible; merged overrides are not resolved.
            }
        }
        for (owner in owners) {
            ownerConditions.clear()
            for (annotation in owner.modifierList?.annotations.orEmpty()) visit(annotation, emptySet())
            // Spring's ProfileCondition accepts any profile declaration on the same owner.
            // A configuration and its method must both pass their own condition.
            if (ownerConditions.isNotEmpty()) conditions += if (ownerConditions.size == 1) ownerConditions.single()
                else ownerConditions.joinToString(" OR ", "(", ")")
        }
        return Conditions(conditions.toList(), unknown)
    }
}
