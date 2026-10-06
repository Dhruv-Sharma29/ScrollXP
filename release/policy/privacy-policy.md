# ScrollXP Privacy Policy

Updated: 5 October 2026

## About ScrollXP

ScrollXP turns selected-app screen time and completed daily budgets into a customizable island. This policy covers the current Android app, package com.scrollxp.app.

Developer: Dhruv Sharma

Privacy contact: dev.dhruvsharma29@gmail.com

## Data accessed on your device

The app selector reads the names and package identifiers of installed apps with launcher activities so you can select up to three apps. Selection and search happen on your device.

After you grant Android Usage Access, ScrollXP reads system app-usage events to estimate when your selected apps were in the foreground. System event history may include other app identifiers needed to determine foreground transitions. ScrollXP saves daily totals for selected apps rather than a copy of the system event stream.

ScrollXP cannot read messages, watched videos, Reels or Shorts contents, passwords, or scroll gestures. It estimates whole-app foreground time; it does not count individual videos.

## Local records and purposes

ScrollXP stores selected app identifiers, daily usage totals and coverage status, tracking pause periods, your budget and goal timezone, goal results, XP and reward records, chests, cosmetic placements, island name and destination, and appearance preferences. These records support tracking, budget evaluation, progress, and customization.

If you enable reminders, ScrollXP stores your reminder time, quiet hours, scheduling revision, and delivery receipts to schedule local notifications and suppress duplicates. Notifications contain generic island or goal text rather than app names or usage totals.

## Optional accounts and cloud backup

ScrollXP requests Internet permission for optional Firebase email accounts, direct Cloud Firestore backups and private friend circles. These features use Firebase Spark services. You can continue on this device without an account or payment. There are no advertising, analytics or AI services in this version. Optional Pro purchases use Google Play Billing, separately from Firebase.

If you create an account, Firebase Authentication processes your email address and password and assigns an account identifier. The app does not save your password in its local database. Firebase maintains the sign-in session. Verify your email before using backups. Signing in does not automatically upload or replace the local island, and switching accounts does not automatically switch the device's island.

Only when you confirm Back up now does ScrollXP upload island name, budget and goal timezone, XP reward ledger, chest contents and opening state, cosmetic preferences, destination, and guide state to Cloud Firestore under your account identifier. The reward ledger includes dated usage-XP and budget rewards; these can reveal a capped indication of selected-app activity and dates on which a budget was completed. Selected app names and identifiers, raw system events, daily usage totals, per-app breakdowns, pause records, and reminder settings are not included.

Each account keeps one latest backup. A new backup replaces the previous copy. Restore requires confirmation, replaces the local game, and clears local usage history. Device app selections remain local, and tracking coverage starts again. Backups are not end-to-end encrypted; Firebase and authorized project administrators can process stored records. Network requests use the Firebase and Google HTTPS services. See Google's Firebase privacy information at https://firebase.google.com/support/privacy.

## Included features

The island widget, weekly balance detail and 30-day history are local features included without an account or payment. Accounts, backups and friend circles are also free, subject to service availability. Pro adds Ocean, Rose and Lavender themes and a 90-day local balance journal. Firebase, Google Play, your operating system and apps you choose for sharing operate under their own privacy policies.

## Optional friend balance circles

You can explicitly create or join up to three private, seven-day circles with up to six members each. Firestore stores the circle name, start/end timestamps, member account identifiers, your chosen display name, join/update timestamps and a best score between zero and seven. Scores count full budget days completed after joining and within the round. The app uploads the count only when you confirm Share my balance score. App names, usage minutes and the dates of individual budget days are not uploaded to circles. Scores are self-reported from the device, without independent server verification, cash prizes or extra XP.

Members can read the roster, display names and scores. Any verified account with the invite code can read circle metadata, including member account identifiers, and join before the end if space is available. Treat the code as a private invitation; sharing it in a public place allows strangers to join. Avoid personal information in circle and display names. The app opens Android's share chooser only when you choose Share invite; the receiving app handles your invitation under its own policy.

Circles refresh when requested and after your own create/join/score/leave actions; there is no background score upload or continuous roster listener. Your membership, display name and score stay until you leave or delete your online account. Leaving removes those records; the circle title and timestamps remain while others are members, and the last member leaving deletes the circle. Ended circles are not automatically deleted. Other members may retain screenshots or copies outside ScrollXP's control. Firebase and authorized project administrators can process these records; they are not end-to-end encrypted.

## Optional Google Play subscriptions

