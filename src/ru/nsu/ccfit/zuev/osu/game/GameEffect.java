package ru.nsu.ccfit.zuev.osu.game;

import android.graphics.PointF;


import com.osudroid.utils.Execution;
import com.osudroid.ui.v2.hud.GameplayLayer;
import com.osudroid.ui.verktex.ColorsKt;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.graphics.BlendMode;
import com.reco1l.verktex.ui.UIAnimatedSprite;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.framework.Color4;
import com.rian.andengine.modifier.UniversalModifierSequence;
import com.rian.andengine.modifier.VerktexModifiers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

public class GameEffect extends GameObject {
    private static final HashSet<String> animationEffects = new HashSet<>(Arrays.asList(
            "hit0", "hit50", "hit100", "hit100k", "hit300", "hit300k", "hit300g"
    ));

    UISprite hit;
    String texname;

    private float duration;

    public GameEffect(final String texname) {
        this.texname = texname;

        if (isAnimationEffect(texname) && ResourceManager.getInstance().isTextureLoaded(texname + "-0")) {
            var hit = new UIAnimatedSprite(GameplayLayer.skinAnimationFrames(texname), OsuSkin.get().getAnimationFramerate());
            hit.setLoop(false);
            this.hit = hit;
        } else {
            hit = new UISprite();
            hit.setTextureRegion(SkinTextures.getWithBeatmapSkin(texname));
        }
    }

    private static boolean isAnimationEffect(String textureName) {
        return animationEffects.contains(textureName);
    }

    public void setColor(@Nullable Color4 color) {
        if (color == null) {
            hit.setColor(new com.reco1l.verktex.data.Color4(1f, 1f, 1f, hit.getAlpha()));
        } else {
            hit.setColor(ColorsKt.toVerktex(color));
        }
    }

    @SafeVarargs
    public final void init(final UIContainer scene, final PointF pos, final float scale,
                           final Consumer<UniversalModifierSequence>... sequenceConsumers) {
        if (hit instanceof UIAnimatedSprite animatedHit) {
            // The AndEngine reset(): the animation restarts, the entity is visible, unrotated, unscaled and white.
            animatedHit.stop();
            animatedHit.play();
            animatedHit.setVisible(true);
            animatedHit.setRotationZ(0);
            animatedHit.setScale(1);
            animatedHit.setColor(new com.reco1l.verktex.data.Color4(1f, 1f, 1f, 1f));
        }
        hit.setX(DimensionKt.getPx(pos.x));
        hit.setY(DimensionKt.getPx(pos.y));
        hit.setOrigin(Anchor.Center);
        hit.setScale(scale);
        hit.setAlpha(1);
        hit.removeSelf();
        hit.setBlendMode(BlendMode.Alpha.INSTANCE);
        scene.plusAssign(hit);

        duration = 0;

        for (int i = 0; i < sequenceConsumers.length; ++i) {
            var consumer = sequenceConsumers[i];

            VerktexModifiers.beginModifierSequence(hit, sequence -> {
                consumer.accept(sequence);
                duration = Math.max(duration, sequence.getDuration());
            });
        }

        VerktexModifiers.beginDelayedSequence(hit, duration, sequence -> sequence.after(e -> Execution.updateThread(() -> {
            hit.removeSelf();
            VerktexModifiers.clearModifiers(hit);
            GameObjectPool.getInstance().putEffect(GameEffect.this);
        })));
    }

    public void setBlendMode(BlendMode blendMode) {
        hit.setBlendMode(blendMode);
    }

    public String getTexname() {
        return texname;
    }

    @Override
    public void update(final float dt) {
    }

}
