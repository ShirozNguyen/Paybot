# 💎 PayBot Multi-Loader 💎

### Hệ Thống Tích Hợp Thanh Toán Ngân Hàng (VietQR) & Thẻ Cào Tự Động Cho Minecraft

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Gradle](https://img.shields.io/badge/Gradle-8.14%20|%209.5-02303A.svg?style=for-the-badge&logo=gradle)](https://gradle.org)
[![Version](https://img.shields.io/badge/Version-v5.5.5-brightgreen.svg?style=for-the-badge)](https://modrinth.com/plugin/paybot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

[![Paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)](https://modrinth.com/plugin/paybot)
[![Purpur](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg)](https://modrinth.com/plugin/paybot)
[![Folia](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/folia_vector.svg)](https://modrinth.com/plugin/paybot)
[![Fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)](https://modrinth.com/project/paybotmod)
[![Quilt](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/quilt_vector.svg)](https://modrinth.com/project/paybotmod)
[![Forge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/forge_vector.svg)](https://modrinth.com/project/paybotmod)
[![NeoForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg)](https://modrinth.com/project/paybotmod)

**[💬 Discord Hỗ Trợ & Hướng Dẫn Cài Đặt](https://discord.gg/QdE5uNYqrV)** • **[📦 Official Addon (PayBot++)](https://modrinth.com/plugin/paybotpp)** • **[🌐 Hangar](https://hangar.papermc.io/TheRealShiroz/PayBot)** • **[☕ SpigotMC](https://www.spigotmc.org/resources/paybot.134369)**

---

## 📖 Giới Thiệu (Overview)

**PayBot** là giải pháp toàn diện hỗ trợ máy chủ Minecraft tự động hóa quy trình nạp tiền và donate từ người chơi thông qua **Chuyển khoản Ngân Hàng (VietQR SePay)** và **Thẻ cào điện thoại/game (TheSieuRe, GachThePro...)**. 

Dự án được xây dựng với kiến trúc **100% Server-Side Multi-Loader**, vận hành mượt mà trên tất cả các nền tảng server phổ biến hiện nay từ Minecraft **1.16.5** đến **1.21.11+** và các phiên bản **Snapshot 26.x**.

---

## 🌟 Ưu Điểm Vượt Trội (Key Highlights)

### 🛡️ 1. 100% Server-Side (Không Cần Cài Mod Ở Phía Client)
* Người chơi sử dụng **Minecraft Vanilla thuần** (hoặc bất kỳ client launcher nào như Lunar, Badlion, TLauncher, Prism, Feather...) đều có thể tham gia và nạp tiền bình thường.
* Client không cần cài đặt thêm mod, không cần resource pack, không bị giới hạn phiên bản.

### 🔒 2. Cơ Chế Chống Trộm & Dupe Đồ GUI Độc Quyền (GUI Anti-Theft Protection)
* Được trang bị bộ lọc bảo mật đa tầng, khóa chặt và vô hiệu hóa **100% các thủ thuật gian lận / rút trộm item** từ menu GUI:
  - ❌ Chặn **Shift + Click** rút đồ về kho cá nhân (`QUICK_MOVE`).
  - ❌ Chặn **Double Click** vào vật phẩm tương tự trên Hotbar để gom đồ (`PICKUP_ALL`).
  - ❌ Chặn **Phím số Hotbar (1-9, F)** để tráo đổi item (`SWAP`).
  - ❌ Chặn **Phím Drop (Q / Ctrl+Q)** vứt item GUI ra đất (`THROW`).
  - ❌ Chặn **Chuột giữa (Middle Click)** nhân bản item (`CLONE`).
  - ❌ Chặn **Kéo rê chuột** phân tán item (`DRAG`).
* Tự động ép đồng bộ tức thì (`sendAllDataToRemote()`) để dập tắt mọi hiển thị ảo của cheat/hack client.

### 🌐 3. Tương Thích Đa Nền Tảng Siêu Rộng (8 Nền Tảng Loader)
* Hỗ trợ đồng thời: **Paper, Purpur, Spigot, Folia, Fabric, Quilt, Forge, NeoForge**.
* Kiến trúc phân tách theo từng bản Minecraft (Granular Multi-Submodules): Mỗi phiên bản Minecraft chạy đúng bytecode tối ưu nhất, không bị lỗi mapping hay phụ thuộc chéo.

### 🗺️ 4. Bản Đồ VietQR Sắc Nét & Khóa Map Chống Ghi Đè (Map Lock)
* Render hình ảnh mã VietQR thanh toán trực tiếp lên tấm Bản Đồ cầm tay của Minecraft với độ tương phản cao, quét mã siêu nhạy qua app ngân hàng.
* Tích hợp cơ chế **Lock Map độc quyền**: Khóa cứng dữ liệu bản đồ, ngăn chặn hoàn toàn việc địa hình thế giới hoặc người chơi khác vô tình ghi đè làm hỏng hình ảnh QR.

### ⚡ 5. Vận Hành Kép Linh Hoạt: Standalone hoặc Bot Discord
* **Chế độ Standalone (Độc lập)**: Tự động kết nối SePay API và cổng thẻ cào trực tiếp từ server Minecraft. **Không cần mở port router/firewall**, không bắt buộc phải có Bot Discord.
* **Chế độ Bot Discord (Nâng cao)**: Kết nối với Bot Discord trung gian để quản lý đơn hàng tập trung, gửi thông báo nạp tiền qua Discord Webhook, và phân phối phần thưởng offline an toàn.

### 🎨 6. Đồ Họa & Màu Sắc Đẳng Cấp (Hex & Gradient)
* Hỗ trợ đầy đủ định dạng mã màu: `&`, `§`, `#RRGGBB` và `<gradient:HEX1:HEX2>text</gradient>`.
* Hệ thống hiển thị Tên và Lore mượt mà xuyên suốt từ thời kỳ NBT Tag (1.16 - 1.20.4) cho đến hệ thống Data Components hiện đại (1.20.5 - 1.21.11+).

### 🚀 7. Tương Thích Folia & Đa Luồng (Thread-Safety)
* 100% tương thích kiến trúc phân vùng luồng của **Folia** và **Canvas**, đảm bảo không gây crash hay lag tick máy chủ.

---

## 🎮 Lệnh & Quyền Hạn (Commands & Permissions)

### Dành Cho Người Chơi (Mặc định có quyền)
| Lệnh | Mô tả |
| :--- | :--- |
| `/napbank` | Mở GUI nạp tiền ngân hàng qua mã VietQR tự động |
| `/napthe` | Mở GUI nạp thẻ cào (chọn nhà mạng, mệnh giá, nhập mã & serial) |
| `/rewardclaim [confirm]` | Xem và nhận phần thưởng nạp tiền khi online trở lại |
| `/paybotplaceholder` | Mở bảng thống kê nạp tiền ingame (không bắt buộc cài PAPI) |

### Dành Cho Quản Trị Viên (`naptien.admin` hoặc OP)
| Lệnh | Mô tả |
| :--- | :--- |
| `/sepaysetup` | Hướng dẫn và cấu hình nhanh Token SePay tự động lấy số tài khoản |
| `/cardsetup` | Trình hướng dẫn cài đặt API gạch thẻ cào (TheSieuRe, GachThePro...) |
| `/cardapisetup` | GUI chuyển đổi nhanh giữa các cổng đổi thẻ cào |
| `/chinhsuamenhgianap` | GUI tùy biến lệnh thưởng (reward commands) theo từng mệnh giá |
| `/PayBotSetup` | Kiểm tra tổng quan trạng thái cấu hình và kết nối của PayBot |
| `/topuplist all` | Xem danh sách và duyệt đơn hàng nạp tiền đang chờ |
| `/testnapbank [số_tiền]` | Giả lập nạp bank thành công để kiểm tra lệnh thưởng và hiệu ứng |
| `/testnapthe [nhà_mạng] [mệnh_giá]` | Giả lập nạp thẻ cào thành công để test hệ thống |
| `/paybot reload` | Nạp lại toàn bộ file `config.yml` và áp dụng ngay lập tức |

---

## 🧩 PlaceholderAPI Hỗ Trợ

> *Hỗ trợ hiển thị trên Scoreboard, TAB, Chat, Hologram (Yêu cầu cài [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) trên server Bukkit/Paper).*

* `%paybot_player_topup%`: Tổng số tiền đã nạp của người chơi (đã định dạng dấu chấm).
* `%paybot_player_bank%`: Tổng tiền nạp qua ngân hàng của người chơi.
* `%paybot_player_card%`: Tổng tiền nạp qua thẻ cào của người chơi.
* `%paybot_total_topup%`: Tổng doanh thu toàn server (Bank + Card).
* `%paybot_total_players%`: Tổng số người chơi đã từng nạp tiền trên server.
* `%paybot_top1_name%` .. `%paybot_top10_name%`: Tên người chơi Top 1 đến Top 10 nạp nhiều nhất.
* `%paybot_top1_amount%` .. `%paybot_top10_amount%`: Số tiền nạp tương ứng của Top 1 đến Top 10.
* `%paybot_db_status%`: Trạng thái kết nối CSDL (`MySQL` hoặc `SQLite`).

---

## ⚖️ Thỏa Thuận Người Dùng & Điều Khoản (User Agreement)

### 🇻🇳 Thỏa Thuận Người Dùng (Tiếng Việt)

#### 1. Quyền hạn của người dùng:
- Bạn được phép tạo video, chụp ảnh màn hình và chia sẻ ở bất cứ đâu, với điều kiện ghi rõ nguồn và dẫn link về trang phát hành chính thức của PayBot.
- Bạn được phép sử dụng PayBot làm dependency cho các plugin/mod khác khi có ghi công tác giả.
- Tác giả ưu tiên cập nhật bản tiếng Việt đầy đủ nhất; đối với bản dịch tiếng Anh bạn có thể đối chiếu thêm để tránh thiếu sót.

#### 2. Hành vi bị nghiêm cấm:
- **Tuyệt đối không phân phối lại, bán lại hoặc re-upload** plugin/mod này lên bất kỳ nền tảng nào khi chưa được tác giả cho phép.
- Không được dịch ngược (decompile) hoặc can thiệp mã nguồn với mục đích xấu hoặc xâm phạm bản quyền.

#### 3. Cam kết từ tác giả:
- Plugin/Mod/Bot **không chứa backdoor, không có khả năng phá hoại (raid) server**.
- Hệ thống **tuyệt đối không can thiệp, không tráo đổi** mã QR hay thông tin tài khoản ngân hàng của chủ server; không có bất kỳ hành vi chiếm đoạt tiền nạp của người chơi.
- Dữ liệu thu thập chỉ phục vụ mục đích kỹ thuật và đối soát giao dịch (tên người chơi, mệnh giá, mã thẻ cào, mã đơn hàng).

*Cập nhật lần cuối: 18/09/2026 (Theo giờ Việt Nam).*

---

### 🇬🇧 User Agreement (English)

#### 1. Permitted Uses:
- You may create videos, capture screenshots, and share them anywhere, provided that you credit PayBot and link back to this official page.
- You may use this project as a dependency for other plugins/mods with appropriate attribution.

#### 2. Prohibited Uses:
- **Redistributing, reselling, or re-uploading** this plugin/mod without explicit authorization is strictly prohibited.
- Reverse engineering or decompiling for malicious purposes is forbidden.

#### 3. Author Commitments:
- The plugin/mod/bot **contains no backdoors, malware, or server-damaging routines**.
- The author **never alters** your bank QR details or payment credentials for personal benefit; zero risk of payment redirection.
- Data collected is limited strictly to transaction processing and fraud prevention.

*Last updated: September 18, 2026.*

---

## 📡 Công Khai Dữ Liệu Chia Sẻ (Data Disclosure)

PayBot chỉ truyền dữ liệu tới các cổng thanh toán bên thứ ba khi admin server đã chủ động cấu hình:
* **[SePay](https://sepay.vn/)**: Nhận thông tin đối soát giao dịch ngân hàng / ví điện tử (qua API Token hoặc Webhook).
* **[TheSieuRe](https://thesieure.com/) / [GachThePro](https://gachthepro.com)**: Nhận thông tin thẻ cào (nhà mạng, mã thẻ, số serial, mệnh giá) để xử lý đổi thẻ.
* **Discord Bot (Tùy chọn)**: Nhận thông báo giao dịch để đồng bộ đơn hàng khi sử dụng tính năng liên kết Discord.

*Không có bất kỳ dữ liệu nhạy cảm nào bị chia sẻ ra ngoài khi chưa cấu hình.*

---

## 🔗 Liên Kết & Hỗ Trợ

* **Tác giả:** `TheRealShiroz`
* **Discord Hỗ Trợ:** [https://discord.gg/QdE5uNYqrV](https://discord.gg/QdE5uNYqrV)
* **Liên hệ trực tiếp:** [https://guns.lol/TheRealShiroz](https://guns.lol/TheRealShiroz)
* **Modrinth Plugin:** [https://modrinth.com/plugin/paybot](https://modrinth.com/plugin/paybot)
* **Modrinth Mod:** [https://modrinth.com/project/paybotmod](https://modrinth.com/project/paybotmod)
* **SpigotMC:** [https://www.spigotmc.org/resources/paybot.134369](https://www.spigotmc.org/resources/paybot.134369)
* **Hangar:** [https://hangar.papermc.io/TheRealShiroz/PayBot](https://hangar.papermc.io/TheRealShiroz/PayBot)
