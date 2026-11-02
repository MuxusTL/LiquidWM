package dev.liquidplus.watermark.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * KyantGlassEngine implements the exact optical Liquid Glass refraction, Signed Distance Field (SDF),
 * chromatic dispersion, and specular highlight mechanics from Kyant0/AndroidLiquidGlass.
 *
 * It supports:
 * 1. Hardware AGSL RuntimeShader on Android 13+ (API 33+).
 * 2. High-performance software ray/lens displacement engine for Bitmap exports & Android < 33.
 */
public class KyantGlassEngine {

    // Kyant0 Signed Distance Field (SDF) and normal gradient shader string
    public static final String ROUNDED_RECT_SDF =
            "float radiusAt(float2 coord, float4 radii) {\n" +
            "    if (coord.x >= 0.0) {\n" +
            "        if (coord.y <= 0.0) return radii.y;\n" +
            "        else return radii.z;\n" +
            "    } else {\n" +
            "        if (coord.y <= 0.0) return radii.x;\n" +
            "        else return radii.w;\n" +
            "    }\n" +
            "}\n" +
            "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
            "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
            "    return outside + inside;\n" +
            "}\n" +
            "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
            "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
            "    } else {\n" +
            "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
            "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
            "    }\n" +
            "}\n";

    // Kyant0 Refraction with Chromatic Dispersion Shader String
    public static final String REFRACTION_AGSL =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            "uniform float chromaticAberration;\n" +
            ROUNDED_RECT_SDF +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(max(0.0, 1.0 - x * x));\n" +
            "}\n" +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "    if (-sd >= refractionHeight) {\n" +
            "        return content.eval(coord);\n" +
            "    }\n" +
            "    sd = min(sd, 0.0);\n" +
            "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));\n" +
            "    float2 refractedCoord = coord + d * grad;\n" +
            "    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));\n" +
            "    float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
            "    half4 color = half4(0.0);\n" +
            "    half4 red = content.eval(refractedCoord + dispersedCoord);\n" +
            "    color.r += red.r / 3.5;\n" +
            "    color.a += red.a / 7.0;\n" +
            "    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));\n" +
            "    color.r += orange.r / 3.5;\n" +
            "    color.g += orange.g / 7.0;\n" +
            "    color.a += orange.a / 7.0;\n" +
            "    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));\n" +
            "    color.r += yellow.r / 3.5;\n" +
            "    color.g += yellow.g / 3.5;\n" +
            "    color.a += yellow.a / 7.0;\n" +
            "    half4 green = content.eval(refractedCoord);\n" +
            "    color.g += green.g / 3.5;\n" +
            "    color.a += green.a / 7.0;\n" +
            "    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));\n" +
            "    color.g += cyan.g / 3.5;\n" +
            "    color.b += cyan.b / 3.0;\n" +
            "    color.a += cyan.a / 7.0;\n" +
            "    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));\n" +
            "    color.b += blue.b / 3.0;\n" +
            "    color.a += blue.a / 7.0;\n" +
            "    half4 purple = content.eval(refractedCoord - dispersedCoord);\n" +
            "    color.r += purple.r / 7.0;\n" +
            "    color.b += purple.b / 3.0;\n" +
            "    color.a += purple.a / 7.0;\n" +
            "    return color;\n" +
            "}\n";

