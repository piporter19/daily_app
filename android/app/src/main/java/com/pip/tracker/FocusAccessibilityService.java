package com.pip.tracker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.HashSet;
import java.util.Set;

/**
 * Watches which app comes to the front. During a focus session, any app that is not on the
 * allowed list is covered by the block screen. It never reads screen content.
 */
public class FocusAccessibilityService extends AccessibilityService {
    private long lastBlockAt = 0;
    private Set<String> systemOk = null;
    private long systemOkAt = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        if (!FocusPrefs.active(this)) { FocusPrefs.normalize(this); return; }
        String pkg = e.getPackageName().toString();
        if (isAllowed(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - lastBlockAt < 700) return;
        lastBlockAt = now;
        FocusPrefs.addBlocked(this, pkg);
        Intent i = new Intent(this, BlockActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("pkg", pkg);
        startActivity(i);
    }

    private boolean isAllowed(String pkg) {
        if (pkg.equals(getPackageName())) return true;
        if (FocusPrefs.allowed(this).contains(pkg)) return true;
        return systemPackages().contains(pkg);
    }

    /** Things that must never be blocked: the home screen, keyboards, calls, system dialogs. */
    private Set<String> systemPackages() {
        long now = System.currentTimeMillis();
        if (systemOk != null && now - systemOkAt < 30000) return systemOk;
        Set<String> s = new HashSet<>();
        s.add("com.android.systemui"); s.add("android");
        s.add("com.android.permissioncontroller"); s.add("com.google.android.permissioncontroller");
        s.add("com.android.server.telecom"); s.add("com.android.incallui"); s.add("com.google.android.dialer");
        try {
            for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
                s.add(r.activityInfo.packageName);
            ResolveInfo d = getPackageManager().resolveActivity(new Intent(Intent.ACTION_DIAL), 0);
            if (d != null && d.activityInfo != null) s.add(d.activityInfo.packageName);
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) for (InputMethodInfo m : imm.getEnabledInputMethodList()) s.add(m.getPackageName());
        } catch (Exception ignored) {}
        systemOk = s; systemOkAt = now;
        return s;
    }

    @Override public void onInterrupt() {}
}
