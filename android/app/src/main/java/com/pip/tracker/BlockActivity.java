package com.pip.tracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shown over a blocked app. Leaving early is possible, but only after a wait. */
public class BlockActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView left, blockedLine;
    private Button endBtn;
    private long openedAt;
    private int delay;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        openedAt = System.currentTimeMillis();
        delay = FocusPrefs.sp(this).getInt("exitDelay", 30);
        String pkg = getIntent().getStringExtra("pkg");
        String label = pkg == null ? "That app" : FocusLabels.of(this, pkg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 64, 64, 64);
        root.setBackgroundColor(Color.parseColor("#12142a"));

        TextView emoji = text("🌳", 64, "#ffffff", false);
        TextView title = text("Stay with it", 30, "#ecebf6", true);
        TextView sub = text(label + " isn't on your list for this session.", 16, "#a2a7c9", false);
        left = text("", 44, "#f4b740", true);
        blockedLine = text("", 13, "#6d7399", false);

        Button back = new Button(this);
        back.setText("Back to my session");
        back.setAllCaps(false);
        back.setOnClickListener(v -> goBack());

        endBtn = new Button(this);
        endBtn.setAllCaps(false);
        endBtn.setEnabled(false);
        endBtn.setOnClickListener(v -> {
            FocusPrefs.end(this, "early");
            goBack();
        });

        for (View v : new View[]{emoji, title, sub, left, blockedLine, back, endBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 18, 0, 0);
            root.addView(v, lp);
        }
        setContentView(root);
        tick();
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void tick() {
        FocusPrefs.normalize(this);
        if (!FocusPrefs.active(this)) { goBack(); return; }
        long ms = FocusPrefs.sp(this).getLong("endAt", 0) - System.currentTimeMillis();
        long s = Math.max(0, ms / 1000);
        left.setText(String.format("%d:%02d", s / 60, s % 60));
        blockedLine.setText(FocusPrefs.blockedTotal(this) + " blocked so far this session");
        long wait = delay - (System.currentTimeMillis() - openedAt) / 1000;
        if (wait > 0) { endBtn.setText("End session early (" + wait + "s)"); endBtn.setEnabled(false); }
        else { endBtn.setText("End session early"); endBtn.setEnabled(true); }
        h.postDelayed(this::tick, 1000);
    }

    private void goBack() {
        h.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    @Override public void onBackPressed() { goBack(); }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
