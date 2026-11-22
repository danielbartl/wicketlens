package dev.wicketlens.testing

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.spring.WicketSpringSetup

data class WicketTestSession(val className: String, val declaration: SourceLocation, val registration: SourceLocation,
    val authentication: WicketTestAuthentication? = null) {
    internal fun sourceFiles() = listOf(declaration.file, registration.file) + authentication?.let {
        listOf(it.signInPageDeclaration.file, it.signInPageRegistration.file, it.authorization.file)
    }.orEmpty()
}

data class WicketTestAuthentication(val signInPage: String, val signInPageDeclaration: SourceLocation,
    val signInPageRegistration: SourceLocation, val roles: List<String>, val authorization: SourceLocation) {
    companion object {
        const val USERNAME_ENV = "WICKET_TEST_USERNAME"
        const val PASSWORD_ENV = "WICKET_TEST_PASSWORD"
        internal const val SESSION = "org.apache.wicket.authroles.authentication.AuthenticatedWebSession"
        internal const val DEPENDENCY = "org.apache.wicket:wicket-auth-roles"
    }
}

/** Read direct application/session declarations without evaluating authentication or constructing the session. */
internal object WicketTestSessions {
    private const val APPLICATION = "org.apache.wicket.protocol.http.WebApplication"
    private const val WEB_SESSION = "org.apache.wicket.protocol.http.WebSession"
    private const val WEB_PAGE = "org.apache.wicket.markup.html.WebPage"
    private const val AUTH_APPLICATION = "org.apache.wicket.authroles.authentication.AuthenticatedWebApplication"
    private const val AUTHORIZE = "org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation"
    private const val REQUEST = "org.apache.wicket.request.Request"
    private const val RESPONSE = "org.apache.wicket.request.Response"

    fun discover(application: PsiClass, page: PsiClass): WicketTestSession? {
        ProgressManager.checkCanceled()
        val base = application.superClass?.takeIf { it is PsiCompiledElement } ?: return null
        return when (base.qualifiedName) {
            APPLICATION -> plainSession(application)
            AUTH_APPLICATION -> authenticatedSession(application, page)
            else -> null
        }
    }

    private fun plainSession(application: PsiClass): WicketTestSession? {
        val factory = frameworkOverride(application, "newSession", APPLICATION, REQUEST, RESPONSE) ?: return null
        val allocation = returned(factory) as? PsiNewExpression ?: return null
        val constructor = allocation.resolveConstructor() ?: return null
        val request = allocation.argumentList?.expressions?.singleOrNull() as? PsiReferenceExpression ?: return null
        val type = constructor.containingClass ?: return null
        if (request.qualifierExpression != null || request.resolve() != factory.parameterList.parameters[0] ||
            allocation.anonymousClass != null || !allocation.resolveMethodGenerics().isValidResult ||
            !sourceType(application, type, WEB_SESSION)) return null
        return WicketTestSession(type.qualifiedName!!, location(type), location(factory))
    }

    private fun authenticatedSession(application: PsiClass, page: PsiClass): WicketTestSession? {
        if (application.methods.any { it.name == "newSession" && it.parameterList.parametersCount == 2 }) return null
        val factory = frameworkOverride(application, "getWebSessionClass", AUTH_APPLICATION) ?: return null
        val type = classLiteral(factory) ?: return null
        if (!sourceType(application, type, WicketTestAuthentication.SESSION) || !type.hasModifierProperty(PsiModifier.PUBLIC) ||
            type.constructors.none { constructor -> constructor.hasModifierProperty(PsiModifier.PUBLIC) &&
                constructor.parameterList.parameters.singleOrNull()?.type?.equalsToText(REQUEST) == true }) return null
        return WicketTestSession(type.qualifiedName!!, location(type), location(factory), authentication(application, page))
    }

