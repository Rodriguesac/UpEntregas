package com.rodriguesacai.entregador;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int color(Context c, int id) { return ContextCompat.getColor(c, id); }

    public static TextView text(Context c, String s, int sp, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s == null ? "" : s);
        v.setTextColor(color(c, R.color.up_text));
        v.setTextSize(sp);
        v.setLineSpacing(dp(c, 2), 1.03f);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        else v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return v;
    }

    public static TextView muted(Context c, String s, int sp) {
        TextView v = text(c, s, sp, false);
        v.setTextColor(color(c, R.color.up_text_muted));
        return v;
    }

    public static TextView eyebrow(Context c, String s) {
        TextView v = text(c, s == null ? "" : s.toUpperCase(), 10, true);
        v.setTextColor(color(c, R.color.up_purple));
        v.setLetterSpacing(.09f);
        return v;
    }

    public static TextView sectionTitle(Context c, String title) {
        TextView t = text(c, title, 18, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 10), 0, dp(c, 4));
        t.setLayoutParams(p);
        return t;
    }

    public static TextView centered(Context c, String s, int sp, boolean bold) {
        TextView t = text(c, s, sp, bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static ImageView brandLogo(Context c, int sizeDp) {
        ImageView image = new ImageView(c);
        image.setImageResource(R.drawable.rodrigues_logo);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setAdjustViewBounds(true);
        image.setContentDescription("Rodrigues Açaí e Cia");
        image.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return image;
    }

    public static ImageView upMark(Context c, int sizeDp) {
        ImageView mark = new ImageView(c);
        mark.setImageResource(R.drawable.up_app_icon);
        mark.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mark.setAdjustViewBounds(true);
        mark.setContentDescription("UP Entregas");
        mark.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return mark;
    }

    public static ImageView upWordmark(Context c, int widthDp, int heightDp) {
        ImageView image = new ImageView(c);
        image.setImageResource(R.drawable.up_wordmark);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setAdjustViewBounds(true);
        image.setContentDescription("UP Entregas");
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), dp(c, heightDp)));
        return image;
    }

    public static Button button(Context c, String s) {
        MaterialButton b = new MaterialButton(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 56));
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_purple)));
        b.setCornerRadius(dp(c, 18));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setElevation(0);
        b.setLetterSpacing(0.01f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 6), 0, dp(c, 6));
        b.setLayoutParams(p);
        return b;
    }

    public static Button greenButton(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(0xFF0C2415);
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_green)));
        return b;
    }

    public static Button yellowButton(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(0xFF111820);
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_yellow)));
        if (b instanceof MaterialButton) {
            MaterialButton m = (MaterialButton) b;
            m.setCornerRadius(dp(c, 18));
        }
        return b;
    }

    public static Button secondaryButton(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(color(c, R.color.up_text));
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_surface_alt)));
        if (b instanceof MaterialButton) {
            MaterialButton m = (MaterialButton) b;
            m.setStrokeColor(ColorStateList.valueOf(color(c, R.color.up_border)));
            m.setStrokeWidth(dp(c, 1));
        }
        return b;
    }

    public static Button outlineButton(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(color(c, R.color.up_purple));
        if (b instanceof MaterialButton) {
            MaterialButton m = (MaterialButton) b;
            m.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_surface)));
            m.setStrokeColor(ColorStateList.valueOf(color(c, R.color.up_purple)));
            m.setStrokeWidth(dp(c, 1));
            m.setCornerRadius(dp(c, 18));
        } else {
            b.setBackground(rounded(c, R.color.up_surface, 18, R.color.up_purple, 1));
        }
        return b;
    }

    public static Button dangerButton(Context c, String s) {
        Button b = button(c, s);
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_danger)));
        return b;
    }

    public static Button miniAction(Context c, String s) {
        MaterialButton b = new MaterialButton(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(color(c, R.color.up_text));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 46));
        b.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.up_surface_alt)));
        b.setCornerRadius(dp(c, 15));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setElevation(0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(c, 3), dp(c, 4), dp(c, 3), dp(c, 4));
        b.setLayoutParams(p);
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        l.setBackground(rounded(c, R.color.up_surface, 22, R.color.up_border, 1));
        l.setElevation(dp(c, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 7), 0, dp(c, 7));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout routeHeaderCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 17), dp(c, 18), dp(c, 15));
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color(c, R.color.up_surface), color(c, R.color.up_surface_alt)});
        g.setCornerRadius(dp(c, 22));
        g.setStroke(dp(c, 1), color(c, R.color.up_border));
        l.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 4), 0, dp(c, 7));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout paymentCard(Context c) {
        LinearLayout l = row(c);
        l.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        GradientDrawable g = new GradientDrawable();
        int raw = color(c, R.color.up_success);
        g.setColor((0x12 << 24) | (raw & 0x00FFFFFF));
        g.setCornerRadius(dp(c, 16));
        g.setStroke(dp(c, 1), (0x42 << 24) | (raw & 0x00FFFFFF));
        l.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 14), 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout upcomingStopCard(Context c) {
        LinearLayout l = row(c);
        l.setPadding(dp(c, 13), dp(c, 11), dp(c, 13), dp(c, 11));
        l.setBackground(rounded(c, R.color.up_surface, 16, R.color.up_border, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 4), 0, dp(c, 4));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout heroCard(Context c) {
        LinearLayout l = card(c);
        l.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        return l;
    }

    public static LinearLayout subCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        l.setBackground(rounded(c, R.color.up_surface_alt, 16, R.color.up_border, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 5), 0, dp(c, 5));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout compactCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        l.setBackground(rounded(c, R.color.up_surface, 17, R.color.up_border, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 5), 0, dp(c, 5));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 30));
        l.setBackgroundColor(color(c, R.color.up_bg));
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView pill(Context c, String s, int colorRes) {
        TextView t = text(c, s, 11, true);
        t.setTextColor(color(c, colorRes));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 11), dp(c, 6), dp(c, 11), dp(c, 6));
        t.setBackground(roundedAlpha(c, colorRes, 0x22, 999));
        return t;
    }

    public static Space space(Context c, int dp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(c, dp)));
        return s;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.up_border));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        p.setMargins(0, dp(c, 12), 0, dp(c, 12));
        v.setLayoutParams(p);
        return v;
    }

    public static GradientDrawable rounded(Context c, int colorRes, int radiusDp, int strokeColorRes, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(c, colorRes));
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), color(c, strokeColorRes));
        return g;
    }

    private static GradientDrawable roundedAlpha(Context c, int colorRes, int alpha, int radiusDp) {
        int raw = color(c, colorRes);
        int col = (alpha << 24) | (raw & 0x00FFFFFF);
        GradientDrawable g = new GradientDrawable();
        g.setColor(col);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static LinearLayout accentCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 20), dp(c, 22), dp(c, 20), dp(c, 22));
        l.setBackground(rounded(c, R.color.up_purple_soft, 24, R.color.up_purple_soft, 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 8), 0, dp(c, 12));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout statTile(Context c, String label, TextView value) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(c, 8), dp(c, 12), dp(c, 8), dp(c, 12));
        l.setBackground(rounded(c, R.color.up_surface, 16, R.color.up_border, 1));
        TextView lab = muted(c, label, 10);
        lab.setGravity(Gravity.CENTER);
        l.addView(lab);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, dp(c, 4), 0, 0);
        l.addView(value);
        return l;
    }

    public static ImageView iconAction(Context c, int drawableRes, String description) {
        ImageView image = new ImageView(c);
        image.setImageResource(drawableRes);
        image.setColorFilter(color(c, R.color.up_purple));
        image.setContentDescription(description);
        image.setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11));
        image.setBackground(rounded(c, R.color.up_surface_alt, 14, R.color.up_border, 1));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return image;
    }

    public static TextView headerAction(Context c, String symbol) {
        TextView t = centered(c, symbol, 15, true);
        t.setTextColor(color(c, R.color.up_purple));
        t.setMinWidth(dp(c, 44));
        t.setMinHeight(dp(c, 44));
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(c, R.color.up_surface_alt, 14, R.color.up_border, 1));
        return t;
    }

    public static LinearLayout bottomBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(c, 8), dp(c, 7), dp(c, 8), dp(c, 8));
        bar.setBackgroundColor(color(c, R.color.up_surface));
        bar.setElevation(dp(c, 14));
        return bar;
    }

    public static LinearLayout navItemView(Context c, int drawableRes, String label, boolean active) {
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(c, 6), dp(c, 5), dp(c, 6), dp(c, 5));
        if (active) item.setBackground(rounded(c, R.color.up_purple_soft, 14, R.color.up_purple_soft, 0));

        ImageView icon = new ImageView(c);
        icon.setImageResource(drawableRes);
        icon.setColorFilter(color(c, active ? R.color.up_purple : R.color.up_text_muted));
        icon.setContentDescription(label);
        item.addView(icon, new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));

        TextView text = centered(c, label, 10, true);
        text.setTextColor(color(c, active ? R.color.up_purple : R.color.up_text_muted));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, dp(c, 2), 0, 0);
        item.addView(text, tp);
        return item;
    }

    public static TextView navItem(Context c, String symbol, String label, boolean active) {
        TextView t = centered(c, symbol + "\n" + label, 10, true);
        t.setTextColor(color(c, active ? R.color.up_purple : R.color.up_text_muted));
        t.setLineSpacing(0, .96f);
        t.setPadding(dp(c, 5), dp(c, 6), dp(c, 5), dp(c, 6));
        if (active) t.setBackground(rounded(c, R.color.up_purple_soft, 14, R.color.up_purple_soft, 0));
        return t;
    }

    public static LinearLayout accentGradientCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 19), dp(c, 20), dp(c, 19), dp(c, 20));
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color(c, R.color.up_purple_dark), color(c, R.color.up_purple)});
        g.setCornerRadius(dp(c, 24));
        l.setBackground(g);
        l.setElevation(dp(c, 2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 7), 0, dp(c, 10));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout summaryStrip(Context c) {
        LinearLayout l = row(c);
        l.setPadding(dp(c, 5), dp(c, 7), dp(c, 5), dp(c, 7));
        l.setBackground(rounded(c, R.color.up_surface, 18, R.color.up_border, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 5), 0, dp(c, 10));
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout summaryItem(Context c, String label, TextView value) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(c, 6), dp(c, 7), dp(c, 6), dp(c, 7));
        TextView lab = muted(c, label, 10);
        lab.setGravity(Gravity.CENTER);
        l.addView(lab);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, dp(c, 3), 0, 0);
        l.addView(value);
        return l;
    }

    public static LinearLayout addressBlock(Context c, String label, String title, String address, boolean destination) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.TOP);
        l.setPadding(0, dp(c, 8), 0, dp(c, 8));
        TextView icon = centered(c, destination ? "●" : "■", 12, true);
        icon.setTextColor(color(c, destination ? R.color.up_success : R.color.up_purple));
        icon.setMinWidth(dp(c, 28));
        l.addView(icon);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(c, 7), 0, 0, 0);
        text.addView(muted(c, label, 10));
        text.addView(text(c, title, 16, true));
        text.addView(muted(c, address, 13));
        l.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return l;
    }

    public static LinearLayout noticeCard(Context c, String title, String body, int colorRes) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 13), dp(c, 11), dp(c, 13), dp(c, 11));
        GradientDrawable g = new GradientDrawable();
        int raw = color(c, colorRes);
        g.setColor((0x18 << 24) | (raw & 0x00FFFFFF));
        g.setCornerRadius(dp(c, 14));
        l.setBackground(g);
        TextView t = text(c, title, 11, true);
        t.setTextColor(raw);
        l.addView(t);
        l.addView(muted(c, body, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 9), 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout moneyRow(Context c, String label, String value, int valueColorRes) {
        LinearLayout row = row(c);
        row.setPadding(0, dp(c, 4), 0, dp(c, 4));
        TextView l = text(c, label, 13, false);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(c, value, 14, true);
        v.setTextColor(color(c, valueColorRes));
        v.setGravity(Gravity.END);
        row.addView(v);
        return row;
    }

    public static LinearLayout menuRow(Context c, String iconText, String title, String subtitle) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        card.setBackground(rounded(c, R.color.up_surface, 17, R.color.up_border, 1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(c, 5), 0, dp(c, 5));
        card.setLayoutParams(cp);

        TextView icon = centered(c, iconText, 14, true);
        icon.setTextColor(color(c, R.color.up_purple));
        icon.setMinWidth(dp(c, 36));
        card.addView(icon);

        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        TextView titleView = text(c, title, 14, true);
        labels.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) labels.addView(muted(c, subtitle, 11));
        card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text(c, "›", 20, false);
        arrow.setTextColor(color(c, R.color.up_text_muted));
        card.addView(arrow);
        card.setTag(titleView);
        return card;
    }

    public static LinearLayout dataLine(Context c, String label, String value) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(c, 8), 0, dp(c, 8));
        l.addView(muted(c, label, 10));
        l.addView(text(c, value, 15, true));
        return l;
    }

    public static EditText input(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextColor(color(c, R.color.up_text));
        e.setHintTextColor(color(c, R.color.up_text_muted));
        e.setTextSize(16);
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        e.setBackground(rounded(c, R.color.up_surface_alt, 14, R.color.up_border, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 6), 0, dp(c, 6));
        e.setLayoutParams(p);
        return e;
    }
    public static TextInputLayout formField(Context c, String label, int inputType) {
        TextInputLayout box = new TextInputLayout(c);
        box.setHint(label);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxBackgroundColor(color(c, R.color.up_surface));
        box.setBoxStrokeColor(color(c, R.color.up_purple));
        box.setDefaultHintTextColor(ColorStateList.valueOf(color(c, R.color.up_text_muted)));
        box.setBoxCornerRadii(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        box.setErrorEnabled(true);
        box.setPadding(0, 0, 0, 0);

        TextInputEditText edit = new TextInputEditText(c);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setTextColor(color(c, R.color.up_text));
        edit.setHintTextColor(color(c, R.color.up_text_muted));
        edit.setTextSize(16);
        edit.setPadding(dp(c, 15), dp(c, 11), dp(c, 15), dp(c, 11));
        box.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(c, 6), 0, dp(c, 6));
        box.setLayoutParams(p);
        return box;
    }

    public static TextInputEditText fieldEdit(TextInputLayout box) {
        return box == null ? null : (TextInputEditText) box.getEditText();
    }

    public static Button choiceButton(Context c, String label, boolean selected) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinHeight(dp(c, 48));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setCornerRadius(dp(c, 16));
        b.setElevation(0);
        b.setTextColor(color(c, selected ? R.color.white : R.color.up_text));
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, selected ? R.color.up_purple : R.color.up_surface_alt)));
        b.setStrokeColor(ColorStateList.valueOf(color(c, selected ? R.color.up_purple : R.color.up_border)));
        b.setStrokeWidth(dp(c, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        b.setLayoutParams(p);
        return b;
    }

    public static View stepBar(Context c, boolean active) {
        View v = new View(c);
        v.setBackground(rounded(c, active ? R.color.up_purple : R.color.up_border, 999, active ? R.color.up_purple : R.color.up_border, 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, 5), 1f);
        p.setMargins(dp(c, 2), 0, dp(c, 2), 0);
        v.setLayoutParams(p);
        return v;
    }

    public static void message(Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;
        View anchor = activity.findViewById(android.R.id.content);
        if (anchor == null) return;
        Snackbar bar = Snackbar.make(anchor, message == null ? "Não foi possível concluir." : message, Snackbar.LENGTH_LONG);
        bar.setBackgroundTint(color(activity, R.color.up_text));
        bar.setTextColor(color(activity, R.color.up_surface));
        bar.setActionTextColor(color(activity, R.color.up_yellow));
        bar.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);
        bar.show();
    }

}
