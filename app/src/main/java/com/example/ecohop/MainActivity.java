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
    private String choosenplan;
    String source= "";
    String destination= "";
    String complete_plan= "Plans will be displayed here........";
    String detailed_plan="Detailed plan will be displayed here.............";

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSearchScreen();   // Start with Search screen
    }

    // ---------------- SEARCH SCREEN ----------------
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void showSearchScreen() {
        setContentView(R.layout.searchdestination);

        AutoCompleteTextView destination_et = findViewById(R.id.destination_et);
        AutoCompleteTextView source_et = findViewById(R.id.source_et);
        Button find_route_btn = findViewById(R.id.find_route_btn);

        setupAutoComplete(destination_et);
        setupAutoComplete(source_et);

        // Navigate to Choose Plan screen
        find_route_btn.setOnClickListener(v -> {
            String source= source_et.getText().toString().trim();
            String destination= destination_et.getText().toString().trim();
            if (!source.isEmpty() && !destination.isEmpty()) {
                // Both fields are filled
                showChoosePlanScreen();
            } else {
                // At least one field is empty
                Toast.makeText(this, "Please fill both you start and end point", Toast.LENGTH_SHORT).show();
            }

        });
    }

    // ---------------- CHOOSE PLAN SCREEN ----------------
    private void showChoosePlanScreen() {
        setContentView(R.layout.choose_plan);
        TextView fullplan = findViewById(R.id.full_plan);
        fullplan.setText(this.complete_plan);

        ImageButton back1 = findViewById(R.id.back1); // back button in choose_plan
        if (back1 != null) {
            back1.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showSearchScreen();
                }
            });
        }

        Button plan1 = findViewById(R.id.plan1);
        Button plan2 = findViewById(R.id.plan2);
        Button plan3 = findViewById(R.id.plan3);

        if (plan1 != null) {
            plan1.setOnClickListener(v -> {
                choosenplan = "plan1";
                showTravelingScreen();
            });
        }
        if (plan2 != null) {
            plan2.setOnClickListener(v -> {
                choosenplan = "plan2";
                showTravelingScreen();
            });
        }
        if (plan3 != null) {
            plan3.setOnClickListener(v -> {
                choosenplan = "plan3";
                showTravelingScreen();
            });
        }
    }

    // ---------------- TRAVELING SCREEN ----------------
    private void showTravelingScreen() {
        setContentView(R.layout.traveling);
        TextView detailedplan = findViewById(R.id.detailed_plan);
        detailedplan.setText(this.detailed_plan);

        ImageButton back2 = findViewById(R.id.back2); // back button in traveling
        if (back2 != null) {
            back2.setOnClickListener(v -> showChoosePlanScreen());
        }

        Button donebtn = findViewById(R.id.donebtn);
        if (donebtn != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                donebtn.setOnClickListener(v -> showSearchScreen());
            }
        }
    }

    // ---------------- AUTOCOMPLETE HANDLER ----------------
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
        Log.d("AutoCompleteDebug", "Fetching suggestions for: " + query);

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
            } catch (Exception e) {
                Log.e("AutoCompleteDebug", "Error fetching suggestions", e);
            }
        }).start();
    }
}
