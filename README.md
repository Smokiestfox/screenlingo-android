# ScreenLingo

App Android dịch chữ trên màn hình theo chu kỳ, Kotlin, Android Views, MediaProjection, Google ML Kit. Giao diện tiếng Việt, mặc định Anh → Việt.

## Chạy ứng dụng

Yêu cầu JDK 17+, Android SDK Platform 35, Build Tools và thiết bị Android 8.0+ (API 26). Mở thư mục bằng Android Studio, hoặc:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Gradle Wrapper 9.3.0 đã có trong dự án. AGP 9.0.1 dùng Kotlin tích hợp. `local.properties` chứa đường dẫn SDK riêng trên máy và được bỏ qua khi quản lý phiên bản. `android.overridePathCheck=true` cho phép build ở đường dẫn Windows có dấu.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

1. Chọn ngôn ngữ gốc và ngôn ngữ đích.
2. Có thể chọn **Tải mô hình ngôn ngữ** trước. Mô hình cần vài chục MB; tải mặc định qua Wi-Fi. App hỏi trước khi cho phép kết nối khác.
3. Nhấn **Bắt đầu dịch màn hình**, cấp quyền hiển thị trên ứng dụng khác và đồng ý hộp thoại chia sẻ màn hình của Android. Quyền thông báo là tùy chọn.
4. Mở ứng dụng muốn đọc, hoặc chọn **Đọc thử trên màn hình mẫu** trong ScreenLingo.
5. Chạm bong bóng **SL** để tạm dừng, chọn vùng, chuyển về toàn màn hình hoặc dừng. Kéo bong bóng để đổi vị trí.
6. Có thể dừng qua thông báo hoặc nút dừng chia sẻ màn hình của hệ thống. Mỗi phiên mới cần đồng ý chia sẻ lại.

## Phạm vi bản đầu

- OCR bundled: Latin, Nhật, Trung, Hàn, Hindi. Danh sách ngôn ngữ **gốc** chỉ gồm ngôn ngữ mà OCR tương ứng nhận diện được. Một số ngôn ngữ như Thái, Nga, Ả Rập chỉ được chọn làm **đích**.
- Dịch trên thiết bị sau khi tải mô hình, không cần API key. Mô hình tải rồi được tái sử dụng khi ngoại tuyến.
- Đặt bản dịch theo bounding box của OCR, giảm cỡ chữ để vừa vùng gốc. Câu dịch dài có thể bị cắt ở mép vùng; chưa có màn hình chi tiết hoặc tùy chỉnh phông chữ.
- Nhịp cập nhật mục tiêu 500–2500 ms; chỉ một khung hình đang xử lý, so sánh điểm ảnh để bỏ qua OCR trên khung không đổi và có cache cho câu lặp lại. Độ trễ chu kỳ thật được hiển thị ở trang chính khi có kết quả; chu kỳ dùng lại kết quả không phản ánh thời gian dịch văn bản mới.
- Không lưu ảnh chụp hay lịch sử nội dung ra đĩa. Giữ tối đa một bitmap đã xử lý để so sánh với khung mới, giải phóng khi đổi vùng hoặc kết thúc phiên. ML Kit có hoạt động tải mô hình và chẩn đoán riêng của SDK.
- Khi khóa màn hình, hệ thống thu hồi chia sẻ, hoặc tác vụ bị loại khỏi danh sách gần đây, phiên dịch dừng và giải phóng tài nguyên.

Đây là realtime theo kiểu lấy mẫu chữ thay đổi, chưa phải dịch video từng khung hình hay giữ định dạng giao diện gốc hoàn toàn. Chất lượng OCR và dịch phụ thuộc chữ, ngôn ngữ và máy. Nội dung đặt `FLAG_SECURE` / DRM có thể bị hệ thống làm đen; app không vượt qua cơ chế đó.

Lớp dịch được tạm ẩn khoảng 100 ms để lấy ảnh gốc, có thể nhấp nhẹ trên thiết bị thật. Overlay dùng alpha 0,79 để cho thao tác xuyên qua theo quy tắc Android 12+; chữ gốc vẫn có thể thấy mờ bên dưới. App tạm ngừng lấy mẫu khi trang cài đặt ScreenLingo đang hiển thị. Vẫn cần kiểm tra độ trễ, pin, nhiệt độ và độ ổn định trên điện thoại thật trước khi coi là bản phát hành.

## Cấu trúc

- `MainActivity`: giao diện, cấu hình, tải mô hình, xin quyền.
- `capture/TranslationService`: foreground service, một MediaProjection và một VirtualDisplay cho mỗi phiên; lấy mẫu và quản lý vòng đời.
- `engine/TranslationEngine`: chọn OCR theo ngôn ngữ, dịch bất đồng bộ, cache giới hạn.
- `overlay/`: canvas bản dịch không nhận chạm, bong bóng và chọn vùng.
- `ReadingActivity`: nội dung tiếng Anh thật để kiểm tra OCR và cuộn trang.
- `docs/REFERENCE_REVIEW.md`: bằng chứng khảo sát XAPK và tài liệu kỹ thuật đối chiếu.

## Kiểm thử

Unit test kiểm tra cache dùng lại câu, giữ dấu/case/ngắt dòng và loại mục ít dùng nhất. Instrumentation test chạy mô hình OCR bundled trên bitmap tạo từ chữ thật, kiểm tra nội dung và bounding box.

Xem `docs/VALIDATION.md` cho kết quả build và kiểm tra trên máy ảo của phiên phát triển này.
