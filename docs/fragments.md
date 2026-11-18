# Fragment definitions and instances

This unreleased development milestone extends [source layouts](source-layouts.md)
with conventional Wicket fragment lookup. Resources remain in their package paths
under `src/main/resources`.

## Separate identities

`new Fragment("slot", "content", this)` creates a component attached as `slot`.
`content` identifies a `<wicket:fragment>` definition in the provider's markup;
that declaration is not a page child. Its children render inside the attached
instance, replacing the placeholder body. Definitions are retained as inactive
source elements in the markup snapshot. Each projected use gets distinct markup
node identities and retains the original file/offset locations.

Reviewed three- and four-argument framework constructors work through the bounded
layout pass, including a proved provider alias and inherited page setup. The
provider must be the selected root instance and its conventional markup lookup
must be available. Definitions can come from the composed inherited resources.
Duplicate definitions are conservative; no override order is guessed. Custom
Fragment subclasses and custom markup strategies remain opaque. Dynamic IDs,
other provider instances, nested definition declarations, unsupported setup and
unavailable definitions do not acquire guessed child bindings.

Attached fragment instances can themselves contain fragment instances. A reused
definition has one source location and multiple instance contexts. Definitions
with no proved instance create no `MARKUP_ONLY` component findings.

## Declaration discovery and navigation

A separate bounded source discovery records direct framework Fragment
construction expressions, including those in uncalled helpers, lambdas and event
handlers. This proves a definition reference only. It does not execute a factory,
prove an attachment, or introduce a repeater item or runtime row ID. In particular,
Inbox's `detailFragment` and `rowFragment`, Dialog's field/detail definitions and
Tabs' tab definitions can navigate while their factory placement remains unknown.
The following [factory milestone](factories.md) now establishes the bounded
Inbox instance/template contexts. The following [navigation milestone](nested-navigation.md)
adds named nested companions and bounded caller/inheritor contexts. Broader
lifecycle work remains deferred.

Native soft references navigate from a literal definition argument to its
`wicket:fragment` attribute, and back to all discovered uses in the selected source
context. Shared child markup can have multiple Java navigation targets. Existing
single-pair editing checks remain strict. The subsequent
[origin-use audit](id-origin-audit.md) keeps shared definitions and their children
protected; an isolated attachment ID can rename when all editor guards hold. This does not enumerate all subclasses or callers
throughout the project.

## MCP implementation decision and additive metadata

Extend `wicket_get_page_structure`, `wicket_validate_page` and
`wicket_get_component_context` through the existing analyzers and immutable
snapshots. No new tool or request parameter is needed. Existing fields and schema
**1** remain; consumers must tolerate these optional additive members:

- `analysis.fragmentUses`: discovered definition-reference records, including
  uninstantiated source constructions.
- `components[].fragment`: provenance for a construction captured by the layout
  pass. The component's `wicketId` remains its attachment ID.

Both use the same record fields:

| Field | Meaning |
| --- | --- |
| `definitionId` | Known definition ID, or null. |
| `providerNodeId` | Proved root/constructed provider identity, or null. Providers other than the selected root remain unsupported. |
| `javaLocation` | Actual construction source location. |
| `definitionIdOrigin` | Source expression for the definition ID. Computed values do not invent editable literals. |
| `definitionLocation`, `definitionIdLocation` | Uniquely located definition tag and attribute, or null when lookup is unresolved. These are lookup facts, not proof of instantiation. |
| `instanceNodeIds` | Allocations captured by the construction pass; empty for unexpanded source declarations. Check graph attachment, findings and diagnostics independently. |
| `diagnostics` | Explicit lookup uncertainty. Projection limits and attachment uncertainty also appear on component diagnostics. |

Validation projects the same binding findings; it does not add a second fragment
checker. Component context exposes instance provenance on the component and its
ancestors. Definition IDs are never path segments unless independently used as
actual attachment IDs. Fetch structure to inspect uninstantiated declarations.

## Bounds, freshness and verification

Source discovery is limited to 32 source classes, 1,024 constructions and 20,000
visitor steps. Projection is limited to 64 instances, 16 rounds of nested
instances and 4,096 generated markup elements per analysis. Existing layout,
markup, snapshot, cancellation and 512-KiB MCP response limits also apply.
Exceeded limits retain explicit uncertainty. Sources, provider markup and
framework archives participate in the existing snapshot dependencies. Unsaved
changes invalidate native and MCP results without saving files.

The focused PSI tests cover repeated/inherited/nested instances, missing and
duplicate definitions, provider isolation, custom sourcing, discarded previews,
unsupported markup, response bounds, cancellation and unsaved changes. A separate
WicketTester process verifies inherited definition reuse, independent child
values and preview replacement against Wicket 11. The opt-in Oat corpus checks
native definition navigation and MCP instance contexts against Wicket 10.11.0 and
11.0.0 on the pinned IDEA fixture platform. This is not a running Oat server or
live IDEA 2026.3 installation test.

Final verification: **2,393** default tests and **8** MCP host tests passed with
zero failures/errors/skips; the 22 release-checker unit tests also passed. Plugin
configuration, structure and binary verification passed against
**IU-262.10968.63**. See the [evidence](wicket-oat-fragment-evidence.json) for
corpus facts, source hashes and development ZIP checksum. The ZIP retains 0.1.0
metadata; this milestone has not been installed into the running IDE or published.
