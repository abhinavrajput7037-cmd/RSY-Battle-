package co.median.android;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * RSY Battle Floating Button Service
 * Shows a draggable floating mini button that opens a mini app view
 */
public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private View miniAppView;
    private WindowManager.LayoutParams floatParams;
    private WindowManager.LayoutParams miniParams;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long lastTouchTime;
    private boolean isMiniOpen = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingButton();
    }

    private void createFloatingButton() {
        // ── Floating Button ──
        floatingView = new LinearLayout(this);
        ((LinearLayout)floatingView).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout)floatingView).setGravity(Gravity.CENTER);

        // Button background - orange/cyan gradient circle
        GradientDrawable bgDrawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFFF6B35, 0xFF00E5FF}
        );
        bgDrawable.setShape(GradientDrawable.OVAL);
        bgDrawable.setSize(150, 150);
        floatingView.setBackground(bgDrawable);
        floatingView.setElevation(20f);

        // RSY text label
        TextView label = new TextView(this);
        label.setText("RSY");
        label.setTextColor(Color.WHITE);
        label.setTextSize(11f);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        ((LinearLayout)floatingView).addView(label);

        TextView sub = new TextView(this);
        sub.setText("⚔");
        sub.setTextColor(Color.WHITE);
        sub.setTextSize(16f);
        sub.setGravity(Gravity.CENTER);
        ((LinearLayout)floatingView).addView(sub);

        floatParams = new WindowManager.LayoutParams(
            150, 150,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        floatParams.gravity = Gravity.TOP | Gravity.START;
        floatParams.x = 0;
        floatParams.y = 300;

        windowManager.addView(floatingView, floatParams);

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatParams.x;
                        initialY = floatParams.y;
                        initialTouchX = e.getRawX();
                        initialTouchY = e.getRawY();
                        lastTouchTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        floatParams.x = initialX + (int)(e.getRawX() - initialTouchX);
                        floatParams.y = initialY + (int)(e.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, floatParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long elapsed = System.currentTimeMillis() - lastTouchTime;
                        float moved = Math.abs(e.getRawX()-initialTouchX) + Math.abs(e.getRawY()-initialTouchY);
                        if (elapsed < 300 && moved < 20) {
                            // It's a tap — toggle mini app
                            toggleMiniApp();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleMiniApp() {
        if (isMiniOpen) {
            closeMiniApp();
        } else {
            openMiniApp();
        }
    }

    private void openMiniApp() {
        if (miniAppView != null) return;
        isMiniOpen = true;

        // Create mini WebView container
        android.webkit.WebView miniWebView = new android.webkit.WebView(this);
        miniWebView.getSettings().setJavaScriptEnabled(true);
        miniWebView.getSettings().setDomStorageEnabled(true);
        miniWebView.loadUrl("https://rsybattle.xyz/user-v3.html");

        // Outer container with header
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0E1330);
        bg.setCornerRadius(24f);
        bg.setStroke(2, 0xFFFF6B35);
        container.setBackground(bg);
        container.setElevation(30f);

        // Header bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(24, 16, 24, 16);
        header.setBackgroundColor(0xFF0E1330);

        TextView title = new TextView(this);
        title.setText("🎮 RSY Battle");
        title.setTextColor(0xFFFF6B35);
        title.setTextSize(14f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tp);
        header.addView(title);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(18f);
        closeBtn.setPadding(16, 8, 16, 8);
        closeBtn.setOnClickListener(vv -> closeMiniApp());
        header.addView(closeBtn);

        container.addView(header);
        container.addView(miniWebView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        miniAppView = container;

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = (int)(dm.widthPixels * 0.85f);
        int h = (int)(dm.heightPixels * 0.65f);

        miniParams = new WindowManager.LayoutParams(
            w, h,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        miniParams.gravity = Gravity.CENTER;

        windowManager.addView(miniAppView, miniParams);

        // Animate in
        miniAppView.setAlpha(0f);
        miniAppView.setScaleX(0.8f);
        miniAppView.setScaleY(0.8f);
        miniAppView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    private void closeMiniApp() {
        if (miniAppView != null) {
            miniAppView.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150)
                .withEndAction(() -> {
                    try { windowManager.removeView(miniAppView); } catch(Exception e) {}
                    miniAppView = null;
                    isMiniOpen = false;
                }).start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) try { windowManager.removeView(floatingView); } catch(Exception e) {}
        if (miniAppView != null) try { windowManager.removeView(miniAppView); } catch(Exception e) {}
    }
}
