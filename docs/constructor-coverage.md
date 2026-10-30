# Reviewed component constructors

These framework contracts also form the base of the later
[source constructor forwarding pass](source-setup.md#custom-component-id-contracts).
Pure source wrappers can establish reordered/fixed IDs by tracing delegation;
unknown custom semantics and transformations still remain unresolved. The trace
validates every delegation argument before propagating an ID origin, including
non-ID arguments and calls whose target supplies a fixed literal. Ordinary
wrappers accept literals, class literals, and their own parameter references,
optionally parenthesized. Effectful or unreviewed arguments are unresolved;
the separate repeater contract retains its reviewed list/model factories.

This audit covers the ten standard component classes added after source
navigation. The baseline five classes (`Label`, `Form`, `TextField`,
`WebMarkupContainer`, and `Panel`) remain supported.
The custom-input extension adds both ID/ID-plus-model overloads of
`FormComponent` and `FormComponentPanel`, bringing the reviewed set to seventeen.
Both preserve the ID through `LabeledWebMarkupContainer`; `FormComponent` also
sets versioning and `FormComponentPanel` supplies separate panel markup. See
[the form-component contract](form-components.md) for supported source wrappers.
The queue/repeater extension adds all three public `ListView` constructors
(ID, ID plus list, ID plus list model) and both `RepeatingView` constructors
(ID, ID plus model), bringing the current reviewed set to nineteen classes.
Their IDs are forwarded unchanged through `AbstractRepeater` to
`WebMarkupContainer`. Population contracts are documented separately in
[queueing and repeaters](queueing-repeaters.md).

## Source and resolution contract

Reviewed artifact: **org.apache.wicket:wicket-core:10.11.0**, using its
[published source JAR](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/10.11.0/wicket-core-10.11.0-sources.jar).
The source archive's SHA-256 is
`4b72cbf44463b7ce2ca39e1cb3c8c43095d0b3d23ef29e221fa48332a453be34`.
The Maven demo and Gradle fixture binaries were confirmed byte-identical.

Every public overload listed below takes the component ID as its first `String`
parameter and forwards that value unchanged through its constructor chain.
`this(...)` delegation and the relevant superclass forwarding were checked down
to `Component`. Labels, URLs, models, choices, renderers, page classes, filters,
and explicit Form arguments do not replace the component ID.

The analyzer requires a valid PSI overload resolution, an exact reviewed
constructor-declaring class, and a first formal parameter of `java.lang.String`.
Only a literal first argument becomes an observed ID. Constants, expressions,
invalid literals, ambiguous/inapplicable overloads, factories, and unreviewed
custom constructors retain their appropriate unresolved diagnostics. This is
not a general Java compiler check or a promise of runtime execution.

## Additional reviewed classes

Names below are relative to `org.apache.wicket`.

| Class | Public overloads reviewed | Additional parameters after ID |
| --- | ---: | --- |
| `markup.html.form.Button` | 2 | Optional string model. |
| `markup.html.form.SubmitLink` | 4 | Optional model and/or Form. |
| `markup.html.link.Link` | 2 | Optional model. Abstract; fixtures implement `onClick()`. |
| `markup.html.link.BookmarkablePageLink` | 2 | Page class; optional page parameters. Constructors have a generic page-class parameter. |
| `markup.html.link.ExternalLink` | 4 | String URL, URL model, and optional string/model label. |
| `ajax.markup.html.AjaxLink` | 2 | Optional model. Abstract; fixtures implement `onClick(AjaxRequestTarget)`. |
| `ajax.markup.html.form.AjaxButton` | 4 | Optional string model and/or Form. Abstract; fixtures use anonymous bodies. |
| `markup.html.form.CheckBox` | 2 | Optional Boolean model. |
| `markup.html.form.DropDownChoice` | 9 | Choice lists or choice models, optional selection model, and optional renderer. |
| `markup.html.panel.FeedbackPanel` | 2 | Optional feedback message filter. |
| **Total** | **33** | |

Forwarding chains include `AbstractLink`, `AbstractSubmitLink`,
`AbstractSingleSelectChoice`, `AbstractChoice`, `FormComponent`,
`LabeledWebMarkupContainer`, `WebMarkupContainer`, `Panel`, and `MarkupContainer`
as applicable. Some constructors also configure behavior or create internal
children; the audit establishes ID semantics, not a complete runtime hierarchy.

## Anonymous classes and retained limits

An anonymous instance can use a reviewed base constructor. Its component type is
reported as the declared base class's qualified name, consistently with ordinary
instances. Generic anonymous links therefore display as `Link`/`AjaxLink` in the
report rather than a trailing generic argument such as `Void>`.

Except for the bounded repeater item callbacks, callback bodies and
framework-owned children are not expanded. Anonymous
component bodies continue to emit informational scope warnings. A known literal
attachment can match while unexpanded children remain unresolved. Enclosing-page
effects in callbacks still invalidate affected constructor attachments under the
existing scope guards. `FeedbackPanel`'s internal message list is not invented as
part of the page graph.

Custom subclasses do not inherit approval from a reviewed superclass. A
constructor such as `SwappedButton(String label, String id)` remains unresolved,
including an anonymous subclass of that custom class. Matching a simple class
name is also insufficient; for example, `example.Button` is unreviewed.

Fields, aliases, arbitrary helpers/factories, markup
inheritance, queueing, and repeaters remain outside this slice. A later
[initialization slice](initialization-coverage.md) supports a conservative subset
of `onInitialize()`; another [markup slice](markup-inheritance.md) supports
conventional source layouts. Other lifecycle methods remain unexpanded. A later
[field slice](field-tracking.md) adds private final field bindings across supported
constructor and initialization additions. Reports remain partial and findings
informational. Other Wicket versions remain unverified.
Queue/repeater support is now available within its separate bounded contract.
The existing local-variable restrictions still apply: passing a tracked Form
local into another component's constructor is an unsupported use and prevents
tracking that Form. Reviewing that constructor's ID parameter does not establish
new alias/escape or parentage semantics.

## Verification and demo

[WicketStandardComponentsTest](../src/test/kotlin/dev/wicketlens/WicketStandardComponentsTest.kt)
exercises all 33 public overloads through real Java/HTML PSI and actual Wicket
fixture libraries. Positive fixtures check valid constructor resolution,
discovery, conventional pairing, matching, types, parentage, source links,
read-only behavior, stable JSON, and report labels. Control cases cover dynamic
and invalid IDs, ambiguous/inapplicable calls, custom constructors, same-name
classes, anonymous callbacks, nested Forms, repeated IDs, Panel roots, and
informational unmatched findings.

The demo [StandardControlsPage.java](../examples/demo/src/main/java/example/StandardControlsPage.java)
and its [HTML](../examples/demo/src/main/resources/example/StandardControlsPage.html)
use all ten added classes alongside `Label` and `Form`. Its full-pipeline test
expects **12 MATCHED** findings. Anonymous-body scope warnings are intentional;
the report is still a partial static snapshot. The demo Maven build compiles both
pages (including the later initialization fixture) without executing a Wicket application. Demo sources are declared Gradle
test inputs so edits invalidate the test task's cached result.
