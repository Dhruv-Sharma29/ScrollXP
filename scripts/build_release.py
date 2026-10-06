#!/usr/bin/env python3
"""Build signed artifacts, verify signatures, and copy a public release package."""
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", Path.home() / "Library/Android/sdk"))
OUTPUT = ROOT / "outputs/release"


def main():
    settings = ROOT / ".private/release/signing.properties"
    if not settings.is_file():
        sys.exit("No upload signing configuration. Run python3 scripts/generate_upload_key.py or configure your existing key.")
    # Do not serialize private signing credentials into Gradle's configuration cache.
    subprocess.run([str(ROOT / "gradlew"), ":app:assembleRelease", ":app:bundleRelease",
        ":app:lintRelease", "--no-configuration-cache", "--console=plain"], cwd=ROOT,
        env=dict(os.environ, JAVA_HOME=str(JDK)), check=True)
    apk = ROOT / "app/build/outputs/apk/release/app-release.apk"
    bundle = ROOT / "app/build/outputs/bundle/release/app-release.aab"
    apksigners = sorted(SDK.glob("build-tools/*/apksigner"), reverse=True)
    if not apksigners: sys.exit("No Android apksigner found; signature verification cannot finish.")
    signature = subprocess.run([str(apksigners[0]), "verify", "--verbose", "--print-certs", str(apk)],
        env=dict(os.environ, JAVA_HOME=str(JDK)), capture_output=True, text=True, check=True)
    bundle_check = subprocess.run([str(JDK / "bin/jarsigner"), "-verify", str(bundle)],
        capture_output=True, text=True, check=True)
    if "jar verified." not in bundle_check.stdout: sys.exit("AAB signature was not verified.")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    checksums = {}
    for source, name in [(apk,"ScrollXP-release.apk"),(bundle,"ScrollXP-release.aab")]:
        target = OUTPUT / name; shutil.copy2(source,target)
        checksums[name] = hashlib.sha256(target.read_bytes()).hexdigest()
    (OUTPUT / "SHA256SUMS.txt").write_text(''.join(f'{checksum}  {name}\n' for name,checksum in checksums.items()))
    (OUTPUT / "SIGNATURE.txt").write_text(signature.stdout + '\nAAB verification:\n' + bundle_check.stdout)
    badging = subprocess.run([str(apksigners[0].parent / "aapt2"), "dump", "badging", str(apk)],
        capture_output=True, text=True, check=True).stdout
    def field(pattern):
        match = re.search(pattern, badging)
        if not match: sys.exit("Cannot read final APK metadata.")
        return match.group(1)
    (OUTPUT / "ARTIFACTS.json").write_text(json.dumps({"package":field(r"package: name='([^']+)'"),
        "versionCode":int(field(r"versionCode='([^']+)'")),"versionName":field(r"versionName='([^']+)'"),
        "minSdk":int(field(r"minSdkVersion:'([^']+)'")),"targetSdk":int(field(r"targetSdkVersion:'([^']+)'")),
        "signing":"local upload certificate; not Google Play's app signing certificate",
        "sha256":checksums},indent=2)+'\n')
    print("Verified signed APK and AAB copied to outputs/release. Private signing files were not copied.")


if __name__ == "__main__":main()
