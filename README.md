# SmartFamily

Family home-management Android app: shared shopping, chores, meals, appliances, family chat and dates, on one encrypted database shared by the family. Hebrew (RTL) and English.

**Current version: 0.2.0 — Phase 1 (shopping and home inventory)**

## What is new in 0.2.0

- Smart shopping lists per family: several lists, templates (e.g. "Shabbat basics"), items grouped by store aisle
- Add items by typing (with suggestions from purchase history), by voice in Hebrew or English, by barcode (Open Food Facts, free) or by photo (Gemini, your own free key)
- Quantities, units, preferred brand, notes, recurring items; same-name items merge
- Children's items wait for a parent's approval
- "I'm at the store" shows the family who is shopping; open lists refresh every few seconds
- "Finish shopping" records the purchase history and can move items into the home inventory
- Home inventory: fridge, freezer, pantry, cleaning cupboard, bathroom; expiry dates with a daily reminder; minimum stock that suggests items for the list
- Home screen: modules can be reordered (Settings → Modules), and available modules open from their card

## Foundation (0.1.0)

- First-launch wizard: language, your name, first family, starting modules
- Several families per user, with a family switcher
- Roles: owner, admin, member, child, guest; managed child profiles without email
- Invite codes (single use, 48 h) and join requests that an admin approves
- Module switches at family level (admins) and personal level
- Local database encrypted with SQLCipher (AES-256), key held in the Android Keystore
- Offline-first sync with your own Supabase project (row-level security per family)
- Encrypted backup file (Argon2id + AES-256-GCM) and restore
- Material 3 design, light/dark, optional wallpaper colours, Help on every screen

The other modules show on the home screen with the version they arrive in (see the spec, section 21).

## Connecting Supabase (free plan)

1. Create a project at supabase.com.
2. SQL Editor → New query → paste `supabase/migrations/0001_init.sql` → Run. Then do the same with `0002_shopping_inventory.sql` (and every later file, in number order).
3. Project Settings → API: copy the **Project URL** and the **anon public** key.
4. In the app: Settings → Cloud sync → paste both → Save → create an account or sign in.

Only the URL and the public anon key are stored in the app. Row-level security on the server decides what each user can read and write.

## Building

GitHub Actions builds every push to `main` and uploads `smartfamily-v<version>.apk` as an artifact (kept 7 days). Pushing a tag `v*` also publishes a GitHub release with the APK.

The version is set in `gradle.properties` (`APP_VERSION_NAME`, `APP_VERSION_CODE`).

### Signing

Add these repository secrets so every build is signed with the same key (required for updates over an installed app):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the `.jks` file, base64-encoded |
| `SIGNING_KEYSTORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

Without them the build still runs, signed with a temporary key.
