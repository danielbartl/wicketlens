package dev.wicketlens.model

/** Serializable values only: reports must not retain PSI or project objects. */
data class SourceLocation(val file: String, val offset: Int)

/** Fragment lookup provenance is separate from the component's attachment ID. */
data class FragmentReference(
    val definitionId: String?,
    val providerNodeId: String?,
    val javaLocation: SourceLocation,
    val definitionIdOrigin: SourceLocation?,
    val definitionLocation: SourceLocation? = null,
    val definitionIdLocation: SourceLocation? = null,
    val instanceNodeIds: List<String> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
)

/** A separate source-declared lifecycle projection; it never describes initial/current runtime state. */
data class ReplacementTemplate(
    val contextId: String,
    val componentPath: String,
    val factoryNodeId: String,
    val factoryLocation: SourceLocation,
    val invocationLocation: SourceLocation,
    val eventLocation: SourceLocation,
    val replacementLocation: SourceLocation,
    val componentClassName: String,
    val selection: RegisteredSelection? = null,
    val contentFactory: ContentFactoryRegistration? = null,
    val directContent: EventContentPlacement? = null,
)

/** Source submission route and declarations, never evidence that validation or a callback ran. */
data class SubmissionTemplate(
    val contextId: String,
    val hostNodeId: String,
    val hostComponentPath: String,
    val hostConstructionLocation: SourceLocation,
    val formNodeId: String,
    val formComponentPath: String,
    val triggerNodeId: String,
    val triggerComponentPath: String,
    val callbackName: String,
    val callbackLocation: SourceLocation,
    val bridgeLocation: SourceLocation?,
    val dispatchLocation: SourceLocation?,
    val preDispatchLocations: List<SourceLocation>,
    val postDispatchLocations: List<SourceLocation>,
    val effects: List<SubmissionEffect>,
    val contentNodeId: String? = null,
    val contentConstructionLocation: SourceLocation? = null,
    val contentSetterLocation: SourceLocation? = null,
    val formCallback: FormCallbackRoute? = null,
    val confirmation: ConfirmationRoute? = null,
)
/** Conditional source consumer routing; the stored callback and business outcome are never evaluated. */
data class ConfirmationRoute(
    val registrationLocation:SourceLocation, val initiatingCallbackLocation:SourceLocation,
    val initiatingRegistrationLocation:SourceLocation, val initiatingMethodLocation:SourceLocation,
    val setterLocations:List<SourceLocation>, val slotLocation:SourceLocation,
    val hookLocation:SourceLocation, val captureLocation:SourceLocation, val clearLocation:SourceLocation,
    val guardLocation:SourceLocation, val consumerDispatchLocation:SourceLocation,
    val closeHookLocation:SourceLocation, val closeClearLocation:SourceLocation,
    val dataParameterLocation:SourceLocation, val dataCalls:List<ConfirmationDataCall>,
    val selectionClears:List<ConfirmationSelectionClear> = emptyList(),
)
data class ConfirmationDataCall(val callLocation:SourceLocation,val methodLocation:SourceLocation,
    val storageLocation:SourceLocation,val mutationLocation:SourceLocation,val mutationMethod:String,
    val argumentLocation:SourceLocation,val dataOriginLocation:SourceLocation)

/** Source helper declarations only. Column dispatch, model values and clear/refresh outcomes stay unknown. */
data class ConfirmationSelectionClear(
    val callLocation:SourceLocation, val helperLocation:SourceLocation, val targetNodeId:String, val targetComponentPath:String,
    val columnFieldLocation:SourceLocation, val guardLocation:SourceLocation,
    val getterCallLocation:SourceLocation, val getterLocation:SourceLocation, val modelFieldLocation:SourceLocation,
    val modelReadLocation:SourceLocation, val collectionClearLocation:SourceLocation,
    val refreshLocation:SourceLocation, val refreshArgumentLocation:SourceLocation, val returnLocation:SourceLocation,
)

data class FormCallbackRoute(val frameworkCallback: String, val formAssociation: String,
    val attachmentLocations: List<SourceLocation>, val constructorLocations: List<SourceLocation>,
    val explicitFormLocation: SourceLocation?)
