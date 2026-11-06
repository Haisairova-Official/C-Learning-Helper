#!/usr/bin/env python3
"""Run real C17/C++17 function/program tests through the existing restricted judge, without restarting services."""
import os
import pathlib
import subprocess

project = pathlib.Path(__file__).resolve().parents[2]
settings = pathlib.Path('/tmp') / f'cassist-judge-{os.getuid()}' / 'judge.env'
environment = os.environ.copy()
environment.update(line.split('=', 1) for line in settings.read_text().splitlines() if line and not line.startswith('#'))
environment['CASSIST_JUDGE_INTEGRATION'] = 'true'
result = subprocess.run(['./mvnw', '-B', '-ntp', '-Dtest=FunctionJudgeIntegrationTest', 'test'],
                        cwd=project / 'backend', env=environment, check=False)
raise SystemExit(result.returncode)
