#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
tools/modrinth_uploader.py — PayBot Modrinth Official API Uploader (Labrinth v2)
Tuân thủ Rule 17: Mỗi class là 1 chức năng riêng biệt.
"""

import os
import sys
import time

# Đảm bảo stdout / stderr hỗ trợ UTF-8 an toàn trên Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import json
import re
import uuid
import urllib.request
import urllib.error
from pathlib import Path


class ModrinthConfigLoader:
    """Class 1: Quản lý đọc và kiểm tra cấu hình kết nối Modrinth API."""

    def __init__(self, config_path="modrinth.json"):
        self.config_path = config_path

    def load_config(self):
        config = {
            "token": os.environ.get("MODRINTH_TOKEN", "").strip(),
            "project_id": "mLgal5cH", # Project ID của paybot
            "slug": "paybot",
            "version_type": os.environ.get("MODRINTH_VERSION_TYPE", "alpha").strip(), # alpha / beta / release
            "api_url": "https://api.modrinth.com/v2"
        }

        # Nếu có file token bí mật trong scratch/ hoặc root
        token_files = [Path("scratch/.modrinth_token"), Path(".modrinth_token")]
        for tf in token_files:
            if tf.exists() and not config["token"]:
                config["token"] = tf.read_text(encoding="utf-8").strip()

        # Nếu có file modrinth.json
        cfg_file = Path(self.config_path)
        if cfg_file.exists():
            try:
                data = json.loads(cfg_file.read_text(encoding="utf-8"))
                for k in ["token", "version_type"]:
                    if k in data and data[k]:
                        config[k] = str(data[k]).strip()
            except Exception as e:
                print(f"[WARN] Khong the doc {self.config_path}: {e}")

        return config


class FabricDependencyResolver:
    """Class 2: Ánh xạ và giải quyết dependencies cho Fabric (gắn Fabric API tương ứng)."""

    # Modrinth Project ID chính xác của Fabric API (slug: fabric-api)
    FABRIC_API_PROJECT_ID = "P7dR8mSH"

    @classmethod
    def resolve_dependencies(cls, loader, mc_version):
        if loader != "fabric":
            return []
        
        dep = {
            "project_id": cls.FABRIC_API_PROJECT_ID,
            "dependency_type": "required"
        }
        return [dep]


class JarMetadataExtractor:
    """Class 3: Trích xuất metadata từ tên file JAR."""

    @staticmethod
    def extract_info(file_path):
        filename = Path(file_path).name
        
        # Bỏ qua các file dummy wrapper hoặc file corrupted
        if filename in ["Fabric_Loader-5.5.5.jar", "Forge_Loader-5.5.5.jar", "NeoForge_Loader-5.5.5.jar",
                        "Fabric_Loader-5.5.6.jar", "Forge_Loader-5.5.6.jar", "NeoForge_Loader-5.5.6.jar",
                        "PayBot-Mod-NeoForge-1.20.3-5.5.5.jar"]:
            return None

        # Trường hợp Plugin Spigot/Paper/Folia: PayBot-Plugin-Paper-5.5.5.jar hoặc PayBot-Plugin-5.5.5.jar
        if "Paper" in filename or "Spigot" in filename or "Plugin" in filename:
            ver_match = re.search(r"(\d+\.\d+\.\d+)", filename)
            version = ver_match.group(1) if ver_match else "5.5.5"
            return {
                "loader": "paper",
                "loaders": ["paper", "purpur", "spigot", "folia"],
                "mc_version": "1.20.4",
                "game_versions": ["1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.4", "1.20.6", "1.21.1", "1.21.4"],
                "mod_version": version,
                "display_name": f"PayBot {version} (Paper / Purpur / Spigot / Folia)"
            }

        # Trường hợp Mod: PayBot-Mod-Fabric-1.20.1-5.5.5.jar hoặc Forge / NeoForge
        m = re.match(r"PayBot-Mod-([A-Za-z]+)-([\d\.]+)-([\d\.]+)\.jar", filename)
        if m:
            raw_loader, mc_ver, mod_ver = m.groups()
            loader = raw_loader.lower()
            loaders = [loader]
            if loader == "fabric":
                loaders.append("quilt")
            return {
                "loader": loader,
                "loaders": loaders,
                "mc_version": mc_ver,
                "game_versions": [mc_ver],
                "mod_version": mod_ver,
                "display_name": f"PayBot {mod_ver} [{raw_loader}] {mc_ver}"
            }

        return None


class ChangelogFormatter:
    """Class 4: Chuẩn bị nội dung changelog định dạng Markdown."""

    @staticmethod
    def get_changelog(version="5.5.5"):
        doc_path = Path(f"docs/MODRINTH_CHANGELOG_{version}.md")
        if doc_path.exists():
            return doc_path.read_text(encoding="utf-8")
        fallback_path = Path("docs/MODRINTH_CHANGELOG_5.5.5.md")
        if fallback_path.exists():
            return fallback_path.read_text(encoding="utf-8")
        return f"# PayBot {version}\n\nBan cap nhat PayBot {version} on dinh va bao mat."


class ModrinthApiClient:
    """Class 5: Giao tiếp với Modrinth Labrinth API v2 để tạo version mới."""

    def __init__(self, token, api_url="https://api.modrinth.com/v2"):
        self.token = token
        self.api_url = api_url
        self.user_agent = "ShirozNguyen/PayBot/5.5.6 (modrinth@paybot.com)"

    def get_project_files(self, project_id):
        """Lấy tập hợp các tên file đã được upload lên project."""
        url = f"{self.api_url}/project/{project_id}/version"
        req = urllib.request.Request(url, headers={"Authorization": self.token, "User-Agent": self.user_agent})
        files = set()
        try:
            with urllib.request.urlopen(req) as resp:
                versions = json.loads(resp.read().decode("utf-8"))
                for v in versions:
                    for f in v.get("files", []):
                        fn = f.get("filename")
                        if fn:
                            files.add(fn)
        except Exception as e:
            print(f"[WARN] Khong the lay danh sach files hien co: {e}")
        return files

    def create_version(self, metadata, jar_path):
        """Gửi request multipart/form-data tạo version mới trên Modrinth."""
        url = f"{self.api_url}/version"
        boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"

        body_bytes = bytearray()

        # Part 1: "data" JSON
        body_bytes.extend(f"--{boundary}\r\n".encode("utf-8"))
        body_bytes.extend(b'Content-Disposition: form-data; name="data"\r\n')
        body_bytes.extend(b'Content-Type: application/json\r\n\r\n')
        body_bytes.extend(json.dumps(metadata).encode("utf-8"))
        body_bytes.extend(b"\r\n")

        # Part 2: file jar
        filename = Path(jar_path).name
        file_data = Path(jar_path).read_bytes()
        body_bytes.extend(f"--{boundary}\r\n".encode("utf-8"))
        body_bytes.extend(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode("utf-8"))
        body_bytes.extend(b'Content-Type: application/java-archive\r\n\r\n')
        body_bytes.extend(file_data)
        body_bytes.extend(b"\r\n")

        # End boundary
        body_bytes.extend(f"--{boundary}--\r\n".encode("utf-8"))

        headers = {
            "Authorization": self.token,
            "User-Agent": self.user_agent,
            "Content-Type": f"multipart/form-data; boundary={boundary}"
        }

        req = urllib.request.Request(url, data=bytes(body_bytes), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as resp:
                resp_data = resp.read().decode("utf-8")
                return json.loads(resp_data)
        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8", errors="ignore")
            raise RuntimeError(f"HTTP {e.code}: {e.reason} - {error_body}")
        except Exception as e:
            raise RuntimeError(f"Loi ket noi Modrinth API: {e}")


class ModrinthUploadCoordinator:
    """Class 6: Điều phối duyệt danh sách JAR, cấu hình metadata và thực hiện upload."""

    def __init__(self, dry_run=False, target_dir="done"):
        self.dry_run = dry_run
        self.target_dir = Path(target_dir)
        self.config_loader = ModrinthConfigLoader()
        self.config = self.config_loader.load_config()

    def run(self):
        print("=" * 65)
        print(" PAYBOT MODRINTH OFFICIAL API UPLOADER (Labrinth v2)")
        print("=" * 65)

        if not self.config["token"] and not self.dry_run:
            print("\n[LOI] Chua tim thay Modrinth Token!")
            sys.exit(1)

        api_client = ModrinthApiClient(self.config["token"])

        # Lấy danh sách files đã có trên project để không bị duplicate
        existing_files = set()
        if not self.dry_run:
            print(f"[*] Dang kiem tra cac file da co tren project '{self.config['slug']}' ({self.config['project_id']})...")
            existing_files = api_client.get_project_files(self.config["project_id"])
            print(f"[*] Tim thay {len(existing_files)} file da ton tai tren Modrinth.")

        # Tìm toàn bộ file JAR
        jar_files = sorted(list(self.target_dir.rglob("*.jar")))
        print(f"[*] Tim thay {len(jar_files)} file JAR trong '{self.target_dir}'")

        success_count = 0
        skipped_count = 0
        failed_count = 0

        for jar_path in jar_files:
            filename = jar_path.name

            # Bỏ qua file kích thước bất thường (< 100KB hoặc lỗi truncated)
            if jar_path.stat().st_size < 100 * 1024:
                skipped_count += 1
                continue
            if "NeoForge-1.20.3" in filename:
                print(f"[-] Bo qua file hong da nhan dien: {filename}")
                skipped_count += 1
                continue

            info = JarMetadataExtractor.extract_info(jar_path)
            if not info:
                skipped_count += 1
                continue

            # Bỏ qua nếu file đã tồn tại trên Modrinth
            if filename in existing_files:
                print(f"[-] Da co tren Modrinth, bo qua: {filename}")
                skipped_count += 1
                continue

            # Build dependencies (đặc biệt là Fabric API nếu loader là fabric)
            deps = FabricDependencyResolver.resolve_dependencies(info["loader"], info["mc_version"])
            changelog = ChangelogFormatter.get_changelog(info["mod_version"])

            # Unique version_number cho Modrinth
            version_number = f"{info['mod_version']}+{info['loader']}.{info['mc_version']}"
            if info["loader"] == "paper":
                version_number = f"{info['mod_version']}+paper"

            payload_data = {
                "name": info["display_name"],
                "version_number": version_number,
                "changelog": changelog,
                "dependencies": deps,
                "game_versions": info["game_versions"],
                "version_type": self.config["version_type"],
                "loaders": info["loaders"],
                "featured": False,
                "project_id": self.config["project_id"],
                "file_parts": ["file"],
                "primary_file": "file"
            }

            print(f"\n[+] Dang upload: {info['display_name']}")
            print(f"    - File: {filename} ({jar_path.stat().st_size / (1024*1024):.2f} MB)")
            print(f"    - Version: {version_number} ({self.config['version_type']})")
            print(f"    - Loaders: {info['loaders']} | MC: {info['game_versions']}")
            if deps:
                print(f"    - Dependencies: Fabric API ({FabricDependencyResolver.FABRIC_API_PROJECT_ID})")

            if self.dry_run:
                print(f"    [DRY-RUN] Hop le 100%. Bo qua buoc POST API.")
                success_count += 1
            else:
                try:
                    res = api_client.create_version(payload_data, str(jar_path))
                    print(f"    [OK] Upload thanh cong! Version ID: {res.get('id', 'OK')}")
                    success_count += 1
                    time.sleep(2.0) # Rate limit protection
                except Exception as e:
                    print(f"    [ERROR] Upload that bai: {e}")
                    failed_count += 1
                    time.sleep(2.0)

        print("\n" + "=" * 65)
        print(f"TONG KET: Thanh cong={success_count} | Bo qua={skipped_count} | Loi={failed_count}")
        print("=" * 65)


if __name__ == "__main__":
    dry = "--dry-run" in sys.argv
    uploader = ModrinthUploadCoordinator(dry_run=dry)
    uploader.run()