data class SubmissionEffect(val kind: String, val methodName: String, val location: SourceLocation,
    val targetNodeId: String, val targetComponentPath: String, val argumentLocations: List<SourceLocation>)

/** Direct source callback argument and its declarations, without a reconstructed UI trigger. */
data class ActionTemplate(
    val contextId:String, val callbackLocation:SourceLocation,
    val registrationLocation:SourceLocation, val registrationMethodLocation:SourceLocation,
    val registrationReceiverLocation:SourceLocation?, val dataParameterLocation:SourceLocation,
    val updates:List<RecordUpdateCall>, val effects:List<SubmissionEffect>,
    val selectionClears:List<ConfirmationSelectionClear>,
)
data class RecordUpdateCall(
    val callLocation:SourceLocation, val methodLocation:SourceLocation, val storageLocation:SourceLocation,
    val argumentLocation:SourceLocation, val dataOriginLocation:SourceLocation,
    val loopLocation:SourceLocation, val loopParameterLocation:SourceLocation,
    val collectionCallLocation:SourceLocation, val updaterLocation:SourceLocation,
    val equalityCallLocation:SourceLocation, val equalityMethodLocation:SourceLocation,
    val transformCallLocation:SourceLocation, val transformMethodLocation:SourceLocation,
    val recordClassName:String, val recordConstructionLocation:SourceLocation,
    val componentArguments:List<RecordUpdateArgument>,
)
data class RecordUpdateArgument(val componentName:String, val kind:String,
    val argumentLocation:SourceLocation, val declarationLocation:SourceLocation, val declarationName:String)

/** Source event placement; a trigger may belong to a separate symbolic row context. */
data class EventContentPlacement(
    val hostNodeId: String,
    val hostConstructionLocation: SourceLocation,
    val contentLocation: SourceLocation,
    val transferLocations: List<SourceLocation>,
    val setterLocation: SourceLocation,
    val triggerLocation: SourceLocation,
    val triggerComponentPath: String,
    val triggerPopulationContextId: String?,
    val triggerHostComponentPath: String?,
    val rowPreconditions: List<EventRowPrecondition>,
    val configurationLocations: List<SourceLocation>,
    val callbackLocations: List<SourceLocation>,
)
data class EventRowPrecondition(val expression: String, val result: Boolean, val location: SourceLocation)

/** The source invokes this factory inside its setter; no factory or callback was executed. */
data class ContentFactoryRegistration(
    val registrationLocation: SourceLocation,
    val hostConstructionLocation: SourceLocation,
    val idLocation: SourceLocation,
    val placeholderLocation: SourceLocation,
    val configurationLocations: List<SourceLocation>,
    val callbackLocations: List<SourceLocation>,
)

/** Toolbar-relative declarations; Wicket supplies the enclosing toolbar's runtime ID. */
data class TableToolbarTemplate(
    val contextId: String,
    val hostClassName: String,
    val hostComponentPath: String,
    val toolbarClassName: String,
    val contentClassName: String,
    val contentNodeId: String,
    val registrationLocation: SourceLocation,
    val hostConstructionLocation: SourceLocation,
    val factoryLocation: SourceLocation,
    val invocationLocation: SourceLocation,
    val idLocation: SourceLocation,
    val cacheLocation: SourceLocation,
    val initializationLocation: SourceLocation,
    val toolbarConstructionLocation: SourceLocation,
    val toolbarAttachmentLocation: SourceLocation,
    val replacementLocation: SourceLocation,
    val configurationLocations: List<SourceLocation>,
    val callbackLocations: List<SourceLocation>,
)

/** Registration order is source evidence; it is never the current runtime index. */
data class RegisteredSelection(
    val registrationIndex: Int,
    val registrationCount: Int,
    val title: String?,
    val definitionId: String,
    val indexLocation: SourceLocation,
    val initializationLocation: SourceLocation?,
)

