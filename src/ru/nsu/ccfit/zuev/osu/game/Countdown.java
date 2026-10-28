package ru.nsu.ccfit.zuev.osu.game;

import android.graphics.PointF;

import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.rian.andengine.modifier.ModifierRegistry;
import com.rian.andengine.modifier.ModifierTicker;
import com.rian.andengine.modifier.VerktexModifiers;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.Constants;
import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.Utils;

public class Countdown extends GameObject {
    public static final float COUNTDOWN_LENGTH = 3f;
    private final UISprite ready;
    private final UISprite count1, count2, count3;
    private final UISprite go;
    private final float speed;
    private float timepassed;
    private UIContainer scene;

    // A sprite's modifiers are paused like the AndEngine setIgnoreUpdate; a paused sequence starts when it
    // is unpaused. They run on the frame ticker, the elapsed frame times the AndEngine modifiers were given.
    public Countdown(final UIContainer scene,
                     final float speed, final float offset, final float time, final ModifierTicker frameTicker) {
        this.speed = speed;
        this.scene = scene;
        timepassed = -time + COUNTDOWN_LENGTH * speed;
        final PointF center = Utils.trackToRealCoords(new PointF((float) Constants.MAP_WIDTH / 2, (float) Constants.MAP_HEIGHT / 2));

        ready = new UISprite();
        ready.setOrigin(Anchor.Center);
        ready.setX(DimensionKt.getPx(center.x));
        ready.setY(DimensionKt.getPx(center.y));
        ready.setTextureRegion(SkinTextures.getWithBeatmapSkin("ready"));

        count3 = new UISprite(SkinTextures.getWithBeatmapSkin("count3"));
        count3.setX(DimensionKt.getPx(0));
        count3.setY(DimensionKt.getPx(center.y - count3.getTextureRegion().getHeight() / 2));
        count3.setVisible(false);

        count2 = new UISprite(SkinTextures.getWithBeatmapSkin("count2"));
        count2.setX(DimensionKt.getPx(Config.getRES_WIDTH() - count2.getTextureRegion().getWidth()));
        count2.setY(DimensionKt.getPx(center.y
                - count2.getTextureRegion().getHeight() / 2));
        count2.setVisible(false);

        count1 = new UISprite(SkinTextures.getWithBeatmapSkin("count1"));
        count1.setX(DimensionKt.getPx(center.x - count1.getTextureRegion().getWidth() / 2));
        count1.setY(DimensionKt.getPx(center.y - count1.getTextureRegion().getHeight() / 2));
        count1.setVisible(false);

        go = new UISprite();
        go.setOrigin(Anchor.Center);
        go.setX(DimensionKt.getPx(center.x));
        go.setY(DimensionKt.getPx(center.y));
        go.setTextureRegion(SkinTextures.getWithBeatmapSkin("go"));

        scene.addToBack(ready);
        scene.addToBack(go);
        scene.addToBack(count1);
        scene.addToBack(count2);
        scene.addToBack(count3);

        ModifierRegistry.bindTicker(ready, frameTicker);
        ModifierRegistry.bindTicker(go, frameTicker);
        ModifierRegistry.bindTicker(count1, frameTicker);
        ModifierRegistry.bindTicker(count2, frameTicker);
        ModifierRegistry.bindTicker(count3, frameTicker);

        // The sequences start once the sprites are in the playfield, at its gameplay time.
        VerktexModifiers.beginModifierSequence(ready, sequence -> sequence
                .fadeInFromZero(COUNTDOWN_LENGTH * speed / 9)
                .rotateTo(0, COUNTDOWN_LENGTH * speed / 9)
                .then(COUNTDOWN_LENGTH * speed / 9)
                .fadeOutFromOne(COUNTDOWN_LENGTH * speed / 9)
                .scaleTo(1.5f, COUNTDOWN_LENGTH * speed / 9));
        ready.setRotationZ(-90);
        ready.setVisible(false);
        ModifierRegistry.setPaused(ready, true);
        ModifierRegistry.setPaused(count3, true);
        VerktexModifiers.beginModifierSequence(count3, sequence -> sequence
                .fadeInFromZero(COUNTDOWN_LENGTH * speed / 18)
                .then(COUNTDOWN_LENGTH * speed * 8 / 18)
                .fadeOutFromOne(COUNTDOWN_LENGTH * speed / 18));
        ModifierRegistry.setPaused(count2, true);
        VerktexModifiers.beginModifierSequence(count2, sequence -> sequence
                .fadeInFromZero(COUNTDOWN_LENGTH * speed / 18)
                .then(COUNTDOWN_LENGTH * speed * 5 / 18)
                .fadeOutFromOne(COUNTDOWN_LENGTH * speed / 18));
        ModifierRegistry.setPaused(count1, true);
        VerktexModifiers.beginModifierSequence(count1, sequence -> sequence
                .fadeInFromZero(COUNTDOWN_LENGTH * speed / 18)
                .then(COUNTDOWN_LENGTH * speed * 2 / 18)
                .fadeOutFromOne(COUNTDOWN_LENGTH * speed / 18));
        VerktexModifiers.beginModifierSequence(go, sequence -> sequence
                .fadeInFromZero(COUNTDOWN_LENGTH * speed / 18)
                .rotateTo(0, COUNTDOWN_LENGTH * speed / 18)
                .then(COUNTDOWN_LENGTH * speed / 18)
                .fadeOutFromOne(COUNTDOWN_LENGTH * speed / 18));
        go.setRotationZ(-180);
        go.setVisible(false);
        ModifierRegistry.setPaused(go, true);
    }

    private void playIfNotNull(String resname) {
        var sound = ResourceManager.getInstance().getCustomSound(resname, 1);
        if (sound != null)
            sound.play();
    }

    @Override
    public void update(final float dt) {
        if (scene == null) {
            return;
        }
        timepassed += dt;

        if (timepassed >= 0 && timepassed - dt < 0) {
            playIfNotNull("readys");
            ready.setVisible(true);
            ModifierRegistry.setPaused(ready, false);
        }

        if (timepassed >= COUNTDOWN_LENGTH * speed * 2 / 6
                && timepassed - dt < COUNTDOWN_LENGTH * speed * 2 / 6) {
            playIfNotNull("count3s");
            count3.setVisible(true);
            ModifierRegistry.setPaused(count3, false);
        }

        if (timepassed >= COUNTDOWN_LENGTH * speed * 3 / 6
                && timepassed - dt < COUNTDOWN_LENGTH * speed * 3 / 6) {
            playIfNotNull("count2s");
            count2.setVisible(true);
            ModifierRegistry.setPaused(count2, false);
        }

        if (timepassed >= COUNTDOWN_LENGTH * speed * 4 / 6
                && timepassed - dt < COUNTDOWN_LENGTH * speed * 4 / 6) {
            playIfNotNull("count1s");
            count1.setVisible(true);
            ModifierRegistry.setPaused(count1, false);
        }

        if (timepassed >= COUNTDOWN_LENGTH * speed * 5 / 6
                && timepassed - dt < COUNTDOWN_LENGTH * speed * 5 / 6) {
            playIfNotNull("gos");
            go.setVisible(true);
            ModifierRegistry.setPaused(go, false);
        }

        if (timepassed >= COUNTDOWN_LENGTH * speed
                && timepassed - dt < COUNTDOWN_LENGTH * speed) {
            scene = null;
            GlobalManager.getInstance().getGameScene().countdownAnimator = null;
            ready.removeSelf();
            go.removeSelf();
            count1.removeSelf();
            count2.removeSelf();
            count3.removeSelf();
        }
    }

}
