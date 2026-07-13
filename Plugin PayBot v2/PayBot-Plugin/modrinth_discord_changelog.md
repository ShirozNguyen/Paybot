# CHÚNG TÔI ĐÃ PHÁT HÀNH PHIÊN BẢN v5.2.0! 🎉

Dưới đây là nội dung Changelog được chuẩn bị sẵn, bạn có thể copy trực tiếp để đăng lên Modrinth và Discord.

---

## 🟢 BẢN DÀNH CHO DISCORD (Định dạng Markdown gọn nhẹ)

```markdown
🚀 **PayBot Update - v5.2.0** (Đồng bộ Plugin & Fabric Mod)

Bản cập nhật mang lại khả năng tương thích với server Folia cho Plugin, cùng các nâng cấp độ ổn định cho tính năng đồng bộ cấu hình trên cả hai nền tảng!

✨ **Tính năng nổi bật:**
*   **Hỗ trợ Folia (Chỉ dành cho Bukkit Plugin):** Plugin hiện tại đã tương thích hoàn toàn khi chạy trên máy chủ Folia mà không gây lỗi thread lập lịch (Scheduler)!
*   **Cơ chế chuyển đổi Scheduler tự động (Plugin):** Tự động nhận diện Folia để sử dụng các Folia Scheduler (Async/GlobalRegion/Player). Nếu chạy Spigot/Paper thường, hệ thống tự động fallback về Bukkit Scheduler truyền thống để đảm bảo ổn định 100%.
*   **Đồng bộ phiên bản:** Cả Plugin và Fabric Mod được đồng bộ lên phiên bản **v5.2.0**.

🐛 **Sửa lỗi quan trọng (Bug Fixes cho cả Plugin & Fabric Mod):**
*   **Fix lỗi mất cấu hình khi đồng bộ:** Sửa bug tự động xóa các key cấu hình (như `sepay`, `card-api`) sau khi chạy setup wizard xong do template JAR thiếu key.
*   **Bảo vệ cấu hình danh sách (YAML Lists):** Thiết kế lại thuật toán quét dữ liệu thực tế (`hasRealData`), đảm bảo các danh sách cấu hình dạng list (ví dụ: `reward-commands: \n  - 'eco 100'`) không bao giờ bị xóa nhầm trên cả Plugin và Fabric Mod.
*   **Fix hardcode banner (Fabric Mod):** Hiển thị đúng version động của mod tại console lúc khởi động thay vì hardcode chuỗi `v5.0.1`.

👉 Hãy tải ngay phiên bản **v5.2.0** mới nhất để vận hành hệ thống nạp tự động trơn tru nhất!
```

---

## 🔵 BẢN DÀNH CHO MODRINTH (Định dạng Markdown chi tiết)

```markdown
# PayBot v5.2.0 Release - Folia Support (Plugin) & SmartConfigMerger Improvements (Plugin & Mod)

This release brings compatibility with **Folia** for Bukkit Plugin and improves config merger stability for both Plugin and Fabric Mod platforms.

### 🌟 What's New

*   **Folia Compatibility (Bukkit Plugin Only):**
    *   Successfully implemented a new scheduler wrapper (`SchedulerUtils`) to support Folia's thread scheduling model (`Bukkit.getAsyncScheduler()`, `Bukkit.getGlobalRegionScheduler()`, and `Player` regional scheduling).
    *   **Auto-fallback mechanism:** If the plugin detects that it is running on standard Bukkit/Spigot/Paper servers, it automatically falls back to the traditional `BukkitScheduler` to preserve maximum compatibility and performance.
*   **Unified Versioning (v5.2.0):**
    *   Both Bukkit Plugin and Fabric Mod versions are now synced at **5.2.0**.
    *   Fabric Mod startup banner fixed to display dynamic version fetched at runtime instead of hardcoded `v5.0.1`.

### 🐛 Bug Fixes (Plugin & Fabric Mod)

*   **SmartConfigMerger Configurations Loss Fixed:**
    *   Fixed a critical bug where the merger would occasionally delete configured sections (such as `sepay` and `card-api`) after setup wizards completed because the shipped JAR template didn't contain those keys.
*   **YAML Lists Protection (`hasRealData` Improvements):**
    *   Redesigned the `hasRealData` checker. The previous version failed to identify list elements (e.g., `- 'give %player% diamonds'`) because it filtered out lines without colons (`:`).
    *   The updated scanner checks nested items and values carefully, ensuring that non-default custom lists are never cleared.
```
