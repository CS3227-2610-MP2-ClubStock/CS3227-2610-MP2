# Agent Guidance

## Project Context

You are assisting Darryl and Keith, a pair of students working on the project in this repository. If the user identifies themselves as an instructor or another project stakeholder, adapt your response to that role.

## Session User Attribution

- Do not ask for the user's identity merely because a new agent session has started. Identity
  discovery must never block or pause the requested work, including autonomous reviews, cloud
  workflows, CI-style tasks, or other non-interactive runs.
- Use an identity only when the user explicitly identifies themselves in the current conversation
  or the available conversation context already contains that identification.
- Do not infer identity from the machine username, filesystem paths, repository ownership,
  branch names, commit metadata, or the nature of the requested work.
- Retain an explicitly identified user for the conversation unless they indicate a change.
- If the user's identity is unknown, complete the task without asking solely for log attribution.
  Do not write the prompt to either student's log. If the user identifies themselves later,
  begin logging from that point rather than retroactively attributing earlier prompts.

## Java and JavaFX

- Apple Silicon Mac users: use [JDK 25 FX Zulu](https://www.azul.com/downloads/?version=java-25-lts&package=jdk#zulu).
- All other platforms: use [JDK 25](https://www.oracle.com/java/technologies/downloads/#jdk25-mac).
- Use JavaFX 25.0.3, or the JavaFX version bundled with the JDK 25 FX Zulu installation for Apple Silicon Mac users.

## Project References

- Read `docs/ProjectDescription.md` for the project overview.
- Read `docs/ProjectRequirements.md` for the agreed functional and non-functional requirements.
- Read `docs/Shared.md` for domain concepts, state rules, and cross-role behaviour shared by both implementations.
- Darryl is implementing the Exco role and must also read `docs/ExcoSpec.md`.
- Keith is implementing the Member role and must also read `docs/MemberSpec.md`.
- If any project documents conflict, flag the discrepancy before implementing affected behavior; do not silently choose which specification takes precedence.

## Commit Conventions

- Use the project-specific [commit skill](.agents/skills/commit/SKILL.md) when creating commits or drafting commit messages. Its message rules take precedence over conflicting SE-EDU commit-message formatting; existing branch conventions remain applicable.
- `$commit` uses `type: description` and automatically groups worktree changes into logical commits. Use `type(scope): description` only when scopes are explicitly requested, such as `$commit with scope`.
- Add special markers such as `BREAKING CHANGE:` only when explicitly requested.

## Post-Prompt Checklist

- After handling each substantive user prompt, append a concise summary of the user's input and
  the resulting output to the explicitly identified user's log:
  - Darryl: `logs/darryl/darryl-log-temp.md`.
  - Keith: `logs/keith/keith-log-temp.md`.
- Use one Markdown section for each new agent session, following this log style:

  ```markdown
  ## YYYY-MM-DD — Concise session title

  - Summary of the substantive request and outcome.
  ```

- Create the session heading when writing the first substantive entry for that session. Use the
  repository's local date and a short title describing the session's main objective. Append later
  substantive prompt summaries from the same session as bullets beneath that heading; do not add
  another heading for every prompt. Sessions on the same date still receive separate headings.
- Create the applicable log file if it does not exist, and use the same `##` section format without
  requiring a separate top-level title. Preserve existing entries and do not reformat historical
  content merely to match the new convention.
- State what the user requested, what was implemented or changed, and which files were affected.
  For requests that do not change files, record the substantive result without implying
  implementation occurred.
- If the `$commit` skill was used, record how it grouped files into logical commits, including each
  group's purpose and the relevant file paths.
- Skip routine identity answers, acknowledgements, and confirmations that add no substantive
  requirements or outcomes. Do not add standalone identity entries; summarize the actual request
  and resulting work instead.
- Summarize the user's request accurately without inventing contributions or personal experiences.
- If the user's identity is unknown or they are another stakeholder, do not write the prompt to
  either student's log and do not interrupt the task to ask. Honor an explicit request not to log a
  prompt or session.
