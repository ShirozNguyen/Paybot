# PayBot Fabric — Session Log

> Ngày: 2026-07-04
> Version: 5.0.2 → 5.0.4 (đang tiếp tục — mỗi Part tăng 1 patch theo yêu cầu, không cố định như session trước)
> Phiên trước (2026-06-24, v5.0.0) đã đóng — xem lịch sử đầy đủ tại CHANGELOG.md.

---

## Danh sách thay đổi

| Part | Version | File | Nội dung thay đổi |
|---|---|---|---|
| 1 | 5.0.3 | `StandaloneBankPoller.java` | **[Nghiêm trọng]** Xoá block `public class StandaloneBankPoller` bị duplicate (dòng 282-499 cũ) — bản v5.0.1 cũ (chỉ set BANK_PAID, bắt `/approve` tay) bị dán chồng lên bản v5.0.2 đúng (auto-reward). File trước đó KHÔNG COMPILE ĐƯỢC. |
| 2 | 5.0.4 | `SetupManager.java` | **[Nghiêm trọng]** Thêm `case CARD_SITE ->` bị thiếu trong `processInput()` — nhập site ở bước [1/4] của `/cardsetup` trước đây rơi vào `default` và tự huỷ session. Giờ validate đúng theo `StandaloneCardProcessor.getSupportedSites()`, sai thì hỏi lại thay vì huỷ. |
| 2 | 5.0.4 | `CommandRegistry.java` | Thêm `.suggests()` (Brigadier tab-complete) cho argument `site` của `/cardsetup <site>` — gõ Tab ra danh sách site hỗ trợ. Không dùng ClickEvent (giữ đúng nguyên tắc đã ghi chú ở `/connect`: tránh rủi ro API Text/ClickEvent đổi giữa các bản Minecraft). |
| 2 | 5.0.4 | `gradle.properties` | Bump `mod_version` 5.0.2 → 5.0.4 |
| 3 | 5.0.5 | `PluginHttpServer.java` | **[Nghiêm trọng]** Route `/receive-config` TRƯỚC ĐÂY KHÔNG TỒN TẠI — mọi request tới đây rơi vào `default -> ok("{\"ok\":true}")`, trả 200 GIẢ khiến bot.py tưởng push thành công nhưng KHÔNG áp dụng gì (silent no-op). Thêm `handleReceiveConfig()` thật: xử lý `bot_url` (mới — phục vụ cơ chế bot tự báo địa chỉ sau khi bỏ ngrok) và `card_rewards`/`bank_rewards` (đồng bộ đúng hành vi Bukkit plugin, tiện thể fix no-op cho cả push reward-config). |
| 3 | 5.0.5 | `gradle.properties` | Bump `mod_version` 5.0.4 → 5.0.5 |
| 4 | — | `BotHttpClient.java` (fabric) | HTTPS + TOFU cert-pinning (SHA-256 fingerprint, lưu vào config `bot-cert-fingerprint`, cảnh báo/chặn tuỳ `bot-cert-strict-pin`) + fallback tự thử `PayBotMod.DEFAULT_BOT_URL` nếu `bot-url` đã lưu bị lỗi. **Verify: compile thật + test thật TrustManager (cả 2 case đúng/sai fingerprint) qua server HTTPS test riêng trong sandbox.** |
| 4 | — | `PayBotMod.java` | Thêm hằng `DEFAULT_BOT_URL` — TRƯỚC ĐÂY fabric không có fallback bootstrap nào (khác plugin có `BOT_PUBLIC_URL`), `/connect` lần đầu sẽ luôn fail nếu `bot-url` trống. |
| 4 | — | `config-template.yml` | Fix `plugin-callback-url` default trỏ sai vào domain ngrok cũ + suffix `/api/sepay-ipn` sai ngữ cảnh — đổi về `""` đúng như comment mô tả. |
| 5 | — | `NapTienPlugin.java` | Thay `detectAndSaveNgrokUrl()` (query ngrok local API :4040) bằng `detectAndSaveCallbackUrl()` (tự dò IP qua `PublicIpResolver`, giống hệt nguồn `reportServerIp()` dùng). Xoá `extractNgrokUrl()` (dead code). |
| 6 | — | `CommandRegistry.java`, `PayBotPlaceholderGui.java` (cả 2 bên), `NapTienPlugin.java`, `plugin.yml`, `PayBotPlaceholderCommand.java` | Đổi tên lệnh `/paybotplaceholder` → `/paybotleaderboard` theo yêu cầu (class name giữ nguyên). |

