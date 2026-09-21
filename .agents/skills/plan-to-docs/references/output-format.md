# Plan-to-Docs Output Format

Use this reference when writing the engineering document and GitHub issue
bodies. Adapt headings to the plan, but retain the identifiers and traceability
fields that make reruns safe.

## Document Identity

Start the file with:

```markdown
<!-- plan-to-docs-document: <document-id> -->
# <Plan title>

| Field | Value |
| --- | --- |
| Status | Proposed, approved, or in progress |
| Source plan | Conversation, relative file path, or user-provided text |
| Document ID | `<document-id>` |
| Target repository | `owner/repository` |
```

Derive `<document-id>` from the plan slug. Keep it stable across reruns even if
the document title changes.

## Recommended Document Sections

### Summary and Goals

Explain the intended outcome, audience, success criteria, and explicit
non-goals. Do not turn unspecified behavior into a non-goal merely to fill the
section.

### Architecture and Constraints

Summarize repository facts that shape the implementation: existing boundaries,
applicable specifications, compatibility requirements, and confirmed design
decisions. Clearly label any inference that is not stated in the source plan.

### Major Features

Create one section per major feature. Each section should contain:

- **Behavior:** the user-visible or system outcome.
- **Mapped requirements:** stable source requirement IDs where present;
  otherwise assign local IDs such as `REQ-1.1`.
- **Components:** one entry per added or materially changed component.
- **Interactions and data flow:** how those components collaborate during the
  primary path and important failure paths.
- **Acceptance criteria:** observable, testable results.

Describe a component using this shape when the fields apply:

```markdown
#### `<Component name>`

- **Responsibility:** What it owns and why it exists.
- **Interface:** Commands, events, API/UI boundaries, or persisted data it
  consumes and exposes.
- **Collaborators:** Existing and new components it calls or is called by.
- **State and data:** State transitions, stored fields, validation, or
  invariants established by the source plan.
- **Failure behavior:** Expected handling of relevant invalid input, unavailable
  dependencies, and partial operations.
```

Use conceptual names when concrete classes or files have not been decided. Do
not present speculative names as committed public interfaces.

### Cross-Cutting Considerations

Include only considerations supported by the plan or repository, such as
authorization, compatibility, migrations, observability, accessibility, or
performance. Omit empty boilerplate categories.

### Delivery Issues

Keep the complete proposed issue bodies in this section, keyed by stable slice
IDs. This is the fallback issue bundle when GitHub is unavailable.

### Traceability

Use a table with one row per behavior slice:

```markdown
| Slice ID | Requirements | Major feature | GitHub issue | State |
| --- | --- | --- | --- | --- |
| `SLICE-001` | `REQ-1.1`, `REQ-1.2` | Example feature | Draft | Not created |
```

Replace `Draft` with the issue link immediately after successful creation.

### Assumptions and Open Questions

Record accepted defaults and remaining non-blocking uncertainty. Resolve any
question that would materially alter the issue set before requesting creation
approval.

## Issue Body Template

Use stable slice IDs within a document. Never renumber an existing slice merely
because another slice was added or removed.

```markdown
## Context

<Why this behavior is needed and the outcome it enables.>

## Requirements

- `<source or local requirement ID>` — <requirement summary>

## Scope

- <Coherent behavior delivered by this issue>

## Out of scope

- <Nearby behavior deliberately left to another slice or excluded by the plan>

## Components

- **<Component>:** <responsibility or change relevant to this slice>

## Acceptance criteria

- [ ] <Observable behavior or verifiable invariant>

## Test scenarios

- <Primary, boundary, authorization, or failure scenario supported by the plan>

## Dependencies

- <Blocking slice ID and reason, or `None`>

## Traceability

- Design document: `<relative-document-path>`
- Behavior slice: `<slice-id>`

<!-- plan-to-docs:<document-id>:<slice-id> -->
```

The issue title should describe the delivered behavior in imperative form.
Avoid titles that name only a file, layer, or atomic implementation action.

## Sizing Heuristics

A slice is appropriately sized when it has one coherent outcome, can receive
meaningful acceptance testing, and can be reviewed without requiring unrelated
features. Reconsider the boundary when:

- Its acceptance criteria describe unrelated user outcomes.
- It spans independently deployable or demonstrable flows.
- It has no observable outcome without another proposed issue.
- Its title would naturally be an atomic task such as “add field” or “write
  tests.”

When clauses must land together to preserve an invariant or complete one
interaction, keep them in the same issue and list the atomic steps under
components or acceptance criteria.
