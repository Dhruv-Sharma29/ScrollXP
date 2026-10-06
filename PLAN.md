# ScrollXP — Product and Implementation Plan

Created: 4 October 2026  
Platform: Android first  
Status: Core game and release materials implemented; current emulator checks pass; broader release validation and publication pending; physical-device validation deferred  
Planning estimate: 6–8 weeks for an experienced developer with limited original artwork; revise after the tracking prototype.

## Current build progress — 5 October 2026

Firebase Spark replaces the earlier Blaze implementation at the user's request. Email accounts and direct Firestore backups are enabled by default when configured. Email/Password is confirmed enabled. Private friend balance circles now use direct Firestore transactions: six members, three circles, seven-day rounds, manual completed-budget counts and no XP/cash rewards. Scores are self-reported rather than protected by server anti-cheat. Account deletion removes all memberships before the backup and Auth identity, retaining a minimal immutable UID/timestamp guard against stale sessions. Cloud Functions and AI remain excluded. Setup and limitations are in `backend/README.md`.

At the user's request, optional Pro subscriptions are implemented through Google Play Billing 9.1.0 with client receipt checks and acknowledgement. Pro adds Ocean/Rose/Lavender palettes and a 90-day local journal; existing widget, weekly detail, 30-day history, backups and circles stay free. Checkout remains disabled until `scrollxp_pro`/`monthly` and the public Play licensing key are configured. There is no independent server verification, cloud entitlement or renewal/refund notification. Play lifecycle tests remain pending; see `release/PLAY_BILLING_SETUP.md`.

The starting page supports local continuation and Firebase email/password accounts. Signup, verification, recovery, sign-out, manual cloud backup/restore, and reauthenticated account deletion use Spark services. Default builds enable these with the matching JSON; an explicit false property disables them. Saved local islands are preserved. Public policy/deletion pages and store declarations still need publication review.

The first development slice is implemented: onboarding, installed-app selection, Usage Access guidance, event-based usage estimates, local Room storage, capped XP, daily budget evaluation, a starter island, 20 collectible items, customization, history, tracking pause, personality options, and reset.

Current test results, including Spark rules, receipt eligibility and UI gates, are recorded in `release/VALIDATION.md`. Real-phone tracking and battery validation are deferred at the user's request.

The UI has been refreshed with warm Daylight and Dusk palettes, original illustrated navigation and treasure cards, locked-treasure previews, segmented next-unlock progress, placement haptics, saved-XP feedback, and reduced-motion controls. Preferences persist, and collection cards adapt to larger Android text. Database version 3 migrates existing worlds and rewards without reset. The 13 new cosmetics have their own original miniature artwork and fixed island placements; opened rewards can be placed immediately or saved for later. Collection filters and recent discoveries make earned items easier to find.

Welcome and milestone chests, duplicate protection, published base odds, collection-completion keepsakes, and the weekly five-day budget quest are implemented. Opt-in local reminders and island sharing are implemented. Meadow haven, Seaside cove at 5,000 lifetime XP, and Cloud sanctuary at 10,000 are available with distinct Daylight/Dusk scenery, cottage and treasures, and previews. Visits do not spend XP. Spark accounts/backups, widgets, private balance circles and the Pro client flow are implemented; real-project account/circle and Play purchase tests remain before publication.

Reminders default to off, request notification permission when enabled, support a chosen wall-clock time and editable quiet hours, and use silent best-effort WorkManager delivery. Clock/timezone changes replan scheduling; receipts suppress duplicates and clock-travel bursts. Paused tracking suppresses delivery. No app names or usage data appear in reminders. Very late deliveries are skipped.

World now includes an island postcard dialog. It exports a 1080 × 1350 PNG using the live island renderer, with matching Daylight/Dusk artwork, cosmetic placements, the island name, and optional level/XP (off by default). Android Share grants read access only to that export; Save uses the document picker. Reset clears local reminder preferences and cached exports.

The optional first-chapter guide covers Usage Access, opening the welcome chest, placing a treasure, and completing one full-day budget. It has no expiry or consecutive-day requirement and grants no separate reward. Users can dismiss it and restore it from Settings. Interactive guest previews introduce all three destinations before setup. Room version 4 stores destination selection, guide dismissal, and a sticky first-placement flag; its migration preserves earlier customization and detects actually placed rewards.

