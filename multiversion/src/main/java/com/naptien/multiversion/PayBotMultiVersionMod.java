package com.naptien.multiversion;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PayBotMultiVersionMod — v5.5.5 [KHUNG STONECUTTER — GIAI ĐOẠN 1, KHÔNG PHẢI BẢN ĐẦY ĐỦ]
 *
 * File này KHÔNG chứa logic PayBot thật (managers, GUI, commands...) — đó là công việc của
 * Giai đoạn 2 (di chuyển ~60+ file từ fabric/src/ sang multiversion/src/ một cách có kiểm
 * soát, xác nhận từng phần). File này CHỈ có 1 mục đích: chứng minh 4 mảnh ghép (Stonecutter +
 * Architectury Loom + Fabric Loader + Gradle) khớp nhau và build ra được 1 file .jar chạy
 * được — nền tảng bắt buộc phải đúng trước khi đổ công sức di chuyển toàn bộ code thật vào.
 *
 * Cách xác nhận: build xong, thả vào server Fabric 1.20.1, khởi động, log server phải hiện
 * đúng dòng bên dưới. Nếu không thấy log này (hoặc server crash lúc load mod) — nghĩa là
 * phần khung (settings.gradle/stonecutter.gradle/build.gradle) còn sai ở đâu đó, cần sửa
 * TRƯỚC khi đụng tới việc di chuyển code thật, vì mọi lỗi từ giờ trở đi coi như build trên
 * nền chưa vững.
 */
public class PayBotMultiVersionMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("PayBot-MultiVersion-Pilot");

    @Override
    public void onInitialize() {
        // [SC] if 1.20.1-fabric — ví dụ cú pháp tiền xử lý Stonecutter theo đúng comment
        // block đã xác nhận qua tài liệu chính thức (dùng để chèn code khác nhau theo từng
        // bản MC một khi có nhiều node). Ở Giai đoạn 1 chỉ có 1 node nên nhánh else không
        // có tác dụng thực tế, nhưng để sẵn đây làm ví dụ mẫu cho Giai đoạn 2 tham khảo.
        //? 1.20.1-fabric {
        LOGGER.info("[PayBot-Pilot] Khung Stonecutter GIAI ĐOẠN 1 khởi động thành công trên node 1.20.1-fabric.");
        LOGGER.info("[PayBot-Pilot] Đây CHƯA PHẢI PayBot đầy đủ — logic thật chưa được di chuyển sang.");
        //?} else {
        /*LOGGER.info("[PayBot-Pilot] Node khác 1.20.1-fabric chưa được cấu hình ở Giai đoạn 1.");*/
        //?}
    }
}
