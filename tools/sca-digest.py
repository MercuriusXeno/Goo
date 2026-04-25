"""Unified SCA digest: parses all static analysis XML reports into one flat text file.

Covers checkstyle, PMD, SpotBugs, CPD, and JUnit test results.
Runs as a Gradle finalizedBy task. Output: build/reports/digest.txt.
"""

import glob
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
import sys

REPO = Path(__file__).resolve().parent.parent
REPORTS = REPO / "build" / "reports"
CHECKSTYLE_XML = REPORTS / "checkstyle" / "main.xml"
PMD_XML = REPORTS / "pmd" / "main.xml"
SPOTBUGS_XML = REPORTS / "spotbugs" / "main.xml"
CPD_XML = REPORTS / "cpd" / "main.xml"
TEST_RESULTS = REPO / "build" / "test-results" / "test"
GAMETEST_RESULTS = REPO / "build" / "test-results" / "gameTest"
OUTPUT = REPORTS / "digest.txt"

SRC_PREFIX = str(REPO / "src" / "main" / "java" / "com" / "mercuriusxeno" / "goo")


def shorten(path):
    """Strip source root prefix, normalize to forward slash."""
    p = path.replace("\\", "/")
    marker = "com/mercuriusxeno/goo/"
    idx = p.find(marker)
    if idx >= 0:
        return p[idx + len(marker):]
    return p


# ── Checkstyle ───────────────────────────────────────────────────────────

def parse_checkstyle():
    """Returns {rule: {file: [(line, message, severity)]}}."""
    if not CHECKSTYLE_XML.exists():
        return {}
    tree = ET.parse(CHECKSTYLE_XML)
    rules = defaultdict(lambda: defaultdict(list))
    for file_el in tree.findall(".//file"):
        name = shorten(file_el.get("name", ""))
        for err in file_el.findall("error"):
            source = err.get("source", "")
            rule = source.rsplit(".", 1)[-1].replace("Check", "")
            severity = err.get("severity", "warning")
            line = err.get("line", "?")
            message = err.get("message", "")
            rules[rule][name].append((line, message, severity))
    return rules


# ── PMD ──────────────────────────────────────────────────────────────────

PMD_NS = "{http://pmd.sourceforge.net/report/2.0.0}"


def parse_pmd():
    """Returns {rule: {file: [(line, message)]}}."""
    if not PMD_XML.exists():
        return {}
    tree = ET.parse(PMD_XML)
    rules = defaultdict(lambda: defaultdict(list))
    for file_el in tree.findall(f".//{PMD_NS}file"):
        name = shorten(file_el.get("name", ""))
        for v in file_el.findall(f"{PMD_NS}violation"):
            rule = v.get("rule", "unknown")
            line = v.get("beginline", "?")
            message = (v.text or "").strip().split("\n")[0]
            rules[rule][name].append((line, message))
    return rules


# ── SpotBugs ─────────────────────────────────────────────────────────────

def parse_spotbugs():
    """Returns {type: [(file, line, category, priority, message)]}."""
    if not SPOTBUGS_XML.exists():
        return {}
    tree = ET.parse(SPOTBUGS_XML)
    bugs = defaultdict(list)
    for bug in tree.findall(".//BugInstance"):
        bug_type = bug.get("type", "UNKNOWN")
        category = bug.get("category", "?")
        priority = bug.get("priority", "?")
        short_msg_el = bug.find("ShortMessage")
        long_msg_el = bug.find("LongMessage")
        message = ""
        if long_msg_el is not None and long_msg_el.text:
            message = long_msg_el.text.strip()
        elif short_msg_el is not None and short_msg_el.text:
            message = short_msg_el.text.strip()
        # Find the primary source line
        source_line = bug.find(".//SourceLine[@primary='true']")
        if source_line is None:
            source_line = bug.find(".//SourceLine")
        if source_line is not None:
            sourcepath = shorten(source_line.get("sourcepath", source_line.get("relSourcepath", "?")))
            line = source_line.get("start", "?")
        else:
            sourcepath = "?"
            line = "?"
        bugs[bug_type].append((sourcepath, line, category, priority, message))
    return bugs


# ── CPD ──────────────────────────────────────────────────────────────────

CPD_NS = "{https://pmd-code.org/schema/cpd-report}"


def parse_cpd():
    """Returns [(tokens, lines, [(file, line)])] sorted by tokens descending."""
    if not CPD_XML.exists():
        return []
    tree = ET.parse(CPD_XML)
    dupes = []
    for dup in tree.findall(f".//{CPD_NS}duplication"):
        tokens = int(dup.get("tokens", "0"))
        lines = int(dup.get("lines", "0"))
        files = []
        for f in dup.findall(f"{CPD_NS}file"):
            path = shorten(f.get("path", "?"))
            line = f.get("line", "?")
            files.append((path, line))
        dupes.append((tokens, lines, files))
    dupes.sort(key=lambda d: d[0], reverse=True)
    return dupes


# ── Tests ────────────────────────────────────────────────────────────────

def parse_tests():
    """Returns (total_tests, total_failures, total_errors, [(class, name, message)]).

    Scans both unit test and gametest JUnit XML result directories.
    """
    total_tests = 0
    total_failures = 0
    total_errors = 0
    failures = []
    for results_dir in (TEST_RESULTS, GAMETEST_RESULTS):
        if not results_dir.exists():
            continue
        for xml_file in sorted(results_dir.glob("TEST-*.xml")):
            tree = ET.parse(xml_file)
            suite = tree.getroot()
            total_tests += int(suite.get("tests", "0"))
            total_failures += int(suite.get("failures", "0"))
            total_errors += int(suite.get("errors", "0"))
            for tc in suite.findall("testcase"):
                fail = tc.find("failure")
                err = tc.find("error")
                if fail is not None:
                    failures.append((tc.get("classname", "?"), tc.get("name", "?"), (fail.get("message") or "").strip()))
                elif err is not None:
                    failures.append((tc.get("classname", "?"), tc.get("name", "?"), (err.get("message") or "").strip()))
    return total_tests, total_failures, total_errors, failures


