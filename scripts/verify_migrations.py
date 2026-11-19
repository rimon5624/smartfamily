#!/usr/bin/env python3
"""Fail the build when a hand-written Room migration does not create exactly what Room expects.

Room exports the full schema of each database version to app/schemas/<db>/<version>.json at
compile time. For every CREATE TABLE / CREATE INDEX statement in Migrations.kt, this script finds
the table in the newest schema and compares the statement with Room's own createSql.
"""
import glob
import json
import re
import sys

schema_files = sorted(glob.glob("app/schemas/*/*.json"), key=lambda p: int(re.search(r"(\d+)\.json$", p).group(1)))
if not schema_files:
    sys.exit("No exported Room schema found (app/schemas). Did kspReleaseKotlin run?")
latest = json.load(open(schema_files[-1]))["database"]
version = latest["version"]

expected = {}
for entity in latest["entities"]:
    name = entity["tableName"]
    expected[name] = entity["createSql"].replace("${TABLE_NAME}", name)
    for idx in entity.get("indices", []):
        expected[idx["name"]] = idx["createSql"].replace("${TABLE_NAME}", name)

source = open("app/src/main/java/com/rimon/smartfamily/data/db/Migrations.kt").read()
statements = re.findall(r'"(CREATE (?:TABLE|INDEX) IF NOT EXISTS `([^`]+)`[^"]*)"', source)

errors = 0
for sql, name in statements:
    want = expected.get(name)
    if want is None:
        print(f"[migration] {name}: not in schema v{version}")
        errors += 1
    elif sql != want:
        print(f"[migration] {name} differs from Room schema v{version}\n  migration: {sql}\n  room:      {want}")
        errors += 1

print(f"Checked {len(statements)} migration statements against schema v{version}: {errors} problem(s)")
sys.exit(1 if errors else 0)
