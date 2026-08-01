# Skill consumer compatibility challenge verdict

Date: 2026-08-01
Commit reviewed: `fd822578cb8c77902948d7efcf9d3a1a9716888c`
Reviewer: fresh Codex `gpt-5.6-sol ultra`, session
`019fbda5-e771-7a13-bc88-6ea84748f4fa`
Verdict: accept with changes.

## Availability and review mode

The project owner reported that Fable 5 quota was exhausted and directed the
work to continue without Fable. The permitted Codex Ultra fallback was started
in a separate Orca terminal with a read-only sandbox.

Direct repository reads failed because the Windows read-only sandbox could not
start PowerShell (`CreateProcessAsUserW failed: -1073283067`). No repository
mutation occurred. Following the architecture-challenge failure path, a fresh
ephemeral Codex Ultra session acted as a no-tools judge over repository and
reference facts that the primary agent had independently verified.

## Committed recommendation

Accept one canonical immutable Skill package plus feature-local web projections
of the two existing CLI install adapters, with these changes:

1. Label the selector **Install with...**, not **Use with...**.
2. Keep the CLI authoritative. A consumer descriptor may project only its
   display name, exact `--agent` value, default project-local directory, and
   deterministic-install support.
3. Generate the name, command, and target path from one descriptor so they
   cannot drift within the selected dialog.
4. Pin every command to `namespace/slug@version` and state that installation is
   project-local by default.
5. Use exactly three support terms:
   - **Verified package** for archive and file integrity;
   - **Install supported** for the deterministic CLI adapter;
   - **Runtime behavior not certified** for the unverified consumer outcome.
6. Once a consumer is selected, the handoff prompt names that consumer and
   exact command instead of asking the user to choose again.
7. Exhaustively test the two descriptors, CLI values, commands, and default
   paths to guard UI/CLI drift.

## Strongest counterargument

The CLI already owns the complete target contract. Web descriptors duplicate
that information and can become a second capability registry that drifts from
the CLI. Agent branding may also be understood as runtime compatibility even
when the UI includes a disclaimer.

The accepted design contains that risk by keeping descriptors feature-local,
limiting them to the two existing CLI values, testing the complete mapping, and
making runtime non-certification visible beside the action and inside the
dialog.

## Rejected alternative

Separate Claude Code and Codex package variants are rejected. The known
difference is the installation/discovery directory, which the current CLI
adapter already handles. Consumer-specific packages would split versions and
digests, complicate provenance, and imply semantic specialization that has not
been established.

The looser alternative of rendering commands directly without a descriptor is
also rejected. It saves little code while allowing label, target, directory,
and command generation to diverge independently.

## Scope limits

The verdict does not authorize server, API, MCP, CLI, persistence,
authorization, global-install, package-format, or runtime-execution changes. It
does not add consumers beyond Claude Code and Codex, certify content safety or
quality, introduce raw-URL installation, or create a second Skill catalog.

## Failure scenarios considered

- A Claude-specific Skill installs correctly into Codex but behaves poorly.
  Therefore installation support must not be labelled compatibility.
- A digest-perfect package contains harmful or defective instructions.
  Therefore package verification must not imply content review or safety.
- The CLI changes while the web mapping remains stale. Therefore the two
  descriptors and exact commands require exhaustive contract tests.

