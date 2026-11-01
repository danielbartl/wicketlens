#!/usr/bin/env python3
"""Verify validation as an exact read-only view of live structure findings."""
import argparse
import hashlib
import json
from pathlib import Path
from check_mcp_spike import McpClient

TOOL = "wicket_validate_page"


def check(url, project):
    files = sorted(list(project.rglob("*.java")) + list(project.rglob("*.html")))
    before = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    client = McpClient(url)
    descriptors = client.tools()
    names = {item["name"] for item in descriptors}
    required = {"execute_tool", "wicketlens_mcp_probe", "wicket_get_page_structure", TOOL}
    assert required <= names and not names - required - {"wicket_get_component_context", "wicket_get_injected_dependencies", "wicket_get_spring_inspections", "wicket_get_spring_test_plan", "wicket_get_test_plan"}, names
    descriptor = next(item for item in descriptors if item["name"] == TOOL)
    assert descriptor["annotations"]["readOnlyHint"] is True
    assert descriptor["annotations"]["destructiveHint"] is False
    assert descriptor["annotations"]["openWorldHint"] is False

    def call(tool, arguments, error=None):
        result = client.call(tool, {"projectPath": str(project), **arguments})
        response = json.loads(result["content"][0]["text"])
        assert response["tool"] == tool and response["schemaVersion"] == 1
        if error:
            assert result.get("isError") and response["error"]["code"] == error, result
        else:
            assert not result.get("isError") and response["status"] == "ok", result
        return response

    def compare(name):
        arguments = {"className": f"example.{name}", "moduleName": "mcp-fixture"}
        response = call(TOOL, arguments)
        validation = response["validation"]
        report = call("wicket_get_page_structure", arguments)["analysis"]
        assert validation["findings"] == report["matching"]["findings"]
        assert validation["warnings"] == report["warnings"]
        assert validation["markupDiagnostics"] == report["markup"]["diagnostics"]
        assert validation["documentDiagnostics"] == report["markup"]["documentDiagnostics"]
        assert validation["scopeComplete"] == {"java": report["scopeComplete"], "markup": report["markup"]["scopeComplete"], "matching": report["matching"]["scopeComplete"]}
        assert call(TOOL, arguments) == response
        return response

    matched = compare("McpPage")
    assert matched["validation"]["summary"]["findingsByCode"] == {"MATCHED": 3}
    mismatched = compare("ValidationPage")
    data = mismatched["validation"]
    assert data["summary"]["findingsByCode"] == {"JAVA_ONLY": 1, "MARKUP_ONLY": 1}
    assert data["summary"]["findingsBySeverity"] == {"INFO": 2}
    unresolved = compare("UnresolvedPage")
    assert unresolved["validation"]["warnings"]
    assert all(item["code"] not in {"MATCHED", "JAVA_ONLY", "MARKUP_ONLY"} for item in unresolved["validation"]["findings"])
    assert any(item["causes"] for item in unresolved["validation"]["findings"])
    errors = {}
    for arguments, code in [
        ({"className": "example.McpPage"}, "AMBIGUOUS_CLASS"),
        ({"className": "example.Missing"}, "CLASS_NOT_FOUND"),
        ({"className": "example.PlainJava", "moduleName": "mcp-fixture"}, "UNSUPPORTED_TARGET"),
        ({"className": "../outside/Page.java"}, "INVALID_REQUEST"),
    ]:
        errors[code] = call(TOOL, arguments, code)["error"]["code"]
    java = project / "mcp-fixture/src/main/java/example/ValidationPage.java"
    old = java.read_text()
    try:
        java.write_text(old.replace('"javaOnly"', '"htmlOnly"'))
        fixed = compare("ValidationPage")
        assert fixed["validation"]["summary"]["findingsByCode"] == {"MATCHED": 1}
    finally:
        java.write_text(old)
    assert compare("ValidationPage") == mismatched
    assert before == {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}

    def redact(value):
        if isinstance(value, str): return value.replace(str(project), "<fixture-project>")
        if isinstance(value, dict): return {key: redact(item) for key, item in value.items()}
        if isinstance(value, list): return [redact(item) for item in value]
        return value

    print(json.dumps({"protocolVersion": client.protocol_version, "tools": sorted(names), "matchedResponse": redact(matched),
                      "mismatchedResponse": redact(mismatched), "unresolvedResponse": redact(unresolved),
                      "errorCodes": errors, "exactStructureFindings": True, "externalFixRefreshed": True,
                      "repeatedRequestEqual": True, "fixtureSourcesUnchanged": True}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    options = parser.parse_args()
    check(options.url, options.project_path.resolve())
