package com.safepath.ai.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.safepath.ai.network.dto.H3CellRiskDto;
import com.safepath.ai.network.dto.H3GridRiskDto;
import com.safepath.ai.viewmodels.JourneyViewModel;
import com.safepath.ai.viewmodels.RiskViewModel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.Polygon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * MapFragment — OpenStreetMap integration via MapLibre, displaying user GPS telemetry,
 * active journey polyline routing, and multi-cell H3 hexagonal risk grid visualization.
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

    private static final String H3_GRID_SOURCE_ID = "h3-grid-source";
    private static final String H3_GRID_FILL_LAYER_ID = "h3-grid-fill-layer";
    private static final String H3_GRID_LINE_LAYER_ID = "h3-grid-line-layer";

    private static final String H3_SELECTED_SOURCE_ID = "h3-selected-source";
    private static final String H3_SELECTED_LINE_LAYER_ID = "h3-selected-line-layer";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Marker userMarker = null;
    private JourneyViewModel journeyViewModel;
    private RiskViewModel riskViewModel;

    private TextView tvMapCoords;
    private TextView tvMapRouteStatus;
    private TextView tvMapAccuracyDisclosed;
    private TextView tvRiskCategoryTitle;
    private TextView tvRiskGridSummary;
    private View viewRiskColorPill;
    private MaterialCardView cardAreaRisk;
    private MaterialCardView cardMapPermissionWarning;
    private FloatingActionButton fabCenterLocation;

    // Selected Cell Inspector Card views
    private MaterialCardView cardSelectedCell;
    private View viewSelectedCellColor;
    private TextView tvSelectedCellTitle;
    private TextView tvSelectedCellId;
    private TextView tvSelectedCellRisk;
    private TextView tvSelectedCellIncidents;
    private TextView tvSelectedCellPeriod;
    private ImageView btnCloseSelectedCell;

    private Location lastLocation = null;
    private AreaRiskDto currentRisk = null;
    private H3GridRiskDto currentH3Grid = null;
    private boolean isCameraInitialized = false;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    cardMapPermissionWarning.setVisibility(View.GONE);
                    journeyViewModel.startLocationUpdates();
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
        tvRiskGridSummary = view.findViewById(R.id.tvRiskGridSummary);
        viewRiskColorPill = view.findViewById(R.id.viewRiskColorPill);
        cardAreaRisk = view.findViewById(R.id.cardAreaRisk);
        cardMapPermissionWarning = view.findViewById(R.id.cardMapPermissionWarning);
        fabCenterLocation = view.findViewById(R.id.fabCenterLocation);

        cardSelectedCell = view.findViewById(R.id.cardSelectedCell);
        viewSelectedCellColor = view.findViewById(R.id.viewSelectedCellColor);
        tvSelectedCellTitle = view.findViewById(R.id.tvSelectedCellTitle);
        tvSelectedCellId = view.findViewById(R.id.tvSelectedCellId);
        tvSelectedCellRisk = view.findViewById(R.id.tvSelectedCellRisk);
        tvSelectedCellIncidents = view.findViewById(R.id.tvSelectedCellIncidents);
        tvSelectedCellPeriod = view.findViewById(R.id.tvSelectedCellPeriod);
        btnCloseSelectedCell = view.findViewById(R.id.btnCloseSelectedCell);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        journeyViewModel = new ViewModelProvider(requireActivity()).get(JourneyViewModel.class);
        riskViewModel = new ViewModelProvider(this).get(RiskViewModel.class);

        setupObservers();
        setupListeners(view);
        checkPermission();
    }

    private void setupObservers() {
        // Location telemetry observer
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

                if (mapLibreMap != null) {
                    updateUserMarker(location.getLatitude(), location.getLongitude());
                    if (!isCameraInitialized) {
                        centerCameraOn(location.getLatitude(), location.getLongitude(), 15.0);
                        isCameraInitialized = true;
                    }
                }

                // Query multi-cell H3 Grid and Area Risk from FastAPI backend
                riskViewModel.fetchH3GridRisk(location.getLatitude(), location.getLongitude(), 2.0, 8);
                riskViewModel.fetchAreaRisk(location.getLatitude(), location.getLongitude(), 1.0);
            }
        });

        // Location tracking status observer
        journeyViewModel.getLocationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;
            switch (status) {
                case IDLE:
                    if (lastLocation == null) {
                        tvMapCoords.setText("Location: Ready");
                        tvMapRouteStatus.setText("IDLE");
                        tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface_variant));
                    }
                    break;
                case ACQUIRING:
                    if (lastLocation == null) {
                        tvMapCoords.setText("Location: Acquiring GPS...");
                        tvMapRouteStatus.setText("ACQUIRING");
                        tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_warning_yellow));
                        tvMapAccuracyDisclosed.setText("Acquiring satellite lock...");
                    }
                    break;
                case ACTIVE:
                    boolean isTracking = Boolean.TRUE.equals(journeyViewModel.getIsTracking().getValue());
                    tvMapRouteStatus.setText(isTracking ? "TRACKING" : "ACTIVE");
                    tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_safe_green));
                    cardMapPermissionWarning.setVisibility(View.GONE);
                    break;
                case PERMISSION_REQUIRED:
                    tvMapCoords.setText("Location Permission Required");
                    tvMapRouteStatus.setText("NO PERMISSION");
                    tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_danger_red));
                    cardMapPermissionWarning.setVisibility(View.VISIBLE);
                    break;
                case LOCATION_DISABLED:
                    tvMapCoords.setText("Device GPS is Turned Off");
                    tvMapRouteStatus.setText("LOCATION DISABLED");
                    tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_danger_red));
                    tvMapAccuracyDisclosed.setText("Tap to open Location Settings and turn on GPS");
                    break;
                case ERROR:
                    tvMapCoords.setText("Location Acquisition Error");
                    tvMapRouteStatus.setText("ERROR");
                    tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_danger_red));
                    break;
            }
        });

        journeyViewModel.getIsTracking().observe(getViewLifecycleOwner(), isTracking -> {
            boolean active = Boolean.TRUE.equals(isTracking);
            if (active) {
                tvMapRouteStatus.setText("TRACKING");
                tvMapRouteStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_safe_green));
            }
        });

        journeyViewModel.getActiveJourney().observe(getViewLifecycleOwner(), journey -> {
            if (journey != null && mapLibreMap != null) {
                updateRoutePolyline(journey);
            }
        });

        // Area Risk Observer (Single point summary)
        riskViewModel.getAreaRisk().observe(getViewLifecycleOwner(), riskDto -> {
            if (riskDto != null) {
                currentRisk = riskDto;
            }
        });

        // H3 Hexagonal Grid Observer
        riskViewModel.getH3GridRisk().observe(getViewLifecycleOwner(), gridDto -> {
            if (gridDto != null) {
                currentH3Grid = gridDto;
                tvRiskCategoryTitle.setText(String.format(Locale.getDefault(),
                        "Current Cell: %s", gridDto.currentRiskCategory));
                tvRiskGridSummary.setText(String.format(Locale.getDefault(),
                        "H3 Grid: %d cells · %d with data · %d no data",
                        gridDto.totalCells, gridDto.cellsWithData, gridDto.cellsNoData));

                try {
                    int pillColor = Color.parseColor(gridDto.currentColorIndicator);
                    viewRiskColorPill.setBackgroundColor(pillColor);
                } catch (Exception e) {
                    viewRiskColorPill.setBackgroundColor(Color.GRAY);
                }

                renderH3GridOverlay(gridDto);
            }
        });

        // Selected Cell Observer
        riskViewModel.getSelectedCell().observe(getViewLifecycleOwner(), cell -> {
            if (cell != null) {
                displaySelectedCellInfo(cell);
            } else {
                cardSelectedCell.setVisibility(View.GONE);
                clearSelectedHighlight();
            }
        });

        riskViewModel.getErrorMessage().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty() && currentH3Grid == null) {
                tvRiskCategoryTitle.setText("Insufficient data");
                tvRiskGridSummary.setText("H3 risk telemetry currently unavailable");
                viewRiskColorPill.setBackgroundColor(Color.LTGRAY);
            }
        });
    }

    private void updateUserMarker(double lat, double lng) {
        if (mapLibreMap == null) return;
        LatLng pos = new LatLng(lat, lng);
        if (userMarker == null) {
            userMarker = mapLibreMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .setTitle("Current Location")
                    .setSnippet("You are here"));
        } else {
            userMarker.setPosition(pos);
        }
    }

    private void setupListeners(View root) {
        fabCenterLocation.setOnClickListener(v -> {
            if (lastLocation != null && mapLibreMap != null) {
                centerCameraOn(lastLocation.getLatitude(), lastLocation.getLongitude(), 16.0);
            } else {
                Toast.makeText(requireContext(), "Acquiring GPS location...", Toast.LENGTH_SHORT).show();
                journeyViewModel.startLocationUpdates();
            }
        });

        cardAreaRisk.setOnClickListener(v -> showRiskDetailsDialog());

        btnCloseSelectedCell.setOnClickListener(v -> riskViewModel.clearSelectedCell());

        View btnEnable = root.findViewById(R.id.btnMapEnableLocation);
        if (btnEnable != null) {
            btnEnable.setOnClickListener(v -> locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }));
        }

        tvMapCoords.setOnClickListener(v -> {
            if (journeyViewModel.getLocationStatus().getValue() == JourneyViewModel.LocationStatus.LOCATION_DISABLED) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
    }

    private void showRiskDetailsDialog() {
        if (currentH3Grid == null && currentRisk == null) {
            Toast.makeText(requireContext(), "Area risk information loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        String category = currentH3Grid != null ? currentH3Grid.currentRiskCategory : (currentRisk != null ? currentRisk.riskCategory : "N/A");
        String guidance = currentRisk != null ? currentRisk.safetyGuidance : "Maintain standard personal alertness and stay on well-lit thoroughfares.";
        String lastUpdatedDate = currentH3Grid != null && currentH3Grid.lastUpdated != null && currentH3Grid.lastUpdated.length() >= 10
                ? currentH3Grid.lastUpdated.substring(0, 10) : "Recent";
        int total = currentH3Grid != null ? currentH3Grid.totalCells : 19;
        int withData = currentH3Grid != null ? currentH3Grid.cellsWithData : 19;
        int noData = currentH3Grid != null ? currentH3Grid.cellsNoData : 0;

        String message = String.format(Locale.getDefault(),
                "Current Location Risk: %s\n"
                + "Spatial Index: %s (H3 Res 8)\n\n"
                + "Grid Scope: %d contiguous cells analyzed\n"
                + "• Cells with baseline data: %d\n"
                + "• Unindexed cells: %d\n\n"
                + "Safety Guidance:\n%s\n\n"
                + "Data Baseline: Historical Police & Open Data Records\n"
                + "Last Updated: %s\n\n"
                + "Mandatory Disclaimer:\nSafePath AI displays historical patterns for situational awareness. It never guarantees absolute safety.",
                category,
                currentH3Grid != null ? currentH3Grid.currentCellId : "Indexed Cell",
                total, withData, noData,
                guidance,
                lastUpdatedDate
        );

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Geographical Risk Analysis")
                .setMessage(message)
                .setPositiveButton("Understood", null)
                .show();
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;

        map.setStyle(new Style.Builder().fromJson(OSM_STYLE_JSON), style -> {
            // H3 Grid GeoJson Source & Layers
            GeoJsonSource h3Source = new GeoJsonSource(H3_GRID_SOURCE_ID);
            style.addSource(h3Source);

            // Fill Layer with translucent data-driven color from feature property "color"
            FillLayer h3FillLayer = new FillLayer(H3_GRID_FILL_LAYER_ID, H3_GRID_SOURCE_ID);
            h3FillLayer.setProperties(
                    PropertyFactory.fillColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.fillOpacity(0.35f)
            );
            style.addLayer(h3FillLayer);

            // Subtle border outline for H3 hexagons
            LineLayer h3LineLayer = new LineLayer(H3_GRID_LINE_LAYER_ID, H3_GRID_SOURCE_ID);
            h3LineLayer.setProperties(
                    PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.lineWidth(1.8f),
                    PropertyFactory.lineOpacity(0.85f)
            );
            style.addLayer(h3LineLayer);

            // Selected Cell Highlight Line Layer
            GeoJsonSource selectedSource = new GeoJsonSource(H3_SELECTED_SOURCE_ID);
            style.addSource(selectedSource);

            LineLayer selectedLineLayer = new LineLayer(H3_SELECTED_LINE_LAYER_ID, H3_SELECTED_SOURCE_ID);
            selectedLineLayer.setProperties(
                    PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(3.8f),
                    PropertyFactory.lineOpacity(1.0f)
            );
            style.addLayer(selectedLineLayer);

            // Active Journey Route layer
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

            // Tap-to-select map click listener
            map.addOnMapClickListener(point -> {
                handleMapClick(point);
                return true;
            });

            Location currentLoc = (lastLocation != null) ? lastLocation : journeyViewModel.getCurrentLocation().getValue();
            if (currentLoc != null) {
                lastLocation = currentLoc;
                updateUserMarker(currentLoc.getLatitude(), currentLoc.getLongitude());
                centerCameraOn(currentLoc.getLatitude(), currentLoc.getLongitude(), 15.0);
                isCameraInitialized = true;
            } else {
                centerCameraOn(20.5937, 78.9629, 4.5);
            }

            if (currentH3Grid != null) {
                renderH3GridOverlay(currentH3Grid);
            }

            Journey active = journeyViewModel.getActiveJourney().getValue();
            if (active != null) {
                updateRoutePolyline(active);
            }
        });
    }

    private void renderH3GridOverlay(H3GridRiskDto gridDto) {
        if (mapLibreMap == null || gridDto == null || gridDto.cells == null) return;
        Style style = mapLibreMap.getStyle();
        if (style == null) return;

        GeoJsonSource source = style.getSourceAs(H3_GRID_SOURCE_ID);
        if (source == null) return;

        List<Feature> features = new ArrayList<>();
        for (H3CellRiskDto cell : gridDto.cells) {
            if (cell.polygonCoordinates == null || cell.polygonCoordinates.isEmpty()) continue;

            List<Point> ring = new ArrayList<>();
            for (List<Double> coord : cell.polygonCoordinates) {
                if (coord.size() >= 2) {
                    ring.add(Point.fromLngLat(coord.get(0), coord.get(1)));
                }
            }
            if (ring.size() < 3) continue;

            Polygon polygon = Polygon.fromLngLats(Collections.singletonList(ring));
            Feature feature = Feature.fromGeometry(polygon);
            feature.addStringProperty("cell_id", cell.cellId);
            feature.addStringProperty("color", cell.colorIndicator != null ? cell.colorIndicator : "#16A34A");
            feature.addStringProperty("risk_category", cell.riskCategory != null ? cell.riskCategory : "Low Risk");
            feature.addNumberProperty("risk_class", cell.riskClass);
            feature.addNumberProperty("incident_count", cell.incidentCount);
            feature.addStringProperty("observation_period", cell.observationPeriod != null ? cell.observationPeriod : "Baseline");
            feature.addBooleanProperty("is_current_location", cell.isCurrentLocation);
            if (cell.primaryIncidentType != null) {
                feature.addStringProperty("primary_incident_type", cell.primaryIncidentType);
            }
            features.add(feature);
        }

        source.setGeoJson(FeatureCollection.fromFeatures(features));
    }

    private void handleMapClick(LatLng point) {
        if (currentH3Grid == null || currentH3Grid.cells == null) return;

        // Try rendered features query first
        if (mapLibreMap != null) {
            android.graphics.PointF screenPoint = mapLibreMap.getProjection().toScreenLocation(point);
            List<Feature> features = mapLibreMap.queryRenderedFeatures(screenPoint, H3_GRID_FILL_LAYER_ID);
            if (!features.isEmpty()) {
                String cellId = features.get(0).getStringProperty("cell_id");
                if (cellId != null) {
                    for (H3CellRiskDto cell : currentH3Grid.cells) {
                        if (cellId.equals(cell.cellId)) {
                            riskViewModel.selectCell(cell);
                            return;
                        }
                    }
                }
            }
        }

        // Geometric distance fallback: find closest cell center
        H3CellRiskDto closest = null;
        double minDistance = Double.MAX_VALUE;
        for (H3CellRiskDto cell : currentH3Grid.cells) {
            double dLat = cell.centerLat - point.getLatitude();
            double dLng = cell.centerLng - point.getLongitude();
            double distSq = dLat * dLat + dLng * dLng;
            if (distSq < minDistance) {
                minDistance = distSq;
                closest = cell;
            }
        }

        // Within ~1.5km tolerance in degree space (~0.015 deg)
        if (closest != null && minDistance < 0.0003) {
            riskViewModel.selectCell(closest);
        }
    }

    private void displaySelectedCellInfo(H3CellRiskDto cell) {
        cardSelectedCell.setVisibility(View.VISIBLE);

        String title = cell.isCurrentLocation ? "H3 Cell (Current Location)" : "H3 Hexagon Cell";
        tvSelectedCellTitle.setText(title);
        tvSelectedCellId.setText(String.format("Index: %s", cell.cellId));

        tvSelectedCellRisk.setText(cell.riskCategory);
        try {
            int color = Color.parseColor(cell.colorIndicator);
            tvSelectedCellRisk.setTextColor(color);
            viewSelectedCellColor.setBackgroundColor(color);
        } catch (Exception ignored) {
            tvSelectedCellRisk.setTextColor(Color.parseColor("#16A34A"));
        }

        if (cell.incidentCount > 0) {
            String crime = cell.primaryIncidentType != null ? cell.primaryIncidentType : "Incident";
            tvSelectedCellIncidents.setText(String.format(Locale.getDefault(),
                    "Incidents: %d recorded (%s)", cell.incidentCount, crime));
        } else {
            tvSelectedCellIncidents.setText("Incidents: 0 recorded in historical dataset");
        }

        tvSelectedCellPeriod.setText(String.format(Locale.getDefault(),
                "Baseline: %s · Conf: %.0f%%", cell.observationPeriod, cell.confidenceScore * 100));

        // Highlight selected cell on map
        highlightSelectedCell(cell);
    }

    private void highlightSelectedCell(H3CellRiskDto cell) {
        if (mapLibreMap == null || cell == null || cell.polygonCoordinates == null) return;
        Style style = mapLibreMap.getStyle();
        if (style == null) return;

        GeoJsonSource source = style.getSourceAs(H3_SELECTED_SOURCE_ID);
        if (source == null) return;

        List<Point> ring = new ArrayList<>();
        for (List<Double> coord : cell.polygonCoordinates) {
            if (coord.size() >= 2) {
                ring.add(Point.fromLngLat(coord.get(0), coord.get(1)));
            }
        }
        if (ring.size() >= 3) {
            Polygon polygon = Polygon.fromLngLats(Collections.singletonList(ring));
            source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(polygon)));
        }
    }

    private void clearSelectedHighlight() {
        if (mapLibreMap == null) return;
        Style style = mapLibreMap.getStyle();
        if (style == null) return;

        GeoJsonSource source = style.getSourceAs(H3_SELECTED_SOURCE_ID);
        if (source != null) {
            source.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<>()));
        }
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
            journeyViewModel.startLocationUpdates();
        }
    }

    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() {
        super.onResume();
        mapView.onResume();
        journeyViewModel.startLocationUpdates();
    }
    @Override public void onPause() {
        super.onPause();
        mapView.onPause();
        journeyViewModel.stopLocationUpdates();
    }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override public void onDestroyView() {
        super.onDestroyView();
        mapView.onDestroy();
        userMarker = null;
        isCameraInitialized = false;
    }
}
