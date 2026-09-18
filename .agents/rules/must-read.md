---
trigger: always_on
---

# PAYBOT — LUẬT TỐI CAO CHO AI AGENT
> Phiên bản tài liệu: 1.0
>
> Mục đích: kiểm soát cách AI Agent đọc, phân tích, sửa, build, test và báo cáo project PayBot.
>
> **NGUYÊN TẮC TỐI THƯỢNG:**
>
> **KHÔNG ĐOÁN. KHÔNG SỬA BẰNG TRÍ NHỚ. KHÔNG COI "CÓ VẺ ĐÚNG" LÀ BẰNG CHỨNG.**
>
> AI phải dựa trên **repository hiện tại + bằng chứng hiện tại + build/test thực tế**.

---

# 0. MỤC TIÊU CỦA TÀI LIỆU

PayBot là project multi-loader và multi-version với nhiều phần code được nhân bản hoặc port giữa nhiều thế hệ Minecraft.

Vì vậy, AI Agent phải hoạt động theo nguyên tắc:

```text
Repository hiện tại
        ↓
Điều tra
        ↓
Xác minh
        ↓
Đưa ra giả thuyết
        ↓
Chứng minh root cause
        ↓
Patch nhỏ nhất
        ↓
Verify
        ↓
Build
        ↓
Test
        ↓
Regression
        ↓
Cập nhật trạng thái
```

Không được rút gọn thành:

```text
Nhớ cách làm
    ↓
Đoán
    ↓
Sửa
    ↓
Hy vọng build được
```

---

# 1. THỨ TỰ ƯU TIÊN NGUỒN THÔNG TIN

Khi các nguồn thông tin mâu thuẫn nhau, AI phải ưu tiên theo thứ tự:

```text
1. Source code hiện tại
2. Git working tree hiện tại
3. Build log mới nhất
4. Test/runtime result mới nhất
5. Configuration hiện tại
6. Gradle/loader metadata hiện tại
7. Tài liệu chính thức của đúng version + đúng loader
8. Tài liệu nội bộ có trạng thái VERIFIED
9. LOG.md
10. CHANGELOG.md
11. Session history
12. AI memory
13. Suy luận của AI
```

## 1.1. Quy tắc tuyệt đối

Nếu:

```text
LOG.md nói A
```

nhưng:

```text
source hiện tại nói B
```

→ dùng B.

Nếu:

```text
AI nhớ A
```

nhưng:

```text
build log nói B
```

→ dùng B.

Nếu:

```text
tài liệu nói A
```

nhưng:

```text
compile thật chứng minh B
```

→ ghi nhận discrepancy và ưu tiên bằng chứng thực tế.

---

# 2. AI MEMORY KHÔNG PHẢI LÀ SOURCE OF TRUTH

AI Agent không được sử dụng các câu như:

```text
"Tôi nhớ..."
"Hôm trước chúng ta..."
"Part trước đã sửa..."
"Version này hình như..."
"MC này chắc giống MC kia..."
"Forge thường..."
"Fabric thường..."
```

làm căn cứ để sửa code.

Memory chỉ được dùng để:

```text
gợi ý nơi cần kiểm tra
```

Không được dùng làm:

```text
bằng chứng
```

---

# 3. PHÂN BIỆT FACT / HYPOTHESIS / UNKNOWN

Mỗi kết luận kỹ thuật phải được phân loại.

## FACT

Có bằng chứng trực tiếp.

```text
[FACT]
MC 1.18.2 không có API X.
Evidence: compile + source/API docs.
```

## VERIFIED

Đã được kiểm chứng bằng build/test.

```text
[VERIFIED]
fabric-1.18.2 compile PASS.
```

## DOCUMENTED

Được tài liệu chính thức xác nhận nhưng chưa build.

```text
[DOCUMENTED]
API X tồn tại trong MC Y.
```

## STATIC VERIFIED

Source/AST/grep đã xác nhận nhưng chưa compile.

```text
[STATIC VERIFIED]
Không còn reference X trong module.
```

## INFERRED

Suy luận từ pattern.

```text
[INFERRED]
Module B có khả năng dùng cùng fix như module A.
```

