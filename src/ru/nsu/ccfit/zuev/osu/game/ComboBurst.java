package ru.nsu.ccfit.zuev.osu.game;

import com.edlplan.framework.easing.Easing;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.rian.andengine.modifier.ModifierRegistry;
import com.rian.andengine.modifier.ModifierTicker;
import com.rian.andengine.modifier.VerktexModifiers;

import java.util.ArrayList;
import java.util.List;

import ru.nsu.ccfit.zuev.audio.BassSoundProvider;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.ResourceManager;

/**
 * Created by dgsrz on 15/11/1.
 */
public class ComboBurst {

    private final List<UISprite> comboBursts = new ArrayList<>();
    private final List<BassSoundProvider> comboBurstVocals = new ArrayList<>();

    private final float rightX;
    private final float bottomY;

    private int nextKeyComboNum = 0;
    private float fromX = 0;
    private int nextShowId = 0;
    private int nextSoundId = 0;

    // The sprites' modifiers run on the frame ticker, the elapsed frame times the AndEngine modifiers were given.
    public ComboBurst(float rightX, float bottomY, ModifierTicker frameTicker) {
        this.rightX = rightX;
        this.bottomY = bottomY;
        breakCombo();

        GraphicsTexture.Region globalTex = SkinTextures.getWithBeatmapSkin("comboburst");
        if (globalTex != null) {
            UISprite sprite = new UISprite(globalTex);
            sprite.setAlpha(0f);
            ModifierRegistry.bindTicker(sprite, frameTicker);
            ModifierRegistry.setPaused(sprite, true);
            comboBursts.add(sprite);
        }
        BassSoundProvider sound = ResourceManager.getInstance().getSound("comboburst");
        if (sound != null) {
            comboBurstVocals.add(sound);
        }
        for (int i = 0; i < 10; i++) {
            GraphicsTexture.Region tex = SkinTextures.getWithBeatmapSkin("comboburst-" + i);
            if (tex != null) {
                UISprite sprite = new UISprite(tex);
                sprite.setAlpha(0f);
                ModifierRegistry.bindTicker(sprite, frameTicker);
                ModifierRegistry.setPaused(sprite, true);
                comboBursts.add(sprite);
            }
            sound = ResourceManager.getInstance().getSound("comboburst-" + i);
            if (sound != null) {
                comboBurstVocals.add(sound);
            }
        }
    }

    public void checkAndShow(int currentCombo) {
        if (Config.isComboburst() && currentCombo >= nextKeyComboNum) {
            if (comboBurstVocals.size() > 0) {
                comboBurstVocals.get(nextSoundId).play(0.8f);
            }
            if (comboBursts.size() > 0) {
                UISprite sprite = comboBursts.get(nextShowId);
                float toX;
                if (fromX > 0) {
                    toX = fromX - sprite.getTextureRegion().getWidth();
                } else {
                    fromX = -sprite.getTextureRegion().getWidth();
                    toX = 0;
                }
                ModifierRegistry.setPaused(sprite, false);
                sprite.setX(DimensionKt.getPx(fromX));
                sprite.setY(DimensionKt.getPx(bottomY - sprite.getTextureRegion().getHeight()));
                VerktexModifiers.beginModifierSequence(sprite, sequence -> sequence
                        .moveToX(toX, 0.5f, Easing.OutSine)
                        .fadeInFromZero(0.5f)
                        .then(1.0f)
                        .moveToX(fromX, 0.5f, Easing.OutSine)
                        .fadeOutFromOne(0.5f)
                        .after(e -> {
                            e.setAlpha(0f);
                            ModifierRegistry.setPaused(sprite, true);
                        }));
            }

            if (comboBursts.size() > 0) {
                int length = comboBursts.size();
                nextShowId = (nextShowId + 1) % length;
            }
            if (comboBurstVocals.size() > 0) {
                int length = comboBurstVocals.size();
                nextSoundId = (nextSoundId + 1) % length;
            }
            if (nextKeyComboNum == 30) {
                nextKeyComboNum = 60;
                fromX = rightX;
            } else if (nextKeyComboNum == 60) {
                nextKeyComboNum = 100;
                fromX = -1;
            } else {
                nextKeyComboNum += 100;
                int mod = nextKeyComboNum / 100;
                if (mod % 2 == 0) {
                    fromX = rightX;
                } else {
                    fromX = -1;
                }
            }
        }
    }

    public void breakCombo() {
        fromX = 0;
        nextKeyComboNum = 30;
    }

    public void attachAll(UIContainer scene) {
        for (final UISprite sprite : comboBursts) {
            scene.plusAssign(sprite);
        }
    }

    public void detachAll() {
        for (final UISprite sprite : comboBursts) {
            sprite.removeSelf();
        }
    }
}
