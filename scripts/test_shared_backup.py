#!/usr/bin/env python3
"""Android SDK ↔ native Swift REST backup acceptance, using demo Firebase emulators only.
Requires a booted Android emulator; never changes the default Firebase session or game database.
"""
from pathlib import Path
import argparse, json, os, shlex, subprocess, sys, tempfile, urllib.request
ROOT = Path(__file__).resolve().parents[1]
PASSWORD = 'Emulator-only-123!'
def run(*command, **kwargs):
    subprocess.run([str(part) for part in command], check=True, **kwargs)
def worker(binary):
    def auth(path, body, admin=False):
        request = urllib.request.Request('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/' + path + '?key=demo-key',
            json.dumps(body).encode(), {'Content-Type': 'application/json', **({'Authorization': 'Bearer owner'} if admin else {})})
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    user = auth('accounts:signUp', {'email': 'shared-backup@example.test', 'password': PASSWORD, 'returnSecureToken': True})
    auth('accounts:update', {'localId': user['localId'], 'emailVerified': True}, admin=True)
    environment = dict(os.environ)
    environment['JAVA_HOME'] = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    def android(phase):
        log = Path('/tmp') / ('ScrollXP-backup-interop-' + phase + '.log')
        with log.open('w') as output:
            result = subprocess.run([str(ROOT/'gradlew'), ':app:connectedDebugAndroidTest',
                '-Pandroid.testInstrumentationRunnerArguments.class=com.scrollxp.app.CloudBackupInteropTest',
                '-Pandroid.testInstrumentationRunnerArguments.cloudEmulators=true',
                '-Pandroid.testInstrumentationRunnerArguments.backupPhase=' + phase], cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT)
        if result.returncode:
            print(log.read_text()[-14000:]); raise SystemExit(result.returncode)
        print('PASS: Android SDK ' + phase + ' phase.', flush=True)
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home()/'Library/Android/sdk')))
    for port in (9099, 8180):
        run(sdk/'platform-tools/adb', '-s', 'emulator-5554', 'reverse', 'tcp:' + str(port), 'tcp:' + str(port))
    android('produce')
    run(binary, 'exchange', ROOT/'backend/fixtures/cross-platform/island-v1.json')
    android('consume')
    run(binary, 'verify')
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ios', type=Path, default=ROOT.parent/'ScrollXP iOS')
    parser.add_argument('--worker', type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.worker: return worker(args.worker)
    with tempfile.TemporaryDirectory(prefix='ScrollXP-backup-interop-') as temp:
        binary = Path(temp)/'native-backup-tests'
        run('swiftc', '-module-cache-path', Path(temp)/'modules', '-parse-as-library', '-swift-version', '5', '-strict-concurrency=complete',
            args.ios/'Shared/IslandCore.swift', args.ios/'Shared/PortableIslandBackup.swift', args.ios/'ScrollXP iOS/OnlineService.swift',
            args.ios/'Tests/Integration/CloudBackupInteropTests.swift', '-o', binary)
        command = shlex.join([sys.executable, str(Path(__file__).resolve()), '--worker', str(binary)])
        run(ROOT/'backend/node_modules/.bin/firebase', 'emulators:exec', '--project', 'demo-scrollxp', '--only', 'auth,firestore', command, cwd=ROOT/'backend')
if __name__ == '__main__': main()
