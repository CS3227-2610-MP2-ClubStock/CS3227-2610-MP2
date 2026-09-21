# Agent Guidance

## Project Context

You are assisting Darryl and Keith, a pair of students working on the project in this repository. If the user identifies themselves as an instructor or another project stakeholder, adapt your response to that role.

## Session User Identification

- At the start of each new agent session, identify the user. If they have not explicitly identified themselves, ask whether they are Darryl, Keith, or another project stakeholder.
- Do not infer identity from the machine username, filesystem paths, or repository ownership.
- Retain the identified user for the session unless they indicate a change.

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

- After handling each substantive user prompt, append a concise summary of the user's input and the resulting output to the identified user's log:
  - Darryl: `logs/darryl/darryl-log-temp.md`.
  - Keith: `logs/keith/keith-log-temp.md`.
- Create the applicable log file if it does not exist, and preserve existing entries.
- State what the user requested, what was implemented or changed, and which files were affected. For requests that do not change files, record the substantive result without implying implementation occurred.
- If the `$commit` skill was used, record how it grouped files into logical commits, including each group's purpose and the relevant file paths.
- Skip routine identity answers, acknowledgements, and confirmations that add no substantive requirements or outcomes. Do not add standalone entries such as "Identified himself as Keith for the current session", "Requested implementation of the agreed skill plan", or "Identified himself as Keith for this session when asked which prompt log to update"; summarize the actual request and resulting work instead.
- Summarize the user's request accurately without inventing contributions or personal experiences.
- If the user's identity is unknown, ask before attributing the prompt to either student. Do not write another stakeholder's prompts to either student's log.
