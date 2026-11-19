# SmartFamily

Family home-management Android app: shared shopping, chores, meals, appliances, family chat and dates, on one encrypted database shared by the family. Hebrew (RTL) and English.

**Current version: 0.6.0 — Phase 5 (encrypted family chat)**

## What is new in 0.6.0

- Family chat, group chats and private chats, end-to-end encrypted (Tink HPKE X25519 + AES-256-GCM). The server stores only ciphertext.
- Every phone publishes its public key; chat keys are sealed per device and re-shared automatically to new phones and members. Removing a member rotates the key.
- Text, photos, files and voice messages (hold the mic). Media is encrypted on the phone before upload to the private `chat-media` bucket.
- Voice transcription with Gemini (free tier, your own key).
- Replies, reactions, edit, delete for everyone, read receipts, search, disappearing messages, safety numbers.
- Notifications for new messages after each sync (held during Shabbat). Instant push (FCM) comes later.
- The chat private key is included in the encrypted backup.
- Supabase: `0006_app_0_6.sql` and `0006_chat_storage.sql`.

## What was new in 0.5.0

- Appliances: washer, dryer, washer-dryer, dishwasher, AC, car, oven, fridge, water heater; brand, model, serial, warranty, store, service phone
- Fill brand/model/serial from a photo of the label (AI)
- A structured guide per appliance (programs, buttons, symbols, error codes, maintenance, safety, with manual page numbers) built from: the PDF manual (Gemini), any AI chat (copy the prompt, attach the PDF, paste the answer back — free), a photo of the panel or a manual page, or common programs as a start
- "What to run": picks one of your own machine's programs by fabric, colour, soil and the care-label photo; timer on the phone's clock; presets per person
- Maintenance tasks become recurring chores
- Live status: Bosch/Siemens via Home Connect (device-flow sign-in; start a program when remote start is on), Samsung via SmartThings (status)
- Tadiran ACs: guide and reminders only (no open interface)

## What is new in 0.4.0

- Recipe book: meal types, kashrut, servings, times, ingredients, steps, favourites, ratings, "cooked N times"
- Import a recipe from a website using its schema.org data (free); AI reads the page or a photo of a handwritten recipe when needed
- Scale servings; send missing ingredients (not in the home inventory) to the shopping list
- AI: "what to cook" from meal, kashrut, time, pantry and what expires soon; substitutes that keep kashrut; dishes that complete a meal
- Cooking mode: one big step at a time, screen kept on, timers found in each step (also set on the phone's clock)
- Weekly menu with Friday-night, Shabbat lunch and seuda shlishit slots, Hebrew dates, copy last week, week's ingredients to the list
- Packed meals per person and place: rules, likes and dislikes, weekly plan (offline, or with AI), packed/eaten/came back, evening reminder
- The family's Supabase project is preset in the app; just create an account in Settings → Cloud sync

## What is new in 0.3.0

- Chores with schedules (daily, set weekdays, every N days, before Shabbat, once), owners, turns between people, steps, snooze
- 14 ready-made chores (laundry, bathrooms, sheets, floors, bins, Shabbat cooking, hot plate, lights timer, table, candles…)
- Shabbat card on the home screen: parasha or holiday, candle lighting and havdalah for your city, countdown
- "Before Shabbat" chores are timed from candle lighting (KosherJava, computed on the phone)
- Kids: points for chores after a parent approves, rewards to redeem
- Family dates by Hebrew or Gregorian date (Adar in leap years, after-sunset births), with reminders
- Family calendar with the Hebrew date and holidays on every day, events with repeats
- Reminders every 15 minutes, held from candle lighting to havdalah
- Supabase: run `supabase/migrations/0003_app_0_3.sql`

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
