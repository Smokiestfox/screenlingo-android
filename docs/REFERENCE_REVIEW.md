# Khảo sát app trong `ref`

Ngày khảo sát: 27/10/2026. Phân tích tĩnh manifest XAPK và tên/tài nguyên Android bằng `aapt2 dump resources`. Chưa cài hoặc chạy các app tham khảo; những mục dưới đây là dấu hiệu từ tài nguyên, không phải đánh giá hành vi hay tốc độ thực tế.

| XAPK | Package / phiên bản | Tài nguyên liên quan |
| --- | --- | --- |
| Bubble Screen Translate | `com.niven.translator` / 4.5.6 | `float_realtime_bubble`, `float_realtime_result`, dịch toàn màn hình / một phần / tự động / realtime, thiết lập chu kỳ và độ trong suốt, tải mô hình offline |
| Translate On Screen | `com.recognize_text.translate.screen` / 1.188 | Auto full-screen, auto region, dịch sau tương tác, thiết lập kiểu chữ, hỗ trợ offline |
| Tap To Translate Screen | `com.tap_to_translate.snap_translate` / 1.125 | Bong bóng tự di chuyển về cạnh, chọn vùng, độ trong suốt chế độ tự động, tài nguyên hỗ trợ offline |

Cả ba manifest có quyền hiển thị nổi và foreground service `mediaProjection`. Đây là căn cứ chọn MediaProjection + lớp phủ cho bản đầu.

## Quyết định trong ScreenLingo

- Ưu tiên chu trình tự động và vùng cố định dành cho hội thoại/phụ đề.
- Bong bóng kéo được; bảng điều khiển có tạm dừng, chọn vùng, toàn màn hình và dừng.
- OCR theo từng khối chữ, bản dịch đặt theo tọa độ của khối gốc.
- Dùng mô hình trên thiết bị và bộ nhớ đệm LRU để giảm việc dịch lặp lại.
- Chỉ một khung hình được xử lý tại một thời điểm. Nhịp 0,5 / 0,9 / 1,5 / 2,5 giây là mục tiêu lấy mẫu, không phải cam kết độ trễ.
- Tạm ẩn lớp chữ dịch trước khi nhận khung hình mới để tránh vòng lặp OCR trên bản dịch. Có thể thấy lớp dịch nhấp nhẹ theo chu kỳ; cần đánh giá thêm trên điện thoại thật.

Tự xây dựng mã nguồn và giao diện; không tái sử dụng mã, biểu tượng hay hình ảnh từ APK tham khảo.

## Tài liệu kỹ thuật đối chiếu

- [Android MediaProjection](https://developer.android.com/media/grow/media-projection): quyền theo phiên, foreground service, resize virtual display và giải phóng tài nguyên.
- [ML Kit Text Recognition Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android): OCR Latin, Trung, Nhật, Hàn và Devanagari; mô hình bundled.
- [ML Kit Translation Android](https://developers.google.com/ml-kit/language/translation/android): tải mô hình theo yêu cầu và đóng Translator.
- [Ngôn ngữ dịch được hỗ trợ](https://developers.google.com/ml-kit/language/translation/translation-language-support): phân biệt phạm vi dịch và phạm vi nhận diện OCR.
