# ScrollXP — Usernames, Friends and Rankings

Created: 6 October 2026  
Status: Android implementation built on 6 October 2026; emulator validation and deployment results are recorded in release/VALIDATION.md. Real-friend and overnight phone validation remain pending.  
Platform: Android first, with a shared Firebase contract for other clients.  
Backend: Existing Firebase Spark Auth and Cloud Firestore; no Cloud Functions or billing upgrade.

## 1. Intended experience

Every verified account chooses a unique username. Users find friends by username, send requests, and accept them in a dedicated Friends section. A separate Ranking page shows accepted friends and an optional local leaderboard.

Pro subscriptions do not gate usernames, adding friends, or rankings. Guest users retain their local island, tracking and game progress. Creating a social profile must not reset an island or upload a backup.

“Local ranking” provisionally means a manually selected city/area, without GPS. This interpretation is awaiting the user's answer. If it means device-only results instead, replace the city board with a Personal history view; one phone cannot independently rank other users without their shared data.

### Example journey

1. Sign in and verify email.
2. Claim `@dhruv29`.
3. Open Friends, search `@rahul`, and send a request.
4. Rahul accepts in Requests.
5. Both explicitly enable sharing their weekly balance score with friends.
6. Open Ranking → Friends to compare shared scores.
7. Optionally choose a city and join its local board after reviewing what becomes visible.

## 2. Existing foundations and compatibility

The current Android implementation already supports verified email accounts, manual backups, account deletion and private invite-code circles. Circles use seven-day rounds, up to six members and self-reported completed budget days. They currently appear in connected features rather than dedicated social pages.

Reuse authentication, verification refresh, account guards, the completed-day history and score eligibility checks. Keep the existing circle collections and seven-day scoring unchanged during migration. Move their entry point into Friends → Circles; do not silently convert old circle members into accepted friends.

Existing backups remain compatible. New usernames, friendships, blocks and ranking entries are online account records, not part of the island backup payload. Restoring a game cannot recreate relationships, overwrite a username, or unlock Pro.

## 3. Unique usernames

- Canonical username: 3–20 lowercase ASCII characters; letters, digits and underscores; first character must be a letter. Show `@` in the UI but do not store it in the key.
- Normalize case and trim whitespace before lookup. Reject unsupported characters, reserved service names and deceptive official-account names.
- Use Firebase UID as the permanent identity. Username is a discoverable handle, never an authentication credential or an email address.
- Claim atomically: create `usernames/{handle}` and the owner's social profile in one Firestore transaction. Rules must validate both post-transaction documents and prevent a second username for the same account.
- Availability checks are advisory. If two people claim the same handle simultaneously, exactly one transaction succeeds; the other receives a useful “already taken” message.
- Require verified email and an account that is not pending deletion. Username creation needs a confirmed online transaction; no offline claim or optimistic success.
- Keep usernames fixed in the first release. Display names can be edited separately. Renaming requires a later atomic migration and protection against impersonation.
- Existing accounts get an optional “Choose your username” step when opening social features. No auto-generated claim using the user's email or real name.
- Account deletion releases the handle only with deletion of its owner profile and discoverable ranking records. Friend and ranking caches retain UIDs so a later claimant cannot inherit the deleted person's relationships.

## 4. Friends section

### Views

**My friends:** accepted users, their username, display name, chosen built-in avatar and friendship actions. Initial target: at most 20 accepted friends per account.

**Add friends:** exact username lookup, one result at a time. Provide a Share my username action using Android's share sheet. Do not scan contacts, search by email, or enumerate all profiles.

**Requests:** separate incoming and outgoing lists with Accept, Decline and Cancel. Initial target: 10 pending requests per direction. Prove the limits can be enforced within Firestore rule access-call limits before committing to this schema.

**Circles:** keep existing invite-code circles available as a distinct feature.

### Relationship behavior

