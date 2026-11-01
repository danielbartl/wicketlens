#!/usr/bin/env python3
"""Check general plain/Spring planning over the existing owned native-host fixture."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient

TOOL = "wicket_get_test_plan"


def check(url, project):
    if not (project / ".wicketlens-mcp-fixture").is_file():
        raise ValueError("Use the owned disposable structure fixture.")
    def hashes():
        return {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(project.rglob("*")) if p.suffix in {".java", ".html", ".iml"}}
    before = hashes()
    client = McpClient(url)
    descriptor = next(t for t in client.tools() if t["name"] == TOOL)
    for key, expected in (("readOnlyHint", True), ("destructiveHint", False), ("idempotentHint", True), ("openWorldHint", False)):
        assert descriptor["annotations"][key] is expected
    def call(module="mcp-fixture", tool=TOOL):
        result = client.call(tool, {"projectPath": str(project), "className": "example.McpPage", "moduleName": module})
        assert not result.get("isError"), result
        response = json.loads(result["content"][0]["text"])
        assert response["status"] == "ok" and response["schemaVersion"] == 1 and not response["truncated"]
        return response
    plain = call()
    plan = plain["testPlan"]
    assert plan["kind"] == "isolated_wicket_tester_scaffold" and plan["constructorStrategy"] == "PAGE"
    assert not plan["runtimeValidityVerified"] and not plan["dependencyAvailabilityChecked"]
    assert plan["mocks"] == [] and plan["manualBindings"] == []
    assert not any(s in plan["source"] for s in ("org.springframework", "org.mockito", "SpringComponentInjector"))
    assert len(plan["requiredDependencies"]) == 3
    spring = call("mcp-spring")
    assert spring["testPlan"] == call("mcp-spring", "wicket_get_spring_test_plan")["springTestPlan"]
    assert call() == plain
    java = project / "mcp-fixture/src/main/java/example/McpPage.java"
    original = java.read_bytes()
    try:
        text = original.decode()
        assert text.count("public McpPage()") == 1
        java.write_text(text.replace("public McpPage()", "public McpPage(int required)"))
        manual = call()["testPlan"]
        assert manual["constructorStrategy"] == "MANUAL_PAGE" and 'UnsupportedOperationException("Configure the subject constructor")' in manual["source"]
    finally:
        java.write_bytes(original)
    assert call() == plain and hashes() == before
    def redact(v):
        if isinstance(v, str): return v.replace(str(project), "<fixture-project>")
        if isinstance(v, list): return [redact(x) for x in v]
        if isinstance(v, dict): return {k: redact(x) for k, x in v.items()}
        return v
    print(json.dumps({"acceptance": "PASS", "plain": redact(plain), "spring": redact(spring),
                      "sharedSpringRecipe": True, "externalConstructorEditRefreshed": True,
                      "deterministic": True, "authoredInputsRestored": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    args = parser.parse_args()
    check(args.url, args.project_path.resolve())
