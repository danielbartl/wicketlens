package dev.wicketlens.export

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.FragmentReference
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.ComponentNode
import dev.wicketlens.model.MarkupReport
import dev.wicketlens.model.MatchFinding
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.model.RegisteredListPopulation
import dev.wicketlens.model.ConfirmationSelectionClear

/** Explicit schema and ordering; no reflection, timestamps, PSI, or external requests. */
class WicketContextExporter {
    fun export(report: PageReport): String {
        ProgressManager.checkCanceled()
        val findings = report.matching?.findings.orEmpty().associateBy { it.javaNodeId }
        val json = JsonObject().apply {
            addProperty("schemaVersion", report.schemaVersion)
            addProperty("qualifiedClassName", report.qualifiedClassName)
            addProperty("rootNodeId", report.rootNodeId)
            addProperty("markupPath", report.markupPath)
            addProperty("scopeComplete", report.scopeComplete)
            if (report.storedActionRegistrations.isNotEmpty()) add("storedActionRegistrations", JsonObject().apply {
                addProperty("kind", "SOURCE_PROVED_CALLBACK_STORAGE")
                addProperty("phase", "INITIAL_DECLARATIONS")
                addProperty("callbackExecution", "not_evaluated")
                for (key in listOf("activeRegistration", "dispatch", "visibility", "laterTreeState", "businessOutcome")) addProperty(key, "unknown")
                add("registrations", array(report.storedActionRegistrations) { registration -> JsonObject().apply {
                    add("registrationLocation", location(registration.registrationLocation))
                    add("methodLocation", location(registration.methodLocation))
                    add("callbackLocation", location(registration.callbackLocation))
                    add("allocationLocation", location(registration.allocationLocation))
                    add("slotLocation", location(registration.slotLocation))
                } })
            })
            report.initialContentSetup?.let { setup -> add("initialContentSetup", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_INITIAL_CONTENT")
                addProperty("phase", "INITIAL_DECLARATIONS")
                for (key in listOf("runtimePresence", "constructorEffects", "configurationEffects", "modelValues", "visibility", "laterTreeState")) addProperty(key, "unknown")
                addProperty("callbackExecution", "not_evaluated")
                addProperty("constructorExecution", "not_evaluated"); addProperty("configurationExecution", "not_evaluated")
                add("hosts", array(setup.hosts) { host -> JsonObject().apply {
                    addProperty("hostNodeId", host.hostNodeId); addProperty("hostClassName", host.hostClassName)
                    addProperty("contentId", host.contentId); addProperty("slotParentNodeId", host.slotParentNodeId); addProperty("contentNodeId", host.contentNodeId)
                    add("constructionLocation", location(host.constructionLocation)); add("constructorLocations", array(host.constructorLocations, ::location))
                    add("setterLocation", location(host.setterLocation)); add("placementLocations", array(host.placementLocations, ::location))
                } })
                add("callbackLocations", array(setup.callbackLocations, ::location))
            }) }
            if (report.populationContexts.isNotEmpty() || !report.populationDiscoveryComplete) {
                addProperty("populationDiscoveryComplete", report.populationDiscoveryComplete)
                add("populationTemplates", array(report.populationContexts) { context -> com.google.gson.JsonParser.parseString(export(context)) })
            }
            report.populationTemplate?.let { population -> add("populationTemplate", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_LIST_POPULATION")
                addProperty("phase", "ITEM_POPULATION_TEMPLATE")
                addProperty("pathScope", "SYMBOLIC_ITEMS")
                addProperty("dispatch", if(report.baseLayoutTemplate!=null) "DECLARED_IMPLEMENTATIONS" else "SELECTED_SOURCE_CLASS")
                for (key in listOf("runtimePresence", "runtimeItemIds", "rowCount", "modelValues", "predicateResults", "authorization", "visibility", "runtimeSubclass")) addProperty(key, "unknown")
                addProperty("configurationExecution", "not_evaluated")
                addProperty("callbackExecution", "not_evaluated")
                addProperty("contextId", population.contextId)
                addProperty("hostNodeId", population.hostNodeId)
                addProperty("hostComponentPath", population.hostComponentPath)
                addProperty("itemNodeId", population.itemNodeId)
                add("ownedNodeIds", array(population.ownedNodeIds) { com.google.gson.JsonPrimitive(it) })
                add("hostLocation", location(population.hostLocation))
                add("callbackLocation", location(population.callbackLocation))
                add("conditions", array(population.conditions) { condition -> JsonObject().apply {
                    addProperty("expression", condition.expression); addProperty("requiredResult", condition.result)
                    addProperty("itemNodeId", condition.itemNodeId); addProperty("variableName", condition.variableName)
                    add("location", location(condition.location)); add("methodLocation", location(condition.methodLocation)); add("declarationLocation", location(condition.declarationLocation))
                } })
                add("callbackLocations", array(population.callbackLocations, ::location))
                add("configurationLocations", array(population.configurationLocations, ::location))
                add("inactiveBodyLocations", array(population.inactiveBodyLocations, ::location))
                population.registration?.let { add("registration", registeredPopulation(it)) }
            }) }
            report.toolbarTemplate?.let { toolbar -> add("toolbarTemplate", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_TABLE_TOOLBAR_CONTENT")
                addProperty("phase", "TABLE_TOOLBAR_TEMPLATE")
                addProperty("pathScope", "TOOLBAR_RELATIVE")
                addProperty("factoryInvocation", "source_immediate")
                addProperty("initialization", "source_null_guarded")
                for (key in listOf("runtimePresence", "runtimeToolbarId", "toolbarInstances", "rowCount", "visibility", "colspan", "modelValues", "configurationEffects", "laterTreeState")) addProperty(key, "unknown")
                addProperty("factoryExecution", "not_evaluated"); addProperty("callbackExecution", "not_evaluated")
                addProperty("contextId", toolbar.contextId); addProperty("hostClassName", toolbar.hostClassName)
                addProperty("hostComponentPath", toolbar.hostComponentPath); addProperty("toolbarClassName", toolbar.toolbarClassName)
                addProperty("contentClassName", toolbar.contentClassName); addProperty("contentNodeId", toolbar.contentNodeId)
                add("registrationLocation", location(toolbar.registrationLocation)); add("hostConstructionLocation", location(toolbar.hostConstructionLocation))
                add("factoryLocation", location(toolbar.factoryLocation)); add("invocationLocation", location(toolbar.invocationLocation)); add("idLocation", location(toolbar.idLocation))
                add("cacheLocation", location(toolbar.cacheLocation)); add("initializationLocation", location(toolbar.initializationLocation))
                add("toolbarConstructionLocation", location(toolbar.toolbarConstructionLocation)); add("toolbarAttachmentLocation", location(toolbar.toolbarAttachmentLocation))
                add("replacementLocation", location(toolbar.replacementLocation))
                add("configurationLocations", array(toolbar.configurationLocations, ::location)); add("callbackLocations", array(toolbar.callbackLocations, ::location))
            }) }
            report.baseLayoutTemplate?.let { layout -> add("baseLayoutTemplate", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_BASE_LAYOUT")
                addProperty("phase", "BASE_LAYOUT_TEMPLATE")
                addProperty("pathScope", "BASE_RELATIVE")
                addProperty("constructorAgreement", "ALL_REVIEWED_ENTRIES")
                addProperty("dispatch", "DECLARED_IMPLEMENTATIONS")
                addProperty("runtimePresence", "unknown")
                addProperty("runtimeConstructor", "unknown")
                addProperty("subclassOverrides", "unknown")
                addProperty("extensionContents", "unknown")
                addProperty("modelValues", "unknown")
                add("constructors", array(layout.constructors) { entry -> JsonObject().apply {
                    addProperty("signature", entry.signature); add("location", location(entry.location))
                } })
                add("extensionSlots", array(layout.extensionSlots, ::location))
            }) }
            report.actionTemplate?.let { action -> add("actionTemplate", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_RECORD_ACTION"); addProperty("phase", "ACTION_TEMPLATE")
                addProperty("contextId", action.contextId); addProperty("pathScope", "PAGE_DECLARED_ATTACHMENT")
                addProperty("preconditions", "SOURCE_CALLBACK_REACHED_AND_PRIOR_SOURCE_CALLS_RETURN")
                addProperty("uiTrigger", "not_reconstructed"); addProperty("callbackExecution", "not_evaluated")
                for(key in listOf("runtimePresence", "runtimeSubclass", "constructorEffects", "configurationEffects", "registrationDispatch", "registrationEffects", "callbackStorage", "callbackReachability", "authorization", "visibility", "rowValues", "loopCounts", "equalityEffects", "predicateResults", "enumInitialization", "dataValues", "mutationOutcome", "businessOutcome", "feedbackOutcome", "modelEffects", "laterTreeState")) addProperty(key,"unknown")
                add("callbackLocation", location(action.callbackLocation)); add("registrationLocation", location(action.registrationLocation))
                add("registrationMethodLocation", location(action.registrationMethodLocation)); add("registrationReceiverLocation", location(action.registrationReceiverLocation))
                add("dataParameterLocation", location(action.dataParameterLocation))
                add("updates", array(action.updates) { update -> JsonObject().apply {
                    addProperty("kind", "SOURCE_RECORD_REPLACEMENT_LOOP"); addProperty("execution", "not_evaluated")
                    addProperty("collectionOperation", "java.util.List.replaceAll"); addProperty("recordClassName", update.recordClassName)
                    addProperty("loopCounts", "unknown"); addProperty("predicateResult", "unknown"); addProperty("outcome", "unknown")
                    addProperty("equalityExecution", "not_evaluated"); addProperty("recordConstruction", "not_evaluated"); addProperty("enumInitialization", "unknown")
                    add("callLocation", location(update.callLocation)); add("methodLocation", location(update.methodLocation)); add("storageLocation", location(update.storageLocation))
                    add("argumentLocation", location(update.argumentLocation)); add("dataOriginLocation", location(update.dataOriginLocation))
                    add("loopLocation", location(update.loopLocation)); add("loopParameterLocation", location(update.loopParameterLocation))
                    add("collectionCallLocation", location(update.collectionCallLocation)); add("updaterLocation", location(update.updaterLocation))
                    add("equalityCallLocation", location(update.equalityCallLocation)); add("equalityMethodLocation", location(update.equalityMethodLocation))
                    add("transformCallLocation", location(update.transformCallLocation)); add("transformMethodLocation", location(update.transformMethodLocation))
                    add("recordConstructionLocation", location(update.recordConstructionLocation))
                    add("componentArguments", array(update.componentArguments) { argument -> JsonObject().apply {
                        addProperty("componentName", argument.componentName); addProperty("kind", argument.kind); addProperty("declarationName", argument.declarationName)
                        add("argumentLocation", location(argument.argumentLocation)); add("declarationLocation", location(argument.declarationLocation))
                    } })
                } })
                add("effects", array(action.effects) { effect -> JsonObject().apply {
                    addProperty("kind", effect.kind); addProperty("methodName", effect.methodName); addProperty("execution", "not_evaluated")
                    addProperty("targetPathScope", "PAGE_DECLARED_ATTACHMENT"); addProperty("targetAttachmentPhase", "INITIAL_DECLARATIONS")
                    addProperty("targetNodeId", effect.targetNodeId); addProperty("targetComponentPath", effect.targetComponentPath)
                    add("location", location(effect.location)); add("argumentLocations", array(effect.argumentLocations, ::location))
                } })
                if(action.selectionClears.isNotEmpty()) add("selectionClears", array(action.selectionClears, ::selectionClear))
            }) }
            report.submissionTemplate?.let { submission -> add("submissionTemplate", JsonObject().apply {
                addProperty("kind", when { submission.confirmation != null -> "SOURCE_DECLARED_CONFIRMATION"; submission.formCallback == null -> "SOURCE_DECLARED_SUBMISSION"; else -> "SOURCE_DECLARED_FORM_CALLBACK" })
                addProperty("phase", if (submission.confirmation == null) "SUBMISSION_TEMPLATE" else "CONFIRMATION_TEMPLATE")
                addProperty("pathScope", if (submission.formCallback == null) "PAGE_DECLARED_ATTACHMENT" else "OWN_COMPANION_DECLARATIONS")
                addProperty("contextId", submission.contextId)
                addProperty("frameworkCallback", submission.formCallback?.frameworkCallback ?: "org.apache.wicket.ajax.markup.html.form.AjaxButton.onSubmit")
                addProperty("dispatchCondition", when { submission.confirmation != null -> "SOURCE_STORED_CONSUMER_ON_SUBMIT_ROUTE"; submission.formCallback != null && submission.callbackName == "onError" -> "SOURCE_ON_ERROR_ROUTE"; else -> "SOURCE_ON_SUBMIT_ROUTE" })
                addProperty("callbackExecution", "not_evaluated")
                for (key in listOf("runtimePresence", "runtimeSubclass", "callbackReachability", "otherDispatchRoutes", "defaultFormProcessing", "validationResult", "modelValues", "feedbackOutcome", "businessOutcome", "behaviorEffects", "opaqueDataEffects", "constructorEffects", "configurationEffects", "bridgeEffects", "postCallbackEffects", "visibility", "laterTreeState", "openCloseState")) addProperty(key, "unknown")
                addProperty("hostNodeId", submission.hostNodeId); addProperty("hostComponentPath", submission.hostComponentPath)
                addProperty("formNodeId", submission.formNodeId); addProperty("formComponentPath", submission.formComponentPath)
                addProperty("triggerNodeId", submission.triggerNodeId); addProperty("triggerComponentPath", submission.triggerComponentPath)
                addProperty("callbackName", submission.callbackName)
                add("hostConstructionLocation", location(submission.hostConstructionLocation))
                add("callbackLocation", location(submission.callbackLocation)); add("bridgeLocation", submission.bridgeLocation?.let(::location) ?: JsonNull.INSTANCE)
                add("dispatchLocation", submission.dispatchLocation?.let(::location) ?: JsonNull.INSTANCE)
                add("preDispatchLocations", array(submission.preDispatchLocations, ::location))
                add("postDispatchLocations", array(submission.postDispatchLocations, ::location))
                add("effects", array(submission.effects) { effect -> JsonObject().apply {
                    addProperty("kind", effect.kind); addProperty("methodName", effect.methodName)
                    addProperty("execution", "not_evaluated"); addProperty("targetPathScope", if (submission.formCallback == null) "PAGE_DECLARED_ATTACHMENT" else "OWN_COMPANION_DECLARATIONS")
                    addProperty("targetNodeId", effect.targetNodeId); addProperty("targetComponentPath", effect.targetComponentPath)
                    addProperty("targetAttachmentPhase", if (submission.formCallback != null) "SOURCE_DECLARED_ADDITIONS" else if (effect.kind == "CONTENT_REPLACEMENT") "SUBMISSION_DECLARATION" else "INITIAL_DECLARATIONS")
                    add("location", location(effect.location)); add("argumentLocations", array(effect.argumentLocations, ::location))
                } })
                submission.confirmation?.let { route -> add("confirmation", JsonObject().apply {
                    addProperty("sourceEvidence", "ASK_SLOT_CAPTURE_CLEAR_GUARD_ACCEPT")
                    addProperty("preconditions", "INITIATING_ASK_REACHED_CURRENT_SLOT_FROM_THIS_REGISTRATION_NON_NULL_AND_SUBMIT_REACHED")
                    for (key in listOf("initiatingDispatch", "initiatingRegistrationEffects", "currentRegistration", "slotPresence", "cleanupReachability", "rows", "dataValues", "dataMutationOutcome", "equalsEffects")) addProperty(key, "unknown")
                    addProperty("callbackExecution", "not_evaluated")
                    add("registrationLocation", location(route.registrationLocation))
                    add("initiatingCallbackLocation", location(route.initiatingCallbackLocation))
                    add("initiatingRegistrationLocation", location(route.initiatingRegistrationLocation))
                    add("initiatingMethodLocation", location(route.initiatingMethodLocation))
                    add("setterLocations", array(route.setterLocations, ::location)); add("slotLocation", location(route.slotLocation))
                    add("hookLocation", location(route.hookLocation)); add("captureLocation", location(route.captureLocation))
                    add("clearLocation", location(route.clearLocation)); add("guardLocation", location(route.guardLocation))
                    add("consumerDispatchLocation", location(route.consumerDispatchLocation))
                    add("cleanupHookLocation", location(route.closeHookLocation)); add("cleanupClearLocation", location(route.closeClearLocation))
                    add("dataParameterLocation", location(route.dataParameterLocation))
                    if(route.selectionClears.isNotEmpty()) add("selectionClears", array(route.selectionClears, ::selectionClear))
                    add("dataCalls", array(route.dataCalls) { call -> JsonObject().apply {
                        addProperty("kind", "SOURCE_DATA_CALL"); addProperty("execution", "not_evaluated")
                        addProperty("outcome", "unknown"); addProperty("mutationMethod", call.mutationMethod)
                        add("callLocation", location(call.callLocation)); add("methodLocation", location(call.methodLocation))
                        add("storageLocation", location(call.storageLocation)); add("mutationLocation", location(call.mutationLocation))
                        add("argumentLocation", location(call.argumentLocation)); add("dataOriginLocation", location(call.dataOriginLocation))
                    } })
                }) }
                submission.formCallback?.let { route -> add("formCallback", JsonObject().apply {
                    addProperty("attachmentEvidence", "SOURCE_DECLARED_ADDITIONS")
                    addProperty("formAssociation", route.formAssociation)
                    addProperty("initialHierarchy", "unknown"); addProperty("inheritedMarkupPlacement", "unknown")
                    addProperty("initializationEffects", "unknown"); addProperty("formProcessingEffects", "unknown")
                    add("attachmentLocations", array(route.attachmentLocations, ::location))
                    add("constructorLocations", array(route.constructorLocations, ::location))
                    add("explicitFormLocation", route.explicitFormLocation?.let(::location) ?: JsonNull.INSTANCE)
                }) }
                addProperty("contentNodeId", submission.contentNodeId)
                add("contentConstructionLocation", submission.contentConstructionLocation?.let(::location) ?: JsonNull.INSTANCE)
                add("contentSetterLocation", submission.contentSetterLocation?.let(::location) ?: JsonNull.INSTANCE)
            }) }
            report.lifecycle?.let { event -> add("lifecycle", JsonObject().apply {
                addProperty("kind", when { event.directContent != null -> "SOURCE_DECLARED_EVENT_CONTENT"
                    event.contentFactory != null -> "SOURCE_DECLARED_CONTENT_FACTORY"
                    event.selection != null -> "SOURCE_DECLARED_STEP_SELECTION"
                    else -> "SOURCE_DECLARED_REPLACEMENT_SITE" })
                addProperty("phase", when { event.contentFactory != null -> "REGISTRATION_TEMPLATE"
                    event.selection != null -> "SELECTION_TEMPLATE"
                    else -> "EVENT_TEMPLATE" })
                addProperty("runtimePresence", "unknown")
                addProperty("contextId", event.contextId)
                addProperty("componentPath", event.componentPath)
                addProperty("factoryNodeId", event.factoryNodeId)
                addProperty("componentClassName", event.componentClassName)
                event.directContent?.let { placement -> add("directContent",JsonObject().apply {
                    addProperty("pathScope","PAGE_DECLARED_ATTACHMENT")
                    addProperty("triggerPathScope",if(placement.triggerPopulationContextId==null) "PAGE_DECLARED_ATTACHMENT" else "SYMBOLIC_ITEMS")
                    addProperty("hostNodeId",placement.hostNodeId);addProperty("triggerComponentPath",placement.triggerComponentPath)
                    addProperty("triggerPopulationContextId",placement.triggerPopulationContextId);addProperty("triggerHostComponentPath",placement.triggerHostComponentPath)
                    for(key in listOf("runtimeRowIds","rowCount","modelValues","visibility","validation","openCloseState","otherEventEffects","laterTreeState")) addProperty(key,"unknown")
                    addProperty("callbackExecution","not_evaluated");addProperty("configurationExecution","not_evaluated")
                    add("hostConstructionLocation",location(placement.hostConstructionLocation));add("contentLocation",location(placement.contentLocation))
                    add("setterLocation",location(placement.setterLocation));add("triggerLocation",location(placement.triggerLocation))
                    add("transferLocations",array(placement.transferLocations,::location));add("configurationLocations",array(placement.configurationLocations,::location))
                    add("callbackLocations",array(placement.callbackLocations,::location))
                    add("rowPreconditions",array(placement.rowPreconditions) { condition -> JsonObject().apply {
                        addProperty("expression",condition.expression);addProperty("requiredResult",condition.result);add("location",location(condition.location))
                    } })
                }) }
                event.contentFactory?.let { factory -> add("contentFactory", JsonObject().apply {
                    addProperty("invocation", "source_immediate")
                    addProperty("factoryExecution", "not_evaluated")
                    addProperty("callbackExecution", "not_evaluated")
                    addProperty("configurationEffects", "unknown")
                    addProperty("modelValues", "unknown")
                    addProperty("visibility", "unknown")
                    addProperty("laterTreeState", "unknown")
                    addProperty("slotPresence", "unknown")
                    addProperty("slotRequirement", "source_declared_placeholder")
                    add("registrationLocation", location(factory.registrationLocation))
                    add("hostConstructionLocation", location(factory.hostConstructionLocation))
                    add("idLocation", location(factory.idLocation))
                    add("placeholderLocation", location(factory.placeholderLocation))
                    add("configurationLocations", array(factory.configurationLocations, ::location))
                    add("callbackLocations", array(factory.callbackLocations, ::location))
                }) }
                event.selection?.let { step -> add("selection", JsonObject().apply {
                    addProperty("registrationIndex", step.registrationIndex)
                    addProperty("registrationCount", step.registrationCount)
                    addProperty("title", step.title)
                    addProperty("definitionId", step.definitionId)
                    add("indexLocation", location(step.indexLocation))
                    add("initializationLocation", location(step.initializationLocation))
                }) }
                add("factoryLocation", location(event.factoryLocation))
                add("invocationLocation", location(event.invocationLocation))
                add("eventLocation", location(event.eventLocation))
                add("replacementLocation", location(event.replacementLocation))
            }) }
            report.treeTemplate?.let { tree -> add("treeTemplate", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_TREE_POPULATION")
                addProperty("phase", "POPULATION_TEMPLATE")
                addProperty("pathScope", "NODE_RELATIVE")
                addProperty("runtimePresence", "unknown")
                addProperty("runtimeNodeIds", "unknown")
                addProperty("nodeCount", "unknown")
                addProperty("expansionState", "unknown")
                addProperty("selectionState", "unknown")
                addProperty("contextId", tree.contextId)
                addProperty("frameworkKind", tree.frameworkKind)
                addProperty("hostClassName", tree.hostClassName)
                addProperty("hostComponentPath", tree.hostComponentPath)
                add("hostLocation", location(tree.hostLocation))
                addProperty("nodeClassName", tree.nodeClassName)
                add("nodeFactoryLocation", location(tree.nodeFactoryLocation))
                add("junctionFactoryLocation", location(tree.junctionFactoryLocation))
                add("contentFactoryLocation", location(tree.contentFactoryLocation))
                addProperty("contentClassName", tree.contentClassName)
                add("conditions", array(tree.conditions) { condition -> JsonObject().apply {
                    addProperty("variableName", condition.variableName)
                    add("declarationLocation", location(condition.declarationLocation))
                    addProperty("nullness", condition.nullness)
                } })
            }) }
            report.formTemplate?.let { form -> add("formTemplate", JsonObject().apply {
                addProperty("kind", "SOURCE_DECLARED_LAZY_FORM_INPUT")
                addProperty("phase", "LAZY_INPUT_TEMPLATE")
                addProperty("pathScope", "WRAPPER_RELATIVE")
                addProperty("runtimePresence", "unknown")
                addProperty("conversionResult", "unknown")
                addProperty("validationResult", "unknown")
                addProperty("modelValues", "unknown")
                addProperty("requiredState", "unknown")
                addProperty("contextId", form.contextId)
                addProperty("wrapperClassName", form.wrapperClassName)
                addProperty("inputClassName", form.inputClassName)
                addProperty("inputNodeId", form.inputNodeId)
                addProperty("inputComponentPath", form.inputComponentPath)
                add("cacheLocation", location(form.cacheLocation))
                add("getterLocation", location(form.getterLocation))
                add("factoryLocation", location(form.factoryLocation))
                add("initializationLocation", location(form.initializationLocation))
                form.registration?.let { registration -> add("registration", JsonObject().apply {
                    addProperty("kind", "CONSTRUCTOR_REFERENCE")
                    addProperty("ownerClassName", registration.ownerClassName)
                    add("wrapperConstructionLocation", location(registration.wrapperConstructionLocation))
                    add("constructorReferenceLocation", location(registration.constructorReferenceLocation))
                    add("inputConstructorLocation", location(registration.inputConstructorLocation))
                    add("factoryFieldLocation", location(registration.factoryFieldLocation))
                }) }
                add("hooks", array(form.hooks) { hook -> JsonObject().apply {
                    addProperty("kind", hook.kind)
                    addProperty("execution", "not_evaluated")
                    add("location", location(hook.location))
                } })
            }) }
            add("warnings", diagnostics(report.warnings))
            if (report.fragmentUses.isNotEmpty()) add("fragmentUses", array(report.fragmentUses, ::fragment))
            add("components", array(report.nodes.sortedWith(compareBy({ it.javaLocation.file }, { it.javaLocation.offset }, { it.nodeId }))) { node ->
                JsonObject().apply {
                    val finding = findings[node.nodeId]
                    addProperty("nodeId", node.nodeId)
                    addProperty("wicketId", node.wicketId)
                    addProperty("componentType", node.componentType)
                    addProperty("parentNodeId", node.parentNodeId)
                    node.queuedInNodeId?.let { addProperty("queuedInNodeId", it) }
                    if (node.possibleComponentTypes.isNotEmpty()) add("possibleComponentTypes", array(node.possibleComponentTypes) { com.google.gson.JsonPrimitive(it) })
                    node.registeredListPopulation?.let { add("registeredListPopulation", registeredPopulation(it)) }
                    if (node.sourceSetupExpanded) addProperty("sourceSetupExpanded", true)
                    node.ownedMarkupPath?.let { addProperty("ownedMarkupPath", it) }
                    node.fragment?.let { add("fragment", fragment(it)) }
                    if (!node.idRenameSafe) addProperty("idRenameSafe", false)
                    node.repetition?.let { addProperty("repetition", it.name) }
                    if (node.template) addProperty("template", true)
                    if (node.templateItem) addProperty("templateItem", true)
                    add("javaLocation", location(node.javaLocation))
                    if (report.baseLayoutTemplate != null && node.alternativeJavaLocations.isNotEmpty()) {
                        add("javaLocations", array(listOf(node.javaLocation) + node.alternativeJavaLocations, ::location))
                        add("idLocations", array(node.idLocations(), ::location))
                    }
                    if (node.branchMerged) {
                        addProperty("branchMerged", true)
                        if (node.conditional) addProperty("conditional", true)
                        addProperty("idRenameSafe", node.idRenameSafe)
                        add("javaLocations", array(listOf(node.javaLocation) + node.alternativeJavaLocations, ::location))
                        add("idLocations", array(node.idLocations(), ::location))
                    }
                    if (node.computedId) {
                        addProperty("computedId", true)
                        addProperty("idRenameSafe", false)
                        add("idOrigins", origins(node))
                    }
                    add("markupLocation", location(node.markupLocation))
                    addProperty("confidence", node.confidence.name)
                    addProperty("status", finding?.code?.name)
                    addProperty("matchingConfidence", finding?.confidence?.name)
                    addProperty("markupNodeId", finding?.markupNodeId)
                    add("diagnostics", diagnostics(node.diagnostics))
                }
            })
            add("markup", report.markup?.let(::markup) ?: JsonNull.INSTANCE)
            add("matching", report.matching?.let { matching ->
                JsonObject().apply {
                    addProperty("scopeComplete", matching.scopeComplete)
                    add("findings", array(matching.findings.sortedWith(compareBy(
                        { it.javaNodeId == null }, { it.javaLocation?.file ?: it.markupLocation?.file.orEmpty() },
                        { it.javaLocation?.offset ?: it.markupLocation?.offset ?: -1 },
                        { it.javaNodeId.orEmpty() }, { it.markupNodeId.orEmpty() }, { it.code.name },
                    )), ::finding))
                }
            } ?: JsonNull.INSTANCE)
        }
        return GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create().toJson(json) + "\n"
    }

    private fun markup(report: MarkupReport) = JsonObject().apply {
        addProperty("file", report.file)
        addProperty("kind", report.kind.name)
        addProperty("scopeComplete", report.scopeComplete)
        add("diagnostics", diagnostics(report.diagnostics))
        add("documentDiagnostics", diagnostics(report.documentDiagnostics))
        add("elements", array(report.elements.sortedWith(compareBy({ it.location.file }, { it.location.offset }, { it.nodeId }))) { element ->
            JsonObject().apply {
                addProperty("nodeId", element.nodeId)
                addProperty("tagName", element.tagName)
                element.markupProviderNodeId?.let { addProperty("markupProviderNodeId", it) }
                addProperty("parentElementId", element.parentElementId)
                addProperty("hasWicketId", element.hasWicketId)
                addProperty("wicketId", element.wicketId)
                addProperty("parentWicketElementId", element.parentWicketElementId)
                add("location", location(element.location))
                add("wicketIdLocation", location(element.wicketIdLocation))
                addProperty("participatesInComponentTree", element.participatesInComponentTree)
                addProperty("confidence", element.confidence.name)
                add("diagnostics", diagnostics(element.diagnostics))
            }
        })
    }

    private fun registeredPopulation(value: RegisteredListPopulation) = JsonObject().apply {
        addProperty("kind", "SOURCE_DECLARED_LIST_CALLBACK")
        addProperty("factoryClassName", value.factoryClassName)
        addProperty("itemFactory", if(value.itemFactoryLocations.isEmpty()) "reviewed_default" else "reviewed_default_with_behavior_decoration")
        addProperty("callbackExecution", "not_evaluated")
        addProperty("behaviorEffects", "unknown")
        add("registrationLocation", location(value.registrationLocation))
        add("factoryLocations", array(value.factoryLocations, ::location))
        add("callbackLocation", location(value.callbackLocation)); add("bridgeLocation", location(value.bridgeLocation))
        add("itemFactoryLocations", array(value.itemFactoryLocations, ::location)); add("behaviorLocations", array(value.behaviorLocations, ::location))
    }

    private fun fragment(value: FragmentReference) = JsonObject().apply {
        addProperty("definitionId", value.definitionId)
        addProperty("providerNodeId", value.providerNodeId)
        add("javaLocation", location(value.javaLocation))
        add("definitionIdOrigin", location(value.definitionIdOrigin))
        add("definitionLocation", location(value.definitionLocation))
        add("definitionIdLocation", location(value.definitionIdLocation))
        add("instanceNodeIds", array(value.instanceNodeIds) { com.google.gson.JsonPrimitive(it) })
        add("diagnostics", diagnostics(value.diagnostics))
    }

    private fun finding(finding: MatchFinding) = JsonObject().apply {
        addProperty("code", finding.code.name)
        addProperty("message", finding.message)
        addProperty("confidence", finding.confidence.name)
        addProperty("severity", finding.severity.name)
        addProperty("javaNodeId", finding.javaNodeId)
        addProperty("markupNodeId", finding.markupNodeId)
        add("javaLocation", location(finding.javaLocation))
        add("markupLocation", location(finding.markupLocation))
        add("causes", diagnostics(finding.causes))
    }

    private fun selectionClear(operation:ConfirmationSelectionClear) = JsonObject().apply {
                        addProperty("kind", "SOURCE_SELECTION_HELPER"); addProperty("execution", "not_evaluated")
                        addProperty("pathScope", "PAGE_DECLARED_ATTACHMENT")
                        addProperty("collectionOperation", "java.util.Collection.clear")
                        addProperty("modelOperation", "org.apache.wicket.model.IModel.getObject")
                        addProperty("preconditions", "SOURCE_HELPER_REACHED")
                        addProperty("clearCondition", "SOURCE_COLUMN_NON_NULL_GUARD_AND_DECLARED_GETTER_DISPATCH")
                        addProperty("refreshCondition", "AFTER_PRIOR_HELPER_STATEMENTS_RETURN")
                        for(key in listOf("columnPresence", "columnDispatch", "modelImplementation", "modelValue", "collectionIdentity", "collectionMutability", "clearOutcome", "refreshOutcome", "laterSelection")) addProperty(key, "unknown")
                        addProperty("targetNodeId", operation.targetNodeId); addProperty("targetComponentPath", operation.targetComponentPath)
                        add("callLocation", location(operation.callLocation)); add("helperLocation", location(operation.helperLocation))
                        add("columnFieldLocation", location(operation.columnFieldLocation)); add("guardLocation", location(operation.guardLocation))
                        add("getterCallLocation", location(operation.getterCallLocation)); add("getterLocation", location(operation.getterLocation))
                        add("modelFieldLocation", location(operation.modelFieldLocation)); add("modelReadLocation", location(operation.modelReadLocation))
                        add("collectionClearLocation", location(operation.collectionClearLocation)); add("refreshLocation", location(operation.refreshLocation))
                        add("refreshArgumentLocation", location(operation.refreshArgumentLocation)); add("returnLocation", location(operation.returnLocation))
    }

    private fun location(location: SourceLocation?): JsonElement = location?.let {
        JsonObject().apply {
            addProperty("file", it.file)
            addProperty("offset", it.offset)
        }
    } ?: JsonNull.INSTANCE

    private fun origins(node: ComponentNode) = array(node.idLocations()) { origin ->
        JsonObject().apply {
            add("location", location(origin))
            addProperty("kind", if (node.isComputedIdOrigin(origin)) "COMPUTED" else "LITERAL")
        }
    }

    private fun diagnostics(values: List<Diagnostic>) = array(values.sortedWith(compareBy(
        { it.location?.file.orEmpty() }, { it.location?.offset ?: -1 }, { it.code.name }, { it.message }, { it.severity.name },
    ))) { diagnostic ->
        JsonObject().apply {
            addProperty("code", diagnostic.code.name)
            addProperty("message", diagnostic.message)
            addProperty("severity", diagnostic.severity.name)
            add("location", location(diagnostic.location))
        }
    }

    private fun <T> array(values: Iterable<T>, convert: (T) -> JsonElement) = JsonArray().apply {
        values.forEach {
            ProgressManager.checkCanceled()
            add(convert(it))
        }
    }
}