/** Node-relative source templates do not supply a page-to-runtime-node path. */
data class TreePopulationTemplate(
    val contextId: String,
    val frameworkKind: String,
    val hostClassName: String,
    val hostComponentPath: String,
    val hostLocation: SourceLocation,
    val nodeClassName: String,
    val nodeFactoryLocation: SourceLocation,
    val junctionFactoryLocation: SourceLocation,
    val contentFactoryLocation: SourceLocation,
    val contentClassName: String,
    val conditions: List<SourceNullnessCondition>,
)
data class SourceNullnessCondition(val variableName: String, val declarationLocation: SourceLocation, val nullness: String)

/** Source-owned lazy input structure, with lifecycle code recorded but never evaluated. */
data class FormInputTemplate(
    val contextId: String,
    val wrapperClassName: String,
    val inputClassName: String,
    val inputNodeId: String,
    val inputComponentPath: String,
    val cacheLocation: SourceLocation,
    val getterLocation: SourceLocation,
    val factoryLocation: SourceLocation,
    val initializationLocation: SourceLocation?,
    val hooks: List<FormHookSource>,
    val registration: FormInputRegistration? = null,
)
data class FormInputRegistration(val ownerClassName: String, val wrapperConstructionLocation: SourceLocation,
    val constructorReferenceLocation: SourceLocation, val inputConstructorLocation: SourceLocation, val factoryFieldLocation: SourceLocation)
/** Common declaration graph across all reviewed constructors, under declared base implementations only. */
data class BaseLayoutTemplate(val constructors: List<LayoutConstructorSource>, val extensionSlots: List<SourceLocation>)
data class LayoutConstructorSource(val signature: String, val location: SourceLocation)

/** Separate symbolic row graphs; conditions are source preconditions, never current model state. */
data class RepeaterPopulationTemplate(val contextId: String, val hostNodeId: String, val hostComponentPath: String,
    val itemNodeId: String, val ownedNodeIds: List<String>, val hostLocation: SourceLocation, val callbackLocation: SourceLocation,
    val conditions: List<SourcePredicateCondition>, val callbackLocations: List<SourceLocation>,
    val configurationLocations: List<SourceLocation>, val inactiveBodyLocations: List<SourceLocation>,
    val registration: RegisteredListPopulation? = null)
data class SourcePredicateCondition(val expression: String, val location: SourceLocation, val methodLocation: SourceLocation,
    val variableName: String, val declarationLocation: SourceLocation, val itemNodeId: String, val result: Boolean)

/** A source factory forwards an item/data lambda to one reviewed ListView population bridge. */
data class RegisteredListPopulation(val factoryClassName: String, val registrationLocation: SourceLocation,
    val factoryLocations: List<SourceLocation>, val callbackLocation: SourceLocation, val bridgeLocation: SourceLocation,
    val itemFactoryLocations: List<SourceLocation>, val behaviorLocations: List<SourceLocation>)

data class FormHookSource(val kind: String, val location: SourceLocation)

enum class Confidence { CONFIRMED, INFERRED, UNRESOLVED }

enum class DiagnosticCode {
    MATCHED,
    JAVA_ONLY,
    MARKUP_ONLY,
    UNRESOLVED_DYNAMIC_ID,
    UNRESOLVED_ID,
    UNRESOLVED_PARENT,
    UNRESOLVED_MARKUP,
    UNRESOLVED_CONSTRUCTOR,
    UNRESOLVED_VARIABLE,
    UNRESOLVED_SCOPE,
    UNRESOLVED_QUEUE,
    UNRESOLVED_BRANCH,
    PARTIAL_ANALYSIS,
}

enum class Severity { INFO, WARNING, ERROR }

data class Diagnostic(
    val code: DiagnosticCode,
    val message: String,
    val location: SourceLocation? = null,
    val severity: Severity = Severity.INFO,
)

