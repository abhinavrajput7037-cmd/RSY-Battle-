package co.median.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.graphics.PixelFormat;

import org.json.JSONObject;

/**
 * RSY Battle Native Bridge Handler
 * Handles: match live popup, vibration, floating button, permissions
 */
public class RsyBridgeHandler {
    private final Activity activity;
    private final Context context;
    private View matchLiveOverlay;
    private WindowManager windowManager;

    public RsyBridgeHandler(Activity activity) {
        this.activity = activity;
        this.context = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Called from JS: median.android.rsyVibrate() */
    @JavascriptInterface
    public void rsyVibrate() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 100, 300, 100, 500}, -1));
            } else {
                v.vibrate(new long[]{0, 300, 100, 300, 100, 500}, -1);
            }
        }
    }

    /** Called from JS: median.android.showMatchLivePopup(matchName, game) */
    @JavascriptInterface
    public void showMatchLivePopup(String matchName, String game) {
        activity.runOnUiThread(() -> {
            if (matchLiveOverlay != null) return; // already showing

            // Vibrate first
            rsyVibrate();

            // Create overlay
            LinearLayout overlay = new LinearLayout(context);
            overlay.setOrientation(LinearLayout.VERTICAL);
            overlay.setGravity(Gravity.CENTER);
            overlay.setPadding(48, 48, 48, 48);

            GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xEE0E1330, 0xEE1a0a2e}
            );
            bg.setCornerRadius(32f);
            bg.setStroke(3, 0xFFFF6B35);
            overlay.setBackground(bg);

            TextView icon = new TextView(context);
            icon.setText("🔴");
            icon.setTextSize(48f);
            icon.setGravity(Gravity.CENTER);
            overlay.addView(icon);

            TextView title = new TextView(context);
            title.setText("MATCH LIVE!");
            title.setTextColor(0xFFFF4444);
            title.setTextSize(26f);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 16, 0, 8);
            overlay.addView(title);

            TextView nameView = new TextView(context);
            nameView.setText(matchName != null ? matchName : "Your Match");
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(16f);
            nameView.setGravity(Gravity.CENTER);
            overlay.addView(nameView);

            TextView sub = new TextView(context);
            sub.setText("Room ID app mein check karo! 🏆");
            sub.setTextColor(0xFFAAAAAA);
            sub.setTextSize(13f);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, 12, 0, 24);
            overlay.addView(sub);

            TextView okBtn = new TextView(context);
            okBtn.setText("  ✅ Let's Go!  ");
            okBtn.setTextColor(Color.WHITE);
            okBtn.setTextSize(16f);
            okBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFF6B35, 0xFFFF4444}
            );
            btnBg.setCornerRadius(50f);
            okBtn.setBackground(btnBg);
            okBtn.setPadding(48, 24, 48, 24);
            okBtn.setGravity(Gravity.CENTER);
            okBtn.setOnClickListener(v -> dismissMatchLivePopup());
            overlay.addView(okBtn);

            matchLiveOverlay = overlay;

            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int w = (int)(dm.widthPixels * 0.85f);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                w, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            try {
                windowManager.addView(matchLiveOverlay, params);
                overlay.setAlpha(0f);
                overlay.setScaleX(0.7f);
                overlay.setScaleY(0.7f);
                overlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();

                // Auto dismiss after 8 seconds
                new Handler(Looper.getMainLooper()).postDelayed(this::dismissMatchLivePopup, 8000);
            } catch (Exception e) {
                matchLiveOverlay = null;
            }
        });
    }

    private void dismissMatchLivePopup() {
        if (matchLiveOverlay != null) {
            matchLiveOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                try { windowManager.removeView(matchLiveOverlay); } catch(Exception e) {}
                matchLiveOverlay = null;
            }).start();
        }
    }

    /** Start floating button service */
    @JavascriptInterface
    public void startFloating() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
            activity.startActivity(intent);
            return;
        }
        Intent svc = new Intent(context, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }

    /** Request all permissions */
    @JavascriptInterface
    public void requestAllPermissions() {
        activity.runOnUiThread(() -> {
            // Request overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
                activity.startActivity(intent);
            }
        });
    }
}
