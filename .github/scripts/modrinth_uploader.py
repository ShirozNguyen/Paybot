#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
.github/scripts/modrinth_uploader.py — PayBot Modrinth Official API Uploader (Labrinth v2)
Tuân thủ Rule 17: Mỗi class là 1 chức năng riêng biệt.
Version: 7.0.1 (CI Runner Engine)
"""

import os
import sys
import time
import json
import re
from pathlib import Path
import requests

# Đảm bảo stdout / stderr hỗ trợ UTF-8 và line-buffering
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
    sys.stderr.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
except Exception:
    pass


class ModrinthConfigLoader:
    """Class 1: Quản lý đọc và kiểm tra cấu hình kết nối Modrinth API từ biến môi trường và file config."""

    def __init__(self, config_path="modrinth.json"):
        self.config_path = config_path

    def load_config(self):
        config = {
            "token": os.environ.get("MODRINTH_TOKEN", "").strip(),
            "project_id": "mLgal5cH",  # Project ID của paybot
            "slug": "paybot",
            "version_type": os.environ.get("MODRINTH_VERSION_TYPE", "release").strip(),
            "api_url": "https://api.modrinth.com/v2"
        }

        # Đọc cấu hình bổ sung từ modrinth.json nếu có
        cfg_file = Path(self.config_path)
        if cfg_file.exists():
            try:
                data = json.loads(cfg_file.read_text(encoding="utf-8"))
                for k in ["token", "version_type", "project_id", "slug"]:
                    if k in data and data[k]:
                        config[k] = str(data[k]).strip()
            except Exception as e:
                print(f"[WARN] Khong the doc {self.config_path}: {e}", flush=True)

        return config


class FabricApiVersionResolver:
    """Class 2: Truy vấn Modrinth API để tìm exact version_id của Fabric API cho từng Minecraft version."""

    FABRIC_API_PROJECT_ID = "P7dR8mSH"

    def __init__(self, session=None):
        self.cache = {}
        self.session = session or requests.Session()
        self.session.headers.update({"User-Agent": "PayBot-Modrinth-DependencyResolver"})

    def get_version_id_for_mc(self, mc_version: str) -> str:
        if mc_version in self.cache:
            return self.cache[mc_version]

        query = {
            "game_versions": json.dumps([mc_version]),
            "loaders": json.dumps(["fabric"])
        }
        url = f"https://api.modrinth.com/v2/project/{self.FABRIC_API_PROJECT_ID}/version"

        try:
            resp = self.session.get(url, params=query, timeout=30)
            if resp.status_code == 200:
                versions = resp.json()
                if versions:
                    chosen = versions[0]
                    v_id = chosen["id"]
                    v_num = chosen["version_number"]
                    self.cache[mc_version] = v_id
                    print(f"    [FabricApiResolver] MC {mc_version} -> Fabric API version {v_num} (ID: {v_id})", flush=True)
                    return v_id
                else:
                    print(f"    [FabricApiResolver] Khong tim thay ban Fabric API cho MC {mc_version}, fallback ve project dependency", flush=True)
                    self.cache[mc_version] = None
                    return None
            else:
                self.cache[mc_version] = None
                return None
        except Exception as e:
            print(f"    [FabricApiResolver] Loi truy van Fabric API cho MC {mc_version}: {e}", flush=True)
            self.cache[mc_version] = None
            return None


class FabricDependencyResolver:
    """Class 3: Ánh xạ và giải quyết dependencies cho Fabric với exact version_id."""

    FABRIC_API_PROJECT_ID = "P7dR8mSH"

    def __init__(self, api_resolver: FabricApiVersionResolver):
        self.api_resolver = api_resolver

    def resolve_dependencies(self, loader: str, mc_version: str) -> list:
        if loader != "fabric":
            return []

        exact_version_id = self.api_resolver.get_version_id_for_mc(mc_version)
        dep = {
            "project_id": self.FABRIC_API_PROJECT_ID,
            "dependency_type": "required"
        }
        if exact_version_id:
            dep["version_id"] = exact_version_id

        return [dep]


class PaperSupportedVersionsProvider:
    """Class 4: Cung cấp danh sách các phiên bản Minecraft mà Paper/Purpur/Folia Plugin tương thích (1.13 - 26.3)."""

    SUPPORTED_GAME_VERSIONS = [
        "1.13", "1.13.1", "1.13.2",
        "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
        "1.15", "1.15.1", "1.15.2",
        "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
        "1.17", "1.17.1",
        "1.18", "1.18.1", "1.18.2",
        "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
        "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
        "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
        "26.1", "26.1.1", "26.1.2", "26.2", "26.3"
    ]

    @classmethod
    def get_game_versions(cls) -> list:
        return list(cls.SUPPORTED_GAME_VERSIONS)


class JarMetadataExtractor:
    """Class 5: Trích xuất metadata (loader, game versions, mod version, display name) từ tên file JAR."""

    @staticmethod
    def extract_info(file_path):
        filename = Path(file_path).name

        # Bỏ qua các file dummy wrapper hoặc file corrupted cũ
        prefixes = ["Fabric_Loader", "Forge_Loader", "NeoForge_Loader"]
        dummy_versions = ["5.5.5", "5.5.6", "5.5.7", "5.5.8", "5.5.10", "5.5.11", "5.5.12", "5.5.13", "5.5.14", "5.5.15", "5.5.19", "5.5.20", "5.5.21"]
        dummy_names = {f"{p}-{v}.jar" for p in prefixes for v in dummy_versions}
        dummy_names.update({f"PayBot-Mod-NeoForge-1.20.3-{v}.jar" for v in dummy_versions})
        if filename in dummy_names:
            return None

        # Trường hợp Plugin Paper/Purpur/Folia: PayBot-Plugin-7.0.1.jar
        if "Paper" in filename or "Plugin" in filename:
            ver_match = re.search(r"(\d+\.\d+\.\d+)", filename)
            version = ver_match.group(1) if ver_match else "7.0.1"
            return {
                "loader": "paper",
                "loaders": ["paper", "purpur", "folia"],
                "mc_version": "1.20.4",
                "game_versions": PaperSupportedVersionsProvider.get_game_versions(),
                "mod_version": version,
                "display_name": f"PayBot {version} (Paper / Purpur / Folia)"
            }

        # Trường hợp Mod: PayBot-Mod-Fabric-1.20.1-7.0.1.jar hoặc Forge / NeoForge
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
    """Class 6: Chuẩn bị nội dung changelog định dạng Markdown từ các file tài liệu phát hành."""

    @staticmethod
    def get_changelog(version="7.0.1"):
        candidate_paths = [
            Path(f".github/release_notes/MODRINTH_CHANGELOG_{version}.md"),
            Path(f"docs/logs/MODRINTH_CHANGELOG_{version}.md"),
            Path(f"docs/MODRINTH_CHANGELOG_{version}.md"),
            Path(".github/release_notes/MODRINTH_CHANGELOG_7.0.1.md")
        ]
        for p in candidate_paths:
            if p.exists():
                return p.read_text(encoding="utf-8")
        return f"# PayBot {version}\n\nBản cập nhật PayBot {version} tối ưu độ ổn định, tương thích và bảo mật."


class ModrinthApiClient:
    """Class 7: Giao tiếp với Modrinth Labrinth API v2 để tạo version mới qua HTTP streaming."""

    def __init__(self, token, api_url="https://api.modrinth.com/v2"):
        self.token = token
        self.api_url = api_url
        self.user_agent = "ShirozNguyen/PayBot/7.0.1 (https://github.com/ShirozNguyen/PayBot)"
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": self.token,
            "User-Agent": self.user_agent
        })

    def get_project_files(self, project_id):
        """Lấy tập hợp các tên file đã được upload lên project."""
        url = f"{self.api_url}/project/{project_id}/version"
        files = set()
        try:
            resp = self.session.get(url, timeout=60)
            if resp.status_code == 200:
                versions = resp.json()
                for v in versions:
                    for f in v.get("files", []):
                        fn = f.get("filename")
                        if fn:
                            files.add(fn)
            else:
                print(f"[WARN] Khong the lay danh sach files (HTTP {resp.status_code})", flush=True)
        except Exception as e:
            print(f"[WARN] Khong the lay danh sach files hien co: {e}", flush=True)
        return files

    def create_version(self, metadata, jar_path):
        """Gửi request multipart/form-data tạo version mới trên Modrinth với streaming upload từ đĩa."""
        url = f"{self.api_url}/version"
        filename = Path(jar_path).name
        max_retries = 3

        for attempt in range(1, max_retries + 1):
            try:
                with open(jar_path, "rb") as f:
                    files = {
                        "data": (None, json.dumps(metadata, ensure_ascii=False), "application/json"),
                        "file": (filename, f, "application/java-archive")
                    }
                    resp = self.session.post(url, files=files, timeout=300)

                if resp.status_code in (200, 201):
                    return resp.json()
                elif resp.status_code == 400:
                    raise RuntimeError(f"HTTP 400 Bad Request: {resp.text}")
                elif resp.status_code in (401, 403):
                    raise RuntimeError(f"HTTP {resp.status_code}: Token khong hop le hoac khong co quyen!")
                elif resp.status_code == 429:
                    retry_after = int(resp.headers.get("Retry-After", 10))
                    print(f"    [WARN] Rate limited (429), cho {retry_after} giay...", flush=True)
                    time.sleep(retry_after)
                    continue
                else:
                    resp.raise_for_status()
            except requests.RequestException as e:
                if attempt == max_retries:
                    raise RuntimeError(f"Loi ket noi Modrinth API sau {max_retries} lan thu: {e}")
                print(f"    [WARN] Thu lai lan {attempt}/{max_retries} do loi ket noi: {e}", flush=True)
                time.sleep(3)


class ModrinthUploadCoordinator:
    """Class 8: Điều phối duyệt danh sách JAR, cấu hình metadata và thực hiện upload an toàn."""

    def __init__(self, dry_run=False, target_dir="Done"):
        self.dry_run = dry_run
        td = Path(target_dir)
        if not td.exists() and Path(target_dir.lower()).exists():
            td = Path(target_dir.lower())
        self.target_dir = td

        self.config_loader = ModrinthConfigLoader()
        self.config = self.config_loader.load_config()
        self.api_resolver = FabricApiVersionResolver()
        self.dep_resolver = FabricDependencyResolver(self.api_resolver)

    def run(self):
        print("=" * 65, flush=True)
        print(" PAYBOT MODRINTH OFFICIAL API UPLOADER (Labrinth v2)", flush=True)
        print(" Version: 7.0.1 — GitHub Actions CI Runner Engine", flush=True)
        print("=" * 65, flush=True)

        if not self.config["token"] and not self.dry_run:
            print("\n[LOI] Chua tim thay MODRINTH_TOKEN trong bien moi truong!", flush=True)
            sys.exit(1)

        api_client = ModrinthApiClient(self.config["token"])

        existing_files = set()
        if not self.dry_run:
            print(f"[*] Dang kiem tra cac file da co tren project '{self.config['slug']}' ({self.config['project_id']})...", flush=True)
            existing_files = api_client.get_project_files(self.config["project_id"])
            print(f"[*] Tim thay {len(existing_files)} file da ton tai tren Modrinth.", flush=True)

        jar_files = sorted(list(self.target_dir.rglob("*.jar")))
        print(f"[*] Tim thay {len(jar_files)} file JAR trong '{self.target_dir}'", flush=True)

        success_count = 0
        skipped_count = 0
        failed_count = 0

        for jar_path in jar_files:
            filename = jar_path.name

            # Bỏ qua file kích thước bất thường (< 100KB hoặc lỗi truncated)
            if jar_path.stat().st_size < 100 * 1024:
                skipped_count += 1
                continue

            info = JarMetadataExtractor.extract_info(jar_path)
            if not info:
                skipped_count += 1
                continue

            # Bỏ qua nếu file đã tồn tại trên Modrinth
            if filename in existing_files:
                print(f"[-] Da co tren Modrinth, bo qua: {filename}", flush=True)
                skipped_count += 1
                continue

            # Build dependencies với exact version_id cho Fabric API
            deps = self.dep_resolver.resolve_dependencies(info["loader"], info["mc_version"])
            changelog = ChangelogFormatter.get_changelog(info["mod_version"])

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

            print(f"\n[+] Dang upload: {info['display_name']}", flush=True)
            print(f"    - File: {filename} ({jar_path.stat().st_size / (1024*1024):.2f} MB)", flush=True)
            print(f"    - Version: {version_number} ({self.config['version_type']})", flush=True)
            print(f"    - Loaders: {info['loaders']} | MC: {info['game_versions']}", flush=True)
            if deps:
                dep_info = deps[0]
                print(f"    - Dependencies: Fabric API (Project: {dep_info.get('project_id')}, Version ID: {dep_info.get('version_id')})", flush=True)

            if self.dry_run:
                print(f"    [DRY-RUN] Hop le 100%. Bo qua buoc POST API.", flush=True)
                success_count += 1
            else:
                try:
                    res = api_client.create_version(payload_data, str(jar_path))
                    print(f"    [OK] Upload thanh cong! Version ID: {res.get('id', 'OK')}", flush=True)
                    success_count += 1
                    time.sleep(1.0)  # Rate limit protection
                except Exception as e:
                    print(f"    [ERROR] Upload that bai: {e}", flush=True)
                    failed_count += 1
                    time.sleep(1.5)
                    if "daily version upload limit reached" in str(e).lower() or "limit_reached" in str(e).lower():
                        print("\n[!] Da cham gioi han upload hang ngay cua Modrinth (daily version upload limit reached).", flush=True)
                        print("[!] Cac file con lai se tu dong tiep tuc khi chay lai tool sau khi Modrinth reset gioi han.", flush=True)
                        break

        print("\n" + "=" * 65, flush=True)
        print(f"TONG KET: Thanh cong={success_count} | Bo qua={skipped_count} | Loi={failed_count}", flush=True)
        print("=" * 65, flush=True)

        if failed_count > 0:
            sys.exit(1)


if __name__ == "__main__":
    dry = "--dry-run" in sys.argv
    uploader = ModrinthUploadCoordinator(dry_run=dry)
    uploader.run()
