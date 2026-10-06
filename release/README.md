# ScrollXP release preparation

This folder contains public release materials. The prepared store-material ZIP is `outputs/release/ScrollXP-store-materials.zip`; signed binaries and their validation record are alongside it. The release is not published. Real-phone validation remains deferred.

## Build a signed APK and AAB

The current local upload key and passwords are in `.private/release/`, excluded from Git and restricted to the file owner. The public upload certificate is `release/upload-certificate.pem`. Keep a secure backup of both private files before your first publication; the key must be reused for later uploads. Do not put it in Git, a store-artwork ZIP, or chat.

```sh
# Only for a new checkout with no existing upload key:
python3 scripts/generate_upload_key.py

# Builds, verifies signatures, and packages artifacts:
python3 scripts/build_release.py
```

Set JAVA_HOME and ANDROID_SDK_ROOT if using other JDK/SDK locations. The generator refuses to overwrite existing signing files. To use an existing upload key, set storeFile, storePassword, keyAlias, and keyPassword in `.private/release/signing.properties` instead. Gradle reads this optional local file; debug builds do not need it. Direct release Gradle builds without this file are unsigned; the packaging script refuses to proceed without it.

The build script disables Gradle's configuration cache during signing and verifies the APK with apksigner and AAB with jarsigner. Public APK/AAB, checksums, signature report, and metadata go to `outputs/release/`; private material is not copied. Treat these as candidate release artifacts until the remaining validation is complete.

The APK has a separate release certificate from the debug APK and cannot replace a debug installation in place. Validate it on a separate emulator/device; avoid uninstalling the debug build if its local island needs preserving. Google Play App Signing has not been configured. Its final app-signing certificate may differ from this upload certificate. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

## Public materials

- `STORE_LISTING.md`: name, short/full description, contact, and asset inventory.
- `policy/privacy-policy.md`: authoritative policy text, also packaged as an offline app asset.
- `privacy/index.html`: self-contained HTML for public policy hosting. No hosting URL is active yet.
- `privacy/delete-account.html`: external account-deletion request page prepared for hosting.
- `DATA_SAFETY.md`: implementation-based worksheet and pending Play Console steps.
- `PLAY_BILLING_SETUP.md`: subscription product/licensing setup, client-verification limits and required purchase tests.
- `assets/`: supplied palm-island screenshot, transparent production logo, Play icon, feature graphic, and five real 1080×1920 RGB app screenshots. The compatibility SVG embeds the PNG artwork. See `assets/LOGO.md` for the built-in imagegen method and retained prompt.

Regenerate launcher resources, store artwork, and policy HTML after editing the policy or replacing `assets/scrollxp-logo-mark.png`:

```sh
/Users/dhruvsharma/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 scripts/prepare_release_assets.py
```

This generator requires Pillow and uses system Arial/Georgia fonts on this Mac. Android foreground and themed layers use packaged PNGs with a cream vector background. The complete logo stays inside the adaptive safe area; the themed layer is selected only on API 33+. The login page uses the same mark. Gameplay island artwork is independent of branding.

Release optimization uses R8 across the whole app. The template's experimental package-only scope caused a Kotlin collection access error during AndroidX startup in the first signed emulator run; removing that scope resolved the cold-launch crash. See the [official packageScope guidance](https://developer.android.com/topic/performance/app-optimization/optimize-specified-packages) for the distinction. The final signed APK is checked separately from the debug test suite.

## Remaining publication work

Email/Password is confirmed enabled. See `VALIDATION.md` for the latest rules deployment and test evidence. Real-account backup/deletion/circle checks, public policy/deletion hosting, Play product/public-key setup, real subscription lifecycle testing, Play Console declarations, target audience/content rating and circle abuse/moderation review, physical-device tracking/battery checks, beta feedback and release approval remain. Current builds enable email accounts with the matching JSON; `-Pscrollxp.onlineEnabled=false` creates an offline-account build. Pro is separately controlled by the public Play licensing key; checkout is currently disabled. No Cloud Functions or Blaze upgrade is needed. The signing scripts do not publish the app.
