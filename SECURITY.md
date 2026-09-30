# Chính Sách Bảo Mật (Security Policy) - TripFinance

Dự án **TripFinance** cam kết bảo vệ an toàn thông tin tài chính cá nhân và đoàn công tác theo các tiêu chuẩn kỹ thuật minh bạch, thực tế và nghiêm ngặt của nền tảng Android.

---

## 1. Lưu Trữ Dữ Liệu An Toàn & Tương Thích Trang Nhớ 16 KB (Android 15+)

- **Mã Hóa CSDL Đầy Đủ (Full Database Encryption at Rest):**
  - Toàn bộ cơ sở dữ liệu Room SQLite (`trip_finance_database`), bao gồm các bút toán chi tiêu, phân bổ, đóng quỹ, lịch sử kiểm toán và thông tin số tài khoản ngân hàng, được mã hóa cấp trang bằng **SQLCipher** tiêu chuẩn AES-256.
  - **Nâng cấp thư viện chính thức:** Dự án đã chuyển dịch hoàn toàn từ thư viện cũ đã bị ngừng phát triển (`android-database-sqlcipher:4.5.4`) sang thư viện chính thức hiện đại **`net.zetetic:sqlcipher-android:4.6.1`**.
  - **Tương thích trang nhớ 16 KB (Android 15+ / TargetSdk = 36):** Các thư viện liên kết động native (`.so`) trong `sqlcipher-android:4.6.1` được Zetetic biên dịch và căn chỉnh ELF 16 KB (16,384 bytes), đảm bảo tương thích ổn định khi chạy trên Android 15/16 và các thiết bị phần cứng hỗ trợ kích thước trang bộ nhớ 16 KB.
  - **Phân biệt cấu hình trang:** Kích thước trang bộ nhớ ảo của hệ điều hành (OS Virtual Memory 16 KB) hoàn toàn độc lập với kích thước khối trang mã hóa của SQLite (`PRAGMA cipher_page_size = 4096`).
- **Quản Lý Khóa Phần Cứng (Hardware-Backed Android KeyStore):**
  - Khóa mã hóa 256-bit được sinh ngẫu nhiên an toàn bằng `SecureRandom`, được mã hóa qua thuật toán `AES/GCM/NoPadding` và lưu trữ trong vùng bảo mật phần cứng Android KeyStore.
- **Kiến Trúc Thất Bại An Toàn (Fail-Closed Architecture):**
  - Tuyệt đối không cho phép cơ chế fallback mở CSDL dạng văn bản thô (Plaintext). Nếu SQLCipher hoặc KeyStore không thể nạp/giải mã, ứng dụng từ chối khởi tạo CSDL và báo lỗi rõ ràng cho người dùng, ngăn ngừa rò rỉ dữ liệu chưa mã hóa ra đĩa flash.

---

## 2. Bảo Mật AI & Quyền Riêng Tư (Zero Secrets in Client & User Consent)

- **Tuyệt Đối Không Chứa GEMINI_API_KEY trong APK:**
  - Để ngăn chặn nguy cơ trích xuất khóa bí mật qua dịch ngược (de-compilation) tệp APK client, ứng dụng **hoàn toàn không nhúng bất kỳ API key nào** trong mã nguồn hoặc `BuildConfig`.
  - Mọi yêu cầu phân tích nâng cao qua Cloud AI phải được định tuyến qua máy chủ Backend trung gian bảo mật (Cloud Run / Cloud Functions) được cấu hình qua biến `AI_BACKEND_URL`.
  - Máy chủ Backend xác thực tính toàn vẹn của ứng dụng thông qua **Firebase App Check** (Header `X-Firebase-AppCheck`), bảo vệ dịch vụ khỏi các yêu cầu trái phép.
- **Cơ Chế Xin Ý Kiến Minh Bạch (Explicit User Consent):**
  - Trước khi gửi dữ liệu phân tích chi tiêu ra ngoài thiết bị, ứng dụng bắt buộc phải hiển thị hộp thoại `AiConsentDialog` để người dùng xác nhận lựa chọn.
  - Dữ liệu gửi đi (nếu người dùng đồng ý) chỉ là các con số tổng hợp ẩn danh (tổng chi, tỷ lệ danh mục, số dư). **Tuyệt đối không bao giờ gửi số tài khoản ngân hàng, thông tin cá nhân hay nhật ký kiểm toán**.
- **Động Cơ Cố Vấn Cục Bộ Ngoại Tuyến (Offline Local Insight Engine):**
  - Khi không có mạng, không có Backend URL, hoặc người dùng từ chối gửi dữ liệu lên đám mây, ứng dụng sử dụng giải thuật phân tích tài chính cục bộ chạy 100% nội bộ trên thiết bị, bảo đảm quyền riêng tư tuyệt đối mà không phát sinh bất kỳ yêu cầu mạng nào.

---

## 3. Toàn Vẹn Nghiệp Vụ & Ràng Buộc Đa Đoàn (Cross-Trip Integrity)

