#!/usr/bin/env python3
"""Count the executable lines a change adds, the way ADR-011 and ADR-017 define them.

Usage:
    python tools/executable-lines.py <base>..<head>
    python tools/executable-lines.py origin/main..HEAD

Prints the production and test counts a ticket's `Size:` line is measured against, plus the raw
diff for reference. Both the worker and the reviewer run this, so the number in the PR body and the
number at the merge gate are the same number — before this script they were two hand counts that
disagreed.

Executable = an added line that is not: blank, a comment or KDoc, an `import`/`package` line, or in
a file that is a resource, a schema, a build script or a preview body. Tests are counted and
reported, but ADR-017 does not cap them: they are required by the ticket's named criteria, so the
criteria are what bound them, not a line budget.
"""
import re
import subprocess
import sys

# A file whose whole content is excluded, by path or by name.
EXCLUDED_SUFFIXES = (
    ".xml",        # Compose/Android resources — pt-BR + en, mandated by SPEC E6
    ".sq",         # SQLDelight schema
    ".gradle.kts", # build scripts
    ".toml",       # the version catalog
    ".md",         # docs, ADRs, tickets
    ".pro",        # proguard/R8 rules
    "Previews.kt", # preview bodies — rule 11 requires them; they are not logic
)

TEST_MARKERS = ("/commonTest/", "/androidHostTest/", "/iosTest/", "/src/test/")
IGNORED_LINE = re.compile(r"^\s*(//|/\*|\*|\*/|import\s|package\s)|^\s*$")


def counts(diff_range: str) -> dict:
    out = subprocess.run(
        ["git", "diff", "--unified=0", "--no-color", diff_range],
        capture_output=True, text=True, encoding="utf-8", errors="replace", check=True,
    ).stdout
    result = {"prod": 0, "test": 0, "raw_added": 0, "excluded": 0}
    path = ""
    for line in out.split("\n"):
        if line.startswith("+++ b/"):
            path = line[6:]
            continue
        if not line.startswith("+") or line.startswith("+++"):
            continue
        result["raw_added"] += 1
        body = line[1:]
        if path.endswith(EXCLUDED_SUFFIXES) or IGNORED_LINE.match(body):
            result["excluded"] += 1
            continue
        bucket = "test" if any(m in path for m in TEST_MARKERS) else "prod"
        result[bucket] += 1
    return result


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(2)
    c = counts(sys.argv[1])
    print(f"executable production : {c['prod']}")
    print(f"executable test       : {c['test']}")
    print(f"raw added lines       : {c['raw_added']}  ({c['excluded']} excluded)")
