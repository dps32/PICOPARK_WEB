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
    private OrthographicCamera hudCamera;
    private Viewport viewport;
    private Viewport hudViewport;

    private GameState       state;
    private NetworkClient   network;
    private TileMapRenderer tiles;
    private SpriteAnimator  sprites;
    private com.badlogic.gdx.graphics.Texture backgroundTexture;

    private String statusMsg = "Connectant...";
    private float  retryDelay = 0f;
    private boolean tryingFallback = false;

    private enum Mode { PROBING_PRIMARY, PROBING_SECONDARY, WAITING_FOR_USER, ACTIVE }
    private Mode mode = Mode.ACTIVE;
    private int countPrimary = -1;
    private int countSecondary = -1;
    private float probeTimer = 0;

    private com.badlogic.gdx.math.Rectangle primaryBtn = new com.badlogic.gdx.math.Rectangle();
    private com.badlogic.gdx.math.Rectangle secondaryBtn = new com.badlogic.gdx.math.Rectangle();
    private com.badlogic.gdx.math.Vector3 touchPoint = new com.badlogic.gdx.math.Vector3();


    public ViewerGame(NetworkClient network, String wsUrl) {
        this(network, wsUrl, null);
    }

    public ViewerGame(NetworkClient network, String wsUrl, String fallbackUrl) {
        this.networkSupplied = network;
        this.wsUrl = wsUrl;
        this.fallbackUrl = fallbackUrl;
        this.currentUrl = wsUrl;
        if (fallbackUrl != null) {
            this.mode = Mode.PROBING_PRIMARY;
        }
    }

    @Override
    public void create() {
        batch  = new SpriteBatch();
        font   = new BitmapFont();
        layout = new GlyphLayout();
        font.setColor(Color.WHITE);

        camera = new OrthographicCamera();
        hudCamera = new OrthographicCamera(WORLD_W, WORLD_H);
        hudCamera.position.set(WORLD_W / 2f, WORLD_H / 2f, 0);
        hudCamera.update();

        viewport = new FitViewport(WORLD_W, WORLD_H, camera);
        hudViewport = new FitViewport(WORLD_W, WORLD_H, hudCamera);

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
                if (mode == Mode.ACTIVE) {
                    handleConnectionFailure();
                } else {
                    handleProbeFinished(0);
                }
            }
            @Override public void onError(String message) {
                statusMsg = "Error: " + message;
                if (mode == Mode.ACTIVE) {
                    handleConnectionFailure();
                } else {
                    handleProbeFinished(0);
                }
            }
        });

        if (mode == Mode.PROBING_PRIMARY) {
            statusMsg = "Buscant millor servidor...";
            startProbe(wsUrl);
        } else {
            network.connect(currentUrl);
        }
    }

    private void startProbe(String url) {
        probeTimer = 2.0f; // 2 segons de timeout per probe
        network.disconnect();
        network.connect(url);
    }

    private void handleProbeFinished(int count) {
        if (mode == Mode.PROBING_PRIMARY) {
            countPrimary = count;
            Gdx.app.log("ViewerGame", "Probe Primary (" + wsUrl + "): " + count);
            mode = Mode.PROBING_SECONDARY;
            startProbe(fallbackUrl);
        } else if (mode == Mode.PROBING_SECONDARY) {
            countSecondary = count;
            Gdx.app.log("ViewerGame", "Probe Secondary (" + fallbackUrl + "): " + count);
            network.disconnect();
            mode = Mode.WAITING_FOR_USER;
            statusMsg = "Selecciona un servidor para conectar";
        }
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

        handleInput();
        network.processMessages();

        updateCamera(delta);

        if (mode != Mode.ACTIVE) {
            probeTimer -= delta;
            if (state.hasSnapshot) {
                handleProbeFinished(state.snapshotPlayers.size);
            } else if (probeTimer <= 0) {
                handleProbeFinished(0);
            }
        }

        if (retryDelay > 0) {
            retryDelay -= delta;
            if (retryDelay <= 0) {
                tryingFallback = false;
                network.connect(currentUrl);
                statusMsg = "Reconnectant...";
            }
        }
        sprites.update(delta);

        viewport.apply();
        Gdx.gl.glClearColor(0.04f, 0.07f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (shouldShowWaitingScreen()) {
            batch.setProjectionMatrix(hudCamera.combined);
            batch.begin();
            renderWaitingScreen();
            batch.end();
        } else {
            // 1. Renderizar el mundo (Background, Tiles, Jugadores)
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            
            // Dibujamos el fondo siguiendo a la cámara para que parezca infinito o cubra todo
            float bgX = camera.position.x - WORLD_W / 2f;
            float bgY = camera.position.y - WORLD_H / 2f;
            batch.draw(backgroundTexture, bgX, bgY, WORLD_W, WORLD_H,
                       (int)bgX, (int)-bgY, (int) WORLD_W, (int) WORLD_H, false, false);

            tiles.render(batch, WORLD_H, state.layerTransforms);

            for (int i = 0; i < state.players.size; i++) {
                GameState.Player p = state.players.get(i);
                float[] c = network.getPlayerColor(p.joinOrder);
                sprites.drawPlayer(batch, p, WORLD_H, c[0], c[1], c[2]);
            }
            batch.end();

            // 2. Renderizar el HUD (Cámara estática / Viewport puro)
            batch.setProjectionMatrix(hudCamera.combined);

            batch.begin();
            renderHUD();
            batch.end();
        }
    }

    private void updateCamera(float delta) {
        if (state.players.size > 0) {
            float avgX = 0, avgY = 0;
            for (int i = 0; i < state.players.size; i++) {
                GameState.Player p = state.players.get(i);
                avgX += p.x + p.width / 2f;
                avgY += (WORLD_H - p.y - p.height / 2f);
            }
            avgX /= state.players.size;
            avgY /= state.players.size;

            float lerp = 3f * delta;
            camera.position.x += (avgX - camera.position.x) * lerp;
            camera.position.y += (avgY - camera.position.y) * lerp;
            camera.update();
        }
    }

    private void handleInput() {
        if (Gdx.input.justTouched()) {
            touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(touchPoint);

            if (primaryBtn.contains(touchPoint.x, touchPoint.y)) {
                if (mode == Mode.WAITING_FOR_USER) {
                    mode = Mode.ACTIVE;
                    switchToServer(wsUrl);
                } else if (mode == Mode.ACTIVE) {
                    switchToServer(wsUrl);
                }
            } else if (secondaryBtn.contains(touchPoint.x, touchPoint.y)) {
                if (mode == Mode.WAITING_FOR_USER) {
                    mode = Mode.ACTIVE;
                    switchToServer(fallbackUrl);
                } else if (mode == Mode.ACTIVE) {
                    switchToServer(fallbackUrl);
                }
            }
        }
    }

    private void switchToServer(String url) {
        if (url == null || url.equals(currentUrl)) return;
        currentUrl = url;
        tryingFallback = false;
        statusMsg = "Cambiando a " + getServerLabel(url) + "...";
        network.disconnect();
        network.connect(currentUrl);
    }

    private boolean shouldShowWaitingScreen() {
        return !state.hasSnapshot ||
               state.snapshotPlayers.size < 2 ||
               (state.players.size < 2 && "waiting".equals(state.phase));
    }

    private void renderWaitingScreen() {
        font.getData().setScale(0.8f);
        font.setColor(1f, 1f, 1f, 1f);
        String msg = "Pico Park Viewer";
        if (mode == Mode.WAITING_FOR_USER) {
            msg = "Selecciona Servidor";
        } else if (state.hasSnapshot && state.snapshotPlayers.size < 2) {
            msg = "Esperando jugadores (Mín. 2)...";
        } else if (!state.connected && mode == Mode.ACTIVE) {
            msg = "Conectando...";
        }
        
        layout.setText(font, msg);
        float tx = (WORLD_W - layout.width) / 2f;
        float ty = WORLD_H / 2f + layout.height / 2f + 15;
        font.draw(batch, msg, tx, ty);

        font.getData().setScale(0.5f);
        if ((mode == Mode.ACTIVE || mode == Mode.WAITING_FOR_USER) && fallbackUrl != null) {
            float btnW = 110, btnH = 30;
            float centerX = WORLD_W / 2f;

            // Botón Primario
            primaryBtn.set(centerX - btnW - 10, WORLD_H / 2f - 20, btnW, btnH);
            boolean isPrimaryActive = mode == Mode.ACTIVE && currentUrl.equals(wsUrl);
            
            // Dibujar fondo de botón (opcional, pero ayuda visualmente)
            // Aquí usamos el color de la fuente para indicar estado
            font.setColor(isPrimaryActive ? Color.CYAN : Color.WHITE);
            String pLabel = getServerLabel(wsUrl);
            if (countPrimary >= 0) pLabel += " [" + countPrimary + "P]";
            layout.setText(font, pLabel);
            font.draw(batch, pLabel, primaryBtn.x + (btnW - layout.width) / 2f, primaryBtn.y + 20);

            // Botón Secundario
            secondaryBtn.set(centerX + 10, WORLD_H / 2f - 20, btnW, btnH);
            boolean isSecondaryActive = mode == Mode.ACTIVE && currentUrl.equals(fallbackUrl);
            
            font.setColor(isSecondaryActive ? Color.CYAN : Color.WHITE);
            String sLabel = getServerLabel(fallbackUrl);
            if (countSecondary >= 0) sLabel += " [" + countSecondary + "P]";
            layout.setText(font, sLabel);
            font.draw(batch, sLabel, secondaryBtn.x + (btnW - layout.width) / 2f, secondaryBtn.y + 20);
        }

        if (mode == Mode.ACTIVE) {
            font.getData().setScale(0.4f);
            if (!state.connected) {
                font.setColor(1f, 0.7f, 0.2f, 1f);
                layout.setText(font, statusMsg);
                font.draw(batch, statusMsg, (WORLD_W - layout.width) / 2f, 15);
            } else {
                font.setColor(0.4f, 1f, 0.4f, 1f);
                String connMsg = "Conectado: " + state.snapshotPlayers.size + " jugadores";
                layout.setText(font, connMsg);
                font.draw(batch, connMsg, (WORLD_W - layout.width) / 2f, 15);
            }
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
                font.draw(batch, p.name + " " + p.score, sx, sy);
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

    private String getServerLabel(String url) {
        if (url == null) return "N/A";
        if (url.contains("10.0.2.2") || url.contains("localhost") || url.contains("127.0.0.1")) return "LOCAL";
        if (url.contains("ieti.site") || url.contains("pico")) return "REMOTO";
        return "SERVER";
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
        hudViewport.update(width, height, true);
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
