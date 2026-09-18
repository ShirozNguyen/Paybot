#!/usr/bin/env python3
"""
JAR & Metadata Validator for PayBot
Adheres to Rule 17: Distinct single-responsibility classes.
Validates ZIP integrity, metadata (fabric.mod.json, mods.toml, plugin.yml),
entrypoint existence in bytecode, and generates jar-report.json & SHA256SUMS.
"""

import os
import sys
import json
import zipfile
import hashlib
from typing import Dict, List, Any, Optional


class ZipIntegrityChecker:
    """Checks physical integrity of a JAR/ZIP archive."""

    @staticmethod
    def verify_zip(jar_path: str) -> bool:
        if not os.path.exists(jar_path) or os.path.getsize(jar_path) == 0:
            return False
        try:
            with zipfile.ZipFile(jar_path, 'r') as z:
                # testzip returns None if no errors found
                return z.testzip() is None
        except Exception:
            return False


class JarBytecodeInspector:
    """Verifies that an entrypoint class actually exists inside the JAR archive."""

    @staticmethod
    def has_class(jar_path: str, class_fqn: str) -> bool:
        if not class_fqn or class_fqn == "N/A":
            return False
        # Convert com.paybot.PayBotMod to com/paybot/PayBotMod.class
        expected_entry = class_fqn.replace(".", "/") + ".class"
        try:
            with zipfile.ZipFile(jar_path, 'r') as z:
                return expected_entry in z.namelist()
        except Exception:
            return False


class FabricMetadataParser:
    """Validates fabric.mod.json metadata."""

    @staticmethod
    def validate(content_bytes: bytes) -> Dict[str, Any]:
        try:
            data = json.loads(content_bytes.decode('utf-8'))
            mod_id = data.get("id")
            version = data.get("version")
            entrypoints = data.get("entrypoints", {})

            # Extract main entrypoint
            main_entry = None
            if isinstance(entrypoints, dict):
                main_list = entrypoints.get("main", [])
                if isinstance(main_list, list) and len(main_list) > 0:
                    main_entry = main_list[0]
                    if isinstance(main_entry, dict):
                        main_entry = main_entry.get("value")

            valid = bool(mod_id and version and main_entry)
            return {
                "valid": valid,
                "mod_id": mod_id,
                "version": version,
                "main_entrypoint": main_entry,
                "error": None if valid else "Missing id, version, or entrypoint"
            }
        except Exception as e:
            return {"valid": False, "error": str(e)}


class BukkitMetadataParser:
    """Validates plugin.yml metadata."""

    @staticmethod
    def validate(content_bytes: bytes) -> Dict[str, Any]:
        try:
            text = content_bytes.decode('utf-8')
            main_class = None
            version = None
            folia_supported = False

            for line in text.splitlines():
                s = line.strip()
                if s.startswith("main:"):
                    main_class = s.split(":", 1)[1].strip()
                elif s.startswith("version:"):
                    version = s.split(":", 1)[1].strip()
                elif s.startswith("folia-supported:"):
                    folia_supported = s.split(":", 1)[1].strip().lower() == "true"

            valid = bool(main_class and version)
            return {
                "valid": valid,
                "mod_id": "PayBot",
                "version": version,
                "main_entrypoint": main_class,
                "folia_supported": folia_supported,
                "error": None if valid else "Missing main class or version"
            }
        except Exception as e:
            return {"valid": False, "error": str(e)}


class ForgeMetadataParser:
    """Validates META-INF/mods.toml or neoforge.mods.toml."""

    @staticmethod
    def validate(content_bytes: bytes) -> Dict[str, Any]:
        try:
            text = content_bytes.decode('utf-8')
            mod_id = None
            version = None

            for line in text.splitlines():
                s = line.strip()
                if s.startswith("modId="):
                    mod_id = s.split("=", 1)[1].strip().strip('"').strip("'")
                elif s.startswith("version="):
                    version = s.split("=", 1)[1].strip().strip('"').strip("'")

            valid = bool(mod_id)
            return {
                "valid": valid,
                "mod_id": mod_id,
                "version": version,
                "main_entrypoint": "com.paybot.PayBotMod",
                "error": None if valid else "Missing modId"
            }
        except Exception as e:
            return {"valid": False, "error": str(e)}


