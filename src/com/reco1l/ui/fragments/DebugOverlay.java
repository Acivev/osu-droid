package com.reco1l.ui.fragments;

import android.widget.TextView;

import com.reco1l.Game;
import com.reco1l.andengine.BaseScene;
import com.reco1l.ui.platform.UIFragment;

import java.text.DecimalFormat;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osuplus.R;

public class DebugOverlay extends UIFragment {

    public static DebugOverlay instance;

    public float logo_scale = 0;

    private TextView text;
    private final DecimalFormat df = new DecimalFormat("#.###");

    //--------------------------------------------------------------------------------------------//

    @Override
    protected int getLayout() {
        return R.layout.debug_overlay;
    }

    @Override
    protected String getPrefix() {
        return "do";
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    protected void onLoad() {
        text = find("text");
    }

    @Override
    protected void onUpdate(float secondsElapsed) {
        if (text == null)
            return;

        short beat = 0;

        if(Game.engine.getScene() instanceof BaseScene) {
            BaseScene scene = (BaseScene) Game.engine.getScene();

            if (scene.timingWrapper != null) {
                beat = (short) scene.timingWrapper.beat;
            }
        }

        String string =
                Game.activity.getRenderer() + "\n" +
                "logo_scale: " + df.format(logo_scale) + "\n" +
                "current_beat: " + beat;

        text.setText(string);
    }
}