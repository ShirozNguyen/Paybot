#!/usr/bin/env python3
"""
Target Auditor Tool for PayBot
Adheres to Rule 17: Distinct single-responsibility classes.
Validates build-targets.json against filesystem and settings.gradle.
"""

import os
import sys
import json
import re
from typing import Dict, List, Any, Set


class TargetManifestReader:
    """Class dedicated strictly to reading and validating the JSON schema of build-targets.json."""

    @staticmethod
    def read_manifest(manifest_path: str) -> Dict[str, Any]:
        if not os.path.exists(manifest_path):
            raise FileNotFoundError(f"Manifest file not found at: {manifest_path}")
        with open(manifest_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if "targets" not in data or not isinstance(data["targets"], list):
            raise ValueError("Invalid manifest format: 'targets' list missing.")
        return data


class SettingsGradleAuditor:
    """Class dedicated strictly to parsing settings.gradle and determining included subprojects."""

    @staticmethod
    def get_included_projects(settings_path: str) -> Set[str]:
        included = set()
        if not os.path.exists(settings_path):
            return included

        with open(settings_path, "r", encoding="utf-8") as f:
            for line in f:
                line_clean = line.strip()
                if line_clean.startswith("//"):
                    continue
                match = re.search(r"include\s*\(?['\"]([^'\"]+)['\"]\)?", line_clean)
                if match:
                    included.add(match.group(1).replace(":", "/"))
        return included


class FileSystemAuditor:
    """Class dedicated strictly to verifying directory and file existence on the local filesystem."""

    @staticmethod
    def check_target_paths(root_dir: str, targets: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        results = []
        for t in targets:
            project_rel = t.get("project", "")
            full_path = os.path.join(root_dir, project_rel)
            exists = os.path.isdir(full_path)
            has_gradle = os.path.isfile(os.path.join(full_path, "build.gradle"))
            has_props = os.path.isfile(os.path.join(full_path, "gradle.properties"))
            results.append({
                "id": t.get("id"),
                "project": project_rel,
                "exists": exists,
                "has_build_gradle": has_gradle,
                "has_properties": has_props
            })
        return results


class AuditReportFormatter:
    """Class dedicated strictly to formatting and printing audit results."""

    @staticmethod
    def print_summary(manifest: Dict[str, Any], fs_checks: List[Dict[str, Any]], settings_included: Set[str]):
        targets = manifest.get("targets", [])
        total = len(targets)
        missing_dirs = [c for c in fs_checks if not c["exists"]]

        print("=" * 60)
        print("          PAYBOT TARGET MANIFEST AUDIT REPORT")
        print("=" * 60)
        print(f"Total Targets in Manifest: {total}")
        print(f"Manifest Version:         {manifest.get('version', 'N/A')} Part {manifest.get('part', 'N/A')}")
        print(f"Root Active in settings:  {len(settings_included)}")
        print(f"Missing Project Dirs:     {len(missing_dirs)}")
        print("-" * 60)

        modes = {}
        for t in targets:
            m = t.get("buildMode", "unknown")
            modes[m] = modes.get(m, 0) + 1

        print("Breakdown by Build Mode:")
        for mode, count in sorted(modes.items()):
            print(f"  - {mode:<16}: {count} targets")

        if missing_dirs:
            print("\n[WARNING] Following project directories were not found on disk:")
            for m in missing_dirs:
                print(f"  ! {m['id']} -> {m['project']}")
        else:
            print("\n[SUCCESS] 100% of target directories verified on disk.")
        print("=" * 60)


def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    manifest_path = os.path.join(root_dir, "build-targets.json")
    settings_path = os.path.join(root_dir, "settings.gradle")

    try:
        manifest = TargetManifestReader.read_manifest(manifest_path)
        settings_included = SettingsGradleAuditor.get_included_projects(settings_path)
        fs_checks = FileSystemAuditor.check_target_paths(root_dir, manifest["targets"])
        AuditReportFormatter.print_summary(manifest, fs_checks, settings_included)
    except Exception as e:
        print(f"[ERROR] Audit failed: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
