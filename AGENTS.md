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
- If the documents conflict, flag the discrepancy before implementing affected behavior.

## Commit Conventions

- Use the project-specific [commit skill](.agents/skills/commit/SKILL.md) when creating commits or drafting commit messages. Its message rules take precedence over conflicting SE-EDU commit-message formatting; existing branch conventions remain applicable.
- `$commit` uses `type: description` and automatically groups worktree changes into logical commits. Use `type(scope): description` only when scopes are explicitly requested, such as `$commit with scope`.
- Add special markers such as `BREAKING CHANGE:` only when explicitly requested.

## Post-Prompt Checklist

- After handling each user prompt, append a concise summary of that prompt to the identified user's log:
  - Darryl: `logs/darryl-log-temp.md`.
  - Keith: `logs/keith-log-temp.md`.
- Create the applicable log file if it does not exist, and preserve existing entries.
- Summarize the user's request accurately without inventing contributions or personal experiences.
- If the user's identity is unknown, ask before attributing the prompt to either student. Do not write another stakeholder's prompts to either student's log.
