package com.pip.tracker;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@CapacitorPlugin(name = "FocusBlocker")
public class FocusBlockerPlugin extends Plugin {

    private boolean accessibilityOn() {
        try {
            String enabled = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String me = new ComponentName(getContext(), FocusAccessibilityService.class).flattenToString();
            return enabled != null && enabled.toLowerCase().contains(me.toLowerCase());
        } catch (Exception e) { return false; }
    }

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getContext().getSystemService(android.content.Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject r = new JSObject();
        r.put("accessibility", accessibilityOn());
        r.put("overlay", Settings.canDrawOverlays(getContext()));
        r.put("battery", batteryOk());
        call.resolve(r);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        String which = call.getString("which", "accessibility");
        Intent i;
        if ("overlay".equals(which)) i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
        else if ("battery".equals(which)) i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
        else i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    /** Apps that have a launcher icon, with a small icon each, sorted by name. */
    @PluginMethod
    public void listApps(PluginCall call) {
        PackageManager pm = getContext().getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        Set<String> seen = new HashSet<>();
        List<JSObject> out = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getContext().getPackageName()) || !seen.add(pkg)) continue;
            JSObject o = new JSObject();
            o.put("pkg", pkg);
            o.put("label", ri.loadLabel(pm).toString());
            try { o.put("icon", iconB64(ri.loadIcon(pm))); } catch (Exception ignored) {}
            out.add(o);
        }
        Collections.sort(out, (a, b) -> a.getString("label", "").compareToIgnoreCase(b.getString("label", "")));
        JSArray arr = new JSArray();
        for (JSObject o : out) arr.put(o);
        JSObject r = new JSObject();
        r.put("apps", arr);
        call.resolve(r);
    }

    private String iconB64(Drawable d) {
        int s = 56;
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, s, s);
        d.draw(c);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 90, bo);
        return "data:image/png;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    @PluginMethod
    public void startSession(PluginCall call) {
        if (!accessibilityOn()) { call.reject("accessibility_off"); return; }
        int mins = call.getInt("durationMin", 25);
        int delay = call.getInt("exitDelaySec", 30);
        Set<String> allowed = new HashSet<>();
        JSArray arr = call.getArray("allowed");
        if (arr != null) for (int i = 0; i < arr.length(); i++) { String s = arr.optString(i, ""); if (!s.isEmpty()) allowed.add(s); }
        FocusPrefs.start(getContext(), System.currentTimeMillis() + mins * 60000L, allowed, call.getString("habit", ""), delay);
        call.resolve(statusObj());
    }

    @PluginMethod
    public void endSession(PluginCall call) {
        FocusPrefs.end(getContext(), call.getString("reason", "manual"));
        call.resolve(statusObj());
    }

    @PluginMethod
    public void getSession(PluginCall call) { call.resolve(statusObj()); }

    @PluginMethod
    public void ackEnd(PluginCall call) { FocusPrefs.ackEnd(getContext()); call.resolve(); }

    private JSObject statusObj() {
        android.content.Context c = getContext();
        FocusPrefs.normalize(c);
        android.content.SharedPreferences p = FocusPrefs.sp(c);
        JSObject r = new JSObject();
        boolean active = FocusPrefs.active(c);
        r.put("active", active);
        r.put("endAt", p.getLong("endAt", 0));
        r.put("startedAt", p.getLong("startedAt", 0));
        r.put("habit", p.getString("habit", ""));
        r.put("exitDelay", p.getInt("exitDelay", 30));
        JSArray blocked = new JSArray();
        try {
            JSONObject o = new JSONObject(p.getString("blocked", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSObject b = new JSObject();
                b.put("pkg", k); b.put("label", FocusLabels.of(c, k)); b.put("count", o.optInt(k, 0));
                blocked.put(b);
            }
        } catch (Exception ignored) {}
        r.put("blocked", blocked);
        r.put("blockedTotal", FocusPrefs.blockedTotal(c));
        String reason = p.getString("lastEndReason", null);
        if (reason != null) {
            JSObject le = new JSObject();
            le.put("reason", reason);
            le.put("at", p.getLong("lastEndAt", 0));
            le.put("startedAt", p.getLong("lastStartedAt", 0));
            le.put("habit", p.getString("lastHabit", ""));
            r.put("lastEnd", le);
        }
        return r;
    }
}
