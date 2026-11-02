package dev.liquidplus.watermark.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import dev.liquidplus.watermark.R;
import dev.liquidplus.watermark.model.WatermarkPreset;

public class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.ViewHolder> {

    public interface OnPresetActionListener {
        void onApply(WatermarkPreset preset);
        void onShare(WatermarkPreset preset);
        void onDelete(WatermarkPreset preset);
    }

    private final Context context;
    private final List<WatermarkPreset> presets;
    private final OnPresetActionListener listener;

    public PresetAdapter(Context context, List<WatermarkPreset> presets, OnPresetActionListener listener) {
        this.context = context;
        this.presets = presets;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_preset, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WatermarkPreset p = presets.get(position);
        holder.tvPresetName.setText(p.name);

        String specs = (p.config != null ? p.config.deviceModel : "") + " · " +
                (p.config != null ? p.config.cameraParams : "");
        holder.tvPresetSubtitle.setText(specs);

        holder.tvShareCode.setText("#" + p.shareCode);

        // Logo
        int resId = R.drawable.ic_brand_leica;
        if (p.config != null && p.config.brandLogo != null) {
            switch (p.config.brandLogo) {
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
            }
        }
        holder.ivPresetLogo.setImageDrawable(ContextCompat.getDrawable(context, resId));

        holder.btnApply.setOnClickListener(v -> {
            if (listener != null) listener.onApply(p);
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShare(p);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(p);
        });
    }

    @Override
    public int getItemCount() {
        return presets.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPresetLogo;
        TextView tvPresetName;
        TextView tvPresetSubtitle;
        TextView tvShareCode;
        AppCompatButton btnApply;
        AppCompatButton btnShare;
        AppCompatButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPresetLogo = itemView.findViewById(R.id.ivPresetLogo);
            tvPresetName = itemView.findViewById(R.id.tvPresetName);
            tvPresetSubtitle = itemView.findViewById(R.id.tvPresetSubtitle);
            tvShareCode = itemView.findViewById(R.id.tvShareCode);
            btnApply = itemView.findViewById(R.id.btnApplyPreset);
            btnShare = itemView.findViewById(R.id.btnSharePreset);
            btnDelete = itemView.findViewById(R.id.btnDeletePreset);
        }
    }
}
