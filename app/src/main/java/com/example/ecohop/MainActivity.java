package com.example.ecohop;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MapView map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        map.setMultiTouchControls(true);

        GeoPoint start = new GeoPoint(12.9716, 77.5946); // Bangalore
        GeoPoint end = new GeoPoint(13.0827, 80.2707);   // Chennai

        map.getController().setZoom(6.0);
        map.getController().setCenter(start);

        getRouteFromORS(start, end);

        EditText destination_et = findViewById(R.id.destination_et);
        EditText source_et = findViewById(R.id.source_et);
        Button find_route_btn = findViewById(R.id.find_route_btn);

        destination_et.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                source_et.setVisibility(View.VISIBLE);
                find_route_btn.setVisibility(View.VISIBLE);
            }
            return false; // <-- Added return statement
        });
    }

    private void getRouteFromORS(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                String apiKey = "YOUR_OPENROUTESERVICE_API_KEY"; // Get free at https://openrouteservice.org/sign-up/
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
}
