package com.anton.awaretest;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Wi-Fi Aware (NAN) probe.
 * 1. Check   - feature flag, WifiAwareManager, isAvailable(), characteristics
 * 2. Attach  - asks the framework/HAL to bring Aware up (this is what fills mCapabilities)
 * 3. Publish - publishes a dummy service to prove discovery sessions can start
 * 4. Close   - tears everything down
 */
public class MainActivity extends Activity {

    private static final String TAG = "AwareTest";

    private TextView out;
    private WifiAwareManager aware;
    private WifiAwareSession session;
    private PublishDiscoverySession publishSession;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean avail = aware != null && aware.isAvailable();
            log("broadcast WIFI_AWARE_STATE_CHANGED -> isAvailable=" + avail);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        root.addView(makeButton("1. Check", v -> check()));
        root.addView(makeButton("2. Attach", v -> attach()));
        root.addView(makeButton("3. Publish test service", v -> publish()));
        root.addView(makeButton("4. Close", v -> closeAll()));

        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(12f);
        out.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(out);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        requestPermissions(perms.toArray(new String[0]), 1);

        check();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(stateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeAll();
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private void log(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> out.append(line + "\n"));
    }

    private void check() {
        PackageManager pm = getPackageManager();
        log("--- check ---");
        log("device: " + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        log("FEATURE_WIFI_AWARE: " + pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE));

        aware = (WifiAwareManager) getSystemService(Context.WIFI_AWARE_SERVICE);
        log("WifiAwareManager: " + (aware != null ? "present" : "null"));
        if (aware == null) {
            return;
        }
        log("isAvailable(): " + aware.isAvailable());
        printCharacteristics();
    }

    private void printCharacteristics() {
        if (aware == null) {
            return;
        }
        Characteristics ch = aware.getCharacteristics();
        if (ch == null) {
            log("getCharacteristics(): null (framework has no capabilities from the HAL yet)");
            return;
        }
        log("maxServiceNameLength: " + ch.getMaxServiceNameLength());
        log("maxServiceSpecificInfoLength: " + ch.getMaxServiceSpecificInfoLength());
        log("maxMatchFilterLength: " + ch.getMaxMatchFilterLength());
        log("supportedCipherSuites (bitmask): " + ch.getSupportedCipherSuites());
    }

    private void attach() {
        if (aware == null) {
            log("run Check first");
            return;
        }
        log("--- attach() ---");
        aware.attach(new AttachCallback() {
            @Override
            public void onAttached(WifiAwareSession s) {
                session = s;
                log("onAttached: OK");
                printCharacteristics();
            }

            @Override
            public void onAttachFailed() {
                log("onAttachFailed");
            }

            @Override
            public void onAwareSessionTerminated() {
                log("onAwareSessionTerminated");
                session = null;
            }
        }, handler);
    }

    private void publish() {
        if (session == null) {
            log("attach first");
            return;
        }
        log("--- publish() ---");
        PublishConfig config = new PublishConfig.Builder()
                .setServiceName("awaretest")
                .build();
        session.publish(config, new DiscoverySessionCallback() {
            @Override
            public void onPublishStarted(PublishDiscoverySession s) {
                publishSession = s;
                log("onPublishStarted: OK");
            }

            @Override
            public void onSessionConfigFailed() {
                log("publish: onSessionConfigFailed");
            }

            @Override
            public void onSessionTerminated() {
                log("publish: onSessionTerminated");
                publishSession = null;
            }
        }, handler);
    }

    private void closeAll() {
        if (publishSession != null) {
            publishSession.close();
            publishSession = null;
        }
        if (session != null) {
            session.close();
            session = null;
            log("closed");
        }
    }
}
