---
trigger: always_on
---

# PAYBOT — AI ENGINEERING PROTOCOL

Tài liệu này mô tả **AI Agent phải thực sự làm việc như thế nào**.

`AGENTS.md` là luật.

File này là quy trình thao tác.

---

# 1. MỌI TASK ĐỀU CÓ 6 GIAI ĐOẠN

```text
PHASE 0 — Preflight
PHASE 1 — Investigation
PHASE 2 — Root Cause Verification
PHASE 3 — Implementation
PHASE 4 — Verification
PHASE 5 — Regression + State Update
```

Không được bỏ qua phase chỉ vì task "có vẻ nhỏ", trừ khi task thực sự không cần bước đó.

---

# PHASE 0 — PREFLIGHT

## 0.1. Kiểm tra repository

Chạy:

```bash
git status
git branch --show-current
git log -1 --oneline
```

Ghi:

```text
Current branch:
Current commit:
Working tree:
```

---

## 0.2. Phân biệt thay đổi cũ và thay đổi mới

Nếu working tree dirty:

```text
Existing changes:
...

Task changes:
...
```

Không được reset hoặc xóa existing changes.

---

## 0.3. Xác định target

```text
Minecraft:
Loader:
Java:
Gradle:
Toolchain:
Module:
```

Nếu task có nhiều target:

```text
Target Group A:
...

Target Group B:
...
```

---

## 0.4. Xác định scope

Ví dụ:

```text
Task:
Fix compilation issue in fabric-1.19

In scope:
fabric-1.19
CommandRegistry.java

Potentially related:
RewardEffectManager.java

Out of scope:
Forge
NeoForge
Paper
PayBotPlusPlus
```

---

# PHASE 1 — INVESTIGATION

## 1.1. Đọc source hiện tại

Không dựa vào session history trước khi đọc source.

Phải đọc:

```text
target file
caller
callee
related API
version config
loader config
```

---

## 1.2. Thu thập exact error

Nếu có build log:

Ghi nguyên:

```text
Module:
File:
Line:
Error:
```

Không paraphrase quá sớm.

---

## 1.3. Search repository

Tìm:

```text
same method
same class
same API
same pattern
same implementation
```

Mục đích:

```text
bug unique?
bug duplicated?
version-specific?
loader-specific?
```

---

## 1.4. Tìm duplicate

Dùng:

```text
hash
diff
similarity
AST
```

khi cần.

Phân loại:

```text
IDENTICAL
SIMILAR
VERSION-SPECIFIC
LOADER-SPECIFIC
UNKNOWN
```

---

## 1.5. Tìm caller

Nếu method thay đổi semantics:

```text
grep caller
```

Phải biết:

```text
who calls it
how result is used
what assumptions caller makes
```

---

## 1.6. Tìm callee

Nếu lỗi nằm trong wrapper:

```text
inspect called method
```

Ví dụ:

```text
A()
→ B()
→ C()
```

phải hiểu chain đủ để tìm root cause.

---

# PHASE 2 — ROOT CAUSE VERIFICATION

## 2.1. Viết hypothesis

Ví dụ:

```text
Hypothesis H1:
MC 1.18.2 does not provide API X.

Confidence:
MEDIUM

Evidence:
Compiler error.

Missing:
Exact API source.
```

---

## 2.2. Kiểm chứng hypothesis

Nguồn ưu tiên:

```text
current source
exact API source
official mappings
official documentation
dependency source
actual compiler
actual build
```

---

## 2.3. Phản chứng

Không chỉ tìm evidence ủng hộ.

Hãy tìm:

```text
Could API exist anyway?
Could import be wrong?
Could type be wrong?
Could mapping be wrong?
Could this be another overload?
Could compiler error be domino?
```

---

## 2.4. Chỉ kết luận root cause khi đủ evidence

Format:

```text
ROOT CAUSE:
...

PROOF:
...

CONFIDENCE:
HIGH / MEDIUM / LOW

WHY OTHER EXPLANATIONS WERE REJECTED:
...
```

---

# PHASE 3 — IMPLEMENTATION

## 3.1. Thiết kế patch

Viết trước:

```text
Current behavior:
...

Problem:
...

Desired behavior:
...

Minimal change:
...

Affected files:
...
```

---

## 3.2. Không patch trước khi có expected diff

Ví dụ:

```diff
- old
+ new
```

Phải biết trước mình định đổi gì.

---

## 3.3. Nếu patch tự động

Bắt buộc:

```text
dry-run
expected match count
actual match count
target list
```

---

## 3.4. Nếu patch có nhiều module

Phải chia:

```text
Group A
Group B
Group C
```

Không patch tất cả cùng một lúc nếu chưa chắc.

