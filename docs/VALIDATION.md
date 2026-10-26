# Kiểm chứng bản 0.1.0

Ngày: 27/10/2026, múi giờ Asia/Bangkok.

## Môi trường

- Windows, JDK 17, Gradle Wrapper 9.3.0, Android Gradle Plugin 9.0.1.
- Compile / target SDK 35, min SDK 26.
- AVD `Medium_Phone_API_36.1`, Android 16, x86_64, màn hình 1080 × 2400; renderer phần mềm. Chưa có điện thoại thật kết nối.

## Kết quả

| Kiểm tra | Kết quả |
| --- | --- |
| `:app:assembleDebug` | PASS, tạo APK debug universal |
| `:app:testDebugUnitTest` | PASS, 3 test cache |
| `:app:lintDebug` | PASS, 0 lỗi, 29 cảnh báo |
| Instrumentation: OCR bitmap chứa “Hello world” | PASS, nhận diện nội dung và bounding box |
| Instrumentation: OCR → dịch Anh / Việt | PASS, tải mô hình, tạo bản dịch khác văn bản gốc |
| MediaProjection từ hộp thoại chia sẻ của Android | PASS, foreground service nhận khung hình thật |
| Bản dịch phủ trên trang mẫu | PASS, quan sát trực tiếp ảnh chụp màn hình |
| Cuộn xuyên qua overlay | PASS, nội dung trang đổi và bản dịch được cập nhật |
| Chọn vùng | PASS, kéo vùng vật lý `(85,1550)–(1010,1780)`, service chỉ dịch vùng đó |
| Tạm dừng / tiếp tục qua bong bóng | PASS, trạng thái service đổi tương ứng; tạm dừng xóa lớp dịch |
| Dừng qua bảng điều khiển nổi | PASS, `dumpsys activity service` xác nhận không còn service |
| Dịch nội dung mới khi ngoại tuyến | PASS, bật chế độ máy bay, xác nhận `Active default network: none`, cuộn tới đoạn mới và thấy bản dịch; đã khôi phục chế độ máy bay về tắt |

Instrumentation chạy 2 test thành công, lần đầu gồm tải mô hình mất 83,665 giây; lần chạy lại dùng mô hình đã có mất 3,086 giây. Đây là thời gian toàn bộ bộ test, không phải độ trễ mỗi lần dịch.

Quyền overlay và thông báo được cấp bằng ADB trên máy ảo để kiểm thử. Chưa kiểm chứng thao tác cấp quyền overlay qua trang Settings trên các hãng điện thoại.

## Quan sát hiệu năng

Sau khi giới hạn ảnh chụp ở 720 px cạnh ngắn, bỏ system bars trước OCR, ẩn alpha cửa sổ dịch lúc chụp và thêm so sánh bitmap:

```text
display=1080x2400 capture=720x1600 region=null
frames=22 ocrFrames=3 blocks=10 latencyMs=114

frames=74 ocrFrames=9 blocks=9 latencyMs=118

region=RectF(85.0,1550.0,1010.0,1780.0)
frames=103 ocrFrames=12 blocks=2 latencyMs=110
```

Số lần OCR thấp hơn số khung đã kiểm tra cho thấy khung không đổi được tái sử dụng. Các giá trị 110–118 ms là chu kỳ tái sử dụng kết quả trên màn hình tĩnh, **không phải độ trễ dịch chữ mới**. Một chu kỳ sau khi cuộn trang nhiều chữ ghi nhận 2.573 ms trên máy ảo này. Khởi tạo mô hình và lần nhận diện đầu có thể chậm hơn.

Phát hiện khi kiểm tra: chỉ đặt `View.INVISIBLE` chưa ngăn được khung cũ có lớp dịch lọt vào OCR trên AVD. Đã thay bằng cập nhật alpha cửa sổ trong WindowManager, giữ khung mới nhất qua khoảng chờ 100 ms rồi mới OCR. Quan sát nhiều chu kỳ cho thấy câu dịch ổn định, cache được dùng lại.

## Giới hạn còn lại

- Chưa đo pin, nhiệt độ, FPS hoặc độ trễ p50/p95 trên điện thoại thật.
- Chưa kiểm chứng xoay màn hình, đa cửa sổ, các bản Android cũ và cơ chế hạn chế chạy nền riêng của từng hãng.
- Chưa chạy bộ kiểm thử OCR cho từng ngôn ngữ Nhật / Trung / Hàn / Hindi.
- Có thể thấy chữ gốc mờ dưới lớp dịch và lớp dịch nhấp trong lúc chụp. Bản dịch dài có thể bị cắt theo vùng chữ gốc.
- Các cảnh báo lint chủ yếu về target SDK / phiên bản thư viện, chuỗi giao diện viết trực tiếp bằng tiếng Việt, API tương thích cũ và gợi ý AndroidX; không có lỗi lint.
- Đây là bản MVP lấy mẫu theo chu kỳ; chưa chứng minh đạt độ trễ dưới một giây cho nội dung thay đổi liên tục.

## Ảnh kiểm chứng

Ảnh trong `images` được lấy từ trang mẫu của chính ứng dụng, không phải nội dung người dùng hay app tham khảo.

- `main.png`: trang chính.
- `live-translation.png`: bản dịch thật từ OCR / mô hình, không phải ô minh họa ở trang chính.
- `offline-translation.png`: dịch khi thiết bị ở chế độ máy bay.

Sau lượt kiểm tra UI, mã có một chỉnh sửa nhỏ ở bước lọc bong bóng: chỉ bỏ khối có tâm nằm trong bong bóng, tránh bỏ cả đoạn văn chỉ vì góc đoạn chạm bong bóng. APK đã được build và lint lại thành công sau chỉnh sửa này.
