# Pro subscriptions on Firebase Spark

The APK includes Google Play Billing 9.1.0. No Firebase billing upgrade, Functions, service-account key or paid server is required for this client-only version. Checkout stays disabled until the public Play licensing key and subscription are configured. The current build does not contain that key and cannot take payments yet.

## Play Console configuration

1. Create the ScrollXP app with package `com.scrollxp.app`, configure Play App Signing and complete the merchant/payment profile needed to sell subscriptions. Google Play has its own registration, service-fee and tax terms, separate from Firebase Spark.
2. Upload the signed AAB to an internal testing track. Never replace the upload key. Increase `versionCode` for each later Play upload.
3. Under the app's monetization products, create an active subscription with product ID **`scrollxp_pro`** and an active auto-renewing monthly base plan **`monthly`**, billing period **one month**. Set the prices and available regions in Play Console. This build deliberately selects the regular base plan, with no trials, introductory offers or prepaid plans. Do not enable offers the UI does not explain.
4. Copy the app's **public Base64 RSA licensing key** from Play Console's licensing setup. This is not a Firebase API key, private signing key, service account or password. Add it as `scrollxp.playLicenseKey=...` to Gradle properties, then rebuild using `python3 scripts/build_release.py`. Alternatively supply `-Pscrollxp.playLicenseKey=...` to a direct Gradle build. Empty or malformed RSA keys disable checkout.
5. Add your test accounts as both internal-track testers and Play license testers. Install through the track's opt-in Play Store link, using the purchasing Play account. A sideloaded APK alone does not establish that the product is ready for sale. Follow [Google's testing guide](https://developer.android.com/google/play/billing/test) to use test payment methods rather than real charges.

The app displays Google's localized price and automatic monthly renewal terms before opening native Play checkout. Firebase sign-in is not required for Pro. Island progression, widgets, 30-day history, cloud backups and friend circles stay free. Pro provides three extra palettes and a 90-day local journal; it does not buy XP or chest odds.

## Access and restore behavior

On launch/resume, purchase callbacks and manual Restore or refresh purchases, the app queries this Play account's subscriptions, including suspended subscriptions. Matching completed receipts must have a valid local RSA signature, be unsuspended and be acknowledged before access is granted. Pending payment, invalid signatures and failed acknowledgement do not unlock Pro. Existing pending/suspended purchases block a second checkout; the user can manage them in Google Play. Failed queries do not retain a stored paid flag. Restore requires a connection to Google Play; Pro access is not guaranteed offline.

Only the locally selected theme persists. Receipts, signatures and purchase tokens are not uploaded to Firebase or written into the game database or backup. Switching Firebase accounts does not move Play purchases. Account deletion, island reset and uninstalling do not cancel subscriptions: use the in-app Manage subscription link or Play's subscription settings.

These are **client-side checks**, not independent server verification. A modified client can bypass local feature gates. There is no Developer API verification, webhook, RTDN, server entitlement or exact renewal/refund state. Google recommends secure backend verification; that remains outside this Spark implementation. Do not use this flow for real-money prizes, tradable rewards or cloud access rights. See [integration](https://developer.android.com/google/play/billing/integrate), [purchase security](https://developer.android.com/google/play/billing/security) and [subscription states](https://developer.android.com/google/play/billing/lifecycle/subscriptions).

## Required purchase validation before sale

- Successful test purchase: themes/journal unlock, receipt acknowledged; restart and restore on another installation with the same Play account.
- Cancelled checkout and failed payment: no access, no change to the free island.
- Pending completion/cancellation and acknowledgement retry: no premature access or duplicate checkout.
- Accelerated renewal, cancellation with remaining paid access, expiry, grace period, account hold, pause/resume and refund/revocation: refresh access and confirm free appearance/history fallback.
- Another Play account, missing product, unsupported plan, network outage and unavailable Play services: clear error, no purchase attempt, free features remain usable.
- Correct price/region, larger text, privacy access, cancellation link and final Data Safety/merchant disclosures.

Unit and emulator tests cover receipt tampering, local eligibility and UI gates. They do not prove Play checkout, renewal, refunds or production payment processing. All real Play lifecycle tests above remain pending.
