package com.naptien.managers;

import com.naptien.PayBotMod;
import com.naptien.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LocalOrderManager — v5.1.0 (Refactored to SQLite for Fabric)
 */
public class LocalOrderManager {

    public static final String BANK_PENDING  = "PENDING";
    public static final String BANK_PAID     = "PAID";
    public static final String BANK_APPROVED = "APPROVED";
    public static final String BANK_EXPIRED  = "EXPIRED";

    public static final String CARD_PROCESSING  = "99";
    public static final String CARD_SUCCESS      = "1";
    public static final String CARD_WRONG_DENOM  = "2";
    public static final String CARD_USED         = "3";
    public static final String CARD_MAINTENANCE  = "4";
    public static final String CARD_WRONG        = "100";
    public static final String CARD_APPROVED     = "APPROVED";

    public static class BankOrder {
        public String  invoiceId;
        public String  playerName;
        public int     amount;
        public String  status;
        public long    createdAt;
        public boolean registeredWithBot = false;
    }

    public static class CardOrder {
        public String  requestId;
        public String  playerName;
        public String  telco;
        public int     denom;
        public String  cardCode;
        public String  cardSerial;
        public String  status;
        public String  message         = "";
        public long    createdAt;
        public int     submitAttempts  = 0;
        public boolean connectionError = false;
    }

    private final PayBotMod mod;
    private final DatabaseManager db;

    private final Map<String, BankOrder> bankOrders = new ConcurrentHashMap<>();
    private final Map<String, CardOrder> cardOrders = new ConcurrentHashMap<>();

    public LocalOrderManager(PayBotMod mod) {
        this.mod = mod;
        this.db = mod.getDatabaseManager();
        loadAll();
    }

    private void loadAll() {
        loadBankOrders();
        loadCardOrders();
    }

    private void loadBankOrders() {
        bankOrders.clear();
        for (Map<String, Object> row : db.getAllBankOrders()) {
            BankOrder o = new BankOrder();
            o.invoiceId          = (String) row.get("invoice_id");
            o.playerName         = (String) row.get("player_name");
            o.amount             = (int) row.get("amount");
            o.status             = (String) row.get("status");
            o.createdAt          = (long) row.get("created_at");
            o.registeredWithBot  = (boolean) row.get("registered_with_bot");
            bankOrders.put(o.invoiceId, o);
        }
        PayBotMod.LOGGER.info("[PayBot] Loaded " + bankOrders.size() + " bank order(s) từ database.");
    }

    private void loadCardOrders() {
        cardOrders.clear();
        for (Map<String, Object> row : db.getAllCardOrders()) {
            CardOrder o = new CardOrder();
            o.requestId      = (String) row.get("request_id");
            o.playerName     = (String) row.get("player_name");
            o.telco          = (String) row.get("telco");
            o.denom          = (int) row.get("denom");
            o.cardCode       = (String) row.get("card_code");
            o.cardSerial     = (String) row.get("card_serial");
            o.status         = (String) row.get("status");
            o.message        = (String) row.get("message");
            o.createdAt      = (long) row.get("created_at");
            o.submitAttempts = (int) row.get("submit_attempts");
            o.connectionError= (boolean) row.get("connection_error");
            cardOrders.put(o.requestId, o);
        }
        PayBotMod.LOGGER.info("[PayBot] Loaded " + cardOrders.size() + " card order(s) từ database.");
    }

    public synchronized void createBankOrder(String invoiceId, String playerName, int amount) {
        BankOrder o   = new BankOrder();
        o.invoiceId   = invoiceId;
        o.playerName  = playerName;
        o.amount      = amount;
        o.status      = BANK_PENDING;
        o.createdAt   = System.currentTimeMillis();
        bankOrders.put(invoiceId, o);
        db.upsertBankOrder(invoiceId, playerName, amount, BANK_PENDING, o.createdAt, 0);
        mod.getLogManager().logBank(playerName, amount, invoiceId,
                LogManager.bankStatusLabel(BANK_PENDING));
    }

    public synchronized void updateBankStatus(String invoiceId, String newStatus) {
        BankOrder o = bankOrders.get(invoiceId);
        if (o == null) return;
        o.status = newStatus;
        db.updateBankStatus(invoiceId, newStatus);
        mod.getLogManager().logBank(o.playerName, o.amount, invoiceId,
                LogManager.bankStatusLabel(newStatus));
    }

    public synchronized void markBankRegistered(String invoiceId) {
        BankOrder o = bankOrders.get(invoiceId);
        if (o == null) return;
        o.registeredWithBot = true;
        db.markBankRegistered(invoiceId, true);
    }

    public BankOrder getBankOrder(String idArg) {
        if (idArg == null) return null;
        BankOrder o = bankOrders.get(idArg);
        if (o != null) return o;
        for (BankOrder order : bankOrders.values()) {
            if (order.invoiceId.startsWith(idArg)) return order;
        }
        return null;
    }

    public List<BankOrder> getAllBankOrders() {
        return new ArrayList<>(bankOrders.values());
    }

    public List<BankOrder> getPendingBankOrders() {
        return bankOrders.values().stream()
                .filter(o -> !BANK_APPROVED.equals(o.status) && !BANK_EXPIRED.equals(o.status))
                .collect(Collectors.toList());
    }