## UNKNOWN

Không đủ thông tin.

```text
[UNKNOWN]
Chưa xác định signature của API X ở MC Y.
```

Không được biến:

```text
INFERRED
```

thành:

```text
VERIFIED
```

---

# 4. QUY TẮC "NO UNSUPPORTED CLAIM"

AI không được nói:

```text
"đã fix"
"đã tương thích"
"đã hỗ trợ"
"an toàn"
"100% đúng"
"fully compatible"
"fully fixed"
```

nếu bằng chứng không đủ.

Thay vào đó phải nói cụ thể:

```text
Source đã sửa: YES
Syntax: PASS
Compile: PASS/FAIL/NOT TESTED
Runtime: PASS/FAIL/NOT TESTED
Integration: PASS/FAIL/NOT TESTED
```

---

# 5. VERSION LÀ MỘT DANH TÍNH ĐỘC LẬP

Không được coi:

```text
1.19
1.19.1
1.19.2
```

là cùng một API target.

Cũng không được coi:

```text
1.21.10
1.21.11
```

là chắc chắn giống nhau.

Mặc định:

> **Mỗi Minecraft version là một môi trường độc lập cho tới khi có bằng chứng chứng minh ngược lại.**

---

# 6. LOADER LÀ MỘT DANH TÍNH ĐỘC LẬP

Không được mặc định:

```text
Fabric = Quilt
Forge = NeoForge
Paper = Folia
Paper = Purpur
```

Một API tồn tại ở Fabric không có nghĩa nó tồn tại ở Forge.

Một implementation chạy ở Paper không có nghĩa scheduler đó an toàn ở Folia.

---

# 7. PHẢI XÁC ĐỊNH TARGET TRƯỚC KHI SỬA

Mỗi task phải xác định:

```text
Minecraft:
Loader:
Java:
Gradle:
Loader/toolchain version:
Module:
File:
Branch:
Commit:
```

Nếu một trong các thông tin này quan trọng nhưng chưa biết:

> Phải điều tra trước.

---

# 8. KHÔNG ĐƯỢC SUY LUẬN API TỪ VERSION GẦN KỀ

Cấm các suy luận:

```text
1.18.2 có API X
→ 1.18 chắc có.

1.19.2 có API X
→ 1.19 chắc có.

1.21.11 có method X
→ 1.21.10 chắc có.

Forge có method X
→ NeoForge chắc có.
```

Muốn dùng chung implementation phải có:

```text
Evidence
```

cho nhóm target tương ứng.

---

# 9. API CHANGES PHẢI ĐƯỢC VERIFY EXACT TARGET

Trước khi đổi:

```java
someMethod(...)
```

AI phải biết:

```text
Exact Minecraft version
Exact loader
Exact class
Exact method signature
Exact parameter types
Exact return type
Exact mapping
```

Không được sửa dựa trên một snippet của một version khác.

---

# 10. BUILD ERROR KHÔNG ĐỒNG NGHĨA ROOT CAUSE

Compiler thường chỉ hiện lỗi đầu tiên mà nó gặp.

Ví dụ:

```text
A()
```

không tồn tại có thể che:

```text
B()
```

không tồn tại.

Vì vậy:

> Sau mỗi fix, phải build lại và xem lỗi mới.

Không được nói:

```text
"Đã fix file này"
```

chỉ vì error đầu tiên biến mất.

---

# 11. API CHAIN PHẢI ĐƯỢC AUDIT

Nếu code:

```java
player.sendSystemMessage(
    Component.literal(
        ClickableTextHelper.makeOpenUrl(...)
    )
);
```

thì khi một phần bị lỗi phải xem cả:

```text
player
sendSystemMessage
Component.literal
ClickableTextHelper
makeOpenUrl
ClickEvent
HoverEvent
```

Không chỉ sửa token compiler highlight.

---

# 12. ROOT CAUSE ≠ ERROR MESSAGE

Phải phân biệt:

```text
Observed error
```

với:

```text
Root cause
```

Ví dụ:

```text
Observed:
Cannot find symbol: method X()

Possible causes:
- method không tồn tại
- import sai
- class sai
- version sai
- loader sai
- mapping sai
- kiểu biến sai
- overload khác
- lỗi domino từ API trước
```

