"""Negative checks for the release evidence gate. Run: python3 -m unittest discover -s tools -p 'test_*.py'."""
from pathlib import Path
import io
import json
import tempfile
import unittest
import zipfile

import check_release as release


class ReleaseCheckTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.put("LICENSE", "Apache License 2.0\n")
        self.put("build.gradle.kts", "// fixture build configuration\n")
        self.put("src/main/kotlin/Dummy.kt", "class Dummy\n")
        self.put("src/main/resources/META-INF/wicketlens-mcp.xml", "<idea-plugin/>\n")
        self.put("build/classes/kotlin/main/dev/wicketlens/Dummy.class", b"fixture bytecode")
        self.put("build/classes/kotlin/test/dev/wicketlens/PluginFoundationTest.class", b"fixture test")
        self.put("build/classes/kotlin/test/dev/wicketlens/WicketMcpHostTest.class", b"fixture host test")
        self.normal_xml = self.put("build/test-results/test/TEST-foundation.xml", '<testsuite name="dev.wicketlens.PluginFoundationTest" tests="1" failures="0" errors="0" skipped="0"><testcase name="testCorePluginLoadsWithMcpServerDisabled"/></testsuite>')
        self.host_xml = self.put("build/test-results/mcpHostTest/TEST-host.xml", '<testsuite name="dev.wicketlens.WicketMcpHostTest" tests="3" failures="0" errors="0" skipped="0"><testcase name="a"/><testcase name="b"/><testcase name="c"/></testsuite>')
        self.verdict = self.put("build/reports/pluginVerifier/IU-262.10968.63/plugins/dev.wicketlens/0.1.0-SNAPSHOT/verification-verdict.txt", "Compatible\n")
        self.zip_path = self.root / "build/distributions/wicketlens.zip"
        self.zip_path.parent.mkdir(parents=True)
        self.entries = {
            "META-INF/LICENSE": (self.root / "LICENSE").read_bytes(),
            "META-INF/wicketlens-mcp.xml": (self.root / "src/main/resources/META-INF/wicketlens-mcp.xml").read_bytes(),
            "META-INF/plugin.xml": b'<idea-plugin><id>dev.wicketlens</id><version>0.1.0-SNAPSHOT</version><idea-version since-build="262.10968.63"/><depends optional="true" config-file="wicketlens-mcp.xml">com.intellij.mcpServer</depends></idea-plugin>',
            "dev/wicketlens/Dummy.class": b"fixture bytecode",
        }
        self.package()
        self.put("docs/mcp-setup-evidence.json", json.dumps({"gui": {"verified": True, "ideBuild": "IU-262.10968.63", "productionClassesSha256": release.artifact(self.root)["classesSha256"], "screenshots": {}}, "clients": {name: {"passed": True, "version": "fixture"} for name in ("codex", "claudeCode")}}))
        for mode in ("normal", "host"):
            release.record_start(self.root, mode)
            release.record_tests(self.root, mode)
        release.record_verifier(self.root)

    def put(self, name, value):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(value if isinstance(value, bytes) else value.encode())
        return path

    def package(self, extra_jar=False):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as inner:
            for name, value in self.entries.items():
                inner.writestr(name, value)
        with zipfile.ZipFile(self.zip_path, "w") as outer:
            outer.writestr("wicketlens/lib/wicketlens.jar", data.getvalue())
            if extra_jar:
                outer.writestr("wicketlens/lib/framework.jar", b"unexpected")

    def testIndependentSuccessfulReceiptsPermitALocalPreview(self):
        result = release.check(self.root)
        self.assertEqual("verified_local_preview", result["status"])
        self.assertEqual(1, result["tests"]["normal"]["tests"])
        self.assertEqual(3, result["tests"]["host"]["tests"])
        self.assertFalse(result["publicationPerformed"])

    def testChangedSourceInvalidatesBothReceipts(self):
        self.put("src/main/kotlin/Dummy.kt", "class Changed\n")
        with self.assertRaisesRegex(release.CheckFailed, "stale"):
            release.check(self.root)

    def testNewSourceCannotHideBehindAnOldReceipt(self):
        self.put("src/main/kotlin/New.kt", "class New\n")
        with self.assertRaisesRegex(release.CheckFailed, "stale"):
            release.check(self.root)

    def testDeletedSourceInvalidatesReceipts(self):
        (self.root / "src/main/kotlin/Dummy.kt").unlink()
        with self.assertRaisesRegex(release.CheckFailed, "stale"):
            release.check(self.root)

    def testChangingInputsDuringTestsCannotProduceAReceipt(self):
        release.record_start(self.root, "normal")
        self.put("src/main/kotlin/Dummy.kt", "class Changed\n")
        with self.assertRaisesRegex(release.CheckFailed, "during verification"):
            release.record_tests(self.root, "normal")
        self.assertFalse((release.records(self.root) / "normal.json").exists())

    def testEditingTestResultsAfterRecordingIsRejected(self):
        self.normal_xml.write_text(self.normal_xml.read_text() + "\n")
        with self.assertRaisesRegex(release.CheckFailed, "reports changed"):
            release.check(self.root)

    def testSkippedTestsCannotBeRecordedAsPassing(self):
        self.host_xml.write_text(self.host_xml.read_text().replace('skipped="0"', 'skipped="1"'))
        with self.assertRaises(release.CheckFailed):
            release.record_tests(self.root, "host")

    def testFocusedRunsWorkButCannotPassTheFullReleaseGate(self):
        self.put("build/classes/kotlin/test/dev/wicketlens/OtherTest.class", b"another test")
        release.record_start(self.root, "normal")
        release.record_tests(self.root, "normal")
        with self.assertRaisesRegex(release.CheckFailed, "Incomplete normal"):
            release.check(self.root)

    def testMissingHostReceiptIsRejected(self):
        (release.records(self.root) / "host.json").unlink()
        with self.assertRaisesRegex(release.CheckFailed, "Missing verification receipt"):
            release.check(self.root)

    def testBundledDependencyJarIsRejected(self):
        self.package(extra_jar=True)
        with self.assertRaisesRegex(release.CheckFailed, "one WicketLens JAR"):
            release.artifact(self.root)

    def testUnexpectedFrameworkOrTestClassesAreRejected(self):
        self.entries["org/framework/Server.class"] = b"unexpected"
        self.package()
        with self.assertRaisesRegex(release.CheckFailed, "classes differ"):
            release.artifact(self.root)

    def testMissingPackagedLicenseIsRejected(self):
        del self.entries["META-INF/LICENSE"]
        self.package()
        with self.assertRaises(KeyError):
            release.artifact(self.root)

    def testMcpDependencyMustStayOptional(self):
        self.entries["META-INF/plugin.xml"] = self.entries["META-INF/plugin.xml"].replace(b'optional="true"', b'optional="false"')
        self.package()
        with self.assertRaisesRegex(release.CheckFailed, "remain optional"):
            release.artifact(self.root)

    def testStalePackagedBytecodeIsRejected(self):
        self.put("build/classes/kotlin/main/dev/wicketlens/Dummy.class", b"changed")
        with self.assertRaisesRegex(release.CheckFailed, "bytecode is stale"):
            release.artifact(self.root)

    def testStaleResourcesAreRejected(self):
        self.put("build/resources/main/icons/sample.svg", "changed resource")
        self.entries["icons/sample.svg"] = b"old resource"
        self.package()
        with self.assertRaisesRegex(release.CheckFailed, "resources are stale"):
            release.artifact(self.root)

    def testVerifierReceiptCannotBeReusedForAnotherZip(self):
        self.entries["META-INF/extra.txt"] = b"changed archive"
        self.package()
        with self.assertRaisesRegex(release.CheckFailed, "another ZIP"):
            release.check(self.root)

    def testChangedVerifierReportsAreRejected(self):
        self.verdict.write_text("Compatible\n\n")
        with self.assertRaisesRegex(release.CheckFailed, "reports changed"):
            release.check(self.root)

    def testIncompatibleVerdictIsRejected(self):
        self.verdict.write_text("Incompatible\n")
        with self.assertRaisesRegex(release.CheckFailed, "not Compatible"):
            release.check(self.root)

    def testStaleGraphicalEvidenceIsRejected(self):
        path = self.root / "docs/mcp-setup-evidence.json"
        value = json.loads(path.read_text())
        value["gui"]["productionClassesSha256"] = "old bytecode"
        path.write_text(json.dumps(value))
        with self.assertRaisesRegex(release.CheckFailed, "different production bytecode"):
            release.check(self.root)

    def testMissingSuccessfulSecondClientIsRejected(self):
        path = self.root / "docs/mcp-setup-evidence.json"
        value = json.loads(path.read_text())
        value["clients"]["claudeCode"]["passed"] = False
        path.write_text(json.dumps(value))
        with self.assertRaisesRegex(release.CheckFailed, "claudeCode"):
            release.check(self.root)


if __name__ == "__main__":
    unittest.main()
