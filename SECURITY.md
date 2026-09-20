# Chính Sách Bảo Mật (Security Policy) - TripFinance

Dự án **TripFinance** cam kết bảo vệ an toàn thông tin tài chính cá nhân và đoàn công tác của người dùng theo các tiêu chuẩn bảo mật cao nhất của nền tảng Android.

---

## 1. Kiến Trúc Bảo Mật & Dữ Liệu Cục Bộ (Local-First Architecture)

- **Lưu trữ độc lập trên thiết bị (Offline-First Sandbox):** Toàn bộ cơ sở dữ liệu Room SQLite (`trip_finance_database`), chứng từ thu chi, lịch sử nộp quỹ và thông tin tài khoản ngân hàng của thành viên được lưu trữ hoàn toàn trong thư mục sandbox bảo vệ của ứng dụng (`/data/data/com.aistudio.tripfinance.vszupq/databases/`).
- **Không truyền dữ liệu nhạy cảm ra ngoài:** Ứng dụng không tự động gửi dữ liệu ngân hàng, số dư hay chi tiêu cá nhân tới bất kỳ máy chủ bên thứ ba nào.

---

## 2. Chính Sách Sao Lưu & Ngăn Ngừa Rò Rỉ Đám Mây (Cloud Backup & Data Extraction)

Theo các khuyến nghị bảo mật khắt khe:
- **Tắt sao lưu đám mây tự động (`android:allowBackup="false"`):** Ngăn chặn hệ điều hành Android tự động tải cơ sở dữ liệu chứa số tài khoản ngân hàng lên Google Drive / Android Cloud Backup khi người dùng bật tính năng sao lưu điện thoại.
- **Quy tắc trích xuất dữ liệu nghiêm ngặt (`data_extraction_rules.xml` & `backup_rules.xml`):**
  - Chặn triệt để toàn bộ thư mục CSDL (`<exclude domain="database" path="."/>`) khỏi cả cơ chế sao lưu đám mây (cloud-backup) và truyền dữ liệu qua cáp (device-transfer).
  - Chặn cấu hình SharedPreferences (`<exclude domain="sharedpref" path="."/>`).

---

## 3. Bảo Vệ Dữ Liệu Tài Khoản Ngân Hàng & Bút Toán Kế Toán

- **Nhật ký kiểm toán bất biến (Immutable Audit Log):** Mọi thao tác thêm mới, sửa đổi hoặc xóa chi tiêu/quỹ đều được ghi nhận vào bảng `audit_logs` với đầy đủ dữ liệu trước/sau (JSON snapshot), ID thành viên thực hiện, địa chỉ hành động và dấu thời gian.
- **Ràng buộc toàn vẹn CSDL (SQLite Check Constraints & Triggers):**
  - Ngăn chặn xóa thành viên đã có phát sinh chi tiêu hoặc phân bổ tài chính.
  - Ngăn chặn nhập số tiền chi tiêu âm, tỷ giá ngoại tệ không hợp lệ.
  - Ràng buộc khóa ngoại `ON DELETE RESTRICT` bảo vệ tính toàn vẹn chứng từ kế toán.
- **Niêm phong quyết toán (Settlement Freezing):** Sau khi trưởng đoàn thực hiện khóa sổ, toàn bộ giao dịch của đoàn được niêm phong ở chế độ chỉ đọc (Read-Only) để ngăn chặn việc sửa chữa số liệu sau đối soát.

---

## 4. Tối Ưu Hóa & Rút Gọn Mã Nguồn Bản Phát Hành (R8 / ProGuard)

- **Code Shrinking & Obfuscation:** Bản phát hành (Release Build) được kích hoạt R8 minification (`isMinifyEnabled = true`) và loại bỏ tài nguyên thừa (`isShrinkResources = true`).
- **Bảo vệ mã nguồn:** Đổi tên các lớp, phương thức nội bộ và xáo trộn mã để ngăn chặn dịch ngược (de-compilation), bảo vệ thuật toán chia tiền tối ưu và cấu trúc CSDL.

---

## 5. Phiên Bản Được Hỗ Trợ (Supported Versions)

| Phiên bản | Trạng thái hỗ trợ cập nhật bảo mật |
| :--- | :--- |
| **19.0.x (Hiện tại)** | :white_check_mark: Được hỗ trợ đầy đủ |
| < 19.0 | :x: Không còn hỗ trợ |

---

## 6. Quy Trình Báo Cáo Lỗ Hổng Bảo Mật (Reporting a Vulnerability)

Nếu phát hiện bất kỳ vấn đề hoặc rủi ro bảo mật nào trong mã nguồn hoặc ứng dụng:
1. **Không công khai lỗ hổng** trên Issue tracker công khai.
2. **Gửi thông tin chi tiết** kèm theo bằng chứng chứng minh (PoC) tới hòm thư bảo mật của dự án hoặc tạo một **Private Security Advisory** trên kho mã nguồn.
3. Đội ngũ phát triển cam kết:
   - Phản hồi xác nhận tiếp nhận thông tin trong vòng **48 giờ**.
   - Phân tích và phát hành bản vá bảo mật trong vòng **7 ngày làm việc**.