Release materials are prepared under `release/`: the adaptive/themed icon, Play icon and feature graphic, factual listing copy, finalized privacy policy (also accessible offline inside the app), and a Data safety worksheet. Candidate APK/AAB files are signed with a private local upload key and verified. A separate emulator is used for the signed APK; full-app R8 optimization fixes the template package-only startup crash. Public policy hosting, Play Console declarations/setup, beta feedback, and physical-device validation remain before publication.

## 1. Product definition

**ScrollXP turns selected-app screen time and completed screen-time goals into a customizable virtual world.**

Users select apps, choose a daily budget, earn capped XP from measured usage, and earn additional progress by staying within that budget. They spend a short daily visit collecting earned rewards and improving an island.

Working tagline: **Turn your screen time into progress.**

The product should feel playful, self-aware, and satisfying. World ownership, visible unlocks, and achievable goals are the main reasons to return. Increasing social-media consumption is not a success metric.

### Initial audience and scope

- Begin with an adult Android beta audience that already uses social or video apps.
- Support selected installed apps, including Instagram and YouTube; TikTok appears only when installed and available on the device.
- Treat all measured time as app usage. YouTube time includes activities beyond Shorts; Instagram time includes activities beyond Reels.
- Offer one complete solo progression experience before introducing multiplayer.
- Make onboarding work without an account.

### Product success

A successful first version lets a user understand tracking, trust the totals, build something immediately, and return voluntarily to develop their island. Daily challenges should remain worthwhile on days with little or no selected-app usage.

## 2. Decisions for the first release

| Area | Decision |
| --- | --- |
| Language | Kotlin |
| IDE | Android Studio |
| Interface | Jetpack Compose and Material 3, customized for the game |
| Minimum Android version | Android 8.0 / API 26 |
| Tracking | UsageStatsManager with user-granted Usage Access |
| Storage | Room for structured records; DataStore for preferences |
| Background work | WorkManager for best-effort reconciliation |
| World | One 2D island with predefined placement slots |
| Economy | XP and cosmetic items; defer a separate coin currency |
| Personality | Curated local messages only; AI removed from scope at the user's request |
| Accounts and backend | Optional Firebase Spark Auth and direct Firestore backups; no paid server |
| Competitive rankings | Deferred; local usage data is not authoritative |

The generated project currently uses package `com.scrollxp.app`, minimum SDK 26, and compile/target SDK 37. Build and release configuration must be validated against the installed tools and the Play requirements applicable at launch. Do not change generated dependency versions merely to match older examples.

## 3. The core experience

```text
Choose apps and a daily budget
              ↓
Use the phone normally
              ↓
Reconcile recorded app usage
              ↓
Award capped usage XP
              ↓
Finalize the completed day's budget challenge
              ↓
Collect earned cosmetics and unlock buildings
              ↓
Customize the island and preview the next milestone
```

### First-session experience

1. Show the island immediately with a brief explanation of the game.
2. Let the user name their island and choose a starter appearance.
3. Grant a starter house and two decorations as onboarding gifts, without competitive points.
4. Explain that tracking reads app-usage timing rather than messages or watched-video content.
5. Offer a clearly marked preview before permission is granted.
6. Guide the user to Android's Usage Access settings and verify access on return.
7. Let the user select up to three supported installed apps.
8. Set a budget for the next full eligible day; explain when it starts and ends.
9. Place the starter house and reveal the next unlock.
10. Offer optional reminders after the user has seen the app's value.

**Acceptance:** a user can reach and customize the starter island without creating an account. Permission denial leaves the preview usable, with no fabricated usage or earned tracking rewards.

### Daily experience

- Refresh usage when the app opens.
- Show the last refresh time and any tracking uncertainty.
- Present the previous day's result when enough data exists to finalize it.
- Show earned rewards in a short collection sequence.
- Let the user place or change a decoration.
- Show today's budget and the next unlock.
- Let the user leave without an unfinished forced interaction.

## 4. MVP features and exclusions

### Included

- Usage Access onboarding and access-status detection.
- Up to three selected installed apps.
- Today's per-app usage and combined usage.
- Persistent XP, levels, and progression milestones.
- One island with approximately 20 unlockable buildings or decorations.
- Fixed placement slots, previews, and simple cosmetic variants.
- One daily budget challenge.
- Five successful daily budgets within a calendar week as a weekly challenge.
- Earned cosmetic chests and duplicate protection.
- Optional Gentle, Sarcastic, or Silent personality.
- A seven-day usage chart and completed-goal history.
- Optional reminders and quiet hours.
- Pause controls, data deletion, and permission guidance.
- Clear states for denied access, missing history, and stale data.

