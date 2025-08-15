package com.example.ecohop;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.PopupWindow;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import org.osmdroid.views.overlay.Marker;


public class MainActivity extends AppCompatActivity {

    private MapView map;
    private LocationManager locationManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable workRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMultiTouchControls(true);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)//checking if location permission is given
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            showUserLocation();
        }


        AutoCompleteTextView destination_et = findViewById(R.id.destination_et);
        AutoCompleteTextView source_et = findViewById(R.id.source_et);
        Button find_route_btn = findViewById(R.id.find_route_btn);
        ImageButton recenter_btn = findViewById(R.id.recenter_btn);

        setupAutoComplete(destination_et);
        setupAutoComplete(source_et);

        recenter_btn.setOnClickListener(v -> {
            showUserLocation();
            Toast.makeText(this, "Centering on your location", Toast.LENGTH_SHORT).show();
        });

        destination_et.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                source_et.setVisibility(View.VISIBLE);
                find_route_btn.setVisibility(View.VISIBLE);
            }
            return false;
        });


        map.setOnTouchListener((v,event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN){
                String sourceText = source_et.getText().toString().trim();
                String destinationText = destination_et.getText().toString().trim();
                if (source_et.getVisibility()==View.VISIBLE && sourceText.isEmpty() && destinationText.isEmpty()){
                    source_et.setVisibility(View.GONE);
                    find_route_btn.setVisibility(View.GONE);

                    destination_et.clearFocus();
                    source_et.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            return false;
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void setupAutoComplete(AutoCompleteTextView autoCompleteTextView){


        autoCompleteTextView.setInputMethodMode(android.widget.ListPopupWindow.INPUT_METHOD_NEEDED);

        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(workRunnable);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 2){
                    workRunnable = () -> fetchSuggestions(s.toString(), autoCompleteTextView);
                    handler.postDelayed(workRunnable, 500);
                }
            }
        });
    }

    private void fetchSuggestions(String query, AutoCompleteTextView autoCompleteTextView){
        Log.d("AutoCompleteDebug", "Fetching suggestions for: " + query);

        new Thread(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String url = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=5";

                if (locationManager != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                    Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnownLocation != null) {
                        double lat = lastKnownLocation.getLatitude();
                        double lon = lastKnownLocation.getLongitude();
                        url += "&viewbox=" + (lon-0.5) + "," + (lat+0.5) + "," + (lon+0.5) + "," + (lat-0.5) + "&bounded=1";
                    }
                }
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url)
                        .header("User-Agent","EcoHop/1.0")
                        .build();
                Response response = client.newCall(request).execute();
                String jsonData = response.body().string();

                JSONArray jsonArray = new JSONArray(jsonData);
                List<String> suggestions = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject object = jsonArray.getJSONObject(i);
                    suggestions.add(object.getString("display_name"));
                }
                Log.d("AutoCompleteDebug", "Found " + suggestions.size() + " suggestions.");

                runOnUiThread(() -> {
                    ArrayAdapter<String> newAdapter = new ArrayAdapter<>(
                            MainActivity.this,
                            android.R.layout.simple_dropdown_item_1line,
                            suggestions
                    );
                    autoCompleteTextView.setAdapter(newAdapter);
                    newAdapter.notifyDataSetChanged();
                });
            }
            catch (Exception e) {
                Log.e("AutoCompleteDebug", "Error fetching suggestions", e);;
            }
        }).start();
    }



    private void getRouteFromORS(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                String apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjEzOGYzN2EyYjgzMTRhNmU4NGZmMGIyOTllYTc1OWJkIiwiaCI6Im11cm11cjY0In0="; // Get free at https://openrouteservice.org/sign-up/
                String url = "https://api.openrouteservice.org/v2/directions/driving-car?api_key="
                        + apiKey
                        + "&start=" + start.getLongitude() + "," + start.getLatitude()
                        + "&end=" + end.getLongitude() + "," + end.getLatitude();

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                Response response = client.newCall(request).execute();
                String jsonData = response.body().string();

                JSONObject json = new JSONObject(jsonData);
                JSONArray coords = json.getJSONArray("features")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<GeoPoint> geoPoints = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray point = coords.getJSONArray(i);
                    double lon = point.getDouble(0);
                    double lat = point.getDouble(1);
                    geoPoints.add(new GeoPoint(lat, lon));
                }

                runOnUiThread(() -> {
                    Polyline line = new Polyline();
                    line.setPoints(geoPoints);
                    line.getOutlinePaint().setColor(Color.BLUE);
                    line.getOutlinePaint().setStrokeWidth(8f);
                    map.getOverlays().add(line);
                    map.invalidate();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showUserLocation();
        }
    }
    private void showUserLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return; // Permission not granted
        }

        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location != null) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();

            GeoPoint currentPoint = new GeoPoint(lat, lon);
            map.getController().setZoom(15.0);
            map.getController().setCenter(currentPoint);

            Marker marker = new Marker(map);
            marker.setPosition(currentPoint);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle("You are here");
            map.getOverlays().add(marker);
        } else {
            Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
        }
    }

}