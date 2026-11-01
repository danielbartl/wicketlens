#!/usr/bin/env python3
"""Verify native Spring inspection findings through the isolated JetBrains MCP host."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient

TOOL = "wicket_get_spring_inspections"


def check(url, project):
    if not (project / ".wicketlens-mcp-fixture").is_file():
        raise ValueError("Use the owned disposable fixture, not a real application project.")
    files = sorted(p for p in project.rglob("*") if p.suffix in {".java", ".html", ".iml"})
    def hashes():
        return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    before = hashes()
    client = McpClient(url)
    descriptor = next(t for t in client.tools() if t["name"] == TOOL)
    for hint, value in (("readOnlyHint", True), ("destructiveHint", False), ("idempotentHint", True), ("openWorldHint", False)):
        assert descriptor["annotations"][hint] is value
    assert {"projectPath", "className", "moduleName", "sourceSet"} <= descriptor["inputSchema"]["properties"].keys()
    def call(module="mcp-spring"):
        result = client.call(TOOL, {"projectPath": str(project), "className": "example.McpPage", "moduleName": module})
        assert not result.get("isError"), result
        response = json.loads(result["content"][0]["text"])
        assert response["schemaVersion"] == 1 and response["tool"] == TOOL and response["status"] == "ok"
        return response
    response = call()
    data = response["springInspections"]
    assert data["schemaVersion"] == 1 and not data["runtimeValidityVerified"] and not data["truncated"]
    findings = data["findings"]
    assert {f["inspectionId"] for f in findings} == {"WicketSpringInjection", "WicketSpringSerialization"}
    assert len(findings) == 2 and all(f["nodeIds"] == ["page"] for f in findings)
    settings = {(s["file"], s["inspectionId"]): s for s in data["checks"]}
    for finding in findings:
        assert settings[(finding["location"]["file"], finding["inspectionId"])]["enabled"]
        assert finding["severity"] == settings[(finding["location"]["file"], finding["inspectionId"])]["severity"]
    java = project / "mcp-spring/src/main/java/example/McpPage.java"
    original = java.read_text()
    for f in findings:
        text = original[f["location"]["offset"]:f["location"]["endOffset"]]
        assert text == ("null" if f["inspectionId"] == "WicketSpringInjection" else "orders")
    assert call() == response
    assert call("mcp-fixture")["springInspections"]["findings"] == []
    assert hashes() == before
    try:
        annotation = '@org.apache.wicket.spring.injection.annot.SpringBean(name="alias")'
        java.write_text(original.replace(annotation, '@SuppressWarnings({"WicketSpringInjection","WicketSpringSerialization"}) ' + annotation))
        assert call()["springInspections"]["findings"] == []
    finally:
        java.write_text(original)
    assert call() == response and hashes() == before
    def redact(value):
        if isinstance(value, str): return value.replace(str(project), "<fixture-project>")
        if isinstance(value, list): return [redact(v) for v in value]
        if isinstance(value, dict): return {k: redact(v) for k, v in value.items()}
        return value
    print(json.dumps({"acceptance": "PASS", "protocolVersion": client.protocol_version, "response": redact(response),
                      "readOnlyHints": True, "exactSourceRanges": True, "configuredSeverityPreserved": True,
                      "externalSuppressionEditsRefreshed": True, "plainWicketQuiet": True,
                      "deterministic": True, "fixtureInputsRestored": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    args = parser.parse_args()
    check(args.url, args.project_path.resolve())