### Deferred

- Exact Reel/Short/TikTok counts or scroll gestures.
- Floating live XP overlays and continuous foreground services.
- 3D worlds, freeform terrain editing, combat, or complex crafting.
- Paid random rewards, tradable items, and cash-value rewards.
- Global rankings, chat, guilds and competitions with independently verified scores or prizes. Small private self-reported balance circles are now included.
- AI-generated roasts and cloud processing of usage history.
- iOS implementation and server purchase verification. Local widgets and client-only Play subscriptions are now included.

## 5. XP and reward economy

All numbers in this section are initial balance hypotheses. Store the economy version with rewards so later changes do not rewrite earned history.

### Daily XP

```text
eligibleMinutes = floor(eligibleSelectedAppSeconds / 60)
usageXP = min(eligibleMinutes × 2, 120)
dailyGoalXP = 100 when a full eligible day is finalized within its budget
dailyXP = usageXP + dailyGoalXP
```

- The 120-XP cap applies across all selected apps together.
- Round once after combining eligible seconds; frequent refreshes must not discard partial minutes.
- Additional selected-app usage beyond 60 minutes grants no additional usage XP.
- Budget success compares actual seconds against the budget in seconds; the display's rounded minutes do not determine success.
- Zero usage can earn the goal bonus if the full-day tracking data is usable.
- Opening ScrollXP repeatedly grants no additional XP.
- Today's goal remains provisional until the day is complete.
- Missing data produces an unknown challenge result, not an automatic success or failure.
- Usage time is an approximation of app activity; it cannot prove watching or scrolling.

Example with a 45-minute daily budget:

| Selected-app usage | Usage XP | Goal XP | Total XP |
| --- | ---: | ---: | ---: |
| 0 minutes, valid coverage | 0 | 100 | 100 |
| 30 minutes | 60 | 100 | 160 |
| 45 minutes | 90 | 100 | 190 |
| 90 minutes | 120 | 0 | 120 |

The capped minute reward still provides some incentive to accumulate usage below the cap. Monitor this explicitly. If beta users increase consumption to farm XP, reduce that component and shift progress toward completed goals before expanding the product.

### Budget rules

- The first rewarded budget starts on the next full eligible day.
- Users choose a realistic target during onboarding. After sufficient history exists, show suggestions based on a recent measured baseline.
- Budget and selected-app changes take effect on the next challenge day.
- Freeze the day's goal, selected apps, timezone, and economy version in its challenge record.
- A user can relax tomorrow's goal without losing previously earned items.
- Pausing tracking makes affected intervals ineligible for goal evaluation.
- A calendar-week challenge requires five successful eligible days; they need not be consecutive.
- Changes that prevent reliable day reconciliation must not create an extra daily bonus.

### Levels and milestones

Use cumulative lifetime XP. XP is not spent when a building is unlocked.

Initial level formula: advancing from level `L` requires `100 + 25 × (L − 1)` additional XP. Display both the current level's progress and cumulative lifetime XP.

| Lifetime XP | Example milestone |
| ---: | --- |
| 0 | Starter house and basic island |
| 100 | Campfire and first cosmetic choice |
| 300 | Garden |
| 600 | Workshop |
| 1,200 | Castle wing |
| 2,500 | Dragon companion |
| 5,000 | Second island area |
| 10,000 | Town expansion |

Spread smaller decoration unlocks between these milestones. Test the first week separately from late progression. Show locked content before it is earned so users can choose what to work toward.

### Chests

- Current implementation: one welcome chest at setup and milestone chests at 100, 300, 600, 1,200, 2,500, 5,000, and 10,000 lifetime XP. One calendar-week chest requires five finalized successful budget days.
- Award chests at selected lifetime milestones and for the weekly challenge.
- Every chest contains a cosmetic; no monetary value or tradability.
- Explain the possible contents and rarity probabilities before opening.
- Current base rarity table: 70% common, 25% uncommon, and 5% rare. There are seven common, four uncommon, and two rare chest cosmetics.
- Specify duplicate protection precisely: choose from unowned items within the rolled rarity; if none remain, use a documented fallback to another unowned rarity.
- Current duplicate fallback: choose uniformly from unreserved items in the rolled rarity; if exhausted, choose uniformly from the lowest remaining rarity.
- Once all 13 chest cosmetics are owned or reserved in unopened chests, clearly replace the random chest with a known keepsake star for the shelf.
- Store the chest contents exactly once when it is earned. Opening or retrying only reveals the stored result.
- Earned chests remain available; no expiry countdown or paid reroll.
- Weekly chests and milestone chests have unique reward keys.

