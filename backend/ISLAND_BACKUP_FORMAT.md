# Shared island backup, version 1

Android and iOS use the existing Android version 1 payload and the same verified Firebase account. No rules migration or new collection is needed. The normative example is `fixtures/cross-platform/island-v1.json`.

Firestore stores one document at `users/{uid}/backups/latest`: integer `version` (1), string `payload` (UTF-8 JSON), string `name`, integer `xp`, and server timestamp `updatedAt`. Both clients validate the payload and require the outer version, name and XP to agree before displaying or restoring it. Only the verified owner may read/write; deletion guards block old sessions. Firebase transport/storage protection applies; this is not end-to-end encryption.

## Payload

| Field | Meaning and limits |
| --- | --- |
| `version` | Integer 1; unsupported versions are rejected. |
| `profile.name` | Nonblank; at most 28 UTF-16 code units. |
| `profile.budget`, `roof` | Integers, 15–180 minutes and 0–2. |
| `profile.personality` | `Gentle`, `Sarcastic`, or `Silent`. |
| `profile.hidden` | Known item IDs joined with `\|`; new iOS exports sort IDs. |
| `profile.zone` | Saved calendar timezone; app-generated IANA ID or `UTC`. |
| `profile.appearance`, `region` | `Daylight` / `Dusk` / `System`; `meadow` / `cove` / `clouds`. |
| `profile.reducedMotion`, `haptics`, `guideDismissed`, `hasPlacedTreasure` | Boolean game/preferences flags. |
| `rewards[]` | Unique `key`, ISO calendar `date` (`yyyy-MM-dd`), `kind`, integer `xp`. `USAGE` key `usage:{date}` awards 0–120; `GOAL` key `goal:{date}` awards exactly 100. Existing odd usage awards remain readable. At most 18,000 entries and 100 million total XP. |
| `chests[]` | Unique `key`, `source`, integer `earnedAt`, `itemId`, `rarity`, nullable integer `openedAt`, integer `economyVersion` (1). At most 2,000 entries. Source/key at most 80 UTF-16 units. |

Chest keys are `milestone:{threshold}` for an earned threshold (0, 100, 300, 600, 1200, 2500, 5000, 10000), or `week:{date}`. Cosmetic IDs and rarity match the shared thirteen-item catalog; reserved cosmetics are unique even when unopened. Multiple `keepsake` items are allowed. Dates/timestamps are validated. Chest timestamps are **Unix milliseconds**, never Swift reference-date seconds. Allowed timestamps are 0–253402300799999. `openedAt: null` means unopened. Legacy iOS records lacked opening times; an opened record exports `openedAt: 0` to preserve that fact without inventing an opening date.

The payload is limited to **900,000 UTF-8 bytes** on both clients. JSON key order is irrelevant. New exports contain only documented fields. Both apps reject invalid rewards, duplicate reservations, wrong types, unknown item/region/rarity values and unsupported economies before changing local progress.

## Save and restore

Save, restore and cloud deletion are explicit, confirmed actions. Sign-in never uploads or restores an island automatically. Saving replaces the account's latest snapshot, including one from the other platform. Restore **replaces** game records rather than merging or adding XP, preserves exact reserved/opened chest contents, and rechecks account identity before the local atomic commit.

Selected app IDs/tokens, device permissions, raw/per-app usage, day/coverage records, breaks, pause settings, reminders, pending app choices and Pro entitlements are absent from the payload. Restore preserves local app selections, pause and reminder preferences, clears usage/coverage and pending changes, and restarts conservative tracking. Android preserves local onboarding; an iPhone may restore game progress before choosing its own apps. Repeated restore cannot award additional XP or reroll existing chests. A dated usage-XP ledger can imply capped activity and is disclosed in the privacy policy.

Earlier Android cloud backups remain compatible. iOS local imports accept both the shared format and earlier native iOS JSON; all new iOS manual exports use the shared format. Android's manual export remains its existing journal CSV; its shared JSON path is cloud backup.

## Acceptance checks

From the Android root, with backend npm dependencies installed and Android emulator `emulator-5554` booted:

```sh
python3 scripts/test_shared_backup.py --ios '../ScrollXP iOS'
```

This uses a named Android SDK demo app and the actual native Swift REST client. A local verified fixture account uploads 120 XP from Android; Swift restores it twice, saves 122 XP; Android restores twice, rejects mismatched metadata and a changed account, saves 124 XP; Swift reads it and deletes the cloud backup. Chest content/timestamps and local privacy are checked throughout. Only `demo-scrollxp` Auth/Firestore emulators are contacted. Android uses `adb reverse` for loopback ports; HTTP is permitted only on debug loopback addresses, never in Release. The iOS project remains in its separate sibling folder.

Real production-account email delivery and acceptance on two physical phones still require device testing.
