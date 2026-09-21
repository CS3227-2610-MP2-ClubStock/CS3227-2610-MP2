# Testing `$plan-to-docs`

The test harness separates deterministic package checks from model-backed
behavior checks.

## Static Checks

Run:

```shell
.agents/skills/plan-to-docs/tests/run-tests.sh
```

This validates the skill frontmatter, explicit-only policy, UI metadata,
linked output reference, stable traceability markers, and text formatting. It
does not invoke Codex or use tokens.

## Disposable End-to-End Checks

Run:

```shell
.agents/skills/plan-to-docs/tests/run-tests.sh --e2e
```

The opt-in test invokes Codex and therefore uses model tokens. It constructs
independent Git repositories beneath a `mktemp` directory and places a fake
`gh` executable first on `PATH`. The fake records GitHub calls and stores issue
state locally; it cannot create real GitHub issues.

The test verifies that:

- The explicit invocation creates exactly one new Markdown file under
  `docs/plans/` in the controlled fixture.
- The document contains component details and stable traceability markers.
- The issue preview names the target repository and behavior slices.
- No issue write occurs before explicit approval.
- Resuming the same session with approval creates the previewed fake issues and
  writes their URLs into the document.
- A rerun detects those issues and does not create duplicates.
- Failed GitHub authentication retains the document and reports the blocker.

Failed runs retain their sandbox and print its path. Use `--keep-sandbox` to
retain a successful sandbox for inspection. Test-created Codex sessions are
deleted during cleanup when possible.

## Manual Invocation Check

Runtime selection is best checked through the Codex UI because similar output
does not prove that a skill was loaded.

1. Start a fresh session in this repository and ask to translate a plan into
   documentation and issues without naming `$plan-to-docs`. Confirm that the
   skill activation indicator is absent.
2. Start another fresh session with the same request but explicitly include
   `$plan-to-docs`. Confirm that the skill activation indicator is present.

The repository's real `AGENTS.md` requires substantive runs to update Keith's
prompt log. The disposable fixture intentionally removes that requirement so
its containment assertion can require only one generated design document.
