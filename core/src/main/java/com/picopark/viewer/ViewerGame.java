package com.picopark.viewer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class ViewerGame extends ApplicationAdapter {

    public static final float WORLD_W = 280f;
    public static final float WORLD_H = 160f;

    private final String wsUrl;
    private final String fallbackUrl;
    private String currentUrl;
    private final NetworkClient networkSupplied;

    private SpriteBatch batch;
    private BitmapFont  font;
    private GlyphLayout layout;
    private OrthographicCamera camera;
    private Viewport viewport;

    private GameState       state;
    private NetworkClient   network;
    private TileMapRenderer tiles;
    private SpriteAnimator  sprites;
    private com.badlogic.gdx.graphics.Texture backgroundTexture;

    private String statusMsg = "Connectant...";
    private float  retryDelay = 0f;
    private boolean tryingFallback = false;

    // Camera tracking
    private static final float CAM_SPEED = 4f;
    private float targetCamX = WORLD_W / 2f;
    private float targetCamY = WORLD_H / 2f;
    private int cameraPlayerIndex = 0;
    private float cameraCycleTimer = 0f;
    private static final float CAMERA_CYCLE_TIME = 3f;

    public ViewerGame(NetworkClient network, String wsUrl) {
        this(network, wsUrl, null);
    }

    public ViewerGame(NetworkClient network, String wsUrl, String fallbackUrl) {
        this.networkSupplied = network;
        this.wsUrl = wsUrl;
        this.fallbackUrl = fallbackUrl;
        this.currentUrl = wsUrl;
    }

    @Override
    public void create() {
        batch  = new SpriteBatch();
        font   = new BitmapFont();
        layout = new GlyphLayout();
        font.setColor(Color.WHITE);

        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_W, WORLD_H, camera);
        camera.position.set(WORLD_W / 2f, WORLD_H / 2f, 0);
        camera.update();

        state   = networkSupplied.state;
        tiles   = new TileMapRenderer();
        sprites = new SpriteAnimator();
        tiles.load();
        sprites.load();

        backgroundTexture = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("levels/media/background.png"));
        backgroundTexture.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat, com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat);

        network = networkSupplied;
        network.setListener(new NetworkClient.Listener() {
            @Override public void onConnected() {
                statusMsg = "Connectat";
                retryDelay = 0;
            }
            @Override public void onDisconnected() {
                statusMsg = "Desconnectat";
                handleConnectionFailure();
            }
            @Override public void onError(String message) {
                statusMsg = "Error: " + message;
                handleConnectionFailure();
            }
        });

        network.connect(currentUrl);
    }

    private void handleConnectionFailure() {
        if (!tryingFallback && fallbackUrl != null) {
            tryingFallback = true;
            currentUrl = fallbackUrl;
            statusMsg = "Provant fallback...";
            network.connect(currentUrl);
        } else {
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        retryDelay = 3f;
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        if (retryDelay > 0) {
            retryDelay -= delta;
            if (retryDelay <= 0) {
                tryingFallback = false;
                currentUrl = wsUrl;
                network.connect(currentUrl);
                statusMsg = "Reconnectant...";
            }
        }

        network.processMessages();
        sprites.update(delta);

        updateCamera(delta);

        viewport.apply();
        Gdx.gl.glClearColor(0.04f, 0.07f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (shouldShowWaitingScreen()) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            renderWaitingScreen();
            batch.end();
        } else {
            // 1. Renderizar el mundo (Background, Tiles, Jugadores)
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            
            // Background con efecto Parallax
            float depthSensitivity = 0.08f;
            float depth = 5.0f; 
            float projectionFactor = (float) Math.exp(-depth * depthSensitivity);
            float viewW = viewport.getWorldWidth();
            float viewH = viewport.getWorldHeight();
            
            batch.draw(backgroundTexture, 
                       camera.position.x - viewW/2f, 
                       camera.position.y - viewH/2f, 
                       viewW, viewH, 
                       (int)(camera.position.x * projectionFactor), 0, (int)viewW, (int)viewH, false, false);

            tiles.render(batch, WORLD_H, state.layerTransforms);

            for (int i = 0; i < state.players.size; i++) {
                GameState.Player p = state.players.get(i);
                float[] c = network.getPlayerColor(p.joinOrder);
                sprites.drawPlayer(batch, p, WORLD_H, c[0], c[1], c[2]);
            }
            batch.end();

            // 2. Renderizar el HUD (Cámara estática / Viewport puro)
            // Esto asegura que el HUD no se mueva con la cámara del mundo y se quede fijo en la pantalla
            batch.setProjectionMatrix(viewport.getCamera().combined);
            // Reseteamos la posición de la cámara del viewport para el HUD si fuera necesario, 
            // pero viewport.getCamera() ya nos da la matriz de proyección "limpia" de 0,0 a W,H
            camera.update(); // Asegurar que no hay transformaciones pendientes

            batch.begin();
            renderHUD();
            batch.end();
        }
    }

    private void updateCamera(float delta) {
        if (state.players.size == 0) {
            targetCamX = WORLD_W / 2f;
            targetCamY = WORLD_H / 2f;
        } else {
            // Seguir al que va más adelantado (derecha)
            float maxX = -1000000f;
            for (int i = 0; i < state.players.size; i++) {
                GameState.Player p = state.players.get(i);
                if (p.x > maxX) {
                    maxX = p.x;
                }
            }
            // Centramos la cámara un poco por delante del jugador más avanzado
            targetCamX = maxX + 40f; 
            targetCamY = WORLD_H / 2f;
        }

        float halfViewW = viewport.getWorldWidth() * 0.5f;
        float halfViewH = viewport.getWorldHeight() * 0.5f;
        
        // No limitamos maxX por la derecha para permitir scroll infinito si el nivel lo es,
        // pero aquí limitamos según WORLD_W (ancho total del mapa)
        float mapWidth = 64 * 16; // El tilemap tiene 64 columnas
        targetCamX = Math.max(halfViewW, Math.min(mapWidth - halfViewW, targetCamX));
        targetCamY = Math.max(halfViewH, Math.min(WORLD_H - halfViewH, targetCamY));

        float lerp = Math.min(1f, 2.0f * delta); // Movimiento suave
        camera.position.x += (targetCamX - camera.position.x) * lerp;
        camera.position.y += (targetCamY - camera.position.y) * lerp;
        camera.update();
    }

    private boolean shouldShowWaitingScreen() {
        return !state.hasSnapshot ||
               state.snapshotPlayers.size < 2 ||
               (state.players.size < 2 && "waiting".equals(state.phase));
    }

    private void renderWaitingScreen() {
        font.getData().setScale(0.8f);
        font.setColor(1f, 1f, 1f, 1f);
        String msg = "Esperando partida...";
        if (state.hasSnapshot && state.snapshotPlayers.size < 2) {
            msg = "Esperando jugadores (Mín. 2)...";
        }
        layout.setText(font, msg);
        float tx = (WORLD_W - layout.width) / 2f;
        float ty = WORLD_H / 2f + layout.height / 2f + 10;
        font.draw(batch, msg, tx, ty);

        font.getData().setScale(0.5f);
        if (!state.connected) {
            font.setColor(1f, 0.7f, 0.2f, 1f);
            layout.setText(font, statusMsg);
            float sx = (WORLD_W - layout.width) / 2f;
            font.draw(batch, statusMsg, sx, WORLD_H / 2f - 10);

            font.getData().setScale(0.4f);
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            String urlMsg = "Servidor: " + currentUrl;
            layout.setText(font, urlMsg);
            float ux = (WORLD_W - layout.width) / 2f;
            font.draw(batch, urlMsg, ux, WORLD_H / 2f - 25);
        } else {
            font.setColor(0.4f, 1f, 0.4f, 1f);
            String connMsg = "Conectado - Jugadores: " + state.snapshotPlayers.size + "/2";
            layout.setText(font, connMsg);
            font.draw(batch, connMsg, (WORLD_W - layout.width) / 2f, WORLD_H / 2f - 10);
        }

        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private void renderHUD() {
        // Al usar una matriz de proyección estática en render(), las coordenadas 0,0 
        // son la esquina inferior izquierda de la pantalla física.
        float viewW = viewport.getWorldWidth();
        float viewH = viewport.getWorldHeight();

        font.getData().setScale(0.6f);
        font.setColor(Color.WHITE);
        String phaseStr = phaseLabel();
        font.draw(batch, phaseStr, 8, viewH - 8);

        if (state.players.size > 0) {
            font.getData().setScale(0.5f);
            font.setColor(1f, 1f, 1f, 0.85f);
            float sx = viewW - 85, sy = viewH - 8;
            font.draw(batch, "JUGADORS", sx, sy); sy -= 10;

            for (int i = 0; i < state.players.size; i++) {
                GameState.Player p = state.players.get(i);
                float[] c = network.getPlayerColor(p.joinOrder);
                font.setColor(c[0], c[1], c[2], 1f);
                String marker = (i == cameraPlayerIndex) ? "> " : "  ";
                font.draw(batch, marker + p.name + " " + p.score, sx, sy);
                sy -= 8;
            }
            font.setColor(Color.WHITE);
        }

        if ("finished".equals(state.phase) && state.winnerName != null && state.winnerName.length() > 0) {
            font.getData().setScale(0.8f);
            font.setColor(1f, 0.9f, 0.2f, 1f);
            String msg = "GUANYADOR: " + state.winnerName;
            layout.setText(font, msg);
            float tx = (viewW - layout.width) / 2f;
            float ty = viewH / 2f + layout.height / 2f + 5;
            font.draw(batch, msg, tx, ty);
            font.setColor(Color.WHITE);
        }

        if (!state.connected) {
            font.getData().setScale(0.5f);
            font.setColor(1f, 0.7f, 0.2f, 1f);
            font.draw(batch, statusMsg, 8, 15);
            font.setColor(Color.WHITE);
        }

        font.getData().setScale(1f);
    }

    private String phaseLabel() {
        if ("waiting".equals(state.phase))  return "ESPERANT  " + state.countdownSeconds + "s";
        if ("playing".equals(state.phase))  return "EN JOC";
        if ("finished".equals(state.phase)) return "PARTIDA ACABADA";
        return "";
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        tiles.dispose();
        sprites.dispose();
        network.disconnect();
    }
}
