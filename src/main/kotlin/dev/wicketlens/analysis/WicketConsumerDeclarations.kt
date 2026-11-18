package dev.wicketlens.analysis

import com.intellij.psi.*

/** Functional operation provenance only; no interface initialization, composition or callback is executed. */
internal class WicketConsumerDeclarations(
    private val depthLimit:Int, private val tick:(PsiElement)->Unit, private val source:(PsiElement)->Unit,
    private val fail:(PsiElement,String)->Nothing,
) {
    fun inspect(type:PsiType?,arity:Int,at:PsiElement) {
            tick(at)
            val sam=type?.let(LambdaUtil::getFunctionalInterfaceMethod) ?: fail(at,"The callback functional interface is unavailable.")
            source(sam)
            val expected=if(arity==1) "java.util.function.Consumer" else "java.util.function.BiConsumer"
            if(sam !is PsiCompiledElement || sam.containingClass?.qualifiedName!=expected || sam.name!="accept" || sam.parameterList.parametersCount!=arity || sam.returnType!=PsiTypes.voidType())
                fail(at,"Only the unchanged compiled Consumer/BiConsumer operation is reviewed.")
            val active=mutableSetOf<PsiClass>()
            fun audit(owner:PsiClass,depth:Int) {
                source(owner);if(depth>=depthLimit || !active.add(owner)) fail(owner,"Callback interface ancestry is cyclic or too deep.")
                if(owner is PsiCompiledElement && owner.qualifiedName in setOf(expected,"java.io.Serializable")) {
                    // Terminal JDK contracts. A serializable library wrapper must still be an empty marker.
                } else {
                    if(owner is PsiCompiledElement && owner.qualifiedName != ("org.danekja.java.util.function.serializable.Serializable" + if(arity==1) "Consumer" else "BiConsumer")) fail(owner,"The callback interface has an unreviewed compiled ancestor.")
                    val methods=owner.methods
                    val compositionOnly=owner is PsiCompiledElement && methods.size==1 && methods.single().let {
                        it.name=="andThen" && !it.hasModifierProperty(PsiModifier.STATIC) && it.hasModifierProperty(PsiModifier.DEFAULT) &&
                            it.parameterList.parameters.singleOrNull()?.type.let { t -> (t as? PsiClassType)?.resolve()==owner } &&
                            (it.returnType as? PsiClassType)?.resolve()==owner
                    }
                    if(!owner.isInterface || owner.fields.isNotEmpty() || owner.initializers.isNotEmpty() || methods.isNotEmpty() && !compositionOnly) fail(owner,"Callback wrappers may only retain the unchanged compiled functional operation; composition is not evaluated.")
                    owner.extendsListTypes.forEach {audit(it.resolve() ?: fail(owner,"Callback ancestry is unresolved."),depth+1)}
                }
                active.remove(owner)
            }
            audit((type as? PsiClassType)?.resolve() ?: fail(at,"Callback type is unavailable."),0)
        }
}
