package dev.liquidplus.watermark.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class WatermarkConfig implements Serializable {
    public static final String POSITION_LEFT = "LEFT";
    public static final String POSITION_RIGHT = "RIGHT";

    public String brandLogo = "leica";
    public String customLogoUri = null;
    public String logoPosition = POSITION_LEFT;
    public String deviceModel = "Redmi 10";
    public String cameraParams = "35mm · f/1.8 · ISO 100";
    public String dateTime = "2026.09.05";
    public boolean showDateTime = true;

    // Liquid Glass Optics
    public float blurStrength = 0.60f;
    public float refractionStrength = 0.75f;
    public float rimLightStrength = 0.85f;
    public int capsuleHeightDp = 44;
    public float posXRatio = 0.5f; // center
    public float posYRatio = 0.86f; // near bottom
    public float scale = 1.0f;

    public WatermarkConfig() {}

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("brandLogo", brandLogo);
            obj.put("customLogoUri", customLogoUri);
            obj.put("logoPosition", logoPosition);
            obj.put("deviceModel", deviceModel);
            obj.put("cameraParams", cameraParams);
            obj.put("dateTime", dateTime);
            obj.put("showDateTime", showDateTime);
            obj.put("blurStrength", blurStrength);
            obj.put("refractionStrength", refractionStrength);
            obj.put("rimLightStrength", rimLightStrength);
            obj.put("posXRatio", posXRatio);
            obj.put("posYRatio", posYRatio);
            obj.put("scale", scale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static WatermarkConfig fromJson(JSONObject obj) {
        WatermarkConfig config = new WatermarkConfig();
        if (obj == null) return config;
        config.brandLogo = obj.optString("brandLogo", "leica");
        config.customLogoUri = obj.optString("customLogoUri", null);
        config.logoPosition = obj.optString("logoPosition", POSITION_LEFT);
        config.deviceModel = obj.optString("deviceModel", "Redmi 10");
        config.cameraParams = obj.optString("cameraParams", "35mm · f/1.8 · ISO 100");
        config.dateTime = obj.optString("dateTime", "2026.09.05");
        config.showDateTime = obj.optBoolean("showDateTime", true);
        config.blurStrength = (float) obj.optDouble("blurStrength", 0.60f);
        config.refractionStrength = (float) obj.optDouble("refractionStrength", 0.75f);
        config.rimLightStrength = (float) obj.optDouble("rimLightStrength", 0.85f);
        config.posXRatio = (float) obj.optDouble("posXRatio", 0.5f);
        config.posYRatio = (float) obj.optDouble("posYRatio", 0.86f);
        config.scale = (float) obj.optDouble("scale", 1.0f);
        return config;
    }
}
