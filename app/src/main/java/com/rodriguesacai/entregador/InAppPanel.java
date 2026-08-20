package com.rodriguesacai.entregador;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Painel nativo do próprio UP Entregas.
 *
 * Não cria Window/Dialog do Android. Todo o conteúdo é anexado à árvore da Activity,
 * mantendo formulários, confirmações e detalhes visualmente dentro do aplicativo.
 */
public final class InAppPanel {
    private final Activity activity;
    private final String title;
    private final String subtitle;
    private final LinearLayout content;
    private FrameLayout overlay;
    private LinearLayout sheet;
    private boolean showing;

    public InAppPanel(Activity activity, String title, String subtitle, LinearLayout content) {
        this.activity = activity;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.content = content;
    }

    public void show() {
        if (showing || activity == null || activity.isFinishing()) return;
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null) return;

        overlay = new FrameLayout(activity);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(0xA6000000);
        overlay.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 52), Ui.dp(activity, 10), Ui.dp(activity, 8));

        sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 10), Ui.dp(activity, 20), Ui.dp(activity, 18));
        sheet.setBackground(Ui.rounded(activity, R.color.up_surface, 28, R.color.up_border, 1));
        sheet.setElevation(Ui.dp(activity, 18));
        sheet.setClickable(true);

        TextView handle = new TextView(activity);
        handle.setBackground(Ui.rounded(activity, R.color.up_border, 999, R.color.up_border, 0));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(activity, 38), Ui.dp(activity, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, 0, 0, Ui.dp(activity, 13));
        sheet.addView(handle, hp);

        LinearLayout header = Ui.row(activity);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = Ui.text(activity, title, 22, true);
        titles.addView(titleView);
        if (!subtitle.trim().isEmpty()) {
            TextView subtitleView = Ui.muted(activity, subtitle, 13);
            LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stp.setMargins(0, Ui.dp(activity, 5), 0, 0);
            titles.addView(subtitleView, stp);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = Ui.headerAction(activity, "×");
        close.setContentDescription("Fechar painel");
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44));
        cp.setMargins(Ui.dp(activity, 10), 0, 0, 0);
        header.addView(close, cp);
        sheet.addView(header);

        View divider = Ui.divider(activity);
        sheet.addView(divider);

        ScrollView body = new ScrollView(activity);
        body.setFillViewport(false);
        body.setClipToPadding(false);
        body.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        body.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM);
        sp.setMargins(0, 0, 0, 0);
        overlay.addView(sheet, sp);
        host.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showing = true;

        overlay.setAlpha(0f);
        sheet.setTranslationY(Ui.dp(activity, 48));
        overlay.animate().alpha(1f).setDuration(160).start();
        sheet.animate().translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public void dismiss() {
        if (!showing || overlay == null) return;
        showing = false;
        ViewGroup parent = (ViewGroup) overlay.getParent();
        overlay.animate().alpha(0f).setDuration(120).start();
        if (sheet != null) {
            sheet.animate().translationY(Ui.dp(activity, 40)).setDuration(150)
                    .withEndAction(() -> {
                        if (parent != null && overlay.getParent() == parent) parent.removeView(overlay);
                        overlay = null;
                        sheet = null;
                    }).start();
        } else if (parent != null) {
            parent.removeView(overlay);
            overlay = null;
        }
    }

    public boolean isShowing() { return showing; }
}
