---
name: commit
description: Create logical Git commits from current worktree changes in ClubStock. Use when asked to commit changes or draft commit messages, including "$commit" and "$commit with scope".
---

# Commit

Use [Conventional Commits 1.0.0-beta.2](https://www.conventionalcommits.org/en/v1.0.0-beta.2/#specification) as a guide. The explicit project rules below take precedence, including over conflicting SE-EDU commit-message formatting. Existing branch conventions remain applicable.

## Invocation

- `$commit`: commit current worktree changes using `type: description`. Never add scopes automatically.
- `$commit with scope`: use `type(scope): description`. Choose a meaningful scope per commit from the affected component, such as `loans`, `inventory`, or `auth`.
- Use scopes only when explicitly requested. Honor explicitly supplied scopes and restrictions on which changes to commit.
- When asked only to draft a message, do not stage or commit.

## Workflow

1. Inspect status, staged and unstaged diffs, untracked files, and recent history. Read applicable repository guidance. By default, consider all current worktree changes, not just changes made by this agent. Respect ignore rules; do not force-add ignored files.
2. Automatically group changes into coherent commits by purpose. Keep related implementation, tests, and documentation together. Do not split merely by file type or directory. Ask only when a material ambiguity prevents selecting the intended contents safely.
3. Stage each group using explicit paths or hunks. Preserve file contents and changes outside an explicitly narrowed request. Existing staging does not determine logical grouping: rearrange it as needed without discarding underlying worktree changes, and preserve out-of-scope staging.
4. Review the complete staged diff before each commit and ensure it contains only that group. Use relevant validation results without claiming checks that were not run. If a commit or hook fails, inspect the cause; do not bypass hooks or repeatedly retry unchanged failures.
5. Create the commits, then inspect the resulting history and status. Report commit hashes, subjects, and any remaining changes or blockers. If no changes exist, report that without creating an empty commit.

Do not push, amend, rebase, or otherwise rewrite history unless requested. Follow repository user-identification and prompt-log instructions. Record the prompt before final staging so its log update can be included when within the requested scope.

## Messages

- Select an ordinary lowercase type automatically: `feat` for new functionality, `fix` for bug fixes, `docs` for documentation, `refactor` for restructuring without behavior changes, `test` for tests, `style` for formatting, `perf` for performance, `build` for build changes, `ci` for CI, or `chore` for maintenance.
- Follow the prefix with a colon, a space, and a concise imperative description. Do not end the subject with a period. Omit scope unless explicitly requested.
- Bodies and footers are optional, separated from the subject and each other by a blank line. Add a body when the reason or impact needs explanation. No mandatory SE-EDU body structure or length limits apply.
- Special markers such as `BREAKING CHANGE:` require an explicit user request. Never infer or automatically add breaking-change markers, including `!` in a subject. Ordinary types such as `feat` and `fix` are still selected automatically.
- When explicitly requested, place `BREAKING CHANGE: ` at the beginning of a body or footer section and describe the incompatible change, following beta.2. Keep footer content to metadata such as issue references and breaking-change information; do not invent issue references.

Examples (illustrative, not claims about existing changes):

```text
feat: add equipment loan requests
fix: prevent duplicate return confirmations
docs: clarify Exco setup requirements
```

Only with an explicit scope request:

```text
fix(inventory): prevent negative stock counts
```

Only with an explicit breaking-change request:

```text
feat: revise loan export fields

BREAKING CHANGE: loan exports use equipmentId instead of equipmentName
```
