package com.picopark.viewer.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.github.czyzby.websocket.WebSockets;
import com.picopark.viewer.GameState;
import com.picopark.viewer.ViewerGame;

public class HtmlLauncher extends GwtApplication {

    @Override
    public GwtApplicationConfiguration getConfig() {
        GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
        cfg.padVertical   = 0;
        cfg.padHorizontal = 0;
        return cfg;
    }

    @Override
    public ApplicationListener createApplicationListener() {
        WebSockets.initiate();

        String wsUrl = getQueryParam("ws");
        if (wsUrl == null || wsUrl.isEmpty()) wsUrl = buildDefaultWsUrl();

        GameState state = new GameState();
        HtmlNetworkClient network = new HtmlNetworkClient(state);
        return new ViewerGame(network, wsUrl);
    }

    private native String getQueryParam(String name) /*-{
        var search = $wnd.location.search.substring(1);
        var pairs  = search.split('&');
        for (var i = 0; i < pairs.length; i++) {
            var kv = pairs[i].split('=');
            if (decodeURIComponent(kv[0]) === name) {
                return decodeURIComponent(kv[1] || '');
            }
        }
        return null;
    }-*/;

    private native String buildDefaultWsUrl() /*-{
        var proto = $wnd.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return proto + '//' + $wnd.location.host;
    }-*/;
}
