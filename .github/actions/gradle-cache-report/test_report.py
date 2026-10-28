# Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import report


PROFILE = """<!doctype html><html><body>
<table><tr><th>Description</th><th>Duration</th></tr>
<tr><td>Total Build Time</td><td>1m2.500s</td></tr>
<tr><td>Configuring Projects</td><td>0.250s</td></tr></table>
<table><tr><th>Task</th><th>Duration</th><th>Result</th></tr>
<tr><td>:logic</td><td>1m0s</td><td>(total)</td></tr>
<tr><td class="indentPath">:logic:compileKotlin</td><td>0.025s</td><td>FROM-CACHE</td></tr>
<tr><td class="indentPath">:logic:generate</td><td>0s</td><td>UP-TO-DATE</td></tr>
<tr><td class="indentPath">:logic:link</td><td>1m0s</td><td></td></tr>
<tr><td class="indentPath">:logic:test</td><td>1.200s</td><td></td></tr>
<tr><td class="indentPath">:logic:skip</td><td>0s</td><td>SKIPPED</td></tr>
<tr><td class="indentPath">:logic:empty</td><td>0s</td><td>NO-SOURCE</td></tr>
</table></body></html>"""


class CacheReportTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def collect(self, states=None, timings=None):
        return report.collect_report(self.root, timings or {}, states or {}, self.root / ".gradle", self.root / ".konan", "iOS logic")

    def test_profiles_keep_cached_executed_and_skipped_outcomes_separate(self):
        (self.root / "profile-one.html").write_text(PROFILE)
        result = self.collect()
        profile = result["profiles"][0]
        self.assertEqual(profile["summary"]["Total Build Time"], 62.5)
        self.assertEqual(len(profile["tasks"]), 6)  # Project totals are not tasks.
        self.assertEqual(profile["tasks"][0]["outcome"], "FROM-CACHE")
        self.assertEqual(profile["tasks"][2]["seconds"], 60)
        markdown = report.render_report(result)
        self.assertIn("| 62.500s | 0.250s | 1 | 1 | 2 | 2 |", markdown)
        self.assertIn("| :logic:link | 60.000s | EXECUTED |", markdown)
        self.assertIn("| :logic:test | 1.200s | EXECUTED |", markdown)
        self.assertNotIn("| :logic:compileKotlin |", markdown)

    def test_repeated_task_in_separate_invocations_is_not_overwritten(self):
        for name in ("profile-one.html", "profile-two.html"):
            (self.root / name).write_text(PROFILE)
        result = self.collect()
        self.assertEqual(len(result["profiles"]), 2)
        self.assertEqual(sum(len(profile["tasks"]) for profile in result["profiles"]), 12)

    def test_archive_lookup_distinguishes_fallback_cold_skipped_and_failed(self):
        cases = [
            ({"outcome": "success", "outputs": {"cache-hit": "true", "cache-matched-key": "exact"}}, "exact"),
            ({"outcome": "success", "outputs": {"cache-hit": "false", "cache-matched-key": "previous-sha"}}, "fallback"),
            ({"outcome": "success", "outputs": {"cache-hit": "false"}}, "cold"),
            ({"outcome": "skipped"}, "skipped"),
            ({"outcome": "failure"}, "failure"),
            ({}, "skipped"),
        ]
        for state, expected in cases:
            with self.subTest(expected=expected):
                self.assertEqual(report.restore_result(state), expected)

    def test_cache_layers_are_disjoint_and_do_not_follow_symlinks(self):
        files = {
            ".gradle/caches/modules-2/library.jar": b"deps",
            ".gradle/wrapper/distribution.zip": b"gradle",
            ".gradle/caches/build-cache-1/output": b"compiled",
            ".konan/compiler": b"native",
        }
        for relative, data in files.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "large").write_bytes(b"x" * 100)
        (self.root / ".gradle/caches/link").symlink_to(outside, target_is_directory=True)
        sizes = report.cache_sizes(self.root / ".gradle", self.root / ".konan")
        self.assertEqual(sizes, {"dependencies": (10, []), "outputs": (8, []), "konan": (6, [])})

    def test_missing_and_invalid_profiles_are_reported_as_unknown(self):
        (self.root / "profile-incomplete.html").write_text("<html>build was interrupted</html>")
        result = self.collect()
        self.assertEqual(len(result["warnings"]), 1)
        self.assertIn("task reuse is unknown", report.render_report(result))
        self.assertNotIn("| 0 |", report.render_report(result))

    def test_skipped_cache_does_not_claim_zero_bytes_were_restored(self):
        result = self.collect(states={"outputs": {"restore": {"outcome": "skipped"}, "save": None}})
        self.assertIsNone(result["caches"]["outputs"]["bytes"])
        self.assertIn("| outputs | skipped | skipped | unknown |", report.render_report(result))

    def test_failed_stage_is_visible_even_when_profile_records_an_execution(self):
        (self.root / "profile-one.html").write_text(PROFILE)
        result = report.collect_report(self.root, {}, {}, self.root / ".gradle", self.root / ".konan", "iOS", {"Tests": "failure"})
        markdown = report.render_report(result)
        self.assertIn("Tests: **failure**", markdown)
        self.assertIn("Executed includes failed executions", markdown)

    def test_elapsed_steps_require_both_markers(self):
        result = self.collect(timings={"restore-start": 10, "restore-end": 12.5, "save-start": 30})
        self.assertEqual(result["cache_step_seconds"], {"restore": 2.5, "save": None})

    def test_profile_text_and_cache_keys_cannot_inject_summary_markup(self):
        (self.root / "profile-one.html").write_text(PROFILE.replace(":logic:link", ":logic:&lt;img&gt;|`link"))
        result = self.collect(states={"outputs": {"restore": {
            "outcome": "success", "outputs": {"cache-matched-key": "<script>|`\ninjected"},
        }}})
        markdown = report.render_report(result)
        self.assertNotIn("<script>", markdown)
        self.assertNotIn("<img>", markdown)
        self.assertIn("&lt;img&gt;&#124;&#96;link", markdown)
        self.assertIn("&lt;script&gt;&#124;&#96; injected", markdown)
        self.assertEqual(report.cell("![image](https://example.com)"), "&#33;&#91;image&#93;&#40;https://example.com&#41;")

    def test_cli_appends_summary_and_writes_machine_readable_report(self):
        (self.root / "profile-one.html").write_text(PROFILE)
        summary = self.root / "summary.md"
        summary.write_text("Existing job summary\n")
        environment = {**os.environ, "RUNNER_TEMP": str(self.root), "GITHUB_STEP_SUMMARY": str(summary)}
        script = str(Path(report.__file__))
        for command in ("restore-start", "restore-end", "save-start", "summary"):
            subprocess.run([sys.executable, script, command, "--profiles", str(self.root), "--output", str(self.root / "report.json")],
                           env=environment, check=True, capture_output=True)
        result = json.loads((self.root / "report.json").read_text())
        self.assertEqual(len(result["profiles"]), 1)
        self.assertGreaterEqual(result["cache_step_seconds"]["restore"], 0)
        self.assertTrue(summary.read_text().startswith("Existing job summary\n"))
        self.assertIn("Slowest executed tasks", summary.read_text())

    def test_duration_accepts_hours_but_rejects_unknown_units(self):
        self.assertEqual(report.duration_seconds("1h2m3.5s"), 3723.5)
        with self.assertRaises(ValueError):
            report.duration_seconds("12ms")


if __name__ == "__main__":
    unittest.main()
