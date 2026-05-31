package co.median.android;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.InputStream;
import java.net.URL;

/**
 * RSY Battle Floating Button Service
 * Features:
 * - App logo shown in circle
 * - Small size (app icon size) - adjustable
 * - Drag anywhere by touching button
 * - Long press = popup with: Delete / Resize / Close options
 * - Mini app opens rsybattle.xyz/user-app
 * - Mini app header draggable to reposition
 */
public class FloatingService extends Service {

    private WindowManager windowManager;
    private FrameLayout floatingView;
    private View miniAppView;
    private WindowManager.LayoutParams floatParams;
    private WindowManager.LayoutParams miniParams;

    // Touch tracking for floating button
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long pressStartTime;
    private boolean isDragging = false;
    private boolean isMiniOpen = false;

    // Current size in dp (adjustable)
    private int currentSizeDp = 52;

    private static final String LOGO_URL = "https://i.ibb.co/LDgmPDCJ/Picsart-26-03-05-08-01-20-104.png";
    private static final String APP_URL  = "https://rsybattle.xyz/user-app.html";

    private Handler handler = new Handler(Looper.getMainLooper());
    private ImageView logoView;
    private Bitmap logoBitmap = null;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // Load logo first, then create button
        loadLogoThenCreate();
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private void loadLogoThenCreate() {
        new Thread(() -> {
            try {
                InputStream in = new URL(LOGO_URL).openStream();
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw != null) {
                    int s = Math.min(raw.getWidth(), raw.getHeight());
                    Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(out);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    BitmapShader sh = new BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    p.setShader(sh);
                    c.drawCircle(s/2f, s/2f, s/2f, p);
                    logoBitmap = out;
                }
            } catch (Exception ignored) {}
            handler.post(this::createFloatingButton);
        }).start();
    }

    private void createFloatingButton() {
        int size = dp(currentSizeDp);

        floatingView = new FrameLayout(this);

        // Circle gradient background
        GradientDrawable circle = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFE91E8C, 0xFF9B27E6}
        );
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(dp(2), 0xFFFFFFFF);
        floatingView.setBackground(circle);
        floatingView.setElevation(dp(8));

        // Logo image
        logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(size, size);
        ip.gravity = Gravity.CENTER;
        logoView.setLayoutParams(ip);
        if (logoBitmap != null) {
            logoView.setImageBitmap(logoBitmap);
        }
        floatingView.addView(logoView);

        // Fallback "RSY" text if no logo
        if (logoBitmap == null) {
            TextView fallback = new TextView(this);
            fallback.setText("RSY");
            fallback.setTextColor(Color.WHITE);
            fallback.setTextSize(10f);
            fallback.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            fallback.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(size, size);
            fp.gravity = Gravity.CENTER;
            fallback.setLayoutParams(fp);
            floatingView.addView(fallback);
        }

        floatParams = new WindowManager.LayoutParams(
            size, size,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        floatParams.gravity = Gravity.TOP | Gravity.START;
        floatParams.x = dp(10);
        floatParams.y = dp(200);

        windowManager.addView(floatingView, floatParams);
        attachFloatTouchListener();
    }

    private void attachFloatTouchListener() {
        floatingView.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = floatParams.x;
                    initialY = floatParams.y;
                    initialTouchX = e.getRawX();
                    initialTouchY = e.getRawY();
                    pressStartTime = System.currentTimeMillis();
                    isDragging = false;
                    handler.postDelayed(longPressRunnable, 600);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - initialTouchX;
                    float dy = e.getRawY() - initialTouchY;
                    if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) {
                        isDragging = true;
                        handler.removeCallbacks(longPressRunnable);
                    }
                    if (isDragging) {
                        floatParams.x = initialX + (int)dx;
                        floatParams.y = initialY + (int)dy;
                        windowManager.updateViewLayout(floatingView, floatParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    long elapsed = System.currentTimeMillis() - pressStartTime;
                    float moved = Math.abs(e.getRawX()-initialTouchX) + Math.abs(e.getRawY()-initialTouchY);
                    if (!isDragging && elapsed < 500 && moved < dp(10)) {
                        toggleMiniApp();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    return true;
            }
            return false;
        });
    }

    // ── Long press menu ──
    private final Runnable longPressRunnable = this::showOptionsPopup;

    private void showOptionsPopup() {
        // Pulse effect
        floatingView.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100)
            .withEndAction(() -> floatingView.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
            .start();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(20), dp(24), dp(24));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1A1A2E);
        cardBg.setCornerRadius(dp(18));
        cardBg.setStroke(dp(1), 0xFFE91E8C);
        card.setBackground(cardBg);
        card.setElevation(dp(20));

        // Title
        TextView title = new TextView(this);
        title.setText("🎮 RSY Battle Button");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        // ── Resize seekbar ──
        TextView resizeLabel = new TextView(this);
        resizeLabel.setText("📐 Size adjust karo");
        resizeLabel.setTextColor(0xFFCCCCCC);
        resizeLabel.setTextSize(12f);
        resizeLabel.setPadding(0, dp(16), 0, dp(4));
        card.addView(resizeLabel);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(80); // 32dp to 112dp
        seekBar.setProgress(currentSizeDp - 32);
        LinearLayout.LayoutParams sbP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbP.setMargins(0, 0, 0, dp(16));
        seekBar.setLayoutParams(sbP);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) {
                    currentSizeDp = 32 + progress;
                    rebuildFloatingButton();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(seekBar);

        // ── Buttons row ──
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);

        TextView cancelBtn = makeBtn("✖ Raho", 0xFF444444);
        TextView deleteBtn = makeBtn("🗑 Hatao", 0xFFE91E8C);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(dp(8), 0, dp(8), 0);
        btns.addView(cancelBtn, bp);
        btns.addView(deleteBtn, bp);
        card.addView(btns);

        FrameLayout.LayoutParams cardP = new FrameLayout.LayoutParams(dp(300), FrameLayout.LayoutParams.WRAP_CONTENT);
        cardP.gravity = Gravity.CENTER;
        overlay.addView(card, cardP);

        WindowManager.LayoutParams op = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        windowManager.addView(overlay, op);

        cancelBtn.setOnClickListener(v -> { try { windowManager.removeView(overlay); } catch(Exception ex) {} });
        deleteBtn.setOnClickListener(v -> {
            try { windowManager.removeView(overlay); } catch(Exception ex) {}
            stopSelf();
        });
        overlay.setOnClickListener(v -> { try { windowManager.removeView(overlay); } catch(Exception ex) {} });
    }

    private TextView makeBtn(String text, int color) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        return btn;
    }

    private void rebuildFloatingButton() {
        int oldX = floatParams.x, oldY = floatParams.y;
        try { windowManager.removeView(floatingView); } catch(Exception e) {}
        int size = dp(currentSizeDp);
        floatingView = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFE91E8C, 0xFF9B27E6});
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(dp(2), 0xFFFFFFFF);
        floatingView.setBackground(circle);
        floatingView.setElevation(dp(8));
        logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(size, size);
        ip.gravity = Gravity.CENTER;
        logoView.setLayoutParams(ip);
        if (logoBitmap != null) logoView.setImageBitmap(logoBitmap);
        floatingView.addView(logoView);
        if (logoBitmap == null) {
            TextView fb = new TextView(this);
            fb.setText("RSY"); fb.setTextColor(Color.WHITE); fb.setTextSize(10f);
            fb.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams fp2 = new FrameLayout.LayoutParams(size, size);
            fp2.gravity = Gravity.CENTER;
            fb.setLayoutParams(fp2);
            floatingView.addView(fb);
        }
        floatParams = new WindowManager.LayoutParams(size, size,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        floatParams.gravity = Gravity.TOP | Gravity.START;
        floatParams.x = oldX; floatParams.y = oldY;
        windowManager.addView(floatingView, floatParams);
        attachFloatTouchListener();
    }

    // ── Mini App ──
    private void toggleMiniApp() {
        if (isMiniOpen) closeMiniApp(); else openMiniApp();
    }

    private void openMiniApp() {
        if (miniAppView != null) return;
        isMiniOpen = true;

        android.webkit.WebView wv = new android.webkit.WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.loadUrl(APP_URL);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0E1330);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), 0xFFE91E8C);
        container.setBackground(bg);
        container.setElevation(dp(20));

        // Draggable Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setBackgroundColor(0xFF0E1330);

        // Drag handle icon
        TextView drag = new TextView(this);
        drag.setText("⠿ ");
        drag.setTextColor(0xFF888888);
        drag.setTextSize(16f);
        header.addView(drag);

        TextView titleTv = new TextView(this);
        titleTv.setText("🎮 RSY Battle");
        titleTv.setTextColor(0xFFE91E8C);
        titleTv.setTextSize(13f);
        titleTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleTv.setLayoutParams(lp);
        header.addView(titleTv);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(16f);
        closeBtn.setPadding(dp(12), dp(8), dp(4), dp(8));
        closeBtn.setOnClickListener(v -> closeMiniApp());
        header.addView(closeBtn);

        container.addView(header);
        container.addView(wv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        miniAppView = container;

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = (int)(dm.widthPixels * 0.88f);
        int h = (int)(dm.heightPixels * 0.68f);

        miniParams = new WindowManager.LayoutParams(w, h,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        miniParams.gravity = Gravity.CENTER;
        windowManager.addView(miniAppView, miniParams);

        // Header drag to move mini window
        final int[] mInitX = {miniParams.x};
        final int[] mInitY = {miniParams.y};
        final float[] mTouchX = {0};
        final float[] mTouchY = {0};
        header.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mInitX[0] = miniParams.x; mInitY[0] = miniParams.y;
                    mTouchX[0] = e.getRawX(); mTouchY[0] = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    miniParams.x = mInitX[0] + (int)(e.getRawX() - mTouchX[0]);
                    miniParams.y = mInitY[0] + (int)(e.getRawY() - mTouchY[0]);
                    try { windowManager.updateViewLayout(miniAppView, miniParams); } catch(Exception ex) {}
                    return true;
            }
            return false;
        });

        miniAppView.setAlpha(0f);
        miniAppView.setScaleX(0.85f);
        miniAppView.setScaleY(0.85f);
        miniAppView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    private void closeMiniApp() {
        if (miniAppView == null) return;
        miniAppView.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(150)
            .withEndAction(() -> {
                try { windowManager.removeView(miniAppView); } catch(Exception e) {}
                miniAppView = null;
                isMiniOpen = false;
            }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (floatingView != null) try { windowManager.removeView(floatingView); } catch(Exception e) {}
        if (miniAppView != null) try { windowManager.removeView(miniAppView); } catch(Exception e) {}
    }
}
