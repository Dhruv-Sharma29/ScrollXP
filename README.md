# ScrollXP

An Android screen-time companion with a customizable island, capped XP, and daily budget goals.

## First development build

Implemented:

- A calm entry page with optional Firebase Spark email signup/login, verification, recovery, sign-out, and reauthenticated account deletion. Login is enabled by default with the matching local configuration; the island can still be used without an account.
- Compose onboarding and selection of up to three installed launcher apps.
- Usage Access explanation and Android settings handoff.
- Foreground-activity estimates, refreshed on return and through a 15-minute WorkManager request.
- Room persistence for preferences, daily history, tracking pauses, and reward records.
- Two XP per eligible minute, capped at 120 usage XP per day.
- Full-day budget evaluation with a 100-XP bonus and explicit unknown/partial results.
- Home, World, Goals, Stats, and Settings screens.
- Three illustrated destinations: Meadow haven, Seaside cove (5,000 lifetime XP), and Cloud sanctuary (10,000). Unlocks are permanent; visiting never spends XP and keeps cottage/treasure placement.
- An optional four-step first-chapter guide, with saved dismissal and a Settings option to show it again; onboarding includes interactive destination previews.
- An original vector island with 20 collectible items: seven XP items and 13 chest cosmetics, three roof styles, and show/hide customization.
- A welcome chest and chests at 100, 300, 600, 1,200, 2,500, 5,000, and 10,000 lifetime XP.
- A Monday–Sunday balance quest: five completed daily budgets earn one chest; days need not be consecutive.
- Stored chest contents, duplicate protection including unopened reservations, disclosed base rarity odds, and keepsake stars after the chest collection is reserved or complete.
- Reveal/placement choices, an Owned/To discover collection filter, recent discoveries, and optional haptics.
- Warm Daylight, Dusk, and System themes, with matching island scenery and saved reduced-motion controls.
- Illustrated treasure cards, locked-item previews, next-unlock progress, placement haptics, and saved-XP feedback.
- Collection cards adapt to larger text; theme and roof choices wrap instead of clipping.
- App and budget changes scheduled for the next day.
- Tracking pause, personality options, and local data reset.
- Opt-in silent daily reminders with a chosen time, editable quiet hours, permission handling, and persisted duplicate protection.
- Island postcards as 1080 × 1350 PNGs through Android Share or Save; level/XP inclusion is optional and off by default.
- Cloud and device-transfer backup exclusions for local records.
- A finalized adaptive/themed launcher icon, offline privacy policy available before setup and in Settings, and public store/policy materials under `release/`.
- Private upload-key signing, verified candidate release APK/AAB packaging, and separate release-emulator validation.

The current build uses Firebase Spark for email accounts, direct manual island backups and private seven-day friend balance circles. Circles share chosen names and manually published completed-budget counts, with six members and three circles per account. Scores are self-reported, without server anti-cheat or XP/cash prizes. The widget, weekly balance detail, 30-day local history, backups and circles stay free.

Optional Pro uses Google Play Billing 9.1.0 for three extra palettes and a 90-day local journal. Completed signed receipts are checked and acknowledged on the device; no purchase data or entitlement is stored in Firebase. Checkout remains disabled until the Play product and public licensing key are configured. No Cloud Functions, Firebase billing account or AI is used. See [backend setup](backend/README.md) and [Play setup and limitations](release/PLAY_BILLING_SETUP.md).

Still pending: Play product/key setup and real purchase lifecycle tests, real-account cloud/circle tests, public privacy/deletion pages, circle abuse/moderation review, physical-device validation and publication. See [PLAN.md](PLAN.md).

## Development

The iPhone/iPad SwiftUI project lives separately beside this repository in [ScrollXP iOS](../ScrollXP%20iOS/README.md). Open `../ScrollXP iOS/ScrollXP iOS.xcodeproj`. It includes the island, rewards, Screen Time extensions, widget, reminders, manual exports, optional email login, and private friend competitions shared with Android. Cloud island backup and StoreKit purchases remain unfinished. See the separate project’s `VALIDATION.md` for test results and physical-device requirements.

Open this folder as a project in Android Studio and let Gradle sync finish. The project uses Kotlin, Compose, Room, and WorkManager. The minimum supported Android version is API 26.

From the project folder on this Mac:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

To run device tests with a connected emulator or Android phone:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. This is a development build, not a store release.

## Trying the app

1. Run the app from Android Studio or install the debug APK on an Android phone.
2. Choose **Continue on this device**, then create an island and select up to three installed apps (or resume your saved island).
3. Tap **Connect Usage Access**, read the explanation, and enable access for ScrollXP in Android Settings.
4. Use a selected app, return to ScrollXP, and tap **Refresh**.
5. Open your welcome chest, reveal its cosmetic, and choose **Place on island** or **Keep for later**. Visit World to filter the collection, change the roof style, and hide/show owned items.
6. Open **Settings → Visual comfort** to choose Daylight, Dusk, or System, and enable Reduce motion if preferred.
7. Open **Settings → Daily island reminder** to opt in, grant notification permission, and choose a time and quiet hours. Tracking pauses also pause reminder delivery.
8. Open **World → Share my island** to preview the artwork, optionally include level/XP, and share or save a PNG.
9. In **World → New horizons**, preview later destinations and visit them after earning their XP requirement.
10. Follow the optional **Your first chapter** guide on Home. Tuck it away anytime, or restore it from Settings.
11. A budget challenge needs a full eligible day. Setup days and days with missing or paused tracking do not receive a goal bonus.

