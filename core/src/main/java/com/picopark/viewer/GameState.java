package com.picopark.viewer;

import com.badlogic.gdx.utils.Array;

public class GameState {

    public static class Player {
        public String id = "";
        public String name = "";
        public float x, y, width = 20, height = 20;
        public String direction = "none";
        public String facing = "down";
        public boolean moving = false;
        public int score = 0;
        public int gemsCollected = 0;
        public int joinOrder = 0;
    }

    public static class Gem {
        public String id = "";
        public String type = "blue";
        public float x, y, width = 15, height = 15;
        public int value = 1;
    }

    public static class LayerTransform {
        public int index;
        public float x, y;
    }

    public static class SnapshotPlayer {
        public String id = "";
        public String name = "";
        public float width = 20, height = 20;
        public int joinOrder = 0;
    }

    // Estat de connexió
    public boolean connected = false;
    public boolean hasSnapshot = false;

    // Fase del joc
    public String phase = "waiting";
    public int countdownSeconds = 60;
    public int remainingGems = 0;
    public int tickCounter = 0;
    public String winnerId = "";
    public String winnerName = "";
    public String levelName = "All together now";

    // Jugadors de la sala (del snapshot)
    public final Array<SnapshotPlayer> snapshotPlayers = new Array<>();

    // Estat de gameplay (actualitzat cada frame)
    public final Array<Player> players = new Array<>();
    public final Array<Gem> gems = new Array<>();
    public final Array<LayerTransform> layerTransforms = new Array<>();

    // Mapa de jugadors per id (per fusionar selfPlayer + otherPlayers)
    public final com.badlogic.gdx.utils.ObjectMap<String, Player> playerById = new com.badlogic.gdx.utils.ObjectMap<>();

    public void clear() {
        connected = false;
        hasSnapshot = false;
        phase = "waiting";
        countdownSeconds = 60;
        remainingGems = 0;
        tickCounter = 0;
        winnerId = "";
        winnerName = "";
        snapshotPlayers.clear();
        players.clear();
        gems.clear();
        layerTransforms.clear();
        playerById.clear();
    }

    public Player getOrCreatePlayer(String id) {
        Player p = playerById.get(id);
        if (p == null) {
            p = new Player();
            p.id = id;
            playerById.put(id, p);
            players.add(p);
        }
        return p;
    }

    public void removePlayersNotIn(Array<String> keepIds) {
        for (int i = players.size - 1; i >= 0; i--) {
            Player p = players.get(i);
            boolean found = false;
            for (String id : keepIds) {
                if (id.equals(p.id)) { found = true; break; }
            }
            if (!found) {
                playerById.remove(p.id);
                players.removeIndex(i);
            }
        }
    }
}
