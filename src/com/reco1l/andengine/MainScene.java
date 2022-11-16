package com.reco1l.andengine;

// Created by Reco1l on 18/9/22 19:28

import com.reco1l.Game;
import com.reco1l.UI;
import com.reco1l.enums.MusicOption;
import com.reco1l.enums.Screens;
import com.reco1l.game.TimingWrapper;
import com.reco1l.ui.custom.Dialog;
import com.reco1l.ui.data.DialogTable;

import org.anddev.andengine.entity.modifier.AlphaModifier;
import org.anddev.andengine.entity.primitive.Rectangle;

import ru.nsu.ccfit.zuev.audio.Status;
import ru.nsu.ccfit.zuev.osu.BeatmapInfo;
import ru.nsu.ccfit.zuev.osu.Config;

public class MainScene extends BaseScene {


    //--------------------------------------------------------------------------------------------//

    @Override
    public Screens getIdentifier() {
        return Screens.MAIN;
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    protected void onCreate() {
        setContinuousPlay(true);
        setTimingWrapper(true);

        timingWrapper.setObserver(new TimingWrapper.Observer() {
            @Override
            public void onKiaiStart() {
                UI.mainMenu.setLogoKiai(true);
                UI.background.setKiai(true);
            }

            @Override
            public void onKiaiEnd() {
                UI.mainMenu.setLogoKiai(false);
                UI.background.setKiai(false);
            }

            @Override
            public void onBeatUpdate(float BPMLength, int beat) {
                UI.mainMenu.onBeatUpdate(BPMLength);
                UI.background.onBeatUpdate(BPMLength, beat);
            }
        });

        Game.library.loadLibraryCache(Game.mActivity, false);

        Config.loadOnlineConfig(Game.mActivity);
        Game.online.Init(Game.mActivity);
        Game.onlineScoring.login();

        setTouchAreaBindingEnabled(true);
    }

    @Override
    public void onMusicControlRequest(MusicOption option, Status current) {
        UI.musicPlayer.currentOption = option;
        super.onMusicControlRequest(option, current);
    }

    @Override
    protected void onSceneUpdate(float secondsElapsed) {
        if (Game.songService != null) {
            if (Game.songService.getStatus() == Status.PLAYING) {
                //beatMarker.update();
            }
        }
    }

    @Override
    public void onMusicChange(BeatmapInfo beatmap) {
        super.onMusicChange(beatmap);

        Game.mActivity.runOnUiThread(() -> {
            if (UI.topBar.musicButton != null) {
                UI.topBar.musicButton.changeMusic(beatmap);
            }
            UI.musicPlayer.changeMusic(beatmap);
        });
    }

    @Override
    public boolean onBackPress() {
        new Dialog(DialogTable.exit()).show();
        return true;
    }

    //--------------------------------------------------------------------------------------------//

    public void loadMusic() {
        Game.library.shuffleLibrary();
        Game.musicManager.beatmap = Game.library.getBeatmap();

        if (Game.musicManager.beatmap != null && Game.library.getSizeOfBeatmaps() > 0) {
            Game.musicManager.play();
        }
        UI.debugOverlay.show();
    }

    public void onExit() {
        Rectangle dim = new Rectangle(0, 0, screenWidth, screenHeight);
        dim.setColor(0, 0, 0, 0f);
        attachChild(dim, 2);

        UI.mainMenu.playExitAnim();
        Game.resources.getSound("seeya").play();
        dim.registerEntityModifier(new AlphaModifier(3.0f, 0, 1));
    }
}