AI phải điều tra trước khi kết luận root cause.

---

# 13. INVESTIGATION PHẢI ĐI TRƯỚC IMPLEMENTATION

Task không được bắt đầu bằng:

```text
write code
```

mà bắt đầu bằng:

```text
inspect
search
compare
verify
```

Sau đó mới patch.

---

# 14. KHÔNG SỬA NẾU ROOT CAUSE CHƯA ĐỦ CHẮC

Nếu chỉ có:

```text
Hypothesis
```

thì không được thực hiện patch lớn.

Phải tiếp tục:

```text
research
source inspection
dependency inspection
build evidence
```

Nếu vẫn không xác minh được:

```text
STOP
```

---

# 15. STOP CONDITIONS

Agent bắt buộc dừng patch khi:

```text
- Không biết chính xác version
- Không biết loader
- Không xác định được target file
- API signature chưa được xác minh
- Target file khác structure dự kiến
- Expected patch pattern không khớp
- Có nhiều match hơn dự kiến
- Không biết patch có ảnh hưởng module khác không
- Build result mâu thuẫn với giả thuyết
- Có nguy cơ xóa thay đổi của người dùng
- Security behavior chưa hiểu rõ
- Payment behavior chưa hiểu rõ
```

Dừng không có nghĩa thất bại.

Dừng để không phá project.

---

# 16. NEVER FORCE A PATCH

Nếu script nói:

```text
0 matches
```

không được cố sửa pattern cho rộng hơn chỉ để patch chạy.

Nếu:

```text
expected = 1
actual = 3
```

→ STOP.

Nếu target file khác source mẫu:

→ STOP.

---

# 17. PATCH NHỎ NHẤT

Luôn tìm:

```text
minimum correct change
```

Không refactor cả class nếu chỉ cần đổi API.

Không rewrite architecture nếu chỉ có compile error.

Không format toàn file nếu chỉ cần 2 dòng.

---

# 18. KHÔNG REFACTOR NGOÀI PHẠM VI

Nếu task:

```text
Fix compile error
```

không tự động:

```text
rewrite GUI
rewrite database
rename packages
rewrite architecture
upgrade Gradle
```

trừ khi root cause thực sự yêu cầu.

---

# 19. MỌI REFACTOR LỚN PHẢI CÓ JUSTIFICATION

Trước refactor lớn phải xác định:

```text
Vấn đề hiện tại:
...

Tại sao fix nhỏ không đủ:
...

Tại sao refactor này giải quyết được:
...

Phạm vi ảnh hưởng:
...

Rủi ro:
...

Kế hoạch verify:
...

Kế hoạch rollback:
...
```

---

# 20. DUPLICATE CODE PHẢI ĐƯỢC NHẬN DIỆN

Khi một class tồn tại ở nhiều module:

```text
DatabaseManager
BotHttpClient
SePayApiClient
QRMapManager
PluginHttpServer
...
```

AI phải xác định:

```text
- Có bao nhiêu bản?
- Bao nhiêu bản giống hệt?
- Bao nhiêu bản gần giống?
- Bao nhiêu bản version-specific?
- Bao nhiêu bản loader-specific?
```

---

# 21. HASH KHÔNG ĐỦ ĐỂ QUYẾT ĐỊNH PROPAGATION

MD5/SHA chỉ chứng minh:

```text
content giống nhau
```

Nó không tự động chứng minh:

```text behavior cần giống nhau
```

Nếu file giống nhau:

```text
→ có cơ sở propagation
```

nhưng vẫn phải xác định:

```text caller
dependency
version
loader
```

khi patch có ảnh hưởng API.

---

# 22. VERSION-SPECIFIC CODE PHẢI CỰC KỲ THẬN TRỌNG

Các file:

```text
VersionAdapter
ItemStack helpers
Map compatibility
Component compatibility
Packet
Command API
Mapping code
Loader API
```

không được propagation mù.

Một file có tên giống nhau không có nghĩa implementation giống nhau.

---

# 23. BUSINESS LOGIC VS VERSION LOGIC

## Business logic

Ví dụ:

