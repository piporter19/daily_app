package com.pip.tracker;

import android.content.Context;
import android.content.pm.PackageManager;

final class FocusLabels {
    private FocusLabels() {}
    static String of(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }
}
