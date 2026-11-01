#!/usr/bin/env python3
"""Check component context against the existing live export, with no new transport."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient

TOOL = "wicket_get_component_context"


def check(url, project):
    files = sorted(list(project.rglob("*.java")) + list(project.rglob("*.html")))
    before = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    client = McpClient(url)
    descriptors = client.tools()
    names = {item["name"] for item in descriptors}
    assert names == {"execute_tool", "wicketlens_mcp_probe", "wicket_get_page_structure", "wicket_validate_page", "wicket_get_injected_dependencies", "wicket_get_spring_inspections", TOOL}, names
    descriptor = next(item for item in descriptors if item["name"] == TOOL)
    for hint, expected in [("readOnlyHint", True), ("destructiveHint", False), ("idempotentHint", True), ("openWorldHint", False)]:
        assert descriptor["annotations"][hint] is expected
    assert {"projectPath", "className", "componentPath", "nodeId", "moduleName", "sourceSet"} <= descriptor["inputSchema"]["properties"].keys()

    def call(tool, arguments, error=None):
        result = client.call(tool, {"projectPath": str(project), **arguments})
        assert len(result["content"]) == 1 and result["content"][0]["type"] == "text"
        response = json.loads(result["content"][0]["text"])
        assert response["tool"] == tool and response["schemaVersion"] == 1
        if error:
            assert result.get("isError") and response["error"]["code"] == error, result
        else:
            assert not result.get("isError") and response["status"] == "ok" and response["truncated"] is False, result
        return response

    arguments = {"className": "example.McpPage", "moduleName": "mcp-fixture"}
    report = call("wicket_get_page_structure", arguments)["analysis"]
    response = call(TOOL, {**arguments, "componentPath": "form:name"})
    context = response["context"]
    node = next(item for item in report["components"] if item["wicketId"] == "name")
    assert context["component"] == node
    assert context["parent"] == next(item for item in report["components"] if item["nodeId"] == node["parentNodeId"])
    assert context["componentPath"] == "form:name" and context["hierarchyResolved"] is True
    assert context["findings"] == [item for item in report["matching"]["findings"] if item["javaNodeId"] == node["nodeId"]]
    markup_ids = {item["markupNodeId"] for item in context["findings"]}
    assert context["markupElements"] == [item for item in report["markup"]["elements"] if item["nodeId"] in markup_ids]
    assert call(TOOL, {**arguments, "nodeId": node["nodeId"]}) == response
    assert call(TOOL, {**arguments, "componentPath": "form:name"}) == response
    root = call(TOOL, {**arguments, "componentPath": ""})["context"]
    assert root["component"]["nodeId"] == report["rootNodeId"] and root["parent"] is None and root["ancestors"] == []
    assert len(root["children"]) == 2

    repeater = call(TOOL, {"className": "example.RepeaterPage", "componentPath": "rows:name"})
    assert repeater["context"]["parent"]["templateItem"] is True
    assert repeater["context"]["component"]["template"] is True
    assert repeater["context"]["findings"][0]["confidence"] == "INFERRED"
    item = call(TOOL, {"className": "example.RepeaterPage", "nodeId": repeater["context"]["parent"]["nodeId"]})["context"]
    assert item["componentPath"] is None and len(item["children"]) == 1
    unresolved_report = call("wicket_get_page_structure", {"className": "example.UnresolvedPage"})["analysis"]
    unresolved_node = next(item for item in unresolved_report["components"] if item["wicketId"] == "title")
    unresolved = call(TOOL, {"className": "example.UnresolvedPage", "nodeId": unresolved_node["nodeId"]})
    assert unresolved["context"]["warnings"] == unresolved_report["warnings"]
    assert any(item["causes"] for item in unresolved["context"]["findings"])
    duplicate_report = call("wicket_get_page_structure", {"className": "example.DuplicatePage"})["analysis"]
    duplicate_node = next(item for item in duplicate_report["components"] if item["wicketId"] == "title")
    duplicate = call(TOOL, {"className": "example.DuplicatePage", "nodeId": duplicate_node["nodeId"]})
    assert duplicate["context"]["componentPath"] is None

    errors = {}
    for args, code in [
        ({**arguments, "componentPath": "missing"}, "COMPONENT_NOT_FOUND"),
        ({**arguments, "nodeId": "obsolete"}, "COMPONENT_NOT_FOUND"),
        ({**arguments}, "INVALID_REQUEST"),
        ({**arguments, "componentPath": "form::name"}, "INVALID_REQUEST"),
        ({**arguments, "componentPath": "form:name", "nodeId": node["nodeId"]}, "INVALID_REQUEST"),
        ({"className": "example.McpPage", "componentPath": "title"}, "AMBIGUOUS_CLASS"),
        ({"className": "example.DuplicatePage", "componentPath": "title"}, "AMBIGUOUS_COMPONENT"),
        ({"className": "example.RepeaterPage", "componentPath": "rows:0:name"}, "COMPONENT_NOT_FOUND"),
    ]:
        errors[code] = call(TOOL, args, code)["error"]["code"]

    java = project / "mcp-fixture/src/main/java/example/McpPage.java"
    html = project / "mcp-fixture/src/main/resources/example/McpPage.html"
    old_java, old_html = java.read_text(), html.read_text()
    try:
        java.write_text(old_java.replace('"name"', '"externalName"'))
        html.write_text(old_html.replace('"name"', '"externalName"'))
        edited = call(TOOL, {**arguments, "componentPath": "form:externalName"})["context"]
        assert edited["component"]["wicketId"] == "externalName" and edited["findings"][0]["code"] == "MATCHED"
        call(TOOL, {**arguments, "componentPath": "form:name"}, "COMPONENT_NOT_FOUND")
    finally:
        java.write_text(old_java)
        html.write_text(old_html)
    assert call(TOOL, {**arguments, "componentPath": "form:name"}) == response
    assert before == {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}

    def redact(value):
        if isinstance(value, str): return value.replace(str(project), "<fixture-project>")
        if isinstance(value, dict): return {key: redact(item) for key, item in value.items()}
        if isinstance(value, list): return [redact(item) for item in value]
        return value

    print(json.dumps({"protocolVersion": client.protocol_version, "tools": sorted(names), "nestedResponse": redact(response),
                      "repeaterResponse": redact(repeater), "unresolvedResponse": redact(unresolved),
                      "errorCodes": errors, "exactExportObjects": True, "nodeIdAndPathAgree": True,
                      "externalEditsRefreshed": True, "repeatedRequestEqual": True, "fixtureSourcesUnchanged": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    options = parser.parse_args()
    check(options.url, options.project_path.resolve())
