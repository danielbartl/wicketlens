# WicketLens — MCP Integration Concept

## 1. Vision

Extend **WicketLens**, an IntelliJ IDEA plugin for Apache Wicket development, with Model Context Protocol (MCP) capabilities.

The goal is to make WicketLens not only a traditional IDE plugin but also a **Wicket-specific intelligence provider for AI coding agents**, including OpenAI Codex, Claude Code, and other MCP-compatible clients.

WicketLens should expose its knowledge of Apache Wicket applications through structured MCP tools, allowing AI agents to understand, analyze, validate, and eventually refactor Wicket applications more reliably.

**Core principle:** WicketLens provides Wicket-specific intelligence; existing AI agents provide reasoning, code generation, and orchestration.

WicketLens does not need its own LLM or chat interface.

## 2. Existing Project Integration

The MCP functionality should extend the existing WicketLens project rather than introduce a separate implementation.

The current semantic analysis functionality should be reused wherever possible.

Expected existing or planned capabilities include:

- Java PSI analysis of Wicket components.
- HTML PSI analysis of `wicket:id` attributes.
- Java/HTML component matching.
- Component hierarchy reconstruction.
- Diagnostics for missing or inconsistent component references.
- Confidence classification for statically resolved versus dynamic components.
- Structured representation of Wicket pages and panels.

These capabilities should form the foundation of the MCP integration.

If the current implementation differs from these assumptions, adapt the integration to the actual architecture instead of duplicating existing functionality.

## 3. Recommended Architecture

Prefer integrating with the **built-in JetBrains MCP server** rather than implementing a standalone MCP server.

IntelliJ IDEA 2025.2 and newer include an integrated MCP server. Investigate the available APIs and extension points for contributing custom MCP tools from third-party IntelliJ plugins.

Proposed architecture:

```text
                  AI Coding Agents
             Codex / Claude Code / Others
                         |
                         | MCP
                         v
              JetBrains MCP Server
                         |
                         | Custom tool integration
                         v
                 WicketLens MCP
                         |
                         v
              WicketLens Semantic API
                         |
            +------------+------------+
            |            |            |
            v            v            v
         Java PSI     HTML PSI     Diagnostics
            |            |            |
            +------------+------------+
                         |
                         v
                Apache Wicket Project
```

### Architectural requirements

1. Reuse existing semantic analysis services.
2. Avoid implementing a second Wicket parser.
3. Keep MCP transport logic separate from Wicket analysis.
4. Use structured, versioned tool responses.
5. Make the analysis services reusable by both IntelliJ UI actions and MCP tools.
6. Keep the initial MCP tools read-only.
7. Support multiple open projects through explicit project scoping where necessary.
8. Do not introduce an LLM dependency.
9. Avoid external network requests from WicketLens itself.
10. Preserve compatibility with the existing plugin functionality.

If JetBrains' MCP extension APIs are unavailable, unstable, or unsuitable, document the limitation and evaluate an alternative local MCP adapter.

Do not immediately introduce a custom server without first investigating the built-in integration.

## 4. Initial MCP Tools

The first implementation should expose three tools.

### Tool 1: `wicket_get_page_structure`

**Purpose:** Allow AI agents to understand the structure of a Wicket page or panel.

Example input:

```json
{
  "className": "com.example.web.CustomerEditPage"
}
```

Example response:

```json
{
  "schemaVersion": "1.0",
  "page": "com.example.web.CustomerEditPage",
  "components": [
    {
      "id": "customerForm",
      "type": "Form",
      "confidence": "CONFIRMED",
      "children": [
        {
          "id": "email",
          "type": "EmailTextField",
          "confidence": "CONFIRMED"
        },
        {
          "id": "save",
          "type": "AjaxButton",
          "confidence": "CONFIRMED"
        }
      ]
    }
  ]
}
```

The response should also include source locations and markup relationships when available.

Dynamic or unresolved components must be clearly identified rather than guessed.

