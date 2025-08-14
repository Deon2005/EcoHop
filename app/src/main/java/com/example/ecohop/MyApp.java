package com.example.ecohop;

import android.app.Application;
import android.preference.PreferenceManager;
import org.osmdroid.config.Configuration;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Load osmdroid configuration once for the whole app
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        // Set the user agent to your package name
        Configuration.getInstance().setUserAgentValue(getPackageName());
    }
}
