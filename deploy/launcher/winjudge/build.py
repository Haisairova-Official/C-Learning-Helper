"""Add the project Windows execution adapter to the pinned upstream module without changing the Go cache."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
MODULE = 'github.com/criyle/go-judge@v1.14.0'


def build(destination):
    env = dict(os.environ, GOOS='windows', GOARCH='amd64', CGO_ENABLED='0', GOWORK='off')
    module = json.loads(subprocess.check_output(['go', 'mod', 'download', '-json', MODULE], env=env))
    with tempfile.TemporaryDirectory() as tmp:
        stage = Path(tmp)
        source = shutil.copytree(module['Dir'], stage / 'judge', copy_function=shutil.copyfile)
        (source / "env/winc").chmod(0o755)
        shutil.copyfile(HERE / 'appcontainer_windows.go', source / 'env/winc/appcontainer_windows.go')

        def read(name, expected):
            original = source / name
            body = original.read_bytes().replace(b'\r\n', b'\n')
            if hashlib.sha256(body).hexdigest() != expected:
                raise RuntimeError('Unexpected upstream Windows source: ' + name)
            return original, body.decode()

        target, code = read('env/env_windows.go', '0231ba7fd51e0d5de5c16c546f9d94b7f45024dca1b2e8d4034f57e0fb20d94c')
        target.write_text(code.replace('winc.NewBuilder("")','winc.NewIsolatedBuilder()').replace(
            'map[string]any{}', 'map[string]any{"windowsIsolation": "appcontainer-job-v1"}'), encoding='utf-8')

        target, code = read('env/winc/environment_windows.go', '60e850394f2624d9514d679dce637b9d24fcfa93b9302093c6d5b0465f51de80')
        code = code.replace('"crypto/rand"', '').replace('"encoding/hex"', '').replace('"fmt"', '')
        code = code.replace('type Environment struct {', 'type Environment struct {\n profileName *uint16\n packageSID *windows.SID')
        code = code.replace('joinExeDirAndFName(e.root, param.Args[0])', 'e.nativeCommand(&param)')
        code = code[:code.index('// create desktop')] + code[code.index('// create job object'):]
        code = code[:code.index('cmdLine := makeCmdLine')] + (
            'var processInfo windows.ProcessInformation\n err = e.startNative(param, argv0, &processInfo)\n\n'
        ) + code[code.index('if processInfo.Process > 0'):]
        code = code.replace('syscall.CloseHandle(processInfo.', 'windows.CloseHandle(processInfo.')
        code = code.replace('handle(func() { windows.CloseHandle(processInfo.Process) })',
                            'handle(func() { windows.TerminateProcess(processInfo.Process, 1); windows.CloseHandle(processInfo.Process) })')
        code = code.replace('defer CloseDesktop(newDesk)', '').replace('if exitCode != 0 {', 'if exitCode != 0 && result.Status == runner.StatusNormal {')
        code = code.replace('fullPath := filepath.Join(e.root, param.Path)', 'if param.Path == "main" { param.Path = "main.exe" }; fullPath := filepath.Join(e.root, param.Path)')
        code = code.replace('// error is ignorable for destroy operation', 'defer e.removeProfile()')
        begin = code.index('if err := removeContents(e.tmp)')
        code = code[:begin] + 'return errors.New("single-use AppContainer")\n}\n\n' + code[code.index('// removeContents', begin):]
        target.write_text(code, encoding='utf-8')
        subprocess.run(['go','build','-trimpath','-o',str(destination.resolve()),'./cmd/go-judge'],
                       check=True,cwd=source,env=env)
        shutil.copyfile(source / 'LICENSE', destination.parent / 'go-judge-LICENSE.txt')


if __name__ == '__main__':
    build(Path(sys.argv[1]))