# ── Output ───────────────────────────────────────────────────────────────

def write_digest(cs_rules, pmd_rules, sb_bugs, cpd_dupes, tests):
    """Writes the unified digest."""
    out = []

    # ── Totals ──
    cs_errors = sum(1 for f in cs_rules.values() for entries in f.values() for _, _, s in entries if s == "error")
    cs_warnings = sum(1 for f in cs_rules.values() for entries in f.values() for _, _, s in entries if s != "error")
    cs_total = cs_errors + cs_warnings
    pmd_total = sum(len(e) for f in pmd_rules.values() for e in f.values())
    sb_total = sum(len(v) for v in sb_bugs.values())
    cpd_total = len(cpd_dupes)
    t_total, t_fail, t_err, t_failures = tests

    out.append("TOTALS")
    out.append(f"  checkstyle: {cs_errors} errors, {cs_warnings} warnings ({cs_total})")
    out.append(f"  pmd: {pmd_total}")
    out.append(f"  spotbugs: {sb_total}")
    out.append(f"  cpd: {cpd_total} duplications")
    out.append(f"  tests: {t_total} passed, {t_fail + t_err} failed")
    out.append("")

    # ── Checkstyle ──
    if cs_rules:
        out.append("--- CHECKSTYLE (by rule, descending) ---")
        out.append("")
        rule_counts = {r: sum(len(e) for e in f.values()) for r, f in cs_rules.items()}
        for rule in sorted(rule_counts, key=rule_counts.get, reverse=True):
            files = cs_rules[rule]
            severity = "error" if any(s == "error" for entries in files.values() for _, _, s in entries) else "warning"
            out.append(f"{rule} [{severity}] ({rule_counts[rule]})")
            for file in sorted(files):
                entries = files[file]
                out.append(f"  {file}")
                for ln, msg, _ in entries:
                    out.append(f"    L{ln}: {msg}")
            out.append("")

    # ── PMD ──
    if pmd_rules:
        out.append("--- PMD (by rule, descending) ---")
        out.append("")
        pmd_counts = {r: sum(len(e) for e in f.values()) for r, f in pmd_rules.items()}
        for rule in sorted(pmd_counts, key=pmd_counts.get, reverse=True):
            files = pmd_rules[rule]
            out.append(f"{rule} ({pmd_counts[rule]})")
            for file in sorted(files):
                entries = files[file]
                out.append(f"  {file}")
                for ln, msg in entries:
                    out.append(f"    L{ln}: {msg}")
            out.append("")

    # ── SpotBugs ──
    if sb_bugs:
        out.append("--- SPOTBUGS (by type, descending) ---")
        out.append("")
        type_counts = {t: len(v) for t, v in sb_bugs.items()}
        for bug_type in sorted(type_counts, key=type_counts.get, reverse=True):
            entries = sb_bugs[bug_type]
            category = entries[0][2] if entries else "?"
            priority = entries[0][3] if entries else "?"
            out.append(f"{bug_type} [{category}/P{priority}] ({type_counts[bug_type]})")
            for sourcepath, line, _, _, message in entries:
                out.append(f"  {sourcepath}:{line} - {message}")
            out.append("")

    # ── CPD ──
    if cpd_dupes:
        out.append("--- CPD (by token count, descending) ---")
        out.append("")
        for tokens, lines, files in cpd_dupes:
            out.append(f"{tokens} tokens, {lines} lines")
            for path, line in files:
                out.append(f"  {path}:{line}")
            out.append("")

    # ── Tests ──
    out.append("--- TESTS ---")
    out.append("")
    if t_fail + t_err == 0:
        out.append(f"{t_total} passed, 0 failed")
    else:
        out.append(f"{t_total} total, {t_fail + t_err} FAILED")
        for classname, name, message in t_failures:
            short_class = classname.rsplit(".", 1)[-1]
            out.append(f"  FAIL {short_class}.{name}")
            if message:
                out.append(f"    {message}")
    out.append("")

    # ── Gate ──
    gate_failures = cs_errors + t_fail + t_err
    if gate_failures > 0:
        out.append("--- GATE: FAIL ---")
        if cs_errors > 0:
            out.append(f"  {cs_errors} checkstyle errors")
        if t_fail + t_err > 0:
            out.append(f"  {t_fail + t_err} test failures")
    else:
        out.append("--- GATE: PASS ---")
    out.append("")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(out), encoding="utf-8")
    print(f"Digest: {OUTPUT}")
    print(f"  checkstyle={cs_total} pmd={pmd_total} spotbugs={sb_total} cpd={cpd_total} tests={t_fail + t_err}F/{t_total}")
    digest_url = "file:///" + str(OUTPUT).replace("\\", "/")
    print(f"  {digest_url}")
    if gate_failures > 0:
        print(f"  GATE FAIL: {cs_errors} checkstyle errors, {t_fail + t_err} test failures")
    return gate_failures > 0


def main():
    cs = parse_checkstyle()
    pmd = parse_pmd()
    sb = parse_spotbugs()
    cpd = parse_cpd()
    tests = parse_tests()
    if not any([cs, pmd, sb, cpd, tests[0]]):
        print("No reports found, skipping digest.")
        sys.exit(0)
    failed = write_digest(cs, pmd, sb, cpd, tests)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
