package dev.liquidplus.watermark.engine;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

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

        // Create mutable copy with same dimensions
        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        // Calculate proportional scale based on photo dimensions (standard 1080p base)
        float baseDim = Math.min(width, height);
        float scale = Math.max(1.0f, baseDim / 1080.0f) * config.scale;

        // Fonts
        Typeface fontRegular, fontBold;
        try {
            fontRegular = ResourcesCompat.getFont(context, R.font.inter_regular);
            fontBold = ResourcesCompat.getFont(context, R.font.inter_bold);
        } catch (Exception e) {
            fontRegular = Typeface.SANS_SERIF;
            fontBold = Typeface.DEFAULT_BOLD;
        }

        // Paints
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.6f * scale);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#44000000"));

        Paint textTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textTitlePaint.setColor(Color.WHITE);
        textTitlePaint.setTextSize(15f * scale);
        textTitlePaint.setTypeface(fontBold);
        textTitlePaint.setShadowLayer(2.5f * scale, 0, 1.2f * scale, Color.parseColor("#66000000"));

        Paint textSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textSubPaint.setColor(Color.parseColor("#E4E4E7"));
        textSubPaint.setTextSize(10.5f * scale);
        textSubPaint.setTypeface(fontRegular);
        textSubPaint.setShadowLayer(2f * scale, 0, 1f * scale, Color.parseColor("#66000000"));

        // Measure content
        float modelWidth = textTitlePaint.measureText(config.deviceModel != null ? config.deviceModel : "");
        float paramsWidth = textSubPaint.measureText(config.cameraParams != null ? config.cameraParams : "");
        float dateWidth = config.showDateTime && config.dateTime != null ? textSubPaint.measureText(config.dateTime) : 0;
        float rightColWidth = Math.max(paramsWidth, dateWidth);

        float logoSize = 24f * scale;
        float paddingSide = 20f * scale;
        float spacingMiddle = 32f * scale;
        float logoMargin = 12f * scale;

        float capsuleW = paddingSide * 2 + logoSize + logoMargin + modelWidth + spacingMiddle + rightColWidth;
        capsuleW = Math.max(capsuleW, 280f * scale);
        float capsuleH = 46f * scale;
        float r = capsuleH / 2.0f; // pill hemisphere

        // Position on photo
        float centerX = width * config.posXRatio;
        float centerY = height * config.posYRatio;
        float left = centerX - capsuleW / 2.0f;
        float top = centerY - capsuleH / 2.0f;

        // Clamp inside photo bounds
        left = Math.max(16f * scale, Math.min(left, width - capsuleW - 16f * scale));
        top = Math.max(16f * scale, Math.min(top, height - capsuleH - 16f * scale));

        RectF capsuleRect = new RectF(left, top, left + capsuleW, top + capsuleH);
        Path capsulePath = new Path();
        capsulePath.addRoundRect(capsuleRect, r, r, Path.Direction.CW);

        // 1. Drop shadow behind capsule
        canvas.drawRoundRect(capsuleRect.left, capsuleRect.top + 3f * scale,
                capsuleRect.right, capsuleRect.bottom + 3f * scale, r, r, shadowPaint);

        // 2. Optical Backdrop Refraction & Blur
        try {
            int cropL = Math.max(0, (int) capsuleRect.left);
            int cropT = Math.max(0, (int) capsuleRect.top);
            int cropW = Math.min(width - cropL, (int) capsuleRect.width());
            int cropH = Math.min(height - cropT, (int) capsuleRect.height());

            if (cropW > 10 && cropH > 10) {
                Bitmap slice = Bitmap.createBitmap(source, cropL, cropT, cropW, cropH);
                // Fast scale blur
                int blurW = Math.max(1, cropW / 12);
                int blurH = Math.max(1, cropH / 12);
                Bitmap small = Bitmap.createScaledBitmap(slice, blurW, blurH, true);
                Bitmap blurredSlice = Bitmap.createScaledBitmap(small, cropW, cropH, true);

                canvas.save();
                canvas.clipPath(capsulePath);
                canvas.drawBitmap(blurredSlice, cropL, cropT, null);
                canvas.restore();

                slice.recycle();
                small.recycle();
                blurredSlice.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Liquid Glass Frosted Gradient Fill
        int alphaTop = (int) (Math.max(0.20f, config.blurStrength * 0.50f) * 255);
        int alphaBottom = (int) (Math.max(0.10f, config.blurStrength * 0.30f) * 255);
        LinearGradient fillGrad = new LinearGradient(
                capsuleRect.left, capsuleRect.top,
                capsuleRect.left, capsuleRect.bottom,
                Color.argb(alphaTop, 255, 255, 255),
                Color.argb(alphaBottom, 255, 255, 255),
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(fillGrad);
        canvas.drawPath(capsulePath, fillPaint);

        // 4. Fresnel Optical Rim Light
        int rimAlpha = (int) (config.rimLightStrength * 230);
        LinearGradient rimGrad = new LinearGradient(
                capsuleRect.left, capsuleRect.top,
                capsuleRect.right, capsuleRect.bottom,
                new int[]{
                        Color.argb(rimAlpha, 255, 255, 255),
                        Color.argb((int)(rimAlpha * 0.45f), 255, 255, 255),
                        Color.argb((int)(rimAlpha * 0.75f), 255, 255, 255)
                },
                new float[]{0.0f, 0.6f, 1.0f},
                Shader.TileMode.CLAMP
        );
        strokePaint.setShader(rimGrad);
        canvas.drawPath(capsulePath, strokePaint);

        // 5. Specular Inner Highlight
        RectF innerSheen = new RectF(
                capsuleRect.left + 3f * scale,
                capsuleRect.top + 1.5f * scale,
                capsuleRect.right - 3f * scale,
                capsuleRect.top + capsuleH * 0.42f
        );
        Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheenPaint.setShader(new LinearGradient(
                innerSheen.left, innerSheen.top,
                innerSheen.left, innerSheen.bottom,
                Color.argb((int)(config.rimLightStrength * 75), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(innerSheen, r, r, sheenPaint);

        // 6. Draw Content inside Capsule
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
            if (config.deviceModel != null) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, curX, textY, textTitlePaint);
            }

            // Right column: Specs & Date
            float rightX = capsuleRect.right - paddingSide;
            textSubPaint.setTextAlign(Paint.Align.RIGHT);

            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                float line1Y = contentCenterY - 2.5f * scale;
                float line2Y = contentCenterY + 11.5f * scale;
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, rightX, line1Y, textSubPaint);
                }
                canvas.drawText(config.dateTime, rightX, line2Y, textSubPaint);
            } else {
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, rightX, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);

        } else {
            // Logo on the Right
            float leftX = capsuleRect.left + paddingSide;

            // Device Model on left
            if (config.deviceModel != null) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, leftX, textY, textTitlePaint);
            }

            float curX = capsuleRect.right - paddingSide;

            // Logo on right
            if (customLogo != null) {
                RectF dest = new RectF(curX - logoSize, contentCenterY - logoSize / 2f, curX, contentCenterY + logoSize / 2f);
                canvas.drawBitmap(customLogo, null, dest, null);
                curX -= (logoSize + logoMargin);
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int)(curX - logoSize), (int)(contentCenterY - logoSize / 2f),
                        (int) curX, (int)(contentCenterY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curX -= (logoSize + logoMargin);
            }

            // Specs on right before logo
            textSubPaint.setTextAlign(Paint.Align.RIGHT);
            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                float line1Y = contentCenterY - 2.5f * scale;
                float line2Y = contentCenterY + 11.5f * scale;
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, curX, line1Y, textSubPaint);
                }
                canvas.drawText(config.dateTime, curX, line2Y, textSubPaint);
            } else {
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = contentCenterY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, curX, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);
        }

        return result;
    }

    public static Uri saveToGallery(Context context, Bitmap bitmap, int quality) {
        String filename = "LiquidWatermark_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos;
        Uri imageUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LiquidWatermark");

                imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    fos = context.getContentResolver().openOutputStream(imageUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                    if (fos != null) fos.close();
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "LiquidWatermark");
                if (!dir.exists()) dir.mkdirs();
                File imageFile = new File(dir, filename);
                fos = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                fos.close();
                imageUri = Uri.fromFile(imageFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return imageUri;
    }

    private static Drawable getBrandDrawable(Context context, String brandLogo) {
        if ("custom".equals(brandLogo)) return null;
        int resId;
        switch (brandLogo) {
            case "hasselblad":
                resId = R.drawable.ic_brand_hasselblad;
                break;
            case "apple":
                resId = R.drawable.ic_brand_apple;
                break;
            case "xiaomi":
                resId = R.drawable.ic_brand_xiaomi;
                break;
            case "sony":
                resId = R.drawable.ic_brand_sony;
                break;
            case "leica":
            default:
                resId = R.drawable.ic_brand_leica;
                break;
        }
        return ContextCompat.getDrawable(context, resId);
    }
}
