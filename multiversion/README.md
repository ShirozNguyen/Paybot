# PayBot Multi-Version — Khung Stonecutter (GIAI ĐOẠN 1)

## Đây là gì

Đây là project Gradle **hoàn toàn độc lập** với `PayBot/` chính (module `fabric/`, `forge/`, `neoforge/`, `plugin/` không hề bị đụng tới). Mục đích: dựng nền tảng để cuối cùng support thật `1.14.x → 26.2` theo đúng yêu cầu, bằng công cụ đúng đắn (Stonecutter) thay vì đoán reflection như trước.

**Đây CHƯA phải PayBot đầy đủ.** Chỉ có 1 file Java "hello world" (`PayBotMultiVersionMod.java`) để kiểm tra khung có ráp đúng không. Toàn bộ ~60+ file logic thật (managers, GUI, commands, adapter item/lore...) **chưa được di chuyển sang**.

## Mức độ tin cậy — cần đọc trước khi build

🔴 **Đây là phần rủi ro cao nhất trong toàn bộ những gì tôi đã làm hôm nay.** Lý do cụ thể:

1. Trang tài liệu chính thức Stonecutter (`stonecutter.kikugie.dev`) **chặn crawler AI** — khi tôi fetch trực tiếp, nó trả về nội dung rác cố ý (kịch bản phim Bee Movie) thay vì tài liệu thật. Tôi chỉ lấy được thông tin qua đoạn trích đã được index bởi search engine (không đầy đủ 100%).
2. Cú pháp `settings.gradle` (phần `stonecutter.create getRootProject() { versions "..." }`) — có độ tin cậy khá cao, vì thấy được cả bản Kotlin lẫn Groovy trong đoạn trích tài liệu chính thức.
3. Cú pháp `build.gradle` của từng node — độ tin cậy **thấp hơn**, vì tôi không tìm được ví dụ Groovy đầy đủ nào cho phần này, phải tự suy luận từ cấu trúc project template (Kotlin) + cách `fabric/build.gradle` của project chính đã hoạt động.
4. Số phiên bản plugin `dev.kikugie.stonecutter` (`0.8.+`) — chỉ thấy trong 1 ví dụ, có thể đã lỗi thời.

**Nói thẳng: khả năng cao lần build đầu tiên sẽ báo lỗi Gradle.** Đó là chuyện bình thường với phần chưa build-test được — không phải dấu hiệu tôi làm ẩu, mà là giới hạn thật của việc không có mạng tới Maven trong sandbox.

## Cách xác nhận (bắt buộc trước khi làm gì tiếp)

```bash
cd multiversion
./gradlew :1.20.1-fabric:build
```

- **Nếu build thành công** và log khởi động mod hiện đúng dòng "Khung Stonecutter GIAI ĐOẠN 1 khởi động thành công" khi thả jar vào server Fabric 1.20.1 → nền đã đúng, báo tôi để làm Giai đoạn 2 (di chuyển code thật).
- **Nếu lỗi** → copy nguyên văn lỗi Gradle đưa tôi. Đừng tự sửa mò — lỗi Gradle thường chỉ đúng 1 dòng bị sai (sai version plugin, sai tên repository...), sửa mò dễ che mất lỗi thật.

## Cấu trúc

```
multiversion/
├── settings.gradle              ← khai báo node (hiện chỉ có "1.20.1-fabric")
├── stonecutter.gradle           ← controller, đánh dấu node đang active
├── gradle.properties            ← property dùng chung mọi node
├── src/main/java/...            ← code DÙNG CHUNG mọi node (hiện chỉ có file demo)
└── versions/
    └── 1.20.1-fabric/
        ├── build.gradle          ← toolchain Loom riêng cho node này
        ├── gradle.properties     ← property riêng (minecraft_version = 1.20.1)
        └── src/main/resources/
            └── fabric.mod.json
```

## Kế hoạch Giai đoạn 2 (chưa làm, đợi xác nhận Giai đoạn 1 trước)

1. Thêm node mới = copy `versions/1.20.1-fabric/` → đổi tên thư mục + đổi 3 dòng version trong `gradle.properties` của node đó + thêm dòng vào `versions "..."` trong `settings.gradle`.
2. Di chuyển code thật (managers, GUI, commands) từ `fabric/src/` sang `multiversion/src/` — làm **từng file một, có kiểm tra**, không copy hàng loạt.
3. Với phần thật sự khác nhau giữa các bản MC (đặc biệt adapter item/lore) — dùng comment `//? version { ... //?} else { ... //?}` để viết code THẲNG cho từng bản, không cần reflection đoán mò nữa (đây là lợi ích chính của Stonecutter so với cách cũ).
4. Ưu tiên theo đúng thứ tự Shiroz chọn: mốc ổn định trước (1.16.5, 1.18.2, 1.19.4, 1.20.1, 1.21.1), rồi mới tới 26.1/26.2 (cần nghiên cứu API mới riêng, Java 25).
