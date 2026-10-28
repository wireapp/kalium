#!/usr/bin/env python3
# Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
"""Summarize existing Gradle profiles and dedicated CI cache operations, offline."""

import argparse
from collections import Counter
import glob
from html import escape
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import stat
import time


class ProfileParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.task_rows = []
        self.task_table = False
        self.row = []
        self.cell = None

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self.task_table = False
        elif tag == "tr":
            self.row = []
        elif tag in ("td", "th"):
            self.cell = []

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.cell is not None:
            self.row.append("".join(self.cell).strip())
            self.cell = None
        elif tag == "tr":
            self.rows.append(self.row)
            if self.row == ["Task", "Duration", "Result"]:
                self.task_table = True
            elif self.task_table:
                self.task_rows.append(self.row)
        elif tag == "table":
            self.task_table = False


def duration_seconds(value):
    parts = re.findall(r"(\d+(?:\.\d+)?)\s*([hms])", value)
    if not parts or re.sub(r"\d+(?:\.\d+)?\s*[hms]|\s", "", value):
        raise ValueError(f"Unrecognized Gradle duration: {value!r}")
    units = {"h": 3600, "m": 60, "s": 1}
    return sum(float(number) * units[unit] for number, unit in parts)


def read_profile(path):
    parser = ProfileParser()
    parser.feed(path.read_text())
    tasks = []
    summary = {}
    for row in parser.rows:
        if len(row) == 2 and row[0] in ("Total Build Time", "Configuring Projects"):
            summary[row[0]] = duration_seconds(row[1])
    for row in parser.task_rows:
        if len(row) == 3 and row[0].startswith(":") and row[2] != "(total)":
            tasks.append({
                "path": row[0],
                "seconds": duration_seconds(row[1]),
                "outcome": row[2] or "EXECUTED",
            })
    if "Total Build Time" not in summary:
        raise ValueError("Missing Gradle profile summary")
    return {"file": path.name, "summary": summary, "tasks": tasks}


def restore_result(step):
    outcome = step.get("outcome", "skipped")
    if outcome != "success":
        return outcome
    outputs = step.get("outputs") or {}
    if not outputs.get("cache-matched-key"):
        return "cold"
    return "exact" if outputs.get("cache-hit") == "true" else "fallback"


def tree_bytes(roots, excluded=()):
    """Logical file bytes, excluding symlinks and separate cache layers."""
    size = 0
    errors = []
    for root in roots:
        if not root.is_dir() or root.is_symlink():
            continue
        for directory, dirs, files in os.walk(root, onerror=lambda error: errors.append(str(error))):
            dirs[:] = [name for name in dirs if name not in excluded and not Path(directory, name).is_symlink()]
            for name in files:
                try:
                    entry = Path(directory, name).lstat()
                    if stat.S_ISREG(entry.st_mode):
                        size += entry.st_size
                except OSError as error:
                    errors.append(str(error))
    return size, errors


def cache_sizes(gradle_home, konan_home, names=None):
    output_dirs = [Path(path) for path in glob.glob(str(gradle_home / "caches/build-cache-*"))]
    definitions = {
        "dependencies": ([gradle_home / "caches", gradle_home / "wrapper"], [path.name for path in output_dirs]),
        "outputs": (output_dirs, []),
        "konan": ([konan_home], []),
    }
    return {name: tree_bytes(roots, excluded) for name, (roots, excluded) in definitions.items()
            if names is None or name in names}


def cell(value):
    text = str(value).replace("\n", " ").replace("\r", " ")
    return "".join(f"&#{ord(character)};" if character in "|`\\[]()*_!~#" else escape(character)
                   for character in text)


