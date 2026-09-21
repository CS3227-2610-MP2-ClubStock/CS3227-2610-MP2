#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
skill_dir="$(cd -- "$script_dir/.." && pwd)"
run_e2e=false
keep_sandbox=false
sandbox_root=""
session_ids=()
captured_session_id=""

usage() {
    printf '%s\n' \
        "Usage: $0 [--e2e] [--keep-sandbox]" \
        "" \
        "  --e2e           Run model-backed tests in disposable repositories." \
        "  --keep-sandbox  Retain the disposable files after a successful run."
}

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    return 1
}

while (($# > 0)); do
    case "$1" in
        --e2e)
            run_e2e=true
            ;;
        --keep-sandbox)
            keep_sandbox=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            exit 2
            ;;
    esac
    shift
done

ruby "$script_dir/validate_skill.rb" "$skill_dir"

if [[ "$run_e2e" != true ]]; then
    printf '%s\n' \
        "Static plan-to-docs checks passed." \
        "Run $0 --e2e for the opt-in model-backed tests."
    exit 0
fi

for required_tool in codex git jq rsync python3; do
    command -v "$required_tool" >/dev/null 2>&1 \
        || fail "Required command is unavailable: $required_tool"
done

sandbox_root="$(mktemp -d)"
sandbox_root="$(cd -- "$sandbox_root" && pwd -P)"
fake_bin="$sandbox_root/bin"
mkdir -p "$fake_bin"
cp "$script_dir/fake-gh.py" "$fake_bin/gh"
chmod 755 "$fake_bin/gh"