## 6. Retention and personality

| Mechanism | Implementation | What to measure |
| --- | --- | --- |
| World ownership | Island name, chosen appearance, decoration placement | Customization after onboarding |
| Anticipation | Next-unlock silhouette with exact progress | Return visits and unlock views |
| Collection | Several small themed cosmetic sets | Set completion and item preference |
| Choice | Select a cosmetic at major milestones | Choice completion and variety |
| Weekly purpose | Five successful days earn a chest | Challenge participation |
| Surprise | Earned chest reveal with duplicate protection | Reward satisfaction |
| Humor | Optional comments on stats and unlocks | Personality selection and opt-outs |
| Social expression | User-triggered island share image | Voluntary sharing |

Keep interaction animations short, skippable where appropriate, and compatible with reduced motion. Support independent sound and haptic settings.

Examples of optional comments:

- Gentle: “Your garden grew a little today.”
- Sarcastic: “Your village now has a library. An ambitious choice.”
- Goal success: “Budget completed. Your dragon respects boundaries.”
- Cap reached: “Today's usage XP is complete. Your island will be here later.”

Avoid messages that insult a person's worth, health, body, or identity. Do not increase notification frequency when the user misses a day. Preserve earned world progress during absence.

Reminders should be opt-in, respect quiet hours, and start with at most one scheduled reminder per day. Offer user-controlled timing. Do not promise precise background reward notifications in the MVP.

## 7. Screens and interaction design

Use four primary destinations: **Home, World, Goals, Stats**. Put settings behind a clear icon rather than a fifth primary tab.

### Home

- Level, XP progress, and next milestone.
- Today's combined usage and per-app breakdown.
- Daily budget with provisional status.
- Earned reward collection action.
- Small island preview that opens World.
- Last-updated time and tracking-status explanation.

### World

- Island with fixed building and decoration slots.
- Tap a slot to view compatible owned items and previews.
- Show locked landmarks and their milestone requirements.
- Item collection panel with rarity and set membership.
- User-triggered share action, with no usage numbers included by default.

### Goals

- Today's locked budget and selected apps.
- Clear start/end boundaries for the challenge.
- Tomorrow's editable budget.
- Weekly five-day progress.
- Explanations for pending or unknown results.

### Stats

- Seven-day combined usage chart.
- Per-app totals and completed-goal history.
- Separate measured usage from earned XP.
- Mark unavailable days and partial days explicitly.

### Settings

- Tracked apps and pending changes.
- Usage Access status and settings shortcut.
- Personality, sound, haptics, and reduced-motion options.
- Reminder settings and quiet hours.
- Pause tracking with its effect on challenges explained.
- Delete local history and reset the world through a clear confirmation.
- Privacy information and measurement limitations.

### Visual direction

Use a cozy fantasy island, calm surfaces, a distinctive XP accent, and readable typography. Prototype the dashboard and island together before polishing illustrations. Support font scaling, accessible contrast, meaningful icon labels, and feedback that does not depend only on color.

## 8. Android tracking design

### Acquisition

1. Declare the Usage Access capability required by UsageStatsManager.
2. Explain the data and purpose before sending the user to Android settings.
3. Recheck access when ScrollXP resumes.
4. Query relevant usage events over a bounded history window.
5. Reconstruct eligible intervals using activity transitions and screen/keyguard events where available.
6. Preserve unresolved session boundaries for later reconciliation.
7. Refresh at app launch, on foreground return, and through periodic background work.

Android retains event history for a limited period. Long gaps between reconciliations may make some days unrecoverable. WorkManager scheduling is best-effort and has a minimum periodic interval of 15 minutes; it cannot provide an exact per-minute reward timer.

### Time accounting

- Count qualifying selected-app activity while the device is interactive and unlocked, according to a documented measurement policy.
- Do not infer video content or scroll gestures from foreground usage.
- Split intervals at challenge boundaries.
- Deduplicate overlapping event windows before calculating totals.
- Make combined eligible duration the union of qualifying intervals so simultaneous apps cannot double the daily cap.
- Define attribution for split-screen and picture-in-picture during the prototype. If attribution is ambiguous, show uncertainty and withhold affected goal evaluation rather than inventing precision.
- Reconcile open sessions after app switching, process death, or reboot using available evidence.
- Detect impossible durations and clock discontinuities.
- Store UTC timestamps alongside the timezone and boundaries used for each day.
- Apply timezone changes only to future challenge windows, without overlapping rewarded periods.

