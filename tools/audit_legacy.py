"""Reproducible, read-only legacy source inventory. Uses only the Python standard library."""
from pathlib import Path
import argparse
import hashlib
import json
import re
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "legacy/1.12.2/src/main"
OUTPUT = ROOT / "content/legacy-inventory.json"


def inventory():
    java = []
    for path in sorted((LEGACY / "java").rglob("*.java")):
        # Canonical LF hashes are stable across Git's Windows/Linux checkout settings.
        data = path.read_bytes().replace(b"\r\n", b"\n")
        java.append({"path": path.relative_to(ROOT).as_posix(),
                     "lines": len(data.splitlines()), "sha256": hashlib.sha256(data).hexdigest()})
    resources = list((LEGACY / "resources").rglob("*"))
    resources = [p for p in resources if p.is_file()]
    weapons = []
    source = LEGACY / "java/techguns/TGuns.java"
    for number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        match = re.search(r'^\s*(\w+)\s*=\s*new\s+(\w+)\("([^"]+)"', line)
        if match:
            weapons.append({"id": match[3], "field": match[1], "legacy_class": match[2],
                            "source": source.relative_to(ROOT).as_posix(), "line": number})
    packages = Counter()
    for item in java:
        parts = Path(item["path"]).parts
        idx = parts.index("java")
        package = "/".join(parts[idx + 1:idx + 3]) if len(parts) > idx + 3 else parts[idx + 1]
        packages[package] += 1
    return {"upstream": "pWn3d1337/Techguns2",
            "commit": "5c95a5b6d79f5adb08096dae39c6307e72b87d64",
            "hash_format": "SHA-256 of UTF-8 file bytes with CRLF normalized to LF",
            "summary": {"java_files": len(java), "java_lines": sum(p["lines"] for p in java),
                        "resource_files": len(resources), "weapons": len(weapons)},
            "packages": dict(sorted(packages.items())),
            "resource_extensions": dict(sorted(Counter(p.suffix for p in resources).items())),
            "weapons": weapons, "java_sources": java}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = inventory()
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != text:
            raise SystemExit("Legacy inventory is stale; run python tools/audit_legacy.py")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps(result["summary"]))
