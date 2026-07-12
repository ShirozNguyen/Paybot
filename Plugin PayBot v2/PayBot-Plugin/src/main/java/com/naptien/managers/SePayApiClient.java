package com.naptien.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.naptien.NapTienPlugin;

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
 * Đây là cách tích hợp MỚI (Phase B v5.0.0) — chỉ cần 1 API Token, KHÔNG cần webhook
 * (không cần bot.py, không cần ngrok, không cần public HTTPS endpoint). Plugin tự
 * POLL định kỳ (outbound only) để lấy giao dịch mới, hoàn toàn khác với flow cũ
 * (/sepaysetup bản cũ dùng merchant-id/secret-key + IPN webhook qua bot.py).
 *
 * Changelog:
 *   v5.0.0 (Phase B) — Thêm mới.
 */
public class SePayApiClient {

    private final NapTienPlugin plugin;

    public SePayApiClient(NapTienPlugin plugin) {
        this.plugin = plugin;
    }

    private String baseUrl() {
        // my.dev.sepay.vn — môi trường sandbox/test riêng của SePay (không đụng dữ liệu
        // ngân hàng thật), dùng để admin thử nghiệm trước khi chuyển sang my.sepay.vn (thật).
        return plugin.getConfig().getBoolean("sepay-api.sandbox", false)
                ? "https://my.dev.sepay.vn" : "https://my.sepay.vn";
    }

    private String token() {
        return plugin.getConfig().getString("sepay-api.api-token", "").trim();
    }

    public static class BankAccountInfo {
        public String id, accountHolderName, accountNumber, bankShortName, bankFullName, bankBin, bankCode;
        public boolean active;
    }

    public static class TransactionInfo {
        public long   id;
        public String accountNumber, transactionDate, content, referenceNumber;
        public long   amountIn, amountOut;
    }

    /** Kết quả test token: null = lỗi mạng/token sai, "" = ok. */
    public String testToken() {
        ApiResult res = get("/userapi/transactions/count");
        if (res.error != null) return res.error;
        return (res.body != null && res.body.has("status") && res.body.get("status").getAsInt() == 200) ? "" : "Token không hợp lệ hoặc đã bị xoá.";
    }

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

    /** Lấy giao dịch mới hơn sinceId (chưa từng poll), sắp tăng dần theo id. */
    public List<TransactionInfo> pollNewTransactions(long sinceId) {
        List<TransactionInfo> list = new ArrayList<>();
        ApiResult res = get("/userapi/transactions/list?since_id=" + (sinceId + 1) + "&limit=200");
        if (res.body == null || !res.body.has("transactions")) return list;
        for (JsonElement el : res.body.getAsJsonArray("transactions")) {
            JsonObject o = el.getAsJsonObject();
            TransactionInfo t = new TransactionInfo();
            try { t.id = Long.parseLong(str(o, "id")); } catch (NumberFormatException ignored) { continue; }
            t.accountNumber    = str(o, "account_number");
            t.transactionDate  = str(o, "transaction_date");
            t.content          = str(o, "transaction_content");
            t.referenceNumber  = str(o, "reference_number");
            t.amountIn         = parseAmount(str(o, "amount_in"));
            t.amountOut        = parseAmount(str(o, "amount_out"));
            list.add(t);
        }
        list.sort(Comparator.comparingLong(t -> t.id));
        return list;
    }

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
            InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
            String body = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
            if (code == 401 || code == 403) {
                result.error = "Token không hợp lệ hoặc không có quyền truy cập (HTTP " + code + ").";
                return result;
            }
            result.body = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            NotificationManager.warn(plugin, "sepay-error", "[PayBot] SePay API lỗi (" + path + "): " + e.getMessage());
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