- A request is pending until its recipient accepts. Sending a request does not reveal private scores or make either person a friend.
- Only the sender can create or withdraw a request; only the recipient can accept or decline it. Either participant can remove an accepted friendship.
- Self-requests and duplicate relationships are rejected. Crossed requests reconcile to a single relationship with explicit acceptance; they do not silently bypass consent.
- Use a deterministic pair ID derived from sorted UIDs, with the actual participant IDs stored and validated. Relationship identities cannot change after creation.
- Transactionally update relationship state and bounded per-user indexes. Rules must reject orphan indexes, injected users and unilateral creation of an accepted friendship.
- Blocking removes the relationship, hides scores and prevents new requests in both directions. Each user controls only their own block records. Unblocking does not restore friendship automatically.
- Provide Report user with predefined reasons and optional bounded text. Reports are private to the reporter and developer; manual Firebase Console review is sufficient for the beta. Do not imply automated moderation exists.
- Requests refresh on screen entry or pull-to-refresh. Push notifications and chat are outside this iteration.

## 5. Ranking page

Use two clear tabs: **Friends** and **Local**. Show period dates, metric explanation, last successful refresh and whether the displayed board is cached.

### Scoring

- Primary metric: completed full budget days in the current week, capped at 7. More scrolling does not improve the ranking.
- Count only finalized successful days with usable tracking coverage. Partial, unknown and restored untracked days do not count.
- For the new boards use one common Monday-to-Monday UTC week. Display its boundaries in the phone's timezone. Only complete budget-day intervals wholly inside that period count; explain this near the metric, especially for new users and timezone changes.
- Use the existing non-overlap checks to prevent duplicate history from counting twice. Score publication also needs an eligibility start time, preventing old history from becoming newly shared ranking progress without consent.
- Joining sharing mid-day normally produces 0 initially; the first complete eligible day begins after sharing starts. Sharing is not an immediate XP award.
- Upload only weekly count, period ID, sharing scope and server update time. Keep app names, minutes, daily dates, budget amount and raw usage history on the phone.
- Scores are self-reported. Rules protect ownership, range, timestamps and period transitions, but do not prove a day was completed. Label boards accordingly; no cash, paid prizes or XP rewards for placement.
- For one period preserve the highest valid published count. A new period starts at 0. Restores, reinstalls or repeated refreshes must not add points.
- Prototype server-time-backed period validation in rules before implementation. Device clock changes must not choose arbitrary future weeks or overwrite an unrelated period.

### Friends ranking

- Include yourself and accepted friends who explicitly opted into friend score sharing. Do not expose pending, removed or blocked users.
- Fetch the accepted friend index, then perform bounded individual score/profile reads. Firestore rules are not filters: do not query every score and expect unauthorized rows to be removed by rules.
- Sort descending by completed days. Give equal counts the same rank, using competition ranking (`1, 1, 3`). Username/UID ordering only stabilizes tied rows; it does not imply a tie winner.
- Show private/not shared, unavailable and stale states separately from 0. A failed read cannot become a fabricated score.
- If some friend results fail, mark the board incomplete and withhold an exact overall position until the eligible roster is loaded.
- Highlight the user's row and explain the next eligible day. Avoid “scroll more to beat your friend” prompts and losing-position alarms.

### Local city/area ranking — provisional interpretation

- Off by default. Let users select a country and city from a curated ID list, using no GPS, background location or address collection.
- Before joining, explain that verified signed-in users can see the chosen username, avatar, chosen area and weekly count.
- The area is self-selected, not verified physical residence. Label it “Selected area.” Do not claim “near you.”
- Query only opted-in entries for the chosen area and current period, ordered by count and stable UID, with a bounded limit of 50. Create the required composite index.
- Show “Top 50 in [area]”; if the user is outside those results, show “Outside the displayed top 50” with their own count. Do not invent an exact city-wide rank or population count without an affordable aggregation design.
- Rules must constrain query scope and limit. Client-side filtering cannot secure private entries.
- Leaving immediately removes the user's local entry and clears their local cached public-board state. Changing area moves the single user-owned entry rather than duplicating it across cities.
- Keep one current entry per participating user, not an accumulating document for every week. Old-period entries are excluded by the current-period query and overwritten on the next share. No scheduled cleanup or paid function is required for weekly rollover.
- A local public ranking expands disclosure beyond accepted friends. Keep its consent and storage separate from private friend scores.

## 6. Proposed Firestore contract

These are new collections; names and fields must be finalized through a rules prototype before shipping.