Pro is an optional monthly auto-renewing subscription. Before checkout, the app displays the price returned by Google Play for your account and region. Subscriptions are unavailable until the developer has configured the Play product and licensing key. Google Play handles your payment method and transaction; ScrollXP does not ask for or store card details. See Google's privacy policy at https://policies.google.com/privacy.

The app queries purchase records from Google Play, checks the signed receipt locally, acknowledges completed verified purchases and uses the result to unlock features on this device. Receipt text, signatures and purchase tokens are processed in memory and are not saved to the island database or uploaded to Firebase. The chosen Pro theme is a local preference. Purchase checking uses Google Play's billing service; an Internet connection and the purchasing Play account are needed to refresh or restore access. Pending, suspended or unverified purchases do not unlock Pro. This version has no independent server purchase verification or renewal/refund notifications and cannot promise tamper-resistant entitlement checks.

Pro follows your Google Play account, independently of your Firebase login. Cancel through Google Play → Payments & subscriptions → Subscriptions, or use Manage subscription in the app. Cancellation normally leaves access until the paid period ends, as reported by Google Play. Deleting a ScrollXP account, resetting local data or uninstalling does not cancel a Google Play subscription. Google retains its transaction records under its own policies; ScrollXP's deletion controls do not erase them.

## Optional postcards and sharing

Only when you choose Share or Save does ScrollXP export a PNG containing island artwork, your island name, the destination name, and optionally level and XP. Level and XP are excluded by default. App names and usage history are excluded.

Share gives the app you choose temporary Android read access to that image. That receiving app can retain or transmit it according to your action and its own policy. Save writes the image to the location you choose. ScrollXP cannot delete recipients' copies or files you saved outside its private storage.

Temporary postcards are kept in the app's private cache, with a maximum of 20 cached exports. Android may clear this cache; new exports remove the oldest files above the limit.

## Retention, protection, and backups

Usage and game records remain in app-private local storage until you delete them or uninstall the app. Records are protected by Android's app sandbox and device security; ScrollXP does not add a separate database encryption layer. Protect your device with a screen lock and keep Android updated.

Android's automatic cloud and device-transfer backups remain disabled for ScrollXP's local records. Explicit Firebase island backups are separate and stay until replaced or deleted. Images you independently save or share may be backed up by other apps or services you use.

The home-screen widget displays your island name, artwork, level, and XP. An optional free detail also displays weekly budget progress. Widgets can be seen by anyone who can view that home screen; they never show app names or usage minutes. Remove them through your launcher. Widget preferences are local.

## Your controls and deletion

In Settings you can pause tracking, change selected apps and budget for the next day, turn reminders off, and choose Delete data and reset island. Reset deletes local history, XP, rewards, island customization, preferences, reminder settings and receipts, and cached postcards, and cancels reminder work and notifications. User-saved and already-shared images remain outside this deletion.

Local reset does not delete an online account, cloud backup or friend memberships, and does not cancel subscriptions. In connected features, Leave circle removes the shared name and score from that circle, and Delete cloud backup removes the latest account backup. Delete online account requires your current password, removes every friend membership, atomically removes the backup and creates a deletion guard, then deletes the Firebase Authentication account. If interrupted, reconnect and repeat deletion; some memberships may already have been removed. If the final Auth step fails, the app reports deletion as pending. Account deletion leaves the local island on this device; use local reset separately if desired. Cancel any Pro subscription separately through Google Play.

The deletion guard contains only the former account identifier as its document ID and a deletion timestamp. It is retained indefinitely to block still-valid sign-in sessions from recreating backups or friend memberships. It contains no email address, password, island or usage records. It cannot be changed or removed by app clients. This free-plan implementation has no automatic server cleanup; the guard is disclosed separately from the game, shared friend data and login records that are deleted.

You can also request online-data deletion by contacting dev.dhruvsharma29@gmail.com from your account email. We may ask you to verify ownership before acting. Do not send your password or purchase token. A public account-deletion request page must be provided before distributing account-enabled builds on Google Play.

You can revoke Usage Access and notification permission in Android Settings. Reset does not revoke those system permissions or erase Android's own usage logs. Uninstalling removes ScrollXP's app-private records but does not delete cloud data. User-saved or shared images remain separate.

## Changes and contact

This policy applies to the features in this version. If data handling changes, the policy and relevant in-app explanations will be updated before the changed behavior is introduced. The update date appears above.

For privacy questions, email dev.dhruvsharma29@gmail.com. Please do not send app-usage history or other sensitive information in a public issue tracker.
