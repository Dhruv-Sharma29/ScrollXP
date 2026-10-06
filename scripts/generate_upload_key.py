#!/usr/bin/env python3
"""Create one private local upload key; refuse to replace an existing identity."""
import os
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / ".private" / "release"
KEYTOOL = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin" / "keytool"


def main():
    PRIVATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(PRIVATE.parent, 0o700)
    os.chmod(PRIVATE, 0o700)
    key = PRIVATE / "upload-key.p12"
    settings = PRIVATE / "signing.properties"
    if key.exists() or settings.exists():
        sys.exit("Signing material already exists. Reuse it; this script never replaces a key.")
    if not KEYTOOL.is_file():
        sys.exit("Set JAVA_HOME to a JDK containing keytool.")
    password = secrets.token_hex(32)
    environment = dict(os.environ, SCROLLXP_UPLOAD_PASSWORD=password)
    old_mask = os.umask(0o077)
    try:
        result = subprocess.run([str(KEYTOOL), "-genkeypair", "-keystore", str(key),
            "-storetype", "PKCS12", "-storepass:env", "SCROLLXP_UPLOAD_PASSWORD",
            "-keypass:env", "SCROLLXP_UPLOAD_PASSWORD", "-alias", "scrollxp-upload",
            "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
            "-dname", "CN=ScrollXP Upload,OU=Android,O=ScrollXP", "-noprompt"],
            env=environment, capture_output=True)
        if result.returncode:
            sys.exit("Key generation failed. No credentials were printed; inspect the JDK setup.")
        with settings.open("x") as file:
            file.write("storeFile=.private/release/upload-key.p12\n")
            file.write(f"storePassword={password}\nkeyAlias=scrollxp-upload\nkeyPassword={password}\n")
        certificate = ROOT / "release" / "upload-certificate.pem"
        result = subprocess.run([str(KEYTOOL), "-exportcert", "-rfc", "-keystore", str(key),
            "-storepass:env", "SCROLLXP_UPLOAD_PASSWORD", "-alias", "scrollxp-upload",
            "-file", str(certificate)], env=environment, capture_output=True)
        if result.returncode:
            sys.exit("Key was created, but public certificate export failed. Keep the private key.")
    finally:
        os.umask(old_mask)
    print("Upload key created in .private/release with owner-only permissions.")
    print("Public certificate: release/upload-certificate.pem")
    print("Keep a secure backup of the key and signing.properties before publishing.")


if __name__ == "__main__":
    main()
