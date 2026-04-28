package com.picopark.viewer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Renderitza les capes de tiles estàtiques del nivell.
 * Les TextureRegions es precalculen a load() i es reutilitzen cada frame.
 */
public class TileMapRenderer {

    private static class LayerDef {
        float originX, originY;
        int tileW, tileH;
        int serverIndex;           // índex de capa al servidor (per layerTransforms)
        int[][] tileMap;
        TextureRegion[][] regions; // precalculat: [row][col] -> regió o null si buit
    }

    private Texture tileset;
    private int tilesetCols;
    private final Array<LayerDef> layers = new Array<>();
    private boolean loaded = false;

    // Definició de capes segons PICOPARK_APP (game_data.json)
    // Només tenim Terrain (layer_000.json)
    private static final String[] LAYER_FILES = {
        "levels/tilemaps/level_000_layer_000.json" // Terrain
    };
    private static final float[] ORIGIN_X = { 0 };
    private static final float[] ORIGIN_Y = { 0 };
    private static final int[]   SRV_IDX  = { 0 };

    public void load() {
        // Usar sandia.png como en PICOPARK_APP
        tileset = new Texture(Gdx.files.internal("levels/media/sandia.png"));
        tilesetCols = tileset.getWidth() / 16;

        JsonReader reader = new JsonReader();
        for (int i = 0; i < LAYER_FILES.length; i++) {
            try {
                JsonValue root = reader.parse(Gdx.files.internal(LAYER_FILES[i]));
                JsonValue mapArr = root.get("tileMap");
                if (mapArr == null) continue;

                LayerDef layer = new LayerDef();
                layer.originX     = ORIGIN_X[i];
                layer.originY     = ORIGIN_Y[i];
                layer.tileW       = 16;
                layer.tileH       = 16;
                layer.serverIndex = SRV_IDX[i];

                int rows = mapArr.size;
                layer.tileMap = new int[rows][];
                int row = 0;
                for (JsonValue rowVal = mapArr.child; rowVal != null; rowVal = rowVal.next) {
                    int cols = rowVal.size;
                    layer.tileMap[row] = new int[cols];
                    int col = 0;
                    for (JsonValue cell = rowVal.child; cell != null; cell = cell.next) {
                        layer.tileMap[row][col++] = cell.asInt();
                    }
                    row++;
                }

                // Precalcular TextureRegions
                layer.regions = new TextureRegion[rows][];
                for (int r = 0; r < rows; r++) {
                    int cols = layer.tileMap[r].length;
                    layer.regions[r] = new TextureRegion[cols];
                    for (int c = 0; c < cols; c++) {
                        int tileId = layer.tileMap[r][c];
                        // Bloques rojos suelen ser IDs altos como 149, 173, etc. en la primera fila.
                        // Si r == 0 es la fila superior del JSON, la saltamos o filtramos.
                        if (tileId < 0 || r == 0) continue; 
                        
                        int idx    = tileId;
                        int tileC  = idx % tilesetCols;
                        int tileR  = idx / tilesetCols;
                        layer.regions[r][c] = new TextureRegion(
                            tileset,
                            tileC * 16, tileR * 16, 16, 16
                        );
                    }
                }
                layers.add(layer);
            } catch (Exception e) {
                Gdx.app.error("TileMapRenderer", "Error carregant " + LAYER_FILES[i] + ": " + e.getMessage());
            }
        }
        loaded = true;
    }

    /**
     * @param worldH  alçada del món en coordenades de joc (720)
     * @param transforms  desplaçaments de capes del servidor
     */
    public void render(SpriteBatch batch, float worldH, Array<GameState.LayerTransform> transforms) {
        if (!loaded) return;
        for (int li = 0; li < layers.size; li++) {
            LayerDef layer = layers.get(li);
            float offX = 0, offY = 0;
            for (int ti = 0; ti < transforms.size; ti++) {
                GameState.LayerTransform lt = transforms.get(ti);
                if (lt.index == layer.serverIndex) {
                    offX = lt.x - layer.originX;
                    offY = lt.y - layer.originY;
                    break;
                }
            }
            for (int r = 0; r < layer.regions.length; r++) {
                for (int c = 0; c < layer.regions[r].length; c++) {
                    TextureRegion reg = layer.regions[r][c];
                    if (reg == null) continue;
                    float drawX = layer.originX + offX + c * layer.tileW;
                    // Convertir Y: servidor Y cap avall -> LibGDX Y cap amunt
                    float drawY = worldH - (layer.originY + offY + r * layer.tileH) - layer.tileH;
                    batch.draw(reg, drawX, drawY, layer.tileW, layer.tileH);
                }
            }
        }
    }

    public void dispose() {
        if (tileset != null) tileset.dispose();
    }

    public boolean isLoaded() { return loaded; }
}
