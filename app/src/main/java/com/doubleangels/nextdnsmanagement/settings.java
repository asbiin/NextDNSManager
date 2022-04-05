package com.doubleangels.nextdnsmanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.AddTrace;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Objects;
import java.util.UUID;

import io.sentry.ITransaction;
import io.sentry.Sentry;

public class settings extends AppCompatActivity {

    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    @AddTrace(name = "settings_create")
    protected void onCreate(Bundle savedInstanceState) {
        ITransaction settings_create_transaction = Sentry.startTransaction("onCreate()", "settings");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        try {
            final SharedPreferences sharedPreferences = getSharedPreferences("mainSharedPreferences", MODE_PRIVATE);
            boolean isManualDisableAnalytics = sharedPreferences.getBoolean("manualDisableAnalytics", false);
            String storedUniqueKey = sharedPreferences.getString("uuid", "defaultValue");
            String uniqueKey;
            if (storedUniqueKey.contains("defaultValue")) {
                uniqueKey = UUID.randomUUID().toString();
                sharedPreferences.edit().putString("uuid", uniqueKey).apply();
            } else {
                uniqueKey = sharedPreferences.getString("uuid", "defaultValue");
            }
            FirebaseCrashlytics.getInstance().setUserId(uniqueKey);
            FirebaseCrashlytics.getInstance().log("Set UUID to: " + uniqueKey);
            Sentry.addBreadcrumb("Set UUID to: " + uniqueKey);

            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            if (isManualDisableAnalytics) {
                FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true);
            }

            Trace remoteConfigStartTrace = FirebasePerformance.getInstance().newTrace("remoteConfig_setup");
            remoteConfigStartTrace.start();
            mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(1800).build();
            mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);
            mFirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);
            remoteConfigStartTrace.stop();

            Window window = this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            Trace remoteConfigFetchTrace = FirebasePerformance.getInstance().newTrace("remoteConfig_fetch");
            remoteConfigFetchTrace.start();
            mFirebaseRemoteConfig.fetchAndActivate().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    boolean updated = task.getResult();
                    FirebaseCrashlytics.getInstance().log("Remote config fetch succeeded: " + updated);
                    Sentry.addBreadcrumb("Remote config fetch succeeded: " + updated);
                    if (updated) {
                        Sentry.setTag("remote_config_fetched", "true");
                    } else {
                        Sentry.setTag("remote_config_fetched", "false");
                    }
                    mFirebaseRemoteConfig.activate();
                }
            });
            remoteConfigFetchTrace.stop();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_background_color));
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_background_color));
            SwitchCompat manualDisableAnalytics = findViewById(R.id.manual_disable_analytics);
            TextView versionNumber = findViewById(R.id.versionNumberTextView);
            versionNumber.setText(BuildConfig.VERSION_NAME);
            ImageView whitelist = findViewById(R.id.whitelistImageView);

            ConnectivityManager connectivityManager = (ConnectivityManager) this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = connectivityManager.getActiveNetwork();
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            updateVisualIndicator(linkProperties, networkInfo);
            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    super.onLinkPropertiesChanged(network, linkProperties);
                    updateVisualIndicator(linkProperties, networkInfo);
                }
            });

            manualDisableAnalytics.setChecked(isManualDisableAnalytics);
            manualDisableAnalytics.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    sharedPreferences.edit().putBoolean("manualDisableAnalytics", true).apply();
                    FirebaseCrashlytics.getInstance().log("Changed analytics to enabled.");
                    Sentry.addBreadcrumb("Changed analytics to enabled.");
                    Sentry.setTag("analytics_manual_control", "enabled");

                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", "set_manual_disable_analytics");
                    mFirebaseAnalytics.logEvent("manual_disable_analytics", bundle);
                    sharedPreferences.edit().putBoolean("manualDisableAnalytics", false).apply();
                    FirebaseCrashlytics.getInstance().log("Changed analytics to disabled.");
                    Sentry.addBreadcrumb("Changed analytics to disabled.");
                    Sentry.setTag("analytics_manual_control", "disabled");
                    Toast.makeText(getApplicationContext(),"Saved!",Toast.LENGTH_SHORT).show();
                }
            });

            ImageView statusIcon = findViewById(R.id.connectionStatus);
            statusIcon.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "help_icon");
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
                Intent helpIntent = new Intent(v.getContext(), help.class);
                startActivity(helpIntent);
            });

            whitelist.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("https://nextdns-management.firebaseapp.com/whitelist.txt"));
                startActivity(intent);
            });
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(e);
            Sentry.captureException(e);
        } finally {
            settings_create_transaction.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_back_only, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Bundle bundle = new Bundle();
        if (item.getItemId() == R.id.back) {
            bundle.putString("id", "back");
            mFirebaseAnalytics.logEvent("toolbar_action", bundle);
            Intent mainIntent = new Intent(this, MainActivity.class);
            startActivity(mainIntent);
        }
        return super.onContextItemSelected(item);
    }

    @AddTrace(name = "update_visual_indicator")
    public void updateVisualIndicator(LinkProperties linkProperties, NetworkInfo networkInfo) {
        ITransaction update_visual_indicator_transaction = Sentry.startTransaction("updateVisualIndicator()", "help");
        try {
            if (networkInfo != null) {
                if (linkProperties.isPrivateDnsActive()) {
                    if (linkProperties.getPrivateDnsServerName() != null) {
                        if (linkProperties.getPrivateDnsServerName().contains("nextdns")) {
                            ImageView connectionStatus = findViewById(R.id.connectionStatus);
                            connectionStatus.setImageResource(R.drawable.success);
                            connectionStatus.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.green));
                            FirebaseCrashlytics.getInstance().log("Set connection status to NextDNS.");
                            Sentry.addBreadcrumb("Set connection status to NextDNS.");
                            Sentry.setTag("private_dns", "nextdns");
                        } else {
                            ImageView connectionStatus = findViewById(R.id.connectionStatus);
                            connectionStatus.setImageResource(R.drawable.success);
                            connectionStatus.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.yellow));
                            FirebaseCrashlytics.getInstance().log("Set connection status to private DNS.");
                            Sentry.addBreadcrumb("Set connection status to private DNS.");
                            Sentry.setTag("private_dns", "private");
                        }
                    } else {
                        ImageView connectionStatus = findViewById(R.id.connectionStatus);
                        connectionStatus.setImageResource(R.drawable.success);
                        connectionStatus.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.yellow));
                        FirebaseCrashlytics.getInstance().log("Set connection status to private DNS.");
                        Sentry.addBreadcrumb("Set connection status to private DNS.");
                        Sentry.setTag("private_dns", "private");
                    }
                } else {
                    ImageView connectionStatus = findViewById(R.id.connectionStatus);
                    connectionStatus.setImageResource(R.drawable.failure);
                    connectionStatus.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.red));
                    FirebaseCrashlytics.getInstance().log("Set connection status to insecure DNS.");
                    Sentry.addBreadcrumb("Set connection status to insecure DNS.");
                    Sentry.setTag("private_dns", "insecure");
                }
            } else {
                ImageView connectionStatus = findViewById(R.id.connectionStatus);
                connectionStatus.setImageResource(R.drawable.failure);
                connectionStatus.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.gray));
                FirebaseCrashlytics.getInstance().log("Set connection status to no connection.");
                Sentry.addBreadcrumb("Set connection status to no connection.");
                Sentry.setTag("private_dns", "no_connection");
            }
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(e);
            Sentry.captureException(e);
        } finally {
            update_visual_indicator_transaction.finish();
        }
    }
}