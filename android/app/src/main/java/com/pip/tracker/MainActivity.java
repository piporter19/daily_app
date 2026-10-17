package com.pip.tracker;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FocusBlockerPlugin.class);
        registerPlugin(NudgePlugin.class);
        super.onCreate(savedInstanceState);
        stash(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        stash(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        Nudge.foreground = true;
    }

    @Override
    public void onPause() {
        Nudge.foreground = false;
        super.onPause();
    }

    /** A notification button asked the app to open somewhere specific (e.g. the "I'm stuck" screen). */
    private void stash(Intent i) {
        if (i != null && i.hasExtra("pip_act")) {
            Nudge.sp(this).edit().putString("launchAct", i.getStringExtra("pip_act")).putString("launchGo", i.getStringExtra("pip_go")).apply();
            i.removeExtra("pip_act");
        }
    }
}
