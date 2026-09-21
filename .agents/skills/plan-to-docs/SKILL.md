---
name: plan-to-docs
description: Convert a provided or finalized software plan into repository-grounded engineering documentation and traceable GitHub implementation issues. Use when the user explicitly invokes $plan-to-docs; do not use for ordinary planning, documentation edits, or issue triage.
---

# Plan to Docs

Turn an agreed software plan into one durable engineering design document and
a set of implementation-sized GitHub issues. Keep the document useful even
when GitHub is unavailable, and preserve traceability across repeated runs.

Read [references/output-format.md](references/output-format.md) before drafting
the document or issue bodies.

## Authorization Boundaries

An explicit `$plan-to-docs` invocation authorizes creating or updating the
local Markdown artifact. It does not by itself authorize creating GitHub
issues.

- Write the document and prepare the issue set first.
- Show the exact target repository and complete issue preview.
- Obtain one explicit confirmation immediately before creating issues.
- Do not create or change labels, assignees, milestones, projects, branches,
  commits, or pushes unless the user separately requests them.
- Apply only existing labels whose meaning clearly matches an issue. Omit an
  ambiguous label instead of guessing.

## Establish the Source Plan

Use, in priority order:

1. The plan supplied with the invocation.
2. A plan file explicitly named by the user.
3. The latest finalized plan in the current conversation.

Do not combine a current plan with superseded conversation plans. If the user
asks the skill to generate the plan, resolve material product and
implementation decisions before creating artifacts. If no stable plan exists,
ask for the missing decisions rather than manufacturing requirements.

## Ground the Result in the Repository

Before writing:

1. Read all applicable `AGENTS.md` files and the repository's relevant
   specifications, architecture documents, code, tests, and issue templates.
2. Identify existing components the plan changes and new components it
   requires. Use repository terminology in the design.
3. Flag contradictions between the source plan and authoritative repository
   guidance. Do not silently choose a precedence.
4. Discover existing GitHub labels without creating or modifying metadata.
5. Resolve the target repository in this order: a repository explicitly named
   by the user, a remote named `upstream`, the current branch's tracking remote,
   then `origin`. Never silently switch targets if access fails.

Prefer an available GitHub integration for repository reads and writes;
otherwise use `gh`. Verify authentication and issue support before the
creation step.

## Create the Engineering Document

Default to `docs/plans/<plan-slug>.md`, using a short kebab-case slug derived
from the plan title. Create `docs/plans/` when needed.

If the destination already exists, inspect it before changing it. Update it
only when its document marker identifies the same plan. For an unrelated file
collision or uncertain ownership, ask the user for a different path rather
than overwriting it.

The document must:

- Stand alone for an engineer who has not read the conversation.
- Separate stated requirements from implementation details inferred through
  repository inspection.
- Organize behavior into major features.
- Give every component within a major feature a detailed responsibility,
  interface, collaborator, state/data, and failure-behavior description when
  those aspects apply.
- Explain component interactions and important data flow rather than merely
  listing filenames or classes.
- Include acceptance criteria and test scenarios grounded in the source plan.
- Contain the complete issue-ready specifications so work can continue when
  GitHub is unavailable.

Do not invent speculative interfaces, migrations, validation rules, or edge
cases. Mark a reasonable low-impact implementation inference as an assumption;
ask about an ambiguity that materially changes product behavior or scope.

## Derive Implementation Issues

Create one issue per independently implementable and verifiable behavior
slice. A numbered or bulleted sub-requirement is evidence for issue boundaries,
not a mandatory one-to-one rule.

- Group clauses that have no useful independent outcome or cannot be verified
  separately.
- Split a broad requirement when it contains multiple independently
  deliverable behaviors.
- Keep atomic work such as adding a field, wiring a handler, or writing an
  individual test inside the corresponding issue; do not make it a standalone
  issue.
- Avoid horizontal issues that implement only one technical layer when a
  vertical behavior slice is feasible.
- Record dependencies only when one slice genuinely cannot be completed or
  verified before another.

Every issue must include the context, mapped requirements, in-scope behavior,
explicit exclusions, acceptance criteria, affected components, test
expectations, dependencies, document path, and stable traceability marker
defined in the format reference.

## Detect Existing Issues

Before previewing creation, inspect both open and closed issues in the resolved
repository. Compare the exact traceability marker in issue bodies, using
pagination when needed. Title similarity alone is not a duplicate match.

- Treat an exact marker match as already created and show its state and URL.
- Do not reopen, edit, or recreate a matching issue unless the user explicitly
  requests that action.
- If duplicate discovery cannot be completed, do not create issues. Preserve
  the local document and report what access is missing.

## Preview and Create

Present a concise preview containing:

- Document path and source plan.
- Exact GitHub owner/repository.
- Every issue title, behavior slice ID, requirement mapping, dependencies, and
  proposed existing labels.
- Existing matching issues that will be skipped.

Ask for a single explicit confirmation to create the listed missing issues.
Any change to the target repository or issue set invalidates that confirmation
and requires a new preview.

After confirmation, create missing issues sequentially. After each successful
creation, immediately add its number, URL, and state to the document's
traceability table. If creation fails partway through, stop, retain successful
links and all remaining issue-ready drafts, and report the exact remaining
slices. A rerun must repeat duplicate discovery before retrying.

## Completion Report

Report the document path, created and skipped issue URLs, labels applied, and
any unresolved drafts or blockers. Do not claim that an issue, label, commit,
or push exists unless the corresponding operation succeeded.
