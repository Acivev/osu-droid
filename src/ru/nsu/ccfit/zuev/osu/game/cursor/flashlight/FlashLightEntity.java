package ru.nsu.ccfit.zuev.osu.game.cursor.flashlight;

import androidx.annotation.NonNull;

import com.edlplan.framework.easing.Easing;
import com.edlplan.framework.math.FMath;
import com.osudroid.mods.ModFlashlight;

import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.framework.Interpolation;

import ru.nsu.ccfit.zuev.osu.Config;


public class FlashLightEntity extends UIContainer {
    private final MainFlashLightSprite mainSprite;
    private final FlashLightDimLayerSprite dimLayer;
    private boolean isTrackingSliders = false;

    private final float followDelay;
    private float nextPX;
    private float nextPY;

    public FlashLightEntity(final ModFlashlight flashlight) {
        super();

        setX(DimensionKt.getPx(Config.getRES_WIDTH() / 2f));
        setY(DimensionKt.getPx(Config.getRES_HEIGHT() / 2f));
        followDelay = flashlight.getFollowDelay();
        mainSprite = new MainFlashLightSprite(flashlight.getSizeMultiplier(), flashlight.isComboBasedSize());
        dimLayer = new FlashLightDimLayerSprite();

        plusAssign(mainSprite);
        plusAssign(dimLayer);
    }

    public void onBreak(boolean isBreak) {
        mainSprite.updateBreak(isBreak);
    }

    public void onMouseMove(float pX, float pY) {
        nextPX = FMath.clamp(pX, 0, Config.getRES_WIDTH());
        nextPY = FMath.clamp(pY, 0, Config.getRES_HEIGHT());
    }

    public void onTrackingSliders(boolean isTrackingSliders) {
        this.isTrackingSliders = isTrackingSliders;
    }

    public void onUpdate(int combo) {
        dimLayer.onTrackingSliders(isTrackingSliders);
        mainSprite.onUpdate(combo);
    }

    @Override
    protected void onUpdate(@NonNull TickingClock clock) {
        super.onUpdate(clock);

        // The gameplay delta, as the AndEngine scene's clock gave it.
        float deltaTimeSec = clock.getElapsedTickTime();

        if (followDelay == 0) {
            setX(DimensionKt.getPx(nextPX));
            setY(DimensionKt.getPx(nextPY));
            return;
        }

        float x = getInterpolatedPosition(deltaTimeSec, getX().resolve(0f), nextPX);
        float y = getInterpolatedPosition(deltaTimeSec, getY().resolve(0f), nextPY);

        setX(DimensionKt.getPx(x));
        setY(DimensionKt.getPx(y));
    }

    private float getInterpolatedPosition(float deltaTime, float current, float next) {
        return Interpolation.floatAt(Math.min(deltaTime, followDelay), current, next, 0, followDelay, Easing.Out);
    }
}

