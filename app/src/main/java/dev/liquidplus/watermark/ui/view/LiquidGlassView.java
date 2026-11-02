package dev.liquidplus.watermark.ui.view;

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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import dev.liquidplus.watermark.R;
import dev.liquidplus.watermark.engine.KyantGlassEngine;
import dev.liquidplus.watermark.model.WatermarkConfig;

public class LiquidGlassView extends View {

    private WatermarkConfig config = new WatermarkConfig();
    private KyantGlassEngine glassEngine;

    private Paint textTitlePaint;
    private Paint textSubPaint;

    private RectF capsuleRect = new RectF();
    private RectF backdropCropRect = new RectF();

    private Drawable brandDrawable = null;
    private Bitmap customLogoBitmap = null;
    private Bitmap backdropBitmap = null;

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
        glassEngine = new KyantGlassEngine();

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

        // Text paints - crisp white typography with subtle optical shadow
        textTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textTitlePaint.setColor(Color.WHITE);
        textTitlePaint.setTextSize(dpToPx(12f));
        textTitlePaint.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
        textTitlePaint.setShadowLayer(dpToPx(1.8f), 0, dpToPx(0.8f), Color.parseColor("#80000000"));

        textSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textSubPaint.setColor(Color.WHITE);
        textSubPaint.setTextSize(dpToPx(9.2f));
        textSubPaint.setTypeface(fontMedium != null ? fontMedium : Typeface.DEFAULT);
        textSubPaint.setShadowLayer(dpToPx(1.4f), 0, dpToPx(0.7f), Color.parseColor("#80000000"));

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