---

## 3.5. Exact replacement

Ưu tiên:

```text
exact context
```

Không:

```text
global replace
```

nếu có nguy cơ thay nhầm.

---

# PHASE 4 — VERIFICATION

## 4.1. Diff review

Sau patch:

```bash
git diff --stat
git diff
```

Kiểm tra:

```text
unexpected file:
unexpected line:
unexpected formatting:
unexpected import:
unexpected behavior:
```

---

## 4.2. Line ending

Kiểm tra:

```text
CRLF/LF
```

Không để patch vô tình rewrite file.

---

## 4.3. Syntax

Tùy project:

```text
tree-sitter
Java parser
Groovy parser
JSON parser
TOML parser
YAML parser
```

Ghi:

```text
Syntax:
PASS
```

---

## 4.4. Search leftover

Nếu task thay API:

```text
old API reference count
```

phải kiểm tra.

Ví dụ:

```text
Before:
Component.literal = 161

After:
Component.literal = 0
```

Nhưng nhớ:

> "0 reference" chỉ chứng minh reference đã biến mất, không chứng minh replacement đúng.

---

## 4.5. Build

Build đúng module.

Ví dụ:

```text
fabric-1.18.2
```

thì phải build:

```text
fabric-1.18.2
```

trước khi build rộng.

---

# PHASE 5 — REGRESSION

## 5.1. Nếu build PASS

Tiếp tục:

```text
related targets
```

---

## 5.2. Nếu build FAIL

Không reset hypothesis ngay lập tức.

Ghi:

```text
Previous hypothesis:
...

New error:
...

Does new evidence contradict old hypothesis?
YES/NO
```

Sau đó investigation lại.

---

## 5.3. Nếu build lộ lỗi domino

Ghi:

```text
Original blocker:
...

Newly exposed issue:
...

Relationship:
DIRECT / INDIRECT / UNKNOWN
```

---

# 2. BUILD ERROR TRIAGE

Khi đọc build log lớn:

## Bước 1

Tách theo module.

```text
fabric-1.18.2
fabric-1.19
fabric-1.19.1
...
```

## Bước 2

Tách:

```text
error
warning
info
```

## Bước 3

Tìm lỗi đầu tiên của từng module.

## Bước 4

Kiểm tra xem các lỗi có cùng root cause không.

---

# 3. LỖI DOMINO

Nếu:

```text
Error A
```

được sửa và build xuất hiện:

```text
Error B
Error C
Error D
```

không được kết luận:

```text
Patch A caused B/C/D
```

cho tới khi phân tích.

Có thể:

```text
B/C/D đã tồn tại
nhưng compiler trước đó chưa đi tới.
```

---

# 4. KHI SỬA API CŨ

Quy trình:

```text
Exact MC version
↓
Exact loader
↓
Exact class
↓
Exact old API
↓
Exact replacement
↓
Signature
↓
Semantic equivalence
↓
Build
```

---

# 5. KHI SỬA MULTI-VERSION

Phải tạo bảng:

| Version | Loader | Current API | Replacement | Evidence | Status |
|---|---|---|---|---|---|

Không nhảy version.

---

# 6. KHI SỬA MULTI-LOADER

Phải tạo:

| Loader | Current implementation | Shared? | Evidence | Action |
|---|---|---|---|---|

Ví dụ:

```text
Fabric: ...
Forge: ...
NeoForge: ...
```

---

# 7. KHI PROPAGATE

Trước:

```text
Source module PASS
```

Sau:

```text
Find all duplicates
```

Then:

```text
Target grouping
```

Then:

```text
Dry-run
```

Then:

```text
Apply
```

Then:

```text
Verify
```

Then:

```text
Build representative
```

Then:

```text
Regression
```

---

# 8. KHI PATCH 100+ CHỖ

Không nên sửa thủ công nếu cùng một transformation được chứng minh là 1:1.

Nhưng script phải:

```text
parse/scan
validate
dry-run
count
patch
verify
```

Không dùng:

```python
text.replace(old, new)
```

một cách mù quáng nếu transformation không thực sự đơn giản.

---

# 9. KHI PATCH JAVA NESTED CALL

Phải hiểu:

```text
opening parenthesis
nested parentheses
string literals
comments
lambdas
method references
```

Không tìm dấu `)` bằng:

```text
first closing parenthesis
```

---

# 10. KHI PATCH LINE ENDING

Trước:

```text
file encoding
line ending
```

Sau:

```text
same encoding
same line ending
```

---

# 11. TEST PLAN THEO FEATURE

## GUI

```text
open
display name
display lore
hex
gradient
click
shift-click
double-click
swap
clone
throw
drag
```

## Payment

