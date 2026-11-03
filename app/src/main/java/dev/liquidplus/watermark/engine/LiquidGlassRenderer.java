package dev.liquidplus.watermark.engine;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

import dev.liquidplus.watermark.R;
import dev.liquidplus.watermark.model.WatermarkConfig;

public class LiquidGlassRenderer {

    public static Bitmap renderWatermark(Context context, Bitmap source, WatermarkConfig config, Bitmap customLogo) {
        if (source == null || config == null) return source;
        int width = source.getWidth();
        int height = source.getHeight();

        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        drawWatermarkToCanvas(context, canvas, width, height, config, customLogo, source);
        return result;
    }

    /**
     * Renders a transparent overlay Bitmap with the exact watermark positioned for video watermarking.
     */
    public static Bitmap renderWatermarkOverlay(Context context, int width, int height, WatermarkConfig config, Bitmap customLogo) {
        if (width <= 0 || height <= 0 || config == null) return null;
        Bitmap overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        drawWatermarkToCanvas(context, canvas, width, height, config, customLogo, null);
        return overlay;
    }

    private static void drawWatermarkToCanvas(Context context, Canvas canvas, int width, int height, WatermarkConfig config, Bitmap customLogo, @Nullable Bitmap backdropSource) {
        // Calculate proportional scale so that the exported watermark matches the exact visual
        // proportion on the photo/video as seen in the editor preview (based on a standard ~360dp reference width)
        float refBaseDim = Math.min(width, height * 1.5f);
        float scale = (refBaseDim / 360.0f) * (config.scale > 0 ? config.scale : 1.0f);
        scale = Math.max(1.0f, scale);

        // Fonts
        Typeface fontRegular, fontMedium, fontBold;
        try {
            fontRegular = ResourcesCompat.getFont(context, R.font.inter_regular);
            fontMedium = ResourcesCompat.getFont(context, R.font.inter_medium);
            fontBold = ResourcesCompat.getFont(context, R.font.inter_bold);
        } catch (Exception e) {
            fontRegular = Typeface.SANS_SERIF;
            fontMedium = Typeface.DEFAULT_BOLD;
            fontBold = Typeface.DEFAULT_BOLD;
        }

        // Paints - Crisp typography matching Photo Watermark specifications
        Paint textTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textTitlePaint.setColor(Color.WHITE);
        textTitlePaint.setTextSize(12.5f * scale);
        textTitlePaint.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
        textTitlePaint.setShadowLayer(1.8f * scale, 0, 0.8f * scale, Color.parseColor("#80000000"));

        Paint textSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textSubPaint.setColor(Color.WHITE);
        textSubPaint.setTextSize(9.5f * scale);
        textSubPaint.setTypeface(fontMedium != null ? fontMedium : Typeface.DEFAULT);
        textSubPaint.setShadowLayer(1.4f * scale, 0, 0.7f * scale, Color.parseColor("#80000000"));

        // Measure content
        float modelWidth = textTitlePaint.measureText(config.deviceModel != null ? config.deviceModel : "");
        float paramsWidth = textSubPaint.measureText(config.cameraParams != null ? config.cameraParams : "");
        float dateWidth = config.showDateTime && config.dateTime != null ? textSubPaint.measureText(config.dateTime) : 0;
        float rightColWidth = Math.max(paramsWidth, dateWidth);

        float logoSize = 16f * scale;
        float paddingSide = 14f * scale;
        float spacingMiddle = 18f * scale;
        float logoMargin = 7f * scale;

        float capsuleW = paddingSide * 2 + logoSize + logoMargin + modelWidth + spacingMiddle + rightColWidth;
        capsuleW = Math.max(capsuleW, 210f * scale);
        // Sleek capsule height matching Photo Watermark
        float capsuleH = 36f * scale;

        // Position on photo/video (3 fixed modes: Left, Center, Right)
        float marginX = 14f * scale;
        float marginY = 16f * scale;
        float left;

        if (WatermarkConfig.ALIGN_LEFT.equalsIgnoreCase(config.watermarkAlignment)) {
            left = marginX;
        } else if (WatermarkConfig.ALIGN_RIGHT.equalsIgnoreCase(config.watermarkAlignment)) {
            left = width - capsuleW - marginX;
        } else {
            // Default: ALIGN_CENTER
            left = (width - capsuleW) / 2.0f;
        }
        float top = height - capsuleH - marginY;

        // Clamp inside bounds
        left = Math.max(4f * scale, Math.min(left, width - capsuleW - 4f * scale));
        top = Math.max(4f * scale, Math.min(top, height - capsuleH - 4f * scale));

        RectF capsuleRect = new RectF(left, top, left + capsuleW, top + capsuleH);

        // 1. Draw Liquid Glass Refraction & Highlights using KyantGlassEngine
        KyantGlassEngine glassEngine = new KyantGlassEngine();
        glassEngine.drawLiquidGlass(
                canvas,
                capsuleRect,
                backdropSource,
                capsuleRect,
                config.blurStrength,
                config.refractionStrength,
                config.rimLightStrength
        );

        // 2. Draw Content inside Capsule
        float contentCenterY = capsuleRect.centerY();
        boolean isLeft = WatermarkConfig.POSITION_LEFT.equalsIgnoreCase(config.logoPosition);
        Drawable brandDrawable = getBrandDrawable(context, config.brandLogo);

        if (isLeft) {
            float curX = capsuleRect.left + paddingSide;

            // Logo
            if (customLogo != null) {
                RectF dest = new RectF(curX, contentCenterY - logoSize / 2f, curX + logoSize, contentCenterY + logoSize / 2f);
                canvas.drawBitmap(customLogo, null, dest, null);
                curX += logoSize + logoMargin;
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int) curX, (int)(contentCenterY - logoSize / 2f),
                        (int)(curX + logoSize), (int)(contentCenterY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curX += logoSize + logoMargin;
            }

            // Device Model
            if (config.deviceModel != null && !config.deviceModel.isEmpty()) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, curX, textY, textTitlePaint);
            }