### Tracking states

Use explicit states such as `READY`, `ACCESS_REQUIRED`, `PARTIAL`, `STALE`, and `UNAVAILABLE`. Separately track a day's suitability for challenge evaluation.

Permission checks and periodic workers cannot prove every intervening state. The prototype must determine what completeness can actually be established on supported devices. Do not advertise perfect tracking or use these client-only records for authoritative competition.

### Battery and privacy

- Avoid continuous polling, overlays, and a foreground service in the MVP.
- Restrict queries to the data needed for selected apps and reconciliation.
- Keep detailed event-derived records on the device.
- Do not collect watched content, screenshots, messages, or contacts.
- Keep raw event retention short and retain daily aggregates only as needed for history.
- Configure backups deliberately; exclude sensitive usage records from automatic cloud backup unless the intended behavior is disclosed and chosen.
- Later analytics should use minimal product events without package names or raw usage timelines.

## 9. Proposed code organization

This is a proposed structure for implementation, not a claim about code already present.

```text
com.scrollxp.app
├── data
│   ├── local          # Room entities, DAOs, migrations
│   ├── preferences    # DataStore settings
│   ├── tracking       # Usage Access and Android event adapter
│   └── repository     # Usage, rewards, goals, and world persistence
├── domain
│   ├── model          # App, interval, goal, reward, item, world
│   ├── tracking       # Interval reconstruction and day allocation
│   ├── progression    # XP, levels, and unlock rules
│   └── challenge      # Eligibility and completed-day evaluation
├── worker             # Reconciliation and reminder scheduling
└── ui
    ├── onboarding
    ├── home
    ├── world
    ├── goals
    ├── stats
    ├── settings
    └── theme
```

Use ViewModels with immutable UI state, StateFlow, and lifecycle-aware collection. Keep XP calculations and interval reconstruction as pure Kotlin logic. Start with a single app module and explicit dependency construction; add a DI framework only when it reduces complexity.

### Proposed persistent records

| Record | Key contents |
| --- | --- |
| TrackedApp | Package identity, selected status, effective time |
| UsageInterval | App, UTC boundaries, provenance, uncertainty |
| DailyUsage | Challenge-day identity, app seconds, combined seconds, coverage state |
| DailyChallenge | Frozen budget, app set, timezone, boundaries, status, economy version |
| RewardLedger | Unique reward key, source, XP delta, item/chest reference, creation time |
| Chest | Source key, stored contents, reveal state |
| InventoryItem | Item identity, variant, unlock source |
| WorldPlacement | Slot identity and placed owned item |
| ReconciliationState | Checkpoint, unresolved sessions, last successful refresh |
| Preferences | Personality, reminders, sound, haptics, quiet hours |

### Reward consistency

- Reward writes and related inventory changes run in a database transaction.
- Grant usage XP as the positive difference between the day's entitlement and already-awarded usage XP.
- Treat first-grant usage XP as permanent. Later measurement corrections can adjust displayed usage and goal eligibility without silently deleting items; record the discrepancy.
- Use unique keys such as `usage:<day>:<version>`, `goal:<day>:<version>`, and `weekly:<week>:<version>` with appropriate cumulative update handling for usage.
- Ensure changing the economy version cannot bypass reward uniqueness for an already-rewarded source period.
- Derive lifetime XP from the ledger or reconcile any cached total against it.
- Unlock each milestone once, even when a large award crosses several thresholds.
- Freeze a chest's result before presenting its reveal animation.

## 10. Implementation phases and acceptance criteria

### Phase 0 — Project setup and baseline build

- Verify package, minimum SDK, installed SDK, Gradle, and Java configuration.
- Build the generated debug app.
- Run it on an emulator and at least one physical phone.
- Establish version control and exclude local SDK paths, build outputs, signing material, and credentials.
- Record the development setup and build commands.

**Exit:** the baseline app launches, and another developer can follow the setup instructions.

### Phase 1 — Tracking feasibility, approximately week 1

