package dev.liquidplus.watermark.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import dev.liquidplus.watermark.R;
import dev.liquidplus.watermark.model.WatermarkConfig;

public class LiquidGlassView extends View {

    private WatermarkConfig config = new WatermarkConfig();
    private Paint fillPaint;
    private Paint strokePaint;
    private Paint textTitlePaint;
    private Paint textSubPaint;
    private Paint shadowPaint;

    private RectF capsuleRect = new RectF();
    private Path capsulePath = new Path();

    private Drawable brandDrawable = null;
    private Bitmap customLogoBitmap = null;

    private Typeface fontRegular;
    private Typeface fontMedium;
    private Typeface fontBold;

    // Dragging support
    private float dX, dY;
    private boolean isDragging = false;
    private float touchStartX, touchStartY;

    public LiquidGlassView(Context context) {
        super(context);
        init();
    }

    public LiquidGlassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidGlassView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Load Inter fonts
        try {
            fontRegular = ResourcesCompat.getFont(getContext(), R.font.inter_regular);
            fontMedium = ResourcesCompat.getFont(getContext(), R.font.inter_medium);
            fontBold = ResourcesCompat.getFont(getContext(), R.font.inter_bold);
        } catch (Exception e) {
            fontRegular = Typeface.SANS_SERIF;
            fontMedium = Typeface.DEFAULT_BOLD;
            fontBold = Typeface.DEFAULT_BOLD;
        }

