package dev.liquidplus.watermark.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import dev.liquidplus.watermark.R;

public class BrandLogoAdapter extends RecyclerView.Adapter<BrandLogoAdapter.ViewHolder> {

    public interface OnLogoClickListener {
        void onLogoClick(String brandKey);
    }

    public static class BrandItem {
        public String key;
        public int drawableRes;

        public BrandItem(String key, int drawableRes) {
            this.key = key;
            this.drawableRes = drawableRes;
        }
    }

    private final Context context;
    private final List<BrandItem> items = new ArrayList<>();
    private String selectedKey = "leica";
    private final OnLogoClickListener listener;

    public BrandLogoAdapter(Context context, String selectedKey, OnLogoClickListener listener) {
        this.context = context;
        this.selectedKey = selectedKey;
        this.listener = listener;

        items.add(new BrandItem("xiaomi", R.drawable.ic_brand_xiaomi));
        items.add(new BrandItem("leica", R.drawable.ic_brand_leica));
        items.add(new BrandItem("hasselblad", R.drawable.ic_brand_hasselblad));
        items.add(new BrandItem("zeiss", R.drawable.ic_brand_zeiss));
        items.add(new BrandItem("apple", R.drawable.ic_brand_apple));
        items.add(new BrandItem("sony", R.drawable.ic_brand_sony));
        items.add(new BrandItem("samsung", R.drawable.ic_brand_samsung));
        items.add(new BrandItem("huawei", R.drawable.ic_brand_huawei));
        items.add(new BrandItem("google", R.drawable.ic_brand_google));
        items.add(new BrandItem("oppo", R.drawable.ic_brand_oppo));
        items.add(new BrandItem("vivo", R.drawable.ic_brand_vivo));
        items.add(new BrandItem("nikon", R.drawable.ic_brand_nikon));
        items.add(new BrandItem("canon", R.drawable.ic_brand_canon));
        items.add(new BrandItem("fujifilm", R.drawable.ic_brand_fujifilm));
    }

    public void setSelectedKey(String key) {
        this.selectedKey = key;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_brand_logo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BrandItem item = items.get(position);
        holder.ivBrandLogo.setImageDrawable(ContextCompat.getDrawable(context, item.drawableRes));

        boolean isSelected = item.key.equalsIgnoreCase(selectedKey);
        holder.viewSelectedRing.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            selectedKey = item.key;
            notifyDataSetChanged();
            if (listener != null) listener.onLogoClick(item.key);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBrandLogo;
        View viewSelectedRing;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBrandLogo = itemView.findViewById(R.id.ivBrandLogo);
            viewSelectedRing = itemView.findViewById(R.id.viewSelectedRing);
        }
    }
}
