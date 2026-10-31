# Local preview verification and release checklist

The current artifact is a **local preview**, version `0.1.0-SNAPSHOT`.
This workflow prepares a reviewable ZIP and evidence. It does not publish,
sign, commit, push or create a release/tag. WicketLens is not on Marketplace yet.

The current run reports **verified_local_preview**: 1,644 normal tests, three
host tests and 20 release-checker tests pass. The artifact is Compatible, with
the existing four deprecated and six experimental API notices. Its exact
checksum and scope are recorded in [portable release evidence](mcp-release-evidence.json).

## Reproduce the checks

Use the documented Java 25 build toolchain, then run from the repository root:

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew test -PmcpHostTests=true
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
python3 tools/check_release.py --output build/reports/release-readiness.json
```

The first command verifies the release gate's success and failure paths. The
host mode runs the three actual-server tests in isolation. Normal `test` runs
the full suite with MCP disabled. Their reports are preserved separately:

- Normal: `build/test-results/test/`, `build/reports/tests/test/`.
- Host: `build/test-results/mcpHostTest/`, `build/reports/tests/mcpHostTest/`.

Gradle records source/build/tooling input fingerprints at test start and requires
the same inputs at successful completion. It records XML hashes, completeness
and counts separately for each mode. A focused `--tests` run still works, but
cannot replace the full normal suite at the release gate. Failed/interrupted
runs leave no valid replacement receipt. Receipts live under
`build/reports/release-records/` and are local generated artifacts.

The checker requires both complete suites with no failures, errors or skips,
including MCP-disabled foundation coverage. It also checks:

- The ZIP contains one plugin JAR, exactly the current production classes and
  processed resources, and no bundled framework/test/dependency classes/JARs.
- The packaged Apache license and optional MCP descriptor match their sources;
  the MCP dependency remains optional.
- The Compatible verifier receipt belongs to this exact ZIP hash and its reports
  are unchanged. Existing API notices remain visible in the verifier reports.
- Graphical/client evidence identifies the same production bytecode, target IDE
  and successful Codex/Claude calls; screenshot hashes match the reviewed files.

Missing, stale, partial or altered evidence fails the check. Do not hand-edit
receipts to silence a failure: rebuild/rerun the relevant commands. Input changes
during a test run require a retry. A production bytecode change also requires a
new graphical/client walkthrough and refreshed [setup evidence](mcp-setup-evidence.json).
SDK/Wicket/client upgrades require an explicit compatibility review even when
the bytecode itself is unchanged.

## Review the local preview

- [ ] Inspect `build/reports/release-readiness.json` and retain its artifact SHA-256.
- [ ] Review the full verifier reports and acknowledge the existing deprecated/
  experimental API notices; Compatible is not a claim about all IDE versions.
- [ ] Follow [installation](../README.md#installation) with the checked ZIP.
  Confirm ordinary sidebar/editor features, then use [MCP setup](mcp-client-setup.md)
  if desired. Installation/restart remains an explicit user action.
- [ ] Keep the exact verified IDE/Wicket/client matrix and the host transport
  limitation in the release notes. Other products/versions remain unverified.
- [ ] Include the read-only/static-analysis scope and the separately deferred
  real-application accuracy/performance evaluation.

These are per-release review items, not automatic approval to publish. The
graphical walkthrough for this slice used the Gradle-prepared sandbox plugin;
it did not claim a fresh normal-user-profile ZIP installation.

## Before publishing a version

- [ ] Choose the release version, update metadata/notes and rerun verification
  for the resulting ZIP. A SNAPSHOT archive is identified as a preview.
- [ ] Review the actual diff and public assets for unwanted project/account data.
  Screenshots must show only the portable demos/settings; evidence paths are redacted.
- [ ] Commit/tag and upload only when requested. For a GitHub preview, attach the
  checked ZIP and checksum. Marketplace publication/signing is separate work.
- [ ] Recheck native wire cancellation/session expiry on SDK upgrades; do not
  describe the current 30-second/reconnection fallback as prompt cancellation.

Application evaluation remains deferred by agreement. Its absence is recorded
explicitly rather than silently treated as completed release evidence.