def render_report(report):
    lines = [f"### Gradle cache effectiveness — {cell(report['label'])}", ""]
    if report["stages"]:
        lines += ["; ".join(f"{cell(name)}: **{cell(outcome)}**" for name, outcome in report["stages"].items()), ""]
    if report["caches"]:
        lines += ["| Cache layer | Restore | Save step | Local contents (MiB) |", "| --- | --- | --- | ---: |"]
        for name, cache in report["caches"].items():
            size = "unknown" if cache["bytes"] is None else f"{cache['bytes'] / 1024 ** 2:.1f}"
            if cache["size_errors"]:
                size += " (partial)"
            lines.append(f"| {cell(name)} | {cell(cache['restore'])} | {cell(cache['save'])} | {size} |")
        lines += ["", "Exact/fallback describe the archive lookup, not Gradle task-output hits. "
                  "Contents are logical file bytes at job end, not compressed archive sizes. "
                  "A successful save step does not prove an upload occurred; consult its logs.", ""]
        for operation in ("restore", "save"):
            seconds = report["cache_step_seconds"].get(operation)
            duration = "not recorded" if seconds is None else f"{seconds:.1f}s"
            lines.append(f"Cache {operation} steps, including action overhead: **{duration}**.")
        lines += ["", "<details><summary>Restored cache keys</summary>", ""]
        for name, cache in report["caches"].items():
            lines.append(f"- {cell(name)}: {cell(cache['matched_key'] or 'none')}")
        lines += ["", "</details>", ""]
    if report["profiles"]:
        lines += ["| Gradle invocation | Build time | Project configuration | FROM-CACHE | UP-TO-DATE | Executed | Other |",
                  "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
        for profile in report["profiles"]:
            counts = Counter(task["outcome"] for task in profile["tasks"])
            other = sum(count for outcome, count in counts.items() if outcome not in ("FROM-CACHE", "UP-TO-DATE", "EXECUTED"))
            summary = profile["summary"]
            configuration = summary.get("Configuring Projects")
            configuration = "unknown" if configuration is None else f"{configuration:.3f}s"
            lines.append(f"| {cell(profile['file'])} | {summary['Total Build Time']:.3f}s | {configuration} | "
                         f"{counts['FROM-CACHE']} | {counts['UP-TO-DATE']} | {counts['EXECUTED']} | {other} |")
        executed = [(profile["file"], task) for profile in report["profiles"] for task in profile["tasks"]
                    if task["outcome"] in ("EXECUTED", "FAILED")]
        if executed:
            lines += ["", "**Slowest executed tasks**", "", "| Task | Duration | Outcome | Invocation |", "| --- | ---: | --- | --- |"]
            for filename, task in sorted(executed, key=lambda entry: entry[1]["seconds"], reverse=True)[:10]:
                lines.append(f"| {cell(task['path'])} | {task['seconds']:.3f}s | {cell(task['outcome'])} | {cell(filename)} |")
        lines += ["", "Counts cover profiled task executions, including non-cacheable tasks. "
                  "They are not an eligible-task cache-hit percentage or an estimate of time saved. "
                  "Executed includes failed executions: Gradle profiles do not identify task failures; "
                  "workflow stage outcomes above indicate build/test failures."]
    else:
        lines += ["No Gradle profiles were produced; task reuse is unknown."]
    if report["warnings"]:
        lines += ["", "Report warnings:"] + [f"- {cell(warning)}" for warning in report["warnings"]]
    return "\n".join(lines) + "\n"


def collect_report(profiles_dir, timings, states, gradle_home, konan_home, label, stages=None):
    warnings = []
    profiles = []
    for path in sorted(profiles_dir.glob("profile-*.html")):
        try:
            profiles.append(read_profile(path))
        except (OSError, ValueError) as error:
            warnings.append(f"{path.name}: {error}")
    measured = {name for name, state in states.items()
                if any((state.get(operation) or {}).get("outcome") == "success" for operation in ("restore", "save"))}
    sizes = cache_sizes(gradle_home, konan_home, measured) if measured else {}
    caches = {}
    for name, state in states.items():
        restore = state.get("restore") or {}
        size, errors = sizes.get(name, (None, []))
        caches[name] = {
            "restore": restore_result(restore),
            "save": (state.get("save") or {}).get("outcome", "skipped"),
            "matched_key": (restore.get("outputs") or {}).get("cache-matched-key", ""),
            "bytes": size,
            "size_errors": errors,
        }
    elapsed = {}
    for operation in ("restore", "save"):
        start, end = timings.get(f"{operation}-start"), timings.get(f"{operation}-end")
        elapsed[operation] = end - start if start is not None and end is not None and end >= start else None
    return {"label": label, "stages": stages or {}, "cache_step_seconds": elapsed,
            "caches": caches, "profiles": profiles, "warnings": warnings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["restore-start", "restore-end", "save-start", "summary"])
    parser.add_argument("--profiles", type=Path, default=Path("build/reports/profile"))
    parser.add_argument("--output", type=Path, default=Path("build/ci/cache-report.json"))
    args = parser.parse_args()
    timings_path = Path(os.environ["RUNNER_TEMP"]) / "kalium-cache-timings.json"
    timings = json.loads(timings_path.read_text()) if timings_path.exists() else {}
    timings["save-end" if args.command == "summary" else args.command] = time.monotonic()
    timings_path.write_text(json.dumps(timings))
    if args.command != "summary":
        return
    report = collect_report(
        args.profiles, timings, json.loads(os.environ.get("CACHE_STATES", "{}")),
        Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))),
        Path(os.environ.get("KONAN_DATA_DIR", str(Path.home() / ".konan"))),
        os.environ.get("CACHE_REPORT_LABEL", "CI"),
        json.loads(os.environ.get("GRADLE_STAGES", "{}")),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
        summary.write(render_report(report))


if __name__ == "__main__":
    main()
