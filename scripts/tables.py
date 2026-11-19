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
]