cleanup() {
    local exit_status=$?
    local session_id

    trap - EXIT INT TERM
    for session_id in "${session_ids[@]}"; do
        codex delete --force "$session_id" >/dev/null 2>&1 || true
    done

    if [[ "$keep_sandbox" == true || $exit_status -ne 0 ]]; then
        printf 'Sandbox retained at %s\n' "$sandbox_root" >&2
    elif [[ -n "$sandbox_root" && -d "$sandbox_root" ]]; then
        case "$sandbox_root" in
            /tmp/*|/private/tmp/*|/var/folders/*|/private/var/folders/*)
                rm -rf -- "$sandbox_root"
                ;;
            *)
                printf 'Refusing to remove unexpected sandbox path: %s\n' \
                    "$sandbox_root" >&2
                exit_status=1
                ;;
        esac
    fi

    exit "$exit_status"
}
trap cleanup EXIT INT TERM

prepare_fixture() {
    local fixture_repo=$1

    mkdir -p \
        "$fixture_repo/.agents/skills/plan-to-docs" \
        "$fixture_repo/plans"
    rsync -a --exclude tests/ \
        "$skill_dir/" "$fixture_repo/.agents/skills/plan-to-docs/"
    cp "$script_dir/fixtures/AGENTS.md" "$fixture_repo/AGENTS.md"
    cp "$script_dir/fixtures/sample-plan.md" \
        "$fixture_repo/plans/sample-plan.md"

    git -C "$fixture_repo" init -q
    git -C "$fixture_repo" config user.name "Plan-to-Docs Test"
    git -C "$fixture_repo" config user.email "plan-to-docs@example.invalid"
    git -C "$fixture_repo" add \
        .agents/skills/plan-to-docs AGENTS.md plans/sample-plan.md
    git -C "$fixture_repo" commit -q -m "test: create fixture baseline"
    git -C "$fixture_repo" remote add origin \
        https://github.com/example/plan-to-docs-fixture.git
}

capture_session_id() {
    local events_file=$1
    local session_id

    session_id="$(jq -r \
        'select(.type == "thread.started") | .thread_id // .thread.id // empty' \
        "$events_file" | head -n 1)"
    [[ -n "$session_id" ]] || fail "Codex did not report a session ID"
    session_ids+=("$session_id")
    captured_session_id="$session_id"
}

assert_only_plan_document_changed() {
    local fixture_repo=$1
    local status_file=$2

    git -C "$fixture_repo" status --porcelain --untracked-files=all \
        >"$status_file"
    [[ "$(wc -l <"$status_file" | tr -d ' ')" == "1" ]] \
        || fail "Expected exactly one changed file in the fixture"
    grep -Eq '^\?\? docs/plans/[^/]+\.md$' "$status_file" \
        || fail "The only generated file must be docs/plans/<slug>.md"
}

assert_no_issue_writes() {
    local calls_file=$1

    if jq -e 'select(.mutation == "issue-create")' \
        "$calls_file" >/dev/null 2>&1; then
        fail "An issue write occurred before approval"
    fi
}

assert_fake_discovery_complete() {
    local calls_file=$1
    local fixture_repo=$2

    jq -s -e --arg fixture_repo "$fixture_repo" '
        length > 0
        and all(.[]; .cwd == $fixture_repo)
        and any(.[]; .args[0:2] == ["auth", "status"])
        and any(.[];
            .args[0:2] == ["repo", "view"]
            or (.args[0] == "api" and any(.args[]; contains("repos/example/plan-to-docs-fixture"))))
        and any(.[];
            .args[0:2] == ["label", "list"]
            or (.args[0] == "api" and any(.args[]; contains("/labels"))))
        and any(.[];
            .args[0:2] == ["issue", "list"]
            or (.args[0] == "api" and any(.args[]; contains("/issues"))))
    ' "$calls_file" >/dev/null \
        || fail "Fake GitHub repository, label, and duplicate discovery did not complete in the fixture"
}

assert_preview_ready_for_approval() {
    local preview_file=$1

    if grep -Eiq \
        '(authentication|API reads|duplicate discovery).*(failed|blocked|unverified)|(failed|blocked|unverified).*(authentication|duplicate discovery)' \
        "$preview_file"; then
        fail "Preview reported a GitHub or duplicate-discovery blocker"
    fi
}

run_codex() {
    local fixture_repo=$1
    local state_file=$2
    local calls_file=$3
    local events_file=$4
    local final_file=$5
    local prompt=$6

    PATH="$fake_bin:$PATH" \
    PLAN_TO_DOCS_FAKE_GH_STATE="$state_file" \
    PLAN_TO_DOCS_FAKE_GH_LOG="$calls_file" \
        codex -a never --disable apps exec \
        --ignore-user-config --json --sandbox workspace-write \
        --add-dir "$sandbox_root" -C "$fixture_repo" \
        -o "$final_file" "$prompt" >"$events_file"
}

resume_codex() {
    local fixture_repo=$1
    local state_file=$2
    local calls_file=$3
    local events_file=$4
    local final_file=$5
    local session_id=$6
    local prompt=$7

    PATH="$fake_bin:$PATH" \
    PLAN_TO_DOCS_FAKE_GH_STATE="$state_file" \
    PLAN_TO_DOCS_FAKE_GH_LOG="$calls_file" \
        codex -a never --disable apps --sandbox workspace-write \
        --add-dir "$sandbox_root" -C "$fixture_repo" exec resume \
        --ignore-user-config --json -o "$final_file" \
        "$session_id" "$prompt" >"$events_file"
}

printf '%s\n' "Running model-backed plan-to-docs tests..."

fixture_repo="$sandbox_root/repo"
state_file="$sandbox_root/gh-state.json"
calls_file="$sandbox_root/gh-calls.jsonl"
preview_events="$sandbox_root/preview-events.jsonl"
preview_final="$sandbox_root/preview-final.txt"
touch "$calls_file"
prepare_fixture "$fixture_repo"

preview_prompt="Use \$plan-to-docs with plans/sample-plan.md. This is an isolated automated test. Invoke GitHub commands only through the exact executable \`$fake_bin/gh\`; never invoke bare \`gh\`, connectors, or network access. Create the local engineering document and present the complete issue preview, then stop for confirmation. This message explicitly does not approve issue creation."
run_codex "$fixture_repo" "$state_file" "$calls_file" \
    "$preview_events" "$preview_final" "$preview_prompt"
capture_session_id "$preview_events"
preview_session="$captured_session_id"

assert_only_plan_document_changed "$fixture_repo" \
    "$sandbox_root/preview-status.txt"
shopt -s nullglob
plan_docs=("$fixture_repo"/docs/plans/*.md)
shopt -u nullglob
[[ ${#plan_docs[@]} -eq 1 ]] \
    || fail "Expected one generated plan document"
plan_doc="${plan_docs[0]}"

grep -Fq '<!-- plan-to-docs-document:' "$plan_doc" \
    || fail "Generated document lacks its stable document marker"
grep -Fq '<!-- plan-to-docs:' "$plan_doc" \
    || fail "Generated document lacks issue traceability markers"
grep -Fq '**Responsibility:**' "$plan_doc" \
    || fail "Generated document lacks component responsibilities"
grep -Fq '**Interface:**' "$plan_doc" \
    || fail "Generated document lacks component interfaces"
grep -Fq 'example/plan-to-docs-fixture' "$preview_final" \
    || fail "Preview does not identify the exact target repository"
grep -Eq 'SLICE-[0-9]+' "$preview_final" \
    || fail "Preview does not expose behavior slice IDs"
assert_fake_discovery_complete "$calls_file" "$fixture_repo"
assert_preview_ready_for_approval "$preview_final"
assert_no_issue_writes "$calls_file"

issue_count="$(awk '/<!-- plan-to-docs:/{count++} END{print count+0}' \
    "$plan_doc")"
((issue_count > 0)) || fail "Expected at least one proposed issue"

approval_events="$sandbox_root/approval-events.jsonl"
approval_final="$sandbox_root/approval-final.txt"
approval_prompt="I explicitly approve creating exactly the missing issues in the preview you just presented. Remain in \`$fixture_repo\` and invoke GitHub commands only through \`$fake_bin/gh\`; never invoke bare \`gh\`."
resume_codex "$fixture_repo" "$state_file" "$calls_file" \
    "$approval_events" "$approval_final" "$preview_session" "$approval_prompt"

created_count="$(jq -s \
    '[.[] | select(.mutation == "issue-create")] | length' "$calls_file")"
[[ "$created_count" == "$issue_count" ]] \
    || fail "Expected $issue_count issue writes after approval, got $created_count"
[[ "$(jq '.issues | length' "$state_file")" == "$issue_count" ]] \
    || fail "Fake GitHub state does not match the proposed issue count"
assert_fake_discovery_complete "$calls_file" "$fixture_repo"
while IFS= read -r issue_url; do
    grep -Fq "$issue_url" "$plan_doc" \
        || fail "Generated document is missing created issue URL: $issue_url"
done < <(jq -r '.issues[].url' "$state_file")
assert_only_plan_document_changed "$fixture_repo" \
    "$sandbox_root/approval-status.txt"

duplicate_events="$sandbox_root/duplicate-events.jsonl"
duplicate_final="$sandbox_root/duplicate-final.txt"
run_codex "$fixture_repo" "$state_file" "$calls_file" \
    "$duplicate_events" "$duplicate_final" "$preview_prompt"
capture_session_id "$duplicate_events"
created_after_rerun="$(jq -s \
    '[.[] | select(.mutation == "issue-create")] | length' "$calls_file")"
[[ "$created_after_rerun" == "$created_count" ]] \
    || fail "Duplicate rerun created an additional issue"
grep -Eiq 'existing|already|skip' "$duplicate_final" \
    || fail "Duplicate rerun did not report existing issues"

auth_repo="$sandbox_root/auth-failure-repo"
auth_state="$sandbox_root/auth-gh-state.json"
auth_calls="$sandbox_root/auth-gh-calls.jsonl"
auth_events="$sandbox_root/auth-events.jsonl"
auth_final="$sandbox_root/auth-final.txt"
touch "$auth_calls"
prepare_fixture "$auth_repo"
PATH="$fake_bin:$PATH" \
PLAN_TO_DOCS_FAKE_GH_STATE="$auth_state" \
PLAN_TO_DOCS_FAKE_GH_LOG="$auth_calls" \
PLAN_TO_DOCS_FAKE_GH_AUTH_FAIL=1 \
    codex -a never --disable apps exec \
    --ignore-user-config --json --sandbox workspace-write \
    --add-dir "$sandbox_root" -C "$auth_repo" \
    -o "$auth_final" "$preview_prompt" >"$auth_events"
capture_session_id "$auth_events"
assert_only_plan_document_changed "$auth_repo" \
    "$sandbox_root/auth-status.txt"
assert_no_issue_writes "$auth_calls"
grep -Eiq 'auth|access|GitHub|block' "$auth_final" \
    || fail "Authentication failure was not reported"

printf '%s\n' "All plan-to-docs tests passed."
