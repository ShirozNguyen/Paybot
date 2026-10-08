package com.paybot.milestone;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Động cơ thẩm định và kích hoạt trao thưởng mốc nạp (Single & Global).
 * Hỗ trợ tự động nhận đầy đủ các mốc khi người chơi nạp nhảy vọt nhiều mốc cùng lúc.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm thẩm định mốc nạp.
 */
public final class MilestoneEvaluationEngine {

    private final MilestoneConfig config;
    private final MilestoneDatabaseService dbService;
    private final Logger logger;

    public MilestoneEvaluationEngine(MilestoneConfig config, MilestoneDatabaseService dbService, Logger logger) {
        this.config = config;
        this.dbService = dbService;
        this.logger = logger;
    }

    /**
     * Thẩm định mốc nạp cá nhân cho người chơi chỉ định.
     */
    public void evaluatePlayerMilestones(String playerName, long playerTotal, long serverTotal, Consumer<String> commandExecutor) {
        if (!config.isEnabled() || playerName == null || playerName.isBlank()) {
            return;
        }

        int cycleId = dbService.getCurrentCycleId();
        Set<Long> claimed = dbService.getClaimedSingleMilestones(playerName, cycleId);
        Map<Long, MilestoneModel> singleMilestones = config.getSingleMilestones();

        for (Map.Entry<Long, MilestoneModel> entry : singleMilestones.entrySet()) {
            long target = entry.getKey();
            MilestoneModel model = entry.getValue();

            if (playerTotal >= target && !claimed.contains(target)) {
                // Đạt mốc cá nhân mới -> thực thi các lệnh thưởng
                for (String rawCmd : model.getCommands()) {
                    String parsed = MilestonePlaceholderParser.parse(rawCmd, playerName, target, playerTotal, serverTotal);
                    commandExecutor.accept(parsed);
                }
                claimed.add(target);
                dbService.recordClaimedSingleMilestone(playerName, target, cycleId);
                logger.info("[PayBot] Người chơi " + playerName + " đã đạt mốc nạp cá nhân " + target + " VNĐ!");
            }
        }
    }

    /**
     * Thẩm định mốc nạp toàn server.
     */
    public void evaluateGlobalMilestones(long serverTotal,
                                         Collection<String> onlinePlayers,
                                         Collection<String> allPlayers,
                                         Consumer<String> commandExecutor,
                                         BiConsumer<String, String> offlineRewardQueue) {
        if (!config.isEnabled()) {
            return;
        }

        int cycleId = dbService.getCurrentCycleId();
        Set<Long> reached = dbService.getReachedGlobalMilestones(cycleId);
        Map<Long, MilestoneModel> globalMilestones = config.getGlobalMilestones();

        Set<String> onlineSet = new HashSet<>();
        for (String p : onlinePlayers) {
            if (p != null && !p.isBlank()) onlineSet.add(p.toLowerCase(Locale.ROOT));
        }

        for (Map.Entry<Long, MilestoneModel> entry : globalMilestones.entrySet()) {
            long target = entry.getKey();
            MilestoneModel model = entry.getValue();

            if (serverTotal >= target && !reached.contains(target)) {
                boolean isNew = dbService.recordReachedGlobalMilestone(target, cycleId);
                reached.add(target);

                if (!isNew) {
                    continue; // Server khác trong cụm đã ghi nhận mốc này trước
                }

                logger.info("[PayBot] TOÀN SERVER ĐÃ ĐẠT MỐC NẠP TOÀN CẦU " + target + " VNĐ!");

                // Trao thưởng cho toàn bộ người chơi từng tham gia server
                Set<String> processed = new HashSet<>();
                for (String player : allPlayers) {
                    if (player == null || player.isBlank()) continue;
                    String lower = player.toLowerCase(Locale.ROOT);
                    if (!processed.add(lower)) continue;

                    boolean isOnline = onlineSet.contains(lower);

                    for (String rawCmd : model.getCommands()) {
                        String parsed = MilestonePlaceholderParser.parse(rawCmd, player, target, 0, serverTotal);
                        if (isOnline) {
                            commandExecutor.accept(parsed);
                        } else {
                            offlineRewardQueue.accept(player, parsed);
                        }
                    }
                }
            }
        }
    }
}
