# Wicket–Spring inspections

WicketLens offers four native Java inspections under **Settings → Editor →
Inspections → WicketLens**. All default to weak warnings, work independently
of the sidebar, and support the usual inspection suppression and disabling.
They require no IntelliJ Spring plugin or Spring dependency in WicketLens itself.

## Evidence before findings

Having Spring on a project's classpath does not activate the inspections.

- A resolved Wicket `@SpringBean` on a Component or Behavior expresses local
  injection intent. Conditional overwrite and `transient` warnings can appear
  without proving that application bootstrap actually installs an injector.
- A resolved `jakarta.inject.Inject` receives these checks only when a recognized
  Wicket Spring injector registration exists. `@Inject` alone could mean CDI or
  another injector; `@Autowired` alone does not mean Wicket injection.
- Proxy-policy findings additionally require one concrete source
  `WebApplication` in the file's module and production/test source set, with a
  reviewed `init()` registration. Multiple applications, indirect registration,
  or unknown wrapping policy do not become guessed proxy findings.

The bootstrap subset follows source application ancestors, an initial resolved
`super.init()`, and same-application listener registration. Slice 5 also follows
straight-line local context/injector variables, no-argument private or final
`void` setup helpers, literal `mountPage` calls, and standard local context
`refresh()` calls. Supported contexts are exact `GenericApplicationContext`,
`StaticApplicationContext`, and `AnnotationConfigApplicationContext` creations.
Class-literal configuration arguments retain their source configuration identity.
The one- and two-argument injector constructors enable wrapping; a literal boolean,
local boolean, or final field with a boolean literal initializer chooses wrapping
explicitly. An unknown third argument can establish registration without a proxy
policy. Conditions, reassignment, virtual helpers, other receivers, custom context
dispatch, application initializer blocks, and nontrivial constructors leave setup
unknown. Unreviewed injector creations also leave setup unknown. Helpers are
bounded to eight nested calls, 128 executed statements, and 16 injector creations.
A test application does not establish production setup, nor does an application
in another module.

The **Possible Wicket Spring application setup issue** inspection reports multiple
reviewed registrations, or a reviewed application without component-listener
registration when local `@SpringBean` intent or injector construction exists.
The latter is conditional: verify external wiring. Constructing an injector alone
also installs a Behavior listener but does not register the Component listener.
Unknown setup and plain applications without injection intent remain quiet.
No automatic setup rewrite or missing-bean diagnosis is offered.

Analysis requires smart mode and committed documents. Cached evidence holds only
values and invalidates on PSI, roots, and file-structure changes.

## Possible overwritten Wicket Spring injection

Wicket's construction listener injects during superclass construction. Subsequent
subclass field initializers, instance initializer blocks, and direct constructor
assignments can replace the injected value—even `= null` matters.

```java
@SpringBean private OrderService orders = null; // possible overwrite
```

Findings cover direct writes to the current instance, including inherited fields.
They do not follow arbitrary helper calls or deferred callbacks, warn on unrelated
receivers, or assume every later lifecycle assignment is a construction overwrite.
Literal unreachable `if` branches are excluded. Warnings describe a possible
overwrite, because explicit later injection may be intentional.

The inspection recognizes a narrow explicit repair: a null reset immediately
followed by resolved `Injector.get().inject(this)`, or a null field initializer
followed by that first constructor operation in every constructor, with no other
instance initialization that could observe the reset. Non-null initialization is
not repaired by this call: Wicket's injector skips fields that are already non-null.

## Possible Wicket Spring serialization or proxy issue

- A `transient` injection field may be null after page deserialization; the default
  Wicket lazy proxy is serializable. Custom serialization hooks in the component's
  source hierarchy make restoration unknown and suppress this storage warning.
- With known wrapping enabled, a final concrete declared dependency type cannot
  be subclass-proxied. Interface injection avoids this particular restriction.
- With known wrapping disabled, a non-transient final concrete non-Serializable
  dependency cannot be serialized with the component when its value is non-null.
  Interfaces and nonfinal classes do not prove the runtime bean lacks Serializable.

Wicket's direct-value exceptions for String, primitive wrappers, and enums do not
receive final-class proxy warnings. Static fields, primitive fields, plain POJOs,
models, and sessions are outside these automatically injected Component/Behavior
checks. No unsafe automatic source rewrites are offered.

These are bounded inspections, not proof that a bean exists, a context is active,
or a whole page can serialize. No-argument constructor/instantiator requirements,
sealed classes, final methods, arbitrary restoration code, and runtime bean
selection are not yet inspected. Spring bean knowledge also does not make an
opaque service method a proven component factory in the hierarchy analyzer.

