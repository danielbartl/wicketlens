# Local preview verification and release checklist

The current release candidate is [**0.1.0-rc.1**](https://github.com/danielbartl/wicketlens/releases/tag/v0.1.0-rc.1).
Earlier previews remain available in GitHub Releases.
The candidate freezes the existing feature scope; see
[RC 1 notes](releases/0.1.0-rc.1.md). Publication is a separate requested action.
WicketLens is not on Marketplace yet.

RC 1 passes **2,317 normal tests**, **eight actual MCP host tests**, and
**20 release-checker tests**. Packaging/project/structure checks pass. Binary
verification is **Compatible**, with five deprecated and six experimental API notices. The synchronous
freshness guard adds one ReadAction.run deprecation to the previous four. The exact final ZIP JAR matches the repeated native
Preview 2 upgrade and the fresh isolated installation. Graphical checks cover
nine exposed tools, Codex CLI 0.166.0 and Claude Code 2.1.317 route calls, native
route navigation without implicit read access, editor following, class-root HTML
navigation, and a clean MCP-disabled Spring dependency view/plain-test preview.
The candidate release provides the verified ZIP and SHA256SUMS.txt. Publication
is a separate action; these checks themselves do not publish anything. Prior published evidence remains at the Preview 2 tag.
Current fingerprints and scope are in [release evidence](mcp-release-evidence.json)
and [setup evidence](mcp-setup-evidence.json).

Repeat the applicable checks for every changed artifact. Source/build input
changes require new complete-suite receipts; changed production bytecode requires
a fresh graphical/client walkthrough. These records certify only their exact
artifact and verified environment, not all IDE/Wicket versions.

## Reproduce the checks

Use the documented Java 25 build toolchain, then run from the repository root:

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew test -PmcpHostTests=true
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
python3 tools/check_release.py --output build/reports/release-readiness.json
```

The first command verifies the release gate's success and failure paths. The
host mode runs the eight actual-server tests in isolation. Normal `test` runs
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

- [x] Inspect `build/reports/release-readiness.json` and retain its artifact SHA-256.
- [x] Review the full verifier reports and acknowledge the existing deprecated/
  experimental API notices; Compatible is not a claim about all IDE versions.
- [x] Follow [installation](../README.md#installation) with the checked ZIP.
  Confirm ordinary sidebar/editor features, then use [MCP setup](mcp-client-setup.md)
  if desired. Use an isolated validation profile for automated installation/upgrade checks;
  normal user-profile installation remains a separate action.
- [x] Keep the exact verified IDE/Wicket/client matrix and the host transport
  limitation in the release notes. Other products/versions remain unverified.
- [x] Include the read-only/static-analysis scope and the separately deferred
  real-application accuracy/performance evaluation.

These are per-release review items. Record which checks used a Gradle sandbox,
an isolated installation profile, or a normal user profile; do not substitute
one claim for another. Keep the final feedback round focused on fixes.

## Before publishing a version

- [x] Choose the release version, update metadata/notes and rerun verification
  for the resulting ZIP. A SNAPSHOT archive is identified as a preview.
- [x] Review the actual diff and public assets for unwanted project/account data.
  Screenshots must show only the portable demos/settings; evidence paths are redacted.
- [ ] Commit/tag and upload only when requested. For a GitHub preview, attach the
  checked ZIP and checksum. Marketplace publication/signing is separate work.
- [ ] Recheck native wire cancellation/session expiry on SDK upgrades; do not
  describe the current 30-second/reconnection fallback as prompt cancellation.

Application evaluation remains deferred by agreement. Its absence is recorded
explicitly rather than silently treated as completed release evidence.

## RC 1 stabilization checks

- [x] Final versioned ZIP: complete normal/host receipts, release-checker tests,
  project/structure checks and Compatible binary verdict.
- [x] Install the published Preview 2 ZIP in an isolated profile, then upgrade
  that profile to RC 1 and verify the loaded version.
- [x] Install RC 1 in a separate clean profile.
- [x] Smoke-test Follow editor, navigation, missing-pair feedback, paired rename
  with Undo, Spring views, test previews and Mounted URLs.
- [x] Refresh native MCP settings/tool-discovery screenshots and real Codex/Claude
  calls for the final production bytecode.
- [x] Source-only smoke-check five external application pages: partial bounded
  results, no accuracy or representative performance claim; full study deferred.
- [x] Review public assets/diff, candidate notes, downloads and landing-page copy.
- [ ] Publish the candidate ZIP and checksum when authorized, then collect
  feedback and fix release issues before 0.1.0.
