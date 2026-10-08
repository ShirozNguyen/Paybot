package com.paybot.milestone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * Quản lý đọc, xác thực và đồng bộ file cấu hình riêng biệt milestones.yml.
 * Hỗ trợ tạo file mặc định kèm chú thích tiếng Việt chuẩn xác và thao tác reload tức thì.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm quản lý cấu hình mốc nạp.
 */
public final class MilestoneConfig {

    private final File configFile;
    private final Logger logger;

    private boolean enabled = true;
    private boolean scheduledResetEnabled = false;
    private MilestoneResetUnit resetUnit = MilestoneResetUnit.MONTHLY;
    private int resetInterval = 1;
    private boolean broadcastOnReset = true;
    private String resetBroadcastMessage = "&6&l[PAYBOT] &aMùa nạp mới đã bắt đầu! Mốc nạp server đã được làm mới, hãy nhanh tay nạp nhận quà!";

    private final Map<Long, MilestoneModel> singleMilestones = new TreeMap<>();
    private final Map<Long, MilestoneModel> globalMilestones = new TreeMap<>();

    public MilestoneConfig(File dataFolder, Logger logger) {
        this.configFile = new File(dataFolder, "milestones.yml");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        if (!configFile.exists()) {
            createDefaultConfigFile();
        }

        singleMilestones.clear();
        globalMilestones.clear();

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

            this.enabled = yaml.getBoolean("enabled", true);

            ConfigurationSection resetSec = yaml.getConfigurationSection("scheduled-reset");
            if (resetSec != null) {
                this.scheduledResetEnabled = resetSec.getBoolean("enabled", false);
                this.resetUnit = MilestoneResetUnit.fromString(resetSec.getString("unit", "monthly"));
                this.resetInterval = Math.max(1, resetSec.getInt("interval", 1));
                this.broadcastOnReset = resetSec.getBoolean("broadcast-on-reset", true);
                this.resetBroadcastMessage = resetSec.getString("reset-broadcast-message",
                        "&6&l[PAYBOT] &aMùa nạp mới đã bắt đầu! Mốc nạp server đã được làm mới!");
            }

            // Đọc single-milestones
            ConfigurationSection singleSec = yaml.getConfigurationSection("single-milestones");
            if (singleSec != null) {
                for (String key : singleSec.getKeys(false)) {
                    try {
                        long amount = Long.parseLong(key.trim());
                        List<String> cmds = singleSec.getStringList(key + ".cmds");
                        if (!cmds.isEmpty()) {
                            singleMilestones.put(amount, new MilestoneModel(amount, cmds));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Đọc global-milestones
            ConfigurationSection globalSec = yaml.getConfigurationSection("global-milestones");
            if (globalSec != null) {
                for (String key : globalSec.getKeys(false)) {
                    try {
                        long amount = Long.parseLong(key.trim());
                        List<String> cmds = globalSec.getStringList(key + ".cmds");
                        if (!cmds.isEmpty()) {
                            globalMilestones.put(amount, new MilestoneModel(amount, cmds));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            logger.info("[PayBot] Đã nạp milestones.yml: " + singleMilestones.size() + " mốc cá nhân, "
                    + globalMilestones.size() + " mốc toàn server. (Scheduled-reset: " + (scheduledResetEnabled ? resetInterval + " " + resetUnit : "Tắt") + ")");

        } catch (Throwable t) {
            logger.warning("[PayBot] Lỗi đọc file milestones.yml: " + t.getMessage());
        }
    }

    private void createDefaultConfigFile() {
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            String content = """
# ==============================================================================
#                      PAYBOT - HỆ THỐNG MỐC NẠP THƯỞNG
#                 (Tích hợp sẵn Mốc Nạp Cá Nhân & Toàn Server)
# ==============================================================================
# Hỗ trợ tự động đồng bộ số tiền nạp, trao thưởng khi đạt mốc,
# lưu trữ phần thưởng cho người chơi Offline và tự động Reset theo chu kỳ.
# ------------------------------------------------------------------------------

# Bật/Tắt toàn bộ hệ thống mốc nạp (true = bật, false = tắt)
enabled: true

# ==============================================================================
# ⏰ HỆ THỐNG TỰ ĐỘNG RESET MỐC NẠP THEO THỜI GIAN (SCHEDULED RESET)
# ==============================================================================
# Cho phép server tự động làm mới (reset) lại các mốc nạp đã đạt được theo chu kỳ.
# 
# 📌 LƯU Ý QUAN TRỌNG DÀNH CHO QUẢN TRỊ VIÊN:
#   - Khi mốc nạp được reset, toàn bộ tiến trình nạp tính mốc và trạng thái các mốc
#     đã nhận sẽ được đưa về 0, giúp người chơi có thể tiếp tục nạp và nhận lại
#     các phần thưởng mốc từ đầu!
#   - Mặc định áp dụng tự động reset cho TOÀN SERVER (cả mốc cá nhân và mốc chung).
#   - Dữ liệu tiền nạp gốc trong lịch sử giao dịch (bank_orders / card_orders) vẫn
#     được bảo toàn nguyên vẹn trong CSDL để đối soát doanh thu, chỉ có tiến trình
#     mốc thưởng là được làm mới theo chu kỳ mới.
# ------------------------------------------------------------------------------
scheduled-reset:
  # 1. Bật/Tắt tính năng tự động reset mốc nạp theo chu kỳ (true = bật, false = tắt)
  enabled: false

  # 2. Đơn vị chu kỳ thời gian để tự động reset.
  # Các giá trị hợp lệ:
  #   - "minute"  : Phút   (Ví dụ: reset mỗi X phút)
  #   - "hour"    : Giờ    (Ví dụ: reset mỗi X giờ)
  #   - "daily"   : Ngày   (Ví dụ: reset mỗi X ngày, vào lúc 00:00 đầu ngày)
  #   - "monthly" : Tháng  (Ví dụ: reset mỗi X tháng, vào ngày 1 đầu tháng)
  #   - "yearly"  : Năm    (Ví dụ: reset mỗi X năm, vào ngày 1 tháng 1)
  unit: "monthly"

  # 3. Giá trị khoảng thời gian (Số nguyên: 1, 2, 3, ...) kết hợp với đơn vị bên trên:
  # Ví dụ:
  #   unit: "monthly" và interval: 1  -> Reset định kỳ vào ngày 1 mỗi tháng (Theo tháng).
  #   unit: "daily"   và interval: 7  -> Reset định kỳ mỗi 7 ngày một lần (Theo tuần).
  #   unit: "daily"   và interval: 1  -> Reset định kỳ mỗi ngày một lần (Lúc 00:00).
  #   unit: "hour"    và interval: 12 -> Reset định kỳ mỗi 12 giờ một lần.
  interval: 1

  # Gửi thông báo broadcast toàn server khi quá trình reset diễn ra thành công
  broadcast-on-reset: true
  reset-broadcast-message: "&6&l[PAYBOT] &aMùa nạp mới đã bắt đầu! Toàn bộ mốc nạp server đã được làm mới, hãy nhanh tay nạp để nhận quà nhé!"

# ==============================================================================
# 🎁 CÁC BIẾN THAY THẾ (PLACEHOLDERS) TRONG CÂU LỆNH TRAO THƯỞNG:
#   [playername] hoặc %player% -> Tên người chơi nhận thưởng
#   [amount]     hoặc %amount% -> Số tiền nạp hoặc giá trị mốc nạp
#   [player_total]             -> Tổng tiền nạp tích lũy của người chơi trong chu kỳ này
#   [server_total]             -> Tổng tiền nạp của toàn server trong chu kỳ này
#   [milestone]                -> Mức tiền của mốc nạp đạt được
# ==============================================================================

# ==============================================================================
# 1. MỐC NẠP CÁ NHÂN (SINGLE MILESTONES)
# Khi từng người chơi nạp tích lũy đạt mốc, tự động thực thi danh sách lệnh dưới.
# ==============================================================================
single-milestones:
  "100000":
    cmds:
      - "give [playername] diamond 5"
      - "broadcast &a[PayBot] Người chơi &e[playername] &ađã đạt mốc nạp cá nhân &b100.000 VNĐ!"
  "500000":
    cmds:
      - "give [playername] netherite_ingot 2"
      - "broadcast &a[PayBot] Người chơi &e[playername] &ađã đạt mốc nạp cá nhân &b500.000 VNĐ!"

# ==============================================================================
# 2. MỐC NẠP TOÀN SERVER (GLOBAL MILESTONES)
# Khi tổng số tiền toàn bộ server nạp chạm mốc, tự động trao thưởng cho TẤT CẢ
# người chơi trong server. Người chơi Offline sẽ tự nhận khi đăng nhập lại!
# ==============================================================================
global-milestones:
  "5000000":
    cmds:
      - "broadcast &6&l[PAYBOT] TOÀN SERVER ĐÃ ĐẠT MỐC NẠP 5.000.000 VNĐ!"
      - "give [playername] emerald 10"
      - "msg [playername] &aBạn nhận được quà mốc nạp toàn server 5 Triệu VNĐ!"
""";
            Files.writeString(configFile.toPath(), content, StandardCharsets.UTF_8);
            logger.info("[PayBot] Đã tạo file cấu hình mẫu: milestones.yml");
        } catch (IOException e) {
            logger.warning("[PayBot] Không thể tạo file milestones.yml: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
    public boolean isScheduledResetEnabled() { return scheduledResetEnabled; }
    public MilestoneResetUnit getResetUnit() { return resetUnit; }
    public int getResetInterval() { return resetInterval; }
    public boolean isBroadcastOnReset() { return broadcastOnReset; }
    public String getResetBroadcastMessage() { return resetBroadcastMessage; }
    public Map<Long, MilestoneModel> getSingleMilestones() { return singleMilestones; }
    public Map<Long, MilestoneModel> getGlobalMilestones() { return globalMilestones; }
}