data class ComponentNode(
    val nodeId: String,
    val wicketId: String?,
    val componentType: String,
    val parentNodeId: String?,
    val javaLocation: SourceLocation,
    val markupLocation: SourceLocation? = null,
    val confidence: Confidence,
    val diagnostics: List<Diagnostic> = emptyList(),
    // Snapshot provenance for source expansion; the exported construction location stays unchanged.
    val idLocation: SourceLocation? = null,
    val idRenameSafe: Boolean = true,
    val computedId: Boolean = false,
    val idUsedInComputation: Boolean = false,
    val queuedInNodeId: String? = null,
    val repetition: Repetition? = null,
    val templateItem: Boolean = false,
    val template: Boolean = false,
    // Alternative allocations/origins grouped by ID/type/parent path across
    // bounded setup paths. The primary locations remain stable navigation targets.
    val branchMerged: Boolean = false,
    val conditional: Boolean = false,
    val alternativeJavaLocations: List<SourceLocation> = emptyList(),
    val alternativeIdLocations: List<SourceLocation> = emptyList(),
    // Per-origin kind is required when a merged path has both literal and computed
    // alternatives. An empty list retains legacy single-kind snapshot behavior.
    val computedIdLocations: List<SourceLocation> = emptyList(),
    // Invocation-local feasible-path membership for queue placement. Zero means
    // shared/no branch expansion. Never exported as runtime branch information.
    val branchPathMask: Int = 0,
    val fragment: FragmentReference? = null,
    val sourceSetupExpanded: Boolean = false,
    val ownedMarkupPath: String? = null,
    // A source factory can preserve an ID across different concrete constructor types.
    val possibleComponentTypes: List<String> = emptyList(),
    val registeredListPopulation: RegisteredListPopulation? = null,
) {
    fun idLocations(): List<SourceLocation> = (listOfNotNull(idLocation) + alternativeIdLocations).distinct()
    fun isComputedIdOrigin(location: SourceLocation): Boolean =
        computedId && (computedIdLocations.isEmpty() || location in computedIdLocations)
}

enum class Repetition { LIST_VIEW, REPEATING_VIEW }

data class InitialContentHost(
    val hostNodeId: String,
    val hostClassName: String,
    val constructionLocation: SourceLocation,
    val constructorLocations: List<SourceLocation>,
    val setterLocation: SourceLocation,
    val contentId: String,
    val slotParentNodeId: String,
    val contentNodeId: String,
    val placementLocations: List<SourceLocation> = emptyList(),
)

data class InitialContentSetup(val hosts: List<InitialContentHost>, val callbackLocations: List<SourceLocation>)

/** Source proof that construction stores a callback; no later dispatch or tree state is established. */
data class StoredActionRegistration(
    val registrationLocation: SourceLocation,
    val methodLocation: SourceLocation,
    val callbackLocation: SourceLocation,
    val allocationLocation: SourceLocation,
    val slotLocation: SourceLocation,
)

data class PageReport(
    val qualifiedClassName: String,
    val rootNodeId: String,
    val nodes: List<ComponentNode>,
    val markupPath: String? = null,
    val schemaVersion: Int = 1,
    val warnings: List<Diagnostic> = emptyList(),
    // Discovery alone cannot establish that all runtime children have been found.
    val scopeComplete: Boolean = false,
    val markup: MarkupReport? = null,
    val matching: MatchReport? = null,
    // Additional source dependencies, including superclass/helper/constructor contracts; not exported.
    val javaSourceFiles: List<String> = emptyList(),
    // Explicit filename suffix selected by opening HTML; Java analysis defaults to unsuffixed markup.
    val markupSuffix: String = "",
    val branchPathCount: Int = 1,
    val fragmentUses: List<FragmentReference> = emptyList(),
    val lifecycle: ReplacementTemplate? = null,
    val treeTemplate: TreePopulationTemplate? = null,
    val formTemplate: FormInputTemplate? = null,
    val baseLayoutTemplate: BaseLayoutTemplate? = null,
    val populationTemplate: RepeaterPopulationTemplate? = null,
    val populationContexts: List<PageReport> = emptyList(),
    val populationDiscoveryComplete: Boolean = true,
    val toolbarTemplate: TableToolbarTemplate? = null,
    val initialContentSetup: InitialContentSetup? = null,
    val submissionTemplate: SubmissionTemplate? = null,
    val actionTemplate: ActionTemplate? = null,
    val storedActionRegistrations: List<StoredActionRegistration> = emptyList(),
)