### Tool 2: `wicket_validate_page`

**Purpose:** Validate the consistency of Java component declarations and Wicket HTML markup.

Example input:

```json
{
  "className": "com.example.web.CustomerEditPage"
}
```

Example response:

```json
{
  "schemaVersion": "1.0",
  "page": "com.example.web.CustomerEditPage",
  "diagnostics": [
    {
      "severity": "ERROR",
      "code": "MISSING_MARKUP_ID",
      "componentId": "customerName",
      "message": "No matching wicket:id found in the resolved markup.",
      "confidence": "CONFIRMED"
    }
  ]
}
```

The analyzer should distinguish between:

- Confirmed inconsistencies.
- Potential inconsistencies.
- Unresolved dynamic relationships.
- Unsupported Wicket constructs.

Avoid reporting definitive errors when the available static analysis is incomplete.

### Tool 3: `wicket_get_component_context`

**Purpose:** Provide detailed Wicket-specific context for a component.

Example input:

```json
{
  "className": "com.example.web.CustomerEditPage",
  "componentPath": "customerForm:email"
}
```

Example response:

```json
{
  "schemaVersion": "1.0",
  "componentPath": "customerForm:email",
  "type": "EmailTextField",
  "parent": "customerForm",
  "model": {
    "type": "PropertyModel",
    "expression": "email",
    "confidence": "INFERRED"
  },
  "markup": {
    "element": "input",
    "wicketId": "email"
  }
}
```

Only return model information if the existing analyzer can actually determine it. Unsupported fields should be omitted or marked unresolved.

## 5. Example AI Workflows

### Workflow A — Understand a page

Developer asks Codex:

"Explain the structure of CustomerEditPage, including its components and their HTML relationships."

Codex calls `wicket_get_page_structure` and uses the structured response to explain the page.

### Workflow B — Validate generated code

Developer asks Codex:

"Add a customer phone number field to CustomerEditPage."

Codex modifies the Java and HTML files using its existing file-editing capabilities.

Afterward, Codex calls `wicket_validate_page` to identify inconsistencies.

If confirmed issues exist, Codex can correct them and validate again.

### Workflow C — Refactor components

Developer asks Codex:

"Extract the address fields from CustomerEditPage into a reusable AddressPanel."

Codex retrieves the component hierarchy and context before making changes.

It then modifies the Java and HTML files and calls the validation tool to check the resulting structure.

### Workflow D — Generate WicketTester tests

Developer asks Codex:

"Generate WicketTester tests for CustomerEditPage."

Codex retrieves the page structure and relevant component context, then generates tests using the project's existing test conventions.

WicketLens does not need to generate the tests itself.

## 6. Implementation Phases

### Phase 1 — Technical feasibility spike

Estimated effort: 4–8 hours.

Tasks:

1. Inspect the current WicketLens architecture.
2. Investigate the JetBrains built-in MCP server and its plugin extension APIs.
3. Verify compatibility with the project's targeted IntelliJ versions.
4. Implement a minimal custom MCP tool returning a static response.
5. Connect an external MCP client, preferably Codex.
6. Confirm that the tool can be discovered and invoked.

**Acceptance criterion:** Codex can successfully invoke a WicketLens-provided MCP tool through IntelliJ's MCP server.

If this cannot be achieved through supported APIs, document the blocker and propose an alternative.

### Phase 2 — Semantic engine integration

Estimated effort: 8–16 hours.

Tasks:

1. Define stable tool request and response models.
2. Connect the page structure tool to the existing semantic engine.
3. Connect the validation tool to existing diagnostics.
4. Implement component context retrieval.
5. Handle missing classes, ambiguous references, unsupported constructs, and analysis failures.
6. Ensure analysis runs safely with IntelliJ threading and PSI read-access requirements.

**Acceptance criterion:** MCP responses accurately represent the same analysis results available through the existing WicketLens plugin.

### Phase 3 — Reliability and testing

Estimated effort: 8–16 hours.

