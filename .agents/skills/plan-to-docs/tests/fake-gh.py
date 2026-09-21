#!/usr/bin/env python3

"""Deterministic GitHub CLI substitute for plan-to-docs tests."""

import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional


REPOSITORY = "example/plan-to-docs-fixture"
REPOSITORY_URL = f"https://github.com/{REPOSITORY}"


def required_path(variable: str) -> Path:
    value = os.environ.get(variable)
    if not value:
        print(f"Missing required environment variable: {variable}", file=sys.stderr)
        raise SystemExit(2)
    return Path(value)


STATE_PATH = required_path("PLAN_TO_DOCS_FAKE_GH_STATE")
LOG_PATH = required_path("PLAN_TO_DOCS_FAKE_GH_LOG")


def load_state() -> dict:
    if not STATE_PATH.exists():
        return {"issues": []}
    return json.loads(STATE_PATH.read_text())


def save_state(state: dict) -> None:
    STATE_PATH.write_text(json.dumps(state, indent=2) + "\n")


def log_call(arguments: List[str], mutation: Optional[str] = None) -> None:
    entry = {"args": arguments, "cwd": str(Path.cwd())}
    if mutation:
        entry["mutation"] = mutation
    with LOG_PATH.open("a") as log_file:
        log_file.write(json.dumps(entry) + "\n")


def option_value(arguments: List[str], *names: str) -> Optional[str]:
    for index, argument in enumerate(arguments):
        if argument in names and index + 1 < len(arguments):
            return arguments[index + 1]
        for name in names:
            prefix = f"{name}="
            if argument.startswith(prefix):
                return argument[len(prefix) :]
    return None


def create_issue(
    arguments: List[str],
    title: Optional[str] = None,
    body: Optional[str] = None,
) -> dict:
    title = title or option_value(arguments, "--title", "-t")
    body_file = option_value(arguments, "--body-file", "-F")
    body = body or option_value(arguments, "--body", "-b")
    if body_file:
        body = sys.stdin.read() if body_file == "-" else Path(body_file).read_text()
    if not title or body is None:
        print("fake gh requires an issue title and body", file=sys.stderr)
        raise SystemExit(2)

    labels = []
    for index, argument in enumerate(arguments):
        if argument in {"--label", "-l"} and index + 1 < len(arguments):
            labels.extend(arguments[index + 1].split(","))
        elif argument.startswith("--label="):
            labels.extend(argument[len("--label=") :].split(","))

    state = load_state()
    number = 101 + len(state["issues"])
    issue = {
        "number": number,
        "title": title,
        "body": body,
        "state": "OPEN",
        "url": f"{REPOSITORY_URL}/issues/{number}",
        "labels": [{"name": label} for label in labels if label],
    }
    state["issues"].append(issue)
    save_state(state)
    log_call(arguments, mutation="issue-create")
    return issue


def api_fields(arguments: List[str]) -> Dict[str, str]:
    fields = {}
    for index, argument in enumerate(arguments):
        if argument in {"-f", "-F", "--field", "--raw-field"} and index + 1 < len(arguments):
            key, separator, value = arguments[index + 1].partition("=")
            if separator:
                fields[key] = value
    return fields


def handle_api(arguments: List[str]) -> None:
    method = (option_value(arguments, "--method", "-X") or "GET").upper()
    endpoint = next(
        (
            argument
            for argument in arguments[1:]
            if not argument.startswith("-")
            and "/" in argument
            and "=" not in argument
        ),
        "",
    )
    if method == "POST" and endpoint.endswith("/issues"):
        fields = api_fields(arguments)
        issue = create_issue(arguments, fields.get("title"), fields.get("body"))
        print(json.dumps(issue))
        return
    if method != "GET":
        print(f"Unsupported fake gh API mutation: {method} {endpoint}", file=sys.stderr)
        raise SystemExit(2)
    if endpoint.endswith("/labels"):
        print(json.dumps([{"name": "enhancement", "description": "New functionality"}]))
    elif endpoint.endswith("/issues"):
        print(json.dumps(load_state()["issues"]))
    else:
        print(json.dumps({"full_name": REPOSITORY, "has_issues": True, "html_url": REPOSITORY_URL}))


def main() -> None:
    arguments = sys.argv[1:]
    log_call(arguments)
    if not arguments or arguments[0] in {"--version", "version"}:
        print("gh version 2.99.0 (plan-to-docs test double)")
        return

    if arguments[:2] == ["auth", "status"]:
        if os.environ.get("PLAN_TO_DOCS_FAKE_GH_AUTH_FAIL") == "1":
            print("not logged into github.com", file=sys.stderr)
            raise SystemExit(1)
        print("Logged in to github.com as plan-to-docs-test")
        return

    if os.environ.get("PLAN_TO_DOCS_FAKE_GH_AUTH_FAIL") == "1":
        print("authentication required for github.com", file=sys.stderr)
        raise SystemExit(1)

    if arguments[:2] == ["repo", "view"]:
        repository = {
            "nameWithOwner": REPOSITORY,
            "hasIssuesEnabled": True,
            "url": REPOSITORY_URL,
        }
        if option_value(arguments, "--jq", "-q") == ".nameWithOwner":
            print(REPOSITORY)
        else:
            print(json.dumps(repository))
        return

    if arguments[:2] == ["label", "list"]:
        print(json.dumps([{
            "name": "enhancement",
            "description": "New functionality",
            "color": "84b6eb",
        }]))
        return

    if arguments[:2] == ["issue", "list"]:
        print(json.dumps(load_state()["issues"]))
        return

    if arguments[:2] == ["issue", "create"]:
        issue = create_issue(arguments)
        print(issue["url"])
        return

    if arguments[0] == "api":
        handle_api(arguments)
        return

    print(f"Unsupported fake gh command: {' '.join(arguments)}", file=sys.stderr)
    raise SystemExit(2)


if __name__ == "__main__":
    main()
