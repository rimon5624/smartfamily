"""Specification of the family-scoped synced tables added from app 0.3 onwards.

Each table automatically gets: id (text, primary key), familyId, updatedAt, deletedAt and the
local-only dirty flag. Field tuples: (kotlin name, kotlin type, kotlin default or None).
policy:
  adults   - active members read; owner/admin/member write (children and guests read only)
  members  - every active member (children included) reads and writes
  admins   - active members read; owner/admin write
  self     - active members read; each member writes only rows with their own user_id
  sender   - active members read; only the sender writes (chat messages)
"""

TABLES = [
    # ---------- 0.3: chores, points, dates, calendar ----------
    dict(name="chores", entity="ChoreEntity", version=3, policy="adults", fields=[
        ("title", "String", None),
        ("category", "String", '"home"'),
        ("assignee", "String", '""'),            # userId, "" = anyone
        ("rotation", "String", '""'),            # comma-separated userIds taking turns
        ("schedule", "String", '"WEEKLY"'),      # DAILY, WEEKLY, EVERY_N_DAYS, BEFORE_SHABBAT, ONCE
        ("intervalDays", "Int", "7"),
        ("weekdays", "Int", "0"),                # bit 0 = Sunday ... bit 6 = Saturday
        ("timeOfDay", "Int", "1080"),            # minutes after midnight for the reminder
        ("hoursBeforeShabbat", "Int", "0"),
        ("estimatedMinutes", "Int", "15"),
        ("points", "Int", "0"),
        ("checklist", "String", '""'),
        ("nextDue", "Long", "0L"),
        ("lastDoneAt", "Long?", "null"),
        ("lastDoneBy", "String?", "null"),
        ("active", "Boolean", "true"),
        ("source", "String", '""'),              # e.g. appliance:<id> for maintenance tasks
        ("createdBy", "String", None),
    ], indices=[]),
    dict(name="chore_completions", entity="ChoreCompletionEntity", version=3, policy="members", fields=[
        ("choreId", "String", None),
        ("title", "String", None),
        ("doneBy", "String", None),
        ("doneAt", "Long", None),
        ("points", "Int", "0"),
        ("approved", "Boolean", "true"),
        ("approvedBy", "String?", "null"),
    ], indices=["choreId"]),
    dict(name="points_entries", entity="PointsEntryEntity", version=3, policy="adults", fields=[
        ("userId", "String", None),
        ("delta", "Int", None),
        ("reason", "String", '""'),
        ("createdAt", "Long", None),
    ], indices=["userId"]),
    dict(name="rewards", entity="RewardEntity", version=3, policy="adults", fields=[
        ("title", "String", None),
        ("cost", "Int", "10"),
    ], indices=[]),
    dict(name="family_dates", entity="FamilyDateEntity", version=3, policy="adults", fields=[
        ("title", "String", None),
        ("personName", "String", '""'),
        ("type", "String", '"BIRTHDAY"'),        # BIRTHDAY, ANNIVERSARY, YAHRZEIT, BAR_MITZVAH, ALIYAH, CUSTOM
        ("gregorian", "String", '""'),           # yyyy-MM-dd, "" when unknown
        ("hebrewDay", "Int", "0"),
        ("hebrewMonth", "Int", "0"),             # KosherJava month numbers (1 = Nisan ... 13 = Adar II)
        ("hebrewYear", "Int", "0"),
        ("drive", "String", '"GREGORIAN"'),      # which calendar sets the yearly date: GREGORIAN or HEBREW
        ("afterSunset", "Boolean", "false"),
        ("remindDays", "String", '"7,1,0"'),
        ("note", "String", '""'),
    ], indices=[]),
    dict(name="events", entity="EventEntity", version=3, policy="adults", fields=[
        ("title", "String", None),
        ("startAt", "Long", None),
        ("endAt", "Long", None),
        ("allDay", "Boolean", "false"),
        ("recurrence", "String", '"NONE"'),      # NONE, WEEKLY, MONTHLY, YEARLY
        ("location", "String", '""'),
        ("note", "String", '""'),
        ("ownerId", "String", None),
        ("remindMinutes", "Int", "60"),
        ("source", "String", '""'),
    ], indices=["startAt"]),

    # ---------- 0.4: recipes, meal plan, packed meals ----------
    dict(name="recipes", entity="RecipeEntity", version=4, policy="adults", fields=[
        ("title", "String", None),
        ("mealTypes", "String", '""'),           # comma-separated: breakfast,lunch,dinner,shabbat,holiday,snack
        ("kashrut", "String", '"parve"'),        # meat, dairy, parve
        ("tags", "String", '""'),                # vegetarian, gluten_free, ...
        ("servings", "Int", "4"),
        ("prepMinutes", "Int", "0"),
        ("cookMinutes", "Int", "0"),
        ("difficulty", "String", '"easy"'),
        ("ingredients", "String", '"[]"'),       # JSON list of {name, quantity, unit, note}
        ("steps", "String", '"[]"'),             # JSON list of strings
        ("equipment", "String", '""'),
        ("notes", "String", '""'),
        ("source", "String", '""'),
        ("favorite", "Boolean", "false"),
        ("timesCooked", "Int", "0"),
        ("lastCooked", "Long?", "null"),
        ("createdBy", "String", None),
    ], indices=[]),
    dict(name="recipe_ratings", entity="RecipeRatingEntity", version=4, policy="members", fields=[
        ("recipeId", "String", None),
        ("userId", "String", None),
        ("stars", "Int", "5"),
        ("comment", "String", '""'),
    ], indices=["recipeId"]),
    dict(name="meal_plan_entries", entity="MealPlanEntryEntity", version=4, policy="adults", fields=[
        ("date", "String", None),                # yyyy-MM-dd
        ("slot", "String", None),                # breakfast, lunch, dinner, shabbat_night, shabbat_lunch, seuda_shlishit, snack
        ("recipeId", "String?", "null"),
        ("title", "String", '""'),
        ("servings", "Int", "4"),
        ("note", "String", '""'),
    ], indices=["date"]),
    dict(name="meal_profiles", entity="MealProfileEntity", version=4, policy="adults", fields=[
        ("personId", "String", '""'),
        ("personName", "String", None),
        ("place", "String", '"school"'),         # kindergarten, school, army, work, trip
        ("rules", "String", '""'),
        ("likes", "String", '""'),               # one food per line
        ("dislikes", "String", '""'),
        ("kashrut", "String", '"dairy"'),
        ("weekdays", "Int", "31"),               # bit 0 = Sunday; default Sunday-Thursday
        ("maxRepeat", "Int", "2"),
    ], indices=[]),
    dict(name="packed_meals", entity="PackedMealEntity", version=4, policy="members", fields=[
        ("profileId", "String", None),
        ("date", "String", None),
        ("items", "String", '""'),               # one item per line
        ("status", "String", '"PLANNED"'),       # PLANNED, PACKED, UNEATEN, EATEN
        ("feedback", "String", '""'),
    ], indices=["profileId", "date"]),

    # ---------- 0.5: appliances ----------
    dict(name="appliances", entity="ApplianceEntity", version=5, policy="adults", fields=[
        ("type", "String", '"washer"'),          # washer, dryer, washer_dryer, dishwasher, ac, car, oven, fridge, water_heater, other
        ("name", "String", None),
        ("brand", "String", '""'),
        ("model", "String", '""'),
        ("serial", "String", '""'),
        ("purchaseDate", "String", '""'),        # yyyy-MM-dd
        ("warrantyEnd", "String", '""'),
        ("store", "String", '""'),
        ("price", "Double", "0.0"),
        ("servicePhone", "String", '""'),
        ("notes", "String", '""'),
        ("ecosystem", "String", '""'),           # "", home_connect, smartthings
        ("externalId", "String", '""'),          # the device id in that ecosystem
        ("profile", "String", '""'),             # structured manual JSON (APL-7)
        ("profileSource", "String", '""'),       # pdf, ai_chat, photo, text, generic
        ("createdBy", "String", None),
    ], indices=[]),
    dict(name="laundry_presets", entity="LaundryPresetEntity", version=5, policy="adults", fields=[
        ("applianceId", "String", None),
        ("name", "String", None),
        ("program", "String", '""'),
        ("temperature", "String", '""'),
        ("spin", "String", '""'),
        ("options", "String", '""'),
        ("note", "String", '""'),
    ], indices=["applianceId"]),

    # ---------- 0.6: end-to-end encrypted chat ----------
    dict(name="user_keys", entity="UserKeyEntity", version=6, policy="self", fields=[
        ("userId", "String", None),
        ("deviceId", "String", None),
        ("publicKeyset", "String", None),        # Tink HPKE public keyset (JSON); the private key never leaves the phone
        ("fingerprint", "String", '""'),         # safety number shown for verification (CHT-12)
    ], indices=["userId"]),
    dict(name="chats", entity="ChatEntity", version=6, policy="members", fields=[
        ("kind", "String", '"GROUP"'),           # FAMILY, GROUP, DIRECT
        ("title", "String", '""'),
        ("members", "String", '""'),             # comma-separated userIds
        ("epoch", "Int", "1"),                   # current key generation; rotated when someone leaves (CHT-13)
        ("createdBy", "String", None),
        ("lastMessageAt", "Long", "0L"),
        ("disappearDays", "Int", "0"),
    ], indices=[]),
    dict(name="chat_keys", entity="ChatKeyEntity", version=6, policy="members", fields=[
        ("chatId", "String", None),
        ("userId", "String", None),
        ("deviceId", "String", None),
        ("epoch", "Int", None),
        ("wrapped", "String", None),             # the chat key encrypted to that device's public key
    ], indices=["chatId"]),
    dict(name="chat_messages", entity="ChatMessageEntity", version=6, policy="sender", fields=[
        ("chatId", "String", None),
        ("senderId", "String", None),
        ("epoch", "Int", None),
        ("nonce", "String", None),
        ("ciphertext", "String", None),          # AES-256-GCM of the message JSON (text, media keys, transcript)
        ("kind", "String", '"text"'),            # text, image, voice, file
        ("sentAt", "Long", None),
        ("editedAt", "Long?", "null"),
        ("replyTo", "String", '""'),
        ("mediaPath", "String", '""'),           # encrypted file in Supabase Storage (bucket chat-media)
    ], indices=["chatId"]),
    dict(name="chat_reactions", entity="ChatReactionEntity", version=6, policy="self", fields=[
        ("chatId", "String", None),
        ("messageId", "String", None),
        ("userId", "String", None),
        ("emoji", "String", None),
    ], indices=["messageId"]),
    dict(name="chat_reads", entity="ChatReadEntity", version=6, policy="self", fields=[
        ("chatId", "String", None),
        ("userId", "String", None),
        ("lastReadAt", "Long", "0L"),
    ], indices=["chatId"]),

    # ---------- 0.7: travel packing, budget, home book and knowledge, polls ----------
    dict(name="trips", entity="TripEntity", version=7, policy="adults", fields=[
        ("title", "String", None),
        ("destination", "String", '""'),
        ("lat", "Double", "0.0"),
        ("lon", "Double", "0.0"),
        ("startDate", "String", '""'),           # yyyy-MM-dd
        ("endDate", "String", '""'),
        ("kinds", "String", '""'),               # comma-separated: BEACH, CITY, NATURE, CAMPING, HOTEL, ABROAD, SHABBAT, BABY
        ("travelers", "String", '""'),           # comma-separated userIds
        ("weather", "String", '""'),             # short forecast summary from Open-Meteo
        ("createdBy", "String", None),
    ], indices=[]),
    dict(name="trip_items", entity="TripItemEntity", version=7, policy="members", fields=[
        ("tripId", "String", None),
        ("title", "String", None),
        ("category", "String", '"general"'),
        ("ownerId", "String", '""'),             # "" = shared item
        ("qty", "Int", "1"),
        ("packed", "Boolean", "false"),
        ("packedBy", "String", '""'),
        ("sort", "Int", "0"),
    ], indices=["tripId"]),
    dict(name="budget_entries", entity="BudgetEntryEntity", version=7, policy="adults", fields=[
        ("kind", "String", '"EXPENSE"'),         # EXPENSE or INCOME
        ("amount", "Double", None),
        ("category", "String", '"other"'),
        ("note", "String", '""'),
        ("date", "Long", None),
        ("payer", "String", '""'),
        ("monthly", "Boolean", "false"),         # a fixed monthly item (rent, kindergarten...)
        ("createdBy", "String", None),
    ], indices=["date"]),
    dict(name="budget_limits", entity="BudgetLimitEntity", version=7, policy="adults", fields=[
        ("category", "String", None),
        ("monthly", "Double", "0.0"),
    ], indices=[]),
    dict(name="home_entries", entity="HomeEntryEntity", version=7, policy="adults", fields=[
        ("section", "String", '"HOME"'),         # HOME (home book) or KNOWLEDGE (knowledge box)
        ("kind", "String", '"NOTE"'),            # CONTACT, WARRANTY, SERVICE, DOCUMENT, NOTE, HOWTO
        ("title", "String", None),
        ("body", "String", '""'),
        ("phone", "String", '""'),
        ("url", "String", '""'),
        ("expiresAt", "Long?", "null"),
        ("tags", "String", '""'),
        ("pinned", "Boolean", "false"),
        ("createdBy", "String", None),
    ], indices=[]),
    dict(name="polls", entity="PollEntity", version=7, policy="members", fields=[
        ("question", "String", None),
        ("options", "String", None),             # one option per line
        ("multi", "Boolean", "false"),
        ("anonymous", "Boolean", "false"),
        ("closesAt", "Long", "0L"),              # 0 = open until closed by hand
        ("closed", "Boolean", "false"),
        ("createdBy", "String", None),
    ], indices=[]),
    dict(name="poll_votes", entity="PollVoteEntity", version=7, policy="self", fields=[
        ("pollId", "String", None),
        ("userId", "String", None),
        ("choices", "String", '""'),             # comma-separated option indexes
    ], indices=["pollId"]),

    # ---------- 0.9: sharing between families ----------
    # Table, functions and row-level security are hand-written in 0009_a_sharing.sql (sql=False).
    dict(name="family_shares", entity="FamilyShareEntity", version=9, policy="admins", sql=False, fields=[
        ("targetFamilyId", "String", None),
        ("sourceName", "String", '""'),
        ("targetName", "String", '""'),
        ("module", "String", None),              # AppModule key, e.g. recipes, calendar, chat
        ("itemId", "String", '""'),              # "" = the whole module
        ("itemTitle", "String", '""'),
        ("canWrite", "Boolean", "false"),
        ("status", "String", '"PENDING"'),       # PENDING, ACCEPTED, DECLINED (set by the receiving family's admin)
        ("createdBy", "String", None),
        ("decidedBy", "String", '""'),
    ], indices=["targetFamilyId"]),
]

