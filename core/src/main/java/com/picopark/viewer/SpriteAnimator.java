package com.picopark.viewer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class SpriteAnimator {

    private static final int FRAME_W = 32, FRAME_H = 32;
    private static final float ANIM_FPS = 6f;

    // Frame ranges in netanfruits_2.png based on joinOrder
    // joinOrder 0 -> netanzana (frames 0-2), 1 -> netanera (3-5), etc.
    private static final int[][] ANIM_RANGES = {
        {0, 2}, {3, 5}, {6, 8}, {9, 11},
        {12, 14}, {15, 17}, {18, 20}
    };

    private static final int GEM_W = 15, GEM_H = 15, GEM_FRAMES = 5;
    private static final float GEM_FPS = 4f;

    private Texture fruitsSheet;
    private TextureRegion[] playerFrames;

    private Texture gemSheet;
    private TextureRegion[] gemRegions;

    private float animTime = 0;

    public void load() {
        fruitsSheet = new Texture(Gdx.files.internal("levels/media/netanfruits_2.png"));
        int cols = fruitsSheet.getWidth() / FRAME_W;
        int totalFrames = 21;
        playerFrames = new TextureRegion[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            int col = i % cols;
            int row = i / cols;
            playerFrames[i] = new TextureRegion(fruitsSheet, col * FRAME_W, row * FRAME_H, FRAME_W, FRAME_H);
        }

        gemSheet = new Texture(Gdx.files.internal("levels/media/gem.png"));
        int gemCols = gemSheet.getWidth() / GEM_W;
        gemRegions = new TextureRegion[20];
        for (int i = 0; i < 20; i++) {
            int col = i % gemCols;
            int row = i / gemCols;
            gemRegions[i] = new TextureRegion(gemSheet, col * GEM_W, row * GEM_H, GEM_W, GEM_H);
        }
    }

    public void update(float delta) {
        animTime += delta;
    }

    public void drawPlayer(SpriteBatch batch, GameState.Player p, float worldH,
                           float r, float g, float b) {
        if (playerFrames == null) return;

        int joinOrder = p.joinOrder;
        if (joinOrder < 0 || joinOrder >= ANIM_RANGES.length) joinOrder = 0;

        int[] range = ANIM_RANGES[joinOrder];
        int startFrame = range[0];
        int endFrame = range[1];
        int span = endFrame - startFrame + 1;

        int frameIndex = startFrame + ((int)(animTime * ANIM_FPS) % span);
        if (frameIndex >= playerFrames.length) frameIndex = playerFrames.length - 1;

        TextureRegion reg = playerFrames[frameIndex];
        float drawX = p.x;
        float drawY = worldH - p.y - p.height;

        boolean flipX = "left".equals(p.facing);
        if (flipX) reg.flip(true, false);
        batch.setColor(r, g, b, 1f);
        batch.draw(reg, drawX, drawY, p.width, p.height);
        batch.setColor(1f, 1f, 1f, 1f);
        if (flipX) reg.flip(true, false);
    }

    public void drawGem(SpriteBatch batch, GameState.Gem gem, float worldH) {
        if (gemRegions == null) return;
        int start;
        switch (gem.type) {
            case "purple": start = 0; break;
            case "green":  start = 5; break;
            case "yellow": start = 10; break;
            default:       start = 15; break;
        }
        int frame = start + ((int)(animTime * GEM_FPS) % GEM_FRAMES);
        if (frame >= gemRegions.length) frame = gemRegions.length - 1;
        TextureRegion reg = gemRegions[frame];
        float drawX = gem.x;
        float drawY = worldH - gem.y - gem.height;
        batch.draw(reg, drawX, drawY, gem.width, gem.height);
    }

    public void dispose() {
        if (fruitsSheet != null) fruitsSheet.dispose();
        if (gemSheet != null) gemSheet.dispose();
    }
}
