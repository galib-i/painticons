#!/usr/bin/env python3
"""Merge Lawnicons icons into Painticons.

This is the icon part of the monthly "Sync upstream icons" workflow. It copies
the icons Lawnicons added (and the ones they changed) from an extracted
upstream tarball into svgs/ and app/assets/appfilter.xml.

The rules are deliberately conservative, because this repo carries local
changes on top of the fork (hand-curated appfilter entries, tweaked icons):

* An existing svg is only overwritten when its content is exactly the upstream
  version we recorded last time. Anything else counts as a local edit and is
  left alone (it shows up in the report as "kept local").
* Icons are never deleted, so icons upstream renamed or dropped stay usable.
* appfilter.xml is edited textually: existing lines are untouched, and only
  entries for components we do not know yet are appended, so local curation
  always wins.
* Only entries whose drawable exists locally after the merge are appended,
  because an entry pointing at a missing drawable is a broken icon.
* Nothing is committed here. The workflow builds a debug APK afterwards and
  only pushes when that build succeeds, so a bad upstream icon cannot land.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_REPO = "LawnchairLauncher/lawnicons"
SVGS_DIR = "svgs"
APPFILTER_PATH = "app/assets/appfilter.xml"
BASELINE_PATH = ".github/upstream-sync-baseline"

ITEM_LINE_RE = re.compile(r"^\s*<item\b[^>]*/>\s*$")
COMPONENT_RE = re.compile(r'\bcomponent="([^"]*)"')

# First run only: how many differing icons may be checked against upstream's
# file history, and how far back that history is searched per icon.
BOOTSTRAP_ICON_LIMIT = 40
BOOTSTRAP_HISTORY_LIMIT = 20

# Newly synced icons are only described in the log and report this many times.
WARNING_LIMIT = 25


def git_blob_sha(data: bytes) -> str:
    """The sha1 git uses to identify a blob, so it can be compared with a tree."""
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def api_json(url: str, token: str | None):
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def fetch_tree_hashes(repo: str, sha: str, token: str | None) -> dict[str, str]:
    """Map every blob path of an upstream commit to its git blob sha."""
    payload = api_json(f"https://api.github.com/repos/{repo}/git/trees/{sha}?recursive=1", token)
    if payload.get("truncated"):
        raise RuntimeError(f"upstream tree {sha} is truncated")
    return {
        entry["path"]: entry["sha"]
        for entry in payload.get("tree", [])
        if entry.get("type") == "blob" and "sha" in entry
    }


def is_unmodified_upstream_icon(repo: str, token: str | None, path: str, local: bytes) -> bool:
    """True when a local icon is an older, untouched upstream version of that icon.

    Only used on the first run, where there is no recorded upstream version to
    compare against. It distinguishes "we simply never picked up upstream's
    newer drawing" from "the local file was edited", so that plain old copies
    of an icon still get updated instead of being protected forever.
    """
    quoted = urllib.parse.quote(path)
    commits = api_json(
        f"https://api.github.com/repos/{repo}/commits?path={quoted}"
        f"&per_page={BOOTSTRAP_HISTORY_LIMIT}",
        token,
    )
    for commit in commits:
        content = api_json(f"https://api.github.com/repos/{repo}/contents/{quoted}?ref={commit['sha']}", token)
        if content.get("encoding") != "base64":
            continue
        if base64.b64decode(content["content"]) == local:
            return True
    return False


def load_baseline(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        print(f"warning: ignoring unreadable baseline {path}: {error}", file=sys.stderr)
        return {}


def svg_paths(root: Path) -> dict[str, Path]:
    """Map icon name -> file for every svg in root/svgs, keyed by that name."""
    directory = root / SVGS_DIR
    if not directory.is_dir():
        raise SystemExit(f"no {SVGS_DIR}/ directory in {root}")
    return {path.relative_to(directory).as_posix(): path for path in directory.rglob("*.svg")}


def drawable_names(paths: dict[str, Path]) -> set[str]:
    """Icon file names without the .svg suffix, i.e. how an appfilter refers to them."""
    return {name.removesuffix(".svg") for name in paths}


def serialise_item(item: ET.Element) -> str:
    return ET.tostring(item, encoding="unicode").strip()


def appfilter_components(root: ET.Element) -> set[str]:
    return {element.get("component") for element in root if element.get("component")}


def merge_appfilter(local_file: Path, upstream_file: Path, available: set[str]) -> tuple[str, list[str], list[str]]:
    """Return the merged appfilter text, the components added and the draws skipped."""
    local_text = local_file.read_text(encoding="utf-8")
    known = appfilter_components(ET.parse(local_file).getroot())

    upstream_root = ET.parse(upstream_file).getroot()
    raw_lines = {
        match.group(1): line.strip()
        for line in upstream_file.read_text(encoding="utf-8").splitlines()
        if ITEM_LINE_RE.match(line)
        for match in [COMPONENT_RE.search(line)]
        if match
    }

    added_components: list[str] = []
    new_lines: list[str] = []
    skipped_drawables: list[str] = []
    for element in upstream_root:
        if element.tag != "item":
            continue
        component = element.get("component")
        if not component or component in known:
            continue
        drawable = element.get("drawable")
        # drawableIgnore entries deliberately point at an icon we do not ship,
        # so they are always safe to add; anything else must resolve locally.
        if element.get("drawableIgnore") is None and (not drawable or drawable not in available):
            if drawable:
                skipped_drawables.append(drawable)
            continue
        new_lines.append("  " + (raw_lines.get(component) or serialise_item(element)))
        added_components.append(component)

    if not new_lines:
        return local_text, [], sorted(set(skipped_drawables))

    closing = local_text.rstrip().rfind("</resources>")
    if closing == -1:
        raise SystemExit(f"{local_file} has no closing </resources> tag")
    head = local_text[:closing].rstrip("\n") + "\n"
    tail = "</resources>\n"
    merged = head + "\n".join(new_lines) + "\n" + tail
    # Fail loudly rather than committing something the app cannot parse.
    ET.fromstring(merged)
    return merged, added_components, sorted(set(skipped_drawables))


def inspect_svg(path: Path) -> list[str]:
    """Cheap sanity checks, reported as warnings; the APK build is the real gate."""
    warnings: list[str] = []
    data = path.read_bytes()
    try:
        root = ET.fromstring(data)
    except ET.ParseError as error:
        return [f"not valid XML: {error}"]
    if not root.tag.endswith("svg"):
        warnings.append(f"root element is <{root.tag}>, not <svg>")
    if any(root.iter("{http://www.w3.org/2000/svg}image")):
        warnings.append("contains a raster <image> element")
    size_kb = len(data) / 1024
    if size_kb > 3:
        warnings.append(f"larger than 3 KB ({size_kb:.1f} KB)")
    return warnings


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", required=True, type=Path,
                        help="extracted upstream checkout (contains svgs/ and app/assets/appfilter.xml)")
    parser.add_argument("--upstream-sha", required=True, help="upstream commit the tarball came from")
    parser.add_argument("--project-dir", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--upstream-repo", default=DEFAULT_REPO)
    parser.add_argument("--baseline", type=Path, default=None)
    parser.add_argument("--report", type=Path, default=None, help="write a JSON summary here")
    parser.add_argument("--dry-run", action="store_true", help="report without touching the repo")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    project = args.project_dir.resolve()
    upstream = args.upstream_dir.resolve()
    baseline_path = args.baseline or project / BASELINE_PATH
    report_path = args.report
    token = os.environ.get("GITHUB_TOKEN")

    upstream_svgs = svg_paths(upstream)
    upstream_appfilter = upstream / APPFILTER_PATH
    if not upstream_appfilter.is_file():
        raise SystemExit(f"no {APPFILTER_PATH} in {upstream}")

    baseline = load_baseline(baseline_path)
    previous_sha = baseline.get("sha")

    # Which local icons may be overwritten? A file is untouched since the last
    # sync when its content still matches the upstream version the baseline
    # points at; anything else is a local edit and wins. On the very first run
    # there is nothing recorded yet, so each differing icon is looked up in
    # upstream's own file history: a match means it is an unmodified older copy
    # and may be updated, no match means it is a local edit.
    previous_hashes: dict[str, str] = {}
    if previous_sha and previous_sha != args.upstream_sha:
        try:
            previous_hashes = {
                path: sha for path, sha in fetch_tree_hashes(args.upstream_repo, previous_sha, token).items()
                if path.startswith(f"{SVGS_DIR}/")
            }
        except (urllib.error.URLError, RuntimeError, json.JSONDecodeError, TimeoutError) as error:
            print(f"warning: could not read upstream tree {previous_sha}: {error}\n"
                  "warning: existing icons will be left alone this run", file=sys.stderr)
    bootstrap_lookups = 0

    added: list[str] = []
    updated: list[str] = []
    kept_local: list[str] = []
    warnings: list[str] = []

    def note_icon_problems(name: str, source: Path) -> None:
        warnings.extend(f"svgs/{name}: {problem}" for problem in inspect_svg(source))

    for name, source in sorted(upstream_svgs.items()):
        target = project / SVGS_DIR / name
        data = source.read_bytes()
        if not target.is_file():
            added.append(name)
            note_icon_problems(name, source)
            if not args.dry_run:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            continue
        current = target.read_bytes()
        if current == data:
            continue
        # Untouched since the last sync when the file still matches the upstream
        # version recorded in the baseline.
        tracked = bool(previous_hashes) and previous_hashes.get(f"{SVGS_DIR}/{name}") == git_blob_sha(current)
        if not previous_sha and token and bootstrap_lookups < BOOTSTRAP_ICON_LIMIT:
            bootstrap_lookups += 1
            try:
                tracked = is_unmodified_upstream_icon(args.upstream_repo, token, f"{SVGS_DIR}/{name}", current)
            except (urllib.error.URLError, RuntimeError, json.JSONDecodeError, TimeoutError) as error:
                warnings.append(f"svgs/{name}: could not check upstream history ({error})")
        if tracked:
            updated.append(name)
            note_icon_problems(name, source)
            if not args.dry_run:
                target.write_bytes(data)
        else:
            kept_local.append(name)

    local_svgs = svg_paths(project)
    upstream_only = sorted(name for name in local_svgs if name not in upstream_svgs)

    available = drawable_names(local_svgs) | {name.removesuffix(".svg") for name in added}
    merged_appfilter, appfilter_added, appfilter_skipped = merge_appfilter(
        project / APPFILTER_PATH, upstream_appfilter, available
    )
    appfilter_changed = bool(appfilter_added)

    changed = bool(added or updated or appfilter_changed)
    if changed and not args.dry_run:
        (project / APPFILTER_PATH).write_text(merged_appfilter, encoding="utf-8")
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(
            json.dumps(
                {
                    "sha": args.upstream_sha,
                    "synced_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )

    report = {
        "upstream_sha": args.upstream_sha,
        "previous_sha": previous_sha,
        "added": added,
        "updated": updated,
        "kept_local": kept_local,
        "upstream_no_longer_ships": upstream_only,
        "appfilter_added": len(appfilter_added),
        "appfilter_skipped_drawables": appfilter_skipped,
        "warnings": warnings,
        "changed": changed,
    }
    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(f"upstream {args.upstream_sha[:10]}: +{len(added)} icons, {len(updated)} updated, "
          f"{len(kept_local)} kept local, {len(appfilter_added)} appfilter entries added")
    if kept_local:
        print("kept local copies (upstream changed them too): " + ", ".join(kept_local))
    if appfilter_skipped:
        print("appfilter entries skipped, drawable not shippable: " + ", ".join(appfilter_skipped))
    for warning in warnings[:WARNING_LIMIT]:
        print("warning: " + warning, file=sys.stderr)
    if len(warnings) > WARNING_LIMIT:
        print(f"warning: {len(warnings) - WARNING_LIMIT} more, see the report", file=sys.stderr)

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as summary:
            summary.write(f"## Lawnicons sync ({args.upstream_sha[:10]})\n\n")
            summary.write(f"- {len(added)} new icons, {len(updated)} updated, "
                          f"{len(kept_local)} left alone (local edits)\n")
            summary.write(f"- {len(appfilter_added)} new appfilter entries\n")
            if added:
                summary.write("\nNew: `" + "`, `".join(added) + "`\n")
            if updated:
                summary.write("\nUpdated: `" + "`, `".join(updated) + "`\n")
            if kept_local:
                summary.write("\nKept local: `" + "`, `".join(kept_local) + "`\n")
            if warnings:
                summary.write("\nWarnings:\n")
                for warning in warnings[:WARNING_LIMIT]:
                    summary.write(f"- {warning}\n")
                if len(warnings) > WARNING_LIMIT:
                    summary.write(f"- {len(warnings) - WARNING_LIMIT} more\n")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as output:
            output.write(f"changed={'true' if changed else 'false'}\n")
            output.write(f"added={len(added)}\n")
            output.write(f"updated={len(updated)}\n")
            output.write(f"appfilter_added={len(appfilter_added)}\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
