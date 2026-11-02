package dev.liquidplus.watermark;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.os.Build;
import java.util.List;
import java.util.Locale;

import dev.liquidplus.watermark.engine.LiquidGlassRenderer;
import dev.liquidplus.watermark.engine.VideoWatermarkProcessor;
import dev.liquidplus.watermark.model.PresetStore;
import dev.liquidplus.watermark.model.WatermarkConfig;
import dev.liquidplus.watermark.model.WatermarkPreset;
import dev.liquidplus.watermark.ui.adapter.PresetAdapter;
import dev.liquidplus.watermark.ui.dialog.IntroDialog;
import dev.liquidplus.watermark.util.ExifReader;
import dev.liquidplus.watermark.util.LocaleHelper;

public class MainActivity extends AppCompatActivity {

    private PresetStore presetStore;
    private FrameLayout fragmentContainer;

    // Bottom Navigation Views
    private LinearLayout tabCreate, tabPresets, tabExplore, tabSettings;
    private FrameLayout tabPillCreate, tabPillPresets, tabPillExplore, tabPillSettings;
    private ImageView iconCreate, iconPresets, iconExplore, iconSettings;
    private TextView textCreate, textPresets, textExplore, textSettings;

    private int currentTab = 0; // 0 = Create, 1 = Presets, 2 = Explore, 3 = Settings

