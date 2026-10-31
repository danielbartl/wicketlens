#!/usr/bin/env python3
"""Check semantic MCP success/errors and source immutability in the disposable fixture."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient, EXPECTED

TOOL = "wicket_get_page_structure"


def check(url, project):
    files = sorted(list(project.rglob("*.java")) + list(project.rglob("*.html")))
    before = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    client = McpClient(url)
    tools = client.tools()
    names = {tool["name"] for tool in tools}
    assert {"execute_tool", "wicketlens_mcp_probe", TOOL} <= names and not names - {"execute_tool", "wicketlens_mcp_probe", TOOL, "wicket_validate_page", "wicket_get_component_context"}, names
    descriptor = next(tool for tool in tools if tool["name"] == TOOL)
    assert descriptor["annotations"]["readOnlyHint"] is True
    assert descriptor["annotations"]["openWorldHint"] is False

    def call(arguments, error=None):
        result = client.call(TOOL, {"projectPath": str(project), **arguments})
        content = result["content"]
        assert len(content) == 1 and content[0]["type"] == "text", result
        data = json.loads(content[0]["text"])
        assert data["schemaVersion"] == 1 and data["tool"] == TOOL
        if error:
            assert result.get("isError") is True and data["status"] == "error", result
            assert data["error"]["code"] == error, data
        else:
            assert not result.get("isError") and data["status"] == "ok", result
            assert data["truncated"] is False
        return data

    arguments = {"className": "example.McpPage", "moduleName": "mcp-fixture"}
    response = call(arguments)
    report = response["analysis"]
    assert report["schemaVersion"] == 1 and report["qualifiedClassName"] == "example.McpPage"
    components = {item["wicketId"]: item for item in report["components"] if item["wicketId"]}
    assert set(components) == {"title", "form", "name"}, report
    assert all(item["status"] == "MATCHED" for item in components.values()), report
    assert components["name"]["parentNodeId"] == components["form"]["nodeId"], report
    assert call(arguments) == response
    failures = {}
    for args, code in [
        ({"className": "example.McpPage"}, "AMBIGUOUS_CLASS"),
        ({"className": "example.Missing"}, "CLASS_NOT_FOUND"),
        ({"className": "example.PlainJava", "moduleName": "mcp-fixture"}, "UNSUPPORTED_TARGET"),
        ({"className": "../outside/Page.java"}, "INVALID_REQUEST"),
        ({"className": "example.McpPage", "sourceSet": "wrong"}, "INVALID_REQUEST"),
    ]:
        failures[code] = call(args, code)["error"]["code"]
    other = call({"className": "example.McpPage", "moduleName": "mcp-other"})
    assert {item["wicketId"] for item in other["analysis"]["components"] if item["wicketId"]} == {"other"}
    probe = client.call("wicketlens_mcp_probe", {"projectPath": str(project)})
    assert json.loads(probe["content"][0]["text"]) == EXPECTED
    java = project / "mcp-fixture/src/main/java/example/McpPage.java"
    html = project / "mcp-fixture/src/main/resources/example/McpPage.html"
    java_text, html_text = java.read_text(), html.read_text()
    new_java, new_html = java.with_name("FreshPage.java"), html.with_name("FreshPage.html")
    try:
        java.write_text(java_text.replace('"title"', '"externalTitle"'))
        html.write_text(html_text.replace('"title"', '"externalTitle"'))
        edited = call(arguments)
        assert {item["wicketId"] for item in edited["analysis"]["components"] if item["wicketId"]} == {"externalTitle", "form", "name"}
        assert all(item["code"] == "MATCHED" for item in edited["analysis"]["matching"]["findings"])
        new_java.write_text(java_text.replace("McpPage", "FreshPage"))
        new_html.write_text(html_text)
        fresh = call({"className": "example.FreshPage", "moduleName": "mcp-fixture"})
        assert fresh["analysis"]["qualifiedClassName"] == "example.FreshPage"
    finally:
        java.write_text(java_text)
        html.write_text(html_text)
        new_java.unlink(missing_ok=True)
        new_html.unlink(missing_ok=True)
    assert call(arguments) == response
    assert before == {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}

    def redact(value):
        if isinstance(value, str):
            return value.replace(str(project), "<fixture-project>")
        if isinstance(value, dict):
            return {key: redact(item) for key, item in value.items()}
        if isinstance(value, list):
            return [redact(item) for item in value]
        return value

    print(json.dumps({"protocolVersion": client.protocol_version, "tools": sorted(names), "response": redact(response),
                      "errorCodes": failures, "repeatedRequestEqual": True, "fixtureSourcesUnchanged": True,
                      "otherModuleSelection": "mcp-other", "originalProbeUnchanged": True,
                      "externalDiskEditsRefreshed": True, "newDiskSourceDiscovered": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    args = parser.parse_args()
    check(args.url, args.project_path.resolve())
