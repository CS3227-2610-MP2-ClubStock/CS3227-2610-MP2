# Individual Reflection — Keith

My work on ClubStock focused on the Member role and contributions to the shared foundations, testing, and delivery process. I used the AI agent for planning, implementation, review, debugging, documentation, and Git operations. The most useful customization was giving the agent repeatable procedures tied to our repository, rather than expecting a general instruction such as “write good Java code” to produce consistent results. The experience also showed me that generating code quickly does not remove the need to examine requirements, failure cases, and the actual user interface.

The tasks and how I selected skills
----------------------------------

I customized my agent to understand ClubStock's terminology, shared state rules, Java and JavaFX environment, and contribution conventions. `AGENTS.md` supplied the instructions that applied across tasks, including reading the specifications and recording substantive outcomes, while individual skills supplied narrower procedures.

I selected skills according to the behavior being changed and the evidence needed to establish success. A change to request ownership or Loan transitions called for `clubstock-feature`; a screen or styling change called for `javafx-ui`; and checking the resulting behavior called for `clubstock-verify`. A feature involving both a service and a screen could need all three. Turning an agreed plan into implementation documents and issue proposals called for an explicit `plan-to-docs` invocation. Preparing logical commits called for `commit`.

This distinction mattered because different tasks have different failure modes. For example, a review request should not silently give permission to the agent to modify application behavior. I learned to select the smallest relevant set of skills and define the expected outcome before asking the agent to act.

Some useful skills I used during the project
--------------------------

**1. `plan-to-docs`: making plans durable and testable.**

I requested this skill to turn a finalized software plan into a repository-grounded design document and reviewable GitHub issue proposals. Its [instructions](../../.agents/skills/plan-to-docs/SKILL.md) define the input, output format, repository discovery, issue boundaries, and authorization rules. It is explicit-only, so an ordinary planning request should not activate its entire workflow.

The useful detail is that it does more than summarize a conversation. It requires component responsibilities, interactions, acceptance criteria, and implementation-sized behavior slices. Stable traceability markers connect the document to issues, and discovery checks both open and closed issues before creating anything. The complete issue drafts remain in the local document if GitHub access fails. Issue creation requires approval of the prepared issue set and target repository.

I also requested a [validation harness](../../.agents/skills/plan-to-docs/tests/README.md). Static checks inspected the package metadata, references, and formatting. Model-backed tests used disposable repositories and a fake GitHub CLI to check observable behavior: document creation, no issue writes before approval, creation after approval, duplicate detection on a rerun, and preservation of local work when authentication failed. This made the skill's promises testable without creating real issues.

The first end-to-end run exposed problems in the harness itself, in which a login shell selected the real `gh` executable, and resumed sessions lost their fixture directory and sandbox options. The corrections used an absolute fake-CLI path, restored the execution options, and added assertions about discovery and working directories. The log records that the complete model-backed suite subsequently passed. This taught me that a skill test must validate its environment as well as the agent's output. Passing metadata checks alone cannot establish correct tool use.

**2. `clubstock-feature`: preserving shared business rules.**

This [shared skill](../../.agents/skills/clubstock-feature/SKILL.md) directs implementation through the existing domain and service layers. It requires the agent to identify the actor, preconditions, state changes, requirement IDs, and effects visible to the other role. That was particularly relevant to my Member implementation because Member actions affect the same records that Exco later processes.

For example, submitting a request must not reserve an EquipmentItem, and zero stock requires a warning rather than rejection. Returning an item changes its Loan to `RETURN_PENDING` and makes the item unavailable, while its authoritative condition remains unchanged until Exco verification. These distinctions are easy to lose if the agent implements only what a screen appears to need.

The skill also requires ownership and transition checks at the shared operation boundary, so callers cannot bypass them by avoiding the UI. In the Member Loan-query work, a later review identified a session-change gap between the initial guard and the read callback. The fix revalidated ownership inside the transaction and added a deterministic regression test. This demonstrated both the value and the limit of a domain skill: it directs attention to the right invariant, but the implementation still needs review at the exact point where mutable state is read.

**3. `clubstock-verify`: matching evidence to the claim.**

The [verification skill](../../.agents/skills/clubstock-verify/SKILL.md) separates compilation, business-rule tests, packaged execution, and desktop inspection. It asks for focused checks based on the affected requirements and checks related records after a transition. An operation rejected before committing should leave shared state unchanged, while a committed operation should produce the expected result for both roles.

This helped make testing more purposeful. During the account-domain review, 19 tests passed, but review still found that `String.trim()` did not satisfy the required Unicode-whitespace handling. Replacing it with `strip()` and adding regression cases increased the passing set to 21. The useful result was the added coverage of the requirement, rather than the larger test count itself.

The distinction between build and runtime evidence also mattered for delivery. After adding packaged installation checks and a four-platform CI smoke matrix, my local results included a passing 275-test suite and Apple Silicon installation verification, while the remote matrix remained pending in that record. This showed me that successful local verification on one platform does not establish that the application works correctly on every supported platform. Similarly, several Member implementation sessions explicitly recorded that visual acceptance was incomplete because native GUI inspection was unavailable.

