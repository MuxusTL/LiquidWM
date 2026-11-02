package dev.liquidplus.watermark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.liquidplus.watermark.engine.ExifReader;
import dev.liquidplus.watermark.engine.LiquidGlassRenderer;
import dev.liquidplus.watermark.engine.VideoWatermarkProcessor;
import dev.liquidplus.watermark.model.PresetStore;
import dev.liquidplus.watermark.model.WatermarkConfig;
import dev.liquidplus.watermark.model.WatermarkPreset;
import dev.liquidplus.watermark.ui.adapter.BrandLogoAdapter;
import dev.liquidplus.watermark.ui.view.LiquidGlassView;
import dev.liquidplus.watermark.util.LocaleHelper;

public class EditorActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_URI = "extra_media_uri";
    public static final String EXTRA_MEDIA_TYPE = "extra_media_type"; // "photo" or "video"
    public static final String EXTRA_PRESET = "extra_preset";

    private Uri mediaUri = null;
    private String mediaType = "photo";
    private Bitmap sourceBitmap = null;
    private Bitmap customLogoBitmap = null;

    private WatermarkConfig config;
    private PresetStore presetStore;

    // Views
    private ImageView ivPreviewMedia;
    private LiquidGlassView liquidGlassWatermark;
    private AppCompatButton btnTogglePreview;
    private TextView tvLogoStatus;
    private TextView btnPosLeft, btnPosRight;
    private EditText etDeviceModel, etCameraParams, etDateTime;
    private SeekBar sbBlur, sbRefraction, sbRimLight;
    private AppCompatButton btnExportFile;

    private boolean isPreviewPaused = false;
    private BrandLogoAdapter logoAdapter;

    private final ActivityResultLauncher<String> customLogoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        customLogoBitmap = BitmapFactory.decodeStream(in);
                        config.brandLogo = "custom";
                        config.customLogoUri = uri.toString();
                        tvLogoStatus.setText(getString(R.string.custom_image_selected, "Tệp tùy chỉnh"));
                        liquidGlassWatermark.setCustomLogoBitmap(customLogoBitmap);
                        liquidGlassWatermark.setConfig(config);
                        if (logoAdapter != null) logoAdapter.setSelectedKey("");
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Không thể tải logo", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        presetStore = new PresetStore(this);

        // Get intent params
        String uriStr = getIntent().getStringExtra(EXTRA_MEDIA_URI);
        if (uriStr != null) mediaUri = Uri.parse(uriStr);

        String typeStr = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        if (typeStr != null) mediaType = typeStr;

        WatermarkPreset preset = (WatermarkPreset) getIntent().getSerializableExtra(EXTRA_PRESET);
        if (preset != null && preset.config != null) {
            config = preset.config;
        } else {
            config = presetStore.getActiveConfig();
        }

        initViews();
        loadMedia();
        setupControls();
    }

    private void initViews() {
        findViewById(R.id.btnBackEditor).setOnClickListener(v -> finish());

        ivPreviewMedia = findViewById(R.id.ivPreviewMedia);
        liquidGlassWatermark = findViewById(R.id.liquidGlassWatermark);
        btnTogglePreview = findViewById(R.id.btnTogglePreview);
        tvLogoStatus = findViewById(R.id.tvLogoStatus);

        btnPosLeft = findViewById(R.id.btnPosLeft);
        btnPosRight = findViewById(R.id.btnPosRight);

        etDeviceModel = findViewById(R.id.etDeviceModel);
        etCameraParams = findViewById(R.id.etCameraParams);
        etDateTime = findViewById(R.id.etDateTime);

        sbBlur = findViewById(R.id.sbBlur);
        sbRefraction = findViewById(R.id.sbRefraction);
        sbRimLight = findViewById(R.id.sbRimLight);

        btnExportFile = findViewById(R.id.btnExportFile);

        // Setup horizontal Brand Logos
        RecyclerView rvBrandLogos = findViewById(R.id.rvBrandLogos);
        rvBrandLogos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        logoAdapter = new BrandLogoAdapter(this, config.brandLogo, brandKey -> {
            config.brandLogo = brandKey;
            customLogoBitmap = null;
            liquidGlassWatermark.setCustomLogoBitmap(null);
            liquidGlassWatermark.setConfig(config);
            tvLogoStatus.setText(getString(R.string.custom_image_selected, brandKey.toUpperCase(Locale.US)));
        });
        rvBrandLogos.setAdapter(logoAdapter);

        // Plus button for custom image
        findViewById(R.id.btnAddCustomLogo).setOnClickListener(v -> customLogoPicker.launch("image/*"));
    }

    private void loadMedia() {
        if (mediaUri != null && "photo".equals(mediaType)) {
            try (InputStream in = getContentResolver().openInputStream(mediaUri)) {
                sourceBitmap = BitmapFactory.decodeStream(in);
                ivPreviewMedia.setImageBitmap(sourceBitmap);

                // Auto-read EXIF metadata
                ExifReader.ExifData exif = ExifReader.readFromUri(this, mediaUri);
                if (exif.deviceModel != null && !exif.deviceModel.isEmpty()) {
                    config.deviceModel = exif.deviceModel;
                }
                if (exif.cameraParams != null && !exif.cameraParams.isEmpty()) {
                    config.cameraParams = exif.cameraParams;
                }
                if (exif.dateTime != null && !exif.dateTime.isEmpty()) {
                    config.dateTime = exif.dateTime;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Fallback: load sample landscape image
        if (sourceBitmap == null) {
            try (InputStream in = getAssets().open("sample_landscape.jpg")) {
                sourceBitmap = BitmapFactory.decodeStream(in);
                ivPreviewMedia.setImageBitmap(sourceBitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Apply config to Watermark View
        if (sourceBitmap != null) {
            liquidGlassWatermark.setBackdropBitmap(sourceBitmap);
        }
        liquidGlassWatermark.setConfig(config);
    }

    private void setupControls() {
        // Device Model input
        etDeviceModel.setText(config.deviceModel);
        etDeviceModel.addTextChangedListener(new SimpleWatcher(s -> {
            config.deviceModel = s;
            liquidGlassWatermark.setConfig(config);
        }));

        findViewById(R.id.btnDetectDevice).setOnClickListener(v -> {
            String detected = Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.US) +
                    Build.MANUFACTURER.substring(1) + " " + Build.MODEL;
            etDeviceModel.setText(detected);
        });

        // Camera Specs input
        etCameraParams.setText(config.cameraParams);
        etCameraParams.addTextChangedListener(new SimpleWatcher(s -> {
            config.cameraParams = s;
            liquidGlassWatermark.setConfig(config);
        }));

        findViewById(R.id.btnReadExif).setOnClickListener(v -> {
            if (mediaUri != null) {
                ExifReader.ExifData exif = ExifReader.readFromUri(this, mediaUri);
                if (exif.cameraParams != null && !exif.cameraParams.isEmpty()) {
                    etCameraParams.setText(exif.cameraParams);
                    Toast.makeText(this, "Đã đọc thông số EXIF từ ảnh!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(this, "Ảnh không có dữ liệu EXIF hoặc đang dùng ảnh mẫu", Toast.LENGTH_SHORT).show();
        });

        // Date input
        etDateTime.setText(config.dateTime);
        etDateTime.addTextChangedListener(new SimpleWatcher(s -> {
            config.dateTime = s;
            liquidGlassWatermark.setConfig(config);
        }));

        // Position Segments
        updatePositionButtons();
        btnPosLeft.setOnClickListener(v -> {
            config.logoPosition = WatermarkConfig.POSITION_LEFT;
            updatePositionButtons();
            liquidGlassWatermark.setConfig(config);
        });
        btnPosRight.setOnClickListener(v -> {
            config.logoPosition = WatermarkConfig.POSITION_RIGHT;
            updatePositionButtons();
            liquidGlassWatermark.setConfig(config);
        });

        // Sliders
        sbBlur.setProgress((int)(config.blurStrength * 100));
        sbBlur.setOnSeekBarChangeListener(new SimpleSeekWatcher(p -> {
            config.blurStrength = p / 100f;
            liquidGlassWatermark.setConfig(config);
        }));

        sbRefraction.setProgress((int)(config.refractionStrength * 100));
        sbRefraction.setOnSeekBarChangeListener(new SimpleSeekWatcher(p -> {
            config.refractionStrength = p / 100f;
            liquidGlassWatermark.setConfig(config);
        }));

        sbRimLight.setProgress((int)(config.rimLightStrength * 100));
        sbRimLight.setOnSeekBarChangeListener(new SimpleSeekWatcher(p -> {
            config.rimLightStrength = p / 100f;
            liquidGlassWatermark.setConfig(config);
        }));

        // Pause/Resume Preview
        btnTogglePreview.setOnClickListener(v -> {
            isPreviewPaused = !isPreviewPaused;
            liquidGlassWatermark.setVisibility(isPreviewPaused ? View.INVISIBLE : View.VISIBLE);
            btnTogglePreview.setText(isPreviewPaused ? R.string.btn_resume_preview : R.string.btn_pause_preview);
        });

        // Export Button
        btnExportFile.setOnClickListener(v -> handleExport());
    }

    private void updatePositionButtons() {
        boolean isLeft = WatermarkConfig.POSITION_LEFT.equalsIgnoreCase(config.logoPosition);
        btnPosLeft.setBackgroundResource(isLeft ? R.drawable.bg_segmented_active : R.drawable.bg_segmented_inactive);
        btnPosLeft.setTextColor(getColor(isLeft ? R.color.btn_black_text : R.color.btn_light_text));

        btnPosRight.setBackgroundResource(!isLeft ? R.drawable.bg_segmented_active : R.drawable.bg_segmented_inactive);
        btnPosRight.setTextColor(getColor(!isLeft ? R.color.btn_black_text : R.color.btn_light_text));
    }

    private void handleExport() {
        btnExportFile.setEnabled(false);
        btnExportFile.setText(R.string.exporting);

        if ("video".equals(mediaType) && mediaUri != null) {
            // Video Export
            VideoWatermarkProcessor.processVideo(this, mediaUri, config, customLogoBitmap, new VideoWatermarkProcessor.VideoProcessCallback() {
                @Override
                public void onProgress(int progressPercent) {
                    runOnUiThread(() -> btnExportFile.setText("Đang xuất video (" + progressPercent + "%)..."));
                }

                @Override
                public void onComplete(Uri outputUri) {
                    runOnUiThread(() -> {
                        btnExportFile.setEnabled(true);
                        btnExportFile.setText(R.string.btn_export);
                        Toast.makeText(EditorActivity.this, R.string.export_success, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        btnExportFile.setEnabled(true);
                        btnExportFile.setText(R.string.btn_export);
                        Toast.makeText(EditorActivity.this, getString(R.string.export_failed, message), Toast.LENGTH_SHORT).show();
                    });
                }
            });

        } else {
            // Photo Export
            new Thread(() -> {
                try {
                    Bitmap targetBitmap = sourceBitmap;
                    if (mediaUri != null) {
                        try (InputStream in = getContentResolver().openInputStream(mediaUri)) {
                            targetBitmap = BitmapFactory.decodeStream(in);
                        }
                    }

                    Bitmap rendered = LiquidGlassRenderer.renderWatermark(this, targetBitmap, config, customLogoBitmap);
                    Uri savedUri = LiquidGlassRenderer.saveToGallery(this, rendered, 100);

                    runOnUiThread(() -> {
                        btnExportFile.setEnabled(true);
                        btnExportFile.setText(R.string.btn_export);
                        if (savedUri != null) {
                            Toast.makeText(this, R.string.export_success, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Không thể lưu ảnh vào thư viện", Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        btnExportFile.setEnabled(true);
                        btnExportFile.setText(R.string.btn_export);
                        Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Persist active config
        if (presetStore != null && config != null) {
            presetStore.setActiveConfig(config);
        }
    }

    private static class SimpleWatcher implements TextWatcher {
        interface OnChanged { void call(String s); }
        private final OnChanged callback;
        SimpleWatcher(OnChanged callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.call(s.toString());
        }
        @Override public void afterTextChanged(Editable s) {}
    }

    private static class SimpleSeekWatcher implements SeekBar.OnSeekBarChangeListener {
        interface OnChanged { void call(int p); }
        private final OnChanged callback;
        SimpleSeekWatcher(OnChanged callback) { this.callback = callback; }
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) callback.call(progress);
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