## Example and behavior audit

Import [the separate Spring demo](../examples/spring-demo/pom.xml) as a Maven
project. It deliberately contains overwrite, transient-storage, and final-type
proxy findings. `AnnotationExamplesPage` demonstrates an unsupported `@Autowired`
field. `MissingInjectionModel` and `MissingInjectionSession` demonstrate missing
manual injection, while `InjectedOrderModel` shows an explicit injection path.
`BeanNamesPage` adds alias navigation, a completion site, and a source-type warning.
The original Wicket demo stays Spring-free. This is an inspection
fixture, not a deployable application or a recommended wiring pattern.

The behavior was checked against released Wicket **10.11.0** source artifacts:
[wicket-spring sources](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-spring/10.11.0/wicket-spring-10.11.0-sources.jar)
(`SpringComponentInjector`, `AnnotProxyFieldValueFactory`) and
[wicket-ioc sources](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-ioc/10.11.0/wicket-ioc-10.11.0-sources.jar)
(`Injector`, `LazyInitProxyFactory`, subclass proxy factory). Fixtures resolve
the actual libraries, including Wicket's Spring **6.2.12** dependency versions.

## Possible annotation or manual-injection issue (slice 3)

The **Possible Wicket Spring annotation or manual-injection issue** inspection
checks field annotations and bounded injection intent:

- Wicket's injector does not support static injection fields. Primitive fields
  never satisfy its null-only injection condition. Resolved `@SpringBean` is
  sufficient intent for these target checks, including on ordinary helpers.
- Models, sessions, validators, and ordinary helpers do not receive automatic
  Component/Behavior injection. A `@SpringBean` field gets a conditional warning
  when no explicit Wicket injection path is recognized.
- With recognized Wicket Spring registration, `@Autowired` fields on Components
  and Behaviors get a conditional warning unless a Spring autowiring or management
  path is recognized. Wicket's listener processes `@SpringBean` and Jakarta
  `@Inject`, not `@Autowired`. A supported second annotation avoids this finding.

Generic `@Inject` needs both recognized registration and Wicket ownership: a
Component, Behavior, model, session, validator, or a type with explicit Wicket
injection intent. Known Spring management/autowiring leaves these generic
annotation checks to Spring. Ordinary Spring services and unclassified POJOs
do not become Wicket injection targets merely by sharing an application module.
The earlier overwrite/serialization inspections retain their Component/Behavior
scope. These new checks do not broaden hierarchy/provider analysis.

Recognized Wicket intent includes resolved `Injector.inject(target)` calls
(including injector subclasses), in source ancestry or callers that reference
the type in the same module and production/test source set. Current-instance
calls in source ancestors and statically typed subtype targets are recognized.
Calls to unrelated methods named `inject`, or injections of unrelated target
types, do not count.

Recognized Spring intent includes `SpringBeanAutowiringSupport` processing calls,
`AutowireCapableBeanFactory.autowireBean`, `configureBean`, and
`autowireBeanProperties`, and
`AutowiredAnnotationBeanPostProcessor.processInjection`. Factory creation through
`createBean`/`autowire` with a class literal, Spring component stereotypes (including
bounded composed annotations), `@Configurable`, Jakarta `@Named`, and references
from resolved `@Bean` factory methods count as management intent for the
`@Autowired` check. Spring autowiring alone does **not** process `@SpringBean`;
manual Wicket injection alone does **not** process `@Autowired`.

These are intent suppressions, not claims that every constructor, conditional
branch, delayed callback, profile, or instance is injected. The inspection does
not prove when a manual method executes, trace arbitrary aliases, interpret XML
or external factories, or infer runtime bean ownership. Source ancestry is bounded
to 12 types, indexed references to 256, source files to 32, and method calls to
4,096. Unknown custom compiled ancestry, unresolved source ancestors, malformed
hierarchies, or exceeded bounds suppress missing-path findings. Meta-annotation
search is separately bounded to depth 8 and 64 annotation types. Cached evidence
stores values and invalidates on PSI, roots, and file structure changes.

Findings anchor the annotation, use native suppression/disable controls, and
offer no automatic annotation replacement or constructor insertion. Such changes
could alter bean selection, lifecycle ordering, and serialization behavior.

The annotation distinction and non-component injection requirement are documented
in Apache Wicket's [Spring integration guide](https://wicket.apache.org/docs/spring.html).
The implementation also checks the released source artifacts linked above.