**4. `javafx-ui`: connecting correct services to usable screens.**

The [JavaFX skill](../../.agents/skills/javafx-ui/SKILL.md) covers screen contracts, role-appropriate navigation, input validation, loading and failure feedback, classpath resources, and background work. It keeps business rules in services while asking controllers to translate input and present results. This was useful for the request-entry, own-request, active-Loan, and return/reporting screens.

Its emphasis on user-visible states was especially valuable, but the work also revealed a verification gap. The request-review screen initially displayed labels without readable values because the default text color was unsuitable against a white card. I had to personally perform acceptance testing to find out that the error was present. A later Member return-option text-color correction provided another example. Resource and compilation checks had not established visual readability. I learned that a UI skill needs actual rendered inspection before I can regard the screen as accepted. If inspection is unavailable, visual acceptance should remain explicitly outstanding.

**5. `commit`: reducing repetitive work while preserving reviewability.**

I used a [project-specific commit skill](../../.agents/skills/commit/SKILL.md) that inspects the whole worktree and groups related implementation, tests, and documentation by purpose. Its default is `type: description`; scopes and breaking-change markers require explicit requests. I also clarified that commit messages must not identify the session user.

The Member request work illustrates its benefit: the recorded commits separated the repository query, submission service, history and cancellation behavior, individual screens, and integration wiring. Each group had an understandable purpose. The skill also requires explicit paths or hunks and review of the staged diff, which matters when unrelated work is present. This reduced repeated instructions about formatting and staging without removing the need to inspect what would actually be committed.

Where the agent helped, and where it created rework
-------------------------------------------------

The agent was effective when the task had a clear specification and a bounded outcome. It helped translate requirements into implementation slices, connect services to JavaFX screens, generate meaningful regression cases, and update documentation alongside changes. The Member request, Loan-query, and reporting work benefited from implementing smaller increments with verification between them. I do not have a measured time-saving figure, but the logs show substantial implementation and validation work completed through this process.

The most significant recurring correction concerned transaction outcomes. Request submission, cancellation, and return/reporting controllers needed follow-up fixes because an exception after a database commit could be presented as a failed action. A later refresh failure could also obscure a successful save. The required behavior was to preserve success for a known `COMMITTED` outcome and reconcile uncertain state before encouraging a retry. Treating every exception alike could mislead the Member into repeating an operation that had already succeeded.

These similar fixes across multiple controllers created additional review and implementation work. They suggest that our instructions and shared UI approach did not make transaction-outcome handling explicit enough early on. Service tests were valuable, but they did not fully cover how controllers translated those outcomes into feedback.

There were also costs outside application code. The `plan-to-docs` harness needed debugging before its results were trustworthy. Mandatory identity questions initially interrupted autonomous work, so I requested passive attribution that would not block a task. In CI, a recorded Ubuntu failure came from running JavaFX installation verification in a build job without a display; the correction kept that check in the existing smoke matrix. These examples taught me to examine the execution environment and workflow rules before assuming a failure needs a product-code change.

What I would change next time
----------------------------

I would make the instructions more explicit in a few areas that repeatedly caused corrections:

- Add a transaction-outcome checklist to the feature and UI skills: distinguish confirmed rollback, known commit, and unknown outcome; preserve save success when refresh fails; and define safe retry behavior.
- Establish a focused JavaFX controller-testing approach earlier, including background-task failures, session changes, stale data, and success-message preservation, and pair it with repeatable visual checks using disposable data.
- Add a packaging and CI skill that inspects display availability, native-library classifiers, artifact identity, and platform coverage before choosing commands. It should verify the artifact that will actually be distributed.
- Test skill routing as well as skill output. Include prompts that should activate a skill and similar prompts that should not, especially for explicit-only workflows such as `plan-to-docs`.
- Maintain concise requirement-to-evidence records so completion means that the requested behavior has supporting checks, with unexecuted scenarios visible. Keep skill references current as the application and build evolve.
- Create a separate skill for issue creation so that `plan-to-docs` can focus on generating documentation.


What I learned about a single software-engineering agent
-------------------------------------------------------

An effective single agent needs a clear task boundary, authoritative project context, suitable tools, and observable completion criteria. A skill should explain when it applies, what it must inspect, which constraints it must preserve, and how to establish the result. Merely collecting more instructions can make the workflow harder to follow if their responsibilities overlap or they become stale.

The same agent can plan, implement, and verify, but its generated tests may share the assumptions behind its generated code. Requirement-based scenarios, failure injection, independent review, and visual acceptance provide different forms of evidence. The Unicode-whitespace issue and unreadable UI values showed why a passing test suite should start a discussion about coverage rather than end it.

My main lesson is that agent customization is an engineering task in its own right. I need to test skills, inspect their failures, and improve the instructions based on repeated patterns. The agent became most useful when it could complete routine work autonomously while making its assumptions, verification limits, and unresolved decisions visible for me to assess.
