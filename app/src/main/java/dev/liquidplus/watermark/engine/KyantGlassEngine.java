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

    // Exact Kyant0 / Photo Watermark 4.0.6 Signed Distance Field (SDF) and normal gradient shader string
    public static final String COMMON_SDF_AGSL =
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
            "        float2 outside = max(cornerCoord, 0.0);\n" +
            "        float outsideLength = length(outside);\n" +
            "        return outsideLength > 0.0001\n" +
            "            ? sign(coord) * (outside / outsideLength)\n" +
            "            : float2(0.0);\n" +
            "    } else {\n" +
            "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
            "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
            "    }\n" +
            "}\n" +
            "float2 safeNormalize(float2 value) {\n" +
            "    float lengthSquared = dot(value, value);\n" +
            "    return lengthSquared > 0.000001\n" +
            "        ? value * inversesqrt(lengthSquared)\n" +
            "        : float2(1.0, 0.0);\n" +
            "}\n" +
            "float2 clampSampleCoord(float2 coord, float2 size) {\n" +
            "    return clamp(coord, float2(0.5), max(size - float2(0.5), float2(0.5)));\n" +
            "}\n" +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(max(0.0, 1.0 - x * x));\n" +
            "}\n";

    // Photo Watermark 4.0.6 Refraction with 7-Band Chromatic Dispersion Shader
    public static final String REFRACTION_AGSL =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            "uniform float chromaticAberration;\n" +
            COMMON_SDF_AGSL +
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
            "    float2 grad = safeNormalize(\n" +
            "        gradSdRoundedRect(centeredCoord, halfSize, gradRadius)\n" +
            "            + depthEffect * safeNormalize(centeredCoord)\n" +
            "    );\n" +
            "    float2 refractedCoord = clampSampleCoord(coord + d * grad, size);\n" +
            "    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.0001));\n" +
            "    float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
            "    half4 color = half4(0.0);\n" +
            "    half4 red = content.eval(clampSampleCoord(refractedCoord + dispersedCoord, size));\n" +
            "    color.r += red.r / 3.5;\n" +
            "    color.a += red.a / 7.0;\n" +
            "    half4 orange = content.eval(clampSampleCoord(refractedCoord + dispersedCoord * (2.0 / 3.0), size));\n" +
            "    color.r += orange.r / 3.5;\n" +
            "    color.g += orange.g / 7.0;\n" +
            "    color.a += orange.a / 7.0;\n" +
            "    half4 yellow = content.eval(clampSampleCoord(refractedCoord + dispersedCoord * (1.0 / 3.0), size));\n" +
            "    color.r += yellow.r / 3.5;\n" +
            "    color.g += yellow.g / 3.5;\n" +
            "    color.a += yellow.a / 7.0;\n" +
            "    half4 green = content.eval(clampSampleCoord(refractedCoord, size));\n" +
            "    color.g += green.g / 3.5;\n" +
            "    color.a += green.a / 7.0;\n" +
            "    half4 cyan = content.eval(clampSampleCoord(refractedCoord - dispersedCoord * (1.0 / 3.0), size));\n" +
            "    color.g += cyan.g / 3.5;\n" +
            "    color.b += cyan.b / 3.0;\n" +
            "    color.a += cyan.a / 7.0;\n" +
            "    half4 blue = content.eval(clampSampleCoord(refractedCoord - dispersedCoord * (2.0 / 3.0), size));\n" +
            "    color.b += blue.b / 3.0;\n" +
            "    color.a += blue.a / 7.0;\n" +
            "    half4 purple = content.eval(clampSampleCoord(refractedCoord - dispersedCoord, size));\n" +
            "    color.r += purple.r / 7.0;\n" +
            "    color.b += purple.b / 3.0;\n" +
            "    color.a += purple.a / 7.0;\n" +
            "    return color;\n" +
            "}\n";

    // Photo Watermark 4.0.6 Specular Interactive Highlight Shader (Rim lighting sheen)
    public static final String HIGHLIGHT_AGSL =
            "uniform float2 size;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float angle;\n" +
            "uniform float falloff;\n" +
            COMMON_SDF_AGSL +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = coord - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);\n" +
            "    float2 normal = float2(cos(angle), sin(angle));\n" +
            "    float d = dot(grad, normal);\n" +
            "    float intensity = pow(abs(d), falloff);\n" +
            "    float t = step(0.0, d);\n" +
            "    return half4(1.0, 1.0, 1.0, 1.0) * (intensity * t);\n" +
            "}\n";

    private Object agslRuntimeShader = null; // Refraction RuntimeShader on API 33+
    private Object agslHighlightShader = null; // Specular Highlight RuntimeShader on API 33+
    private Paint agslPaint = null;
    private Paint agslHighlightPaint = null;
    private Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public KyantGlassEngine() {
        initShaders();
        borderPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(32, 0, 0, 0));
    }

    private void initShaders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader refShader = new android.graphics.RuntimeShader(REFRACTION_AGSL);
                agslRuntimeShader = refShader;
                agslPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                agslPaint.setShader(refShader);

                android.graphics.RuntimeShader hShader = new android.graphics.RuntimeShader(HIGHLIGHT_AGSL);
                agslHighlightShader = hShader;
                agslHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                agslHighlightPaint.setShader(hShader);
            } catch (Throwable t) {
                agslRuntimeShader = null;
                agslHighlightShader = null;
                agslPaint = null;
                agslHighlightPaint = null;
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

        // 1. Soft subtle ambient shadow behind the capsule
        canvas.drawRoundRect(
                capsuleRect.left, capsuleRect.top + 1.2f,
                capsuleRect.right, capsuleRect.bottom + 1.2f,
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

        boolean isHw = canvas.isHardwareAccelerated();
        boolean drewRefraction = false;

        // Try AGSL GPU Shader on Android 13+ ONLY if canvas is hardware-accelerated
        if (isHw && slice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslRuntimeShader != null) {
            drewRefraction = drawAgslRefraction(canvas, pillPath, capsuleRect, slice, w, h, r, refractionStrength);
        }

        // Software Fallback (Runs on all Android versions, software canvases & Bitmap exports)
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

        // 3. Subtle Liquid Glass Tint Layer (crisp light absorption)
        Paint glassTint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassTint.setStyle(Paint.Style.FILL);
        glassTint.setColor(Color.argb(drewRefraction ? 20 : 45, 255, 255, 255));
        canvas.drawRoundRect(capsuleRect, r, r, glassTint);

        // 4. Interactive Specular Highlight (AGSL on API 33+, Hardware Accelerated only)
        if (isHw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslHighlightShader != null) {
            drawAgslHighlight(canvas, pillPath, capsuleRect, w, h, r, rimLightStrength);
        }

        // 5. Specular Highlights & Rim Glow (Corner crescents + luminous perimeter stroke)
        drawKyantHighlights(canvas, capsuleRect, r, rimLightStrength);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void drawAgslHighlight(Canvas canvas, Path pillPath, RectF capsuleRect, float w, float h, float r, float rimLightStrength) {
        try {
            android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) agslHighlightShader;
            shader.setFloatUniform("size", w, h);
            shader.setFloatUniform("cornerRadii", new float[]{r, r, r, r});
            // 45 degrees top-left / top-right angle matching Photo Watermark
            shader.setFloatUniform("angle", 0.7853982f);
            shader.setFloatUniform("falloff", 16.0f);

            agslHighlightPaint.setAlpha((int) (Math.min(1.0f, rimLightStrength) * 230));

            canvas.save();
            canvas.clipPath(pillPath);
            canvas.translate(capsuleRect.left, capsuleRect.top);
            canvas.drawRect(0, 0, w, h, agslHighlightPaint);
            canvas.restore();
        } catch (Throwable ignored) {}
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private boolean drawAgslRefraction(Canvas canvas, Path pillPath, RectF capsuleRect, Bitmap slice, float w, float h, float r, float refractionStrength) {
        try {
            android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) agslRuntimeShader;
            BitmapShader bmpShader = new BitmapShader(slice, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.postScale(w / slice.getWidth(), h / slice.getHeight());
            bmpShader.setLocalMatrix(matrix);

            shader.setInputShader("content", bmpShader);
            shader.setFloatUniform("size", w, h);
            shader.setFloatUniform("offset", 0.0f, 0.0f);
            shader.setFloatUniform("cornerRadii", new float[]{r, r, r, r});
            shader.setFloatUniform("refractionHeight", r * 0.95f);
            shader.setFloatUniform("refractionAmount", -r * 0.50f * Math.max(0.2f, refractionStrength));
            shader.setFloatUniform("depthEffect", 0.25f);
            shader.setFloatUniform("chromaticAberration", 0.35f * refractionStrength);

            canvas.save();
            canvas.clipPath(pillPath);
            canvas.translate(capsuleRect.left, capsuleRect.top);
            canvas.drawRoundRect(0, 0, w, h, r, r, agslPaint);
            canvas.restore();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Draws authentic iPhone Liquid Glass specular highlights: 2 corner crescent arcs,
     * top rim sheen line, and luminous uniform glass perimeter.
     */
    private void drawKyantHighlights(Canvas canvas, RectF capsuleRect, float r, float rimLightStrength) {
        int cornerAlpha = (int) (Math.min(1.0f, rimLightStrength * 1.25f) * 220);
        int borderAlpha = (int) (Math.min(1.0f, rimLightStrength) * 170);

        float h = capsuleRect.height();
        float strokeScale = Math.max(1.0f, h / 36.0f);

        // 1. Full perimeter glass stroke (Uniform luminous translucent white, NO black at bottom)
        borderPaint.setShader(null);
        borderPaint.setColor(Color.argb(borderAlpha, 255, 255, 255));
        borderPaint.setStrokeWidth(1.0f * strokeScale);
        canvas.drawRoundRect(capsuleRect, r, r, borderPaint);

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setShader(null);
        highlightPaint.setColor(Color.argb(cornerAlpha, 255, 255, 255));
        highlightPaint.setStrokeWidth(1.6f * strokeScale);

        // 2. Left corner specular crescent arc (Viền sáng góc trái iPhone)
        float inset = 1.0f * strokeScale;
        RectF leftArcRect = new RectF(
                capsuleRect.left + inset, capsuleRect.top + inset,
                capsuleRect.left + 2 * r - inset, capsuleRect.bottom - inset
        );
        canvas.drawArc(leftArcRect, 110, 140, false, highlightPaint);

        // 3. Right corner specular crescent arc (Viền sáng góc phải iPhone)
        RectF rightArcRect = new RectF(
                capsuleRect.right - 2 * r + inset, capsuleRect.top + inset,
                capsuleRect.right - inset, capsuleRect.bottom - inset
        );
        canvas.drawArc(rightArcRect, 290, 140, false, highlightPaint);

        // 4. Top rim specular sheen line
        highlightPaint.setStrokeWidth(1.2f * strokeScale);
        highlightPaint.setColor(Color.argb((int) (cornerAlpha * 0.90f), 255, 255, 255));
        canvas.drawLine(capsuleRect.left + r, capsuleRect.top + inset, capsuleRect.right - r, capsuleRect.top + inset, highlightPaint);
    }

    /**
     * Executes the exact Photo Watermark 4.0.6 refraction physics, Signed Distance Field,
     * circleMap displacement, and 7-band chromatic dispersion in software.
     */
    public static Bitmap renderSoftwareRefraction(
            Bitmap sourceSlice,
            int targetW,
            int targetH,
            float refractionStrength,
            float blurStrength
    ) {
        if (sourceSlice == null || sourceSlice.isRecycled()) return null;

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
        float refractionHeight = radius * 0.95f;
        float refractionAmount = -radius * 0.50f * Math.max(0.2f, refractionStrength);
        float chromaticAberration = 0.35f * refractionStrength;
        float depthEffect = 0.25f;

        for (int y = 0; y < h; y++) {
            float py = y - halfH;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float px = x - halfW;

                // 1. Photo Watermark sdRoundedRect
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

                    // Photo Watermark gradSdRoundedRect
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

                    // SafeNormalize with depth curvature
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

                // 2. Photo Watermark 7-band Chromatic Dispersion
                float disp = chromaticAberration * ((px * py) / Math.max(halfW * halfH, 0.0001f));
                float dispX = d * gradX * disp;
                float dispY = d * gradY * disp;

                float refX = x + d * gradX;
                float refY = y + d * gradY;

                // Sample 7 spectral bands
                int r1 = sampleChannelR(srcPixels, w, h, refX + dispX, refY + dispY);
                int r2 = sampleChannelR(srcPixels, w, h, refX + dispX * 0.667f, refY + dispY * 0.667f);
                int r3 = sampleChannelR(srcPixels, w, h, refX + dispX * 0.333f, refY + dispY * 0.333f);
                int r7 = sampleChannelR(srcPixels, w, h, refX - dispX, refY - dispY);

                int g2 = sampleChannelG(srcPixels, w, h, refX + dispX * 0.667f, refY + dispY * 0.667f);
                int g3 = sampleChannelG(srcPixels, w, h, refX + dispX * 0.333f, refY + dispY * 0.333f);
                int g4 = sampleChannelG(srcPixels, w, h, refX, refY);
                int g5 = sampleChannelG(srcPixels, w, h, refX - dispX * 0.333f, refY - dispY * 0.333f);

                int b5 = sampleChannelB(srcPixels, w, h, refX - dispX * 0.333f, refY - dispY * 0.333f);
                int b6 = sampleChannelB(srcPixels, w, h, refX - dispX * 0.667f, refY - dispY * 0.667f);
                int b7 = sampleChannelB(srcPixels, w, h, refX - dispX, refY - dispY);

                float redVal = (r1 / 3.5f) + (r2 / 3.5f) + (r3 / 3.5f) + (r7 / 7.0f);
                float greenVal = (g2 / 7.0f) + (g3 / 3.5f) + (g4 / 3.5f) + (g5 / 3.5f);
                float blueVal = (b5 / 3.0f) + (b6 / 3.0f) + (b7 / 3.0f);

                // 3. Specular highlight calculation (45-degree rim normal)
                float dDot = gradX * 0.7071f - gradY * 0.7071f;
                float spec = 0f;
                if (dDot > 0f) {
                    spec = (float) Math.pow(dDot, 16.0) * 160f;
                }

                int finalR = Math.min(255, (int)(redVal * 1.03f + spec + 8));
                int finalG = Math.min(255, (int)(greenVal * 1.03f + spec + 8));
                int finalB = Math.min(255, (int)(blueVal * 1.03f + spec + 12));

                dstPixels[row + x] = (0xFF << 24) | (finalR << 16) | (finalG << 8) | finalB;
            }
        }

        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        output.setPixels(dstPixels, 0, w, 0, 0, w, h);

        if (needsRecycle) {
            input.recycle();
        }

        return output;
    }

    private static int sampleChannelR(int[] pixels, int w, int h, float x, float y) {
        int ix = Math.max(0, Math.min(w - 1, Math.round(x)));
        int iy = Math.max(0, Math.min(h - 1, Math.round(y)));
        return (pixels[iy * w + ix] >> 16) & 0xFF;
    }

    private static int sampleChannelG(int[] pixels, int w, int h, float x, float y) {
        int ix = Math.max(0, Math.min(w - 1, Math.round(x)));
        int iy = Math.max(0, Math.min(h - 1, Math.round(y)));
        return (pixels[iy * w + ix] >> 8) & 0xFF;
    }

    private static int sampleChannelB(int[] pixels, int w, int h, float x, float y) {
        int ix = Math.max(0, Math.min(w - 1, Math.round(x)));
        int iy = Math.max(0, Math.min(h - 1, Math.round(y)));
        return pixels[iy * w + ix] & 0xFF;
    }
}
