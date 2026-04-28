package com.picopark.viewer;

import com.badlogic.gdx.Gdx;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class AndroidNetworkClient extends NetworkClient {

    private WebSocketClient wsClient;
    private volatile boolean connecting = false;

    public AndroidNetworkClient(GameState state) {
        super(state);
    }

    @Override
    public void connect(String wsUrl) {
        if (connecting || isConnected()) return;
        connecting = true;
        state.clear();

        try {
            wsClient = new WebSocketClient(new URI(wsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connecting = false;
                    state.connected = true;
                    if (listener != null) Gdx.app.postRunnable(() -> listener.onConnected());
                }

                @Override
                public void onMessage(String message) {
                    enqueueMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connecting = false;
                    state.connected = false;
                    if (listener != null) Gdx.app.postRunnable(() -> listener.onDisconnected());
                }

                @Override
                public void onError(Exception ex) {
                    connecting = false;
                    state.connected = false;
                    String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "Error";
                    if (listener != null) Gdx.app.postRunnable(() -> listener.onError(msg));
                }
            };
            wsClient.setConnectionLostTimeout(30);
            wsClient.connect();
        } catch (Exception e) {
            connecting = false;
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        if (wsClient != null) {
            try { wsClient.close(); } catch (Exception ignored) {}
            wsClient = null;
        }
        connecting = false;
        state.connected = false;
    }

    @Override
    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }
}