    public void setBackdropBitmap(Bitmap bmp) {
        this.backdropBitmap = bmp;
        invalidate();
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
        // Slim, elegant capsule height matching user's reference image
        float h = dpToPx(34f);

        // Measure text widths
        float modelWidth = textTitlePaint.measureText(config.deviceModel != null ? config.deviceModel : "");
        float paramsWidth = textSubPaint.measureText(config.cameraParams != null ? config.cameraParams : "");
        float dateWidth = config.showDateTime && config.dateTime != null ? textSubPaint.measureText(config.dateTime) : 0;
        float rightColWidth = Math.max(paramsWidth, dateWidth);

        float logoSize = dpToPx(16f);
        float paddingSide = dpToPx(14f);
        float spacingMiddle = dpToPx(20f);
        float logoMargin = dpToPx(7f);

        float totalW = paddingSide * 2 + logoSize + logoMargin + modelWidth + spacingMiddle + rightColWidth;
        totalW = Math.max(totalW, dpToPx(210f));

        // Add small padding for drop shadow
        setMeasuredDimension((int) Math.ceil(totalW + dpToPx(12f)), (int) Math.ceil(h + dpToPx(10f)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float shadowPaddingX = dpToPx(4f);
        float shadowPaddingY = dpToPx(3f);
        float w = getWidth() - shadowPaddingX * 2;
        float h = getHeight() - shadowPaddingY * 2;
        float r = h / 2.0f; // Perfect pill hemisphere

        capsuleRect.set(shadowPaddingX, shadowPaddingY, shadowPaddingX + w, shadowPaddingY + h);

        // Calculate backdrop crop rect relative to parent container
        calculateBackdropCrop(capsuleRect);

        // 1. Draw Liquid Glass Refraction & Highlights using Kyant0 Engine
        glassEngine.drawLiquidGlass(
                canvas,
                capsuleRect,
                backdropBitmap,
                backdropCropRect,
                config.blurStrength,
                config.refractionStrength,
                config.rimLightStrength
        );

        // 2. Draw Content inside the Capsule (Brand, Device, Camera Params, Date)
        float paddingLeft = capsuleRect.left + dpToPx(13f);
        float paddingRight = capsuleRect.right - dpToPx(13f);
        float centerY = capsuleRect.centerY();
        float logoSize = dpToPx(16f);

        boolean isLeft = WatermarkConfig.POSITION_LEFT.equalsIgnoreCase(config.logoPosition);

        if (isLeft) {
            float curX = paddingLeft;
            // Draw Logo
            if (customLogoBitmap != null) {
                RectF dest = new RectF(curX, centerY - logoSize / 2f, curX + logoSize, centerY + logoSize / 2f);
                canvas.drawBitmap(customLogoBitmap, null, dest, null);
                curX += logoSize + dpToPx(7f);
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int) curX, (int)(centerY - logoSize / 2f),
                        (int)(curX + logoSize), (int)(centerY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curX += logoSize + dpToPx(7f);
            }

            // Draw Device Model
            if (config.deviceModel != null && !config.deviceModel.isEmpty()) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, curX, textY, textTitlePaint);
            }

            // Draw Right Column: Camera Params & Date
            float rightX = paddingRight;
            textSubPaint.setTextAlign(Paint.Align.RIGHT);

            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                // Two compact lines
                float line1Y = centerY - dpToPx(1.8f);
                float line2Y = centerY + dpToPx(9.0f);
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, rightX, line1Y, textSubPaint);
                }
                textSubPaint.setColor(Color.parseColor("#E0E0E0"));
                canvas.drawText(config.dateTime, rightX, line2Y, textSubPaint);
                textSubPaint.setColor(Color.WHITE);
            } else {
                // Single centered line
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, rightX, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);

        } else {
            // Logo on the Right
            float leftX = paddingLeft;
            if (config.deviceModel != null && !config.deviceModel.isEmpty()) {
                Paint.FontMetrics fm = textTitlePaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(config.deviceModel, leftX, textY, textTitlePaint);
            }

            float curRight = paddingRight;
            if (customLogoBitmap != null) {
                RectF dest = new RectF(curRight - logoSize, centerY - logoSize / 2f, curRight, centerY + logoSize / 2f);
                canvas.drawBitmap(customLogoBitmap, null, dest, null);
                curRight -= (logoSize + dpToPx(7f));
            } else if (brandDrawable != null) {
                brandDrawable.setBounds((int)(curRight - logoSize), (int)(centerY - logoSize / 2f),
                        (int) curRight, (int)(centerY + logoSize / 2f));
                brandDrawable.draw(canvas);
                curRight -= (logoSize + dpToPx(7f));
            }

            textSubPaint.setTextAlign(Paint.Align.RIGHT);
            if (config.showDateTime && config.dateTime != null && !config.dateTime.isEmpty()) {
                float line1Y = centerY - dpToPx(1.8f);
                float line2Y = centerY + dpToPx(9.0f);
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, curRight, line1Y, textSubPaint);
                }
                textSubPaint.setColor(Color.parseColor("#E0E0E0"));
                canvas.drawText(config.dateTime, curRight, line2Y, textSubPaint);
                textSubPaint.setColor(Color.WHITE);
            } else {
                Paint.FontMetrics fm = textSubPaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2f;
                if (config.cameraParams != null && !config.cameraParams.isEmpty()) {
                    canvas.drawText(config.cameraParams, curRight, textY, textSubPaint);
                }
            }
            textSubPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void calculateBackdropCrop(RectF capsule) {
        if (backdropBitmap == null || backdropBitmap.isRecycled()) return;
        View parent = (View) getParent();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) return;

        float parentW = parent.getWidth();
        float parentH = parent.getHeight();
        float bmpW = backdropBitmap.getWidth();
        float bmpH = backdropBitmap.getHeight();

        // Calculate aspect fill / center crop scale
        float scale = Math.max(parentW / bmpW, parentH / bmpH);
        float scaledW = bmpW * scale;
        float scaledH = bmpH * scale;
        float dx = (parentW - scaledW) / 2.0f;
        float dy = (parentH - scaledH) / 2.0f;

        // View coordinate in parent
        float viewX = getX() + capsule.left;
        float viewY = getY() + capsule.top;

        // Map to bitmap pixel coordinate
        float cropL = (viewX - dx) / scale;
        float cropT = (viewY - dy) / scale;
        float cropW = capsule.width() / scale;
        float cropH = capsule.height() / scale;

        backdropCropRect.set(cropL, cropT, cropL + cropW, cropT + cropH);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                dX = getX() - touchStartX;
                dY = getY() - touchStartY;
                isDragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float diffX = Math.abs(event.getRawX() - touchStartX);
                float diffY = Math.abs(event.getRawY() - touchStartY);
                if (diffX > 6 || diffY > 6) {
                    isDragging = true;
                }
                if (isDragging) {
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) {
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
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                isDragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
