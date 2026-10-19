#!/usr/bin/env python3
# Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
"""Check which CI events may read or write caches and override Gradle defaults."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


POLICY = Path(__file__).with_name("cache-policy.sh")


class CachePolicyTest(unittest.TestCase):
    def evaluate(self, **overrides):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            environment = Path(directory) / "environment"
            inputs = {
                "EVENT_NAME": "",
                "REF": "",
                "REPOSITORY": "wireapp/kalium",
                "PR_BASE_REF": "",
                "PR_HEAD_REPOSITORY": "",
                "MERGE_GROUP_BASE_REF": "",
                "GRADLE_OPTS": "",
                **overrides,
            }
            result = subprocess.run(
                ["bash", str(POLICY)],
                env={
                    **os.environ,
                    **inputs,
                    "GITHUB_OUTPUT": str(output),
                    "GITHUB_ENV": str(environment),
                },
                capture_output=True,
                text=True,
            )
            return (
                result,
                dict(line.split("=", 1) for line in output.read_text().splitlines()) if output.exists() else {},
                environment.read_text() if environment.exists() else "",
            )

    def assert_policy(self, enabled=False, read_only=False, **event):
        result, outputs, environment = self.evaluate(**event)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(outputs, {"enabled": str(enabled).lower(), "read_only": str(read_only).lower()})
        self.assertTrue(environment.endswith(f"-Dorg.gradle.caching={str(enabled).lower()}\n"))

    def test_develop_push_can_restore_and_save(self):
        self.assert_policy(enabled=True, EVENT_NAME="push", REF="refs/heads/develop")

    def test_same_repository_pr_to_develop_can_restore_and_save(self):
        self.assert_policy(
            enabled=True,
            EVENT_NAME="pull_request",
            PR_BASE_REF="develop",
            PR_HEAD_REPOSITORY="wireapp/kalium",
        )

    def test_develop_merge_queue_is_read_only(self):
        self.assert_policy(enabled=True, read_only=True, EVENT_NAME="merge_group", MERGE_GROUP_BASE_REF="refs/heads/develop")

    def test_untrusted_or_unrelated_runs_disable_caching(self):
        cases = [
            {"EVENT_NAME": "pull_request", "PR_BASE_REF": "develop", "PR_HEAD_REPOSITORY": "fork/kalium"},
            {"EVENT_NAME": "pull_request", "PR_BASE_REF": "release/test", "PR_HEAD_REPOSITORY": "wireapp/kalium"},
            {"EVENT_NAME": "merge_group", "MERGE_GROUP_BASE_REF": "refs/heads/release/test"},
            {"EVENT_NAME": "push", "REF": "refs/heads/feature"},
            {"EVENT_NAME": "push", "REF": "refs/tags/v1.2.3"},
            {"EVENT_NAME": "push", "REF": "refs/tags/v1.2.3-rc1"},
            {"EVENT_NAME": "push", "REF": "refs/tags/backup/1.2.3"},
            {"EVENT_NAME": "workflow_dispatch", "REF": "refs/heads/develop"},
            {"EVENT_NAME": "workflow_dispatch", "REF": "refs/tags/v1.2.3"},
            {"EVENT_NAME": "pull_request_target", "REF": "refs/heads/develop"},
            {"EVENT_NAME": "workflow_run", "REF": "refs/heads/develop"},
            {},
        ]
        for event in cases:
            with self.subTest(event=event):
                self.assert_policy(**event)

    def test_release_overrides_an_inherited_cache_enable_flag(self):
        result, _, environment = self.evaluate(
            EVENT_NAME="push",
            REF="refs/tags/v1.2.3",
            GRADLE_OPTS='-Dorg.gradle.caching=true -Dorg.gradle.workers.max=2 -Dorg.gradle.jvmargs="-Xmx4g -XX:+UseParallelGC"',
        )
        self.assertEqual(result.returncode, 0)
        self.assertEqual(
            environment,
            'GRADLE_OPTS=-Dorg.gradle.caching=true -Dorg.gradle.workers.max=2 '
            '-Dorg.gradle.jvmargs="-Xmx4g -XX:+UseParallelGC" -Dorg.gradle.caching=false\n',
        )

    def test_empty_repository_names_cannot_enable_pr_caching(self):
        self.assert_policy(EVENT_NAME="pull_request", PR_BASE_REF="develop", REPOSITORY="", PR_HEAD_REPOSITORY="")

    def test_environment_injection_is_rejected(self):
        for newline in ["\n", "\r"]:
            with self.subTest(newline=repr(newline)):
                result, outputs, environment = self.evaluate(GRADLE_OPTS=f"-Xmx4g{newline}OTHER=value")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("GRADLE_OPTS must not contain line breaks", result.stdout)
                self.assertEqual(outputs, {})
                self.assertEqual(environment, "")


if __name__ == "__main__":
    unittest.main()