Tasks:

1. Add automated tests for tool registration and response serialization.
2. Test against real Wicket applications.
3. Verify that MCP tools cannot modify project files.
4. Handle project closing, indexing, cancellation, and unavailable PSI data.
5. Test repeated requests and large component trees.
6. Document MCP configuration and supported IDE versions.
7. Verify compatibility with Codex and, if practical, another MCP client.

**Acceptance criterion:** The integration is reliable enough for an experimental public release.

### Overall estimate

Approximately **20–40 hours** of additional development, assuming the existing semantic engine is functional and the JetBrains MCP integration APIs are accessible.

A standalone MCP implementation or major semantic-engine refactoring could increase the effort.

## 7. Security and Privacy

WicketLens may analyze proprietary application source code.

Therefore:

- The MCP integration must operate locally by default.
- No source code should be sent directly to external AI providers by WicketLens.
- MCP tools should be read-only initially.
- Responses should contain only information necessary for the requested analysis.
- File access must be limited to the selected IntelliJ project.
- Requests must not permit arbitrary filesystem traversal.
- Large responses should be bounded or truncated explicitly.
- Tool errors must not leak unrelated files, credentials, or environment variables.
- The MCP server should use the JetBrains platform's supported local authentication and connection mechanisms where available.

Important: Although WicketLens itself does not transmit code to an external provider, the connected AI agent may send MCP responses to its model provider. This depends on the agent's configuration and privacy policies.

## 8. Future Extensions

The following capabilities are intentionally excluded from the initial implementation but should be considered when designing the interfaces.

### Advanced analysis

- `wicket_find_component_usages`
- `wicket_explain_model`
- `wicket_analyze_ajax`
- `wicket_analyze_lifecycle`
- `wicket_get_test_scenarios`

### Runtime intelligence

Potential integration with a running Wicket application to inspect:

- Actual component hierarchies.
- Component visibility and enabled state.
- Model values and model types.
- Ajax update targets.
- Lifecycle events.
- Runtime component paths.

Runtime integration must be opt-in and must avoid exposing sensitive application data by default.

### Agent-assisted development

Potential workflows include:

- Wicket page and panel generation.
- Cross-file component refactoring.
- WicketTester test generation.
- Migration between Wicket versions.
- Automated post-change validation.
- Identification of outdated Wicket patterns.

## 9. Product Positioning

**WicketLens — Apache Wicket Intelligence for IntelliJ and AI Agents**

The product should have three complementary layers:

1. **WicketLens Core:** Semantic analysis and component understanding.
2. **WicketLens IntelliJ:** Navigation, inspections, visualization, and developer actions.
3. **WicketLens MCP:** Structured access to Wicket intelligence for external AI coding agents.

All three layers should reuse the same underlying analysis wherever possible.

The MCP integration should remain optional so WicketLens continues to function as a conventional IntelliJ plugin.

## 10. Instructions for Codex

Before implementing this proposal:

1. Inspect the existing WicketLens codebase.
2. Identify which semantic analysis services can be reused.
3. Verify the actual JetBrains MCP extension API against the installed SDK.
4. Check the plugin's current minimum supported IntelliJ version.
5. Identify any compatibility risks.
6. Propose the smallest feasible integration.

Then implement Phase 1 as a technical spike.

Do not build a standalone MCP server, implement all three tools, or substantially refactor the semantic engine until the feasibility spike has demonstrated that the chosen integration approach works.

After Phase 1, report:

- Whether JetBrains' built-in MCP server supports the required extension mechanism.
- Whether Codex can discover and invoke a WicketLens tool.
- Which IntelliJ versions are supported.
- Whether any experimental or unstable APIs are required.
- What changes would be needed in the existing architecture.
- A revised effort estimate for completing Phases 2 and 3.

**Primary objective:** Demonstrate that an external AI coding agent can access WicketLens-specific semantic intelligence through MCP while reusing the existing IntelliJ plugin infrastructure.