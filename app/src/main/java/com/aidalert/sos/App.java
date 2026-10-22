package com.aidalert.sos;

import android.app.Application;

import com.aidalert.sos.util.Notifier;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.createChannels(this);
    }
}
