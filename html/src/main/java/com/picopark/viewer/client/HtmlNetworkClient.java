package com.picopark.viewer.client;

import com.badlogic.gdx.Gdx;
import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSocketHandler;
import com.github.czyzby.websocket.WebSockets;
import com.github.czyzby.websocket.data.WebSocketCloseCode;
import com.picopark.viewer.GameState;
import com.picopark.viewer.NetworkClient;

public class HtmlNetworkClient extends NetworkClient {

    private WebSocket socket;

    public HtmlNetworkClient(GameState state) {
        super(state);
    }

    @Override
    public void connect(String wsUrl) {
        if (isConnected()) return;
        state.clear();

        socket = WebSockets.newSocket(wsUrl);
        socket.setSendGracefully(true);
        socket.addListener(new WebSocketHandler() {
            @Override
            public boolean onOpen(WebSocket webSocket) {
                state.connected = true;
                if (listener != null) listener.onConnected();
                return FULLY_HANDLED;
            }

            @Override
            public boolean onClose(WebSocket webSocket, WebSocketCloseCode code, String reason) {
                state.connected = false;
                if (listener != null) listener.onDisconnected();
                return FULLY_HANDLED;
            }

            @Override
            public boolean onMessage(WebSocket webSocket, String packet) {
                enqueueMessage(packet);
                return FULLY_HANDLED;
            }

            @Override
            public boolean onError(WebSocket webSocket, Throwable error) {
                state.connected = false;
                String msg = error != null && error.getMessage() != null ? error.getMessage() : "Error";
                if (listener != null) listener.onError(msg);
                return FULLY_HANDLED;
            }
        });
        socket.connect();
    }

    @Override
    public void disconnect() {
        if (socket != null) {
            WebSockets.closeGracefully(socket);
            socket = null;
        }
        state.connected = false;
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isOpen();
    }
}
