# SmartFamily

Family home-management Android app: shared shopping, chores, meals, appliances, family chat and dates, on one encrypted database shared by the family. Hebrew (RTL) and English.

**Current version: 0.1.0 — Phase 0 (foundation)**

## What is in 0.1.0

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
2. SQL Editor → New query → paste `supabase/migrations/0001_init.sql` → Run.
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