- **Kiểm Tra Thành Viên Theo Đoàn (Cross-Trip Member Validation):**
  - Khắc phục lỗ hổng của ràng buộc Foreign Key thông thường (chỉ kiểm tra sự tồn tại trong bảng `trip_members` mà không xét ngữ cảnh chuyến đi). Hệ thống áp dụng cơ chế kiểm tra đa lớp (Defense-in-Depth):
    1. **Tầng Repository:** Trong `addExpenseWithSplits`, `updateExpenseWithSplits`, `addFundContribution`, hệ thống kiểm tra và ném `IllegalArgumentException` nếu người chi (`payerMemberId`), người tạo (`createdMemberId`), người nộp quỹ (`memberId`, `recordedByMemberId`), hoặc bất kỳ người chịu chi nào trong danh sách `splits` không thuộc danh sách thành viên của chuyến đi (`tripId`).
    2. **Tầng CSDL (SQLite Triggers):** Các trigger `trg_check_expense_members_belong_to_same_trip_*`, `trg_check_split_member_belongs_to_same_trip_*`, `trg_check_fund_member_belongs_to_same_trip_*` từ chối các thao tác ghi dữ liệu vi phạm toàn vẹn giữa các đoàn.
- **Động Cơ Quyết Toán Phi Trạng Thái (Stateless Settlement Engine):**
  - Loại bỏ hoàn toàn biến toàn cục khả biến `lastReconciliationError` trong `SettlementEngine`. Kết quả tính toán và lỗi đối soát được đóng gói trong đối tượng trả về bất biến `SettlementCalculationResult`, đảm bảo an toàn tuyệt đối khi chạy đồng thời đa luồng (Thread-Safe).
- **Dung Sai Đối Soát Bằng Không Tuyệt Đối (Zero Discrepancy Tolerance):**
  - Toàn bộ thuật toán chia tiền (`SplitCalculator`) phân bổ phần dư theo thứ tự ưu tiên chính xác tới từng 1 đồng.
  - Đối soát tài chính yêu cầu `totalMemberBalance - remainingFund == 0L`. Bất kỳ sai lệch nào (dù chỉ 1 đồng) đều bị chặn để ngăn thất thoát tài chính.
- **Niêm Phong Quyết Toán (Settlement Freezing):**
  - Khi chuyến đi được khóa sổ (`isSettled == 1`), 15 SQLite triggers và tầng Repository chặn hoàn toàn các hành vi sửa/xóa khoản chi, phân bổ, quỹ chung, tỷ giá hoặc thông tin đoàn.

---

## 4. Thực Tế Về R8 / ProGuard (Code Obfuscation vs Data Security)

- **Mục Đích Thật Sự của R8/ProGuard:**
  - R8 và ProGuard là các công cụ thu gọn kích thước mã (code shrinking), loại bỏ tài nguyên thừa (`isShrinkResources = true`) và gây khó khăn cho việc đọc hiểu mã nguồn thông qua đổi tên các ký hiệu lớp/hàm (identifier obfuscation).
  - **R8 không phải là một biện pháp bảo mật dữ liệu** hay ranh giới ngăn chặn giả mạo (tamper-proofing boundary) trên một thiết bị client không đáng tin cậy. Một kẻ tấn công có quyền truy cập root trên thiết bị vẫn có thể phân tích hành vi của ứng dụng.
- **Quy Tắc Tinh Gọn (Minimal ProGuard Rules):**
  - Không lạm dụng giữ lại toàn bộ gói package. Các quy tắc trong `proguard-rules.pro` chỉ giữ lại các trường và hàm khởi tạo tối thiểu của Room Entity phục vụ ánh xạ SQLite ORM và các lớp JNI native của SQLCipher.
  - An toàn thông tin của TripFinance không dựa vào "bảo mật qua sự che giấu" (Security through Obscurity), mà dựa vào cơ chế mã hóa lưu trữ AES-256 (SQLCipher), quản lý khóa phần cứng (KeyStore), kiểm soát toàn vẹn giao dịch (ACID Transactions & DB Triggers) và kiến trúc không lưu khóa bí mật trên client.

---

## 5. Chính Sách Sao Lưu & Ngăn Ngừa Rò Rỉ Dữ Liệu

- **Vô Hiệu Hóa Sao Lưu Tự Động (`android:allowBackup="false"`):** Ngăn hệ điều hành tự động đưa tệp CSDL lên các dịch vụ đám mây chưa được kiểm soát.
- **Quy Tắc Trích Xuất Dữ Liệu (`data_extraction_rules.xml` & `backup_rules.xml`):**
  - Loại trừ toàn bộ thư mục CSDL (`<exclude domain="database" path="."/>`) khỏi cả cơ chế cloud backup và device-to-device transfer qua cáp ADB.
  - Loại trừ thư mục SharedPreferences (`<exclude domain="sharedpref" path="."/>`).

---

## 6. Phiên Bản Được Hỗ Trợ (Supported Versions)

| Phiên bản | Trạng thái hỗ trợ cập nhật bảo mật |
| :--- | :--- |
| **19.0.x (Hiện tại)** | :white_check_mark: Được hỗ trợ đầy đủ |
| < 19.0 | :x: Không còn hỗ trợ |

---

## 7. Quy Trình Báo Cáo Lỗ Hổng Bảo Mật (Vulnerability Disclosure)

Nếu bạn phát hiện vấn đề bảo mật hoặc rủi ro tiềm ẩn trong ứng dụng:
1. **Không mở Issue công khai** trên diễn đàn hoặc GitHub issue tracker.
2. **Gửi thông tin chi tiết** kèm theo bằng chứng chứng minh (PoC) qua email bảo mật của dự án.
3. Đội ngũ kỹ thuật cam kết:
   - Xác nhận tiếp nhận thông tin trong vòng **48 giờ**.
   - Phân tích và phát hành bản vá bảo mật trong vòng **7 ngày làm việc**.
