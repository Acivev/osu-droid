package ru.nsu.ccfit.zuev.osu.scoring;

import android.util.Log;

import com.edlplan.framework.utils.functionality.SmartIterator;
import com.osudroid.beatmaps.BeatmapCache;
import com.osudroid.data.DatabaseManager;
import com.osudroid.multiplayer.Multiplayer;
import com.osudroid.ui.verktex.IPressable;
import com.osudroid.ui.verktex.ISkinnable;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.ModIcon;
import com.osudroid.ui.verktex.ScoreNumber;
import com.osudroid.ui.verktex.SharedBackground;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.ui.v2.multi.LobbyScene;
import com.osudroid.utils.Execution;
import com.osudroid.verktex.bridge.EngineRouter;
import com.osudroid.verktex.bridge.RoutedScene;
import com.osudroid.verktex.bridge.VerktexBridge;
import com.reco1l.verktex.Verktex;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.Dimension;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.LayoutConstraints;
import com.reco1l.verktex.data.Vec4;
import com.reco1l.verktex.graphics.GraphicsRenderer;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.KeyboardEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.math.Easing;
import com.reco1l.verktex.math.FloatAnimation;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UIComponent;
import com.reco1l.verktex.ui.UIScene;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.verktex.ui.shape.UIBox;
import com.reco1l.verktex.ui.text.UIText;
import com.reco1l.osu.ui.entity.StatisticSelector;

import org.andengine.engine.Engine;
import org.andengine.util.debug.Debug;

import com.osudroid.GameMode;
import com.osudroid.beatmaps.Beatmap;
import com.osudroid.difficulty.BeatmapDifficultyCalculator;
import com.osudroid.difficulty.attributes.DifficultyAttributes;
import com.osudroid.difficulty.attributes.DroidDifficultyAttributes;
import com.osudroid.difficulty.attributes.PerformanceAttributes;
import com.osudroid.difficulty.attributes.StandardDifficultyAttributes;
import com.osudroid.mods.ModAutoplay;
import com.osudroid.mods.ModCustomSpeed;
import com.osudroid.mods.ModDifficultyAdjust;
import com.osudroid.mods.ModFlashlight;
import com.osudroid.mods.ModNightCore;
import com.osudroid.mods.ModOldNightCore;
import com.osudroid.ui.SendingPanel;

import org.apache.commons.io.FilenameUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;

import ru.nsu.ccfit.zuev.audio.serviceAudio.SongService;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.DifficultyAlgorithm;
import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import com.osudroid.data.BeatmapInfo;

import ru.nsu.ccfit.zuev.osu.ToastLogger;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.game.GameScene;
import ru.nsu.ccfit.zuev.osu.menu.SongMenu;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osu.online.OnlineScoring;
import ru.nsu.ccfit.zuev.osuplus.BuildConfig;

public class ScoringScene extends UIScene implements RoutedScene, ISkinnable {
    private final Engine engine;
    private final GameScene game;
    private final SongMenu menu;
    private SongService songService;
    private StatisticV2 replayStat;
    private int replayID = -1;
    public BeatmapInfo beatmapInfo;

    // Multiplayer
    public StatisticV2 currentStatistic;

    private StatisticSelector selector;

    // Each pointer's events go to the child that consumed its press, until it is lifted.
    private final HashMap<Integer, UIComponent> touchAreaBindings = new HashMap<>();

    public ScoringScene(final Engine pEngine, final GameScene pGame,
                        final SongMenu pMenu) {
        engine = pEngine;
        game = pGame;
        menu = pMenu;
    }