    public BankOrder matchPendingByContent(String content, String code) {
        String upperContent = content == null ? "" : content.toUpperCase();
        String upperCode    = code == null ? "" : code.toUpperCase();
        for (BankOrder order : getPendingBankOrders()) {
            String iid = order.invoiceId.toUpperCase();
            if (upperContent.contains(iid) || iid.equals(upperCode)) return order;
        }
        return null;
    }

    public List<BankOrder> getUnregisteredBankOrders() {
        return bankOrders.values().stream()
                .filter(o -> BANK_PENDING.equals(o.status) && !o.registeredWithBot)
                .collect(Collectors.toList());
    }

    public synchronized void createCardOrder(String requestId, String playerName,
                                              String telco, int denom,
                                              String cardCode, String cardSerial) {
        CardOrder o    = new CardOrder();
        o.requestId    = requestId;
        o.playerName   = playerName;
        o.telco        = telco;
        o.denom        = denom;
        o.cardCode     = cardCode;
        o.cardSerial   = cardSerial;
        o.status       = CARD_PROCESSING;
        o.createdAt    = System.currentTimeMillis();
        cardOrders.put(requestId, o);
        db.upsertCardOrder(requestId, playerName, telco, denom, cardCode, cardSerial,
                CARD_PROCESSING, "", o.createdAt, 0, 0);
        mod.getLogManager().logCard(playerName, denom, cardCode, cardSerial,
                requestId, LogManager.cardStatusLabel(CARD_PROCESSING));
    }

    public synchronized void updateCardStatus(String requestId, String newStatus, String message) {
        CardOrder o = cardOrders.get(requestId);
        if (o == null) return;
        o.status  = newStatus;
        o.message = message != null ? message : "";
        db.updateCardStatus(requestId, newStatus, o.message);
        if (!CARD_PROCESSING.equals(newStatus)) {
            mod.getLogManager().logCard(o.playerName, o.denom, o.cardCode, o.cardSerial,
                    requestId, LogManager.cardStatusLabel(newStatus));
        }
    }

    public synchronized void markCardConnectionError(String requestId, boolean hasError) {
        CardOrder o = cardOrders.get(requestId);
        if (o == null) return;
        o.connectionError = hasError;
        db.updateCardConnectionError(requestId, hasError);
    }

    public synchronized void incrementCardSubmitAttempts(String requestId) {
        CardOrder o = cardOrders.get(requestId);
        if (o == null) return;
        o.submitAttempts++;
        db.incrementCardSubmitAttempts(requestId);
    }

    public CardOrder getCardOrder(String requestId) {
        return cardOrders.get(requestId);
    }

    public List<CardOrder> getAllCardOrders() {
        return new ArrayList<>(cardOrders.values());
    }

    public List<CardOrder> getPendingApprovalCardOrders() {
        return cardOrders.values().stream()
                .filter(o -> CARD_SUCCESS.equals(o.status))
                .collect(Collectors.toList());
    }

    public List<CardOrder> getProcessingCardOrders() {
        return cardOrders.values().stream()
                .filter(o -> CARD_PROCESSING.equals(o.status) && !o.connectionError)
                .collect(Collectors.toList());
    }

    public List<CardOrder> getConnectionErrorCardOrders() {
        return cardOrders.values().stream()
                .filter(o -> o.connectionError)
                .collect(Collectors.toList());
    }

    public String getBankDataFilePath() {
        return new java.io.File(mod.getDataDir().toFile(), "paybot.db").getAbsolutePath();
    }

    public String getCardDataFilePath() {
        return new java.io.File(mod.getDataDir().toFile(), "paybot.db").getAbsolutePath();
    }

    public synchronized int pruneOldOrders(int bankRetentionDays, int cardRetentionDays) {
        int removed = 0;
        long now = System.currentTimeMillis();

        if (bankRetentionDays >= 0) {
            long cutoff = now - bankRetentionDays * 24L * 60 * 60 * 1000;
            List<String> terminalStatuses = List.of(BANK_EXPIRED, BANK_APPROVED);
            int dbRemoved = db.deleteBankOrdersBefore(cutoff, terminalStatuses);

            Iterator<Map.Entry<String, BankOrder>> it = bankOrders.entrySet().iterator();
            while (it.hasNext()) {
                BankOrder o = it.next().getValue();
                boolean terminal = BANK_EXPIRED.equals(o.status) || BANK_APPROVED.equals(o.status);
                if (terminal && o.createdAt <= cutoff) { it.remove(); removed++; }
            }
        }

        if (cardRetentionDays >= 0) {
            long cutoff = now - cardRetentionDays * 24L * 60 * 60 * 1000;
            List<String> terminalStatuses = List.of(
                    CARD_SUCCESS, CARD_WRONG_DENOM, CARD_USED,
                    CARD_MAINTENANCE, CARD_WRONG, CARD_APPROVED);
            db.deleteCardOrdersBefore(cutoff, terminalStatuses);

            Iterator<Map.Entry<String, CardOrder>> it = cardOrders.entrySet().iterator();
            while (it.hasNext()) {
                CardOrder o = it.next().getValue();
                boolean terminal = !CARD_PROCESSING.equals(o.status);
                if (terminal && o.createdAt <= cutoff) { it.remove(); removed++; }
            }
        }

        return removed;
    }
}
