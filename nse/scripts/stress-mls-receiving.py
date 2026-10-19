#!/usr/bin/env python3
"""Stress real MLS/SQL receiving across two iOS simulator processes (no APNs/WS)."""

import argparse
import json
import subprocess
import tempfile
import time
from pathlib import Path

from ios_test_support import create_simulator, delete_simulator

REPO = Path(__file__).resolve().parents[2]
APP_FIXTURE = REPO / 'logic/build/bin/iosSimulatorArm64/swiftAppFixtureDebugExecutable/swift-app-verifier.kexe'
FIXTURE = REPO / 'domain/messaging/receiving/build/bin/iosSimulatorArm64/swiftFixtureDebugExecutable/swift-receiving-fixture.kexe'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true', help='Use binaries already rebuilt from current sources')
    parser.add_argument('--simulator', help='Booted, disposable simulator UDID to reuse; by default a temporary one is created')
    parser.add_argument('--case', choices=['before-commit', 'after-commit', 'app-first', 'nse-first', 'app-resume'],
                        help='Run only cases with this ownership/checkpoint order')
    parser.add_argument('--messages', type=int, default=200)
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--report', type=Path, default=REPO / 'nse/build/reports/mls-stress/result.json')
    args = parser.parse_args()
    if not 1 <= args.messages <= 5000 or not 1 <= args.rounds <= 100:
        parser.error('messages must be 1..5000 and rounds 1..100')
    if not args.skip_build:
        subprocess.run(['./gradlew',
                        ':domain:messaging:receiving:linkSwiftFixtureDebugExecutableIosSimulatorArm64',
                        ':logic:linkSwiftAppFixtureDebugExecutableIosSimulatorArm64', '--console=plain'],
                       cwd=REPO, check=True, timeout=1800)
    temporary_simulator = args.simulator is None
    if temporary_simulator:
        args.simulator, runtime = create_simulator('Kalium MLS stress')
        print(f'Running on temporary simulator {args.simulator} ({runtime})', flush=True)
    try:
        if temporary_simulator:
            subprocess.run(['xcrun', 'simctl', 'bootstatus', args.simulator, '-b'], check=True, capture_output=True)
        stress(args)
    finally:
        if temporary_simulator:
            delete_simulator(args.simulator)


def stress(args):
    args.report.parent.mkdir(parents=True, exist_ok=True)
    logs = args.report.parent / 'logs'
    logs.mkdir(exist_ok=True)
    results = []
    sequence = 0

    def command(*parts):
        return ['xcrun', 'simctl', 'spawn', args.simulator, str(FIXTURE), 'stress', *map(str, parts)]

    def run(*parts, expected=None):
        nonlocal sequence
        sequence += 1
        output = subprocess.run(command(*parts), capture_output=True, text=True, timeout=120)
        log = logs / f'{sequence:04d}-{parts[0]}.log'
        log.write_text(output.stdout + output.stderr)
        if output.returncode or (expected and expected not in output.stdout):
            raise RuntimeError(f'{parts[0]} failed; see {log}')
        return output.stdout

    # The ready barrier is inside the actual native transaction, not a timing assumption.
    cases = [('before-commit', 'NONE'), ('after-commit', 'NONE'),
             ('before-commit', 'BEFORE_COMMIT'), ('after-commit', 'AFTER_COMMIT'),
             ('after-commit', 'MARKER'), ('app-first', 'NONE'), ('nse-first', 'NONE'), ('app-resume', 'NONE')]
    if args.case:
        cases = [case for case in cases if case[0] == args.case]
    try:
        with tempfile.TemporaryDirectory(prefix='wire-mls-stress-') as temporary:
            work = Path(temporary)
            for round_number in range(args.rounds):
                for stage, interruption in cases:
                    label = f'{round_number}-{stage}-{interruption}'
                    manifest = work / f'{label}.json'
                    gate = work / label
                    started = time.monotonic()
                    run('prepare', manifest, args.messages, expected='MLS_STRESS_PREPARED')
                    holder = None
                    try:
                        with (logs / f'{label}-holder.log').open('w') as log:
                            if stage == 'nse-first':
                                run('receive', manifest, 'none', gate, 'NONE', expected='MLS_STRESS_RECEIVED')
                            holder_command = (['xcrun', 'simctl', 'spawn', args.simulator, str(APP_FIXTURE),
                                               str(manifest), 'stress', str(gate), stage] if stage in ('app-first', 'nse-first', 'app-resume')
                                              else command('receive', manifest, stage, gate, interruption))
                            holder = subprocess.Popen(holder_command, stdout=log, stderr=log)
                            deadline = time.monotonic() + 90
                            while not Path(f'{gate}.ready').exists():
                                if holder.poll() is not None or time.monotonic() > deadline:
                                    raise RuntimeError(f'{label}: holder failed to reach transaction barrier')
                                time.sleep(.05)
                            if stage == 'app-resume':
                                run('receive', manifest, 'none', gate, 'NONE', expected='MLS_STRESS_RECEIVED')
                            else:
                                for _ in range(5):
                                    run('receive', manifest, 'none', gate, 'NONE', expected='MLS_STRESS_BUSY')
                            Path(f'{gate}.release').touch()
                            if holder.wait(timeout=120):
                                raise RuntimeError(f'{label}: holder failed; see holder log')
                        expected = ('MLS_STRESS_APP_RECEIVED' if stage in ('app-first', 'nse-first', 'app-resume') else
                                    'MLS_STRESS_RECEIVED' if interruption == 'NONE' else 'MLS_STRESS_INTERRUPTED')
                        if expected not in (logs / f'{label}-holder.log').read_text():
                            raise RuntimeError(f'{label}: missing holder outcome')
                        if stage in ('nse-first', 'app-resume') and (
                            'MLS_STRESS_APP_VISIBLE_BEFORE_SYNC' not in (logs / f'{label}-holder.log').read_text()
                        ):
                            raise RuntimeError(f'{label}: app did not confirm stored messages before sync')
                        run('cleanup-trigger', manifest)
                        # A fresh process retries the interrupted event, then another replays it.
                        for _ in range(2):
                            run('receive', manifest, 'none', gate, 'NONE', expected='MLS_STRESS_RECEIVED')
                        run('verify', manifest, stage, expected='MLS_STRESS_VERIFIED')
                        result = dict(case=label, messages=args.messages, commits=(args.messages + 9) // 10,
                                      busyAttempts=0 if stage == 'app-resume' else 5, seconds=round(time.monotonic() - started, 2), passed=True)
                        results.append(result)
                        print(json.dumps(result), flush=True)
                    finally:
                        Path(f'{gate}.release').touch()
                        if holder is not None and holder.poll() is None:
                            # Native barrier has its own 60 second timeout; allow it to release resources.
                            holder.wait(timeout=90)
                        run('cleanup', manifest)
    finally:
        args.report.write_text(json.dumps(dict(
            scope='Independent iOS simulator processes: NSE NotificationProcessor and main CoreLogic/EventProcessor. Real MLS/SQL, deterministic injected events; no live WS or APNs.',
            expectedCases=args.rounds * len(cases), completedCases=len(results), cases=results,
        ), indent=2) + '\n')
    print(f'PASS {len(results)} cases, {len(results) * args.messages} messages; {args.report}', flush=True)


if __name__ == '__main__':
    main()
