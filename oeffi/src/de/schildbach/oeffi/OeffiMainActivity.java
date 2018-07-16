/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.oeffi.plans.PlansPickerActivity;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.ChangelogDialogBuilder;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Downloader;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.oeffi.util.NavigationMenuAdapter;
import de.schildbach.oeffi.util.UiThreadExecutor;
import de.schildbach.pte.NetworkId;
import de.schildbach.wallet.integration.android.BitcoinIntegration;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.PopupMenu;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public abstract class OeffiMainActivity extends OeffiActivity {
    protected NetworkId network;

    private DrawerLayout navigationDrawerLayout;
    private RecyclerView navigationDrawerListView;
    private View navigationDrawerFooterView;
    private View navigationDrawerFooterHeartView;

    private int versionCode, lastVersionCode;

    private final Handler handler = new Handler();

    private static final int DIALOG_NEW_VERSION = 101;
    private static final int DIALOG_MESSAGE = 102;

    private static final Logger log = LoggerFactory.getLogger(OeffiMainActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize network
        network = prefsGetNetworkId();
        ErrorReporter.getInstance().setNetworkId(network);

        final long now = System.currentTimeMillis();
        versionCode = applicationVersionCode();
        lastVersionCode = prefs.getInt(Constants.PREFS_KEY_LAST_VERSION, 0);

        if (prefsGetNetwork() == null) {
            NetworkPickerActivity.start(this);

            prefs.edit().putLong(Constants.PREFS_KEY_LAST_INFO_AT, now).commit();

            downloadAndProcessMessages(prefsGetNetwork());
        } else if (versionCode != lastVersionCode) {
            prefs.edit().putInt(Constants.PREFS_KEY_LAST_VERSION, versionCode).commit();

            if (versionCode > lastVersionCode) {
                if (lastVersionCode > 0 && prefs.getBoolean(Constants.PREFS_KEY_SHOW_CHANGELOG, true))
                    showDialog(DIALOG_NEW_VERSION);
            }
        } else {
            downloadAndProcessMessages(prefsGetNetwork());
        }
    }

    @Override
    protected void onResume() {
        checkChangeNetwork();

        super.onResume();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateNavigation();
    }

    protected abstract String taskName();

    protected void setActionBarSecondaryTitleFromNetwork() {
        final String network = this.network != null ? this.network.name() : prefsGetNetwork();
        if (network != null)
            getMyActionBar().setSecondaryTitle(NetworkResources.instance(this, network).label);
    }

    private void checkChangeNetwork() {
        final NetworkId newNetwork = prefsGetNetworkId();

        if (newNetwork != null && newNetwork != network) {
            log.info("Network change detected: {} -> {}", network, newNetwork);
            ErrorReporter.getInstance().setNetworkId(newNetwork);

            network = newNetwork;
            onChangeNetwork(network);
        }
    }

    protected void onChangeNetwork(final NetworkId network) {
    }

    protected void initNavigation() {
        navigationDrawerLayout = (DrawerLayout) findViewById(R.id.navigation_drawer_layout);
        navigationDrawerListView = (RecyclerView) findViewById(R.id.navigation_drawer_list);
        navigationDrawerFooterView = findViewById(R.id.navigation_drawer_footer);
        navigationDrawerFooterHeartView = findViewById(R.id.navigation_drawer_footer_heart);

        final AnimatorSet heartbeat = (AnimatorSet) AnimatorInflater.loadAnimator(OeffiMainActivity.this,
                R.animator.heartbeat);
        heartbeat.setTarget(navigationDrawerFooterHeartView);

        final MyActionBar actionBar = getMyActionBar();

        final NavigationMenuAdapter menuAdapter = new NavigationMenuAdapter(this,
                new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(final MenuItem item) {
                        onOptionsItemSelected(item);
                        navigationDrawerLayout.closeDrawers();
                        return false;
                    }
                });
        final Menu menu = menuAdapter.getMenu();
        onCreateOptionsMenu(menu);
        onPrepareOptionsMenu(menu);

        navigationDrawerListView.setLayoutManager(new LinearLayoutManager(this));
        navigationDrawerListView
                .addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        navigationDrawerListView.setAdapter(menuAdapter);

        navigationDrawerLayout.setDrawerShadow(R.drawable.view_shadow_right, Gravity.LEFT);
        navigationDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            public void onDrawerOpened(final View drawerView) {
                handler.postDelayed(new Runnable() {
                    public void run() {
                        heartbeat.start();
                    }
                }, 2000);
            }

            public void onDrawerClosed(final View drawerView) {
            }

            public void onDrawerSlide(final View drawerView, final float slideOffset) {
            }

            public void onDrawerStateChanged(final int newState) {
            }
        });

        navigationDrawerFooterView.setOnClickListener(new View.OnClickListener() {
            public void onClick(final View v) {
                handler.removeCallbacksAndMessages(null);
                heartbeat.start();
            }
        });

        actionBar.setDrawer(new OnClickListener() {
            public void onClick(final View v) {
                toggleNavigation();
            }
        });

        updateNavigation();
    }

    protected void updateNavigation() {
        if (navigationDrawerFooterView != null)
            navigationDrawerFooterView.setVisibility(
                    getResources().getBoolean(R.bool.layout_navigation_drawer_footer_show) ? View.VISIBLE : View.GONE);
    }

    protected boolean isNavigationOpen() {
        return navigationDrawerLayout.isDrawerOpen(Gravity.LEFT);
    }

    private void toggleNavigation() {
        if (navigationDrawerLayout.isDrawerOpen(Gravity.LEFT))
            navigationDrawerLayout.closeDrawer(Gravity.LEFT);
        else
            navigationDrawerLayout.openDrawer(Gravity.LEFT);
    }

    protected void closeNavigation() {
        navigationDrawerLayout.closeDrawer(Gravity.LEFT);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.global_options, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem stationsItem = menu.findItem(R.id.global_options_stations);
        stationsItem.setChecked(this instanceof StationsActivity);

        final MenuItem directionsItem = menu.findItem(R.id.global_options_directions);
        directionsItem.setChecked(this instanceof DirectionsActivity);

        final MenuItem plansItem = menu.findItem(R.id.global_options_plans);
        plansItem.setChecked(this instanceof PlansPickerActivity);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.global_options_stations: {
            if (this instanceof StationsActivity)
                return true;

            final Intent intent = new Intent(this, StationsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);

            return true;
        }

        case R.id.global_options_directions: {
            if (this instanceof DirectionsActivity)
                return true;

            final Intent intent = new Intent(this, DirectionsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            if (this instanceof StationsActivity)
                overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            else
                overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);

            return true;
        }

        case R.id.global_options_plans: {
            if (this instanceof PlansPickerActivity)
                return true;

            final Intent intent = new Intent(this, PlansPickerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);

            return true;
        }

        case R.id.global_options_preferences: {
            startActivity(new Intent(this, PreferencesActivity.class));
            return true;
        }

        case R.id.global_options_about: {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        case R.id.global_options_donate: {
            final PopupMenu popup = new PopupMenu(this, getMyActionBar());
            popup.inflate(R.menu.donate_menu);
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(final MenuItem item) {
                    if (item.getItemId() == R.id.donate_menu_bitcoin) {
                        BitcoinIntegration.request(OeffiMainActivity.this, Constants.BITCOIN_ADDRESS);
                        return true;
                    } else if (item.getItemId() == R.id.donate_menu_flattr) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.FLATTR_THING_URL)));
                        return true;
                    } else if (item.getItemId() == R.id.donate_menu_euro) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(getString(R.string.about_donate_euro_summary))));
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            popup.show();
            return true;
        }
        }

        return false;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleNavigation();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id, final Bundle bundle) {
        switch (id) {
        case DIALOG_NEW_VERSION:
            return ChangelogDialogBuilder.get(this, versionCode, applicationVersionName(), applicationVersionFlavor(),
                    lastVersionCode, taskName()).create();
        case DIALOG_MESSAGE:
            return messageDialog(bundle);
        }

        return super.onCreateDialog(id, bundle);
    }

    private void downloadAndProcessMessages(final String network) {
        final HttpUrl.Builder remoteUrl = Constants.MESSAGES_BASE_URL.newBuilder();
        final StringBuilder remoteFileName = new StringBuilder("messages");
        final String flavor = applicationVersionFlavor();
        if (flavor != null)
            remoteFileName.append('-').append(flavor);
        remoteFileName.append(".txt");
        remoteUrl.addPathSegment(remoteFileName.toString());
        remoteUrl.addEncodedQueryParameter("installer",
                Strings.nullToEmpty(getPackageManager().getInstallerPackageName(getPackageName())));
        remoteUrl.addQueryParameter("task", taskName());
        final File localFile = new File(getFilesDir(), "messages.txt");
        final Downloader downloader = new Downloader(getCacheDir());
        final ListenableFuture<Integer> download = downloader.download(remoteUrl.build(), localFile);
        Futures.addCallback(download, new FutureCallback<Integer>() {
            public void onSuccess(final @Nullable Integer status) {
                processMessages(network);
            }

            public void onFailure(final Throwable t) {
            }
        }, new UiThreadExecutor());
    }

    private void processMessages(final String network) {
        BufferedReader reader = null;
        String line = null;

        final File indexFile = new File(getFilesDir(), "messages.txt");

        try {
            reader = new BufferedReader(new InputStreamReader(
                    indexFile.exists() ? new FileInputStream(indexFile) : getAssets().open("messages.txt"),
                    Charsets.UTF_8));

            while (true) {
                line = reader.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#')
                    continue;

                try {
                    if (processMessageLine(network, line))
                        break;
                } catch (final Exception x) {
                    log.info("Problem parsing message '" + line + "': ", x);
                }
            }
        } catch (final IOException x) {
            // ignore
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException x2) {
                }
            }
        }
    }

    private final Pattern PATTERN_KEY_VALUE = Pattern.compile("([\\w-]+):(.*)");

    private boolean processMessageLine(final String network, final String line) throws ParseException {
        final Iterator<String> fieldIterator = Splitter.on('|').trimResults().split(line).iterator();
        final String id = fieldIterator.next();
        final String conditions = fieldIterator.next();
        final String repeat = Strings.emptyToNull(fieldIterator.next());
        final String action = fieldIterator.next();

        // check conditions
        if (!Strings.isNullOrEmpty(conditions)) {
            final Map<String, String> conditionsMap = Splitter.on(Pattern.compile("\\s+")).trimResults()
                    .withKeyValueSeparator(":").split(conditions);
            for (final Map.Entry<String, String> conditionEntry : conditionsMap.entrySet()) {
                final String name = conditionEntry.getKey();
                final String value = conditionEntry.getValue();

                if (name.equals("min-sdk")) {
                    final int minSdk = Integer.parseInt(value);
                    if (Build.VERSION.SDK_INT < minSdk)
                        return false;
                } else if (name.equals("max-sdk")) {
                    final int maxSdk = Integer.parseInt(value);
                    if (Build.VERSION.SDK_INT > maxSdk)
                        return false;
                } else if (name.equals("min-version")) {
                    final int version = Integer.parseInt(value);
                    if (applicationVersionCode() < version)
                        return false;
                } else if (name.equals("max-version")) {
                    final int version = Integer.parseInt(value);
                    if (applicationVersionCode() > version)
                        return false;
                } else if (name.equals("version")) {
                    final int version = Integer.parseInt(value);
                    if (applicationVersionCode() >= version)
                        return false;
                } else if (name.equals("network")) {
                    if (network == null || !value.equalsIgnoreCase(network))
                        return false;
                } else if (name.equals("lang")) {
                    if (!value.equalsIgnoreCase(Locale.getDefault().getLanguage()))
                        return false;
                } else if (name.equals("task")) {
                    if (!(taskName().equalsIgnoreCase(value)))
                        return false;
                } else if (name.equals("first-install-before")) {
                    final Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
                    if (date.getTime() >= applicationFirstInstallTime())
                        return false;
                } else if (name.equals("prefs-show-info")) {
                    final boolean requiredValue = "true".equalsIgnoreCase(value);
                    final boolean actualValue = prefs.getBoolean(Constants.PREFS_KEY_SHOW_INFO, true);

                    if (actualValue != requiredValue)
                        return false;
                } else if (name.equals("limit-info")) {
                    if (System.currentTimeMillis() < prefs.getLong(Constants.PREFS_KEY_LAST_INFO_AT, 0)
                            + parseTimeExp(value))
                        return false;
                } else if (name.equals("installed-package")) {
                    final List<PackageInfo> installedPackages = getPackageManager().getInstalledPackages(0);
                    boolean match = false;
                    loop: for (final String packageName : Splitter.on(',').trimResults().splitToList(value)) {
                        for (final PackageInfo pi : installedPackages) {
                            if (pi.packageName.equals(packageName)) {
                                match = true;
                                break loop;
                            }
                        }
                    }
                    if (!match)
                        return false;
                } else if (name.equals("not-installed-package")) {
                    final List<PackageInfo> installedPackages = getPackageManager().getInstalledPackages(0);
                    for (final String packageName : Splitter.on(',').trimResults().splitToList(value)) {
                        for (final PackageInfo pi : installedPackages)
                            if (pi.packageName.equals(packageName))
                                return false;
                    }
                } else if (name.equals("installer")) {
                    final String installer = Strings
                            .nullToEmpty(getPackageManager().getInstallerPackageName(getPackageName()));
                    if (!value.equalsIgnoreCase(installer))
                        return false;
                } else if (name.equals("not-installer")) {
                    final String installer = Strings
                            .nullToEmpty(getPackageManager().getInstallerPackageName(getPackageName()));
                    if (value.equalsIgnoreCase(installer))
                        return false;
                } else {
                    log.info("Unhandled condition: '{}={}'", name, value);
                }
            }
        }

        // check repeat
        final SharedPreferences messagesPrefs = getSharedPreferences("messages", Context.MODE_PRIVATE);
        if (!"always".equals(repeat)) {
            if (repeat == null || repeat.equals("once")) {
                if (messagesPrefs.contains(id))
                    return false;
            } else {
                if (System.currentTimeMillis() < messagesPrefs.getLong(id, 0) + parseTimeExp(repeat))
                    return false;
            }
        }

        log.info("Picked message: '{}'", line);

        // fetch and show message
        if ("info".equals(action) || "warning".equals(action)) {
            final HttpUrl.Builder url = Constants.MESSAGES_BASE_URL.newBuilder()
                    .addEncodedPathSegment(id + (Locale.getDefault().getLanguage().equals("de") ? "-de" : "") + ".txt");
            final Request.Builder request = new Request.Builder();
            request.url(url.build());
            final Call call = Application.OKHTTP_CLIENT.newCall(request.build());
            call.enqueue(new Callback() {
                public void onResponse(final Call call, final Response response) throws IOException {
                    try {
                        if (response.isSuccessful()) {
                            final Bundle message = new Bundle();
                            message.putString("action", action);

                            final BufferedReader reader = new BufferedReader(response.body().charStream());
                            String line;
                            String lastKey = null;

                            while (true) {
                                line = reader.readLine();
                                if (line == null)
                                    break;
                                line = line.trim();
                                if (!line.isEmpty() && line.charAt(0) == '#')
                                    continue;

                                final Matcher m = PATTERN_KEY_VALUE.matcher(line);
                                final boolean matches = m.matches();
                                if (matches) {
                                    final String key = m.group(1);
                                    final String value = m.group(2).trim();

                                    message.putString(key, value + " ");
                                    lastKey = key;
                                } else if (lastKey != null) {
                                    if (line.isEmpty())
                                        line = "\n\n";

                                    message.putString(lastKey, message.getString(lastKey) + line);
                                } else {
                                    throw new IllegalStateException("line needs to match 'key: value': '" + line + "'");
                                }
                            }

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if (isFinishing())
                                        return;

                                    showDialog(DIALOG_MESSAGE, message);

                                    final long now = System.currentTimeMillis();
                                    messagesPrefs.edit().putLong(id, now).commit();
                                    if ("info".equals(action))
                                        prefs.edit().putLong(Constants.PREFS_KEY_LAST_INFO_AT, now).commit();
                                }
                            });
                        } else {
                            log.info("Got '{}: {}' when fetching message from: '{}'", response.code(),
                                    response.message(), url);
                        }
                    } finally {
                        response.close();
                    }
                }

                public void onFailure(final Call call, final IOException x) {
                    log.info("Problem fetching message from: '" + url + "'", x);
                }
            });
        }

        return true;
    }

    private class MessageOnClickListener implements DialogInterface.OnClickListener {
        private final String link;

        public MessageOnClickListener(final String link) {
            this.link = link;
        }

        public void onClick(final DialogInterface dialog, final int which) {
            if ("select-network".equals(link))
                NetworkPickerActivity.start(OeffiMainActivity.this);
            else
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        }
    }

    private Dialog messageDialog(final Bundle message) {
        final DialogBuilder builder = DialogBuilder.get(this);
        final String action = message.getString("action");
        if ("info".equals(action))
            builder.setIcon(R.drawable.ic_info_grey600_24dp);
        else if ("warning".equals(action))
            builder.setIcon(R.drawable.ic_warning_amber_24dp);
        final String title = message.getString("title");
        if (title != null)
            builder.setTitle(title);
        final String body = message.getString("body");
        builder.setMessage(body);
        final String positive = message.getString("button-positive");
        if (positive != null) {
            final Iterator<String> parts = Splitter.on('|').trimResults().limit(2).split(positive).iterator();
            final String text = parts.next();
            if ("dismiss".equals(text))
                builder.setPositiveButton(R.string.alert_hint_neutral, null);
            else
                builder.setPositiveButton(text, parts.hasNext() ? new MessageOnClickListener(parts.next()) : null);
        }
        final String neutral = message.getString("button-neutral");
        if (neutral != null) {
            final Iterator<String> parts = Splitter.on('|').trimResults().limit(2).split(neutral).iterator();
            final String text = parts.next();
            if ("dismiss".equals(text))
                builder.setNeutralButton(R.string.alert_hint_neutral, null);
            else
                builder.setNeutralButton(text, parts.hasNext() ? new MessageOnClickListener(parts.next()) : null);
        }
        final String negative = message.getString("button-negative");
        if (negative != null) {
            final Iterator<String> parts = Splitter.on('|').trimResults().limit(2).split(negative).iterator();
            final String text = parts.next();
            if ("dismiss".equals(text))
                builder.setNegativeButton(R.string.alert_hint_neutral, null);
            else
                builder.setNegativeButton(text, parts.hasNext() ? new MessageOnClickListener(parts.next()) : null);
        } else {
            builder.setNegativeButton(R.string.alert_hint_neutral, null);
        }

        final Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private long parseTimeExp(final String exp) {
        if (exp.endsWith("h"))
            return DateUtils.HOUR_IN_MILLIS * Integer.parseInt(exp.substring(0, exp.length() - 1));
        else if (exp.endsWith("d"))
            return DateUtils.DAY_IN_MILLIS * Integer.parseInt(exp.substring(0, exp.length() - 1));
        else if (exp.endsWith("w"))
            return DateUtils.WEEK_IN_MILLIS * Integer.parseInt(exp.substring(0, exp.length() - 1));
        else
            throw new IllegalArgumentException("cannot parse time expression: '" + exp + "'");
    }
}
