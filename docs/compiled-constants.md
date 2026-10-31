# Constants from compiled dependencies

WicketLens can resolve String, boolean, byte, short, char, int, and long
`static final` constants from dependency classes and the JDK. These values can
supply component IDs, conditions, immutable helper configuration, and the
existing reviewed scalar calculations.

```java
add(new Label(LibraryIds.TITLE));
add(new Label("item" + Long.BYTES));
if (Integer.MAX_VALUE > 0) {
    add(new Label("status"));
}
```

The field must have a constant value in its compiled Java PSI metadata. Analysis
does not load library classes, execute static initialization, run methods, or
choose values from attached source. A final field assigned by a static block,
`new String(...)`, or a getter remains unresolved. Mutable and boxed fields,
floating point, enum/object values, and binary method execution remain outside
this slice. Constructing the constant holder still requires the existing separate
initialization contract.

This uses IntelliJ's compiled field constant API. Its class-file stub builder
derives field initializers from binary constant metadata; see JetBrains'
[StubBuildingVisitor](https://github.com/JetBrains/intellij-community/blob/master/java/java-psi-impl/src/com/intellij/psi/impl/compiled/StubBuildingVisitor.java).
The metadata is defined by
[JVMS 25 §4.7.2](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.2).
Reading a Java constant variable does not require initializing its declaring
class, as described in
[JLS 25 §12.4.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.1).

## Resolution and editing

Java PSI resolves the exact accessible declaration, including qualified names,
static imports, nested classes, interfaces, and inherited constants. Ambiguous
or inaccessible references cannot establish a value. Instance-qualified access
such as `holder().TITLE` stays unsupported; receiver evaluation is not discarded.
Source constants may combine binary values using their existing expression audit.
Custom component constructor ID tracing retains its separate forwarding contract;
this slice does not interpret additional expressions inside constructor chains.

The whole use expression, such as `LibraryIds.TITLE`, retains ID provenance.
Native Java declaration navigation continues to work. Shared binary IDs do not
acquire per-pair rename, literal completion/coloring, or fixes that rewrite a
fragment. Java-only findings highlight the reference without a fragment fix.
Independent literal IDs under a known binary flag retain normal editor features.

## Snapshots and bounds

Consulted class files and lookup types join the report's internal Java dependency
list. Source lookup types remain dependencies too. Snapshots version class files
as binary metadata rather than opening them as text; JAR snapshots also include
the enclosing archive. An archive replacement invalidates the snapshot even if
class entries have not refreshed. SDK/library root changes invalidate the normal
root version. JDK runtime-image paths resolve through the IDE's `jrt` filesystem.

All dependency lists and versions remain value-only. Existing cancellation,
12-level lookup depth, 24-file and 5,000-step budgets, and 4,096-character String
limits remain in force. Exhausted or unsupported analysis retains uncertainty.
JSON schema 1 is unchanged; internal binary dependencies are not exported as
additional report properties.

## Demo and verification

Open `example.BinaryConstantSetupPage` in the portable Maven demo. It has three
MATCHED bindings: `xmlTitle`, `item8`, and `status`, using constants from
`XMLConstants`, `Long`, and `Integer`. No additional dependency is required.

The fixtures compile actual Java 17 class files and package a temporary dependency
JAR. They cover metadata types, unsupported initializers, access/import/scope
guards, inherited lookup, mismatched attached source, initialization avoidance,
String limits/escaping, provenance, editor restrictions, binary/JAR/JDK snapshot
capture, archive changes, library removal, native literal rename, and service
copying. Real application accuracy/performance evaluation remains deferred.
