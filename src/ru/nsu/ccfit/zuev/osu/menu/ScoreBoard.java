package ru.nsu.ccfit.zuev.osu.menu;

import android.util.Log;

import com.osudroid.data.BeatmapInfo;
import com.osudroid.data.DatabaseManager;
import com.osudroid.multiplayer.Multiplayer;
import com.osudroid.ui.verktex.IPressable;
import com.osudroid.ui.verktex.ISkinnable;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.ui.verktex.songmenu.BoardScroller;
import com.osudroid.utils.Execution;
import com.osudroid.utils.ModUtils;
import com.osudroid.verktex.bridge.VerktexBridge;
import com.reco1l.verktex.Verktex;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.Vec2;
import com.reco1l.verktex.fonts.Font;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.texture.Textures;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.verktex.ui.text.UIText;

import org.andengine.util.math.MathUtils;
import org.jetbrains.annotations.Nullable;

import ru.nsu.ccfit.zuev.osu.*;
import ru.nsu.ccfit.zuev.osu.game.GameHelper;
import ru.nsu.ccfit.zuev.osu.helper.MD5Calculator;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;
import ru.nsu.ccfit.zuev.osu.online.OnlineFileOperator;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osu.scoring.BeatmapLeaderboardScoringMode;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The leaderboard of the song menu. Rows are positioned by hand in virtual units. The load and avatar
 * executors only fetch; every change they made to the tree is handed to the update thread.
 */
public class ScoreBoard extends UIContainer {
    private static final float ROW_WIDTH = 724 * 1.1f;

    private final MenuItemListener listener;
    private final UIText loadingText;
    private float percentShow = -1;
    private boolean showOnlineScores = false;
    private BeatmapInfo lastBeatmapInfo;
    private boolean wasOnline = false;
    private boolean isScroll = false;

    // Legacy SurfaceScrollDetector fed by the rows: window pixels, 10 px trigger distance.
    private int detectorPointerId = -1;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private boolean triggering = false;

    private final BoardScroller scroller = new BoardScroller(Config.getRES_HEIGHT());
    private float secPassed = 0;
    private float tapTime = 0;
    private float height = 0;
    private float downTime = -1;
    private int _scoreID = -1;
    private boolean moved = false;
    private volatile ArrayList<ScoreBoardItem> scoreItems = null;
    private BeatmapLeaderboardScoringMode currentScoringMode;

    private volatile LoadTask currentTask;

    private Runnable currentAvatarTask;

    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();

    // Rows in creation order, which is the order their touch areas were registered and asked in.
    private final ArrayList<ScoreItem> touchOrder = new ArrayList<>();

    private int avatarSerial = 0;

    // LegacyUnits.scale the sizes were last applied for.
    private float appliedScale = 0;


    public ScoreBoard(final UIContainer layer, final MenuItemListener listener) {
        layer.plusAssign(this);

        this.loadingText = new UIText();
        this.loadingText.setWrapText(false);
        this.loadingText.setColor(Color4.Companion.getWhite());
        this.plusAssign(this.loadingText);

        this.listener = listener;
        this.scroller.setCamY(-146);
        this.scroller.setMaxY(100500);
    }

    // The AndEngine text was created with a capacity of 50 characters and cut longer texts.
    private void setLoadingText(String text) {
        loadingText.setText(text.length() > 50 ? text.substring(0, 50) : text);
    }