*(Version cột "—" vì đang chờ chốt lại toàn bộ theo đúng version bạn yêu cầu ở cuối phiên — xem mục cuối checklist.)*

## Files KHÔNG thay đổi (đã kiểm tra, không cần đụng)

- `QRMapManager.java`, `TestPaymentGui.java`, `StandaloneCardProcessor.java` (bot_url/reward-config push không đụng logic reward, chỉ đụng nhận config).

## Đang tiếp tục trong phiên này (chưa xong, sẽ update thêm Part)

- [x] bot.py: bỏ ngrok hoàn toàn, migrate aiohttp → FastAPI, HTTPS self-signed trên IP thật VPS, auto port-scan — XONG, verify đầy đủ.
- [x] Cơ chế bot tự push bot-url mới sau restart — XONG (bot.py + `/receive-config` cả 2 bên).
- [x] Plugin + Fabric: `BotHttpClient` → HTTPS + TOFU cert-pinning + fallback tự phục hồi khi bot-url stale — XONG, verify bằng compile thật + test TrustManager thật.
- [x] Fabric: `detectAndSaveNgrokUrl()` → tự dò IP:port thật — XONG.
- [x] **Bonus fix (không có trong plan gốc):** `_extract_real_ip()` (bot.py) tin `X-Forwarded-For` — ĐÚNG khi còn ngrok (ngrok edge set, client không giả mạo được), nhưng SAI khi bot tự host trực tiếp (client tự forge header được, có thể né ban hoặc đổ oan IP khác!). Đổi sang tin `request.client.host` (IP kết nối TCP thật, ASGI scope, không giả mạo được), thêm config `TRUST_PROXY_HEADERS` (mặc định `false`) cho trường hợp admin tự đặt reverse proxy tin cậy sau này.
- [x] Đổi tên `/paybotplaceholder` → `/paybotleaderboard` (cả 2 bên).
- [ ] Auto-reload config mỗi 10s (phát hiện thay đổi từ đĩa) + tự xoá key/comment thừa — cả 3 codebase (**đang làm tiếp, ưu tiên cao theo yêu cầu**)
- [ ] Xoá dòng webhook ngrok trong `showSePayDone()` (Fabric)
- [ ] Đồng bộ QR UI đẹp (mod → plugin), testnapbank/testnapthe dùng đúng GUI thật (Fabric), xoá file rác `CardApiSetupGui.java`
- [ ] Bổ sung hướng dẫn setup SePay API đầy đủ cho Fabric (đã có bên plugin, kiểm tra fabric có chưa)
- [ ] Rà soát `/paybotsetup` xem có lỗi thời so với bản hiện tại không
- [ ] Cơ chế offline-reward file cache (`OfflineRewards.*`, tự load RAM, tự refresh 10s, check khi player join, không log trừ khi bật option)
- [ ] **Cuối list (theo yêu cầu):** cơ chế check trạng thái thẻ 5s/lần (dùng API chargingws/v2 — cần xác nhận hiện có đã đúng vậy chưa, nếu đúng rồi thì khỏi sửa)
- [ ] **Cuối list (theo yêu cầu):** `/disablepaybot` gửi IP về bot lưu `blocked_ip.db`, thêm `/viewblockedips`, `/removeip [ip]`, UI đẹp
- [ ] **Cuối cùng:** chốt lại version thống nhất bot.py/plugin/fabric = 5.0.3 theo đúng yêu cầu (hiện đang lệch 5.0.4/5.0.3/5.0.5 do đánh số theo Part độc lập từng bên)
- [ ] **Sau khi xong tất cả:** viết changelog riêng dạng Markdown cho Modrinth (khác CHANGELOG.md kỹ thuật — ngắn gọn, không đi sâu code, dễ hiểu cho người dùng thường)
- [ ] **Phát hiện cũ chưa xử lý:** Fabric vẫn thiếu `/bot-disconnect` và `/check-online` so với plugin — cần hỏi lại mức độ ưu tiên.

## Part 4 (bot.py + cả 2 bên) — Retry push + API key 2 chiều

