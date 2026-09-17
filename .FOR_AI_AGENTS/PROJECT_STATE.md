# PAYBOT — PROJECT STATE

> **QUAN TRỌNG:** Đây là trạng thái HIỆN TẠI của repository, không phải lịch sử.
>
> AI Agent phải đọc file này trước khi bắt đầu task lớn.
>
> Không được điền dữ liệu bằng trí nhớ.
>
> Nếu dữ liệu không được xác minh từ repository/build/test hiện tại:
>
> **ghi `UNKNOWN` hoặc `UNVERIFIED`.**

---

# 1. CURRENT PROJECT IDENTITY

```text
Project:
PayBot

Current version:
5.5.5 (Part 74)

Current branch:
master

Current commit:
STATIC VERIFIED

Last updated:
2026-09-17

Last verified by:
Antigravity AI (Part 74 - Fix Legacy Fabric Round 3 & GitHub Actions CI Build)
```

---

# 2. CURRENT GIT STATE

```text
Working tree:
CLEAN / DIRTY / UNKNOWN

Uncommitted changes:
UNKNOWN

Pre-existing changes:
UNKNOWN

Current task changes:
UNKNOWN
```

---

# 3. LAST VERIFIED BUILD

```text
Commit:
UNKNOWN

Date:
UNKNOWN

Environment:
UNKNOWN

Java:
UNKNOWN

Gradle:
UNKNOWN

Command:
UNKNOWN

Result:
UNKNOWN
```

---

# 4. LAST VERIFIED RUNTIME

```text
Commit:
UNKNOWN

Minecraft:
UNKNOWN

Loader:
UNKNOWN

Java:
UNKNOWN

Server:
UNKNOWN

Result:
UNKNOWN
```

---

# 5. MODULE STATUS

## Fabric

| Minecraft | Compile | Build | Runtime | Feature Test | Evidence |
|---|---|---|---|---|---|
| 1.14.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.15.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.16.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.17.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.18.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.19.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.20.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.21.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 26.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |

**QUAN TRỌNG:** Không được điền PASS cho cả range chỉ vì một patch version PASS.

---

# 6. FORGE

| Minecraft | Compile | Build | Runtime | Feature Test | Evidence |
|---|---|---|---|---|---|
| 1.14.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.15.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.16.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.17.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.18.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.19.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.20.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 26.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |

---

# 7. NEOFORGE

| Minecraft | Compile | Build | Runtime | Feature Test | Evidence |
|---|---|---|---|---|---|
| 1.20.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 1.21.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 26.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |

---

# 8. QUILT

| Minecraft | Compile | Build | Runtime | Feature Test | Evidence |
|---|---|---|---|---|---|
| Legacy | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| Modern | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| 26.x | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |

---

# 9. PAPER / PURPUR / FOLIA

| Minecraft | Loader | Compile | Build | Runtime | Feature Test | Evidence |
|---|---|---|---|---|---|---|
| | Paper | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| | Purpur | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |
| | Folia | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | |

---

# 10. CURRENT FAILURES

Chỉ ghi lỗi hiện tại.

Mỗi lỗi:

```text
Module:
Minecraft:
Loader:

File:
Line:

Error:
...

Classification:
CONFIGURE
DEPENDENCY
GRADLE
LOOM
REMAP
COMPILE
RUNTIME
TEST

Root cause:
VERIFIED / INFERRED / UNKNOWN

Evidence:
...

First observed:
...

Last observed:
...

Next action:
...
```

---

# 11. CURRENT UNVERIFIED

Ví dụ:

```text
Module:
...

Reason:
Source exists but no successful build.

Evidence:
...

Next verification:
...
```

---

# 12. CURRENT RUNTIME-UNTESTED

```text
Module:
Minecraft:
Loader:
Feature:
Build status:
Runtime status:
```

---

# 13. CURRENT TECHNICAL DEBT

Chỉ ghi debt hiện tại:

```text
- ...
- ...
```

Không chép lịch sử từ LOG nếu nó không còn đúng.

---

# 14. KNOWN API BOUNDARIES

Chỉ ghi boundary đã xác minh.

Format:

```text
Minecraft:
Loader:
API:
Boundary:
Old:
New:
Evidence:
Status:
```

---

# 15. KNOWN TOOLCHAIN BOUNDARIES

```text
Minecraft range:
Loader:
Gradle:
Java:
Loom/toolchain:
Reason:
Evidence:
```

---

# 16. CURRENT ARCHITECTURE

## Core

```text
UNKNOWN
```

## Payment

```text
UNKNOWN
```

## Database

```text
UNKNOWN
```

## GUI

```text
UNKNOWN
```

## QR

```text
UNKNOWN
```

## Security

```text
UNKNOWN
```

## Multi-version

```text
UNKNOWN
```

## Build system

```text
UNKNOWN
```

---

# 17. DUPLICATED CODE GROUPS

Theo dõi các class có nhiều bản:

```text
Class:
Number of copies:
Number identical:
Number version-specific:
Number loader-specific:
Last audit:
```

---

# 18. PROPAGATED FIXES

Mỗi propagation quan trọng:

```text
Issue:
Source module:
Target group:
Number of targets:
Method:
Verification:
Build verification:
Regression:
```

---

# 19. SECURITY STATUS