| Record | Purpose | Access boundary |
| --- | --- | --- |
| `socialProfiles/{uid}` | Owner's handle, display name, avatar, consent and social settings | Owner; limited profile projection for accepted friends |
| `usernames/{handle}` | Atomic reservation and minimal discoverable profile projection | Verified exact-name gets; no enumeration; owner transaction writes |
| `friendships/{pairId}` | Participant UIDs, request sender/state and server timestamps | Participants only; state-specific transition rules |
| `socialLinks/{uid}` | Bounded incoming, outgoing and accepted relationship IDs | Owner reads; strictly coupled relationship transactions |
| `blocks/{uid}/users/{otherUid}` | Private owner-controlled block records | Owner operations; rules may check both participants' blocks |
| `friendScores/{uid}` | Current weekly count and sharing eligibility | Owner and eligible accepted, unblocked friends |
| `localScores/{uid}` | Opt-in discoverable area and current weekly score | Verified constrained area/period queries; owner writes/deletes |
| `socialReports/{reportId}` | Abuse report and review metadata | Reporter submission; privileged developer review |

Do not add email, password, precise location or purchase credentials to discoverable records. Preserve the existing `users/{uid}/backups/latest`, `friendLinks`, `friendCircles` and immutable `accountDeletions` guard.

Authorization must hold for direct SDK requests and modified clients, not just UI buttons. Keep strict allowed fields, bounded strings/arrays, server timestamps and immutable participant/identity fields. Audit the per-operation and transaction rule lookup limits.

## 7. Sync, quotas and failure states

- Score sharing is an explicit opt-in. Initial release uses Share score / Refresh; adding automatic sync later needs separate user consent and a write budget.
- Refresh on entry with a short cache interval and offer manual refresh. Avoid continuous listeners, refresh-per-keystroke lookup and per-second writes.
- Publish only when the period/count changes. Do not write on every app open or scrolling event.
- Cache only minimal display rows. Sign-out, account switch, block, removal and consent withdrawal must invalidate relevant caches. Offline cached data must be visibly dated; revocation takes effect when clients next contact the service.
- Queue no offline friend acceptance, username claim or public-score publication as a successful action. Show a retry state and wait for server confirmation.
- Estimate friend-index, profile, score, query and security-rule dependent reads before beta. Observe actual Firebase usage. Local boards and reporting increase shared quota usage; client cooldowns are UX measures, not abuse-proof server rate limits.
- Spark has finite free quotas. At quota exhaustion show online features temporarily unavailable while preserving the local game; do not automatically upgrade billing.

## 8. Deletion and consent withdrawal

Extend the existing retryable deletion flow before introducing these records:

1. Reauthenticate and refresh the token.
2. Remove current local-board entry and private published score.
3. Remove each bounded friendship and pending request with both participants' indexes updated consistently.
4. Leave existing circles using the current cleanup.
5. Remove the username reservation, social profile, owned block records and empty indexes. Define report retention/minimization in the policy before collecting reports.
6. Remove the backup and create the immutable deletion guard with proof of completed owned social cleanup.
7. Delete the Auth identity; preserve the retry path for interruptions.

Keep an account-owned bounded cleanup inventory where necessary, so deletion can be proved without scanning unrelated users or trusting that a UI loop finished. Rules must prevent a stale token or concurrent friendship transaction from recreating data after the guard is created. Do not loosen existing backup protection to make cleanup easier.

Friend-score withdrawal removes that score without deleting the account. Leaving the local board removes only its discoverable entry. Neither action cancels a Play subscription or resets the island.

## 9. UI direction

- Add Friends and Ranking destinations to the app's existing navigation pattern, preserving access to the world, collection, history and settings. Resolve small-screen navigation fit during the UI phase rather than overcrowding the bar.
- Reuse calm Daylight/Dusk palettes, readable typography, reduced motion and built-in avatar illustrations. No flashing rank changes or forced celebration loops.
- Friends empty state: “Add your first friend” with one primary action.
- Ranking empty state: explain how sharing starts and when the first eligible point is available. Offer Add friends rather than fake leaderboard entries.
- Requests show Accept and Decline with distinct accessible labels. Confirm Remove/Block, and make cancellation recoverable.
- Handle keyboard insets, larger font sizes, long names, screen readers and slow connections.
- Keep clear status chips: Pending, Friend, Not sharing, Updated [time]. Do not infer online presence from a score update.

## 10. Implementation sequence

### Phase 1 — Username and rules foundation

Build validation, atomic claim, exact-name lookup and profile creation. Prototype identity coupling, delete guards and concurrent claims in the Firebase emulators before adding social UI.