- [x] **bot.py:** `synced_bot_url` (cột mới `server_connections`) — track RIÊNG từng server đã nhận bot-url mới nhất hay chưa, không gộp chung.
- [x] **bot.py:** `retry_bot_url_worker()` — nền mỗi 5 phút, CHỈ retry server chưa sync, bỏ cuộc sau 200 lần (~17 ngày) kèm log 1 lần, KHÔNG log mỗi lần thử (đúng yêu cầu "không báo lên log").
- [x] **Điều chỉnh so với đề xuất gốc của user:** KHÔNG thêm field "ip" tự khai báo vào mọi request — lý do: tin giá trị self-declared thay vì lấy từ connection thật đi ngược lại chính fix bảo mật X-Forwarded-For vừa làm (Part 15). Server_id + API key đã đủ để xác thực; IP thật vẫn lấy qua `request.client.host` hoặc `/api/report-server-ip` như cũ.
- [x] **Bảo mật (mới, cả 3 codebase):** X-API-Key giờ bắt buộc CẢ 2 CHIỀU — trước đây chỉ chiều plugin/mod→bot check key, chiều bot→plugin/mod chỉ check server_id khớp (dễ giả mạo nếu biết/đoán được server_id). `API_KEY` promote thành module-level constant trong bot.py (trước kẹt trong `build_http_app()`), thêm header vào toàn bộ hàm push (`push_config_to_plugin`, `execute_reward` push, `bot-disconnect` x2, `push_bot_url`). `PluginHttpServer.serve()` (cả 2 bên) thêm check tập trung trước khi vào switch/case, trừ `/api/sepay-ipn`.

## Part 5 — Auto-reload config 10s + dọn dẹp lặt vặt

- [x] **Plugin:** `SmartConfigMerger.sync()` — thêm khả năng XOÁ block key thừa (không còn trong template hiện tại), giữ nguyên logic ADD cũ. Compile thật OK (stub).
- [x] **Fabric:** `PayBotConfig.sync()` — tương đương, viết thêm `parseIntoBlocks()`/`keyOf()` helper (fabric trước đây không có class Block riêng như plugin). Compile thật OK (stub).
- [x] Wiring watcher 10s: Plugin dùng `BukkitTask` (`runTaskTimerAsynchronously`, 200 tick), Fabric dùng `scheduler.scheduleAtFixedRate` (10s) — cả 2 chỉ log khi THẬT SỰ có thay đổi (added>0 hoặc removed>0), không spam log mỗi lần check.
- [x] Toggle `auto-config-sync: true` (mặc định bật) — thêm vào cả `config.yml` (plugin) và `config-template.yml` (fabric).
- [x] Thêm `bot-cert-fingerprint` + `bot-cert-strict-pin` vào cả 2 template (khớp cơ chế TOFU đã làm ở Part 4).
- [x] Fix default `bot-url` ngrok cũ còn sót trong `config.yml` template (plugin) — fabric đã đúng sẵn (trống).
- [x] Xoá dòng hiển thị webhook (tuỳ chọn) trong `showSePayDone()` (Fabric) — theo đúng yêu cầu, không còn lý do cần webhook.
- [x] Xoá file rác `CardApiSetupGui.java` (Fabric) — xác nhận dead code (chỉ còn trong comment lịch sử) trước khi xoá.
- [ ] **QR UI parity — CHƯA RÕ, cần hỏi lại:** So sánh code cho thấy `QRMapManager.java` bên PLUGIN (414 dòng, có `drawInfoLines`/`drawFitted` riêng) thực ra ĐẦY ĐỦ HƠN bản Fabric (371 dòng) — ngược với giả định ban đầu (tưởng mod đẹp hơn cần port sang plugin). Không tìm thấy chuỗi "Ổ Nạp"/"Locked" khớp y hệt ảnh 2 trong code hiện tại ở cả 2 bên — có thể ảnh chụp từ bản cũ hơn, hoặc chữ đã đổi. Cần bạn xác nhận lại cụ thể muốn đồng bộ chỗ nào.

## Part 6 — testnapbank/testnapthe dùng đúng GUI thật + SePay tutorial 4 bước

- [x] **testnapbank/testnapthe:** Trước đây `TestPaymentGui.openBankTest/openCardTest` tự vẽ GUI RIÊNG (khác hẳn plugin — plugin mở ĐÚNG `NapBankGui`/`NapTheGui` thật). Đã sửa: thêm `testMode` vào `GuiSession`, `NapBankGui`/`NapTheGui` tự check cờ này ở bước chọn mệnh giá → gọi `fakeApproveBank/Card` (giờ public) thay vì tạo đơn thật. `CommandRegistry` set cờ rồi gọi thẳng GUI thật. Xoá 3 hàm vẽ GUI riêng đã lỗi thời (94 dòng). Verify: brace balance tất cả file liên quan.
- [x] **SePay tutorial 4 bước:** Fabric trước đây chỉ có 1 dòng gợi ý link, thiếu hẳn hướng dẫn từng bước như plugin. Đã port nguyên `TUTORIAL_PAGES` (4 trang, đúng y hệt nội dung/text plugin) + state machine `SEPAY_ASK_TUTORIAL`/`SEPAY_TUTORIAL` vào `SetupManager.java`, `/sepaysetup` giờ hỏi "yes/skip" y hệt plugin. **Verify: compile thật với stub đầy đủ — 0 lỗi.**

