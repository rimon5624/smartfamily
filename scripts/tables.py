"""Specification of the family-scoped synced tables added from app 0.3 onwards.

Each table automatically gets: id (text, primary key), familyId, updatedAt, deletedAt and the
local-only dirty flag. Field tuples: (kotlin name, kotlin type, kotlin default or None).
policy:
  adults   - active members read; owner/admin/member write (children and guests read only)
  members  - every active member (children included) reads and writes
  admins   - active members read; owner/admin write
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
]
