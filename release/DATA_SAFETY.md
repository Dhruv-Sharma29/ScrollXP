# Data safety preparation — optional connected features

Prepared 5 October 2026. This is a review worksheet, not a submitted Play Console declaration.

## Current evidence

| Behavior | Implementation and disclosure |
| --- | --- |
| Installed launcher apps | Narrow launcher-intent query, used for the local selector; no QUERY_ALL_PACKAGES permission. |
| Usage events | Android Usage Access; replayed locally into selected-app daily totals. Other app identifiers can occur in system transitions; raw event history is not stored as a copied stream. |
| Local history and game | Room records plus private reminder preferences; daily minute totals/app identifiers remain local. No ads, analytics, or AI. |
| Networking | INTERNET is declared for optional Firebase Spark accounts, direct backups and private circles. Login is on when the matching JSON exists unless explicitly disabled. Play Billing 9.1.0 adds BILLING permission; Cloud Functions remain absent. |
| Accounts | Optional Firebase email/password authentication, verification/recovery, user identifier and sign-in session. No password stored in Room. |
| Explicit cloud backup | Optional island preferences, chest contents, XP ledger and earned dates, stored under the authenticated UID. Dated usage-XP can imply capped activity and must be disclosed. |
| Friend circles | Explicit circle creation/join and manual best-score sharing send chosen names, account identifiers, a 0–7 completed-budget-day count, membership and timestamps to Firestore. Members read rosters; verified invite holders can get circle metadata. No app names/minutes/daily dates. No global discovery or chat. |
| Purchases | Google Play processes subscription transactions. Signed receipts, signatures and tokens are checked/acknowledged in memory; they are not stored in Room or sent to Firebase. Pro theme stays local. Client checks provide no independent server anti-fraud guarantee. Checkout is disabled until Play setup. |
| Reminders | Optional generic local notifications, private receipts and settings; no push-notification service. |
| Share/Save | User-chosen PNG of island name/art/destination; level and XP opt-in. No usage history or app names. Scoped FileProvider read grant and Android document picker. |
| Deletion | Separate local reset, backup deletion, leaving a circle and reauthenticated account deletion. All memberships are removed before atomic guard/backup deletion and Auth deletion; interrupted deletion can be retried. A former UID/timestamp guard stays indefinitely. Play subscriptions must be cancelled separately; Google transaction records and recipients' copies are outside these controls. |
| Backup | Android automatic backups remain disabled; explicit Firebase backups are optional. Saved images can enter other services' backups. |
| Security | Android app-private storage; no claim of app-level database encryption or end-to-end encryption. |

## Proposed answers to review in Play Console

Do not reuse the former blanket **No collection** answer for an account-enabled release. Review email/user identifiers, game progress, dated activity-derived reward records, friend display names/interaction and balance scores, purchase history and Firebase/Play Billing SDK data practices against the form's categories. Review whether completed-budget counts fall under app interactions or another applicable activity category. Do not claim that all activity stays local: the XP ledger and voluntary circle counts are exceptions. Accounts, backups, circles and purchases are optional. Purposes include app functionality, account management, payment processing and security. Explain indefinite UID/timestamp guard retention, circle retention until leave/deletion, and separate Play transaction controls.

The [official definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) distinguish local-only processing from data transmitted off device, including SDK transmissions. Review whether service-provider and user-initiated sharing exceptions apply to Firebase processing and postcard sharing; this worksheet does not automatically classify those transfers as exempt. HTTPS is used by the configured Firebase/Google services; no end-to-end encryption is claimed. Include all distributed builds and SDK behavior in the final declaration.

Account creation/deletion is enabled in the configured Spark build. Before Play distribution, supply both the in-app deletion flow and an externally accessible deletion-request page, as required by [Google's account-deletion guidance](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en). Local reset remains independent of online deletion.

## Required before submission

- Host `privacy/index.html` at an active public HTTPS URL, without a login or geographic restriction; add the actual URL in Play Console. The in-app policy already ships offline.
- Review target audience, content rating (including cosmetic chests), app-access instructions, ads declaration, applicable permissions/declarations, and all versions you distribute.
- Confirm the final merged permissions and dependency report, including Firebase and Play Billing practices. Revisit this worksheet whenever distributed data handling changes.
- Finish physical-device validation when the user is ready and review the beta results.

The [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) calls for an accessible in-app policy, public policy link, developer/contact information, data practices, and retention/deletion terms. This package prepares these materials; it does not claim Play approval or legal certification.

## New Android social and nightly backup disclosure — 6 October 2026

Review discoverable username and UID, relationship/request/block data, voluntarily published weekly count and optional self-selected area under the applicable user identifiers, other personal information/app-interaction categories. Local-board records are discoverable to verified users, not restricted to friends or physical residents; no GPS is accessed. Reports contain reporter/target UIDs, predefined reason and timestamp for private manual review. Own submitted reports are removed during account deletion; reports from others have manual abuse-review retention. A deletion-intent UID/timestamp exists during interrupted cleanup, followed by the existing indefinite deletion guard.

Nightly backup is an explicit opt-in background upload of the same disclosed island/reward data, not Android's automatic system backup. Account/phone binding, conflict pause, approximate scheduling and disable-on-sign-out/reset/deletion are disclosed. Re-review the public pages and final Play form before publication; no change to Firebase billing or payment verification.
