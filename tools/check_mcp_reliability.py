#!/usr/bin/env python3
"""Exercise simultaneous semantic requests through the actual JetBrains host."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
from threading import Barrier
from check_mcp_spike import McpClient

TOOLS = ("wicket_get_page_structure", "wicket_validate_page", "wicket_get_component_context", "wicket_get_injected_dependencies", "wicket_get_spring_inspections", "wicket_get_test_plan")
MODULES = ("mcp-fixture", "mcp-other")


def check(url, project):
    # Project infrastructure/indexes belong to the host; these are the authored fixture inputs.
    files = sorted(p for p in project.rglob("*") if p.suffix in {".java", ".html", ".iml"})
    before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    baseline_client = McpClient(url)
    names = {tool["name"] for tool in baseline_client.tools()}
    assert set(TOOLS) | {"wicketlens_mcp_probe", "wicket_get_spring_test_plan"} <= names

    def call(client, tool, module, bad=False):
        args = {"projectPath": str(project), "className": "example.Missing" if bad else "example.McpPage", "moduleName": module}
        if tool == "wicket_get_component_context":
            args["componentPath"] = "title" if module == "mcp-fixture" else "other"
        result = client.call(tool, args)
        assert len(result["content"]) == 1 and result["content"][0]["type"] == "text"
        value = json.loads(result["content"][0]["text"])
        assert value["tool"] == tool and value["schemaVersion"] == 1
        if bad:
            assert result.get("isError") is True and value["status"] == "error"
            assert value["error"]["code"] == "CLASS_NOT_FOUND"
        else:
            assert not result.get("isError") and value["status"] == "ok" and value["truncated"] is False
            assert value["target"]["moduleName"] == module
        return value

    cases = [(tool, module) for tool in TOOLS for module in MODULES] + [("wicket_get_spring_test_plan", "mcp-spring")]
    baseline = {(tool, module): call(baseline_client, tool, module) for tool, module in cases}
    for module in MODULES:
        structure = baseline[(TOOLS[0], module)]["analysis"]
        assert {node["wicketId"] for node in structure["components"] if node["wicketId"]} == (
            {"title", "form", "name"} if module == "mcp-fixture" else {"other"})
        assert structure["matching"]["findings"] == baseline[(TOOLS[1], module)]["validation"]["findings"]
        node = baseline[(TOOLS[2], module)]["context"]["component"]
        assert node in structure["components"]

    sessions = len(cases)
    barrier = Barrier(sessions)

    def worker(tool, module):
        # Each independent MCP session owns its transport headers and request IDs.
        client = McpClient(url)
        barrier.wait(timeout=30)
        call(client, tool, module, bad=True)
        for _ in range(3):
            assert call(client, tool, module) == baseline[(tool, module)]
        return {"tool": tool, "module": module, "successfulRequests": 3, "expectedErrors": 1}

    with ThreadPoolExecutor(max_workers=sessions) as pool:
        futures = [pool.submit(worker, tool, module) for tool, module in cases]
        workers = [future.result(timeout=120) for future in futures]
    for key, value in baseline.items():
        assert call(baseline_client, *key) == value
    assert before == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    assert files == sorted(p for p in project.rglob("*") if p.suffix in {".java", ".html", ".iml"})
    print(json.dumps({"protocolVersion": baseline_client.protocol_version, "concurrentSessions": sessions,
                      "semanticRequests": sessions * 6, "expectedErrors": sessions, "workers": workers,
                      "allResponsesEqualBaseline": True, "crossModuleIsolation": True,
                      "existingSessionUsableAfterConcurrentRequests": True, "fixtureInputsUnchanged": True,
                      "scope": "Bounded fixture concurrency check, not an application performance evaluation",
                      "acceptance": "PASS"}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", type=Path, default=Path(__file__).resolve().parents[1] / "build/mcp-structure/project")
    options = parser.parse_args()
    check(options.url, options.project_path.resolve())
