package com.picopark.viewer;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Base class for platform-specific WebSocket clients.
 * Android: AndroidNetworkClient (org.java-websocket)
 * HTML/GWT: HtmlNetworkClient (czyzby gdx-websockets)
 */
public abstract class NetworkClient {

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    private static final float[][] PLAYER_COLORS = {
        {1f,   0.4f, 0.4f},
        {0.4f, 0.7f, 1f},
        {0.4f, 1f,   0.5f},
        {1f,   1f,   0.4f},
        {1f,   0.6f, 0.2f},
        {0.8f, 0.4f, 1f},
        {1f,   0.4f, 0.8f},
        {0.4f, 1f,   1f},
    };

    protected final GameState state;
    protected Listener listener;
    private final JsonReader jsonReader = new JsonReader();

    private final Object queueLock = new Object();
    private final Array<String> messageQueue = new Array<>();

    public NetworkClient(GameState state) {
        this.state = state;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public float[] getPlayerColor(int joinOrder) {
        return PLAYER_COLORS[Math.max(0, joinOrder) % PLAYER_COLORS.length];
    }

    public abstract void connect(String wsUrl);
    public abstract void disconnect();
    public abstract boolean isConnected();

    /** Thread-safe: enqueue a message received from the WS thread. */
    protected void enqueueMessage(String message) {
        synchronized (queueLock) { messageQueue.add(message); }
    }

    /** Must be called every frame from the render thread. */
    public void processMessages() {
        Array<String> batch;
        synchronized (queueLock) {
            if (messageQueue.size == 0) return;
            batch = new Array<>(messageQueue);
            messageQueue.clear();
        }
        for (int i = 0; i < batch.size; i++) {
            try { handleMessage(batch.get(i)); }
            catch (Exception e) { /* silently skip malformed messages */ }
        }
    }

    private void handleMessage(String text) {
        JsonValue root = jsonReader.parse(text);
        if (root == null) return;
        String type = root.getString("type", "");
        if ("snapshot".equals(type)) handleSnapshot(root.get("snapshot"));
        else if ("gameplay".equals(type)) handleGameplay(root.get("gameState"));
    }

    private void handleSnapshot(JsonValue snap) {
        if (snap == null) return;
        state.hasSnapshot = true;
        state.levelName = snap.getString("level", "All together now");
        state.snapshotPlayers.clear();
        JsonValue arr = snap.get("players");
        if (arr != null) {
            for (JsonValue pv = arr.child; pv != null; pv = pv.next) {
                GameState.SnapshotPlayer sp = new GameState.SnapshotPlayer();
                sp.id        = pv.getString("id", "");
                sp.name      = pv.getString("name", "Player");
                sp.joinOrder = pv.getInt("joinOrder", 0);
                state.snapshotPlayers.add(sp);
            }
        }
    }

    private void handleGameplay(JsonValue gs) {
        if (gs == null) return;
        state.phase            = gs.getString("phase", state.phase);
        state.tickCounter      = gs.getInt("tickCounter", state.tickCounter);
        state.countdownSeconds = gs.getInt("countdownSeconds", state.countdownSeconds);
        state.remainingGems    = gs.getInt("remainingGems", state.remainingGems);
        state.winnerId         = gs.getString("winnerId", state.winnerId);
        state.winnerName       = gs.getString("winnerName", state.winnerName);

        Array<String> seen = new Array<>();
        JsonValue self = gs.get("selfPlayer");
        if (self != null) {
            String id = self.getString("id", "");
            if (id.length() > 0) { seen.add(id); applyPlayer(state.getOrCreatePlayer(id), self); }
        }
        JsonValue others = gs.get("otherPlayers");
        if (others != null) {
            for (JsonValue pv = others.child; pv != null; pv = pv.next) {
                String id = pv.getString("id", "");
                if (id.length() > 0) { seen.add(id); applyPlayer(state.getOrCreatePlayer(id), pv); }
            }
        }
        if (self != null || others != null) state.removePlayersNotIn(seen);

        for (int i = 0; i < state.players.size; i++) {
            GameState.Player p = state.players.get(i);
            for (int j = 0; j < state.snapshotPlayers.size; j++) {
                GameState.SnapshotPlayer sp = state.snapshotPlayers.get(j);
                if (sp.id.equals(p.id)) { p.name = sp.name; p.joinOrder = sp.joinOrder; break; }
            }
        }

        JsonValue gemsArr = gs.get("gems");
        if (gemsArr != null) {
            state.gems.clear();
            for (JsonValue gv = gemsArr.child; gv != null; gv = gv.next) {
                GameState.Gem g = new GameState.Gem();
                g.id     = gv.getString("id", "");
                g.type   = gv.getString("type", "blue");
                g.x      = gv.getFloat("x", 0);
                g.y      = gv.getFloat("y", 0);
                g.width  = gv.getFloat("width", 15);
                g.height = gv.getFloat("height", 15);
                g.value  = gv.getInt("value", 1);
                state.gems.add(g);
            }
        }

        JsonValue layersArr = gs.get("layerTransforms");
        if (layersArr != null) {
            state.layerTransforms.clear();
            for (JsonValue lv = layersArr.child; lv != null; lv = lv.next) {
                GameState.LayerTransform lt = new GameState.LayerTransform();
                lt.index = lv.getInt("index", 0);
                lt.x     = lv.getFloat("x", 0);
                lt.y     = lv.getFloat("y", 0);
                state.layerTransforms.add(lt);
            }
        }
    }

    private void applyPlayer(GameState.Player p, JsonValue pv) {
        p.x             = pv.getFloat("x", p.x);
        p.y             = pv.getFloat("y", p.y);
        p.width         = pv.getFloat("width", p.width);
        p.height        = pv.getFloat("height", p.height);
        p.direction     = pv.getString("direction", p.direction);
        p.facing        = pv.getString("facing", p.facing);
        p.moving        = pv.getBoolean("moving", p.moving);
        p.score         = pv.getInt("score", p.score);
        p.gemsCollected = pv.getInt("gemsCollected", p.gemsCollected);
    }
}