# What another family can be given access to (0.9). table -> (module key or SQL expression, item column or None).
# Item None: only whole-module shares apply. Chat tables are further limited to the chat's own members.
SHARE = {
    "chores": ("'chores'", "id"), "chore_completions": ("'chores'", "chore_id"),
    "points_entries": ("'chores'", None), "rewards": ("'chores'", None),
    "family_dates": ("'calendar'", "id"), "events": ("'calendar'", "id"),
    "recipes": ("'recipes'", "id"), "recipe_ratings": ("'recipes'", "recipe_id"), "meal_plan_entries": ("'recipes'", None),
    "meal_profiles": ("'packed_meals'", "id"), "packed_meals": ("'packed_meals'", "profile_id"),
    "appliances": ("'appliances'", "id"), "laundry_presets": ("'appliances'", "appliance_id"),
    "chats": ("'chat'", "id"), "chat_keys": ("'chat'", "chat_id"), "chat_messages": ("'chat'", "chat_id"),
    "chat_reactions": ("'chat'", "chat_id"), "chat_reads": ("'chat'", "chat_id"),
    "trips": ("'travel'", "id"), "trip_items": ("'travel'", "trip_id"),
    "budget_entries": ("'budget'", None), "budget_limits": ("'budget'", None),
    "home_entries": ("case when section = 'KNOWLEDGE' then 'knowledge' else 'home_book' end", "id"),
    "polls": ("'polls'", "id"), "poll_votes": ("'polls'", "poll_id"),
}