```text
valid
invalid
timeout
duplicate
malformed response
retry
provider error
database error
```

## QR

```text
create
display
lock
inventory tick
expire
remove
logout
reconnect
```

## Database

```text
connect
timeout
wrong credential
database unavailable
duplicate query
transaction state
reconnect
```

## Folia

```text
player operation
entity operation
global operation
async operation
logout
login
timer
broadcast
```

---

# 12. RUNTIME TEST REPORT

Format:

```text
Minecraft:
Loader:
Java:
Server:
Commit:
Environment:

Feature:
...

Scenario:
...

Expected:
...

Actual:
...

Result:
PASS / FAIL

Evidence:
...
```

---

# 13. API RESEARCH REPORT

Nếu cần tra docs:

```text
API:
Minecraft:
Loader:

Official source:
...

Method/class:
...

Signature:
...

Available since:
...

Not available in:
...

Evidence:
...

Confidence:
...
```

---

# 14. SECURITY CHANGE REPORT

```text
Threat:
...

Affected component:
...

Current vulnerability:
...

Attack path:
...

Mitigation:
...

Failure behavior:
...

Residual risk:
...

Verification:
...
```

---

# 15. PAYMENT CHANGE REPORT

```text
Payment flow:
...

Current behavior:
...

Bug:
...

Potential financial impact:
...

Fix:
...

Idempotency:
...

Retry semantics:
...

HTTP semantics:
...

Database semantics:
...

Verification:
...
```

---

# 16. GIT REPORT

Mỗi task lớn:

```text
Branch:
Start commit:
End commit:
Pre-existing modifications:
New modifications:
Files changed:
Unexpected changes:
```

---

# 17. ROLLBACK PLAN

Mỗi refactor lớn:

```text
Rollback commit:
Files affected:
Data migration:
Config migration:
Known rollback risks:
```

---

# 18. CURRENT STATE UPDATE

Sau task lớn phải cập nhật:

```text
docs/PROJECT_STATE.md
```

Ít nhất:

```text
current version
current commit
build status
known failures
known unverified
last verified build
last verified runtime
technical debt
```

---

# 19. API LEDGER UPDATE

Nếu phát hiện API boundary mới:

```text
docs/API_COMPATIBILITY.md
```

---

# 20. FINAL STATUS

Mỗi task kết thúc bằng:

```text
STATUS:
PASS
PARTIAL
BLOCKED
FAIL
UNVERIFIED
```

Không dùng:

```text
DONE
```

nếu còn verification quan trọng chưa làm.

---

# 21. "DONE" CRITERIA

Task chỉ được coi là DONE khi:

```text
[ ] Root cause verified
[ ] Patch applied
[ ] Diff reviewed
[ ] Static verification passed
[ ] Build passed
[ ] Required runtime test passed
[ ] Regression checked
[ ] Documentation updated
[ ] Remaining unknowns recorded
```

Nếu runtime không thuộc scope:

```text
Runtime:
NOT REQUIRED
```

chứ không giả thành PASS.

---

# 22. KHI KHÔNG CÓ MẠNG / KHÔNG BUILD ĐƯỢC

Không được viết:

```text
Build should pass.
```

Phải:

```text
Source verification:
PASS

Syntax:
PASS

External dependency resolution:
NOT AVAILABLE

Compile:
UNVERIFIED

Runtime:
UNVERIFIED
```

---

# 23. KHI TOOL HỎNG

Nếu tool kiểm tra hỏng:

```text
do not substitute confidence for verification
```

Ví dụ:

```text
tree-sitter unavailable
```

không được nói:

```text
syntax definitely correct
```

---

# 24. KHI AI TỰ GÂY LỖI

Format:

```text
Self-introduced issue:
...

Detection:
...

Impact:
...

Rollback:
...

Corrected approach:
...

Verification:
...
```

---

# 25. CHECKLIST CUỐI CÙNG

```text
[ ] Tôi đã đọc source hiện tại.
[ ] Tôi biết đúng version.
[ ] Tôi biết đúng loader.
[ ] Tôi không dựa vào memory.
[ ] Tôi đã tìm duplicate nếu có thể.
[ ] Root cause đã được chứng minh.
[ ] Patch là tối thiểu.
[ ] Diff đúng scope.
[ ] Line ending không bị phá.
[ ] Syntax đã kiểm tra.
[ ] Old API reference đã kiểm tra.
[ ] Build exact target đã chạy.
[ ] Build result đã được đọc.
[ ] Regression đã xem xét.
[ ] Runtime đã test hoặc đánh dấu UNTESTED.
[ ] Unknown đã được ghi lại.
[ ] PROJECT_STATE đã cập nhật.
[ ] Không làm mất thay đổi của user.
```