#!/usr/bin/env python3
"""Kotlin declaration/reference triage for Android repositories.
Run from repository root:
  python tools/audit-kotlin-reachability.py
  python tools/audit-kotlin-reachability.py --out build/kotlin-reachability

This is a heuristic scan, NOT a compiler-backed call graph or proof of dead code.
"""
from __future__ import annotations
import argparse, json, re, subprocess
from collections import defaultdict
from pathlib import Path

DECLARATIONS = [
    ("type", re.compile(r"^\s*(?:(?:public|private|protected|internal|abstract|open|final|sealed|data|enum|annotation|value|inner|expect|actual)\s+)*(?:class|interface|object|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)")),
    ("function", re.compile(r"^\s*(?:(?:public|private|protected|internal|inline|infix|operator|tailrec|suspend|external|override|open|final|abstract|expect|actual)\s+)*(?:suspend\s+)?fun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_<>?.]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*\(")),
    ("property", re.compile(r"^\s*(?:(?:public|private|protected|internal|const|lateinit|override|open|final|abstract|expect|actual)\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\b")),
]
FRAMEWORK_MARKERS = ("@Composable", "@Preview", "@Inject", "@Provides", "@Binds", "@Keep",
                     "@JavascriptInterface", "override fun on", "override val", "override var")
ANDROID_BASES = ("Activity", "Service", "BroadcastReceiver", "ContentProvider",
                 "AccessibilityService", "VoiceInteractionService", "RecognitionService",
                 "Worker", "CoroutineWorker", "Application", "ViewModel")

def git_files():
    try:
        raw = subprocess.check_output(["git", "ls-files", "-z"], stderr=subprocess.STDOUT)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise SystemExit("Run from a Git checkout with git available: " + str(exc))
    return [Path(x.decode("utf-8", "surrogateescape")) for x in raw.split(b"\0") if x]

def read_text(path):
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="build/kotlin-reachability")
    args = parser.parse_args()
    tracked = git_files()
    kotlin_paths = [p for p in tracked if p.suffix == ".kt"]
    texts, declarations = {}, []
    declaration_lines = defaultdict(set)

    for path in kotlin_paths:
        text = read_text(path)
        if text is None:
            continue
        key = path.as_posix()
        texts[key] = text
        package = ""
        for number, line in enumerate(text.splitlines(), 1):
            pm = re.match(r"^\s*package\s+([\w.]+)", line)
            if pm:
                package = pm.group(1)
            found = next(((kind, m.group(1)) for kind, pattern in DECLARATIONS
                          if (m := pattern.match(line))), None)
            if not found:
                continue
            kind, name = found
            source_set = ("production" if "/src/main/" in key else
                          "test" if "/src/test/" in key or "/src/androidTest/" in key else "other")
            declarations.append({
                "kind": kind, "name": name, "package": package, "path": key,
                "line": number, "source_set": source_set, "line_text": line.strip()[:240]
            })
            declaration_lines[(key, name)].add(number)

    searchable = {}
    for path in tracked:
        text = read_text(path)
        if text is not None:
            searchable[path.as_posix()] = text

    grouped = defaultdict(list)
    for item in declarations:
        grouped[item["name"]].append(item)

    for name, items in grouped.items():
        token = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(name) + r"(?![A-Za-z0-9_])")
        hits, occurrences = [], 0
        production_occurrences, test_occurrences, other_occurrences = 0, 0, 0
        production_locations, test_locations, other_locations = [], [], []
        for source_path, source_text in searchable.items():
            for number, line in enumerate(source_text.splitlines(), 1):
                if source_path.endswith(".kt") and number in declaration_lines.get((source_path, name), set()):
                    continue
                count = len(token.findall(line))
                if not count:
                    continue
                occurrences += count
                source_set = ("production" if "/src/main/" in source_path else
                              "test" if "/src/test/" in source_path or "/src/androidTest/" in source_path
                              else "other")
                location = {"path": source_path, "line": number, "text": line.strip()[:220]}
                if source_set == "production":
                    production_occurrences += count
                    if len(production_locations) < 40: production_locations.append(location)
                elif source_set == "test":
                    test_occurrences += count
                    if len(test_locations) < 40: test_locations.append(location)
                else:
                    other_occurrences += count
                    if len(other_locations) < 40: other_locations.append(location)
                if len(hits) < 40: hits.append(location)
        for item in items:
            item["reference_occurrences_outside_declaration_lines"] = occurrences
            item["production_reference_occurrences"] = production_occurrences
            item["test_reference_occurrences"] = test_occurrences
            item["other_reference_occurrences"] = other_occurrences
            item["reference_locations"] = hits
            item["production_reference_locations"] = production_locations
            item["test_reference_locations"] = test_locations
            item["other_reference_locations"] = other_locations
            source_lines = texts.get(item["path"], "").splitlines()
            context = "\n".join(source_lines[max(0, item["line"]-4):item["line"]+2])
            framework = any(marker in context for marker in FRAMEWORK_MARKERS)
            android_base = any(re.search(r"(?::|extends\s+)\s*" + re.escape(base) + r"\b", context)
                               for base in ANDROID_BASES)
            item["triage"] = ("framework_or_annotation_entrypoint_review" if framework or android_base
                              else "has_textual_reference" if occurrences
                              else "no_textual_reference_candidate")
            item["manual_review_required"] = True

    declarations.sort(key=lambda x: (x["source_set"], x["path"], x["line"], x["name"]))
    counts = {status: sum(d["triage"] == status for d in declarations) for status in (
        "has_textual_reference", "framework_or_annotation_entrypoint_review",
        "no_textual_reference_candidate")}
    report = {
        "warning": "Heuristic text scan only; not a compiler-backed call graph and never proof of dead code.",
        "tracked_kotlin_files_scanned": len(texts),
        "tracked_text_files_searched": len(searchable),
        "declarations_detected": len(declarations),
        "production_declarations": sum(d["source_set"] == "production" for d in declarations),
        "test_declarations": sum(d["source_set"] == "test" for d in declarations),
        "triage_counts": counts, "declarations": declarations
    }
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    json_file = out / "kotlin-reachability.json"
    json_file.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Kotlin files scanned:", len(texts))
    print("Tracked text files searched:", len(searchable))
    print("Declarations detected:", len(declarations))
    print("Triage counts:", json.dumps(counts, ensure_ascii=False))
    print("JSON report:", json_file)
    print("WARNING: manually verify candidates; this heuristic cannot prove dead code.")

if __name__ == "__main__":
    main()
