package com.paybot.milestone;

import com.paybot.PayBotMod;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Đọc và quản lý file cấu hình riêng milestones.yml trên Mod Loader (SnakeYAML).
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm quản lý cấu hình mốc nạp.
 */
public final class MilestoneConfig {

    private final File configFile;

    private boolean enabled = true;
    private boolean scheduledResetEnabled = false;
    private MilestoneResetUnit resetUnit = MilestoneResetUnit.MONTHLY;
    private int resetInterval = 1;
    private boolean broadcastOnReset = true;
    private String resetBroadcastMessage = "§6§l[PAYBOT] §aMùa nạp mới đã bắt đầu! Mốc nạp server đã được làm mới, hãy nhanh tay nạp nhận quà!";

    private final Map<Long, MilestoneModel> singleMilestones = new TreeMap<>();
    private final Map<Long, MilestoneModel> globalMilestones = new TreeMap<>();

    public MilestoneConfig(File dataFolder) {
        this.configFile = new File(dataFolder, "milestones.yml");
        load();
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() {
        if (!configFile.exists()) {
            createDefaultConfigFile();
        }

        singleMilestones.clear();
        globalMilestones.clear();

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream in = new FileInputStream(configFile)) {
                root = yaml.load(in);
            }

            if (root == null) {
                root = Collections.emptyMap();
            }

            Object enabledObj = root.get("enabled");
            this.enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));

            Object resetObj = root.get("scheduled-reset");
            if (resetObj instanceof Map<?, ?> resetMap) {
                this.scheduledResetEnabled = Boolean.parseBoolean(String.valueOf(resetMap.get("enabled")));
                this.resetUnit = MilestoneResetUnit.fromString(String.valueOf(resetMap.get("unit")));
                try {
                    this.resetInterval = Math.max(1, Integer.parseInt(String.valueOf(resetMap.get("interval"))));
                } catch (Exception ignored) {
                    this.resetInterval = 1;
                }
                this.broadcastOnReset = Boolean.parseBoolean(String.valueOf(resetMap.getOrDefault("broadcast-on-reset", "true")));
                this.resetBroadcastMessage = String.valueOf(resetMap.getOrDefault("reset-broadcast-message",
                        "§6§l[PAYBOT] §aMùa nạp mới đã bắt đầu! Mốc nạp server đã được làm mới!"));
            }

            Object singleObj = root.get("single-milestones");
            if (singleObj instanceof Map<?, ?> singleMap) {
                for (Map.Entry<?, ?> entry : singleMap.entrySet()) {
                    try {
                        long amount = Long.parseLong(String.valueOf(entry.getKey()).trim());
                        if (entry.getValue() instanceof Map<?, ?> valMap) {
                            Object cmdsObj = valMap.get("cmds");
                            List<String> cmds = new ArrayList<>();
                            if (cmdsObj instanceof List<?> list) {
                                for (Object c : list) cmds.add(String.valueOf(c));
                            }
                            if (!cmds.isEmpty()) {
                                singleMilestones.put(amount, new MilestoneModel(amount, cmds));
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            Object globalObj = root.get("global-milestones");
            if (globalObj instanceof Map<?, ?> globalMap) {
                for (Map.Entry<?, ?> entry : globalMap.entrySet()) {
                    try {
                        long amount = Long.parseLong(String.valueOf(entry.getKey()).trim());
                        if (entry.getValue() instanceof Map<?, ?> valMap) {
                            Object cmdsObj = valMap.get("cmds");
                            List<String> cmds = new ArrayList<>();
                            if (cmdsObj instanceof List<?> list) {
                                for (Object c : list) cmds.add(String.valueOf(c));
                            }
                            if (!cmds.isEmpty()) {
                                globalMilestones.put(amount, new MilestoneModel(amount, cmds));
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            PayBotMod.LOGGER.info("[PayBot] Đã nạp milestones.yml: {} mốc cá nhân, {} mốc toàn server. (Scheduled-reset: {})",
                    singleMilestones.size(), globalMilestones.size(), (scheduledResetEnabled ? resetInterval + " " + resetUnit : "Tắt"));

        } catch (Throwable t) {
            PayBotMod.LOGGER.warn("[PayBot] Lỗi đọc file milestones.yml: {}", t.getMessage());
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
  reset-broadcast-message: "§6§l[PAYBOT] §aMùa nạp mới đã bắt đầu! Toàn bộ mốc nạp server đã được làm mới, hãy nhanh tay nạp để nhận quà nhé!"

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
      - "say §a[PayBot] Người chơi §e[playername] §ađã đạt mốc nạp cá nhân §b100.000 VNĐ!"
  "500000":
    cmds:
      - "give [playername] netherite_ingot 2"
      - "say §a[PayBot] Người chơi §e[playername] §ađã đạt mốc nạp cá nhân §b500.000 VNĐ!"

# ==============================================================================
# 2. MỐC NẠP TOÀN SERVER (GLOBAL MILESTONES)
# Khi tổng số tiền toàn bộ server nạp chạm mốc, tự động trao thưởng cho TẤT CẢ
# người chơi trong server. Người chơi Offline sẽ tự nhận khi đăng nhập lại!
# ==============================================================================
global-milestones:
  "5000000":
    cmds:
      - "say §6§l[PAYBOT] TOÀN SERVER ĐÃ ĐẠT MỐC NẠP 5.000.000 VNĐ!"
      - "give [playername] emerald 10"
""";
            Files.writeString(configFile.toPath(), content, StandardCharsets.UTF_8);
            PayBotMod.LOGGER.info("[PayBot] Đã tạo file cấu hình mẫu: milestones.yml");
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] Không thể tạo file milestones.yml: {}", e.getMessage());
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
