#!/usr/bin/env python3
"""Verify dependency/profile/FactoryBean facts through the actual bundled MCP host."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient

TOOL = "wicket_get_injected_dependencies"


def check(url, project):
    if not (project / ".wicketlens-mcp-fixture").is_file():
        raise ValueError("Use the owned disposable development fixture, not an application project.")
    files = sorted(p for p in project.rglob("*") if p.suffix in {".java", ".html", ".iml"})
    def hashes():
        return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    before = hashes()
    client = McpClient(url)
    descriptors = client.tools()
    descriptor = next(tool for tool in descriptors if tool["name"] == TOOL)
    for name, value in (("readOnlyHint", True), ("destructiveHint", False), ("idempotentHint", True), ("openWorldHint", False)):
        assert descriptor["annotations"][name] is value
    assert {"projectPath", "className", "moduleName", "sourceSet"} <= descriptor["inputSchema"]["properties"].keys()

    def call(tool=TOOL, module="mcp-spring"):
        result = client.call(tool, {"projectPath": str(project), "className": "example.McpPage", "moduleName": module})
        assert not result.get("isError"), result
        response = json.loads(result["content"][0]["text"])
        assert response["status"] == "ok" and response["schemaVersion"] == 1 and not response["truncated"]
        return response

    response = call()
    data = response["dependencies"]
    assert data["schemaVersion"] == 1 and not data["runtimeBindingsVerified"] and not data["profileActivationEvaluated"]
    assert data["fieldCount"] == 3 and data["registeredApplication"] is None
    owner = data["owners"][0]
    fields = {field["name"]: field for field in owner["fields"]}
    product = fields["orders"]["candidates"][0]
    assert product["names"] == ["alias", "orders"] and product["type"] == "example.Service"
    assert product["factory"] and not product["factoryLookup"] and product["compatible"]
    assert product["profiles"] == ["(!test)", "((prod & eu) OR (staging))"]
    factory = fields["factory"]["candidates"][0]
    assert factory["factoryLookup"] and factory["names"] == ["&alias", "&orders"]
    assert fields["optional"]["required"] is False and fields["optional"]["candidates"] == []
    graph = call("wicket_get_page_structure")["analysis"]
    assert owner["nodeId"] == graph["rootNodeId"]
    assert call() == response
    assert call(module="mcp-fixture")["dependencies"]["owners"] == []
    assert hashes() == before
    config = project / "mcp-spring/src/main/java/example/SpringConfig.java"
    original = config.read_text()
    try:
        config.write_text(original.replace('"!test"', '"!qa"'))
        assert call()["dependencies"]["owners"][0]["fields"][0]["candidates"][0]["profiles"][0] == "(!qa)"
    finally:
        config.write_text(original)
    assert call() == response and hashes() == before

    def redact(value):
        if isinstance(value, str): return value.replace(str(project), "<fixture-project>")
        if isinstance(value, list): return [redact(item) for item in value]
        if isinstance(value, dict): return {key: redact(item) for key, item in value.items()}
        return value

    print(json.dumps({"acceptance": "PASS", "protocolVersion": client.protocol_version,
                      "tools": sorted(tool["name"] for tool in descriptors), "response": redact(response),
                      "sameGraphNodeIdentity": True, "externalProfileEditsRefreshed": True,
                      "plainWicketRemainsEmpty": True, "repeatedResponseEqual": True,
                      "fixtureInputsRestored": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    args = parser.parse_args()
    check(args.url, args.project_path.resolve())
