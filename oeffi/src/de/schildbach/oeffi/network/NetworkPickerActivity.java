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

package de.schildbach.oeffi.network;

import android.Manifest;
import android.app.ActivityManager.TaskDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Address;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.AreaAware;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.LocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.list.NetworkClickListener;
import de.schildbach.oeffi.network.list.NetworkContextMenuItemListener;
import de.schildbach.oeffi.network.list.NetworkListEntry;
import de.schildbach.oeffi.network.list.NetworksAdapter;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NetworkPickerActivity extends ComponentActivity implements LocationHelper.Callback, NetworkClickListener,
        NetworkContextMenuItemListener {
    public static void start(final Context context) {
        final Intent intent = new Intent(context, NetworkPickerActivity.class);
        context.startActivity(intent);
    }

    private SharedPreferences prefs;

    private MyActionBar actionBar;
    private RecyclerView listView;
    private NetworksAdapter listAdapter;
    private OeffiMapView mapView;

    private final List<NetworkId> lastNetworks = new LinkedList<>();

    private LocationHelper locationHelper;
    private Point deviceLocation;
    private Address deviceAddress;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler handler = new Handler();

    private static final String INDEX_FILENAME = "networks.txt";
    private static final int MAX_LAST_NETWORKS = 3;

    private static final Logger log = LoggerFactory.getLogger(NetworkPickerActivity.class);

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                maybeStartLocation();
            });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        EdgeToEdge.enable(this, Constants.STATUS_BAR_STYLE);
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        locationHelper = new LocationHelper((LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

        backgroundThread = new HandlerThread("getAreaThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        setContentView(R.layout.network_picker_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = findViewById(R.id.action_bar);
        setPrimaryColor(R.color.bg_action_bar);
        actionBar.setPrimaryTitle(getTitle());

        listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        final String network = prefsGetNetwork();
        listAdapter = new NetworksAdapter(this, network, this, this);
        listView.setAdapter(listAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(listView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        mapView = findViewById(R.id.network_picker_map);
        final TextView mapDisclaimerView = findViewById(R.id.network_picker_map_disclaimer);
        mapDisclaimerView.setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());
        ViewCompat.setOnApplyWindowInsetsListener(mapDisclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        if (network != null) {
            findViewById(R.id.network_picker_firsttime_message).setVisibility(View.GONE);
            actionBar.setBack(v -> finish());
            final NetworkId networkId = prefsGetNetworkId();
            if (networkId != null) {
                backgroundHandler.post(new GetAreaRunnable(NetworkProviderFactory.provider(networkId), handler) {

                    @Override
                    protected void onResult(final Point[] area) {
                        mapView.setAreaAware(new AreaAware() {
                            final Point[] myArea = area != null && area.length > 1 ? area : null;

                            public Point[] getArea() {
                                return myArea;
                            }
                        });
                        mapView.setLocationAware(new LocationAware() {
                            final Location referenceLocation = area != null && area.length == 1
                                    ? Location.coord(area[0]) : null;

                            public Point getDeviceLocation() {
                                return deviceLocation;
                            }

                            public Location getReferenceLocation() {
                                return referenceLocation;
                            }

                            public Float getDeviceBearing() {
                                return null;
                            }
                        });

                        mapView.zoomToAll();
                    }
                });
            }
        }

        loadLastNetworks();

        parseIndex();
        updateGUI();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Override
    protected void onStart() {
        super.onStart();

        updateFragments();
        maybeStartLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopLocation();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    public void maybeStartLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        if (locationHelper.isRunning())
            return;

        final Criteria criteria = new Criteria();
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        locationHelper.startLocation(criteria, true, Constants.LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS);
    }

    public void stopLocation() {
        locationHelper.stop();
    }

    public void onLocationStart(final String provider) {
        actionBar.startProgress();
    }

    public void onLocationStop(final boolean timedOut) {
        actionBar.stopProgress();
    }

    public void onLocationFail() {
    }

    public void onLocation(final Point here) {
        actionBar.startProgress();

        deviceLocation = here;

        mapView.animateToLocation(here.getLatAsDouble(), here.getLonAsDouble());

        parseIndex();
        updateGUI();

        new GeocoderThread(this, here, new GeocoderThread.Callback() {
            public void onGeocoderResult(final Address address) {
                log.info("cc={}, admin={}, subAdmin={}, locality={}", address.getCountryCode(), address.getAdminArea(),
                        address.getSubAdminArea(), address.getLocality());

                actionBar.stopProgress();

                deviceAddress = address;

                parseIndex();
                updateGUI();
            }

            public void onGeocoderFail(final Exception exception) {
                actionBar.stopProgress();
            }
        });
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        // disable back key if no network is selected
        if (keyCode == KeyEvent.KEYCODE_BACK && prefsGetNetwork() == null)
            return true;

        return super.onKeyDown(keyCode, event);
    }

    public void onNetworkClick(final NetworkListEntry.Network entry) {
        // persist in preferences
        prefs.edit().putString(Constants.PREFS_KEY_NETWORK_PROVIDER, entry.id).commit();

        // put last selected network to the front
        final NetworkId network = NetworkId.valueOf(entry.id);
        lastNetworks.remove(network);
        lastNetworks.add(0, network);
        while (lastNetworks.size() > MAX_LAST_NETWORKS)
            lastNetworks.remove(lastNetworks.size() - 1);
        saveLastNetworks();

        finish();
    }

    public boolean onNetworkContextMenuItemClick(final NetworkListEntry.Network entry, final int menuItemId) {
        if (menuItemId == R.id.network_picker_context_remove) {
            // placeholder for action
            listAdapter.notifyDataSetChanged();
            return true;
        } else {
            return false;
        }
    }

    private void updateFragments() {
        final Resources res = getResources();

        final View listFrame = findViewById(R.id.network_picker_list_frame);
        final boolean listShow = res.getBoolean(R.bool.layout_list_show);
        listFrame.setVisibility(isInMultiWindowMode() || listShow ? View.VISIBLE : View.GONE);

        final View mapFrame = findViewById(R.id.network_picker_map_frame);
        final boolean mapShow = res.getBoolean(R.bool.layout_map_show);
        mapFrame.setVisibility(!isInMultiWindowMode() && mapShow ? View.VISIBLE : View.GONE);

        final LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) listFrame.getLayoutParams();
        layoutParams.width = listShow && mapShow ? res.getDimensionPixelSize(R.dimen.layout_list_width)
                : LinearLayout.LayoutParams.MATCH_PARENT;
    }

    private void updateGUI() {
        listAdapter.notifyDataSetChanged();
        mapView.invalidate();
    }

    private String prefsGetNetwork() {
        return prefs.getString(Constants.PREFS_KEY_NETWORK_PROVIDER, null);
    }

    private NetworkId prefsGetNetworkId() {
        final String id = prefsGetNetwork();
        if (id == null)
            return null;

        try {
            return NetworkId.valueOf(id);
        } catch (final IllegalArgumentException x) {
            log.warn("Ignoring unkown selected network: {}", id);
            return null;
        }
    }

    private void parseIndex() {
        final Map<String, NetworkListEntry> entriesMap = new LinkedHashMap<>();
        final List<NetworkListEntry> entries = new LinkedList<>();

        String line = null;
        try (final BufferedReader reader =
                     new BufferedReader(new InputStreamReader(getAssets().open(INDEX_FILENAME)))) {
            while (true) {
                line = reader.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;

                final String[] fields = line.split("\\|");
                final String networkId = fields[0];
                final String group = fields[1];
                final String coverage = fields.length >= 3 ? fields[2] : "";
                final String state = fields.length >= 4 ? fields[3] : null;
                final NetworkListEntry entry = new NetworkListEntry.Network(networkId, state, group, coverage);

                entriesMap.put(networkId, entry);
            }
        } catch (final Exception x) {
            throw new RuntimeException("problem parsing: '" + line + "'", x);
        }

        // last used networks
        boolean firstLastUsed = true;
        for (final NetworkId lastNetwork : lastNetworks) {
            final NetworkListEntry networkEntry = entriesMap.get(lastNetwork.name());
            if (networkEntry != null && !NetworkListEntry.Network.STATE_DISABLED
                    .equals(((NetworkListEntry.Network) networkEntry).state)) {
                if (firstLastUsed) {
                    entries.add(new NetworkListEntry.Separator(getString(R.string.network_picker_separator_last)));
                    firstLastUsed = false;
                }

                entriesMap.remove(lastNetwork.name());
                entries.add(networkEntry);
            }
        }

        // suggested networks
        boolean firstSuggested = true;
        for (Iterator<NetworkListEntry> i = entriesMap.values().iterator(); i.hasNext();) {
            final NetworkListEntry.Network networkEntry = (NetworkListEntry.Network) i.next();
            if (isSuggested(networkEntry)) {
                if (firstSuggested) {
                    entries.add(new NetworkListEntry.Separator(getString(R.string.network_picker_separator_suggested)));
                    firstSuggested = false;
                }

                entries.add(networkEntry);
                i.remove();
            }
        }

        // nearby networks
        boolean firstNearby = true;
        for (Iterator<NetworkListEntry> i = entriesMap.values().iterator(); i.hasNext();) {
            final NetworkListEntry.Network networkEntry = (NetworkListEntry.Network) i.next();
            if (isNearby(networkEntry)) {
                if (firstNearby) {
                    entries.add(new NetworkListEntry.Separator(getString(R.string.network_picker_separator_nearby)));
                    firstNearby = false;
                }

                entries.add(networkEntry);
                i.remove();
            }
        }

        // rest
        String lastGroup = null;
        for (final NetworkListEntry entry : entriesMap.values()) {
            final NetworkListEntry.Network networkEntry = (NetworkListEntry.Network) entry;
            final String group = networkEntry.group;
            if (!group.equals(lastGroup)) {
                if ("eu".equals(group)) {
                    entries.add(new NetworkListEntry.Separator(getString(R.string.network_picker_separator_europe)));
                } else {
                    final String[] groupFields = group.split("-", 2);
                    entries.add(new NetworkListEntry.Separator(
                            new Locale(groupFields[0], groupFields[1]).getDisplayCountry()));
                }
                lastGroup = group;
            }

            entries.add(entry);
        }

        listAdapter.setEntries(entries);
    }

    private boolean isSuggested(final NetworkListEntry.Network network) {
        if (deviceLocation == null)
            return false;

        if (NetworkListEntry.Network.STATE_DEPRECATED.equals(network.state)
                || NetworkListEntry.Network.STATE_DISABLED.equals(network.state))
            return false;

        final NetworkProvider networkProvider = NetworkProviderFactory.provider(NetworkId.valueOf(network.id));

        boolean inArea = false;

        final Point[] area = getArea(networkProvider);

        if (area == null || area.length <= 2)
            return false;

        final double lat = deviceLocation.getLatAsDouble();
        final double lon = deviceLocation.getLonAsDouble();

        // raycast point in polygon test
        final int numPoints = area.length;
        int j = numPoints - 1;

        for (int i = 0; i < numPoints; i++) {
            final Point vertex1 = area[i];
            final Point vertex2 = area[j];

            if (vertex1.getLonAsDouble() < lon && vertex2.getLonAsDouble() >= lon
                    || vertex2.getLonAsDouble() < lon && vertex1.getLonAsDouble() >= lon) {
                if ((vertex1.getLatAsDouble() + (lon - vertex1.getLonAsDouble()))
                        / (vertex2.getLonAsDouble() - vertex1.getLonAsDouble())
                        * (vertex2.getLatAsDouble() - vertex1.getLatAsDouble()) < lat)
                    inArea = !inArea;
            }

            j = i;
        }

        return inArea;
    }

    private Point[] getArea(final NetworkProvider networkProvider) {
        try {
            return networkProvider.getArea();
        } catch (final IOException x) {
            throw new RuntimeException(x);
        }
    }

    private boolean isNearby(final NetworkListEntry.Network network) {
        if (deviceAddress == null)
            return false;

        final String coverage = network.coverage;

        final String deviceCountryCode = deviceAddress.getCountryCode();
        if (deviceCountryCode != null && coverage.contains(deviceCountryCode))
            return true;

        final String deviceAdminArea = deviceAddress.getAdminArea();
        if (deviceAdminArea != null && coverage.contains(deviceAdminArea))
            return true;

        final String deviceSubAdminArea = deviceAddress.getSubAdminArea();
        if (deviceSubAdminArea != null && coverage.contains(deviceSubAdminArea))
            return true;

        final String deviceLocality = deviceAddress.getLocality();
        if (deviceLocality != null && coverage.contains(deviceLocality))
            return true;

        return false;
    }

    private void loadLastNetworks() {
        lastNetworks.clear();
        for (final String networkId : prefs.getString(Constants.PREFS_KEY_LAST_NETWORK_PROVIDERS, "").split(",")) {
            try {
                lastNetworks.add(NetworkId.valueOf(networkId));
            } catch (final IllegalArgumentException x) {
                // don't care
            }
        }
    }

    private void saveLastNetworks() {
        final StringBuilder prefsValue = new StringBuilder();
        for (final NetworkId network : lastNetworks)
            prefsValue.append(network.name()).append(',');
        if (prefsValue.length() > 0)
            prefsValue.setLength(prefsValue.length() - 1);
        prefs.edit().putString(Constants.PREFS_KEY_LAST_NETWORK_PROVIDERS, prefsValue.toString()).commit();
    }

    protected final void setPrimaryColor(final int colorResId) {
        final int color = getResources().getColor(colorResId);
        actionBar.setBackgroundColor(color);
        setTaskDescription(new TaskDescription(null, null, color));
    }
}
