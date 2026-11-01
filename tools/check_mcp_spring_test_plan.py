#!/usr/bin/env python3
"""Verify the existing Spring scaffold over the actual host using only owned fixtures."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient

TOOL = "wicket_get_spring_test_plan"


def check(url, project):
    if not (project / ".wicketlens-mcp-fixture").is_file():
        raise ValueError("Use the owned disposable fixture, not an application project.")
    def hashes():
        return {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(project.rglob("*")) if p.suffix in {".java", ".html", ".iml"}}
    before = hashes()
    client = McpClient(url)
    descriptor = next(t for t in client.tools() if t["name"] == TOOL)
    for hint, value in (("readOnlyHint", True), ("destructiveHint", False), ("idempotentHint", True), ("openWorldHint", False)):
        assert descriptor["annotations"][hint] is value
    assert {"projectPath", "className", "moduleName", "sourceSet"} <= descriptor["inputSchema"]["properties"].keys()

    def call(module="mcp-spring", error=None):
        result = client.call(TOOL, {"projectPath": str(project), "className": "example.McpPage", "moduleName": module})
        response = json.loads(result["content"][0]["text"])
        assert response["schemaVersion"] == 1 and response["tool"] == TOOL
        if error:
            assert result.get("isError") and response["error"]["code"] == error, response
        else:
            assert not result.get("isError") and response["status"] == "ok" and not response["truncated"], response
        return response

    response = call()
    plan = response["springTestPlan"]
    assert plan["schemaVersion"] == 1 and plan["constructorStrategy"] == "PAGE"
    assert not plan["runtimeValidityVerified"] and not plan["dependencyAvailabilityChecked"]
    assert plan["targetClass"] == "example.McpPage" and plan["suggestedFileName"] == "McpPageSpringTest.java"
    assert plan["mocks"] == [{"beanName": "external", "type": "example.Service"}], plan["mocks"]
    assert len(plan["manualBindings"]) == 2 and plan["testRoots"] == []
    assert "new SpringComponentInjector" in plan["source"] and "throw new UnsupportedOperationException(" in plan["source"]
    assert "org.apache.wicket:wicket-tester" in plan["requiredDependencies"]
    assert call() == response
    call("mcp-fixture", "TEST_PLAN_UNAVAILABLE")
    call(None, "AMBIGUOUS_CLASS")
    assert hashes() == before
    java = project / "mcp-spring/src/main/java/example/McpPage.java"
    original = java.read_text()
    try:
        java.write_text(original.replace('name="external"', 'name="externalUpdated"'))
        updated = call()["springTestPlan"]
        assert updated["mocks"] == [{"beanName": "externalUpdated", "type": "example.Service"}]
        assert 'registerSingleton("externalUpdated"' in updated["source"]
    finally:
        java.write_text(original)
    assert call() == response and hashes() == before

    def redact(value):
        if isinstance(value, str): return value.replace(str(project), "<fixture-project>")
        if isinstance(value, list): return [redact(v) for v in value]
        if isinstance(value, dict): return {k: redact(v) for k, v in value.items()}
        return value
    print(json.dumps({"acceptance": "PASS", "protocolVersion": client.protocol_version,
                      "response": redact(response), "readOnlyHints": True, "manualGuardsPreserved": True,
                      "externalBindingEditsRefreshed": True, "plainWicketRejected": True,
                      "ambiguousModuleRejected": True, "deterministic": True, "fixtureInputsRestored": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    args = parser.parse_args()
    check(args.url, args.project_path.resolve())