```text
IPN authentication:
UNKNOWN

Database credential exposure:
UNKNOWN

Scoped database permissions:
UNKNOWN

GUI anti-theft:
UNKNOWN

Secret handling:
UNKNOWN

Debug-mode safety:
UNKNOWN
```

---

# 20. PAYMENT STATUS

```text
SePay IPN:
UNKNOWN

SePay polling:
UNKNOWN

Card API:
UNKNOWN

Retry:
UNKNOWN

Duplicate transaction protection:
UNKNOWN

Reward:
UNKNOWN

Payment database:
UNKNOWN
```

---

# 21. GUI STATUS

```text
Name:
UNKNOWN

Lore:
UNKNOWN

Hex:
UNKNOWN

Gradient:
UNKNOWN

Anti-theft:
UNKNOWN

Custom config:
UNKNOWN

Version compatibility:
UNKNOWN
```

---

# 22. QR STATUS

```text
Creation:
UNKNOWN

Display:
UNKNOWN

Map locking:
UNKNOWN

Terrain overwrite protection:
UNKNOWN

Expiry:
UNKNOWN

Player logout:
UNKNOWN

Player reconnect:
UNKNOWN
```

---

# 23. FOLIA STATUS

```text
Scheduler:
UNKNOWN

Player operations:
UNKNOWN

Entity operations:
UNKNOWN

Broadcast:
UNKNOWN

Reward:
UNKNOWN

GUI:
UNKNOWN

QR:
UNKNOWN
```

---

# 24. DEBUGGING STATUS

```text
debug-mode:
UNKNOWN

Plugin PayBotDebug:
UNKNOWN

Fabric PayBotDebug:
UNKNOWN

Forge/NeoForge PayBotDebug:
UNKNOWN
```

---

# 25. TEST COVERAGE

| Feature | Static | Compile | Runtime | Integration |
|---|---|---|---|---|
| Startup | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| Database | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| MySQL | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| SQLite | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| SePay | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| Card API | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| Reward | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| GUI | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| QR | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| Folia | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| PlaceholderAPI | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

---

# 26. LAST KNOWN GOOD

```text
Commit:
UNKNOWN

Date:
UNKNOWN

Reason:
UNKNOWN

Build:
UNKNOWN

Runtime:
UNKNOWN
```

---

# 27. KNOWN BAD COMMITS

Chỉ ghi khi có bằng chứng:

```text
Commit:
Problem:
Affected modules:
Evidence:
Fixed by:
```

---

# 28. CURRENT RISKS

Chỉ ghi risk còn tồn tại:

```text
Risk:
...

Impact:
LOW / MEDIUM / HIGH / CRITICAL

Likelihood:
LOW / MEDIUM / HIGH / UNKNOWN

Evidence:
...

Mitigation:
...
```

---

# 29. CURRENT UNKNOWN QUESTIONS

Đây là phần rất quan trọng.

Không được xóa câu hỏi chỉ vì AI "đoán được".

Ví dụ:

```text
1. API X ở MC Y thực sự có signature nào?
2. Module Z đã runtime test chưa?
3. Forge 26.x đã build thật chưa?
```

Mỗi câu hỏi:

```text
Question:
...

Why it matters:
...

Current evidence:
...

Missing evidence:
...

Owner:
...

Status:
OPEN / VERIFIED / CLOSED
```

---

# 30. CURRENT TODO

Chỉ ghi việc chưa làm:

```text
Priority:
Task:
Reason:
Dependencies:
Verification plan:
```

---

# 31. CURRENT DO-NOT-TOUCH

Có thể ghi những khu vực không nên thay đổi trong task hiện tại:

```text
File:
Reason:
Scope restriction:
```

---

# 32. UPDATE RULE

Sau task lớn:

```text
[ ] cập nhật Current Commit
[ ] cập nhật Last Verified Build
[ ] cập nhật Failures
[ ] cập nhật Unverified
[ ] cập nhật Risks
[ ] cập nhật API boundaries
[ ] cập nhật Test Coverage
[ ] cập nhật TODO
[ ] cập nhật Last Known Good nếu phù hợp
```

---

# 33. QUY TẮC TÍNH TRUNG THỰC

Không được dùng:

```text
probably
should work
almost certainly
likely
I think
```

để thay cho status.

Dùng:

```text
VERIFIED
DOCUMENTED
STATIC VERIFIED
INFERRED
UNVERIFIED
UNKNOWN
```

---

# 34. PROJECT STATE PHẢI ĐƯỢC REBUILD TỪ REPOSITORY KHI CÓ NGHI NGỜ

Nếu nghi ngờ file này đã cũ:

```text
Không tin file.
```

Phải:

```text
inspect repository
inspect git
inspect build log
update state
```

---

# 35. LOGICAL RULE

```text
PROJECT_STATE.md
≠ source of truth

PROJECT_STATE.md
= snapshot có cấu trúc của source/build/test hiện tại
```

Nếu snapshot sai:

> sửa snapshot, không sửa source để khớp snapshot.

---

# 36. FINAL RULE

Nếu một fact không thể chứng minh:

```text
UNKNOWN
```

Không có gì xấu hổ khi ghi:

```text
UNKNOWN
```

Điều nguy hiểm là ghi:

```text
PASS
```

khi thực tế chưa biết.