```text
Database
Payment
Order
Reward
HTTP
Configuration
Transaction
```

có thể có nhiều bản giống nhau.

## Version logic

Ví dụ:

```text
Minecraft API
DataComponents
NBT
Packet
Mapping
Text Component
Chat
Scheduler
```

phải được verify theo target.

---

# 24. SAFE PROPAGATION PROTOCOL

Khi một fix đã được VERIFIED:

```text
Source fix
   ↓
Extract exact diff
   ↓
Find candidate targets
   ↓
Group by actual content
   ↓
Verify old pattern
   ↓
Dry-run
   ↓
Apply
   ↓
Static verify
   ↓
Build representative targets
   ↓
Regression
```

---

# 25. KHÔNG PROPAGATE TỪ "SOURCE OF MEMORY"

Không bao giờ:

```text
Part trước đã sửa
↓
copy fix từ trí nhớ
```

Phải:

```text
current source
↓
current diff
↓
exact patch
```

---

# 26. AUTOMATED PATCH PHẢI FAIL-CLOSED

Script patch phải có:

```text
dry-run
expected count
actual count
target list
mismatch detection
summary
verification
```

Ví dụ:

```text
Expected matches: 98
Actual matches:
98

Status:
SAFE TO APPLY
```

Nếu:

```text
97
```

→ STOP.

---

# 27. KHÔNG DÙNG REGEX NGÂY THƠ CHO JAVA PHỨC TẠP

Đặc biệt:

```text
nested method calls
multiline expressions
lambdas
generics
strings
annotations
constructors
```

không được xử lý bằng regex đơn giản nếu nguy cơ thay nhầm cao.

Ưu tiên:

```text
AST
parser
exact contextual patch
```

---

# 28. LINE ENDING PHẢI ĐƯỢC COI LÀ DỮ LIỆU

Trước sửa:

```text
detect CRLF/LF
detect encoding
```

Sau sửa:

```text
verify CRLF/LF
verify encoding
verify file size
verify diff
```

Không suy luận line ending từ:

```text
file kế bên
module kế bên
extension
```

---

# 29. KHÔNG ĐƯỢC REWRITE TOÀN FILE KHÔNG CẦN THIẾT

Nếu task chỉ thay:

```text
3 lines
```

thì không nên tạo diff:

```text
500 lines changed
```

Do:

```text
newline conversion
formatting
encoding conversion
```

---

# 30. GIT SAFETY

Trước task lớn:

```bash
git status
git branch
git diff
```

Phải xác định:

```text
changes existing before task
changes created by agent
```

---

# 31. CẤM DESTRUCTIVE GIT COMMANDS

Không tự ý chạy:

```bash
git reset --hard
git clean -fd
git checkout -- .
git restore .
```

khi có nguy cơ làm mất thay đổi của người dùng.

---

# 32. KHÔNG ĐƯỢC ASSUME MỌI THAY ĐỔI HIỆN TẠI LÀ CỦA AGENT

Nếu working tree đã dirty:

```text
→ giữ nguyên
→ phân biệt với patch hiện tại
```

---

# 33. PAYMENT CODE CÓ MỨC ĐỘ RỦI RO CAO

Các khu vực:

```text
SePay
IPN
transaction
order
reward
database
duplicate detection
card API
payment amount
```

phải có mức kiểm tra cao nhất.

Không được "sửa cho hết lỗi" bằng cách:

```text
return true
return false
ignore exception
fallback success
HTTP 200
skip validation
```

nếu chưa hiểu semantics.

---

# 34. SECURITY CODE PHẢI ĐƯỢC ĐÁNH GIÁ THEO FAILURE MODE

Mọi thay đổi authentication/authorization phải xác định:

```text
Normal case
Failure case
Attacker case
Network failure
Database failure
Configuration failure
```

Không chỉ kiểm tra "server có chạy không".

---

# 35. FAIL-OPEN / FAIL-CLOSED PHẢI CÓ LÝ DO

Khi gặp:

```text
return true/false
```

không được đổi chỉ vì:

```text
"để khỏi lỗi"
```

Phải biết:

```text
true nghĩa gì?
false nghĩa gì?
caller