    private Object agslRuntimeShader = null; // android.graphics.RuntimeShader on API 33+
    private Paint agslPaint = null;
    private Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public KyantGlassEngine() {
        initShaders();
        borderPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.parseColor("#38000000"));
    }

    private void initShaders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader shader = new android.graphics.RuntimeShader(REFRACTION_AGSL);
                agslRuntimeShader = shader;
                agslPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                agslPaint.setShader(shader);
            } catch (Throwable t) {
                agslRuntimeShader = null;
                agslPaint = null;
            }
        }
    }

    /**
     * Renders authentic Kyant0 Liquid Glass onto target canvas at capsuleRect with given backdrop.
     */
    public void drawLiquidGlass(
            Canvas canvas,
            RectF capsuleRect,
            @Nullable Bitmap backdropSource,
            @Nullable RectF backdropCropRect,
            float blurStrength,
            float refractionStrength,
            float rimLightStrength
    ) {
        float w = capsuleRect.width();
        float h = capsuleRect.height();
        float r = h / 2.0f; // Perfect pill hemisphere

        Path pillPath = new Path();
        pillPath.addRoundRect(capsuleRect, r, r, Path.Direction.CW);

        // 1. Drop shadow behind the capsule
        canvas.drawRoundRect(
                capsuleRect.left, capsuleRect.top + 2.5f,
                capsuleRect.right, capsuleRect.bottom + 2.5f,
                r, r, shadowPaint
        );

        // 2. Refracted Backdrop Image
        Bitmap slice = null;
        if (backdropSource != null && !backdropSource.isRecycled() && backdropCropRect != null) {
            int cropL = Math.max(0, Math.min(backdropSource.getWidth() - 1, (int) backdropCropRect.left));
            int cropT = Math.max(0, Math.min(backdropSource.getHeight() - 1, (int) backdropCropRect.top));
            int cropW = Math.max(1, Math.min(backdropSource.getWidth() - cropL, (int) backdropCropRect.width()));
            int cropH = Math.max(1, Math.min(backdropSource.getHeight() - cropT, (int) backdropCropRect.height()));

            if (cropW > 4 && cropH > 4) {
                try {
                    slice = Bitmap.createBitmap(backdropSource, cropL, cropT, cropW, cropH);
                } catch (Throwable ignored) {}
            }
        }

        boolean drewRefraction = false;

        // Try AGSL GPU Shader on Android 13+
        if (slice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslRuntimeShader != null) {
            drewRefraction = drawAgslRefraction(canvas, pillPath, capsuleRect, slice, w, h, r, refractionStrength);
        }

        // Software Fallback (Runs on all Android versions & Bitmap exports)
        if (!drewRefraction && slice != null) {
            Bitmap refractedSlice = renderSoftwareRefraction(slice, (int) w, (int) h, refractionStrength, blurStrength);
            if (refractedSlice != null) {
                canvas.save();
                canvas.clipPath(pillPath);
                canvas.drawBitmap(refractedSlice, null, capsuleRect, null);
                canvas.restore();
                drewRefraction = true;
                if (refractedSlice != slice) {
                    refractedSlice.recycle();
                }
            }
        }

        if (slice != null) {
            slice.recycle();
        }

        // Fallback tint if no backdrop slice is available
        if (!drewRefraction) {
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setShader(new LinearGradient(
                    capsuleRect.left, capsuleRect.top,
                    capsuleRect.left, capsuleRect.bottom,
                    Color.argb(55, 255, 255, 255),
                    Color.argb(25, 255, 255, 255),
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(capsuleRect, r, r, fillPaint);
        }

        // 3. Kyant0 Ambient & Fresnel Specular Highlights (Top Sheen Arc & Rim Glow)
        drawKyantHighlights(canvas, capsuleRect, r, rimLightStrength);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private boolean drawAgslRefraction(Canvas canvas, Path pillPath, RectF capsuleRect, Bitmap slice, float w, float h, float r, float refractionStrength) {
        try {
            android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) agslRuntimeShader;
            BitmapShader bmpShader = new BitmapShader(slice, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.postScale(w / slice.getWidth(), h / slice.getHeight());
            matrix.postTranslate(capsuleRect.left, capsuleRect.top);
            bmpShader.setLocalMatrix(matrix);

            shader.setInputShader("content", bmpShader);
            shader.setFloatUniform("size", w, h);
            shader.setFloatUniform("offset", -capsuleRect.left, -capsuleRect.top);
            shader.setFloatUniform("cornerRadii", new float[]{r, r, r, r});
            shader.setFloatUniform("refractionHeight", r * 0.90f);
            shader.setFloatUniform("refractionAmount", -r * 0.70f * Math.max(0.2f, refractionStrength));
            shader.setFloatUniform("depthEffect", 0.35f);
            shader.setFloatUniform("chromaticAberration", 0.85f);

            canvas.save();
            canvas.clipPath(pillPath);
            canvas.drawRoundRect(capsuleRect, r, r, agslPaint);
            canvas.restore();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Draws authentic Kyant0 specular highlight and Fresnel rim glow.
     */
    private void drawKyantHighlights(Canvas canvas, RectF capsuleRect, float r, float rimLightStrength) {
        float w = capsuleRect.width();
        float h = capsuleRect.height();

        // 1. Top Specular Sheen Arc (Curved inner refraction highlight)
        int topSheenAlpha = (int) (Math.min(1.0f, rimLightStrength * 1.2f) * 140);
        if (topSheenAlpha > 5) {
            RectF sheenRect = new RectF(
                    capsuleRect.left + 3f,
                    capsuleRect.top + 1.2f,
                    capsuleRect.right - 3f,
                    capsuleRect.top + h * 0.55f
            );
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(1.4f);
            highlightPaint.setShader(new LinearGradient(
                    sheenRect.left, sheenRect.top,
                    sheenRect.right, sheenRect.top,
                    new int[]{
                            Color.argb(0, 255, 255, 255),
                            Color.argb(topSheenAlpha, 255, 255, 255),
                            Color.argb((int)(topSheenAlpha * 0.85f), 255, 255, 255),
                            Color.argb(0, 255, 255, 255)
                    },
                    new float[]{0.0f, 0.35f, 0.70f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(sheenRect, r, r, highlightPaint);
        }

        // 2. Optical Outer Rim Light (Kyant Fresnel border)
        int rimAlpha = (int) (Math.min(1.0f, rimLightStrength) * 180);
        borderPaint.setStrokeWidth(0.9f);
        borderPaint.setShader(new LinearGradient(
                capsuleRect.left, capsuleRect.top,
                capsuleRect.right, capsuleRect.bottom,
                new int[]{
                        Color.argb(rimAlpha, 255, 255, 255),
                        Color.argb((int)(rimAlpha * 0.35f), 255, 255, 255),
                        Color.argb((int)(rimAlpha * 0.65f), 255, 255, 255)
                },
                new float[]{0.0f, 0.55f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(capsuleRect, r, r, borderPaint);
    }

    /**
     * Executes the exact Kyant0 refraction physics, Signed Distance Field, circleMap displacement,
     * and chromatic dispersion in software.
     */
    public static Bitmap renderSoftwareRefraction(
            Bitmap sourceSlice,
            int targetW,
            int targetH,
            float refractionStrength,
            float blurStrength
    ) {
        if (sourceSlice == null || sourceSlice.isRecycled()) return null;

        // Downscale slice for processing if needed to preserve real-time FPS
        Bitmap input = sourceSlice;
        boolean needsRecycle = false;
        if (sourceSlice.getWidth() != targetW || sourceSlice.getHeight() != targetH) {
            input = Bitmap.createScaledBitmap(sourceSlice, targetW, targetH, true);
            needsRecycle = true;
        }

        int w = input.getWidth();
        int h = input.getHeight();
        int totalPixels = w * h;

        int[] srcPixels = new int[totalPixels];
        int[] dstPixels = new int[totalPixels];
        input.getPixels(srcPixels, 0, w, 0, 0, w, h);

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        float radius = halfH; // Pill hemisphere
        float refractionHeight = radius * 0.90f;
        float refractionAmount = -radius * 0.68f * Math.max(0.2f, refractionStrength);
        float chromaticAberration = 0.85f;
        float depthEffect = 0.35f;

        for (int y = 0; y < h; y++) {
            float py = y - halfH;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float px = x - halfW;

                // 1. Kyant0 sdRoundedRect
                float cornerX = Math.abs(px) - (halfW - radius);
                float cornerY = Math.abs(py) - (halfH - radius);
                float outside = (float) Math.hypot(Math.max(cornerX, 0f), Math.max(cornerY, 0f)) - radius;
                float inside = Math.min(Math.max(cornerX, cornerY), 0f);
                float sd = outside + inside;

                if (sd > 0f) {
                    dstPixels[row + x] = 0; // Outside pill
                    continue;
                }

                float d = 0f;
                float gradX = 0f;
                float gradY = 0f;

                if (-sd < refractionHeight) {
                    float normSd = -sd / refractionHeight;
                    float circleVal = 1.0f - normSd;
                    float circleMap = 1.0f - (float) Math.sqrt(Math.max(0.0, 1.0 - circleVal * circleVal));
                    d = circleMap * refractionAmount;

                    // Kyant0 gradSdRoundedRect
                    float gradRadius = Math.min(radius * 1.5f, Math.min(halfW, halfH));
                    float cX = Math.abs(px) - (halfW - gradRadius);
                    float cY = Math.abs(py) - (halfH - gradRadius);

                    if (cX >= 0f || cY >= 0f) {
                        float maxCX = Math.max(cX, 0f);
                        float maxCY = Math.max(cY, 0f);
                        float len = (float) Math.hypot(maxCX, maxCY);
                        if (len > 0.0001f) {
                            float sx = px >= 0f ? 1.0f : -1.0f;
                            float sy = py >= 0f ? 1.0f : -1.0f;
                            gradX = sx * (maxCX / len);
                            gradY = sy * (maxCY / len);
                        }
                    } else {
                        float stepX = cY <= cX ? 1.0f : 0.0f;
                        float sx = px >= 0f ? 1.0f : -1.0f;
                        float sy = py >= 0f ? 1.0f : -1.0f;
                        gradX = sx * stepX;
                        gradY = sy * (1.0f - stepX);
                    }

                    // Depth curvature
                    float cLen = (float) Math.hypot(px, py);
                    if (cLen > 0.0001f) {
                        gradX += depthEffect * (px / cLen);
                        gradY += depthEffect * (py / cLen);
                        float gLen = (float) Math.hypot(gradX, gradY);
                        if (gLen > 0.0001f) {
                            gradX /= gLen;
                            gradY /= gLen;
                        }
                    }
                }

                // 2. Kyant0 Chromatic dispersion
                float disp = chromaticAberration * ((px * py) / (halfW * halfH));
                float dispX = d * gradX * disp;
                float dispY = d * gradY * disp;

                float refX = x + d * gradX;
                float refY = y + d * gradY;

                int rx = Math.max(0, Math.min(w - 1, Math.round(refX + dispX)));
                int ry = Math.max(0, Math.min(h - 1, Math.round(refY + dispY)));
                int gx = Math.max(0, Math.min(w - 1, Math.round(refX)));
                int gy = Math.max(0, Math.min(h - 1, Math.round(refY)));
                int bx = Math.max(0, Math.min(w - 1, Math.round(refX - dispX)));
                int by = Math.max(0, Math.min(h - 1, Math.round(refY - dispY)));

                int rPix = srcPixels[ry * w + rx];
                int gPix = srcPixels[gy * w + gx];
                int bPix = srcPixels[by * w + bx];

                int r = (rPix >> 16) & 0xFF;
                int g = (gPix >> 8) & 0xFF;
                int b = bPix & 0xFF;

                // Subtle transparency & luminance boost
                r = Math.min(255, (int)(r * 1.04f + 9));
                g = Math.min(255, (int)(g * 1.04f + 9));
                b = Math.min(255, (int)(b * 1.04f + 14));

                dstPixels[row + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        output.setPixels(dstPixels, 0, w, 0, 0, w, h);

        if (needsRecycle) {
            input.recycle();
        }

        return output;
    }
}