            // Right column: Specs & Date
            float rightX = capsuleRect.right - paddingSide;
            textSubPaint.setTextAlign(Paint.Align.RIGHT);

            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                float line1Y = contentCenterY - 1.8f * scale;
                float line2Y = contentCenterY + 9.0f * scale;
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, rightX, line1Y, textSubPaint);
                }
                textSubPaint.setColor(Color.parseColor("#E0E0E0"));
                canvas.drawText(config.dateTime, rightX, line2Y, textSubPaint);
                textSubPaint.setColor(Color.WHITE);
            } else {
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, rightX, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);

        } else {
            // Right logo
            float curLeft = capsuleRect.left + paddingSide;
            if (config.deviceModel != null && !config.deviceModel.isEmpty()) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, curLeft, textY, textTitlePaint);
            }

            float curRight = capsuleRect.right - paddingSide;
            if (customLogo != null) {
                RectF dest = new RectF(curRight - logoSize, contentCenterY - logoSize / 2f, curRight, contentCenterY + logoSize / 2f);
                canvas.drawBitmap(customLogo, null, dest, null);
                curRight -= (logoSize + logoMargin);
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int)(curRight - logoSize), (int)(contentCenterY - logoSize / 2f),
                        (int) curRight, (int)(contentCenterY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curRight -= (logoSize + logoMargin);
            }

            textSubPaint.setTextAlign(Paint.Align.RIGHT);
            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                float line1Y = contentCenterY - 1.8f * scale;
                float line2Y = contentCenterY + 9.0f * scale;
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, curRight, line1Y, textSubPaint);
                }
                textSubPaint.setColor(Color.parseColor("#E0E0E0"));
                canvas.drawText(config.dateTime, curRight, line2Y, textSubPaint);
                textSubPaint.setColor(Color.WHITE);
            } else {
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, curRight, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private static Drawable getBrandDrawable(Context context, String brandKey) {
        if ("custom".equals(brandKey) || brandKey == null) return null;
        int resId = dev.liquidplus.watermark.ui.view.LiquidGlassView.getBrandDrawableRes(brandKey);
        return resId != 0 ? ContextCompat.getDrawable(context, resId) : null;
    }

    public static Uri saveToGallery(Context context, Bitmap bitmap, int quality) {
        String filename = "LiquidWatermark_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos = null;
        Uri imageUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LiquidWatermark");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    fos = context.getContentResolver().openOutputStream(imageUri);
                    if (fos != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                        fos.flush();
                        fos.close();
                        fos = null;
                    }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    context.getContentResolver().update(imageUri, values, null, null);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "LiquidWatermark");
                if (!dir.exists()) dir.mkdirs();
                File imageFile = new File(dir, filename);
                fos = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                fos.flush();
                fos.close();
                fos = null;
                imageUri = Uri.fromFile(imageFile);

                android.media.MediaScannerConnection.scanFile(
                        context,
                        new String[]{imageFile.getAbsolutePath()},
                        new String[]{"image/jpeg"},
                        null
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }

        return imageUri;
    }

    public static Uri saveBitmapToGallery(Context context, Bitmap bitmap) throws Exception {
        Uri uri = saveToGallery(context, bitmap, 96);
        if (uri == null) throw new Exception("Không thể tạo bản ghi MediaStore");
        return uri;
    }
}
