# ScrollXP on Firebase Spark

The Android app uses Firebase Authentication and direct Cloud Firestore backups and private friend circles. Email signup/login, verification, recovery, manual save/restore, circles and account deletion require no Cloud Functions or Firebase billing account. Pro uses Google Play Billing on the device, independently of Firebase; see `../release/PLAY_BILLING_SETUP.md`. AI remains excluded. Weekly widget detail, 30-day local history, backups and circles are free.

## Setup without billing

The matching `app/google-services.json` is already installed locally for package `com.scrollxp.app`, project `scrollxp`. It is ignored by Git. Manage this project with `dev.dhruvsharma29@gmail.com`.

Completed on 5 October 2026: Email/Password is confirmed enabled with passwords required. The existing `(default)` database is Standard/Native in Delhi (`asia-south2`) with its free tier enabled. The Spark rules and payload index exemption below were published successfully; no Functions or billing upgrade was performed. The steps below are for reproducing setup or publishing later rule changes. Real-account sign-in/verification and a second-device restore still need end-to-end validation.

1. Keep the project on Spark; no payment method or Blaze upgrade is needed.
2. Enable Email/Password in Firebase Authentication. Review verification and reset templates.
3. Create the `(default)` Firestore database in Standard edition and production mode. The app needs Native mode. For a new database, Mumbai (`asia-south1`) suits the initial audience. Do not recreate an existing database just to change its location.
4. Publish the prepared access rules and indexes. From the repository root:

```sh
npm ci --prefix backend
cd backend
npx firebase login
npx firebase deploy --project scrollxp --only firestore:rules,firestore:indexes
```

The configuration no longer contains Functions. Do not deploy Functions or run `firebase init` over the existing files. An alternative is to paste `backend/firestore.rules` into Firestore → Rules and click Publish; the included index configuration also disables payload indexing.

5. Build normally. Login is enabled by default when the matching JSON exists:

```sh
cd /Users/dhruvsharma/Desktop/Coding/ScrollXP
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

Use `-Pscrollxp.onlineEnabled=false` only when you deliberately want an offline-account build. Pro is controlled separately by the public `scrollxp.playLicenseKey` property and Play product configuration. Without a valid public RSA key checkout remains disabled.

Firestore access depends on these rules; they are now published to `scrollxp`. Email sign-in can work before rule publication, but backup/account deletion require the rules. Stay within Spark's daily/storage limits; services can stop accepting requests at their limits, while the local island remains usable. No billing upgrade is performed by the app or these commands.

## Backup and account boundaries

Only an explicitly confirmed Back up now uploads the cosmetic profile, XP reward ledger, and reserved/opened chests to `users/{uid}/backups/latest`. The dated usage-XP ledger can imply capped selected-app activity; the policy discloses this. Selected app identifiers, raw events, per-app/daily usage totals, pause records, and reminder preferences are excluded. The UTF-8 payload is capped at 900 KB and schema/reward/chest validation occurs before restore.

One account keeps one latest snapshot; one device keeps one local island even across account switches. Sign-in never saves or restores automatically. Restore replaces local game records transactionally, clears local usage/break history, keeps device app selection and setup state, and restarts coverage. Repeat restore does not add XP or reroll chests. It rechecks the signed-in UID before commit. A new device goes through app selection; no permissions or full-day coverage are imported.

Firestore uses an in-memory cache and explicit server reads. Fresh verification tokens are requested after email verification so access rules can see the change. The rules allow only the verified owner's backup and reject cross-account reads/writes, unrelated collections, malformed fields, extra data, client timestamps, excessive payloads, and invalid XP bounds. The client cannot promise a confirmed save during a network failure.

## Account deletion on Spark

The user reauthenticates with their current password and refreshes the ID token. The client first removes all friend memberships in transactions; then a transaction deletes the empty friend index and backup and creates `accountDeletions/{uid}` with only `deletedAt`. Guard creation requires authentication within five minutes and proof that no backup or friend index exists after the transaction. Backup and membership writes check the guard's post-transaction state. Even an unverified owner can remove their own backup and memberships. Cleanup can partially complete during an interruption; retry continues safely.

Then Firebase Authentication deletes the identity. If this final step fails, the app says deletion is pending and offers retry; it does not claim full success. The immutable guard permanently blocks old sessions from recreating backups or memberships. Its former UID and timestamp are retained indefinitely, with no email, password, island or activity records. No scheduled function or paid cleanup is used. Local reset and Google Play subscription cancellation are separate actions.

## Private friend circles

`friendLinks/{uid}` indexes up to three circle codes. `friendCircles/{code}` contains the title, server creation time, seven-day end time and up to six member UIDs. Its `members/{uid}` documents contain only chosen name, best completed-budget count (0–7), server join time and server update time. Create, join and leave atomically update all three records; rules reject orphan memberships, injected members and dropped indexes. Transactions serialize competition for the last available seat.

Verified code holders can read metadata and join before the end; only current members can list the roster. Circle enumeration is denied. Codes are bearer invitations, so a leaked code admits strangers; membership UIDs are visible in metadata. Scores are explicitly self-reported: rules protect ownership, fields and range, but cannot prove a goal was completed. No app names, minutes or daily dates are uploaded, and no XP or cash rewards are granted.

Scores publish only on explicit confirmation, count complete days after joining, cannot decrease, and close 48 hours after the round ends. Circles refresh manually or after the user's own actions, without continuous listeners. Leave removes the member record and index; the last member removes the circle. Ended groups persist until members leave. There is no scheduled cleanup, global discovery or chat. Device clocks must be within five minutes of server time to create a round. Real-account two-client validation and publication abuse/moderation review remain pending.

The retired Blaze code is preserved only in ignored `work/legacy-blaze/` for recovery. It is absent from active Firebase configuration, APK dependencies, and release materials. Existing real-world data from any independently deployed older server would need a separate migration; no such deployment was performed here.

## Checks

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' npm --prefix backend run test:rules
```

Local demo Auth and Firestore emulators test signup, recovery/sign-in, verified-token backups, cross-account isolation, failed reauthentication, deletion/retry, stale sessions, malformed writes, circle privacy, capacity/concurrent joins, expiry and deletion after loss of verification. No real users, emails, purchases, billing or deployed Functions are contacted. Android checks cover restore integrity, free extras, unavailable checkout, invitation validation and larger text; JVM tests cover receipt signatures and Pro/score eligibility.

Real-project account flows, backup/restore on a second installation, public policy/deletion hosting, and physical-device validation remain necessary before publication. The separate local Firebase testing toolchain has known upstream dependency advisories; it is not included in the APK. Avoid `npm audit fix --force` dependency downgrades without reviewing and rerunning the tests.

References: [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Android account management](https://firebase.google.com/docs/auth/android/manage-users), [Firestore access conditions](https://firebase.google.com/docs/firestore/security/rules-conditions).
