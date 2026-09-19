package com.paybot.managers;

import com.paybot.PayBotPlugin;
import com.paybot.utils.SchedulerUtils;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PaymentRecoveryWorker — v5.5.8 Part 122 (Tuân thủ Rule 17)
 * <p>
 * Lớp chuyên trách quét và phục hồi các giao dịch bị gián đoạn do server crash/restart đột ngột.
 * Chạy khi server khởi động (Startup Recovery) và định kỳ mỗi 60s.
 */
public class PaymentRecoveryWorker {

    private static final long TIMEOUT_THRESHOLD_MS = 60_000L; // 60 giây timeout cho processing

    private final PayBotPlugin plugin;
    private ScheduledExecutorService recoveryScheduler;

    public PaymentRecoveryWorker(PayBotPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (recoveryScheduler != null && !recoveryScheduler.isShutdown()) return;
        recoveryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PayBot-PaymentRecoveryWorker");
            t.setDaemon(true);
            return t;
        });
        recoveryScheduler.scheduleWithFixedDelay(() -> {
            try {
                runRecovery();
            } catch (Throwable t) {
                // Ignore background errors
            }
        }, 5, 60, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (recoveryScheduler != null) {
            recoveryScheduler.shutdownNow();
            recoveryScheduler = null;
        }
    }

    /**
     * Quét và phục hồi toàn diện các đơn hàng kẹt.
     */
    public void runRecovery() {
        recoverOfflineRewards();
        recoverBankOrders();
        recoverCardOrders();
    }

    private void recoverOfflineRewards() {
        try {
            if (plugin.getDatabaseManager() != null) {
                int reverted = plugin.getDatabaseManager().revertProcessingOfflineRewards();
                if (reverted > 0) {
                    plugin.getLogger().info("[PayBot-Recovery] Đã phục hồi " + reverted + " phần thưởng offline kẹt về PENDING.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PayBot-Recovery] Lỗi khi phục hồi offline rewards: " + e.getMessage());
        }
    }

    private void recoverBankOrders() {
        try {
            List<LocalOrderManager.BankOrder> orders = plugin.getLocalOrderManager().getAllBankOrders();
            long now = System.currentTimeMillis();

            for (LocalOrderManager.BankOrder o : orders) {
                if (LocalOrderManager.BANK_REWARD_PROCESSING.equals(o.status)
                        || LocalOrderManager.BANK_REWARD_PENDING.equals(o.status)
                        || LocalOrderManager.BANK_CONFIRMED.equals(o.status)) {

                    if (now - o.createdAt > TIMEOUT_THRESHOLD_MS) {
                        List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, o.amount, "bank");
                        if (rewardCmds.isEmpty()) continue;

                        String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, o.amount, "bank");
                        String rewardHash = RewardDeliveryLedger.computeRewardHash(rewardCmds, rewardAmt);

                        if (plugin.getRewardDeliveryLedger() != null && plugin.getRewardDeliveryLedger().hasDelivered("bank", o.invoiceId, rewardHash)) {
                            plugin.getLocalOrderManager().updateBankStatus(o.invoiceId, LocalOrderManager.BANK_APPROVED);
                            continue;
                        }

                        plugin.getLogger().info("[PayBot-Recovery] Tự động giao bù đơn bank kẹt: #" + o.invoiceId + " cho " + o.playerName);
                        SchedulerUtils.runSync(plugin, () -> {
                            boolean wasOnline = RewardDispatcher.dispatchOrQueue(
                                    plugin, o.invoiceId, o.playerName, rewardCmds, rewardAmt,
                                    String.valueOf(o.amount), "bank", o.invoiceId, "");
                            plugin.getLocalOrderManager().updateBankStatus(o.invoiceId, LocalOrderManager.BANK_APPROVED);
                            if (plugin.getRewardDeliveryLedger() != null) {
                                plugin.getRewardDeliveryLedger().recordDelivery("bank", o.invoiceId, o.playerName, rewardHash, "DONE");
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PayBot-Recovery] Lỗi khi phục hồi bank orders: " + e.getMessage());
        }
    }

    private void recoverCardOrders() {
        try {
            List<LocalOrderManager.CardOrder> orders = plugin.getLocalOrderManager().getAllCardOrders();
            long now = System.currentTimeMillis();

            for (LocalOrderManager.CardOrder o : orders) {
                if (LocalOrderManager.CARD_REWARD_PROCESSING.equals(o.status)
                        || LocalOrderManager.CARD_REWARD_PENDING.equals(o.status)
                        || LocalOrderManager.CARD_CONFIRMED.equals(o.status)) {

                    if (now - o.createdAt > TIMEOUT_THRESHOLD_MS) {
                        List<String> rewardCmds = RewardDispatcher.resolveRewardCmds(plugin, o.denom, "card");
                        if (rewardCmds.isEmpty()) continue;

                        String rewardAmt = RewardDispatcher.computeRewardAmt(plugin, o.denom, "card");
                        String rewardHash = RewardDeliveryLedger.computeRewardHash(rewardCmds, rewardAmt);

                        if (plugin.getRewardDeliveryLedger() != null && plugin.getRewardDeliveryLedger().hasDelivered("card", o.requestId, rewardHash)) {
                            plugin.getLocalOrderManager().updateCardStatus(o.requestId, LocalOrderManager.CARD_APPROVED, o.message);
                            continue;
                        }

                        plugin.getLogger().info("[PayBot-Recovery] Tự động giao bù đơn thẻ kẹt: #" + o.requestId + " cho " + o.playerName);
                        SchedulerUtils.runSync(plugin, () -> {
                            boolean wasOnline = RewardDispatcher.dispatchOrQueue(
                                    plugin, o.requestId, o.playerName, rewardCmds, rewardAmt,
                                    String.valueOf(o.denom), "card", o.requestId, "");
                            plugin.getLocalOrderManager().updateCardStatus(o.requestId, LocalOrderManager.CARD_APPROVED, o.message);
                            if (plugin.getRewardDeliveryLedger() != null) {
                                plugin.getRewardDeliveryLedger().recordDelivery("card", o.requestId, o.playerName, rewardHash, "DONE");
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PayBot-Recovery] Lỗi khi phục hồi card orders: " + e.getMessage());
        }
    }
}
