package dev.liquidplus.watermark.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PresetStore {
    private static final String PREF_NAME = "liquid_watermark_presets";
    private static final String KEY_PRESETS = "presets_json";
    private static final String KEY_ACTIVE_CONFIG = "active_config_json";
    private static final String KEY_FIRST_LAUNCH = "first_launch_done";

    private final SharedPreferences prefs;
    private final List<WatermarkPreset> presets = new ArrayList<>();

    public PresetStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPresets();
    }

    private void loadPresets() {
        presets.clear();
        String json = prefs.getString(KEY_PRESETS, null);
        if (json == null) {
            // Seed default presets
            seedDefaults();
            savePresets();
        } else {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    WatermarkPreset p = WatermarkPreset.fromJson(arr.getJSONObject(i));
                    if (p != null) presets.add(p);
                }
            } catch (Exception e) {
                e.printStackTrace();
                seedDefaults();
            }
        }
    }

    private void seedDefaults() {
        // Preset 1: Leica Master Pill
        WatermarkConfig c1 = new WatermarkConfig();
        c1.brandLogo = "leica";
        c1.deviceModel = "Xiaomi 14 Ultra";
        c1.cameraParams = "23mm · f/1.63 · ISO 50";
        c1.dateTime = "2026.09.05";
        c1.blurStrength = 0.65f;
        c1.refractionStrength = 0.80f;
        presets.add(new WatermarkPreset("Leica Master Pill", "849204821", c1));

        // Preset 2: Hasselblad Pro
        WatermarkConfig c2 = new WatermarkConfig();
        c2.brandLogo = "hasselblad";
        c2.deviceModel = "OnePlus 12 Pro";
        c2.cameraParams = "70mm · f/2.6 · ISO 100";
        c2.dateTime = "2026.08.15";
        c2.blurStrength = 0.50f;
        c2.refractionStrength = 0.70f;
        presets.add(new WatermarkPreset("Hasselblad Natural Color", "739104822", c2));

        // Preset 3: Apple Minimal Glass
        WatermarkConfig c3 = new WatermarkConfig();
        c3.brandLogo = "apple";
        c3.deviceModel = "iPhone 16 Pro Max";
        c3.cameraParams = "24mm · f/1.78 · ISO 80";
        c3.dateTime = "2026.10.12";
        c3.blurStrength = 0.75f;
        c3.refractionStrength = 0.85f;
        presets.add(new WatermarkPreset("Apple Minimal Glass", "918237401", c3));

        // Preset 4: Redmi / Xiaomi Classic (Matching user screenshot)
        WatermarkConfig c4 = new WatermarkConfig();
        c4.brandLogo = "xiaomi";
        c4.deviceModel = "Redmi 10";
        c4.cameraParams = "35mm · f/1.8 · ISO 100";
        c4.dateTime = "2026.09.05";
        c4.blurStrength = 0.60f;
        c4.refractionStrength = 0.75f;
        presets.add(new WatermarkPreset("Redmi 10 Sunset Lake", "592019482", c4));
    }

    public synchronized void savePresets() {
        JSONArray arr = new JSONArray();
        for (WatermarkPreset p : presets) {
            arr.put(p.toJson());
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply();
    }

    public List<WatermarkPreset> getPresets() {
        return presets;
    }

    public void addPreset(String name, WatermarkConfig config) {
        String code = generateShareCode();
        WatermarkPreset p = new WatermarkPreset(name, code, config);
        presets.add(0, p);
        savePresets();
    }

    public void deletePreset(String id) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(id)) {
                presets.remove(i);
                savePresets();
                break;
            }
        }
    }

    public WatermarkPreset findByCode(String code) {
        if (code == null) return null;
        String clean = code.trim().replace("#", "");
        for (WatermarkPreset p : presets) {
            if (p.shareCode.equalsIgnoreCase(clean)) {
                return p;
            }
        }
        return null;
    }

    public String generateShareCode() {
        Random r = new Random();
        long num = 100000000L + (long)(r.nextDouble() * 899999999L);
        return String.valueOf(num);
    }

    public WatermarkConfig getActiveConfig() {
        String json = prefs.getString(KEY_ACTIVE_CONFIG, null);
        if (json != null) {
            try {
                return WatermarkConfig.fromJson(new JSONObject(json));
            } catch (Exception ignored) {}
        }
        // Default to preset 0 config
        if (!presets.isEmpty()) {
            return presets.get(0).config;
        }
        return new WatermarkConfig();
    }

    public void setActiveConfig(WatermarkConfig config) {
        if (config == null) return;
        prefs.edit().putString(KEY_ACTIVE_CONFIG, config.toJson().toString()).apply();
    }

    public boolean isFirstLaunch() {
        return !prefs.getBoolean(KEY_FIRST_LAUNCH, false);
    }

    public void setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply();
    }
}
