# Injected-dependency view

The WicketLens sidebar has a **Dependencies** tab alongside **Hierarchy** and
**Text report**. Analyze a page or keep Follow editor enabled, then select this
tab to inspect injection fields on that page and its discovered source component
types. This is Spring slice 6. The related bootstrap and WicketTester recipe are
documented in [Spring test scaffolding](spring-testing.md).

## Reading the tree

Owner rows identify the component path and type. Field rows show the declared
type, injection annotation, lookup mode, and number of source candidates. Expand
a field to see source declarations and their names/aliases. Aliases for the same
declaration form one candidate; multiple declarations stay separate.

Select a row to read its details. The view distinguishes by-type lookup, literal
bean names, unknown explicit names, unsupported field targets, and Spring
autowiring intent. Required/optional policies are shown when directly known.
Initializer and transient-storage notes point out wiring concerns without
replacing the native inspections.

Search matches component paths, fields, types, declaring classes, annotations,
and source bean names. Owner and field context stay visible. Native tree speed
search also finds candidates inside collapsed branches.

**Go to injection field** opens the field declaration, including inherited
fields. **Go to bean source** opens a selected source candidate; on a field row
it is enabled only when one candidate exists. Multiple candidates never cause
the first one to be selected implicitly. Enter or double-click opens the selected
row's Java source. The sidebar's existing Go to Java action also follows the
dependency selection; Go to HTML is unavailable for dependency rows.

## Evidence and scope

- Resolved Wicket `@SpringBean` expresses local injection intent even when
  application registration is not verified.
- Jakarta `@Inject` and Spring `@Autowired` require the existing recognized
  Wicket–Spring application setup. Generic injection alone and Spring classpath
  presence alone do not create dependency rows.
- `@SpringBean` takes precedence over `@Inject`/`@Named`. Empty or omitted names
  request by-type lookup. Direct string literals provide by-name lookup; other
  explicit name expressions stay unknown, rather than falling back to by type.
- Static and primitive injection targets remain visible as unsupported. An
  `@Autowired` field explains that Wicket's listener does not process it; known
  Spring management/autowiring intent is reported without claiming execution.
- Discovery covers the selected page and component types already found by the
  component analyzer, including inherited Java source fields. Separate instances
  of one source component type keep separate owner groups. Hidden superclass
  fields retain their distinct declaration locations.

The view does not invent models, sessions, helpers, behaviors, or runtime objects
that were not discovered as component owners. Compiled-only fields and custom
compiled ancestry are not reconstructed. Unavailable source ancestry is partial.
Anonymous component bodies are not resolved as separate source owners in this slice.
Source discovery is bounded to 64 populated owners, 256 fields, 12 source
ancestor types per owner, and 32 candidates per field. Reached limits are shown
instead of implying completeness.

Bean discovery reuses [the source catalog](spring-integration.md#bean-name-editing-and-source-type-validation-slice-4)
in the analyzed page's module and production/test source set. By-type candidates
must have compatible declared types. By-name candidates can still be inspected
when declared types differ; the details explicitly leave conversions and runtime
replacement unverified. Generic/unresolved field types and FactoryBean products
do not acquire guessed compatibility. Profiles and additional bean metadata are
not evaluated. An empty candidate list is **not a missing-bean finding**.

The source catalog's own limits and unknown contexts still apply. No IntelliJ
Spring plugin or runtime Spring classes are required by WicketLens.

## Shared snapshot and navigation

Dependency discovery runs in the existing cancellable smart read action with
committed documents. The service prepares values only; Swing presentation performs
no PSI or bean lookup work. Field, source ancestor, application, configuration,
and displayed candidate sources participate in snapshot version tracking.

After source or configuration edits, dependency rows remain readable, while field
and bean navigation is disabled until **Re-analyze**. Refresh rebuilds candidate
locations and keeps the selected tab. Cancelling analysis or clearing the followed
page clears dependencies with the component report. Navigating into a bean factory
preserves the analyzed page's context until an ordinary editor switch.

The component graph, text report, and JSON schema 1 are unchanged. **Copy Wicket
Context** continues to export component context; dependency rows are a separate
sidebar view and are not added to the JSON payload.

## Try it

Import [the Spring demo](../examples/spring-demo/pom.xml). Analyze
`SpringDemoPage.java`: its four injection fields each have a source candidate.
The `orders` alias and `orderService` name identify one factory declaration.
`BeanNamesPage.java` also demonstrates by-name selection, an incompatible declared
source type, and default by-type lookup. Native inspections refresh independently
of this manually refreshed snapshot.
