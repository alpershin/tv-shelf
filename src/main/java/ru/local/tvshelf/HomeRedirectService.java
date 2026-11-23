package ru.local.tvshelf;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

public final class HomeRedirectService extends AccessibilityService {
    private static final String GOOGLE_TV = "com.google.android.apps.tv.launcherx";
    private static final String GOOGLE_HOME = GOOGLE_TV + ".home.HomeActivity";
    private static final long HOME_TRANSITION_MS = 150;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openShelf = () -> startActivity(new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || !TextUtils.equals(GOOGLE_TV, event.getPackageName())
                || !TextUtils.equals(GOOGLE_HOME, event.getClassName())) {
            return;
        }

        // Google TV can announce Home more than once as its window comes to the front.
        handler.removeCallbacks(openShelf);
        handler.postDelayed(openShelf, HOME_TRANSITION_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(openShelf);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(openShelf);
        super.onDestroy();
    }
}