    private fun authentication(application: PsiClass, page: PsiClass): WicketTestAuthentication? {
        if (application.initializers.isNotEmpty() || application.fields.any { it.initializer != null } ||
            application.methods.any { it.name == "restartResponseAtSignInPage" && it.parameterList.isEmpty } ||
            application.constructors.any { it.body?.statements?.isNotEmpty() != false }) return null
        val init = frameworkOverride(application, "init", AUTH_APPLICATION) ?: return null
        val statements = init.body?.statements.orEmpty()
        if (statements.size !in 2..64) return null
        val calls = statements.map { (it as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return null }
        if (!WicketSpringSetup.superInit(calls.first()) || !calls.drop(1).all(::plainPageMount)) return null
        val guard = page.modifierList?.findAnnotation(AUTHORIZE)?.takeIf {
            it.resolveAnnotationType() is PsiCompiledElement && it.resolveAnnotationType()?.qualifiedName == AUTHORIZE
        } ?: return null
        val value = guard.findDeclaredAttributeValue("value") ?: return null
        val entries = (value as? PsiArrayInitializerMemberValue)?.initializers?.toList() ?: listOf(value)
        if (entries.size !in 1..24) return null
        val roles = entries.map {
            ((it as? PsiLiteralExpression)?.value as? String)?.takeIf { role -> role.isNotBlank() && role.length <= 128 } ?: return null
        }.distinct()
        val signInMethod = frameworkOverride(application, "getSignInPageClass", AUTH_APPLICATION) ?: return null
        val signIn = classLiteral(signInMethod) ?: return null
        if (signIn == page || !sourceType(application, signIn, WEB_PAGE)) return null
        return WicketTestAuthentication(signIn.qualifiedName!!, location(signIn), location(signInMethod), roles, location(guard))
    }

    private fun plainPageMount(call: PsiMethodCallExpression): Boolean {
        ProgressManager.checkCanceled()
        val method = call.resolveMethod() ?: return false
        val arguments = call.argumentList.expressions
        val receiver = call.methodExpression.qualifierExpression
        return (receiver == null || receiver is PsiThisExpression && receiver.qualifier == null) &&
            method is PsiCompiledElement && method.name == "mountPage" && method.containingClass?.qualifiedName == APPLICATION &&
            call.resolveMethodGenerics().isValidResult && arguments.size == 2 &&
            (arguments[0] as? PsiLiteralExpression)?.value is String && arguments[1] is PsiClassObjectAccessExpression
    }

    private fun frameworkOverride(owner: PsiClass, name: String, declaringType: String, vararg parameters: String): PsiMethod? =
        owner.findMethodsByName(name, false).singleOrNull { method ->
            method.parameterList.parameters.map { it.type.canonicalText } == parameters.toList() &&
                method.findSuperMethods().any { it is PsiCompiledElement && it.containingClass?.qualifiedName == declaringType }
        }

    private fun returned(method: PsiMethod) = (method.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue
    private fun classLiteral(method: PsiMethod) = (returned(method) as? PsiClassObjectAccessExpression)
        ?.let { (it.operand.type as? PsiClassType)?.resolve() }

    private fun sourceType(application: PsiClass, type: PsiClass, framework: String): Boolean {
        val appFile = application.containingFile.virtualFile ?: return false
        val source = type.containingFile?.virtualFile ?: return false
        val index = ProjectFileIndex.getInstance(application.project)
        return type !is PsiCompiledElement && type.qualifiedName != null && type.containingClass == null &&
            !type.hasModifierProperty(PsiModifier.ABSTRACT) && type.typeParameters.isEmpty() &&
            index.isInSourceContent(source) && index.getModuleForFile(source) == index.getModuleForFile(appFile) &&
            index.isInTestSourceContent(source) == index.isInTestSourceContent(appFile) &&
            InheritanceUtil.isInheritor(type, framework) && PsiTreeUtil.findChildOfType(type, PsiErrorElement::class.java) == null
    }

    private fun location(element: PsiElement): SourceLocation = SourceLocation(element.containingFile.virtualFile.path,
        (element as? PsiNameIdentifierOwner)?.nameIdentifier?.textOffset ?: element.textOffset)
}