**Done when:** two concurrent users cannot claim one handle; one user cannot claim many handles; existing islands/accounts survive; exact-name lookup reveals only the intended projection.

### Phase 2 — Friend relationships

Build request transitions, bounded indexes, lists, block/remove/report and deletion cleanup. Add the dedicated Friends section and keep circles accessible.

**Done when:** acceptance is mutual and recipient-authorized, denied users cannot read private data, races leave no orphan links, and cleanup retries safely.

### Phase 3 — Friends ranking

Define the canonical week and eligibility start; build score publication, accepted-roster reads, tied ranks, sharing consent and incomplete/offline states.

**Done when:** independent users see the same fully loaded board, ties share positions, partial days do not score, and removed/blocked friendships lose score access immediately on the server.

### Phase 4 — Local ranking

After resolving the meaning of “local,” add the curated area picker, separate public consent, single-row current score, scoped top-50 query/index and opt-out cleanup. If local means device-only, replace this phase with personal weekly history.

**Done when:** private users cannot appear, queries cannot enumerate unrelated areas/private entries, period transitions hide stale results, and top-50 labels do not claim exact unseen ranks.

### Phase 5 — Beta and release updates

Test with real accounts on multiple phones; update privacy policy, account-deletion instructions, Data Safety answers and screenshots to match actual shipped disclosure. Deploy only reviewed rules/indexes to the existing Spark project and build a fresh testing APK.

**Done when:** the checks below pass and the user has tested the intended sharing experience with friends. This feature work does not complete separate Play Billing lifecycle or publication requirements.

## 11. Required validation

### Emulator and domain checks

- Case normalization, invalid/reserved handles, simultaneous same-name claims and one-handle-per-UID enforcement.
- Unverified and deletion-pending access denial; no email/backups exposed by lookup.
- Request, accept, decline, cancel, crossed requests, duplicate/self-request and enforced capacity boundaries.
- Foreign profile/relationship writes denied; unilateral accepted friendship and forged index denied.
- Blocking, removal, account switching and old-token access revocation.
- Completed-day eligibility, overlap, partial days, midnight/week transitions, timezone/clock changes and reset/restore behavior.
- Equal-count rank assignment, pagination bounds and incomplete roster handling.
- Public-board opt-in/out, area change, strict query constraints, score bounds and timestamp checks.
- Interrupted account deletion and concurrent request/publication races; no re-created data after deletion guard.
- Verify request limits and rule access calls with real emulator operations, not assertions that mirror client functions.

### Real-phone beta

Use at least three distinct verified accounts: A and B accept friendship; C remains pending. A/B publish after a complete eligible day; both see matching rank rows. C cannot read their private scores. Block/remove B and confirm server denial with the other installation. Enable/disable local participation, reopen the apps and verify visibility changes. Include offline/reconnect and two devices signed into the same account so sharing does not silently overwrite ownership or duplicate points.

Preserve existing game progress and test fixtures; do not change dates or inject fabricated days into production accounts to simulate a ranking win.

## 12. Technical references

- [Firestore transactions and atomic rule validation](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Security rules and query constraints](https://firebase.google.com/docs/firestore/security/rules-query)
- [Firestore usage and free quotas](https://firebase.google.com/docs/firestore/quotas)

The Spark architecture above is a proposed design grounded in these APIs. Rule prototypes and multi-client tests are required before treating it as implemented or secure.

## Implementation decisions — 6 October 2026

The Android version uses a fixed unique handle as its display identity; separate editable display names and avatar selection remain future polish. The combined friend/request/block limit is 20, enforced through one-entry deltas in bounded indexes rather than separate incoming/outgoing quotas. Public local rows are discoverable to verified users regardless of their own area; current-week collection queries are limited to 50, and the app queries the selected area. The initial curated list contains eight Indian cities. `socialPairs` stores relationship state and blocking together. Reports use one fixed profile-abuse reason and private manual review. Existing circles remain available in Friends and connected features.

Nightly backup was added to this iteration at the user's request. It is opt-in around 1 a.m., uses the existing latest-backup format, and pauses on cloud timestamp conflict. A manual save/restore, sign-out, local reset, backup deletion or account deletion disables it. Local Room saving remains automatic as before. Exact overnight timing is not promised.
