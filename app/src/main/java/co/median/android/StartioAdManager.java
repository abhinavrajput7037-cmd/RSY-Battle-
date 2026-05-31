package co.median.android;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;
import com.startapp.sdk.ads.banner.Banner;
import com.startapp.sdk.ads.banner.BannerListener;

public class StartioAdManager {

    private static final int INTERSTITIAL_INTERVAL = 150 * 1000;
    private static final int VIDEO_INTERVAL        = 5 * 60 * 1000;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private StartAppAd interstitialAd;
    private StartAppAd videoAd;
    private boolean interstitialReady = false;
    private boolean videoReady        = false;
    private boolean isPaused          = false;
    private boolean bannerShown       = false;
    private FrameLayout bottomBannerContainer;

    // Named listener fields — avoids 'this' confusion inside Runnable
    private final AdEventListener interstitialListener = new AdEventListener() {
        @Override
        public void onReceiveAd(Ad ad) {
            interstitialReady = true;
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    showInterstitial();
                    handler.postDelayed(interstitialCycleRunnable, INTERSTITIAL_INTERVAL);
                }
            }, 3000);
        }
        @Override
        public void onFailedToReceiveAd(Ad ad) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    interstitialAd.loadAd(interstitialListener);
                }
            }, 30000);
        }
    };

    private final AdEventListener videoListener = new AdEventListener() {
        @Override
        public void onReceiveAd(Ad ad) {
            videoReady = true;
            handler.postDelayed(videoCycleRunnable, VIDEO_INTERVAL);
        }
        @Override
        public void onFailedToReceiveAd(Ad ad) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    videoAd.loadAd(videoListener);
                }
            }, 30000);
        }
    };

    private final AdEventListener interstitialReloadListener = new AdEventListener() {
        @Override public void onReceiveAd(Ad ad) { interstitialReady = true; }
        @Override public void onFailedToReceiveAd(Ad ad) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    interstitialAd.loadAd(interstitialReloadListener);
                }
            }, 15000);
        }
    };

    private final AdEventListener videoReloadListener = new AdEventListener() {
        @Override public void onReceiveAd(Ad ad) { videoReady = true; }
        @Override public void onFailedToReceiveAd(Ad ad) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    videoAd.loadAd(videoReloadListener);
                }
            }, 15000);
        }
    };

    private final Runnable interstitialCycleRunnable = new Runnable() {
        @Override public void run() {
            showInterstitial();
            handler.postDelayed(interstitialCycleRunnable, INTERSTITIAL_INTERVAL);
        }
    };

    private final Runnable videoCycleRunnable = new Runnable() {
        @Override public void run() {
            showVideoAd();
            handler.postDelayed(videoCycleRunnable, VIDEO_INTERVAL);
        }
    };

    public StartioAdManager(Activity activity) {
        this.activity = activity;
    }

    public void init(String appId) {
        StartAppSDK.init(activity, appId, true);

        bottomBannerContainer = activity.findViewById(R.id.startio_bottom_banner_container);

        interstitialAd = new StartAppAd(activity);
        interstitialAd.loadAd(interstitialListener);

        videoAd = new StartAppAd(activity);
        videoAd.loadAd(videoListener);

        showBottomBanner();
    }

    public void onPause() {
        isPaused = true;
        handler.removeCallbacks(interstitialCycleRunnable);
        handler.removeCallbacks(videoCycleRunnable);
    }

    public void onResume() {
        isPaused = false;
        // Cycle wapas shuru karo — pause mein cancel ho gayi thi
        handler.removeCallbacks(interstitialCycleRunnable);
        handler.removeCallbacks(videoCycleRunnable);
        handler.postDelayed(interstitialCycleRunnable, INTERSTITIAL_INTERVAL);
        handler.postDelayed(videoCycleRunnable, VIDEO_INTERVAL);
    }

    public void onPageLoaded() {}

    private void showInterstitial() {
        if (isPaused || !interstitialReady) return;
        try {
            interstitialReady = false;
            interstitialAd.showAd();
            interstitialAd.loadAd(interstitialReloadListener);
        } catch (Exception ignored) {}
    }

    private void showVideoAd() {
        if (isPaused || !videoReady) return;
        try {
            videoReady = false;
            videoAd.showAd();
            videoAd.loadAd(videoReloadListener);
        } catch (Exception ignored) {}
    }

    private void showBottomBanner() {
        if (bannerShown) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (bottomBannerContainer == null) return;
                try {
                    Banner banner = new Banner(activity);
                    banner.setBannerListener(new BannerListener() {
                        @Override public void onReceiveAd(View view) {
                            bannerShown = true;
                            bottomBannerContainer.setVisibility(View.VISIBLE);
                        }
                        @Override public void onFailedToReceiveAd(View view) {
                            handler.postDelayed(new Runnable() {
                                @Override public void run() {
                                    bannerShown = false;
                                    showBottomBanner();
                                }
                            }, 30000);
                        }
                        @Override public void onImpression(View view) {}
                        @Override public void onClick(View view) {}
                    });
                    bottomBannerContainer.removeAllViews();
                    bottomBannerContainer.addView(banner);
                } catch (Exception ignored) {}
            }
        });
    }
}
