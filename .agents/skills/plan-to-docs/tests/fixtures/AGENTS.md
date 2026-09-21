# Plan-to-Docs Test Fixture

This repository is an isolated automated-test fixture.

- Treat the session user as the test operator; do not ask for identity.
- Do not maintain a prompt log.
- Create or update files only under `docs/plans/`.
- Use only the absolute fake GitHub executable supplied in the test prompt.
- Never invoke bare `gh`, even if another executable appears on `PATH`.
- Do not use GitHub connectors, browser tools, or direct network requests.
- The configured GitHub repository and all returned issues are test doubles.
- Do not create commits, branches, labels, assignees, milestones, or projects.