    public void load(final StatisticV2 stat, final BeatmapInfo beatmap,
                     final SongService player, final String replayPath, final String mapMD5,
                     final BeatmapInfo beatmapToReplay) {
        VerktexBridge.checkUpdateThread();

        Verktex.INSTANCE.withTreeLock(() -> {
        clearChildren();
        touchAreaBindings.clear();
        final float vu = LegacyUnits.getScale();
        songService = player;
        currentStatistic = stat;
        if (replayPath != null && beatmap == null) {
            replayStat = stat;
        }
        GraphicsTexture.Region tex = SharedBackground.region();
        if (tex == null) {
            tex = SkinTextures.get("menu-background");
        }
        float height = tex.getHeight();
        height *= Config.getRES_WIDTH() / (float) tex.getWidth();
        final LegacySprite bg = new LegacySprite(0, (Config.getRES_HEIGHT() - height) / 2,
                Config.getRES_WIDTH(), height, tex, vu);
        setBackground(bg);

        final LegacyRectangle bgTopRect = new LegacyRectangle(0, 0, Config.getRES_WIDTH(), Utils.toRes(100), vu);
        bgTopRect.setColor(0, 0, 0, 0.8f);
        plusAssign(bgTopRect);

        beatmapInfo = beatmapToReplay;
        if (beatmapToReplay == null && beatmap != null) {
            beatmapInfo = beatmap;
        }

        float paddingButtom = Multiplayer.isConnected()
                ? Multiplayer.roomScene.getChat().getButtonHeight()
                : 0f;

        // Its children are in virtual units, its scale makes them window pixels.
        UIContainer scaledContainer = new UIContainer() {{
            setWidth(DimensionKt.getPx(Config.getRES_WIDTH()));
            setHeight(DimensionKt.getPx(Config.getRES_HEIGHT()));
            setY(DimensionKt.getPx(100f * vu));
            setScaleOrigin(Anchor.TopLeft);
            setScale((Config.getRES_HEIGHT() - paddingButtom) / Config.getRES_HEIGHT() * vu);
        }};
        plusAssign(scaledContainer);

        final int x = 0, y = 0;
        final GraphicsTexture.Region panelr = SkinTextures.get(
                "ranking-panel");
        final LegacySprite panel = new LegacySprite(x, y, Utils.toRes(panelr.getWidth() * 0.9f),
                Utils.toRes(panelr.getHeight() * 0.9f), "ranking-panel", 1f);
        scaledContainer.plusAssign(panel);

        final GraphicsTexture.Region hit300sr = SkinTextures.get("hit300");
        final LegacySprite hit300s = new LegacySprite(Utils.toRes(10), Utils.toRes(130),
                Utils.toRes(hit300sr.getWidth()), Utils.toRes(hit300sr.getHeight()), "hit300", 1f);
        hit300s.setPosition(Utils.toRes(70 - hit300s.getLegacyWidth() / 2 + x), Utils.toRes(130 - hit300s.getLegacyHeight() / 2 + y));
        scaledContainer.plusAssign(hit300s);

        final GraphicsTexture.Region hit100sr = SkinTextures.get("hit100");
        final LegacySprite hit100s = new LegacySprite(Utils.toRes(10), Utils.toRes(130 + 92),
                Utils.toRes(hit100sr.getWidth()), Utils.toRes(hit100sr.getHeight()), "hit100", 1f);
        hit100s.setPosition(Utils.toRes(70 - hit100s.getLegacyWidth() / 2 + x), Utils.toRes(130 + 92 - hit100s.getLegacyHeight() / 2 + y));
        scaledContainer.plusAssign(hit100s);

        final GraphicsTexture.Region hit50sr = SkinTextures.get("hit50");
        final LegacySprite hit50s = new LegacySprite(0, Utils.toRes(120 + 92 * 2),
                Utils.toRes(hit50sr.getWidth()), Utils.toRes(hit50sr.getHeight()), "hit50", 1f);
        hit50s.setPosition(Utils.toRes(70 - hit50s.getLegacyWidth() / 2 + x), Utils.toRes(130 + 92 * 2 - hit50s.getLegacyHeight() / 2 + y));
        scaledContainer.plusAssign(hit50s);

        final GraphicsTexture.Region hit300ksr = SkinTextures.get("hit300g");
        final LegacySprite hit300ks = new LegacySprite(Utils.toRes(300), Utils.toRes(100),
                Utils.toRes(hit300ksr.getWidth()), Utils.toRes(hit300ksr.getHeight()), "hit300g", 1f);
        hit300ks.setPosition(Utils.toRes(340 - hit300ks.getLegacyWidth() / 2 + x), Utils.toRes(130 - hit300ks.getLegacyHeight() / 2 + y));
        scaledContainer.plusAssign(hit300ks);

        final GraphicsTexture.Region hit100ksr = SkinTextures.get("hit100k");
        final LegacySprite hit100ks = new LegacySprite(Utils.toRes(300), Utils.toRes(120 + 92),
                Utils.toRes(hit100ksr.getWidth()), Utils.toRes(hit100ksr.getHeight()), "hit100k", 1f);
        hit100ks.setPosition(Utils.toRes(340 - hit100ks.getLegacyWidth() / 2 + x), Utils.toRes(130 + 92 - hit100ks.getLegacyHeight() / 2 + y));
        scaledContainer.plusAssign(hit100ks);

        final GraphicsTexture.Region hit0sr = SkinTextures.get("hit0");
        final LegacySprite hit0s = new LegacySprite(Utils.toRes(300), Utils.toRes(120 + 92 * 2),
                Utils.toRes(hit0sr.getWidth()), Utils.toRes(hit0sr.getHeight()), "hit0", 1f);
        hit0s.setPosition(Utils.toRes(340 - hit0s.getLegacyWidth() / 2 + x), Utils.toRes(130 + 92 * 2 - hit0s.getLegacyHeight() / 2 + y));
        scaledContainer.plusAssign(hit0s);

        final LegacySprite rankingText = new LegacySprite(Utils.toRes(580), 0,
                "ranking-title", vu);
        rankingText.setPosition((float) (Config.getRES_WIDTH() * 5) / 6 - rankingText.getLegacyWidth() / 2, 0);
        plusAssign(rankingText);

        StringBuilder scoreStr = new StringBuilder(String.valueOf(stat.getTotalScoreWithMultiplier()));
        while (scoreStr.length() < 8) {
            scoreStr.insert(0, "0");
        }
        final ScoreNumber scoreNum = new ScoreNumber(Utils.toRes(220 + x),
                Utils.toRes(18 + y), scoreStr.toString(), 1, false);
        scaledContainer.plusAssign(scoreNum);

        final ScoreNumber hit300num = new ScoreNumber(Utils.toRes(138 + x),
                Utils.toRes(110 + y), stat.getHit300() + "x", 1,
                false);
        scaledContainer.plusAssign(hit300num);
        final ScoreNumber hit100num = new ScoreNumber(Utils.toRes(138 + x),
                Utils.toRes(110 + 85 + y), stat.getHit100() + "x",
                1, false);
        scaledContainer.plusAssign(hit100num);
        final ScoreNumber hit50num = new ScoreNumber(Utils.toRes(138 + x),
                Utils.toRes(110 + 85 * 2 + y), stat.getHit50() + "x",
                1, false);
        scaledContainer.plusAssign(hit50num);

        final ScoreNumber hit300knum = new ScoreNumber(Utils.toRes(400 + x),
                Utils.toRes(110 + y), stat.getHit300k() + "x", 1,
                false);
        scaledContainer.plusAssign(hit300knum);
        final ScoreNumber hit100knum = new ScoreNumber(Utils.toRes(400 + x),
                Utils.toRes(110 + 85 + y), stat.getHit100k() + "x",
                1, false);
        scaledContainer.plusAssign(hit100knum);
        final ScoreNumber hit0num = new ScoreNumber(Utils.toRes(400 + x),
                Utils.toRes(110 + 85 * 2 + y), stat.getMisses() + "x",
                1, false);
        scaledContainer.plusAssign(hit0num);

        final LegacySprite maxComboText = new LegacySprite(Utils.toRes(20 + x),
                Utils.toRes(332 + y),
                "ranking-maxcombo", 1f);
        scaledContainer.plusAssign(maxComboText);
        final LegacySprite accText = new LegacySprite(Utils.toRes(260 + x), Utils.toRes(332 + y),
                "ranking-accuracy", 1f);
        scaledContainer.plusAssign(accText);
        final ScoreNumber maxCombo = new ScoreNumber(Utils.toRes(20 + x),
                Utils.toRes(maxComboText.getLegacyY() + 38), stat.getScoreMaxCombo() + "x", 1,
                false);
        scaledContainer.plusAssign(maxCombo);
        final String accStr = String
                .format(Locale.ENGLISH, "%2.2f%%", stat.getAccuracy() * 100);
        final ScoreNumber accuracy = new ScoreNumber(Utils.toRes(260 + x),
                Utils.toRes(accText.getLegacyY() + 38), accStr, 1, false);
        scaledContainer.plusAssign(accuracy);

        final FloatAnimation markModifier = new FloatAnimation(1f, 0f, Easing.Companion.getNone());
        final LegacySprite mark = new LegacySprite(Utils.toRes(610), 0,
                "ranking-" + stat.getMark(), vu) {

            @Override
            protected void onUpdate(final TickingClock clock) {
                // Fades in while shrinking from 2 to 1 around the centre, over 2 seconds.
                final float progress = markModifier.update(clock);
                setAlpha(progress);
                setScaleX(2 - progress);
                setScaleY(2 - progress);
                super.onUpdate(clock);
            }

        };
        if (beatmap != null) {
            mark.setAlpha(0);
            mark.setScale(1.5f);
            markModifier.jumpTo(0f);
            markModifier.animateTo(1f, 2f, Easing.Companion.getNone());
        }
        mark.setPosition((float) (Config.getRES_WIDTH() * 5) / 6 - mark.getLegacyWidth() / 2, 80);
        plusAssign(mark);

        final LegacySprite backBtn = new LegacySprite(Utils.toRes(580), Utils.toRes(490),
                "ranking-back", vu) {


            @Override
            public boolean onInputEvent(final InputEvent event) {
                if (!(event instanceof PointerEvent pSceneTouchEvent)) {
                    return false;
                }
                if (pSceneTouchEvent.getAction() == PointerEvent.Action.Down) {
                    setColor(0.7f, 0.7f, 0.7f);
                    ResourceManager.getInstance().getSound("menuback").play();
                    return true;
                }
                if (pSceneTouchEvent.getAction() == PointerEvent.Action.Up) {
                    back();
                    return true;
                }
                return false;
            }

        };
        backBtn.setPosition(Config.getRES_WIDTH() - backBtn.getLegacyWidth() - 10, Config.getRES_HEIGHT() - backBtn.getLegacyHeight() - 10 - paddingButtom);
        plusAssign(backBtn);

        LegacySprite retryBtn = null;

        if (!Multiplayer.isMultiplayer)
        {
            retryBtn = new LegacySprite(Utils.toRes(580), Utils.toRes(400), "ranking-retry", vu) {

                @Override
                public boolean onInputEvent(final InputEvent event) {
                    if (!(event instanceof PointerEvent pSceneTouchEvent)) {
                        return false;
                    }
                    if (pSceneTouchEvent.getAction() == PointerEvent.Action.Down) {
                        setColor(0.7f, 0.7f, 0.7f);
                        ResourceManager.getInstance().getSound("menuback").play();
                        return true;
                    }
                    if (pSceneTouchEvent.getAction() == PointerEvent.Action.Up) {
                        ResourceManager.getInstance().getSound("applause").stop();
                        menu.show();
                        game.startGame(null, null, null);
                        stopMusic();
                        return true;
                    }
                    return false;
                }

            };
        }

        LegacySprite replayBtn = null;
        var mods = stat.getMod();

        if (!Multiplayer.isMultiplayer && replayPath != null)
        {
            replayBtn = new LegacySprite(Utils.toRes(580), Utils.toRes(400),
                                                "ranking-replay", vu) {

                @Override
                public boolean onInputEvent(final InputEvent event) {
                    if (!(event instanceof PointerEvent pSceneTouchEvent)) {
                        return false;
                    }
                    if (pSceneTouchEvent.getAction() == PointerEvent.Action.Down) {
                        setColor(0.7f, 0.7f, 0.7f);
                        ResourceManager.getInstance().getSound("menuback").play();
                        return true;
                    }
                    if (pSceneTouchEvent.getAction() == PointerEvent.Action.Up) {
                        ResourceManager.getInstance().getSound("applause").stop();
                        SongMenu.stopMusicStatic();
                        menu.show();
                        game.startGame(beatmapToReplay, replayPath, mods);

                        stopMusic();
                        return true;
                    }
                    return false;
                }

            };
        }

        if (stat.isPerfect()) {
            final LegacySprite perfect = new LegacySprite(0, 0, "ranking-perfect", 1f);
            // Bug 1: the legacy accuracy.getHeight() was still 0 here, the ScoreNumber was built on the next tick.
            perfect.setPosition(0, ((Dimension.Fixed) accuracy.getY()).toPixels() + 10);
            scaledContainer.plusAssign(perfect);
        }

        if (beatmap != null && retryBtn != null) {
            retryBtn.setPosition(Config.getRES_WIDTH() - backBtn.getLegacyWidth() - 10, backBtn.getLegacyY() - retryBtn.getLegacyHeight() - 10);
            plusAssign(retryBtn);
        } else if (replayPath != null && replayBtn != null) {
            replayBtn.setPosition(Config.getRES_WIDTH() - backBtn.getLegacyWidth() - 10, backBtn.getLegacyY() - replayBtn.getLegacyHeight() - 10);
            plusAssign(replayBtn);
        }

        var modX = mark.getLegacyX() - 30;
        var modY = mark.getLegacyY() + mark.getLegacyHeight() * 2 / 3;
        for (var mod : mods.values()) {
            if (!mod.isUserPlayable()) {
                continue;
            }

            var modIcon = new ModIcon(mod);
            modIcon.setX(DimensionKt.getPx(modX * vu));
            modIcon.setY(DimensionKt.getPx(modY * vu));
            modIcon.setWidth(DimensionKt.getPx(68 * vu));
            modIcon.setHeight(DimensionKt.getPx(66 * vu));
            modX -= 30;
            plusAssign(modIcon);
        }

        String infoStr = beatmapInfo.getArtistText() + " - " + beatmapInfo.getTitleText() + " [" + beatmapInfo.getVersion() + "]";

        String mapperStr = "Beatmap by " + beatmapInfo.getCreator();
        String playerStr = "Played by " + stat.getPlayerName() + " on " +
                new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(stat.getTime()));
        playerStr += String.format("  %s(%s)", BuildConfig.VERSION_NAME + "-GLES ", BuildConfig.BUILD_TYPE);
        if (mods.contains(ModCustomSpeed.class) ||
            mods.contains(ModDifficultyAdjust.class) ||
            (mods.contains(ModFlashlight.class) && !mods.ofType(ModFlashlight.class).getUsesDefaultSettings())) {

            var customSpeed = mods.ofType(ModCustomSpeed.class);
            var difficultyAdjust = mods.ofType(ModDifficultyAdjust.class);
            var flashlight = mods.ofType(ModFlashlight.class);

            mapperStr += " [";
            if (customSpeed != null && customSpeed.getTrackRateMultiplier() != 1f) {
                mapperStr += String.format(Locale.ENGLISH, "%.2fx,", customSpeed.getTrackRateMultiplier());
            }

            if (difficultyAdjust != null) {
                if (difficultyAdjust.getAr() != null) {
                    mapperStr += String.format(Locale.ENGLISH, "AR%.1f,", difficultyAdjust.getAr());
                }

                if (difficultyAdjust.getOd() != null) {
                    mapperStr += String.format(Locale.ENGLISH, "OD%.1f,", difficultyAdjust.getOd());
                }

                if (difficultyAdjust.getCs() != null) {
                    mapperStr += String.format(Locale.ENGLISH, "CS%.1f,", difficultyAdjust.getCs());
                }

                if (difficultyAdjust.getHp() != null) {
                    mapperStr += String.format(Locale.ENGLISH, "HP%.1f,", difficultyAdjust.getHp());
                }
            }

            if (flashlight != null && !flashlight.getUsesDefaultSettings()) {
                if (flashlight.getFollowDelay() != ModFlashlight.DEFAULT_FOLLOW_DELAY) {
                    mapperStr += String.format(Locale.ENGLISH, "FLD%.2f,", flashlight.getFollowDelay());
                }

                if (flashlight.getSizeMultiplier() != ModFlashlight.DEFAULT_SIZE_MULTIPLIER) {
                    mapperStr += String.format(Locale.ENGLISH, "%.2fx,", flashlight.getSizeMultiplier());
                }
            }

            if (mapperStr.endsWith(",")){
                mapperStr = mapperStr.substring(0, mapperStr.length() - 1);
            }

            mapperStr += "]";
        }
        Debug.i("playedtime " + stat.getTime());
        final LegacyText beatmapInfoText = new LegacyText(Utils.toRes(4), Utils.toRes(2),
                "font", infoStr, vu);
        final LegacyText mapperInfo = new LegacyText(Utils.toRes(4), beatmapInfoText.getLegacyY() + beatmapInfoText.getLegacyHeight() + Utils.toRes(2),
                "smallFont", mapperStr, vu);
        final LegacyText playerInfo = new LegacyText(Utils.toRes(4), mapperInfo.getLegacyY() + mapperInfo.getLegacyHeight() + Utils.toRes(2),
                "smallFont", playerStr, vu);

        if (Config.isDisplayScoreStatistics() && !currentStatistic.isTeamStatistic()) {

            StringBuilder ppinfo = new StringBuilder();
            Beatmap beatmapData;

            try {
                beatmapData = BeatmapCache.getBeatmap(
                    beatmapInfo,
                    true,
                    switch (Config.getDifficultyAlgorithm()) {
                        case droid -> GameMode.Droid;
                        case standard -> GameMode.Standard;
                    }
                );
            } catch (Exception e) {
                Log.e("ScoringScene", "Failed to parse beatmap for difficulty calculation", e);
                beatmapData = null;
            }

            if (beatmapData != null) {
                DifficultyAttributes difficultyAttributes = null;
                PerformanceAttributes performanceAttributes = null;

                // Don't try to load online replay
                if (replayPath != null && beatmapToReplay != null && !replayPath.startsWith("https://")) {
                    var replay = new Replay();
                    replay.setObjectCount(beatmapToReplay.getTotalHitObjectCount());
                    replay.setBeatmap(beatmapToReplay.getFullBeatmapsetName(), beatmapToReplay.getFullBeatmapName(), mapMD5);

                    if (replay.load(replayPath, true)) {
                        // If statistics information is not complete, we populate it with information from replay.
                        if (
                            stat.getSliderHeadHits() == -1 ||
                            stat.getSliderTickHits() == -1 ||
                            stat.getSliderRepeatHits() == -1 ||
                            stat.getSliderEndHits() == -1
                        ) {
                            replay.populateStatistics(beatmapData, stat);
                        }

                        var copiedMods = mods;

                        if (mods.contains(ModNightCore.class) && replay.replayVersion <= 3) {
                            copiedMods = mods.deepCopy();
                            copiedMods.removeOfType(ModNightCore.class);
                            copiedMods.put(ModOldNightCore.class);
                        }

                        switch (Config.getDifficultyAlgorithm()) {
                            case droid -> {
                                var playableBeatmap = beatmapData.createDroidPlayableBeatmap(copiedMods.values());

                                difficultyAttributes = BeatmapDifficultyCalculator.calculateDroidDifficultyForReplay(beatmapData, playableBeatmap);

                                performanceAttributes = BeatmapDifficultyCalculator.calculateDroidPerformanceWithReplayStat(
                                    playableBeatmap, (DroidDifficultyAttributes) difficultyAttributes, replay, stat
                                );
                            }

                            case standard -> {
                                var playableBeatmap = beatmapData.createStandardPlayableBeatmap(copiedMods.values());

                                difficultyAttributes = BeatmapDifficultyCalculator.calculateStandardDifficulty(beatmapData, playableBeatmap);

                                performanceAttributes = BeatmapDifficultyCalculator.calculateStandardPerformanceWithReplayStat(
                                    playableBeatmap, (StandardDifficultyAttributes) difficultyAttributes,
                                    replay.objectData, stat
                                );
                            }
                        }
                    }
                }

                if (difficultyAttributes == null) {
                    difficultyAttributes = switch (Config.getDifficultyAlgorithm()) {
                        case droid -> BeatmapDifficultyCalculator.calculateDroidDifficulty(beatmapData, mods.values());
                        case standard -> BeatmapDifficultyCalculator.calculateStandardDifficulty(beatmapData, mods.values());
                    };

                    performanceAttributes = switch (Config.getDifficultyAlgorithm()) {
                        case droid -> BeatmapDifficultyCalculator.calculateDroidPerformance(
                            beatmapData, (DroidDifficultyAttributes) difficultyAttributes, stat
                        );

                        case standard -> BeatmapDifficultyCalculator.calculateStandardPerformance(
                            beatmapData, (StandardDifficultyAttributes) difficultyAttributes, stat
                        );
                    };
                }

                var maxPerformanceAttributes = switch (Config.getDifficultyAlgorithm()) {
                    case droid -> BeatmapDifficultyCalculator.calculateDroidPerformance(
                        (DroidDifficultyAttributes) difficultyAttributes
                    );

                    case standard -> BeatmapDifficultyCalculator.calculateStandardPerformance(
                        (StandardDifficultyAttributes) difficultyAttributes
                    );
                };

                ppinfo.append(String.format(
                    Locale.ENGLISH,
                    "%.2f★ | %.2f/%.2f" + (Config.getDifficultyAlgorithm() == DifficultyAlgorithm.droid ? "dpp" : "pp"),
                    difficultyAttributes.starRating,
                    performanceAttributes.total,
                    maxPerformanceAttributes.total
                ));
            }

            if (stat.getUnstableRate() > 0) {
                if (beatmapData != null) {
                    ppinfo.append("\n");
                }
                ppinfo.append(String.format(Locale.ENGLISH, "Error: %.2fms - %.2fms avg", stat.getNegativeHitError(), stat.getPositiveHitError()));
                ppinfo.append("\n");
                ppinfo.append(String.format(Locale.ENGLISH, "Unstable Rate: %.2f", stat.getUnstableRate()));
            }

            final LegacyText ppInfo = new LegacyText(Utils.toRes(4), Config.getRES_HEIGHT() - playerInfo.getLegacyHeight() - Utils.toRes(2),
                    "smallFont", ppinfo.toString(), vu);
            ppInfo.setPosition(Utils.toRes(244), Config.getRES_HEIGHT() - ppInfo.getLegacyHeight() - Utils.toRes(2) - paddingButtom);
            final LegacyRectangle statisticRectangle = new LegacyRectangle(Utils.toRes(240), Config.getRES_HEIGHT() - ppInfo.getLegacyHeight() - Utils.toRes(4) - paddingButtom, ppInfo.getLegacyWidth() + Utils.toRes(12), ppInfo.getLegacyHeight() + Utils.toRes(4), vu);
            statisticRectangle.setColor(0, 0, 0, 0.5f);
            plusAssign(statisticRectangle);
            plusAssign(ppInfo);
        }
        plusAssign(beatmapInfoText);
        plusAssign(mapperInfo);
        plusAssign(playerInfo);

        // In case the scene was reloaded
        if (Multiplayer.isMultiplayer) {
            updateLeaderboard();
        }

        if (beatmap != null && beatmap.getMD5().equals(mapMD5)) {
            ResourceManager.getInstance().getSound("applause").play();

            // Save score
            if (stat.getTotalScoreWithMultiplier() <= 0 || mods.contains(ModAutoplay.class) ||
                    (Multiplayer.isMultiplayer &&
                        ((Multiplayer.room != null && Multiplayer.room.isTeamVersus()) || game.hasFailed))) {
                return null;
            }

            int totalNotes = stat.getHit300() + stat.getHit100() + stat.getHit50() + stat.getMisses();

            // Do not save and submit score if note count does not match, since it indicates a corrupted score
            // (potentially from bugging the gameplay by any unnecessary means).
            if (totalNotes != beatmap.getTotalHitObjectCount()) {
                ToastLogger.showText(com.osudroid.resources.R.string.replay_corrupted, true);
                return null;
            }

            stat.setReplayFilename(FilenameUtils.getName(replayPath));
            stat.setBeatmapMD5(beatmap.getMD5());

            try {
                DatabaseManager.getScoreInfoTable().insertScore(stat.toScoreInfo());
            } catch (Exception e) {
                Log.e("GameScene", "Failed to save score to database", e);
            }

            // Upload score
            if (!OnlineManager.getInstance().isStayOnline() ||
                    (Multiplayer.isMultiplayer && !Config.isSubmitScoreOnMultiplayer())) {
                return null;
            }

            boolean hasUnrankedMod = SmartIterator.wrap(mods.values().iterator()).applyFilter(m -> !m.isRanked()).hasNext();

            if (hasUnrankedMod || Config.isRemoveSliderLock()) {
                return null;
            }

            var sendingPanel = new SendingPanel(OnlineManager.getInstance().getRank(),
                    OnlineManager.getInstance().getScore(), OnlineManager.getInstance().getAccuracy(),
                    OnlineManager.getInstance().getPP());

            plusAssign(sendingPanel);

            OnlineScoring.getInstance().sendRecord(beatmap, stat, sendingPanel, replayPath);
        }
        return null;
        });
    }

    public void updateLeaderboard() {
        // Called from the socket thread.
        Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {

        if (Multiplayer.finalData != null) {

            if (selector != null) {
                var oldSelector = selector;
                oldSelector.removeSelf();
                oldSelector.clearChildren();
            }

            selector = new StatisticSelector(Multiplayer.finalData);

            plusAssign(selector);
        }
        return null;
        }));
    }

    public void back() {
        ResourceManager.getInstance().getSound("applause").stop();
        Multiplayer.finalData = null;
        currentStatistic = null;

        if (Multiplayer.isMultiplayer)
        {
            // Preventing NPEs when player gets disconnected while playing
            if (!Multiplayer.isConnected()) {
                Verktex.INSTANCE.withTreeLock(() -> new LobbyScene()).show();
            } else {
                Multiplayer.roomScene.show();
            }
            return;
        }
        replayMusic();
        GlobalManager.getInstance().getSongMenu().show();
        GlobalManager.getInstance().getSongMenu().updateScore();
        setReplayID(-1);
    }

    public boolean isShown() {
        return Verktex.INSTANCE.getScene() == this;
    }

    public void show() {
        EngineRouter.showVerktex(this);
    }

    @Override
    public void onShown() {
        // Skin changes only reach the shown Verktex scene, so the regions are looked up again in
        // case the skin changed while this scene was not shown.
        propagate(child -> {
            if (child instanceof ISkinnable skinnable) {
                skinnable.onSkinChanged();
            }
            return Unit.INSTANCE;
        });

        // A release that went to AndEngine while this scene was not shown left its press behind.
        propagate(child -> {
            if (child instanceof IPressable pressable) {
                pressable.cancelPress();
            }
            return Unit.INSTANCE;
        });
        touchAreaBindings.clear();
    }

    @Override
    public void onSkinChanged() {
        // The background is not a child.
        if (getBackground() instanceof ISkinnable skinnable) {
            skinnable.onSkinChanged();
        }
    }

    @Override
    protected void onDraw(final GraphicsRenderer renderer) {
        // UIScene.onDraw without the safe-area padding: the legacy scene covers the whole window.
        onPerformLayout();

        final UIComponent background = getBackground();
        if (background != null) {
            background.draw(renderer);
        }

        final Vec4 clip = renderer.getClipBounds();
        for (final UIComponent child : getChildren()) {
            if (child.intersectsSceneRect(clip.getX(), clip.getY(), clip.getZ(), clip.getW())) {
                onDrawChild(renderer, child);
            }
        }
    }

    @Override
    public boolean onInputEvent(final InputEvent event) {
        // Keys arrive on the UI thread. The isShown() check keeps a quick second Back from running back() twice.
        if (event instanceof KeyboardEvent keyboardEvent && keyboardEvent.getAction() == KeyboardEvent.Action.Down
                && keyboardEvent.getKey() == KeyboardEvent.Key.Back) {
            Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                if (isShown()) {
                    back();
                }
                return null;
            }));
            return true;
        }

        if (!(event instanceof PointerEvent pointerEvent)) {
            return super.onInputEvent(event);
        }

        // The bound touch area gets the pointer's events wherever they are.
        if (pointerEvent.getAction() != PointerEvent.Action.Down) {
            final UIComponent bound = touchAreaBindings.get(pointerEvent.getPointerId());

            if (pointerEvent.getAction() == PointerEvent.Action.Up || pointerEvent.getAction() == PointerEvent.Action.Unknown) {
                touchAreaBindings.remove(pointerEvent.getPointerId());
            }

            if (bound != null && bound.getParent() == this && bound.onInputEvent(event)) {
                return true;
            }
        }

        // Front to back and inside their bounds. A child may rebuild the scene while it handles the event.
        final List<UIComponent> children = new ArrayList<>(getChildren());
        for (int i = children.size() - 1; i >= 0; i--) {
            final UIComponent child = children.get(i);

            if (child.containsScenePoint(pointerEvent.getX(), pointerEvent.getY()) && child.onInputEvent(event)) {
                if (pointerEvent.getAction() == PointerEvent.Action.Down) {
                    touchAreaBindings.put(pointerEvent.getPointerId(), child);
                }
                return true;
            }
        }
        return false;
    }

    public void stopMusic() {
        if (songService != null) {
            songService.stop();
        }
    }

    public void replayMusic() {
        if (songService != null) {
            songService.stop();
//            songService.preLoadWithLoop(game.filePath);
            songService.preLoad(beatmapInfo.getAudioPath());
            menu.updateMusicEffects();
            songService.play();
        }
    }

    public StatisticV2 getReplayStat() {
        return replayStat;
    }

    public void setReplayStat(StatisticV2 replayStat) {
        this.replayStat = replayStat;
    }

    public int getReplayID() {
        return replayID;
    }

    public void setReplayID(int id) {
        this.replayID = id;
    }

    /**
     * A sprite placed and sized in virtual units, which are {@code units} window pixels. A skin
     * texture is looked up again after a skin change.
     */
    private static class LegacySprite extends UISprite implements ISkinnable {
        private final String textureName;
        private final float units;
        private final float legacyWidth;
        private final float legacyHeight;
        private float legacyX;
        private float legacyY;

        LegacySprite(final float x, final float y, final String textureName, final float units) {
            this(x, y, Float.NaN, Float.NaN, SkinTextures.get(textureName, assetPath(textureName)), textureName, units);
        }

        LegacySprite(final float x, final float y, final float width, final float height, final String textureName, final float units) {
            this(x, y, width, height, SkinTextures.get(textureName, assetPath(textureName)), textureName, units);
        }

        LegacySprite(final float x, final float y, final float width, final float height, final GraphicsTexture.Region region, final float units) {
            this(x, y, width, height, region, null, units);
        }

        private LegacySprite(final float x, final float y, final float width, final float height,
                             final GraphicsTexture.Region region, final String textureName, final float units) {
            super(region);
            this.textureName = textureName;
            this.units = units;
            // Without a given size the sprite takes the size of its texture.
            legacyWidth = Float.isNaN(width) ? (region != null ? region.getWidth() : 0) : width;
            legacyHeight = Float.isNaN(height) ? (region != null ? region.getHeight() : 0) : height;

            setScaleType(ScaleType.Stretch);

            // A child is measured at most as large as the window, so a larger sprite is measured at a
            // fraction of its size and stretched back by its scale.
            final float stretchX = Math.max(1f, (float) Math.ceil(legacyWidth / Config.getRES_WIDTH()));
            final float stretchY = Math.max(1f, (float) Math.ceil(legacyHeight / Config.getRES_HEIGHT()));
            if (stretchX > 1f || stretchY > 1f) {
                setScaleOrigin(Anchor.TopLeft);
                setScaleX(stretchX);
                setScaleY(stretchY);
            }
            setWidth(DimensionKt.getPx(legacyWidth / stretchX * units));
            setHeight(DimensionKt.getPx(legacyHeight / stretchY * units));
            setPosition(x, y);
        }

        // The bundled ranking-SH has an upper-case extension.
        private static String assetPath(final String textureName) {
            return "ranking-SH".equals(textureName) ? "gfx/ranking-SH.PNG" : "gfx/" + textureName + ".png";
        }

        void setPosition(final float x, final float y) {
            legacyX = x;
            legacyY = y;
            setX(DimensionKt.getPx(x * units));
            setY(DimensionKt.getPx(y * units));
        }

        void setColor(final float red, final float green, final float blue) {
            setColor(new Color4(red, green, blue, getAlpha()));
        }

        float getLegacyX() {
            return legacyX;
        }

        float getLegacyY() {
            return legacyY;
        }

        float getLegacyWidth() {
            return legacyWidth;
        }

        float getLegacyHeight() {
            return legacyHeight;
        }

        @Override
        public void onSkinChanged() {
            if (textureName != null) {
                setTextureRegion(SkinTextures.get(textureName, assetPath(textureName)));
            }
        }
    }

    /**
     * A text placed in virtual units, which are {@code units} window pixels, in a ResourceManager
     * font's size. Its size is measured, an empty text has none.
     */
    private static class LegacyText extends UIText {
        private static final LayoutConstraints UNBOUNDED = new LayoutConstraints(0f, Float.POSITIVE_INFINITY, 0f, Float.POSITIVE_INFINITY);

        private final float units;
        private float legacyY;

        LegacyText(final float x, final float y, final String fontName, final String text, final float units) {
            this.units = units;
            setFontSize(DimensionKt.getPx(LegacyUnits.fontSize(fontName) * units));
            setWrapText(false);
            setColor(Color4.Companion.getWhite());
            setText(text);
            setPosition(x, y);
        }

        void setPosition(final float x, final float y) {
            legacyY = y;
            setX(DimensionKt.getPx(x * units));
            setY(DimensionKt.getPx(y * units));
        }

        float getLegacyY() {
            return legacyY;
        }

        float getLegacyWidth() {
            return getText().isEmpty() ? 0 : measure(UNBOUNDED).getX() / units;
        }

        float getLegacyHeight() {
            return getText().isEmpty() ? 0 : measure(UNBOUNDED).getY() / units;
        }
    }

    /**
     * A rectangle placed and sized in virtual units, which are {@code units}
     * window pixels.
     */
    private static class LegacyRectangle extends UIBox {
        LegacyRectangle(final float x, final float y, final float width, final float height, final float units) {
            setX(DimensionKt.getPx(x * units));
            setY(DimensionKt.getPx(y * units));
            setWidth(DimensionKt.getPx(width * units));
            setHeight(DimensionKt.getPx(height * units));
        }

        void setColor(final float red, final float green, final float blue, final float alpha) {
            setBackgroundColor(new Color4(red, green, blue, alpha));
        }
    }
}
