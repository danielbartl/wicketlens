#!/usr/bin/env python3
"""Run the demo, prove a title regression is caught in a disposable copy, then restore it."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

SCENARIOS = {
    "spring-orders": ("OrdersPage.java", "orders.title()", '"Regression: title ignores service"', "Regression: title ignores service"),
    "spring-details": ("OrderDetailsPage.java", "prices.priceFor(orderId)", '"Regression: price ignores order"', "Regression: price ignores order"),
    "plain": ("HelloPage.java", '"Hello, "', '"Goodbye, "', "Goodbye, "),
}


def inputs(demo):
    return {path.relative_to(demo).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted([demo / "pom.xml", *(p for p in (demo / "src").rglob("*") if p.is_file())])}


def reports(demo):
    files = sorted((demo / "target/surefire-reports").glob("TEST-*.xml"))
    if not files:
        raise RuntimeError("Maven produced no fresh Surefire reports.")
    suites = [ET.parse(path).getroot() for path in files]
    counts = {key: sum(int(suite.get(key, 0)) for suite in suites)
              for key in ("tests", "failures", "errors", "skipped")}
    failures = [failure for suite in suites for failure in suite.iter("failure")]
    return counts, failures


def verify(demo, maven, offline=False, timeout=300, scenario="spring-orders"):
    filename, expression, replacement, marker = SCENARIOS[scenario]
    relative = Path("src/main/java/example") / filename
    page = demo / relative
    original = page.read_bytes()
    text = original.decode("utf-8")
    if text.count(expression) != 1:
        raise RuntimeError(f"The {scenario} check requires exactly one {expression} rendering expression.")
    before = inputs(demo)
    result = {"scope": "Portable Wicket demo only; not a production-application evaluation",
              "scenario": scenario, "status": "FAIL", "mcpUsedByVerifier": False}
    with tempfile.TemporaryDirectory(prefix="wicketlens-agent-test-") as temporary:
        work = Path(temporary)
        try:
            def run(folder, name):
                command = [maven, "-B", *(["-o"] if offline else []), "-f", str(folder / "pom.xml"), "clean", "test"]
                with (work / f"{name}.log").open("w") as log:
                    process = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
                counts, failures = reports(folder)
                return process.returncode, counts, failures

            def passing(folder, name):
                code, counts, _ = run(folder, name)
                if code != 0 or counts["tests"] == 0 or any(counts[k] for k in ("failures", "errors", "skipped")):
                    raise RuntimeError(f"{name}: expected passing tests with no errors or skips.")
                result[name] = counts

            passing(demo, "baseline")
            copy = work / "demo"
            copy.mkdir()
            shutil.copy2(demo / "pom.xml", copy / "pom.xml")
            shutil.copytree(demo / "src", copy / "src")
            (copy / relative).write_text(text.replace(expression, replacement), encoding="utf-8")
            code, counts, failures = run(copy, "regression")
            if code == 0 or counts["failures"] == 0 or counts["errors"] or counts["skipped"] or not any(
                "Assertion" in failure.get("type", "") and marker in (failure.get("message", "") + "".join(failure.itertext()))
                for failure in failures
            ):
                raise RuntimeError("The negative control must fail on the mutated behavior assertion, not a build/setup error.")
            if counts["tests"] != result["baseline"]["tests"]:
                raise RuntimeError("The negative control must run the same number of tests as the baseline.")
            result["regression"] = {**counts, "expectedFailure": True, "behaviorAssertionFailed": True}
            (copy / relative).write_bytes(original)
            passing(copy, "restored")
            if result["restored"]["tests"] != result["baseline"]["tests"]:
                raise RuntimeError("Restoration must run the same number of tests as the baseline.")
            if before != inputs(demo):
                raise RuntimeError("Authored demo inputs changed during verification.")
            result.update(status="PASS", authoredInputsUnchanged=True, mutationConfinedToDisposableCopy=True)
            return result
        finally:
            result["authoredInputsUnchanged"] = before == inputs(demo)
            output = demo / "target/agent-workflow"
            output.mkdir(parents=True, exist_ok=True)
            for log in work.glob("*.log"):
                shutil.copy2(log, output / log.name)
            (output / "verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven", default=shutil.which("mvn"), help="Maven executable; defaults to mvn on PATH")
    parser.add_argument("--offline", action="store_true", help="Use Maven's existing local dependency cache")
    parser.add_argument("--timeout", type=int, default=300, help="Timeout in seconds for each Maven run")
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parent, help="Portable demo directory")
    parser.add_argument("--scenario", choices=SCENARIOS, default="spring-orders")
    args = parser.parse_args()
    if not args.maven or args.timeout <= 0:
        parser.error("Maven on PATH and a positive timeout are required.")
    print(json.dumps(verify(args.project.resolve(), args.maven, args.offline, args.timeout, args.scenario), indent=2))
