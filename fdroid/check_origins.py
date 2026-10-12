#!/usr/bin/env python3
"""M8 Task 15: prove every resolved artifact comes from Google Maven or Maven Central,
and scan the coordinates against F-Droid's non-free signature list (fdroid-suss).

Input: Gradle dependency reports (`./gradlew :app:dependencies --configuration
releaseRuntimeClasspath`, `./gradlew :app:buildEnvironment`, ...) saved as text files.
For each group:name:version it asks both repositories for the POM, Google first.
Needs network. Prints one summary line per repository and every artifact found in neither.

Usage: check_origins.py [--suss suss.json] report.txt [report.txt ...]
"""
import json, re, sys, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

REPOS = {
    "google": "https://dl.google.com/dl/android/maven2",
    "central": "https://repo.maven.apache.org/maven2",
}
LINE = re.compile(r"--- ([\w.\-]+):([\w.\-]+):(\{strictly )?([\w.\-+]+)\}?(?: -> ([\w.\-+]+))?")


def coordinates(paths):
    out = set()
    for p in paths:
        for line in open(p, encoding="utf-8"):
            m = LINE.search(line)
            if m:
                g, a, _, v, resolved = m.groups()
                out.add((g, a, resolved or v))
    return sorted(out)


def origin(c):
    g, a, v = c
    for name, base in REPOS.items():
        url = f"{base}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.pom"
        try:
            req = urllib.request.Request(url, method="HEAD")
            with urllib.request.urlopen(req, timeout=30) as r:
                if r.status == 200:
                    return c, name
        except urllib.error.HTTPError:
            continue
    return c, None


def main(argv):
    suss, hits = None, []
    if argv[:1] == ["--suss"]:
        suss, argv = argv[1], argv[2:]
    coords = coordinates(argv)
    with ThreadPoolExecutor(8) as pool:
        found = list(pool.map(origin, coords))
    counts = {}
    for _, name in found:
        counts[name] = counts.get(name, 0) + 1
    print(f"{len(coords)} distinct artifacts")
    for name in ("google", "central", None):
        print(f"  {name or 'NEITHER'}: {counts.get(name, 0)}")
    for c, name in found:
        if name is None:
            print("  not found:", ":".join(c))
    if suss:
        data = json.load(open(suss, encoding="utf-8"))
        # As fdroidserver's scanner does: every gradle signature is an error regex,
        # compiled as '.*' + sig, case-insensitive, matched against the coordinate.
        sigs = [(n, sig) for n, d in data["signatures"].items() for sig in d.get("gradle_signatures", [])]
        hits = [(n, ":".join(c)) for n, sig in sigs for c in coords
                if re.compile(".*" + sig, re.IGNORECASE).match(":".join(c))]
        print(f"fdroid-suss gradle signatures checked: {len(sigs)}, hits: {len(hits)}")
        for h in hits:
            print("  non-free:", *h)
    return 0 if counts.get(None, 0) == 0 and not hits else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
