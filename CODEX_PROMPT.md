# Codex implementation prompt — WicketLens feasibility prototype

You are a senior IntelliJ Platform plugin engineer and Apache Wicket specialist. Implement a runnable, tested proof-of-concept IntelliJ IDEA plugin named **WicketLens** in this repository. Do not stop after planning: create code, tests, fixtures, build configuration, and documentation, then run the available verification tasks.

Read spec.md first. Treat its acceptance criteria and explicit exclusions as binding.

## Mission

Prove that IntelliJ PSI can discover statically declared Wicket components in Java, reconstruct common parent-child relationships, locate the associated HTML markup, and report matched, unmatched, and unresolved IDs without false certainty.

## Working principles

1. Use IntelliJ Platform Gradle Plugin 2.x and a recent stable IntelliJ IDEA target. Verify compatible versions.
2. Use Kotlin for plugin code. Declare required Java plugin dependencies and use PSI rather than regex for Java analysis.
3. Support add(new Label("title", ...)), variables followed by add(variable), nested container.add(child), and literal string IDs.
4. Resolve conventional Java-to-HTML pairing. Treat missing or ambiguous markup as unresolved.
5. Extract wicket:id values through HTML/XML PSI. Match components with their correct nesting context.
6. Provide an Analyze Wicket Page action and a readable report inside IntelliJ.
7. Provide Copy Wicket Context to export structured JSON with component types, IDs, parent relationships, statuses, and source locations.
8. Keep analysis read-only, cancellable, and off the UI thread where appropriate.
9. Add automated PSI fixture tests covering static components, nesting, variables, missing IDs, dynamic IDs, and unrelated add methods.
10. Provide README instructions for building, testing, and launching a sandbox IDE.

## Execution sequence

- Inspect repository and SDK documentation.
- Create a short implementation plan and proceed.
- Establish build and sandbox launch.
- Implement Java PSI discovery with tests.
- Implement HTML PSI extraction and file pairing.
- Implement conservative matching and confidence classification.
- Add UI action and JSON export.
- Run tests, build, and available plugin verification tasks.
- Document limitations and report actual test results.

## Non-goals

No LLM integration, MCP server, AI chat UI, automatic code edits, Wicket runtime instrumentation, full project index, arbitrary factory resolution, or full markup inheritance.

## Definition of done

A launchable IntelliJ plugin can analyze a simple Wicket Java page and its companion HTML, show a component hierarchy with ID matching, and export structured JSON. Tests cover the supported patterns. Explicitly report anything incomplete.