## Part 7 — /paybotsetup rà soát (phát hiện bug thật)

- [x] **BUG THẬT (nghiêm trọng, UX):** `CommandManager.literal("PayBotSetup")` — DUY NHẤT lệnh có chữ hoa trong toàn bộ file (đã quét lại toàn bộ danh sách lệnh xác nhận). Brigadier match literal case-sensitive → gõ `/paybotsetup` (tự nhiên, giống mọi lệnh khác) báo "Unknown command", phải gõ đúng hoa thường `/PayBotSetup` mới chạy. Đổi về chữ thường.
- [x] Fix default `plugin-port` sai: `25565` (TRÙNG port Minecraft mặc định) → `25580` đúng theo config-template.yml.
- [x] Thêm status bot-url/cert-fingerprint/auto-config-sync (trước đây thiếu, dù đây là phần quan trọng của Bot-connected mode giờ đã HTTPS+TOFU).
- [x] Đồng bộ cùng nội dung tương tự vào plugin's `/paybotsetup` (thêm status bot-url/cert/auto-sync, đổi nhãn "Webhook port" → "HTTP port (nhận lệnh từ bot)" cho đúng vai trò thật hiện tại).

## Part 8 — Offline-reward cache (đã có sẵn phần lớn — chỉ cần tăng tốc độ)

- [x] **Phát hiện:** `OfflineRewardManager.java` (file .yml + RAM cache + hasPendingRewards() nhanh không qua mạng) **ĐÃ TỒN TẠI SẴN ở cả 2 bên**, khá đầy đủ — không cần viết lại từ đầu.
- [x] Đổi interval fallback-poll: Fabric 10 phút → 10s. Plugin: tách riêng remote-poll ra 10s (trước đây gộp chung, giãn 5 phút với lý do "qua ngrok" đã lỗi thời).
- [x] Xác nhận: cả 2 bên ĐÃ tự confirm reward về bot sau khi dispatch (tránh trùng lặp dù tăng tần suất poll) + KHÔNG log khi không có gì (chỉ log khi rewards.isEmpty()==false).
- [x] Dọn field `autoRewardPollTick` không còn dùng.

## Part 9 (bot.py) — Card-check verify theo docs

- [x] Xác nhận công thức sign "check" (`md5(partner_key+request_id)`, hàm `md5_sign_check()`) ĐÃ ĐÚNG, dùng nhất quán ở cả `check_card_status()` (bot-connected) và `standalone_card_poll_worker()` — không phải bug, dù khác format với 1 doc tham khảo khác (có thể là provider khác trong họ API tương tự).
- [x] **Phát hiện lệch thật:** `standalone_card_poll_worker()` poll mỗi **60 GIÂY** (+ cutoff 30s riêng khiến thực tế ~60s/đơn) — không phải "check liên tục 5s" như yêu cầu. Đã sửa: loop 60s→5s, cutoff 30s→5s.
- [x] `check_pending_cards()` (bot-connected) đã sẵn 10s — đổi thêm xuống 5s cho nhất quán 2 chế độ.

## Part 10 — IP-blocking feature (blocked_ip.db + /viewblockedips + /removeip)

- [x] **bot.py:** file SQLite riêng `blocked_ip.db` (tách biệt `naptien.db` theo đúng yêu cầu). Endpoint `/api/report-blocked-ip` — dùng `_extract_real_ip()` (IP kết nối TCP thật, KHÔNG tin field tự khai báo — nhất quán fix bảo mật Part 15).
- [x] `/viewblockedips` (embed đẹp, tối đa 25 gần nhất) + `/removeip <ip>` — cả 2 owner-only (BOT_OWNER_ID).
- [x] Plugin: `BotHttpClient.reportBlockedIp()` + hook vào `DisablePaybotCommand` sau khi `BanGuard.banCurrentServer()` thành công (fire-and-forget async).
- [x] Fabric: tương tự với `BanManager` + hook vào `/disablepaybot`.
