package com.naptien.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.naptien.PayBotMod;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SePayApiClient — Gọi trực tiếp API của SePay (my.sepay.vn / my.dev.sepay.vn) bằng
 * Bearer Token, theo docs.sepay.vn (API Giao dịch + API Tài khoản ngân hàng).
 * <p>
 * v5.0.2 FIX: Port từ plugin sang Fabric mod. Trước đây mod chỉ hỗ trợ Merchant ID +
 * Secret Key (qua webhook bot.py) — cơ chế đó phụ thuộc ngrok + bot.py online. Giờ
 * mod tự POLL SePay API (outbound only) giống plugin, không cần webhook, không cần bot.
 * <p>
 * Chỉ cần 1 API Token (lấy tại My.SePay.vn → Cấu hình Công ty → API Access).
 *
 * Changelog:
 *   v5.0.0 (Phase B, plugin) — Thêm mới cho plugin.
 *   v5.0.2 — Port sang Fabric mod.
 */
public class SePayApiClient {

    private final PayBotMod mod;

    public SePayApiClient(PayBotMod mod) {
        this.mod = mod;
    }

    // Đọc config động — không cache để config reload trong lúc chạy hoạt động đúng
    private String baseUrl() {
        // sepay-api mod không có field "sandbox" (mod chỉ support production) —
        // nếu sau này cần thêm thì thêm vào config-template.yml.
        return "https://my.sepay.vn";
    }

    private String token() {
        return mod.getConfig().getString("sepay-api.api-token", "").trim();
    }

    // ─── Public DTO ──────────────────────────────────────────────────────────

    public static class BankAccountInfo {
        public String id, accountHolderName, accountNumber, bankShortName, bankFullName, bankBin, bankCode;
        public boolean active;
    }

    public static class TransactionInfo {
        public long   id;
        public String accountNumber, transactionDate, content, referenceNumber;
        public long   amountIn, amountOut;
    }

    // ─── API calls ───────────────────────────────────────────────────────────

    /** Test token: null = ok; non-null = thông báo lỗi cụ thể. */
    public String testToken() {
        ApiResult res = get("/userapi/transactions/count");
        if (res.error != null) return res.error;
        return (res.body != null && res.body.has("status") && res.body.get("status").getAsInt() == 200)
                ? null : "Token không hợp lệ hoặc đã bị xoá.";
    }

    /** Lấy danh sách tài khoản ngân hàng đang liên kết với SePay account này. */
    public List<BankAccountInfo> listBankAccounts() {
        List<BankAccountInfo> list = new ArrayList<>();
        ApiResult res = get("/userapi/bankaccounts/list");
        if (res.body == null || !res.body.has("bankaccounts")) return list;
        for (JsonElement el : res.body.getAsJsonArray("bankaccounts")) {
            JsonObject o = el.getAsJsonObject();
            BankAccountInfo b = new BankAccountInfo();
            b.id                = str(o, "id");
            b.accountHolderName = str(o, "account_holder_name");
            b.accountNumber     = str(o, "account_number");
            b.bankShortName     = str(o, "bank_short_name");
            b.bankFullName      = str(o, "bank_full_name");
            b.bankBin           = str(o, "bank_bin");
            b.bankCode          = str(o, "bank_code");
            b.active            = "1".equals(str(o, "active"));
            list.add(b);
        }
        return list;
    }

    /** Lấy giao dịch mới hơn sinceId, trả về list sắp tăng dần theo id. */
    public List<TransactionInfo> pollNewTransactions(long sinceId) {
        List<TransactionInfo> list = new ArrayList<>();
        ApiResult res = get("/userapi/transactions/list?since_id=" + (sinceId + 1) + "&limit=200");
        if (res.body == null || !res.body.has("transactions")) return list;
        for (JsonElement el : res.body.getAsJsonArray("transactions")) {
            JsonObject o = el.getAsJsonObject();
            TransactionInfo t = new TransactionInfo();
            try { t.id = Long.parseLong(str(o, "id")); } catch (NumberFormatException ignored) { continue; }
            t.accountNumber   = str(o, "account_number");
            t.transactionDate = str(o, "transaction_date");
            t.content         = str(o, "transaction_content");
            t.referenceNumber = str(o, "reference_number");
            t.amountIn        = parseAmount(str(o, "amount_in"));
            t.amountOut       = parseAmount(str(o, "amount_out"));
            list.add(t);
        }
        list.sort(Comparator.comparingLong(t -> t.id));
        return list;
    }

    // ─── Internal HTTP ────────────────────────────────────────────────────────

    private static class ApiResult {
        JsonObject body;
        String     error; // null nếu ok
    }

    private ApiResult get(String path) {
        ApiResult result = new ApiResult();
        try {
            URL url = new URL(baseUrl() + path);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + token());
            con.setRequestProperty("Content-Type", "application/json");
            con.setConnectTimeout(15_000);
            con.setReadTimeout(15_000);
            int code = con.getResponseCode();
            if (code == 401 || code == 403) {
                result.error = "Token không hợp lệ hoặc không có quyền truy cập (HTTP " + code + ").";
                PayBotMod.LOGGER.warn("[PayBot] SePay API: " + result.error);
                return result;
            }
            InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
            String body = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "{}";
            result.body = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            PayBotMod.LOGGER.warn("[PayBot] SePay API lỗi (" + path + "): " + e.getMessage());
            result.error = "Lỗi mạng: " + e.getMessage();
        }
        return result;
    }

    private static String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    private static long parseAmount(String s) {
        try { return (long) Double.parseDouble(s); } catch (Exception e) { return 0L; }
    }
}
