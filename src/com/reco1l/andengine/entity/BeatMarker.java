package com.reco1l.andengine.entity;

// Created by Reco1l on 11/10/22 19:45

import com.reco1l.Game;
import com.reco1l.andengine.IAttachableEntity;
import com.reco1l.utils.Animation;
import com.reco1l.utils.Animation.ValueAnimationListener;

import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.sprite.Sprite;
import org.anddev.andengine.opengl.texture.region.TextureRegion;

public class BeatMarker implements IAttachableEntity {

    private Sprite spriteLeft, spriteRight;

    private boolean isKiai = false;
    private float level;
    private int sideCursor = 0;
    private int beat = 0;

    private ValueAnimationListener<Float> listener;

    //--------------------------------------------------------------------------------------------//

    @Override
    public void draw(Scene scene, int index) {
        Scene entity = new Scene();
        entity.setBackgroundEnabled(false);

        TextureRegion texture = Game.resources.getTexture("border-gradient");
        texture.setHeight(screenHeight);

        spriteLeft = new Sprite(0, 0, texture);

        spriteRight = new Sprite(screenWidth - texture.getWidth(), 0, texture);
        spriteRight.setRotation(180);

        entity.attachChild(spriteLeft);
        entity.attachChild(spriteRight);

        scene.attachChild(entity, index);

        listener = val -> {
            spriteRight.setAlpha(val);
            spriteLeft.setAlpha(val);
        };
    }

    public void setKiai(boolean bool) {
        this.isKiai = bool;
    }

    @Override
    public void update() {
        if (Game.songService != null) {
            level = Game.songService.getLevel() * 1.2f;
        }
    }

    public void onBpmUpdate(float bpm) {
        long upTime = (long) (bpm * 0.07f);
        long downTime = (long) (bpm * 0.9f);

        if (!isKiai) {
            downTime *= 1.3f;
        }

        final float fLevel = level;
        Animation fadeUp = new Animation().ofFloat(0f, fLevel).runOnUpdate(listener);
        Animation fadeDown = new Animation().ofFloat(fLevel, 0f).runOnUpdate(listener);

        fadeUp.runOnStart(() -> {
            if (isKiai) {
                sideCursor = sideCursor == 0 ? 1 : 0;

                spriteLeft.setVisible(sideCursor == 0);
                spriteRight.setVisible(sideCursor == 1);
            } else {
                spriteRight.setVisible(beat == 0);
                spriteLeft.setVisible(beat == 0);
            }
        });

        fadeUp.runOnEnd(() -> beat++).play(upTime);
        fadeDown.delay(upTime).play(downTime);

        if (beat > 3) {
            beat = 0;
        }
    }
}
