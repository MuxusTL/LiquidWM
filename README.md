# Liquid Watermark (Android)

Ứng dụng tạo thanh Watermark thấu kính quang học **Liquid Glass** phản xạ như iPhone trên nền tảng Android Native.

## 🌟 Tính năng nổi bật

1. **Hiệu ứng thấu kính Liquid Glass chân thực:**
   - Mô phỏng khúc xạ ánh sáng (Signed Distance Field Refraction), độ mờ nền (Frosted Blur) và viền phản quang quang học (Fresnel Specular Rim Light).
   - Tối ưu bằng AGSL Shader trên Android 13+ (API 33+) và RenderEffect trên Android 12+, kết hợp thuật toán Displacement Bitmap cho chất lượng xuất ảnh tuyệt đối.

2. **Thiết kế tối giản (Minimal Light Mode):**
   - Tông màu đen & trắng sang trọng: Nền trắng `#F7F8FA`, thẻ card trắng bo góc `20dp`, nút đen `#09090B` bo tròn mềm mại.
   - Toàn bộ giao diện sử dụng phông chữ **Inter** (`Inter-Regular`, `Inter-Medium`, `Inter-SemiBold`, `Inter-Bold`).
   - Màn hình chính có tên ứng dụng to ở bên trái không có subtext theo đúng yêu cầu.

3. **Hỗ trợ song song Ảnh & Video:**
   - **Ảnh**: Tự động nhận diện thông số EXIF (Tiêu cự, Khẩu độ, ISO, Tốc độ màn trập, Ngày chụp, Model máy) và render xuất ảnh ở độ phân giải gốc nguyên bản (100% Quality).
   - **Video**: Gắn watermark động lên video, hỗ trợ xem trước thời gian thực và xuất file MP4 giữ nguyên track âm thanh gốc không suy giảm chất lượng.

4. **Kho cấu hình & Chia sẻ mã 9 chữ số:**
   - Quản lý cấu hình watermark yêu thích (Leica, Hasselblad, Apple, Xiaomi, Sony, hoặc ảnh riêng).
   - Tạo mã chia sẻ 9 chữ số (vd: `849204821`) để sao chép hoặc nhập cấu hình nhanh chóng.

5. **Đa ngôn ngữ & Bảng giới thiệu:**
   - 100% Tiếng Việt mặc định.
   - Hỗ trợ chuyển đổi nhanh giữa Tiếng Việt và English trong mục Cài đặt.
   - Bảng giới thiệu tính năng (Onboarding Sheet) hiện ở lần mở đầu tiên hoặc xem lại từ Cài đặt.

## 📁 Cấu trúc thư mục

- `app/src/main/java/dev/liquidplus/watermark/`:
  - `MainActivity.java`: Điều hướng 4 tab (Sáng tạo, Kho cấu hình, Khám phá, Của tôi).
  - `EditorActivity.java`: Trình tùy biến logo, vị trí, văn bản, thông số và xuất file.
  - `ui/view/LiquidGlassView.java`: Custom View vẽ thanh Liquid Glass tương tác kéo thả.
  - `engine/LiquidGlassRenderer.java`: Engine render watermark ảnh độ phân giải cao.
  - `engine/VideoWatermarkProcessor.java`: Bộ xử lý video MediaCodec + MediaMuxer.
  - `engine/ExifReader.java`: Đọc tự động siêu dữ liệu camera từ ảnh.
  - `model/`: `WatermarkConfig`, `WatermarkPreset`, `PresetStore`.
  - `util/LocaleHelper.java`: Bộ chuyển đổi ngôn ngữ không cần khởi động lại.
