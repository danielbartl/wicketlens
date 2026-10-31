#!/usr/bin/env python3
"""Check the isolated JetBrains host over real MCP HTTP; no third-party packages."""
import argparse
import json
from pathlib import Path
import urllib.parse
import urllib.request

TOOL = "wicketlens_mcp_probe"
EXPECTED = {
    "schemaVersion": 1,
    "plugin": "WicketLens",
    "tool": TOOL,
    "phase": 1,
    "status": "ok",
    "semanticAnalysis": False,
}


class McpClient:
    """Shared loopback-only test client for the connectivity and semantic checks."""
    def __init__(self, url):
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1", "::1"} or parsed.username or parsed.password:
            raise ValueError("Use the isolated host's loopback HTTP endpoint.")
        self.url = url
        self.headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
        self.next_id = 2
        initialized = self.request({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": "2025-06-18", "capabilities": {},
            "clientInfo": {"name": "wicketlens-spike-check", "version": "1"},
        }})
        self.protocol_version = initialized["protocolVersion"]
        self.headers["MCP-Protocol-Version"] = self.protocol_version
        self.request({"jsonrpc": "2.0", "method": "notifications/initialized"})

    def request(self, body):
        req = urllib.request.Request(self.url, json.dumps(body).encode(), self.headers)
        with urllib.request.urlopen(req, timeout=30) as response:
            session = response.headers.get("Mcp-Session-Id")
            if session:
                self.headers["Mcp-Session-Id"] = session
            if response.status == 202:
                return None
            if response.headers.get_content_type() == "application/json":
                message = json.load(response)
            else:
                message = None
                data = []
                for raw in response:
                    line = raw.decode().rstrip("\r\n")
                    if line.startswith("data:"):
                        data.append(line[5:].lstrip())
                    elif not line and data:
                        candidate = json.loads("\n".join(data))
                        data.clear()
                        if candidate.get("id") == body.get("id"):
                            message = candidate
                            break
                if message is None:
                    raise RuntimeError("No matching MCP response received.")
            if "error" in message:
                raise RuntimeError(message["error"])
            return message["result"]

    def rpc(self, method, params):
        identifier = self.next_id
        self.next_id += 1
        return self.request({"jsonrpc": "2.0", "id": identifier, "method": method, "params": params})

    def tools(self):
        return self.rpc("tools/list", {})["tools"]

    def call(self, name, arguments):
        return self.rpc("tools/call", {"name": name, "arguments": arguments})


def check(url, project_path):
    client = McpClient(url)
    tools = client.tools()
    names = {tool["name"] for tool in tools}
    if TOOL not in names or names - {TOOL, "execute_tool"}:
        raise AssertionError(f"Expected the isolated probe (and optional host router); found {sorted(names)}")
    descriptor = next(tool for tool in tools if tool["name"] == TOOL)
    expected_hints = {"readOnlyHint": True, "destructiveHint": False, "idempotentHint": True, "openWorldHint": False}
    if any(descriptor.get("annotations", {}).get(key) != value for key, value in expected_hints.items()):
        raise AssertionError("Missing or incorrect read-only tool hints.")
    result = client.call(TOOL, {"projectPath": str(Path(project_path).resolve())})
    if result.get("isError"):
        raise AssertionError(result)
    content = result.get("content", [])
    if len(content) != 1 or content[0].get("type") != "text" or json.loads(content[0]["text"]) != EXPECTED:
        raise AssertionError("Unexpected static probe response.")
    print(json.dumps({"protocolVersion": client.protocol_version, "tools": sorted(names), "annotations": expected_hints,
                      "response": EXPECTED}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:64343/stream")
    parser.add_argument("--project-path", default=str(Path(__file__).resolve().parents[1] / "build/mcp-spike/project"))
    options = parser.parse_args()
    check(options.url, options.project_path)