## Measurement limits

- Measures selected apps' foreground activity; it cannot count Reels, Shorts, watched videos, or gestures.
- Uses a last-resumed-activity estimate. Split-screen, picture-in-picture, manufacturer behavior, and incomplete system event logs need physical-device validation.
- Background work is best-effort and is not a live XP timer.
- A reconciliation gap longer than 36 hours conservatively marks affected days partial. Android may retain too little history to recover longer gaps.
- Coverage is a best-effort estimate, not proof of continuous usage access. Public competitive rankings are therefore excluded.
- Challenges use the timezone saved at setup, displayed in Goals. Automatic timezone migration is deferred so rewarded day windows do not overlap.
- Already-earned usage XP remains permanent when later reconciliation changes a usage estimate.
- History starts when tracking is configured and access is detected; historical usage from before enrollment does not earn XP.

## Reminder behavior

Reminders are off by default. They follow the phone's current timezone, separately from the frozen timezone used for budget goals. If a chosen time falls in quiet hours, the reminder moves to the end of quiet hours. Android clock/timezone changes replan the pending reminder.

WorkManager scheduling is best-effort; Android battery restrictions, Doze, and force-stopping the app can delay or prevent delivery. Reminders delayed more than six hours are skipped. A persisted receipt limits delivery to once per local date and at least 20 hours apart across clock/timezone changes. Notifications contain generic island/goal text and never selected-app names or usage numbers. Permission and tracking-pause state are checked at delivery.

## Privacy

The configured Spark build enables optional accounts, direct backups and private circles, with Internet permission. Play Billing adds BILLING permission; Functions remain absent. No analytics, ads or AI service is included. Selected-app aggregates remain on the phone; explicit backups contain the game profile, dated XP ledger and chests. Circles share chosen names/UIDs and confirmed balance counts, without app names or daily dates. Account deletion removes memberships and atomically removes the backup with an immutable UID/timestamp guard before deleting the Auth identity. Interrupted deletion can be retried. Local reset and Play cancellation remain separate. Postcards include only island art/name and optional level/XP, with a scoped FileProvider grant; reset clears local preferences, cached exports and reminder records but not saved images, online data or Android's own usage logs.

## Verification

The current build passes 50 JVM tests, 38 Android emulator tests and 20 local Firebase cases. New checks cover signed receipt tampering, Pro/score eligibility, unavailable checkout, invitation validation, private rosters, circle capacity/concurrent joins and deletion after lost verification. Current counts and signed runtime results are recorded in [release/VALIDATION.md](release/VALIDATION.md); prior results below describe earlier release checks. Real-project cloud/circle and Play purchase flows remain to be validated. Use `-Pscrollxp.onlineEnabled=false` only for an intentionally offline-account build.

The development build has domain tests for interval replay, activity transitions, day clipping, locks/reboots, pause exclusions, reward caps, level boundaries, budget eligibility, chest rarity boundaries, duplicate protection, and calendar weeks. Emulator persistence tests cover reward uniqueness, transactional rollback, reset, and upgrading the original database without losing island customization, rewards, or history. Theme and motion preferences are checked across database reopening. Reward tests cover concurrent milestone settlement, repeated opening, restart persistence, nonconsecutive weekly successes, future-date exclusion, complete-collection fallback, and version 2-to-3 migration.

Verified on 5 October 2026: debug APK build, 49 unit tests, 35 emulator tests, and Android lint with zero errors. Earlier live tracking validation measured approximately 1 minute 57 seconds in Chrome and awarded 2 XP. The version 4 upgrade preserves this XP, the placed lantern, and the existing preferences, while adding the saved destination and guide state. Migration tests also distinguish previously placed cosmetics from hidden or unopened rewards. Chest odds/reveal, cosmetic placement, the 20-item collection, and weekly progress are inspected in the emulator. Additional tests cover reminder quiet-hour boundaries, daylight-saving transitions, clock rollback, timezone travel, persisted/concurrent delivery receipts, preference reset, postcard rendering, and PNG FileProvider access. Android notification denial/approval, background delivery, next-day recurrence, and the native Share/Save flows are inspected in the emulator. New tests verify destination unlock boundaries, locked previews, visiting/revisiting without XP spending, guide dismissal/restoration persistence, and guest onboarding at 1.5× text size. All three destinations render and export distinct Daylight/Dusk scenery; their six PNG previews were visually inspected. Live emulator checks confirm the migrated guide shows 3/4 steps, and locked previews retain the existing cottage and lantern. The UI was also checked in Daylight/Dusk and with larger Android text during the earlier UI refresh.

Candidate release APK/AAB files are signed with the local upload key and their signatures verified. Debug and release lint report zero errors. The signed APK is checked on a separate fresh Android 37 emulator, including offline policy access. The original experimental package-only R8 optimization caused a release startup access error; full-app optimization resolves the crash. Privacy/branding tests verify contact text, deletion/sharing sections at 1.5× text size, Internet permission for optional services and no QUERY_ALL_PACKAGES permission, and both adaptive/themed icon renders.

See [release/README.md](release/README.md) for signing, artifacts, store materials, and pending publication work. Real-phone validation is required before describing tracking as reliable or publishing a production release.
