package dev.liquidplus.watermark.engine;

import android.content.Context;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExifReader {

    public static class ExifData {
        public String deviceModel = "";
        public String cameraParams = "";
        public String dateTime = "";
        public String brandKey = null;
    }

    public static ExifData readFromUri(Context context, Uri uri) {
        ExifData data = new ExifData();
        if (uri == null) return data;

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return data;
            ExifInterface exif = new ExifInterface(in);

            // 1. Device Model & Brand Detection
            String model = exif.getAttribute(ExifInterface.TAG_MODEL);
            String make = exif.getAttribute(ExifInterface.TAG_MAKE);
            if (model != null && !model.trim().isEmpty()) {
                data.deviceModel = model.trim();
            } else if (make != null && !make.trim().isEmpty()) {
                data.deviceModel = make.trim();
            }

            String searchBrand = ((make != null ? make : "") + " " + (model != null ? model : "")).toLowerCase(Locale.US);
            if (searchBrand.contains("xiaomi") || searchBrand.contains("redmi") || searchBrand.contains("poco")) {
                data.brandKey = "xiaomi";
            } else if (searchBrand.contains("apple") || searchBrand.contains("iphone")) {
                data.brandKey = "apple";
            } else if (searchBrand.contains("samsung")) {
                data.brandKey = "samsung";
            } else if (searchBrand.contains("sony")) {
                data.brandKey = "sony";
            } else if (searchBrand.contains("huawei") || searchBrand.contains("honor")) {
                data.brandKey = "huawei";
            } else if (searchBrand.contains("pixel") || searchBrand.contains("google")) {
                data.brandKey = "google";
            } else if (searchBrand.contains("oneplus")) {
                data.brandKey = "hasselblad";
            } else if (searchBrand.contains("oppo")) {
                data.brandKey = "oppo";
            } else if (searchBrand.contains("vivo") || searchBrand.contains("iqoo")) {
                data.brandKey = "vivo";
            } else if (searchBrand.contains("leica")) {
                data.brandKey = "leica";
            } else if (searchBrand.contains("zeiss")) {
                data.brandKey = "zeiss";
            } else if (searchBrand.contains("hasselblad")) {
                data.brandKey = "hasselblad";
            } else if (searchBrand.contains("nikon")) {
                data.brandKey = "nikon";
            } else if (searchBrand.contains("canon")) {
                data.brandKey = "canon";
            } else if (searchBrand.contains("fuji")) {
                data.brandKey = "fujifilm";
            }

            // 2. Camera Specs: Focal Length, Aperture, ISO, Shutter Speed
            StringBuilder params = new StringBuilder();

            // Focal length
            double focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0);
            if (focalLength > 0) {
                if (focalLength == (long) focalLength) {
                    params.append(String.format(Locale.US, "%dmm", (long) focalLength));
                } else {
                    params.append(String.format(Locale.US, "%.1fmm", focalLength));
                }
            }

            // Aperture / F-Number
            double aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0);
            if (aperture > 0) {
                if (params.length() > 0) params.append(" · ");
                params.append(String.format(Locale.US, "f/%.1f", aperture));
            }

            // ISO
            int iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0);
            if (iso == 0) {
                iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0);
            }
            if (iso > 0) {
                if (params.length() > 0) params.append(" · ");
                params.append("ISO ").append(iso);
            }

            // Shutter Speed / Exposure Time
            double expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0);
            if (expTime > 0) {
                if (params.length() > 0) params.append(" · ");
                if (expTime < 1.0) {
                    params.append(String.format(Locale.US, "1/%ds", Math.round(1.0 / expTime)));
                } else {
                    params.append(String.format(Locale.US, "%.1fs", expTime));
                }
            }

            if (params.length() > 0) {
                data.cameraParams = params.toString();
            }

            // 3. Date / Time
            String dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dateStr == null) {
                dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME);
            }
            if (dateStr != null && dateStr.length() >= 10) {
                // EXIF format is typically "YYYY:MM:DD HH:MM:SS" -> convert to "YYYY.MM.DD"
                String ymd = dateStr.substring(0, 10).replace(':', '.');
                data.dateTime = ymd;
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
                data.dateTime = sdf.format(new Date());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }

    public static android.graphics.Bitmap loadOrientedBitmap(Context context, Uri uri) {
        if (uri == null) return null;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
            if (bitmap == null) return null;

            try (InputStream inExif = context.getContentResolver().openInputStream(uri)) {
                if (inExif != null) {
                    ExifInterface exif = new ExifInterface(inExif);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    int rotation = 0;
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                        case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                        case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                    }
                    if (rotation != 0) {
                        android.graphics.Matrix m = new android.graphics.Matrix();
                        m.postRotate(rotation);
                        android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                        if (rotated != bitmap) {
                            bitmap.recycle();
                            bitmap = rotated;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return bitmap;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
