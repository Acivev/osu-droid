package ru.nsu.ccfit.zuev.osu.game.cursor.main;

import androidx.annotation.NonNull;

import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.rian.andengine.modifier.ModifierType;
import com.rian.andengine.modifier.OnModifierFinished;
import com.rian.andengine.modifier.UniversalModifierSequence;
import com.rian.andengine.modifier.VerktexModifiers;

import java.util.function.Consumer;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.game.ISliderListener;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

public class CursorSprite extends UISprite implements ISliderListener {
    public final float baseSize = Config.getCursorSize() * 2;
    private final float clickAnimationTime = 0.25f;

    private final OnModifierFinished rotationModifierFinished = e -> {
        setRotationZ(0);
        startRotationModifierLoop();
    };

    // The AndEngine onLoadComplete ran on the first update after an attach.
    private boolean isLoaded = false;

    private final Consumer<UniversalModifierSequence> clickSequence = sequence ->
        sequence.scaleTo(baseSize * 1.25f, clickAnimationTime)
                .then()
                .scaleTo(baseSize, clickAnimationTime);

    public CursorSprite() {
        super();

        setAnchor(Anchor.Center);
        setOrigin(OsuSkin.get().isCursorCentre() ? Anchor.Center : Anchor.TopLeft);
        // reco1l scaled and rotated around the origin.
        setScaleOrigin(getOrigin());
        setRotationOrigin(getOrigin());
        setScale(baseSize);
        setTextureRegion(SkinTextures.getWithBeatmapSkin("cursor"));
    }

    @Override
    protected void onUpdate(@NonNull TickingClock clock) {
        if (!isLoaded) {
            isLoaded = true;
            onLoadComplete();
        }

        super.onUpdate(clock);
    }

    @Override
    public void onDetached() {
        isLoaded = false;
        super.onDetached();
    }

    private void onLoadComplete() {
        startRotationModifierLoop();
    }

    public void handleClick() {
        if (!OsuSkin.get().isCursorExpand())
            return;

        VerktexModifiers.clearModifiers(this, ModifierType.ScaleXY);
        setScale(baseSize);
        VerktexModifiers.beginModifierSequence(this, clickSequence);
    }

    public void update(float pSecondsElapsed) {
        if (getScaleX() > 2f) {
            setScale(Math.max(baseSize, this.getScaleX() - (baseSize * 0.75f) * pSecondsElapsed));
        }
    }

    @Override
    public void onSliderStart() {

    }

    @Override
    public void onSliderTracking() {
        if (!OsuSkin.get().isCursorExpand())
            return;

        VerktexModifiers.clearModifiers(this, ModifierType.ScaleXY);
        setScale(baseSize);
        VerktexModifiers.scaleTo(this, baseSize * 1.25f, clickAnimationTime);
    }

    @Override
    public void onSliderEnd() {
        if (!OsuSkin.get().isCursorExpand())
            return;

        VerktexModifiers.clearModifiers(this, ModifierType.ScaleXY);
        setScale(baseSize * 1.25f);
        VerktexModifiers.scaleTo(this, baseSize, clickAnimationTime);
    }

    private void startRotationModifierLoop() {
        if (OsuSkin.get().isRotateCursor()) {
            VerktexModifiers.clearModifiers(this, ModifierType.Rotation);
            VerktexModifiers.rotateTo(this, 360, 14).after(rotationModifierFinished);
        }
    }
}