- Build a temporary tracking screen with permission state, selected apps, measured totals, and last refresh time.
- Implement interval reconstruction and conservative uncertainty handling.
- Test controlled sessions on available phones, ideally covering Pixel, Samsung, and another manufacturer.
- Compare stopwatch sessions and system usage reports while documenting differences in measurement semantics.
- Evaluate battery behavior and recoverability after background restrictions.

**Exit:** normal single-app foreground sessions reconcile credibly; known ambiguous conditions are documented and surfaced. If tracking is unreliable on the target devices, resolve the scope before building the game.

### Phase 2 — Product foundation, approximately week 2

- Add Room, preferences, repositories, and navigation.
- Build onboarding, app selection, and access guidance.
- Add Home and Stats with real data and honest empty states.
- Freeze daily challenge boundaries and pending settings changes.

**Exit:** a new user can set up tracking, inspect usage, and understand their next eligible challenge day.

### Phase 3 — World and progression, approximately weeks 3–4

- Build the island and fixed placement slots.
- Add starter gifts, XP ledger, level progression, and milestone unlocks.
- Create the first approximately 20 items and basic animations.
- Implement inventory and persistent customization.

**Exit:** real usage produces one consistent reward stream, and the world survives restart without duplicate awards.

### Phase 4 — Challenges and earned rewards, approximately week 5

- Finalize full-day goals after reconciliation.
- Add weekly five-day progress and earned chests.
- Implement duplicate protection, curated personality, and optional reminders.
- Test zero-usage success, missed days, uncertain days, and reminder settings.

**Exit:** the first week offers understandable progression without requiring longer scrolling sessions.

### Phase 5 — Reliability and beta, approximately week 6

- Complete tracking edge-case tests and database recovery checks.
- Test offline use, process death, reboot, access removal, font scaling, and reduced motion.
- Review permissions, backup behavior, deletion, battery use, and accessibility.
- Distribute a beta to 30–50 adult Android users.

**Exit:** no unresolved critical data-loss, duplicate-reward, or misleading-tracking issues; participants understand the product.

### Phase 6 — Balance and launch preparation, approximately weeks 7–8

- Analyze return behavior, goal participation, and consumption changes.
- Tune early unlock pacing and simplify confusing screens.
- Prepare screenshots, description, privacy information, and support contact.
- Complete applicable Play Console declarations and release testing.
- Confirm current store requirements and use staged rollout when available.

**Exit:** release checklist passes and product behavior matches the store description.

## 11. Meaningful validation

### Automated domain and storage tests

- Event replay cannot double usage duration.
- Partial-minute accumulation survives repeated refreshes.
- Screen locking closes eligible measurement intervals according to policy.
- Sessions crossing midnight allocate to the correct frozen day boundaries.
- Overlapping selected-app intervals cannot double combined elapsed time.
- Unknown history cannot receive the completed-day goal bonus.
- A zero-usage eligible day can receive that bonus.
- Usage cap, level boundaries, and multi-milestone awards are correct.
- Repeated reconciliation cannot duplicate goal or weekly rewards.
- Chest contents remain identical across retries and process restart.
- Database transactions do not leave XP and inventory inconsistent.
- Timezone and economy changes cannot create duplicate reward periods.

### Device acceptance checks

- Controlled 5-, 15-, and 30-minute sessions across selected apps.
- Switching between selected and unselected apps.
- Locked screen, interrupted session, reboot, and process termination.
- Access denied, removed, restored, and tracking paused.
- Battery saver and manufacturer background restrictions.
- Split-screen, picture-in-picture, long inactivity, and missing event history.
- Internet disconnected: tracking, world, and goals remain usable locally.
- Large text, screen reader labels, color contrast, and reduced motion.

Record the observed measurement error and supported conditions. Set the launch tolerance after prototype evidence instead of promising arbitrary precision.

## 12. Beta measurement and decision rules

| Metric | Definition / use |
| --- | --- |
| Setup completion | Percentage reaching a configured island and tracked app set |
| Access completion | Percentage of users who choose and successfully enable access |
| First customization | Percentage placing or changing a world item |
| Day-1 retention | Activated users returning on the next local calendar day |
| Day-7 retention | Activated users returning on the seventh local calendar day |
| Goal participation | Eligible days with a chosen budget and evaluated result |
| Goal completion | Successful evaluated days divided by evaluated eligible days |
| Reward engagement | Earned chests revealed and unlocks placed |
| Tracking trust | Complaints, unexplained corrections, and unavailable days |
| Notification acceptance | Opt-in, opt-out, and complaint rates |
| Consumption change | Within-user selected-app usage before/after, with incomplete data excluded |