## Bean-name editing and source-type validation (slice 4)

Basic completion and Go to Declaration now support direct Java string literals in
`@SpringBean(name = "...")`. A resolved annotation expresses intent even without
bootstrap proof. Resolved Jakarta `@Inject @Named("...")` also works on automatic
Wicket targets, or types with recognized manual Wicket injection, when Spring
registration is recognized. `@Named` alone is not evidence of Wicket Spring;
`@SpringBean` takes precedence when both annotations appear on a field.

The source catalog reads Java declarations in the same module and production/test
source set:

- Direct resolved `@Bean` methods: default method names, literal `name`/`value`, and
  literal arrays of aliases. Conflicting mirrored attributes and unresolved or
  constant-expression names are not guessed.
- Direct standard `@Component`, `@Service`, `@Repository`, `@Controller`, and
  `@Configuration` classes: literal names and standard default decapitalization,
  including acronym names such as `URLService`.

Completion filters by the field's assignable declared type, deduplicates names,
labels candidates **context not verified**, and replaces the complete string value
with proper Java escaping. Profiles may contribute suggestions; suggestions are
source declarations, not proof that a bean is active. `FactoryBean` product types,
custom composed stereotypes/name generators, generic type parameters, dependency
modules, compiled definitions, XML, and runtime registrations are not inferred.
The catalog is bounded to 256 eligible declarations and 32 aliases per method.
It caches value records and invalidates on PSI, root, and file-structure changes.

Navigation uses soft references to the source method or class, including aliases.
Duplicate names have multiple navigation targets rather than a guessed unique
bean. Unknown names remain soft and do not become Java unresolved-reference
errors. These are navigation-only references: Java method/class rename does not
rewrite explicit bean-name strings or aliases. Paired bean-name refactoring and
Spring Find Usages are not supplied by this slice. Existing Java references on
constant-expression names stay in control.

The **Possible incompatible Wicket Spring bean source type** inspection checks
only positive, bounded source evidence. It requires recognized Wicket Spring
bootstrap with a reviewed
`new AnnotationConfigApplicationContext(Config.class, ...)` creation passed
directly or through a supported local variable to the injector. All roots must
be reviewed Java `@Configuration` classes in the same
module/source set, without imports, scans, profiles, custom annotations,
inheritance, initializer effects, or extra setup methods. Only a unique named
declaration in those roots can be checked. Unknown/conditional roots, duplicate
source names, truncated catalogs, FactoryBean products, nonfinal or generic
declared bean types, and unknown/generic consumer types suppress the finding.
Unreviewed bean names/return types and known bean post-processors make a root
uncertain. JDK/enum consumer types and recognized String constructor/valueOf
conversion targets also remain quiet, since Spring can convert named lookups.

If the declaration has a final class type incompatible with the field, the
inspection reports a weak source-type warning and asks the developer to verify
context selection, conversions, and runtime replacement. It does not claim to resolve the
actual runtime bean or to establish a complete application context. **Absent bean
names never produce missing-bean findings**, including optional `@SpringBean`
injections. No automatic bean-name replacement is offered. Normal suppression and
inspection disabling apply independently of the sidebar.

No IntelliJ Spring plugin or additional runtime library is required. Optional
integration with the IDE's Spring model remains future work. Naming semantics
follow Spring's [Bean annotation documentation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html)
and [component naming documentation](https://www.springframework.org/spring/reference/6.2/core/beans/classpath-scanning.html).
Named lookup conversion was checked against Spring 6.2.12's
[AbstractBeanFactory source](https://github.com/spring-projects/spring-framework/blob/v6.2.12/spring-beans/src/main/java/org/springframework/beans/factory/support/AbstractBeanFactory.java).

## Injected-dependency view (slice 6)

The sidebar's **Dependencies** tab groups injection fields by the analyzed page
and discovered source components. It shows lookup intent, source candidates and
aliases, and field/bean navigation, with the same stale-snapshot protections as the
component hierarchy. It does not claim runtime bindings or report missing beans.
See [the view contract](injected-dependencies.md) for scope, controls, and limits.
This implements slice 6; the bootstrap and test scaffold in slice 5 are described
above and in [Spring test scaffolding](spring-testing.md).

## Remaining Spring work

1. Optional IntelliJ Spring model integration and broader bean discovery
   (XML, composed stereotypes, external modules, profiles, and FactoryBean products).

These are follow-ups. Spring slices 1–6 are implemented. Conditional/bootstrap
flow, application-specific test customization, and runtime wiring remain outside
the reviewed source subset.
