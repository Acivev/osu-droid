package ru.nsu.ccfit.zuev.osu.game.cursor.flashlight;

import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.ui.UISprite;

public class FlashlightAreaSizedSprite extends UISprite {
    public static final float BASE_SIZE = 6f;

    public FlashlightAreaSizedSprite(GraphicsTexture.Region pTextureRegion) {
        super();
        setOrigin(Anchor.Center);
        setScale(BASE_SIZE);
        setTextureRegion(pTextureRegion);
    }
}