Start with qualitative interviews and local diagnostic summaries. If analytics is added, define minimal event schemas and disclose collection; never place raw usage events in analytics or crash logs.

Suggested hypotheses: most participants can explain the reward rules, at least half customize the world after onboarding, and some return after a week specifically for their world or goals. Small-beta percentages are directional evidence, not reliable industry benchmarks.

Pause economy expansion if consumption rises because users are farming XP. Investigate notification opt-outs before adding more reminders. Investigate low access completion through permission clarity and preview value. Investigate good setup but weak return behavior through world choices and pacing.

## 13. Monetization and later development

Keep usage tracking, core goals, and the first world free during validation.

Optional auto-renewing monthly Pro is now implemented at the user's request, adding three themes and a 90-day local journal. Existing widget and 30-day history stay free. Google Play supplies the localized price and handles payment; Firebase stays on Spark. Product/key setup and real Play purchase validation are required before sale. Local signature checks and acknowledgement do not replace secure server verification.

Suggested later order:

1. More cosmetic sets and island themes.
2. Optional cloud backup with explicit usage-data choices.
3. User-triggered friend visits and shared building projects.
4. Fair goal-based competitions with server-side reward validation.
5. Widgets and deeper statistics.
6. Separate iOS feasibility study before committing to cross-platform parity.

Client-side records can be modified or spoofed. Server validation can limit abuse but cannot prove that foreground time was active scrolling. Keep future competitive stakes low and cosmetic.

## 14. Principal risks

| Risk | Response |
| --- | --- |
| Users mistake app time for Reel/Short counts | Accurate labels in onboarding, stats, and listing |
| Background collection is delayed | Reconcile on return and show last-updated time |
| Missing events invalidate a day | Explicit unknown state; no fabricated goal result |
| Users farm foreground time | Capped minute XP, goal rewards, no raw-usage leaderboard |
| XP farming increases consumption | Measure and rebalance the usage reward component |
| Permission request reduces onboarding | Preview world first; explain access clearly |
| World content becomes repetitive | Small themed sets and meaningful cosmetic choices |
| Economy grows too complicated | Start with XP and cosmetics only |
| Artwork delays release | Fixed slots, reusable assets, one island |
| Sensitive history enters backups or logs | Deliberate backup rules and minimal diagnostics |
| Local cheating affects competition | Defer rankings and later design validation explicitly |

## 15. Launch checklist

- [x] Generated baseline debug build verified with `:app:assembleDebug`.
- [ ] Physical-device launch verified.
- [ ] Tracking semantics and supported conditions documented.
- [ ] Permission denial, removal, and missing-data states verified.
- [x] Reward ledger and chest reveal are idempotent under emulator persistence/concurrency tests.
- [ ] Daily and weekly boundaries survive clock/timezone changes conservatively.
- [ ] First-week progression is complete and understandable.
- [ ] Local data deletion and backup configuration verified.
- [ ] Accessibility, offline behavior, and battery checks completed.
- [ ] No critical crashes or data-loss issues remain in beta.
- [x] Prepared store description accurately states whole-app foreground-time estimates; not yet submitted.
- [ ] Applicable privacy and Play disclosures completed.
- [ ] Monetization remains disabled until its own implementation is verified.

## 16. Immediate next tasks

1. Collect feedback on the three destinations and first-chapter guide; refine progression pacing and onboarding.
2. Finish broader accessibility/offline coverage; the release policy at larger text, icon variants, and signed cold launch are already checked. Host the prepared public privacy HTML and configure Play Console using the release materials.
3. Perform the deferred real-phone tracking, battery, permission, reminder timing, and manufacturer checks when the user is ready.
4. Complete release privacy, accessibility, and store preparation before publishing.

## 17. Official technical references

- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager): permission and available usage statistics/events.
- [WorkManager work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work): periodic scheduling constraints.
- [Jetpack Compose](https://developer.android.com/compose): Android interface toolkit.
- [Room](https://developer.android.com/training/data-storage/room): local structured storage.
- [Google Play sensitive API policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en): prefer narrowly scoped APIs over AccessibilityService when suitable.
- [Google Play Billing](https://developer.android.com/google/play/billing): client subscription integration and required Play configuration.

Recheck release-sensitive platform and store guidance during implementation and before publication.