        // 1. Fill paint (Frosted liquid glass translucent fill)
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // 2. Stroke paint (Fresnel optical rim light)
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(1.2f));

        // 3. Shadow paint
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.parseColor("#33000000"));

        // 4. Text paints
        textTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textTitlePaint.setColor(Color.WHITE);
        textTitlePaint.setTextSize(dpToPx(14f));
        textTitlePaint.setTypeface(fontBold);
        textTitlePaint.setShadowLayer(dpToPx(2f), 0, dpToPx(1f), Color.parseColor("#66000000"));

        textSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textSubPaint.setColor(Color.parseColor("#E4E4E7"));
        textSubPaint.setTextSize(dpToPx(10f));
        textSubPaint.setTypeface(fontRegular);
        textSubPaint.setShadowLayer(dpToPx(1.5f), 0, dpToPx(0.8f), Color.parseColor("#66000000"));

        updateBrandLogo();
    }

    public void setConfig(WatermarkConfig config) {
        this.config = config != null ? config : new WatermarkConfig();
        updateBrandLogo();
        requestLayout();
        invalidate();
    }

    public WatermarkConfig getConfig() {
        return this.config;
    }

    public void setCustomLogoBitmap(Bitmap bmp) {
        this.customLogoBitmap = bmp;
        requestLayout();
        invalidate();
    }

    private void updateBrandLogo() {
        if ("custom".equals(config.brandLogo)) {
            brandDrawable = null;
            return;
        }
        int resId;
        switch (config.brandLogo) {
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
        brandDrawable = ContextCompat.getDrawable(getContext(), resId);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        float h = dpToPx(44f);

        // Measure text widths
        float modelWidth = textTitlePaint.measureText(config.deviceModel != null ? config.deviceModel : "");
        float paramsWidth = textSubPaint.measureText(config.cameraParams != null ? config.cameraParams : "");
        float dateWidth = config.showDateTime && config.dateTime != null ? textSubPaint.measureText(config.dateTime) : 0;
        float rightColWidth = Math.max(paramsWidth, dateWidth);

        float logoSize = dpToPx(24f);
        float paddingSide = dpToPx(18f);
        float spacingMiddle = dpToPx(28f);
        float logoMargin = dpToPx(10f);

        float totalW = paddingSide * 2 + logoSize + logoMargin + modelWidth + spacingMiddle + rightColWidth;
        // Minimum capsule width
        totalW = Math.max(totalW, dpToPx(260f));

        setMeasuredDimension((int) Math.ceil(totalW + dpToPx(16f)), (int) Math.ceil(h + dpToPx(12f)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float shadowPadding = dpToPx(4f);
        float w = getWidth() - shadowPadding * 2;
        float h = getHeight() - shadowPadding * 2;
        float r = h / 2.0f; // Perfect pill hemisphere

        capsuleRect.set(shadowPadding, shadowPadding, shadowPadding + w, shadowPadding + h);
        capsulePath.reset();
        capsulePath.addRoundRect(capsuleRect, r, r, Path.Direction.CW);

        // 1. Drop shadow behind the capsule
        canvas.drawRoundRect(capsuleRect.left, capsuleRect.top + dpToPx(2.5f),
                capsuleRect.right, capsuleRect.bottom + dpToPx(2.5f), r, r, shadowPaint);

        // 2. Liquid Glass Backdrop Frosted Gradient
        int alphaTop = (int) (clamp(config.blurStrength * 0.45f + 0.15f, 0.15f, 0.65f) * 255);
        int alphaBottom = (int) (clamp(config.blurStrength * 0.25f + 0.08f, 0.08f, 0.40f) * 255);
        LinearGradient fillGrad = new LinearGradient(
                capsuleRect.left, capsuleRect.top,
                capsuleRect.left, capsuleRect.bottom,
                Color.argb(alphaTop, 255, 255, 255),
                Color.argb(alphaBottom, 255, 255, 255),
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(fillGrad);
        canvas.drawPath(capsulePath, fillPaint);

        // 3. Specular Optical Rim Light (Fresnel reflection along the curved edge)
        int rimAlpha = (int) (config.rimLightStrength * 220);
        LinearGradient rimGrad = new LinearGradient(
                capsuleRect.left, capsuleRect.top,
                capsuleRect.right, capsuleRect.bottom,
                new int[]{
                        Color.argb(rimAlpha, 255, 255, 255),
                        Color.argb((int)(rimAlpha * 0.4f), 255, 255, 255),
                        Color.argb((int)(rimAlpha * 0.7f), 255, 255, 255)
                },
                new float[]{0.0f, 0.6f, 1.0f},
                Shader.TileMode.CLAMP
        );
        strokePaint.setShader(rimGrad);
        canvas.drawPath(capsulePath, strokePaint);

        // 4. Top highlight sheen (Subtle liquid reflection arc inside)
        RectF innerSheen = new RectF(
                capsuleRect.left + dpToPx(3f),
                capsuleRect.top + dpToPx(1.5f),
                capsuleRect.right - dpToPx(3f),
                capsuleRect.top + h * 0.42f
        );
        Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheenPaint.setShader(new LinearGradient(
                innerSheen.left, innerSheen.top,
                innerSheen.left, innerSheen.bottom,
                Color.argb((int)(config.rimLightStrength * 70), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(innerSheen, r, r, sheenPaint);

        // 5. Draw Content inside the Capsule
        float paddingLeft = capsuleRect.left + dpToPx(16f);
        float paddingRight = capsuleRect.right - dpToPx(16f);
        float centerY = capsuleRect.centerY();
        float logoSize = dpToPx(22f);

        boolean isLeft = WatermarkConfig.POSITION_LEFT.equalsIgnoreCase(config.logoPosition);

        // Draw Left: Logo & Device Model
        if (isLeft) {
            float curX = paddingLeft;
            // Draw Logo
            if (customLogoBitmap != null) {
                RectF dest = new RectF(curX, centerY - logoSize / 2f, curX + logoSize, centerY + logoSize / 2f);
                canvas.drawBitmap(customLogoBitmap, null, dest, null);
                curX += logoSize + dpToPx(10f);
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int) curX, (int)(centerY - logoSize / 2f),
                        (int)(curX + logoSize), (int)(centerY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curX += logoSize + dpToPx(10f);
            }

            // Draw Device Model
            if (config.deviceModel != null) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, curX, textY, textTitlePaint);
            }

            // Draw Right Column: Camera Params & Date
            float rightX = paddingRight;
            textSubPaint.setTextAlign(Paint.Align.RIGHT);

            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                // Two lines
                float line1Y = centerY - dpToPx(2f);
                float line2Y = centerY + dpToPx(11f);
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, rightX, line1Y, textSubPaint);
                }
                canvas.drawText(config.dateTime, rightX, line2Y, textSubPaint);
            } else {
                // Single centered line
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, rightX, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);

        } else {
            // Logo on the Right
            float leftX = paddingLeft;
            // Draw Device Model on the Left
            if (config.deviceModel != null) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, leftX, textY, textTitlePaint);
            }

            float curX = paddingRight;
            // Draw Logo on the Right
            if (customLogoBitmap != null) {
                RectF dest = new RectF(curX - logoSize, centerY - logoSize / 2f, curX, centerY + logoSize / 2f);
                canvas.drawBitmap(customLogoBitmap, null, dest, null);
                curX -= (logoSize + dpToPx(10f));
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int)(curX - logoSize), (int)(centerY - logoSize / 2f),
                        (int) curX, (int)(centerY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curX -= (logoSize + dpToPx(10f));
            }

            // Draw Specs to the left of the Logo
            textSubPaint.setTextAlign(Paint.Align.RIGHT);
            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                float line1Y = centerY - dpToPx(2f);
                float line2Y = centerY + dpToPx(11f);
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, curX, line1Y, textSubPaint);
                }
                canvas.drawText(config.dateTime, curX, line2Y, textSubPaint);
            } else {
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null) {
                    canvas.drawText(config.cameraParams, curX, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dX = getX() - event.getRawX();
                dY = getY() - event.getRawY();
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float diffX = Math.abs(event.getRawX() - touchStartX);
                float diffY = Math.abs(event.getRawY() - touchStartY);
                if (diffX > 10 || diffY > 10) {
                    isDragging = true;
                }
                if (isDragging) {
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) {
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;
                        // Clamp inside parent container
                        newX = Math.max(0, Math.min(newX, parent.getWidth() - getWidth()));
                        newY = Math.max(0, Math.min(newY, parent.getHeight() - getHeight()));
                        setX(newX);
                        setY(newY);

                        // Update ratio in config
                        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
                            config.posXRatio = (newX + getWidth() / 2f) / parent.getWidth();
                            config.posYRatio = (newY + getHeight() / 2f) / parent.getHeight();
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                return true;
        }
        return super.onTouchEvent(event);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
