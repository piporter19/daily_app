package com.pip.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Focus session state, shared by the plugin, the accessibility service and the block screen. */
final class FocusPrefs {
    private FocusPrefs() {}

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("focus", Context.MODE_PRIVATE); }

    /** True while a session is running and its time has not run out. */
    static boolean active(Context c) {
        SharedPreferences p = sp(c);
        return p.getBoolean("active", false) && System.currentTimeMillis() < p.getLong("endAt", 0);
    }

    /** If the timer ran out while nobody was looking, close the session as completed. */
    static void normalize(Context c) {
        SharedPreferences p = sp(c);
        if (p.getBoolean("active", false) && System.currentTimeMillis() >= p.getLong("endAt", 0)) end(c, "completed");
    }

    static Set<String> allowed(Context c) { return new HashSet<>(sp(c).getStringSet("allowed", new HashSet<String>())); }

    static void start(Context c, long endAt, Set<String> allowed, String habit, int exitDelaySec) {
        sp(c).edit().putBoolean("active", true).putLong("startedAt", System.currentTimeMillis()).putLong("endAt", endAt)
            .putStringSet("allowed", allowed).putString("habit", habit == null ? "" : habit)
            .putInt("exitDelay", exitDelaySec).putString("blocked", "{}").remove("lastEndReason").apply();
    }

    static void end(Context c, String reason) {
        SharedPreferences p = sp(c);
        p.edit().putBoolean("active", false).putString("lastEndReason", reason).putLong("lastEndAt", System.currentTimeMillis())
            .putLong("lastStartedAt", p.getLong("startedAt", 0)).putString("lastHabit", p.getString("habit", "")).apply();
    }

    static void ackEnd(Context c) { sp(c).edit().remove("lastEndReason").apply(); }

    static void addBlocked(Context c, String pkg) {
        try {
            SharedPreferences p = sp(c);
            JSONObject o = new JSONObject(p.getString("blocked", "{}"));
            o.put(pkg, o.optInt(pkg, 0) + 1);
            p.edit().putString("blocked", o.toString()).apply();
        } catch (Exception ignored) {}
    }

    static int blockedTotal(Context c) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("blocked", "{}"));
            int n = 0; java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) n += o.optInt(it.next(), 0);
            return n;
        } catch (Exception e) { return 0; }
    }
}
