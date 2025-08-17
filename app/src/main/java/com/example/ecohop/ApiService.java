package com.example.ecohop;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {

    // IMPORTANT: 10.0.2.2 is the special alias for your computer's localhost
    // when running the app in the Android Emulator.
    // If you deploy your server online, replace this with your public URL.
    private static final String SERVER_URL = "https://ecohop-backend.onrender.com/api/analyze_journey";

    private final OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // A callback interface to handle async responses
    public interface ApiCallback {
        void onSuccess(String routes, String analysis);
        void onFailure(Exception e);
    }

    public void analyzeJourney(String startLocation, String endLocation, final ApiCallback callback) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("start_location", startLocation);
            jsonBody.put("end_location", endLocation);
        } catch (JSONException e) {
            callback.onFailure(e);
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject resultJson = new JSONObject(responseBody);
                        String routes = resultJson.getString("routes");
                        String analysis = resultJson.getString("analysis");
                        callback.onSuccess(routes, analysis);
                    } catch (JSONException e) {
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new IOException("Request failed with code: " + response.code()));
                }
            }
        });
    }
}