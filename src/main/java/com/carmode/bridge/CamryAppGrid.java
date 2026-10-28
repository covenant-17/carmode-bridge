package com.carmode.bridge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.List;
import java.util.function.Consumer;

/** Scrollable launcher-style tiles; GridView adapts columns to the actual available width. */
final class CamryAppGrid extends GridView {
    CamryAppGrid(Context context) {
        super(context);
        setNumColumns(AUTO_FIT); setColumnWidth(CamryUi.dp(context, 144));
        setStretchMode(STRETCH_COLUMN_WIDTH);
        setHorizontalSpacing(CamryUi.dp(context, 12)); setVerticalSpacing(CamryUi.dp(context, 12));
        setPadding(CamryUi.dp(context, 4), CamryUi.dp(context, 8), CamryUi.dp(context, 4), CamryUi.dp(context, 8));
        setClipToPadding(false); setDrawSelectorOnTop(false);
    }
    void apps(List<AppResolver.Entry> entries, List<Drawable> icons, Consumer<AppResolver.Entry> launch) {
        setAdapter(new BaseAdapter() {
            public int getCount() { return entries.size(); }
            public Object getItem(int position) { return entries.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, ViewGroup parent) {
                Tile tile = recycled instanceof Tile ? (Tile) recycled : new Tile(getContext());
                AppResolver.Entry entry = entries.get(position);
                tile.icon.setImageDrawable(icons.get(position)); tile.label.setText(entry.label);
                tile.setContentDescription(entry.label);
                return tile;
            }
        });
        setOnItemClickListener((parent, view, position, id) -> launch.accept(entries.get(position)));
    }
    private static final class Tile extends LinearLayout {
        final ImageView icon;
        final TextView label;
        Tile(Context context) {
            super(context); setOrientation(VERTICAL); setGravity(Gravity.CENTER);
            setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CamryUi.dp(context, 144)));
            int padding = CamryUi.dp(context, 12); setPadding(padding, padding, padding, padding);
            GradientDrawable card = CamryUi.panel(context, false); card.setColor(CamryUi.CARD);
            setBackground(new RippleDrawable(ColorStateList.valueOf(0x4649D6CF), card, null));
            icon = new ImageView(context); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LinearLayout.LayoutParams(CamryUi.dp(context, 56), CamryUi.dp(context, 56)));
            label = new TextView(context); label.setTextColor(Color.WHITE); label.setTextSize(18);
            label.setGravity(Gravity.CENTER); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            text.topMargin = CamryUi.dp(context, 8); addView(label, text);
        }
    }
}
