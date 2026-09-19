package com.paybot.managers;

import com.paybot.PayBotMod;

import java.util.List;

/**
 * PaymentRecoveryWorker — v5.5.8 Part 122 (Tuân thủ Rule 17)
 * <p>
 * Lớp chuyên trách quét và phục hồi các giao dịch bị gián đoạn do server crash/restart đột ngột.
 * Chạy khi server khởi động (Startup Recovery) và định kỳ mỗi 60s.
 */
public class PaymentRecoveryWorker {

    private static final long TIMEOUT_THRESHOLD_MS = 60_000L; // 60 giây timeout cho processing

    private final PayBotMod mod;

    public PaymentRecoveryWorker(PayBotMod mod) {
        this.mod = mod;
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
            if (mod.getDatabaseManager() != null) {
                int reverted = mod.getDatabaseManager().revertProcessingOfflineRewards();
                if (reverted > 0) {
                    PayBotMod.LOGGER.info("[PayBot-Recovery] Đã phục hồi " + reverted + " phần thưởng offline kẹt về PENDING.");
                }
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot-Recovery] Lỗi khi phục hồi offline rewards: " + e.getMessage());
        }
    }

    private void recoverBankOrders() {
        try {
            List<LocalOrderManager.BankOrder> orders = mod.getLocalOrderManager().getAllBankOrders();
            long now = System.currentTimeMillis();

            for (LocalOrderManager.BankOrder o : orders) {
                if (LocalOrderManager.BANK_REWARD_PROCESSING.equals(o.status)
                        || LocalOrderManager.BANK_REWARD_PENDING.equals(o.status)
                        || LocalOrderManager.BANK_CONFIRMED.equals(o.status)) {

                    if (now - o.createdAt > TIMEOUT_THRESHOLD_MS) {
                        List<String> rewardCmds = mod.resolveRewardCmds(o.amount, "bank");
                        if (rewardCmds.isEmpty()) continue;

                        String rewardAmt = mod.computeRewardAmt(o.amount, "bank");
                        String rewardHash = RewardDeliveryLedger.computeRewardHash(rewardCmds, rewardAmt);

                        if (mod.getRewardDeliveryLedger() != null && mod.getRewardDeliveryLedger().hasDelivered("bank", o.invoiceId, rewardHash)) {
                            mod.getLocalOrderManager().updateBankStatus(o.invoiceId, LocalOrderManager.BANK_APPROVED);
                            continue;
                        }

                        PayBotMod.LOGGER.info("[PayBot-Recovery] Tự động giao bù đơn bank kẹt: #" + o.invoiceId + " cho " + o.playerName);
                        mod.runOnMainThread(() -> {
                            boolean wasOnline = mod.dispatchOrQueueReward(
                                    o.invoiceId, o.playerName, rewardCmds, rewardAmt,
                                    String.valueOf(o.amount), "bank");
                            mod.getLocalOrderManager().updateBankStatus(o.invoiceId, LocalOrderManager.BANK_APPROVED);
                            if (mod.getRewardDeliveryLedger() != null) {
                                mod.getRewardDeliveryLedger().recordDelivery("bank", o.invoiceId, o.playerName, rewardHash, "DONE");
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot-Recovery] Lỗi khi phục hồi bank orders: " + e.getMessage());
        }
    }

    private void recoverCardOrders() {
        try {
            List<LocalOrderManager.CardOrder> orders = mod.getLocalOrderManager().getAllCardOrders();
            long now = System.currentTimeMillis();

            for (LocalOrderManager.CardOrder o : orders) {
                if (LocalOrderManager.CARD_REWARD_PROCESSING.equals(o.status)
                        || LocalOrderManager.CARD_REWARD_PENDING.equals(o.status)
                        || LocalOrderManager.CARD_CONFIRMED.equals(o.status)) {

                    if (now - o.createdAt > TIMEOUT_THRESHOLD_MS) {
                        List<String> rewardCmds = mod.resolveRewardCmds(o.denom, "card");
                        if (rewardCmds.isEmpty()) continue;

                        String rewardAmt = mod.computeRewardAmt(o.denom, "card");
                        String rewardHash = RewardDeliveryLedger.computeRewardHash(rewardCmds, rewardAmt);

                        if (mod.getRewardDeliveryLedger() != null && mod.getRewardDeliveryLedger().hasDelivered("card", o.requestId, rewardHash)) {
                            mod.getLocalOrderManager().updateCardStatus(o.requestId, LocalOrderManager.CARD_APPROVED, o.message);
                            continue;
                        }

                        PayBotMod.LOGGER.info("[PayBot-Recovery] Tự động giao bù đơn thẻ kẹt: #" + o.requestId + " cho " + o.playerName);
                        mod.runOnMainThread(() -> {
                            boolean wasOnline = mod.dispatchOrQueueReward(
                                    o.requestId, o.playerName, rewardCmds, rewardAmt,
                                    String.valueOf(o.denom), "card");
                            mod.getLocalOrderManager().updateCardStatus(o.requestId, LocalOrderManager.CARD_APPROVED, o.message);
                            if (mod.getRewardDeliveryLedger() != null) {
                                mod.getRewardDeliveryLedger().recordDelivery("card", o.requestId, o.playerName, rewardHash, "DONE");
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot-Recovery] Lỗi khi phục hồi card orders: " + e.getMessage());
        }
    }
}
