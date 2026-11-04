# 🚀 PayBot v7.0.2 — Bản Cập Nhật Lớn: Đồng Bộ Cấu Hình & Hoàn Thiện Master Setup Hub Live

Chào mừng bạn đến với **PayBot v7.0.2**! Đây là bản cập nhật bảo trì và nâng cấp quan trọng, giải quyết triệt để các vấn đề đồng bộ cấu hình, kết nối trực tiếp dữ liệu sống cho Master Setup Hub (Mốc Nạp & Lịch Sử Giao Dịch) và đồng bộ hóa toàn bộ các biến thay thế (Placeholders) trên tất cả nền tảng Minecraft.

---

## 🌟 Điểm Nhấn Nổi Bật Trong Phiên Bản 7.0.2

### 🔄 1. Khắc Phục Triệt Để Lỗi Đồng Bộ Cấu Hình (Fix Config Sync & Watcher Loop)
* **Khắc phục lỗi mất cấu hình sau khi restart server**: Hệ thống cầu nối cấu hình thông minh (Dual-Write Schema Bridge) đảm bảo các cài đặt qua menu GUI luôn được ghi nhận chính xác vào cả cấu hình giao diện lẫn cấu hình lõi của hệ thống (`features.card-enabled`, `features.sepay-enabled`, `card-api.site`, `vietqr-template`), bảo toàn vĩnh viễn dữ liệu khi server khởi động lại.
* **Sửa lỗi Watcher Loop**: Khắc phục lỗi cơ chế khôi phục chú thích (`SmartConfigMerger`) nhận diện nhầm các dòng ghi chú hướng dẫn có chứa dấu hai chấm (`:`) thành key cấu hình, chấm dứt hoàn toàn hiện tượng file `config.yml` tự động bị ghi đè lặp lại mỗi 10 giây.
* **Đồng bộ hóa tức thì**: Lệnh `/paybot reload` và cơ chế nạp ngầm giờ đây luôn nạp lại dữ liệu mới nhất từ ổ đĩa vào bộ nhớ trước khi áp dụng tới các hệ thống thanh toán.

### 🎁 2. Hệ Thống Mốc Nạp (Milestones) Hoạt Động Trực Tiếp (100% Live CRUD)
* **Loại bỏ dữ liệu mô phỏng**: Danh sách mốc nạp cá nhân và mốc toàn server giờ đây được nạp và quản lý trực tiếp từ file `milestones.yml`.
* **Thêm / Sửa / Xóa mốc trực quan trong game**:
  * **Click Trái**: Chỉnh sửa số tiền mốc nạp.
  * **Shift + Click Trái**: Chỉnh sửa danh sách lệnh trao thưởng khi đạt mốc (hỗ trợ nhập 1 dòng với dấu `;` hoặc nhập từng dòng liên tiếp cho tới khi gõ `done`/`save`).
  * **Shift + Click Phải**: Xóa mốc nạp ngay lập tức.
  * **Nút [+ Thêm Mốc Nạp Mới]**: Tạo mốc nạp tích lũy mới cực kỳ nhanh chóng.
* **Chu kỳ Reset Tự Động (Scheduled Reset)**: Cấu hình trực quan đơn vị reset (`MINUTE`, `HOUR`, `DAILY`, `MONTHLY`, `YEARLY`), khoảng cách chu kỳ và tin nhắn thông báo khi reset.
* **Nút Reset Ngay Lập Tức (TNT)**: Cho phép quản trị viên kích hoạt làm mới toàn bộ tiến trình mốc nạp server ngay lập tức để mở mùa nạp mới chỉ với một cú click!

### 📋 3. Quản Lý Đơn Nạp (Transaction Manager) & Duyệt Thủ Công Trực Quan
* **Kết nối CSDL thời gian thực**: Hiển thị đầy đủ danh sách các giao dịch nạp Bank (VietQR) và Thẻ cào thực tế từ CSDL SQLite / MySQL.
* **Bộ lọc trạng thái thông minh**: Lọc nhanh các đơn `⏳ Chờ Duyệt (PENDING)`, `✅ Thành Công (SUCCESS)` và `❌ Thất Bại (FAILED)`.
* **Duyệt đơn thủ công 1-Click**: Bấm `Shift + Click` vào bất kỳ đơn chờ duyệt nào để mở giao diện xác nhận, kiểm tra chi tiết giao dịch và trao thưởng ngay lập tức cho người chơi (tự động đưa vào hàng chờ offline nếu người chơi không có mặt).

### 🧩 4. Đồng Bộ Hóa Toàn Diện Biến Thay Thế (Universal Placeholders)
* Giờ đây bạn có thể sử dụng linh hoạt mọi biến người chơi và số tiền trong các lệnh thưởng nạp mà không lo xung đột hay sai cú pháp:
  * **Tên người chơi**: `%player_name%`, `%player%`, `[playername]`, `{player}`, `{playername}`, `[player]`
  * **Số tiền nạp / mệnh giá**: `%amount%`, `{amount}`, `[amount]`
* Hỗ trợ đồng bộ 100% trên toàn bộ các nền tảng: **Paper, Purpur, Folia, Fabric, Forge, NeoForge** từ **1.16.5** đến **1.21.11** và **Snapshot 26.x**.

### 🎨 5. Tùy Biến Giao Diện Player GUI & Nối Toàn Bộ Menu Setup
* Bổ sung tính năng đổi Tên hiển thị và Dòng Lore cho **9 nhà mạng thẻ cào** và **9 mệnh giá ngân hàng** trong menu `/napthe` và `/napbank`.
* Nối toàn bộ sự kiện click và chat prompt cho menu Bot Discord Companion và menu Giả lập nạp ảo (Simulation Test).

---

## 📦 Hướng Dẫn Cài Đặt & Nâng Cấp

1. Tải về file JAR phù hợp với nền tảng máy chủ của bạn (`PayBot-7.0.2.jar` cho Paper/Purpur/Folia, hoặc file mod tương ứng cho Fabric/Forge/NeoForge).
2. Đặt file vào thư mục `plugins/` (hoặc `mods/`).
3. Khởi động server hoặc dùng lệnh `/paybot reload`.
4. Gõ lệnh `/paybot` (hoặc `/paybot setup`) để mở **Master Setup Hub** và trải nghiệm toàn bộ tính năng mới!

---

*Cảm ơn toàn thể cộng đồng máy chủ Minecraft đã luôn đồng hành, góp ý và ủng hộ PayBot!*
