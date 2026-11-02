package dev.liquidplus.watermark.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.UUID;

public class WatermarkPreset implements Serializable {
    public String id;
    public String name;
    public String shareCode; // 9-digit code
    public WatermarkConfig config;
    public long createdAt;

    public WatermarkPreset() {
        this.id = UUID.randomUUID().toString();
        this.config = new WatermarkConfig();
        this.createdAt = System.currentTimeMillis();
    }

    public WatermarkPreset(String name, String shareCode, WatermarkConfig config) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.shareCode = shareCode;
        this.config = config;
        this.createdAt = System.currentTimeMillis();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("shareCode", shareCode);
            obj.put("config", config != null ? config.toJson() : new JSONObject());
            obj.put("createdAt", createdAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static WatermarkPreset fromJson(JSONObject obj) {
        if (obj == null) return null;
        WatermarkPreset preset = new WatermarkPreset();
        preset.id = obj.optString("id", UUID.randomUUID().toString());
        preset.name = obj.optString("name", "Untitled");
        preset.shareCode = obj.optString("shareCode", "123456789");
        preset.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        JSONObject configObj = obj.optJSONObject("config");
        preset.config = WatermarkConfig.fromJson(configObj);
        return preset;
    }
}
