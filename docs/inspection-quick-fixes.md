# Unpaired-ID inspection quick fixes

Place the caret on an unpaired Wicket ID and press **Alt+Enter**, or use the
quick-fix menu on its Problems inspection result. WicketLens offers these actions
when the current analysis establishes an editable, unambiguous scope:

- **Change Wicket ID to '…'** changes the highlighted side to an existing,
  unpaired ID under the same component parent. Candidates must have a compatible
  standard component/tag shape. This corrects a mismatch; native paired Rename
  remains the operation for changing an already matched pair.
- **Create HTML counterpart for '…'** inserts a basic matching tag inside the
  proven markup parent of a Java component.
- **Create Java Label/TextField/… for '…'** adds basic component setup under the
  proven Java parent of a markup ID.

The actions use IntelliJ ModCommands, including native diff previews for the file
being changed and normal command undo. Preview does not modify either physical
file. Every invocation recalculates the finding, counterpart, collisions, source
ownership, and insertion target. Saved actions become no-ops when the problem or
their eligibility changes. Pending document edits prevent mixed PSI snapshots.

## Supported shapes

| Java component | Generated HTML | Java generation from markup |
| --- | --- | --- |
| `Label` | `span` | A leaf `span`; decoded text supplies the initial model. |
| `Form<Void>` | `form` | `form` |
| `TextField<String>` | `input type="text"` | `input` with no type or `type="text"` |
| `CheckBox` | `input type="checkbox"` | `input type="checkbox"` |
| `Button` | `button type="submit"` | `button` with no type or `type="submit"` |
| `WebMarkupContainer` | `div` | `div`, `section`, `fieldset`, or a `span` containing child tags/custom entities |

Corrections also accept common Label/container tags and a Button's submit input.
A Label correction cannot absorb markup component children. Suggestions for
standard constructors use exact reviewed component types; custom component tags,
choice data, converters, and models require application-specific decisions.
Generated code is a starting point for the binding; model wiring and application
behavior still need to be supplied where appropriate.

Java generation appends to a straight-line constructor or the constructor/
`onInitialize()` block owning a matched local parent. It can use private final
component fields belonging to the analyzed class. A missing no-argument
constructor is created only for a direct WebPage subclass. Generated Forms and
containers get a unique final local variable, allowing subsequent child fixes.
Imports are shortened without changing existing symbol bindings. IDs and Label
text are escaped for Java; markup IDs are escaped for HTML.

Markup generation uses the matched parent tag, the Page's body/safe root wrapper,
or the composite's `wicket:panel` section. Void elements and unsupported insertion
contexts receive no creation action. A missing parent can be created first, then
its children. Repeated IDs under different parents remain separate paths.

Final locals and additions to owned local/field containers also participate in
the bounded custom-component constructor contract. Creating supported internal
setup in a Panel/FormComponentPanel preserves its outer ID pairing. Escaping a
local or operating on a constructor-supplied external receiver remains unresolved.

## Scope guards

Fixes require one complete conventional markup source owned by the selected
class, supported findings without uncertainty causes, and no known project
inheritors of that class. Shared inherited templates, merged inheritance, shared
Java literal origins for ID changes, factory-only parent receivers, dynamic
setup, unsupported tag/type shapes, and read-only edit targets retain their
diagnostics without unsafe edits. Composite preview IDs are inactive and have no
unpaired-ID action.

## Demo

Open `example.QuickFixPage`. Its deliberate mismatches produce **1 MATCHED**,
**2 JAVA_ONLY**, and **2 MARKUP_ONLY** findings. Try:

1. On Java `"titel"`, choose **Change Wicket ID to 'title'**.
2. On Java `"name"`, choose **Create HTML counterpart for 'name'**.
3. On HTML `wicket:id="note"`, choose **Create Java Label for 'note'**.

The result has **4 MATCHED** findings and no unpaired-ID inspection results.
The sidebar snapshot becomes stale after edits; **Re-analyze** refreshes it.
The demo may already include fixes applied interactively. The automated test
recreates the starting mismatches only in fixture copies and preserves the demo
edits. Real application evaluation remains deferred.

## Verification

The full regression suite passes **524 tests**, including 41 quick-fix tests and
two additional custom-component isolation guards. Native fixture checks cover
cross-file previews, intention discovery, command undo, and a complete demo
repair. Packaging, plugin configuration/structure, and IntelliJ binary
compatibility checks pass, with the previous four deprecated and six experimental
API notices. The thirteen demo Java classes compile against Wicket 10.11.0.
The graphical sandbox shows the expected two Java/two HTML problems and all
three action types. A read-only check generated eight native diff previews;
the physical source text and current sidebar snapshot remained unchanged.
