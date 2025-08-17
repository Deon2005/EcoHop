package com.example.ecohop;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable workRunnable;
    private ApiService apiService = new ApiService();

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSearchScreen();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void showSearchScreen() {
        setContentView(R.layout.searchdestination);

        AutoCompleteTextView destination_et = findViewById(R.id.destination_et);
        AutoCompleteTextView source_et = findViewById(R.id.source_et);
        Button find_route_btn = findViewById(R.id.find_route_btn);

        setupAutoComplete(destination_et);
        setupAutoComplete(source_et);

        find_route_btn.setOnClickListener(v -> {
            String source = source_et.getText().toString().trim();
            String destination = destination_et.getText().toString().trim();

            if (source.isEmpty() || destination.isEmpty()) {
                Toast.makeText(this, "Please fill both start and end points", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Finding routes...", Toast.LENGTH_LONG).show();

            apiService.analyzeJourney(source, destination, new ApiService.ApiCallback() {
                @Override
                public void onSuccess(String routes, String analysis) {
                    runOnUiThread(() -> {
                        showTravelingScreen(routes, analysis);
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        Log.e("ApiService", "API call failed", e);
                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void showTravelingScreen(String routes, String analysis) {
        setContentView(R.layout.traveling);
        TextView detailedplan = findViewById(R.id.detailed_plan);

        String full_plan_text = "--- AVAILABLE ROUTES ---\n" + routes +
                "\n\n--- ANALYSIS ---\n" + analysis;

        detailedplan.setText(full_plan_text);

        ImageButton back2 = findViewById(R.id.back2);
        if (back2 != null) {
            back2.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showSearchScreen();
                }
            });
        }

        Button donebtn = findViewById(R.id.donebtn);
        if (donebtn != null) {
            donebtn.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showSearchScreen();
                }
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void setupAutoComplete(AutoCompleteTextView autoCompleteTextView) {
        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(workRunnable);
            }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 2) {
                    workRunnable = () -> fetchSuggestions(s.toString(), autoCompleteTextView);
                    handler.postDelayed(workRunnable, 500);
                }
            }
        });
    }

    private void fetchSuggestions(String query, AutoCompleteTextView autoCompleteTextView) {
        new Thread(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String url = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=5";

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url)
                        .header("User-Agent", "EcoHop/1.0")
                        .build();
                Response response = client.newCall(request).execute();
                String jsonData = response.body().string();

                JSONArray jsonArray = new JSONArray(jsonData);
                List<String> suggestions = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject object = jsonArray.getJSONObject(i);
                    suggestions.add(object.getString("display_name"));
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> newAdapter = new ArrayAdapter<>(
                            MainActivity.this,
                            android.R.layout.simple_dropdown_item_1line,
                            suggestions
                    );
                    autoCompleteTextView.setAdapter(newAdapter);
                    newAdapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                Log.e("AutoCompleteDebug", "Error fetching suggestions", e);
            }
        }).start();
    }
}