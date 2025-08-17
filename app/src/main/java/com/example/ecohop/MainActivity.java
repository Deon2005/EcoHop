package com.example.ecohop;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable workRunnable;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.searchdestination);

        AutoCompleteTextView destination_et = findViewById(R.id.destination_et);
        AutoCompleteTextView source_et = findViewById(R.id.source_et);
        Button find_route_btn = findViewById(R.id.find_route_btn);

        setupAutoComplete(destination_et);
        setupAutoComplete(source_et);

        // For now just switch to directions layout
        find_route_btn.setOnClickListener(v -> {
            setContentView(R.layout.choose_plan);
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void setupAutoComplete(AutoCompleteTextView autoCompleteTextView) {
        autoCompleteTextView.setInputMethodMode(android.widget.ListPopupWindow.INPUT_METHOD_NEEDED);

        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(workRunnable);
            }

            @Override
            public void afterTextChanged(Editable s) {
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
