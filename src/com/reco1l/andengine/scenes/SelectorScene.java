package com.reco1l.andengine.scenes;

// Created by Reco1l on 26/9/22 17:40

import com.edlplan.ext.EdExtensionHelper;
import com.edlplan.replay.OdrDatabase;
import com.reco1l.Game;
import com.reco1l.UI;
import com.reco1l.enums.Screens;
import com.reco1l.andengine.BaseScene;
import com.reco1l.game.TimingWrapper;
import com.reco1l.utils.Animation;
import com.reco1l.utils.Resources;
import com.reco1l.view.BarButton;

import org.anddev.andengine.entity.scene.Scene;

import ru.nsu.ccfit.zuev.osu.BeatmapInfo;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.TrackInfo;
import ru.nsu.ccfit.zuev.osuplus.R;

public class SelectorScene extends BaseScene {

    //--------------------------------------------------------------------------------------------//

    @Override
    public Screens getIdentifier() {
        return Screens.Selector;
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    protected void onCreate() {
        setTimingWrapper(true);
        setContinuousPlay(false);
        setBackgroundAutoChange(false);

        timingWrapper.setObserver(new TimingWrapper.Observer() {
            @Override
            public void onKiaiStart() {
                UI.beatmapCarrousel.setKiai(true);
                UI.background.setKiai(true);
            }

            @Override
            public void onKiaiEnd() {
                UI.beatmapCarrousel.setKiai(false);
                UI.background.setKiai(false);
            }

            @Override
            public void onBeatUpdate(float BPMLength, int beat) {
                UI.beatmapCarrousel.onBeatUpdate(BPMLength);
                UI.background.onBeatUpdate(BPMLength, beat);
            }
        });

        bindDataBaseChangedListener();
        setTouchAreaBindingEnabled(true);
        createTopBarButtons();
    }

    private void createTopBarButtons() {
        BarButton mods = new BarButton(Game.activity);

        mods.setAsToggle(true);
        mods.setIcon(Resources.drw(R.drawable.v_tune));

        BarButton search = new BarButton(Game.activity);

        search.setAsToggle(true);
        search.setIcon(Resources.drw(R.drawable.v_search));

        BarButton random = new BarButton(Game.activity);

        random.setIcon(Resources.drw(R.drawable.v_random));

        UI.topBar.addButton(getIdentifier(), mods);
        UI.topBar.addButton(getIdentifier(), search);
        UI.topBar.addButton(getIdentifier(), random);
    }

    @Override
    protected void onSceneUpdate(float secondsElapsed) {

    }

    @Override
    public void show() {
        super.show();

        setTouchAreaBindingEnabled(false);
        bindDataBaseChangedListener();
        Game.gameScene.setOldScene(this);
    }

    //--------------------------------------------------------------------------------------------//

    public void onTrackSelect(TrackInfo track, boolean isAlreadySelected) {
        Game.global.setSelectedTrack(track);

        if (isAlreadySelected) {
            Game.resources.getSound("menuhit").play();
            Game.musicManager.stop();
            Game.global.getGameScene().startGame(track, null);
            return;
        }

        // EdExtensionHelper.onSelectTrack(track);

        UI.beatmapPanel.updateProperties(track);
        UI.beatmapPanel.updateScoreboard();
        UI.background.changeFrom(track.getBackground());
    }

    public void playMusic(BeatmapInfo beatmap) {
        Game.musicManager.change(beatmap);
        Game.songService.setVolume(0);

        if (beatmap.getPreviewTime() >= 0) {
            Game.songService.seekTo(beatmap.getPreviewTime());
        } else {
            Game.songService.seekTo(Game.songService.getLength() / 2);
        }

        Animation.ofFloat(0, Config.getBgmVolume())
                .runOnUpdate(value -> Game.songService.setVolume((float) value))
                .play(400);
    }

    //--------------------------------------------------------------------------------------------//

    public void loadScore(int id, String player) {
        boolean isOnline = UI.beatmapPanel.isOnlineBoard;

        Game.summaryScene.loadFromBoard(UI.beatmapCarrousel.selectedTrack, id, isOnline, player);
    }

    //--------------------------------------------------------------------------------------------//


    @Override
    public void onMusicChange(BeatmapInfo beatmap) {
        if (Game.engine.getCurrentScene() != this) {
            if (beatmap != null) {
                UI.beatmapCarrousel.selectedTrack = beatmap.getTrack(0);
            }
        }
        super.onMusicChange(beatmap);
    }

    @Override
    public void onMusicEnd() {
        playMusic(Game.library.getBeatmap());
    }

    @Override
    public boolean onBackPress() {
        Game.engine.setScene(Game.mainScene);
        return true;
    }

    @Override
    public void onSceneChange(Scene oldScene, Scene newScene) {
        if (newScene == this) {
            UI.background.setBlur(true);
        } else {
            UI.beatmapCarrousel.selectedTrack = null;
        }
    }


    //--------------------------------------------------------------------------------------------//

    public void bindDataBaseChangedListener() {
        OdrDatabase.get().setOnDatabaseChangedListener(UI.beatmapPanel::updateScoreboard);
    }

    public void unbindDataBaseChangedListener() {
        OdrDatabase.get().setOnDatabaseChangedListener(null);
    }
}