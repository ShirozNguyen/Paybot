# 💎 PayBot Multi-Loader 💎

### Hệ Thống Tích Hợp Thanh Toán Ngân Hàng (VietQR) & Thẻ Cào Tự Động Cho Minecraft

<!-- ===== BUILD WITH ===== -->
[![Java 21](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/built-with/java21_46h.png)](https://www.oracle.com/java/)
[![Gradle](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/built-with/gradle_46h.png)](https://gradle.org)
[![Maven](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/built-with/maven_46h.png)](https://maven.apache.org)

<!-- ===== SUPPORTED PLATFORMS ===== -->
[![Paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)](https://modrinth.com/plugin/paybot)
[![Purpur](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg)](https://modrinth.com/plugin/paybot)
[![Fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)](https://modrinth.com/project/paybotmod)
[![Quilt](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/quilt_vector.svg)](https://modrinth.com/project/paybotmod)
[![Forge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/forge_vector.svg)](https://modrinth.com/project/paybotmod)
[![NeoForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg)](https://modrinth.com/project/paybotmod)

**[💬 Discord Hỗ Trợ & Hướng Dẫn Cài Đặt](https://discord.gg/QdE5uNYqrV)** 
• **[📦 Official Addon (PayBot++)](https://modrinth.com/plugin/paybotpp)** 
• **[🌐 Hangar](https://hangar.papermc.io/TheRealShiroz/PayBot)** 
• **[☕ SpigotMC](https://www.spigotmc.org/resources/paybot.134369)**


---

## 📖 Giới Thiệu (Overview)

**PayBot** là giải pháp toàn diện hỗ trợ máy chủ Minecraft tự động hóa quy trình nạp tiền và donate từ người chơi thông qua **Chuyển khoản Ngân Hàng (VietQR SePay)** và **Thẻ cào điện thoại/game (TheSieuRe, GachThePro...)**. 

Dự án được xây dựng với kiến trúc **100% Server-Side Multi-Loader**, vận hành mượt mà trên tất cả các nền tảng server phổ biến hiện nay từ Minecraft **1.16.5** đến **1.21.11+** và các phiên bản **Snapshot 26.x**.

---

## 🌟 Tính Năng Nổi Bật

### 🛡️ 1. Hoạt Động Hoàn Toàn Phía Server (100% Server-Side)
* Người chơi dùng **Minecraft nguyên bản (Vanilla)** hay bất cứ launcher nào (Lunar, Badlion, TLauncher, Prism...) đều vào server và nạp tiền được ngay.
* Người chơi **không cần cài thêm mod** hay resource pack, vào là trải nghiệm được luôn.

### 🔒 2. Chống Gian Lận & Giữ An Toàn Tuyệt Đối Cho Menu (GUI)
* Tự động bảo vệ các vật phẩm trong giao diện menu nạp tiền, chặn đứng mọi thao tác lấy trộm hay dupe đồ:
  - ❌ Chặn kéo rê chuột, bấm chuột giữa hoặc dùng phím tắt Hotbar (1-9, F) để tráo đồ.
  - ❌ Chặn phím vứt đồ (Q) và Shift + Click rút đồ từ menu về túi.
  - ❌ Tự động đồng bộ ngay lập tức để người chơi không bị kẹt đồ ảo hay lợi dụng hack client.

### 🌐 3. Hỗ Trợ Đa Dạng Các Nền Tảng Server
* Chạy mượt mà trên: **Paper, Purpur, Folia, Fabric, Quilt, Forge và NeoForge**.
* *(Lưu ý: Đối với hệ máy chủ Bukkit, PayBot tối ưu tốt nhất cho Paper, Purpur và Folia để đảm bảo hiệu năng và hỗ trợ đầy đủ các tính năng hiện đại).*
* Từng bản cài đặt được tối ưu chuẩn xác cho phiên bản Minecraft tương ứng, giúp server vận hành nhẹ nhàng, ổn định.

### 🗺️ 4. Bản Đồ VietQR Tiện Lợi & Khóa Hiển Thị Chống Đè Địa Hình
* Tạo mã VietQR thanh toán rõ nét trực tiếp trên tấm Bản Đồ cầm tay, quét mã siêu nhanh bằng ứng dụng ngân hàng.
* Tích hợp tính năng khóa bản đồ thông minh: Giữ cố định hình ảnh QR, tuyệt đối không bị địa hình thế giới xung quanh quét đè làm mất nét mã.

### ⚡ 5. Linh Hoạt Vận Hành: Chạy Độc Lập Hoặc Kèm Discord Bot
* **Chế độ Độc lập (Standalone)**: Tự động xử lý nạp ngân hàng qua SePay và cổng thẻ cào trực tiếp từ server. Cực kỳ đơn giản, **không cần mở port** mạng hay cấu hình firewall rườm rà.
* **Chế độ Discord Bot**: Kết nối với bot Discord để thông báo giao dịch vào kênh riêng, duyệt đơn tiện lợi và tự động trao quà khi người chơi vào lại game.

### 🎨 6. Màu Sắc Bắt Mắt & Giao Diện Thân Thiện
* Hỗ trợ đầy đủ các mã màu hiện đại: từ mã màu truyền thống `&`, `§` cho tới mã màu Hex `#RRGGBB` và dải màu chuyển động Gradient cực đẹp.
* Hiển thị sắc nét, đồng bộ chuẩn chỉnh trên tất cả các phiên bản Minecraft.

### 🚀 7. Tối Ưu Hiệu Năng & Tương Thích Folia
* Tương thích hoàn toàn với cơ chế đa luồng của Folia/Canvas, xử lý nhẹ nhàng, không gây giật lag hay tụt TPS server.

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

## 🇺🇸 PayBot - English Documentation

### 🌟 Key Highlights

#### 🛡️ 1. 100% Server-Side (No Client Mod Required)
* Players using pure **Vanilla Minecraft** or any launcher/client (Lunar, Badlion, TLauncher, Prism...) can join and top up immediately.
* **No client-side mod or resource pack is needed**.

#### 🔒 2. Anti-Theft & GUI Item Dupe Protection
* Comprehensive inventory click and slot drag protection, blocking 100% of GUI theft or ghost item exploits:
  - ❌ Blocks mouse drag, middle click (clone), and hotbar key swaps (1-9, F).
  - ❌ Blocks drop key (Q) and shift-click quick move from menu interfaces.
  - ❌ Automatically resynchronizes player inventory state immediately.

#### 🌐 3. Broad Multi-Platform Support
* Runs seamlessly on: **Paper, Purpur, Folia, Fabric, Quilt, Forge, and NeoForge**.
* *(Note: For Bukkit-family servers, Paper, Purpur, or Folia is recommended to take full advantage of modern multithreading and color rendering).*
* Granular builds optimized per Minecraft release for lightweight and stable performance.

#### 🗺️ 4. In-Game VietQR Handheld Map & Anti-Overwrite Map Lock
* Renders clear VietQR payment codes directly onto an in-game Minecraft Handheld Map, easily scannable via mobile banking apps.
* Built-in intelligent **Map Lock**: Permanently prevents surrounding world terrain from scanning and overwriting the QR image.

#### ⚡ 5. Dual Operating Modes: Standalone or Discord Bot
* **Standalone Mode**: Connects directly to SePay and scratch card gateways from the Minecraft server. No open port or router firewall config needed.
* **Discord Bot Mode**: Centralized transaction management, Discord Webhook notifications, and safe offline reward distribution.

#### 🎨 6. Rich Colors & Modern Design (Hex & Gradient)
* Full support for color formatting: traditional `&` and `§`, Hex `#RRGGBB`, and smooth `<gradient>` effects.
* Symmetrical and clean typography across all Minecraft releases.

#### 🚀 7. Folia & Thread-Safety Compliance
* Fully compliant with Folia's regional multithreading model, ensuring smooth TPS and zero server crashes.

---

### 🎮 Commands & Permissions

#### Player Commands (Default for everyone)
| Command | Description |
| :--- | :--- |
| `/napbank` | Opens the bank deposit GUI with an auto-generated VietQR map |
| `/napthe` | Opens the scratch card deposit GUI (telco, denomination, pin, serial) |
| `/rewardclaim [confirm]` | View and claim pending offline deposit rewards upon logging in |
| `/paybotplaceholder` | In-game deposit statistics panel (works even without PlaceholderAPI) |

#### Admin Commands (`naptien.admin` or OP)
| Command | Description |
| :--- | :--- |
| `/sepaysetup` | Quick interactive setup wizard with SePay API Token |
| `/cardsetup` | Interactive wizard to configure card exchange APIs (TheSieuRe, GachThePro...) |
| `/cardapisetup` | Quick GUI to toggle between card payment gateways |
| `/chinhsuamenhgianap` | Denomination reward command customization GUI |
| `/PayBotSetup` | System diagnostic overview of PayBot configurations and connectivity |
| `/topuplist all` | View and approve pending deposit orders |
| `/testnapbank [amount]` | Simulate a successful bank deposit to verify reward commands |
| `/testnapthe [carrier] [denom]` | Simulate a successful card deposit to verify rewards and effects |
| `/paybot reload` | Instantly reload configuration from `config.yml` |

---

### 🤖 Discord Bot (Optional Extended Automation)
The companion Discord Bot serves as an optional middleware layer between Minecraft and payment providers:
* Automatically forwards payment alerts and transaction receipts to dedicated Discord channels.
* Queues offline rewards if the server is temporarily offline and delivers them safely when reconnected.

---

## 🔗 Liên Kết & Hỗ Trợ / Official Links

* **Tác giả / Author:** `TheRealShiroz`
* **Discord Community:** [https://discord.gg/QdE5uNYqrV](https://discord.gg/QdE5uNYqrV)
* **Direct Contact:** [https://guns.lol/TheRealShiroz](https://guns.lol/TheRealShiroz)
* **Modrinth Plugin:** [https://modrinth.com/plugin/paybot](https://modrinth.com/plugin/paybot)
* **Modrinth Mod:** [https://modrinth.com/project/paybotmod](https://modrinth.com/project/paybotmod)
* **SpigotMC:** [https://www.spigotmc.org/resources/paybot.134369](https://www.spigotmc.org/resources/paybot.134369)
* **Hangar:** [https://hangar.papermc.io/TheRealShiroz/PayBot](https://hangar.papermc.io/TheRealShiroz/PayBot)
