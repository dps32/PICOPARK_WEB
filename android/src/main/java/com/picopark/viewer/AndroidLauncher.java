package com.picopark.viewer;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class AndroidLauncher extends AndroidApplication {

    private static final String REMOTE_WS_URL = "wss://pico5.ieti.site";
    private static final String LOCAL_WS_URL = "ws://10.0.2.2:3000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra("WS_URL");
        if (url == null || url.isEmpty()) {
            url = REMOTE_WS_URL;
        }

        GameState state = new GameState();
        AndroidNetworkClient network = new AndroidNetworkClient(state);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        initialize(new ViewerGame(network, url, LOCAL_WS_URL), config);
    }
}
