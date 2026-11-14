package com.safepath.ai.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.safepath.ai.R;
import com.safepath.ai.models.Journey;
import com.safepath.ai.network.dto.AreaRiskDto;
import com.safepath.ai.viewmodels.JourneyViewModel;
import com.safepath.ai.viewmodels.RiskViewModel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MapFragment — OpenStreetMap integration via MapLibre, displaying user GPS telemetry,
 * active journey polyline routing, and real-time historical area risk estimations (PROMPT 8).
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String OSM_STYLE_JSON = "{"
            + "\"version\": 8,"
            + "\"sources\": {"
            + "  \"osm-tiles\": {"
            + "    \"type\": \"raster\","
            + "    \"tiles\": [\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],"
            + "    \"tileSize\": 256,"
            + "    \"attribution\": \"© OpenStreetMap contributors\""
            + "  }"
            + "},"
            + "\"layers\": [{"
            + "  \"id\": \"osm-tiles-layer\","
            + "  \"type\": \"raster\","
            + "  \"source\": \"osm-tiles\","
            + "  \"minzoom\": 0,"
            + "  \"maxzoom\": 19"
            + "}]"
            + "}";

    private static final String ROUTE_SOURCE_ID = "journey-route-source";
    private static final String ROUTE_LAYER_ID  = "journey-route-layer";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private JourneyViewModel journeyViewModel;
    private RiskViewModel riskViewModel;

    private TextView tvMapCoords;
    private TextView tvMapRouteStatus;
    private TextView tvMapAccuracyDisclosed;
    private TextView tvRiskCategoryTitle;
    private TextView tvRiskSubtitle;
    private View viewRiskColorPill;
    private MaterialCardView cardAreaRisk;
    private MaterialCardView cardMapPermissionWarning;
    private FloatingActionButton fabCenterLocation;

    private Location lastLocation = null;
    private AreaRiskDto currentRisk = null;
    private boolean isCameraInitialized = false;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    cardMapPermissionWarning.setVisibility(View.GONE);
                } else {
                    cardMapPermissionWarning.setVisibility(View.VISIBLE);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.mapView);
        tvMapCoords = view.findViewById(R.id.tvMapCoords);
        tvMapRouteStatus = view.findViewById(R.id.tvMapRouteStatus);
        tvMapAccuracyDisclosed = view.findViewById(R.id.tvMapAccuracyDisclosed);
        tvRiskCategoryTitle = view.findViewById(R.id.tvRiskCategoryTitle);
        tvRiskSubtitle = view.findViewById(R.id.tvRiskSubtitle);
        viewRiskColorPill = view.findViewById(R.id.viewRiskColorPill);
        cardAreaRisk = view.findViewById(R.id.cardAreaRisk);
        cardMapPermissionWarning = view.findViewById(R.id.cardMapPermissionWarning);
        fabCenterLocation = view.findViewById(R.id.fabCenterLocation);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        journeyViewModel = new ViewModelProvider(requireActivity()).get(JourneyViewModel.class);
        riskViewModel = new ViewModelProvider(this).get(RiskViewModel.class);

        setupObservers();
        setupListeners(view);
        checkPermission();
    }

    private void setupObservers() {
        journeyViewModel.getCurrentLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null) {
                lastLocation = location;
                tvMapCoords.setText(String.format(Locale.getDefault(),
                        "Lat: %.5f°, Lng: %.5f°", location.getLatitude(), location.getLongitude()));

                float acc = location.getAccuracy();
                if (acc <= 8.0f) {
                    tvMapAccuracyDisclosed.setText(String.format(Locale.getDefault(),
                            "High accuracy: ± %.1fm (GPS Fix)", acc));
                } else if (acc <= 25.0f) {
                    tvMapAccuracyDisclosed.setText(String.format(Locale.getDefault(),
                            "Moderate accuracy: ± %.1fm (Estimated)", acc));
                } else {
                    tvMapAccuracyDisclosed.setText(String.format(Locale.getDefault(),
                            "Approximate accuracy: ± %.1fm (Satellite Signal Degraded)", acc));
                }

                if (!isCameraInitialized && mapLibreMap != null) {
                    centerCameraOn(location.getLatitude(), location.getLongitude(), 15.0);
                    isCameraInitialized = true;
                }

                // Query Area Risk from FastAPI backend
                riskViewModel.fetchAreaRisk(location.getLatitude(), location.getLongitude(), 1.0);
            }
        });

        journeyViewModel.getIsTracking().observe(getViewLifecycleOwner(), isTracking -> {
            boolean active = Boolean.TRUE.equals(isTracking);
            tvMapRouteStatus.setText(active ? "TRACKING" : "IDLE");
            tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.color_safe_green : R.color.color_on_surface_variant));
        });

        journeyViewModel.getActiveJourney().observe(getViewLifecycleOwner(), journey -> {
            if (journey != null && mapLibreMap != null) {
                updateRoutePolyline(journey);
            }
        });

        // Area Risk Observer (PROMPT 8)
        riskViewModel.getAreaRisk().observe(getViewLifecycleOwner(), riskDto -> {
            if (riskDto != null) {
                currentRisk = riskDto;
                tvRiskCategoryTitle.setText(riskDto.riskCategory);
                tvRiskSubtitle.setText(String.format("Baseline: %s · Tap for guidance", riskDto.dataPeriod));

                try {
                    int pillColor = Color.parseColor(riskDto.colorIndicator);
                    viewRiskColorPill.setBackgroundColor(pillColor);
                } catch (Exception e) {
                    viewRiskColorPill.setBackgroundColor(Color.GRAY);
                }
            }
        });

        riskViewModel.getErrorMessage().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty() && currentRisk == null) {
                tvRiskCategoryTitle.setText("Insufficient data");
                tvRiskSubtitle.setText("Area risk telemetry currently unavailable");
                viewRiskColorPill.setBackgroundColor(Color.LTGRAY);
            }
        });
    }

    private void setupListeners(View root) {
        fabCenterLocation.setOnClickListener(v -> {
            if (lastLocation != null && mapLibreMap != null) {
                centerCameraOn(lastLocation.getLatitude(), lastLocation.getLongitude(), 16.0);
            } else {
                Toast.makeText(requireContext(), "Acquiring GPS location...", Toast.LENGTH_SHORT).show();
            }
        });

        // Tap on risk card shows safety guidance dialog
        cardAreaRisk.setOnClickListener(v -> showRiskDetailsDialog());

        View btnEnable = root.findViewById(R.id.btnMapEnableLocation);
        if (btnEnable != null) {
            btnEnable.setOnClickListener(v -> locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }));
        }
    }

    private void showRiskDetailsDialog() {
        if (currentRisk == null) {
            Toast.makeText(requireContext(), "Area risk information loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = String.format(
                "Classification: %s\n\n"
                + "Practical Safety Guidance:\n%s\n\n"
                + "Data Baseline: %s (Last updated: %s)\n\n"
                + "Mandatory Safety Disclaimer:\n%s",
                currentRisk.riskCategory,
                currentRisk.safetyGuidance,
                currentRisk.dataPeriod,
                currentRisk.lastUpdated.substring(0, 10),
                currentRisk.disclaimer
        );

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Area Safety Information")
                .setMessage(message)
                .setPositiveButton("Understood", null)
                .show();
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;

        map.setStyle(new Style.Builder().fromJson(OSM_STYLE_JSON), style -> {
            GeoJsonSource routeSource = new GeoJsonSource(ROUTE_SOURCE_ID);
            style.addSource(routeSource);

            LineLayer routeLayer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID);
            routeLayer.setProperties(
                    PropertyFactory.lineColor(ContextCompat.getColor(requireContext(), R.color.color_primary)),
                    PropertyFactory.lineWidth(4.5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            );
            style.addLayer(routeLayer);

            if (lastLocation != null) {
                centerCameraOn(lastLocation.getLatitude(), lastLocation.getLongitude(), 15.0);
                isCameraInitialized = true;
            } else {
                centerCameraOn(20.5937, 78.9629, 4.5);
            }

            Journey active = journeyViewModel.getActiveJourney().getValue();
            if (active != null) {
                updateRoutePolyline(active);
            }
        });
    }

    private void updateRoutePolyline(Journey journey) {
        if (mapLibreMap == null) return;
        Style style = mapLibreMap.getStyle();
        if (style == null) return;

        GeoJsonSource source = style.getSourceAs(ROUTE_SOURCE_ID);
        if (source == null) return;

        try {
            List<Point> points = new ArrayList<>();
            if (journey.waypointsJson != null && !journey.waypointsJson.isEmpty()) {
                JSONArray arr = new JSONArray(journey.waypointsJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject pt = arr.getJSONObject(i);
                    points.add(Point.fromLngLat(pt.getDouble("lng"), pt.getDouble("lat")));
                }
            }

            if (points.size() >= 2) {
                LineString lineString = LineString.fromLngLats(points);
                source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)));
            }
        } catch (Exception ignored) {}
    }

    private void centerCameraOn(double lat, double lng, double zoom) {
        if (mapLibreMap != null) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(lat, lng))
                    .zoom(zoom)
                    .build();
            mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000);
        }
    }

    private void checkPermission() {
        boolean fine = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
            cardMapPermissionWarning.setVisibility(View.VISIBLE);
        } else {
            cardMapPermissionWarning.setVisibility(View.GONE);
        }
    }

    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override public void onDestroyView() {
        super.onDestroyView();
        mapView.onDestroy();
    }
}
