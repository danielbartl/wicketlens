#!/usr/bin/env python3
"""Check the local preview ZIP against current sources and separate verification receipts. Standard library only."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile


class CheckFailed(Exception):
    pass


def require(value, message):
    if not value:
        raise CheckFailed(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_hashes(root, paths):
    return {p.relative_to(root).as_posix(): digest(p.read_bytes()) for p in sorted(paths)}


def inputs(root):
    paths = set()
    for folder in ("src", "tools", "gradle/wrapper"):
        base = root / folder
        if base.exists():
            paths.update(p for p in base.rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    for p in (root / "examples").rglob("*") if (root / "examples").exists() else []:
        parts = p.relative_to(root / "examples").parts
        if p.is_file() and not {"target", "build", ".idea"}.intersection(parts) and ("src" in parts or p.name == "pom.xml"):
            paths.add(p)
    for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", "LICENSE"):
        p = root / name
        if p.is_file():
            paths.add(p)
    return file_hashes(root, paths)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(path)


def load(path):
    require(path.is_file(), f"Missing verification receipt: {path.name}. Run the documented Gradle checks.")
    return json.loads(path.read_text())


def records(root):
    return root / "build/reports/release-records"


def results_path(root, mode):
    return root / "build/test-results" / ("mcpHostTest" if mode == "host" else "test")


def test_results(root, mode, require_complete=False):
    paths = sorted(results_path(root, mode).glob("TEST-*.xml"))
    require(paths, f"No {mode} test results. Run the {mode} Gradle test command.")
    suites = [ET.parse(p).getroot() for p in paths]
    counts = {key: sum(int(r.get(key, "0")) for r in suites) for key in ("tests", "failures", "errors", "skipped")}
    require(counts["tests"] > 0 and not any(counts[k] for k in ("failures", "errors", "skipped")), f"{mode} suite has failed, skipped, or empty results: {counts}")
    names = {s.get("name") for s in suites}
    if mode == "host":
        complete = names == {"dev.wicketlens.WicketMcpHostTest"} and counts["tests"] >= 3
    else:
        compiled = root / "build/classes/kotlin/test"
        expected = {p.relative_to(compiled).with_suffix("").as_posix().replace("/", ".")
                    for p in compiled.rglob("*Test.class") if "$" not in p.name and p.stem != "WicketMcpHostTest"}
        complete = bool(expected and expected <= names) and any(t.get("name") == "testCorePluginLoadsWithMcpServerDisabled" for s in suites for t in s.findall("testcase"))
    if require_complete:
        require(complete, f"Incomplete {mode} suite; run its documented Gradle command without --tests filters.")
    return {"counts": counts, "complete": complete, "xmlHashes": file_hashes(root, paths)}


def record_start(root, mode):
    (records(root) / f"{mode}.json").unlink(missing_ok=True)
    write_json(records(root) / f"{mode}-started.json", {"inputs": inputs(root)})


def record_tests(root, mode):
    start = load(records(root) / f"{mode}-started.json")
    require(start["inputs"] == inputs(root), "Sources changed during verification; rerun the tests.")
    result = test_results(root, mode)
    write_json(records(root) / f"{mode}.json", {"inputs": start["inputs"], **result})


def artifact(root):
    paths = list((root / "build/distributions").glob("*.zip"))
    require(len(paths) == 1, "Expected one current plugin ZIP in build/distributions; remove obsolete ZIPs before verification.")
    path = paths[0]
    with zipfile.ZipFile(path) as outer:
        require(len(outer.namelist()) == len(set(outer.namelist())), "Duplicate entries in plugin ZIP.")
        jars = [n for n in outer.namelist() if n.endswith(".jar")]
        require(len(jars) == 1 and jars[0].startswith("wicketlens/lib/"), "Expected one WicketLens JAR; extra bundled libraries are not allowed.")
        with zipfile.ZipFile(io.BytesIO(outer.read(jars[0]))) as jar:
            require(len(jar.namelist()) == len(set(jar.namelist())), "Duplicate entries in plugin JAR.")
            require(jar.read("META-INF/LICENSE") == (root / "LICENSE").read_bytes(), "Packaged license differs from LICENSE.")
            optional = "META-INF/wicketlens-mcp.xml"
            require(jar.read(optional) == (root / "src/main/resources" / optional).read_bytes(), "Optional MCP descriptor differs from source.")
            xml = ET.fromstring(jar.read("META-INF/plugin.xml"))
            require(xml.findtext("id") == "dev.wicketlens", "Wrong plugin identity.")
            ide = xml.find("idea-version")
            require(xml.findtext("version") and ide is not None and ide.get("since-build"), "Missing plugin version or IDE build metadata.")
            dependency = next((d for d in xml.findall("depends") if d.text == "com.intellij.mcpServer"), None)
            require(dependency is not None and dependency.get("optional") == "true" and dependency.get("config-file") == "wicketlens-mcp.xml", "MCP dependency must remain optional.")
            classes = {}
            for base in (root / "build/classes/kotlin/main", root / "build/classes/java/main"):
                if base.exists():
                    classes.update({p.relative_to(base).as_posix(): p.read_bytes() for p in base.rglob("*.class")})
            shipped = {n for n in jar.namelist() if n.endswith(".class")}
            require(classes and set(classes) == shipped, "Packaged classes differ from the current production classes (or include test/framework classes).")
            require(all(jar.read(name) == data for name, data in classes.items()), "Packaged bytecode is stale; rebuild the ZIP.")
            require(all(name.startswith("dev/wicketlens/") for name in shipped), "Framework classes must not be bundled.")
            require(not any(n.endswith(".jar") for n in jar.namelist()), "Nested dependency JARs must not be bundled.")
            resources = root / "build/resources/main"
            for resource in resources.rglob("*") if resources.exists() else []:
                if resource.is_file():
                    require(jar.read(resource.relative_to(resources).as_posix()) == resource.read_bytes(), "Packaged resources are stale; rebuild the ZIP.")
            return {"file": path.relative_to(root).as_posix(), "sha256": digest(path.read_bytes()),
                    "version": xml.findtext("version"), "sinceBuild": ide.get("since-build"),
                    "productionClassCount": len(classes),
                    "classesSha256": digest(json.dumps({n: digest(b) for n, b in sorted(classes.items())}, sort_keys=True).encode())}


def verifier_result(root, package):
    folder = root / "build/reports/pluginVerifier" / f"IU-{package['sinceBuild']}" / "plugins/dev.wicketlens" / package["version"]
    verdict = folder / "verification-verdict.txt"
    require(verdict.is_file(), "No verifier result for this preview's target IDE/version.")
    value = verdict.read_text().strip()
    require(value.startswith("Compatible"), f"Plugin verification is not Compatible: {value}")
    return {"verdict": value, "reportHashes": file_hashes(root, folder.rglob("*.txt"))}


def record_verifier(root):
    package = artifact(root)
    write_json(records(root) / "verifier.json", {"artifactSha256": package["sha256"], **verifier_result(root, package)})


def setup_evidence(root, package):
    evidence = load(root / "docs/mcp-setup-evidence.json")
    require(evidence["gui"]["productionClassesSha256"] == package["classesSha256"], "Graphical/client evidence belongs to different production bytecode; repeat the walkthrough.")
    require(evidence["gui"]["verified"] and evidence["gui"]["ideBuild"] == "IU-" + package["sinceBuild"], "Missing graphical verification for the target IDE.")
    for name in ("codex", "claudeCode"):
        require(evidence["clients"][name]["passed"] and evidence["clients"][name]["version"], f"Missing successful {name} client verification.")
    for name, checksum in evidence["gui"]["screenshots"].items():
        path = root / name
        require(path.is_file() and digest(path.read_bytes()) == checksum, "Setup screenshots changed; update the reviewed evidence.")
    return {"graphicalSetup": "PASS", "clients": {name: evidence["clients"][name]["version"] for name in ("codex", "claudeCode")}}


def check(root):
    current = inputs(root)
    tests = {}
    for mode in ("normal", "host"):
        recorded = load(records(root) / f"{mode}.json")
        require(recorded["inputs"] == current, f"{mode} verification is stale; source, build, license, or tooling inputs changed.")
        latest = test_results(root, mode, require_complete=True)
        require(latest == {key: recorded[key] for key in ("counts", "complete", "xmlHashes")}, f"{mode} test reports changed after their successful run.")
        tests[mode] = latest["counts"]
    package = artifact(root)
    recorded = load(records(root) / "verifier.json")
    require(recorded["artifactSha256"] == package["sha256"], "The verifier receipt belongs to another ZIP; run verifyPlugin again.")
    require(verifier_result(root, package) == {key: recorded[key] for key in ("verdict", "reportHashes")}, "Verifier reports changed after verification.")
    return {"status": "verified_local_preview", "artifact": package, "tests": tests,
            "binaryCompatibility": recorded["verdict"], "setup": setup_evidence(root, package), "publicationPerformed": False,
            "limitations": ["Only the pinned IntelliJ/Wicket fixture combination is verified.",
                            "Native wire cancellation/session expiry remains an upstream limitation; see docs/mcp-isolation-transport.md.",
                            "Real-application accuracy/performance evaluation remains deferred.",
                            "This check does not publish, sign, tag, or establish Marketplace approval."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--record-start", choices=("normal", "host"))
    action.add_argument("--record-tests", choices=("normal", "host"))
    action.add_argument("--record-verifier", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.record_start:
            record_start(root, args.record_start)
        elif args.record_tests:
            record_tests(root, args.record_tests)
        elif args.record_verifier:
            record_verifier(root)
        else:
            report = check(root)
            if args.output:
                write_json(args.output, report)
            print(json.dumps(report, indent=2))
        return 0
    except (CheckFailed, OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile) as failure:
        print(f"Release check failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
