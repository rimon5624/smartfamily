#!/usr/bin/env python3
"""Validates the English and Hebrew string files: same keys, same placeholders, no bad escapes, and every
R.string / R.array used in Kotlin exists. Exits non-zero on any problem (run in CI)."""
import re, sys, pathlib, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent.parent
res = root / "app/src/main/res"
def load(p):
    t = ET.parse(p).getroot(); d = {}
    for e in t:
        if e.get("translatable") == "false": d.setdefault(("_fixed", e.get("name")), 1); continue
        d[(e.tag, e.get("name"))] = "".join(e.itertext()) if e.tag == "string" else [ "".join(i.itertext()) for i in e ]
    return d, p.read_text(encoding="utf-8")
en, en_raw = load(res / "values/strings.xml")
he, he_raw = load(res / "values-iw/strings.xml")
bad = []
for k in sorted((set(en) ^ set(he)) - {x for x in set(en) | set(he) if x[0] == "_fixed"}):
    bad.append(f"only in {'English' if k in en else 'Hebrew'}: {k[1]}")
ph = re.compile(r"%(\d+\$)?[sdf]")
for k in set(en) & set(he):
    if k[0] != "string": 
        if len(en[k]) != len(he[k]): bad.append(f"array size differs: {k[1]}")
        continue
    a, b = sorted(ph.findall(en[k])), sorted(ph.findall(he[k]))
    if a != b: bad.append(f"placeholders differ in {k[1]}: {a} vs {b}")
for name, raw in (("English", en_raw), ("Hebrew", he_raw)):
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', raw, re.S):
        body = m.group(2)
        if re.search(r"(?<!\\)'", body): bad.append(f"{name}: unescaped apostrophe in {m.group(1)}")
        if re.search(r'(?<!\\)"', body): bad.append(f"{name}: unescaped quote in {m.group(1)}")
used = set()
for f in (root / "app/src/main/java").rglob("*.kt"):
    for m in re.finditer(r"R\.(string|array)\.([A-Za-z0-9_]+)", f.read_text(encoding="utf-8")):
        used.add(("string" if m.group(1) == "string" else "string-array", m.group(2)))
for f in (root / "app/src/main").rglob("*.xml"):
    for m in re.finditer(r"@string/([A-Za-z0-9_]+)", f.read_text(encoding="utf-8")): used.add(("string", m.group(1)))
for k in sorted(used):
    if k not in en and ("_fixed", k[1]) not in en: bad.append(f"used but missing: {k[1]}")
print(f"{len(en)} English / {len(he)} Hebrew resources, {len(used)} used")
for b in bad: print("ERROR", b)
sys.exit(1 if bad else 0)