    public static String formatScore(long score) {
        var sb = new StringBuilder();
        sb.append(Math.abs(score));
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, ' ');
        }
        return sb.toString();
    }

    public static String formatPP(int pp) {
        var sb = new StringBuilder();
        sb.append(pp);
        return sb.toString();
    }

    private void initFromOnline(BeatmapInfo beatmapInfo) {
        currentScoringMode = Config.getBeatmapLeaderboardScoringMode();

        if (currentScoringMode == BeatmapLeaderboardScoringMode.SCORE) {
            setLoadingText("Loading score leaderboard...");
        } else {
            setLoadingText("Loading pp leaderboard...");
        }

        final var scoringMode = currentScoringMode;

        currentTask = new LoadTask(true) {

            @Override
            public void run() {
                if (GlobalManager.getInstance().getSelectedBeatmap() != beatmapInfo) {
                    return;
                }

                List<String> scores;

                try {
                    scores = OnlineManager.getInstance().getTop(beatmapInfo.getMD5());
                } catch (OnlineManager.OnlineManagerException e) {
                    Log.e("Scoreboard", "Failed to load scores from online.", e);

                    if (isActive()) {
                        post(() -> setLoadingText("Cannot load scores"));
                    }
                    return;
                }

                if (!isActive()) {
                    return;
                }

                final String failMessage = OnlineManager.getInstance().getFailMessage();
                post(() -> setLoadingText(failMessage));

                boolean isPPScoringMode = scoringMode == BeatmapLeaderboardScoringMode.PP;
                var username = OnlineManager.getInstance().getUsername();
                var items = new ArrayList<ScoreBoardItem>(scores.size());
                var sb = new StringBuilder();

                float nextTotal = 0;

                for (int i = 0; i < scores.size() && isActive(); ++i) {

                    var data = scores.get(i).split("\\s+");

                    if (data.length < 9 || data.length > 10) {
                        continue;
                    }

                    var isInLeaderboard = data.length == 9;
                    var isPersonalBest = data.length == 10 || data[1].equals(username);

                    var scoreID = Integer.parseInt(data[0]);
                    var playerName = isPersonalBest ? username : data[1];
                    var score = Long.parseLong(data[2]);
                    var pp = Float.parseFloat(data[3]);
                    var combo = Integer.parseInt(data[4]);
                    var mark = data[5];
                    var mods = data[6];
                    var accuracy = Float.parseFloat(data[7]);
                    var avatarURL = data[8];
                    var beatmapRank = isPersonalBest && !isInLeaderboard ? Integer.parseInt(data[9]) : (i + 1);

                    sb.setLength(0);
                    var scoreStr = isPPScoringMode ?
                            // For display purposes, we round the pp.
                            formatPP(Math.round(pp)) :
                            formatScore(score);

                    sb.setLength(0);
                    var titleStr = sb.append('#').append(beatmapRank).append(' ').append(playerName)
                            .append('\n')
                            .append(StringTable.format(
                                    isPPScoringMode ? com.osudroid.resources.R.string.menu_performance : com.osudroid.resources.R.string.menu_score, scoreStr, combo))
                            .toString();

                    if (i < scores.size() - 1) {
                        String[] nextData = scores.get(i + 1).split("\\s+");

                        if (nextData.length == 9 || nextData.length == 10) {
                            nextTotal = isPPScoringMode ? Float.parseFloat(nextData[3]) : Integer.parseInt(nextData[2]);
                        }
                    } else {
                        nextTotal = 0;
                    }

                    sb.setLength(0);

                    var modString = "";

                    try {
                        modString = ModUtils.deserializeMods(mods).toDisplayModString(false);
                    } catch (final Exception e) {
                        Log.e("ScoreBoard", "Failed to parse mods from local score.", e);
                    }

                    var currentTotal = isPPScoringMode ? pp : score;
                    var diffTotal = Math.round(currentTotal) - Math.round(nextTotal);

                    sb.setLength(0);
                    var accStr = sb.append(modString)
                            .append('\n')
                            .append(StringTable.format("%.2f", GameHelper.Round(accuracy * 100, 2))).append('%')
                            .append('\n')
                            .append(nextTotal == 0 ? "-" : ((diffTotal != 0 ? "+" : "") + diffTotal))
                            .toString();

                    if (!isActive()) {
                        return;
                    }

                    post(() -> {
                        if (isPersonalBest) {
                            attachRow(new ScoreItem(avatarExecutor, titleStr, accStr, mark, true, scoreID, avatarURL, playerName, true), true);
                        }

                        if (isInLeaderboard) {
                            attachRow(new ScoreItem(avatarExecutor, titleStr, accStr, mark, true, scoreID, avatarURL, playerName, false), false);
                        }
                    });

                    if (isInLeaderboard) {
                        var item = new ScoreBoardItem();
                        item.set(beatmapRank, playerName, combo, score, scoreID);
                        items.add(item);
                    }
                }
                post(() -> {
                    scoreItems = items;
                    percentShow = 0;
                });
            }
        };
        loadExecutor.submit(currentTask);
    }

    private void initFromLocal(BeatmapInfo beatmap) {

        currentTask = new LoadTask(false) {

            @Override
            public void run() {
                if (GlobalManager.getInstance().getSelectedBeatmap() != beatmap) {
                    return;
                }

                DatabaseManager.getScoreInfoTable().migrateScores(beatmap.getMD5(), beatmap.getBeatmapDifficulty());
                var scores = DatabaseManager.getScoreInfoTable().getBeatmapLeaderboard(beatmap.getMD5(), beatmap.getBeatmapDifficulty());

                if (scores.isEmpty() || !isActive()) {

                    // This allows the in-game leaderboard to show even if the local database is empty, it'll append
                    // the player score (because the in-game leaderboard assumes that the board finished loading only
                    // if the scores list isn't null).
                    if (isActive()) {
                        post(() -> scoreItems = new ArrayList<>());
                    }
                    return;
                }

                var items = new ArrayList<ScoreBoardItem>(scores.size());
                var sb = new StringBuilder();

                long nextTotalScore;

                for (int i = 0; i < scores.size() && isActive(); ++i) {

                    var scored = scores.get(i);
                    var score = scored.getScoreInfo();
                    var effectiveScore = scored.getEffectiveScore();

                    sb.setLength(0);
                    var totalScore = formatScore(effectiveScore);

                    sb.setLength(0);
                    var titleStr = sb.append('#').append(i + 1).append(' ').append(score.getPlayerName())
                            .append('\n')
                            .append(StringTable.format(com.osudroid.resources.R.string.menu_score, totalScore, score.getMaxCombo()))
                            .toString();

                    if (i < scores.size() - 1) {
                        nextTotalScore = scores.get(i + 1).getEffectiveScore();
                    } else {
                        nextTotalScore = 0;
                    }

                    sb.setLength(0);

                    var modString = "";

                    try {
                        modString = ModUtils.deserializeMods(score.getMods()).toDisplayModString(false);
                    } catch (final Exception e) {
                        Log.e("ScoreBoard", "Failed to parse mods from local score.", e);
                    }

                    var diffTotalScore = effectiveScore - nextTotalScore;

                    sb.setLength(0);
                    var accStr = sb.append(modString)
                            .append('\n')
                            .append(StringTable.format("%.2f", GameHelper.Round(score.getAccuracy() * 100, 2))).append('%')
                            .append('\n')
                            .append(nextTotalScore == 0 ? "-" : ((diffTotalScore != 0 ? "+" : "") + diffTotalScore))
                            .toString();

                    if (!isActive()) {
                        return;
                    }

                    post(() -> attachRow(new ScoreItem(avatarExecutor, titleStr, accStr, score.getMark(), false, (int) score.getId(), null, null, false), false));

                    var item = new ScoreBoardItem();
                    item.set(i + 1, score.getPlayerName(), score.getMaxCombo(), effectiveScore, (int) score.getId());
                    items.add(item);
                }
                post(() -> {
                    scoreItems = items;
                    percentShow = 0;
                });
            }
        };
        loadExecutor.submit(currentTask);
    }

    /**
     * Must be called on the update thread.
     */
    public synchronized void init(final BeatmapInfo beatmapInfo) {
        VerktexBridge.checkUpdateThread();

        if (lastBeatmapInfo == beatmapInfo && showOnlineScores == wasOnline && wasOnline &&
                Config.getBeatmapLeaderboardScoringMode() == currentScoringMode) {
            return;
        }

        loadingText.setText("");
        lastBeatmapInfo = beatmapInfo;
        wasOnline = showOnlineScores;
        scoreItems = null;

        Verktex.INSTANCE.withTreeLock(() -> {

            // The legacy kept the previous task active for a null beatmap, so its rows could still arrive.
            dropRows();
            scroller.setCamY(-146);
            plusAssign(loadingText);
            return null;
        });

        if (beatmapInfo == null)
            return;

        if (OnlineManager.getInstance().isStayOnline() && showOnlineScores) {
            initFromOnline(beatmapInfo);
            return;
        }

        initFromLocal(beatmapInfo);
    }

    /**
     * Drops the rows, which unloads their avatars, and stops both executors. The scene calls this before it
     * discards the board, because clearing an ancestor does not detach the rows.
     */
    public void release() {
        VerktexBridge.checkUpdateThread();

        Verktex.INSTANCE.withTreeLock(() -> {
            dropRows();
            return null;
        });
        loadExecutor.shutdownNow();
    }

    // A late post of the old task is a no-op once it is no longer current, and a late avatar finds its row detached.
    private void dropRows() {
        if (currentTask != null && currentTask.avatarExecutor != null)
            currentTask.avatarExecutor.shutdownNow();

        currentTask = null;
        clearChildren();
        touchOrder.clear();
        currentAvatarTask = null;
    }

    private void attachRow(ScoreItem row, boolean atFront) {
        plusAssign(row);

        if (atFront) {
            moveToBack(row);
        }

        touchOrder.add(row);
        height = row.virtualHeight;
    }

    @Override
    protected void onUpdate(final TickingClock clock)
    {
        final float scale = LegacyUnits.getScale();
        if (appliedScale != scale) {
            appliedScale = scale;

            loadingText.setX(DimensionKt.getPx(5 * scale));
            loadingText.setY(DimensionKt.getPx(230 * scale));
            loadingText.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("strokeFont") * scale));
        }

        super.onUpdate(clock);
        final float pSecondsElapsed = clock.getElapsedTickTime();
        secPassed += pSecondsElapsed;

        // The first child is always the loading text, and only leaderboard items need to be updated.
        if (getChildren().size() <= 1) {
            return;
        }

        if (percentShow == -1) {
            float y = -scroller.getCamY();

            var count = getChildren().size();
            for (int i = 0; i < count; i++)
            {
                var child = getChildren().get(i);

                if (!(child instanceof ScoreItem))
                    continue;

                var sprite = (ScoreItem) child;
                sprite.setPosition(sprite.positionX, y);
                y += 0.8f * (sprite.virtualHeight - 32);
            }

            y += scroller.getCamY();
            scroller.update(pSecondsElapsed, y, height);

            // The legacy cleared the flag whenever its update left the velocity at zero.
            if (scroller.getVelocityY() == 0) {
                isScroll = false;
            }
        } else {
            percentShow += pSecondsElapsed * 4;
            if (percentShow > 1) {
                percentShow = 1;
            }

            var count = getChildren().size();
            int spriteIndex = 0;
            for (int i = 0; i < count; i++)
            {
                var child = getChildren().get(i);

                if (!(child instanceof ScoreItem))
                    continue;

                var sprite = (ScoreItem) child;
                sprite.setPosition(-160, 146 + 0.8f * percentShow * spriteIndex * (sprite.virtualHeight - 32));
                spriteIndex++;
            }

            if (percentShow == 1) {
                percentShow = -1;
            }
        }

        if (downTime >= 0) {
            downTime += pSecondsElapsed;
        }

        if (downTime > 0.5f) {
            moved = true;
            if (!Multiplayer.isMultiplayer && _scoreID != -1 && !showOnlineScores) {
                GlobalManager.getInstance().getSongMenu().showDeleteScoreMenu(_scoreID);
            }
            downTime = -1;
        }
    }

    @Override
    public boolean onInputEvent(final InputEvent event) {
        // The scene asked the touch areas back to front, which is the order they were registered in.
        for (int i = 0; i < touchOrder.size(); i++) {
            if (touchOrder.get(i).onInputEvent(event)) {
                return true;
            }
        }
        return false;
    }

    /** Legacy {@code SurfaceScrollDetector.onManagedTouchEvent}, distances in window pixels as the surface gave them. */
    private void feedScrollDetector(final PointerEvent event) {
        final float touchX = event.getX();
        final float touchY = event.getY();

        final PointerEvent.Action action = event.getAction();
        if (action == PointerEvent.Action.Down) {
            prepareScroll(event.getPointerId(), touchX, touchY);
        } else if (action == PointerEvent.Action.Move) {
            if (detectorPointerId == -1) {
                prepareScroll(event.getPointerId(), touchX, touchY);
            } else if (detectorPointerId == event.getPointerId()) {
                final float distanceY = touchY - lastTouchY;

                if (triggering || Math.abs(touchX - lastTouchX) > 10 || Math.abs(distanceY) > 10) {
                    if (!triggering) {
                        onScrollStarted(event.getPointerId());
                    } else {
                        onScroll(event.getPointerId(), distanceY);
                    }

                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    triggering = true;
                }
            }
        } else if (detectorPointerId == event.getPointerId()) {
            // Up and cancel.
            if (triggering) {
                onScrollFinished(event.getPointerId(), touchY - lastTouchY);
            }
            detectorPointerId = -1;
        }
    }

    private void prepareScroll(final int pointerId, final float x, final float y) {
        detectorPointerId = pointerId;
        lastTouchX = x;
        lastTouchY = y;
        triggering = false;
    }

    private void onScrollStarted(int pPointerID) {
        scroller.onScrollStarted(pPointerID, secPassed);
        tapTime = secPassed;
        isScroll = true;
    }

    private void onScroll(int pPointerID, float pDistanceY) {
        // The detector only reports the pointer the scroller tracks, so the legacy pointer check always passed.
        isScroll = true;
        scroller.onScroll(pPointerID, pDistanceY);
    }

    private void onScrollFinished(int pPointerID, float pDistanceY) {
        if (secPassed - tapTime <= 0.001f) {
            isScroll = false;
        }
        scroller.onScrollFinished(pPointerID, pDistanceY, secPassed);
    }

    public boolean isShowOnlineScores() {
        return showOnlineScores;
    }

    public void setShowOnlineScores(boolean showOnlineScores) {
        this.showOnlineScores = showOnlineScores;
    }

    @Nullable
    public ArrayList<ScoreBoardItem> getScoreBoardItems() {
        return scoreItems;
    }


    private abstract class LoadTask implements Runnable {

        protected final ExecutorService avatarExecutor;

        LoadTask(boolean fromOnline) {
            avatarExecutor = fromOnline ? Executors.newSingleThreadExecutor() : null;
        }

        protected final boolean isActive() {
            return currentTask == this;
        }

        /** Runs {@code block} on the update thread while this is still the current task. */
        protected final void post(final Runnable block) {
            Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                if (isActive()) {
                    block.run();
                }
                return null;
            }));
        }

    }


    /**
     * A leaderboard row. The AndEngine sprite was the row; here it is a child of this container, which carries
     * the sprite's size, its 0.65 scale and its scale centre (the centre of the texture, set before the size changed).
     */
    private class ScoreItem extends UIContainer implements IPressable, ISkinnable {


        private float dx = 0;

        private float dy = 0;

        private UISprite avatar;

        private String avatarKey;

        private Runnable avatarTask;

        private final ExecutorService avatarExecutor;

        private final String username;

        private final int scoreID;

        private final boolean showOnline;

        private final String markStr;

        private final UISprite sprite;

        private final UIText topText;

        private final UIText text;

        private final UIText accText;

        private final UISprite mark;

        // Legacy position, in virtual units.
        private float positionX = -150;

        private float positionY = 40;

        private final float baseX;

        private final float baseY;

        /** Height of the row before its scale, in virtual units (legacy {@code getHeight()}). */
        private final float virtualHeight;

        private boolean texturesInvalid = true;

        // LegacyUnits.scale the sizes were last applied for.
        private float appliedScale = 0;


        private ScoreItem(
                ExecutorService avatarExecutor,
                String title,
                String acc,
                String markStr,
                boolean showOnline,
                int scoreID,
                String avaURL,
                String username,
                boolean isPersonalBest) {
            super();

            this.avatarExecutor = avatarExecutor;
            this.showOnline = showOnline;
            this.username = username;
            this.scoreID = scoreID;
            this.markStr = markStr;

            var shouldLoadAvatar = showOnlineScores
                    && Config.getLoadAvatar()
                    && avaURL != null
                    && avatarExecutor != null;

            baseX = shouldLoadAvatar ? 90 : 0;

            sprite = new UISprite(null);
            sprite.setScaleType(UISprite.ScaleType.Stretch);

            if (isPersonalBest) {

                topText = new UIText();
                topText.setText("Personal Best");
                topText.setWrapText(false);
                topText.setColor(Color4.Companion.getWhite());

                final float lineHeight = topText.getFontFamily().get(
                        DimensionKt.getPx(LegacyUnits.fontSize("strokeFont") * LegacyUnits.getScale()),
                        Font.Weight.Normal, Font.Style.Normal).getMetrics().getLineHeight();
                baseY = lineHeight / LegacyUnits.getScale() + 5;

                topText.setOrigin(Anchor.TopCenter);
                topText.setScale(0.8f);

                virtualHeight = baseY + 120;

            } else {
                topText = null;
                baseY = 0;
                virtualHeight = 107;
            }

            setScale(0.65f);

            sprite.setColor(new Color4(0f, 0f, 0f, 0.5f));

            avatarTask = shouldLoadAvatar ? new Runnable() {

                @Override
                public void run() {
                    final GraphicsTexture.Source source = !avatarExecutor.isShutdown() ? fetchAvatar(avaURL) : null;

                    Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                        // The row was removed meanwhile; nothing was registered yet.
                        if (getParent() == null) {
                            return null;
                        }

                        GraphicsTexture.Region region = null;

                        if (source != null) {
                            // One key per row, so unloading one row cannot take the region of another showing the same avatar.
                            final String key = "scoreboard-avatar#" + avatarSerial++;
                            try {
                                region = SkinTextures.register(key, source, true);
                            } catch (final Exception e) {
                                region = null;
                            }

                            if (region != null) {
                                avatarKey = key;
                            }
                        }

                        avatar = new UISprite(region != null ? region : SkinTextures.get("emptyavatar"));
                        avatar.setScaleType(UISprite.ScaleType.Stretch);
                        plusAssign(avatar);
                        appliedScale = 0;

                        if (currentAvatarTask == this)
                            currentAvatarTask = null;
                        return null;
                    }));
                }
            } : null;


            text = new UIText();
            text.setText(title);
            text.setWrapText(false);
            text.setColor(Color4.Companion.getWhite());
            accText = new UIText();
            accText.setText(acc);
            accText.setWrapText(false);
            accText.setColor(Color4.Companion.getWhite());
            mark = new UISprite(null);
            mark.setScaleType(UISprite.ScaleType.Stretch);

            text.setScale(1.2f);
            mark.setScale(1.5f);
            plusAssign(sprite);
            if (topText != null)
                plusAssign(topText);
            plusAssign(text);
            plusAssign(accText);
            plusAssign(mark);
        }

        /** Legacy {@code setPosition}, in virtual units. */
        private void setPosition(final float x, final float y) {
            positionX = x;
            positionY = y;
            setX(DimensionKt.getPx(x * LegacyUnits.getScale()));
            setY(DimensionKt.getPx(y * LegacyUnits.getScale()));
        }

        @Override
        public void onDetached()
        {
            super.onDetached();

            if (avatarKey != null)
                Textures.INSTANCE.unload(avatarKey);
            avatarKey = null;
        }

        @Override
        public void onSkinChanged() {
            texturesInvalid = true;
        }

        @Override
        protected void onUpdate(final TickingClock clock)
        {
            if (texturesInvalid) {
                texturesInvalid = false;

                sprite.setTextureRegion(SkinTextures.get("menu-button-background"));
                mark.setTextureRegion(SkinTextures.get("ranking-" + markStr + "-small"));

                if (avatarKey == null && avatar != null) {
                    avatar.setTextureRegion(SkinTextures.get("emptyavatar"));
                }
                appliedScale = 0;
            }

            final float scale = LegacyUnits.getScale();
            if (appliedScale != scale) {
                appliedScale = scale;

                final GraphicsTexture.Region region = sprite.getTextureRegion();
                final float textureWidth = region != null ? region.getWidth() : 0;
                final float textureHeight = region != null ? region.getHeight() : 0;

                setWidth(DimensionKt.getPx(ROW_WIDTH * scale));
                setHeight(DimensionKt.getPx(virtualHeight * scale));
                setScaleOrigin(new Vec2(textureWidth / 2 / ROW_WIDTH, textureHeight / 2 / virtualHeight));
                setX(DimensionKt.getPx(positionX * scale));
                setY(DimensionKt.getPx(positionY * scale));

                sprite.setWidth(getWidth());
                sprite.setHeight(getHeight());

                if (topText != null) {
                    // getWidth() was still the texture width when the legacy centred this text.
                    topText.setX(DimensionKt.getPx((textureWidth + baseX) / 2 * scale));
                    topText.setY(DimensionKt.getPx(20 * scale));
                    topText.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("strokeFont") * scale));
                }

                text.setX(DimensionKt.getPx((baseX + 160) * scale));
                text.setY(DimensionKt.getPx((baseY + 20) * scale));
                text.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("font") * scale));

                accText.setX(DimensionKt.getPx(670 * scale));
                accText.setY(DimensionKt.getPx((baseY + 12) * scale));
                accText.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("smallFont") * scale));

                final GraphicsTexture.Region markRegion = mark.getTextureRegion();
                final float markWidth = markRegion != null ? markRegion.getWidth() : 0;
                final float markHeight = markRegion != null ? markRegion.getHeight() : 0;
                mark.setX(DimensionKt.getPx((baseX + markWidth / 2 + 60) * scale));
                mark.setY(DimensionKt.getPx((baseY + 35) * scale));
                mark.setWidth(DimensionKt.getPx(markWidth * scale));
                mark.setHeight(DimensionKt.getPx(markHeight * scale));

                if (avatar != null) {
                    avatar.setX(DimensionKt.getPx(55 * scale));
                    avatar.setY(DimensionKt.getPx((baseY + 12) * scale));
                    avatar.setWidth(DimensionKt.getPx(90 * scale));
                    avatar.setHeight(DimensionKt.getPx(90 * scale));
                }
            }

            super.onUpdate(clock);

            // This is to avoid loading avatars when the scene was changed (game started or user gone back to main menu).
            if (avatarTask != null && currentAvatarTask == null) {

                var task = avatarTask;
                avatarTask = null;
                currentAvatarTask = task;

                try {
                    avatarExecutor.submit(task);
                } catch (RejectedExecutionException e) {
                    if (currentAvatarTask == task)
                        currentAvatarTask = null;
                }
            }
        }

        @Override
        public boolean onInputEvent(final InputEvent inputEvent) {
            if (!(inputEvent instanceof PointerEvent event)) {
                return false;
            }

            // AndEngine only reported touches inside the touch area.
            if (!containsScenePoint(event.getX(), event.getY())) {
                return false;
            }

            feedScrollDetector(event);

            // Local coordinates before the 0.65 scale, in virtual units, as the AndEngine localX/localY.
            final Vec2 local = toLocalSpaceFromScene(event.getX(), event.getY());
            final float localX = local.getX() / LegacyUnits.getScale();
            final float localY = local.getY() / LegacyUnits.getScale();

            final PointerEvent.Action action = event.getAction();
            if (action == PointerEvent.Action.Down) {
                moved = false;
                sprite.setAlpha(0.8f);
                listener.stopScroll(positionY + localY);
                dx = localX;
                dy = localY;
                downTime = 0;
                _scoreID = scoreID;
                return true;
            } else if (action == PointerEvent.Action.Up && !moved && !isScroll) {
                downTime = -1;
                sprite.setAlpha(0.5f);

                if (Multiplayer.isMultiplayer)
                    return true;

                listener.openScore(scoreID, showOnline, username);
                GlobalManager.getInstance().getScoring().setReplayID(scoreID);
                return true;
            } else if (action == PointerEvent.Action.Unknown || action == PointerEvent.Action.Move && MathUtils.distance(dx, dy, localX, localY) > 10) {
                // Unknown stands for AndEngine's ACTION_OUTSIDE (and cancel).
                downTime = -1;
                sprite.setAlpha(0.5f);
                moved = true;
            }
            return false;
        }

        @Override
        public void cancelPress() {
            downTime = -1;
            sprite.setAlpha(0.5f);
        }
    }

    /**
     * Legacy {@code OnlineManager.loadAvatarToTextureManager} without the AndEngine upload: the avatar the server
     * gave, else the default avatar. Null when neither decodes.
     */
    private static GraphicsTexture.Source fetchAvatar(final String avatarURL) {
        if (avatarURL.isEmpty()) {
            return null;
        }

        final File file = new File(Config.getCachePath(), MD5Calculator.getStringMD5(avatarURL));
        OnlineFileOperator.downloadFile(avatarURL, file.getAbsolutePath(), true);
        final GraphicsTexture.Source source = SkinTextures.decode(file);
        if (source != null) {
            return source;
        }

        final File defaultAvatarFile = new File(Config.getCachePath(), MD5Calculator.getStringMD5(OnlineManager.defaultAvatarURL));
        OnlineFileOperator.downloadFile(OnlineManager.defaultAvatarURL, defaultAvatarFile.getAbsolutePath());
        return SkinTextures.decode(defaultAvatarFile);
    }
}