    // Media Launchers
    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Intent intent = new Intent(this, EditorActivity.class);
                    intent.putExtra(EditorActivity.EXTRA_MEDIA_URI, uri.toString());
                    intent.putExtra(EditorActivity.EXTRA_MEDIA_TYPE, "photo");
                    startActivity(intent);
                }
            });

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Intent intent = new Intent(this, EditorActivity.class);
                    intent.putExtra(EditorActivity.EXTRA_MEDIA_URI, uri.toString());
                    intent.putExtra(EditorActivity.EXTRA_MEDIA_TYPE, "video");
                    startActivity(intent);
                }
            });

    private final ActivityResultLauncher<String> quickPhotoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), this::handleQuickPhotos);

    private final ActivityResultLauncher<String> quickVideoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    handleQuickVideo(uri);
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        presetStore = new PresetStore(this);
        fragmentContainer = findViewById(R.id.fragmentContainer);

        initNavBars();
        switchTab(0); // Default to Create

        // Show introduction dialog on first launch
        if (presetStore.isFirstLaunch()) {
            new IntroDialog(this, () -> presetStore.setFirstLaunchDone()).show();
        }
    }

    private void initNavBars() {
        tabCreate = findViewById(R.id.tabCreate);
        tabPresets = findViewById(R.id.tabPresets);
        tabExplore = findViewById(R.id.tabExplore);
        tabSettings = findViewById(R.id.tabSettings);

        tabPillCreate = findViewById(R.id.tabPillCreate);
        tabPillPresets = findViewById(R.id.tabPillPresets);
        tabPillExplore = findViewById(R.id.tabPillExplore);
        tabPillSettings = findViewById(R.id.tabPillSettings);

        iconCreate = findViewById(R.id.iconCreate);
        iconPresets = findViewById(R.id.iconPresets);
        iconExplore = findViewById(R.id.iconExplore);
        iconSettings = findViewById(R.id.iconSettings);

        textCreate = findViewById(R.id.textCreate);
        textPresets = findViewById(R.id.textPresets);
        textExplore = findViewById(R.id.textExplore);
        textSettings = findViewById(R.id.textSettings);

        tabCreate.setOnClickListener(v -> switchTab(0));
        tabPresets.setOnClickListener(v -> switchTab(1));
        tabExplore.setOnClickListener(v -> switchTab(2));
        tabSettings.setOnClickListener(v -> switchTab(3));
    }

    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        resetNavStyles();

        fragmentContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        int activeColor = ContextCompat.getColor(this, R.color.text_primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);

        switch (tabIndex) {
            case 0: // Sáng tạo / Create
                tabPillCreate.setBackgroundResource(R.drawable.bg_nav_pill_active);
                iconCreate.setColorFilter(activeColor);
                textCreate.setTextColor(activeColor);
                textCreate.setTypeface(null, android.graphics.Typeface.BOLD);

                View viewHome = inflater.inflate(R.layout.fragment_home, fragmentContainer, false);
                setupHomeTab(viewHome);
                fragmentContainer.addView(viewHome);
                break;

            case 1: // Kho cấu hình / Presets
                tabPillPresets.setBackgroundResource(R.drawable.bg_nav_pill_active);
                iconPresets.setColorFilter(activeColor);
                textPresets.setTextColor(activeColor);
                textPresets.setTypeface(null, android.graphics.Typeface.BOLD);

                View viewPresets = inflater.inflate(R.layout.fragment_presets, fragmentContainer, false);
                setupPresetsTab(viewPresets);
                fragmentContainer.addView(viewPresets);
                break;

            case 2: // Khám phá / Explore
                tabPillExplore.setBackgroundResource(R.drawable.bg_nav_pill_active);
                iconExplore.setColorFilter(activeColor);
                textExplore.setTextColor(activeColor);
                textExplore.setTypeface(null, android.graphics.Typeface.BOLD);

                View viewExplore = inflater.inflate(R.layout.fragment_explore, fragmentContainer, false);
                setupExploreTab(viewExplore);
                fragmentContainer.addView(viewExplore);
                break;

            case 3: // Của tôi / Cài đặt
                tabPillSettings.setBackgroundResource(R.drawable.bg_nav_pill_active);
                iconSettings.setColorFilter(activeColor);
                textSettings.setTextColor(activeColor);
                textSettings.setTypeface(null, android.graphics.Typeface.BOLD);

                View viewSettings = inflater.inflate(R.layout.fragment_settings, fragmentContainer, false);
                setupSettingsTab(viewSettings);
                fragmentContainer.addView(viewSettings);
                break;
        }
    }

    private void resetNavStyles() {
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);

        tabPillCreate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabPillPresets.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabPillExplore.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tabPillSettings.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        iconCreate.setColorFilter(inactiveColor);
        iconPresets.setColorFilter(inactiveColor);
        iconExplore.setColorFilter(inactiveColor);
        iconSettings.setColorFilter(inactiveColor);

        textCreate.setTextColor(inactiveColor);
        textPresets.setTextColor(inactiveColor);
        textExplore.setTextColor(inactiveColor);
        textSettings.setTextColor(inactiveColor);

        textCreate.setTypeface(null, android.graphics.Typeface.NORMAL);
        textPresets.setTypeface(null, android.graphics.Typeface.NORMAL);
        textExplore.setTypeface(null, android.graphics.Typeface.NORMAL);
        textSettings.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    // --- Tab 1: Home / Create ---
    private void setupHomeTab(View root) {
        AppCompatButton btnSelectPhoto = root.findViewById(R.id.btnSelectPhoto);
        AppCompatButton btnSelectVideo = root.findViewById(R.id.btnSelectVideo);
        AppCompatButton btnQuickPhoto = root.findViewById(R.id.btnQuickPhoto);
        AppCompatButton btnQuickVideo = root.findViewById(R.id.btnQuickVideo);
        View btnToolMyPresets = root.findViewById(R.id.btnToolMyPresets);
        View btnToolExplore = root.findViewById(R.id.btnToolExplore);
        TextView tvPresetsCountDesc = root.findViewById(R.id.tvPresetsCountDesc);

        int count = presetStore.getPresets().size();
        tvPresetsCountDesc.setText(getString(R.string.tool_my_presets_desc, count));

        btnSelectPhoto.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        btnSelectVideo.setOnClickListener(v -> videoPickerLauncher.launch("video/*"));

        if (btnQuickPhoto != null) {
            btnQuickPhoto.setOnClickListener(v -> quickPhotoPickerLauncher.launch("image/*"));
        }
        if (btnQuickVideo != null) {
            btnQuickVideo.setOnClickListener(v -> quickVideoPickerLauncher.launch("video/*"));
        }

        btnToolMyPresets.setOnClickListener(v -> switchTab(1));
        btnToolExplore.setOnClickListener(v -> switchTab(2));
    }

    private WatermarkConfig getQuickWatermarkConfig() {
        WatermarkConfig config = presetStore.getActiveConfig();
        if (config == null) {
            config = new WatermarkConfig();
        }
        if (config.deviceModel == null || config.deviceModel.isEmpty()) {
            String mfg = (Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.US) : "");
            config.deviceModel = (Build.MANUFACTURER != null && !Build.MANUFACTURER.isEmpty() ?
                    Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.US) + Build.MANUFACTURER.substring(1) : "") + " " + Build.MODEL;
            if (mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco")) {
                config.brandLogo = "xiaomi";
            } else if (mfg.contains("apple")) {
                config.brandLogo = "apple";
            } else if (mfg.contains("samsung")) {
                config.brandLogo = "samsung";
            } else if (mfg.contains("sony")) {
                config.brandLogo = "sony";
            } else if (mfg.contains("huawei") || mfg.contains("honor")) {
                config.brandLogo = "huawei";
            } else if (mfg.contains("google")) {
                config.brandLogo = "google";
            } else if (mfg.contains("oppo")) {
                config.brandLogo = "oppo";
            } else if (mfg.contains("vivo") || mfg.contains("iqoo")) {
                config.brandLogo = "vivo";
            } else if (mfg.contains("oneplus")) {
                config.brandLogo = "hasselblad";
            }
        }
        return config;
    }

    private void handleQuickPhotos(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_processing_title)
                .setMessage(getString(R.string.quick_processing_photo, 1, uris.size()))
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            int total = uris.size();
            int successCount = 0;

            for (int i = 0; i < total; i++) {
                Uri uri = uris.get(i);
                int currentIdx = i + 1;
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.setMessage(getString(R.string.quick_processing_photo, currentIdx, total));
                    }
                });

                try {
                    WatermarkConfig baseConfig = getQuickWatermarkConfig();
                    WatermarkConfig photoConfig = WatermarkConfig.fromJson(baseConfig.toJson());

                    ExifReader.ExifData exif = ExifReader.readFromUri(this, uri);
                    if (exif.cameraParams != null && !exif.cameraParams.isEmpty()) {
                        photoConfig.cameraParams = exif.cameraParams;
                    }
                    if (exif.dateTime != null && !exif.dateTime.isEmpty()) {
                        photoConfig.dateTime = exif.dateTime;
                        photoConfig.showDateTime = true;
                    }

                    Bitmap orientedBmp = ExifReader.loadOrientedBitmap(this, uri);
                    if (orientedBmp != null) {
                        Bitmap watermarked = LiquidGlassRenderer.renderWatermark(this, orientedBmp, photoConfig, null);
                        Uri saved = LiquidGlassRenderer.saveToGallery(this, watermarked, 100);
                        if (saved != null) {
                            successCount++;
                        }
                        if (watermarked != orientedBmp) {
                            watermarked.recycle();
                        }
                        orientedBmp.recycle();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            final int finalSuccess = successCount;
            runOnUiThread(() -> {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (finalSuccess > 0) {
                    Toast.makeText(MainActivity.this, getString(R.string.quick_photo_success, finalSuccess), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Không thể xử lý ảnh đã chọn", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void handleQuickVideo(Uri videoUri) {
        if (videoUri == null) return;

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_processing_title)
                .setMessage(getString(R.string.quick_processing_video, 0))
                .setCancelable(false)
                .create();
        progressDialog.show();

        WatermarkConfig config = getQuickWatermarkConfig();

        VideoWatermarkProcessor.processVideo(this, videoUri, config, null, new VideoWatermarkProcessor.VideoProcessCallback() {
            @Override
            public void onProgress(int progressPercent) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.setMessage(getString(R.string.quick_processing_video, progressPercent));
                    }
                });
            }

            @Override
            public void onComplete(Uri outputUri) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, R.string.quick_video_success, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, getString(R.string.export_failed, message), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // --- Tab 2: Presets Library ---
    private void setupPresetsTab(View root) {
        root.findViewById(R.id.btnBackPresets).setOnClickListener(v -> switchTab(0));

        AppCompatButton btnOpenWatermarkSettings = root.findViewById(R.id.btnOpenWatermarkSettings);
        AppCompatButton btnSaveCurrentPreset = root.findViewById(R.id.btnSaveCurrentPreset);
        AppCompatButton btnImportCode = root.findViewById(R.id.btnImportCode);
        TextView tvSavedCountRatio = root.findViewById(R.id.tvSavedCountRatio);

        RecyclerView rvSavedPresets = root.findViewById(R.id.rvSavedPresets);
        View layoutEmptyPresets = root.findViewById(R.id.layoutEmptyPresets);

        rvSavedPresets.setLayoutManager(new LinearLayoutManager(this));

        List<WatermarkPreset> presetList = presetStore.getPresets();
        tvSavedCountRatio.setText(getString(R.string.preset_manage_count, presetList.size(), 30));

        PresetAdapter adapter = new PresetAdapter(this, presetList, new PresetAdapter.OnPresetActionListener() {
            @Override
            public void onApply(WatermarkPreset preset) {
                presetStore.setActiveConfig(preset.config);
                Intent intent = new Intent(MainActivity.this, EditorActivity.class);
                intent.putExtra(EditorActivity.EXTRA_PRESET, preset);
                startActivity(intent);
            }

            @Override
            public void onShare(WatermarkPreset preset) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Liquid Watermark Share Code", preset.shareCode);
                if (clipboard != null) clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, getString(R.string.toast_code_copied, preset.shareCode), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(WatermarkPreset preset) {
                presetStore.deletePreset(preset.id);
                switchTab(1); // Refresh
                Toast.makeText(MainActivity.this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
            }
        });

        rvSavedPresets.setAdapter(adapter);
        layoutEmptyPresets.setVisibility(presetList.isEmpty() ? View.VISIBLE : View.GONE);

        btnOpenWatermarkSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EditorActivity.class);
            startActivity(intent);
        });

        btnSaveCurrentPreset.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_enter_preset_name);
            final EditText input = new EditText(this);
            input.setHint("Tên cấu hình (vd: Chụp phong cảnh)");
            builder.setView(input);
            builder.setPositiveButton(R.string.btn_save, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = "Cấu hình tùy chỉnh";
                presetStore.addPreset(name, presetStore.getActiveConfig());
                switchTab(1); // Refresh
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton(R.string.btn_cancel, null);
            builder.show();
        });

        btnImportCode.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_enter_share_code);
            final EditText input = new EditText(this);
            input.setHint("Nhập mã (vd: 849204821)");
            builder.setView(input);
            builder.setPositiveButton(R.string.btn_apply, (dialog, which) -> {
                String code = input.getText().toString().trim();
                WatermarkPreset found = presetStore.findByCode(code);
                if (found != null) {
                    presetStore.setActiveConfig(found.config);
                    Toast.makeText(this, R.string.toast_applied, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, EditorActivity.class);
                    intent.putExtra(EditorActivity.EXTRA_PRESET, found);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton(R.string.btn_cancel, null);
            builder.show();
        });
    }

    // --- Tab 3: Explore ---
    private void setupExploreTab(View root) {
        root.findViewById(R.id.btnApplyLeica).setOnClickListener(v -> applyExploreStyle("leica", "Xiaomi 14 Ultra", "23mm · f/1.63 · ISO 50"));
        root.findViewById(R.id.btnApplyHasselblad).setOnClickListener(v -> applyExploreStyle("hasselblad", "OnePlus 12", "70mm · f/2.6 · ISO 100"));
        root.findViewById(R.id.btnApplyApple).setOnClickListener(v -> applyExploreStyle("apple", "iPhone 16 Pro", "24mm · f/1.78 · ISO 80"));
    }

    private void applyExploreStyle(String logo, String model, String specs) {
        WatermarkConfig config = new WatermarkConfig();
        config.brandLogo = logo;
        config.deviceModel = model;
        config.cameraParams = specs;
        presetStore.setActiveConfig(config);

        Intent intent = new Intent(this, EditorActivity.class);
        startActivity(intent);
    }

    // --- Tab 4: Settings ---
    private void setupSettingsTab(View root) {
        RadioGroup rgLanguage = root.findViewById(R.id.rgLanguage);
        RadioButton rbVietnamese = root.findViewById(R.id.rbVietnamese);
        RadioButton rbEnglish = root.findViewById(R.id.rbEnglish);

        String currentLang = LocaleHelper.getLanguage(this);
        if (LocaleHelper.LANG_EN.equalsIgnoreCase(currentLang)) {
            rbEnglish.setChecked(true);
        } else {
            rbVietnamese.setChecked(true);
        }

        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEnglish) {
                LocaleHelper.setLocale(MainActivity.this, LocaleHelper.LANG_EN);
                recreate();
            } else if (checkedId == R.id.rbVietnamese) {
                LocaleHelper.setLocale(MainActivity.this, LocaleHelper.LANG_VI);
                recreate();
            }
        });

        root.findViewById(R.id.btnReopenIntro).setOnClickListener(v -> new IntroDialog(this, null).show());
    }
}