class ChecksumGenerator:
    """Generates SHA-256 hashes and formats SHA256SUMS file."""

    @staticmethod
    def sha256_file(filepath: str) -> str:
        h = hashlib.sha256()
        with open(filepath, "rb") as f:
            while chunk := f.read(65536):
                h.update(chunk)
        return h.hexdigest()

    @staticmethod
    def write_checksums(jar_records: List[Dict[str, Any]], output_path: str):
        lines = []
        for rec in sorted(jar_records, key=lambda x: x["file"]):
            lines.append(f"{rec['sha256']}  {rec['file']}")
        with open(output_path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


class JarValidatorCoordinator:
    """Coordinates overall JAR validation and creates jar-report.json."""

    @staticmethod
    def audit_single_jar(jar_path: str) -> Dict[str, Any]:
        filename = os.path.basename(jar_path)
        size = os.path.getsize(jar_path)
        sha256 = ChecksumGenerator.sha256_file(jar_path)
        is_valid_zip = ZipIntegrityChecker.verify_zip(jar_path)

        meta_valid = False
        entrypoint_valid = False
        loader = "unknown"
        main_entry = None
        error = None

        if is_valid_zip:
            try:
                with zipfile.ZipFile(jar_path, 'r') as z:
                    names = z.namelist()
                    if "fabric.mod.json" in names:
                        loader = "fabric"
                        m = FabricMetadataParser.validate(z.read("fabric.mod.json"))
                        meta_valid = m["valid"]
                        main_entry = m.get("main_entrypoint")
                        error = m.get("error")
                    elif "plugin.yml" in names:
                        loader = "bukkit"
                        m = BukkitMetadataParser.validate(z.read("plugin.yml"))
                        meta_valid = m["valid"]
                        main_entry = m.get("main_entrypoint")
                        error = m.get("error")
                    elif "META-INF/neoforge.mods.toml" in names:
                        loader = "neoforge"
                        m = ForgeMetadataParser.validate(z.read("META-INF/neoforge.mods.toml"))
                        meta_valid = m["valid"]
                        main_entry = m.get("main_entrypoint")
                        error = m.get("error")
                    elif "META-INF/mods.toml" in names:
                        loader = "forge"
                        m = ForgeMetadataParser.validate(z.read("META-INF/mods.toml"))
                        meta_valid = m["valid"]
                        main_entry = m.get("main_entrypoint")
                        error = m.get("error")

                if main_entry:
                    entrypoint_valid = JarBytecodeInspector.has_class(jar_path, main_entry)
            except Exception as e:
                error = str(e)

        return {
            "file": filename,
            "path": jar_path,
            "size": size,
            "sha256": sha256,
            "validZip": is_valid_zip,
            "loader": loader,
            "metadataValid": meta_valid,
            "mainEntrypoint": main_entry,
            "entrypointValid": entrypoint_valid,
            "error": error
        }

    @staticmethod
    def audit_all(target_dirs: List[str], output_json: str, output_checksums: str) -> Dict[str, Any]:
        jar_records = []
        for d in target_dirs:
            if not os.path.exists(d):
                continue
            for root, _, files in os.walk(d):
                for f in files:
                    if f.endswith(".jar"):
                        if any(x in f for x in ["-dev.jar", "-shadow.jar", "-sources.jar"]):
                            continue
                        full_p = os.path.join(root, f)
                        jar_records.append(JarValidatorCoordinator.audit_single_jar(full_p))

        report = {
            "totalJars": len(jar_records),
            "validZips": len([j for j in jar_records if j["validZip"]]),
            "validMetadata": len([j for j in jar_records if j["metadataValid"]]),
            "validEntrypoints": len([j for j in jar_records if j["entrypointValid"]]),
            "jars": jar_records
        }

        with open(output_json, "w", encoding="utf-8") as jf:
            json.dump(report, jf, indent=2, ensure_ascii=False)

        ChecksumGenerator.write_checksums(jar_records, output_checksums)
        return report


def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    scan_dirs = [
        os.path.join(root_dir, "done"),
        os.path.join(root_dir, "artifacts")
    ]
    report_json_path = os.path.join(root_dir, "jar-report.json")
    checksums_path = os.path.join(root_dir, "SHA256SUMS")

    print(f"Scanning for built JARs in: {scan_dirs}...")
    report = JarValidatorCoordinator.audit_all(scan_dirs, report_json_path, checksums_path)
    print("=" * 60)
    print("          PAYBOT JAR INTEGRITY AUDIT REPORT")
    print("=" * 60)
    print(f"Total JARs Analyzed:       {report['totalJars']}")
    print(f"Valid ZIP Archives:        {report['validZips']}")
    print(f"Valid Metadata Descriptors:{report['validMetadata']}")
    print(f"Verified Entrypoint Classes: {report['validEntrypoints']}")
    print(f"Generated Reports:         {report_json_path}")
    print(f"Generated Checksums:       {checksums_path}")
    print("=" * 60)


if __name__ == "__main__":
    main()
