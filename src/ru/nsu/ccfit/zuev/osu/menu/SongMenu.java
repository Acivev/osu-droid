package ru.nsu.ccfit.zuev.osu.menu;

import static com.osudroid.data.BeatmapsKt.BeatmapInfo;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;

import com.edlplan.framework.easing.Easing;
import com.edlplan.ui.fragment.SearchBarFragment;
import com.edlplan.ui.fragment.BeatmapPropertiesFragment;
import com.edlplan.ui.fragment.ScoreMenuFragment;
import com.osudroid.beatmaps.BeatmapCache;
import com.osudroid.ui.v1.BeatmapAttributeDisplay;
import com.osudroid.ui.verktex.IPressable;
import com.osudroid.ui.verktex.ISkinnable;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.ui.verktex.songmenu.LegacyPressTracker;
import com.osudroid.ui.verktex.songmenu.ListScroller;
import com.osudroid.utils.Execution;
import com.osudroid.verktex.bridge.Destination;
import com.osudroid.verktex.bridge.EngineRouter;
import com.osudroid.verktex.bridge.RoutedScene;
import com.osudroid.verktex.bridge.VerktexBridge;
import com.reco1l.verktex.ui.UIScene;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.framework.EasingKt;
import com.osudroid.multiplayer.api.RoomAPI;
import com.osudroid.data.BeatmapInfo;
import com.osudroid.data.BeatmapSetInfo;
import com.osudroid.data.DatabaseManager;
import com.osudroid.ui.verktex.songmenu.SkinButton;
import com.reco1l.verktex.ui.UISprite;
import com.osudroid.multiplayer.Multiplayer;

import com.osudroid.ui.v2.modmenu.ModMenu;
import com.osudroid.GameMode;
import com.osudroid.difficulty.BeatmapDifficultyCalculator;
import com.osudroid.math.Precision;
import com.osudroid.mods.LegacyModConverter;
import com.osudroid.mods.ModDifficultyAdjust;
import com.osudroid.mods.ModNightCore;
import com.osudroid.mods.ModPrecise;
import com.osudroid.mods.ModReplayV6;
import com.osudroid.utils.LRUCache;
import com.osudroid.utils.ModUtils;

import com.reco1l.verktex.Verktex;
import com.reco1l.verktex.android.AndroidTextureSource;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.Vec2;
import com.reco1l.verktex.data.Vec4;
import com.reco1l.verktex.graphics.GraphicsRenderer;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.KeyboardEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.texture.Textures;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UIComponent;
import com.reco1l.verktex.ui.shape.UIBox;
import com.reco1l.verktex.ui.text.UIText;

import org.andengine.engine.Engine;
import org.andengine.util.debug.Debug;
import org.andengine.util.math.MathUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import kotlin.Unit;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobKt;
import ru.nsu.ccfit.zuev.audio.BassSoundProvider;
import ru.nsu.ccfit.zuev.audio.Status;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.DifficultyAlgorithm;
import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.LibraryManager;
import ru.nsu.ccfit.zuev.osu.RankedStatus;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.ToastLogger;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.game.GameHelper;
import ru.nsu.ccfit.zuev.osu.game.GameScene;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager.OnlineManagerException;
import ru.nsu.ccfit.zuev.osu.online.OnlinePanel;
import ru.nsu.ccfit.zuev.osu.online.OnlineScoring;
import ru.nsu.ccfit.zuev.osu.scoring.BeatmapLeaderboardScoringMode;
import ru.nsu.ccfit.zuev.osu.scoring.ScoringScene;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;
import ru.nsu.ccfit.zuev.skins.OsuSkin;
import ru.nsu.ccfit.zuev.skins.SkinLayout;

/**
 * The song menu. Positions and sizes are the virtual units of the legacy menu, converted to window
 * pixels in {@link #onPerformLayout}. Its state and tree are changed on the update thread only.
 */
public class SongMenu extends UIScene implements ISkinnable, RoutedScene, MenuItemListener {
    private ListLayer frontLayer = new ListLayer();
    SortOrder sortOrder = SortOrder.Title;
    private Engine engine;
    public GameScene game;
    private ScoringScene scoreScene;
    private final ListScroller scroller = new ListScroller(Config.getRES_WIDTH(), Config.getRES_HEIGHT(), Utils::toRes);
    private Activity context;
    private ListLayer backLayer = new ListLayer();
    private ArrayList<BeatmapSetItem> items = new ArrayList<>();
    private BeatmapSetItem selectedItem = null;
    // Read by the difficulty calculation job.
    private volatile BeatmapInfo selectedBeatmap;
    private UISprite bg = null;
    private String backgroundPath = "";
    private ScoreBoard board;
    private String filterText = "";
    private boolean favsOnly = false;
    @Nullable private List<String> limitC;
    private float secPassed = 0;
    private ScrollBar scrollbar;
    // Written by the music loading job.
    private volatile boolean allowAutomaticPlaybackRestart = true;
    // Main thread only.
    private ValueAnimator musicVolumeAnimator;

    private Job calculationJob,
                musicLoadingJob,
                backgroundLoadingJob,
                mapStatusJob;

    private UIText
            beatmapMetadataText,
            beatmapCreatorText,
            beatmapLengthText,
            beatmapHitObjectsText,
            beatmapDifficultyText;

    private UISprite scoringSwitcher = null;
    private String scoringSwitcherTexture = "ranking_disabled";
    private SearchBarFragment searchBar = null;
    private GroupType groupType = GroupType.MapSet;

    private Timer previousSelectionTimer;
    private final long previousSelectionInterval = 1000;
    private boolean previousSelectionPerformed;
    private final LinkedList<BeatmapSetItem> previousSelectedItems = new LinkedList<>();
    private final LRUCache<String, RankedStatus> mapStatuses = new LRUCache<>(50);

    // Tree, rebuilt by load()
    private UIContainer backgroundLayer = new UIContainer();
    private UIBox backgroundBox = new UIBox();
    private UISprite songSelectTop = null;
    private UIText emptyText = null;

    // LegacyUnits.scale and songselect-top region the empty text and the top banner were last sized for.
    private float appliedScale = 0;
    private GraphicsTexture.Region appliedTopRegion = null;
    private OnlinePanel onlinePanel = null;

    /** The shown background sprite, null while the background is a plain colour. */
    private UISprite backgroundSprite = null;

    /** Whether {@link #backgroundSprite} shows {@code menu-background}, which is looked up again after a skin change. */
    private boolean isMenuBackground = false;

    /** Key of the beatmap background the scene registered, unloaded once it is replaced. */
    private String backgroundTextureKey = null;
    private int backgroundGeneration = 0;

    /** Decoded beatmap background and its path, handed to AndEngine by {@link #handOverBackground}. */
    private Bitmap currentBackgroundBitmap = null;
    private String handOverPath = null;

    private boolean isLibraryEmpty = false;

    // Virtual y of the five info texts, found on the update thread.
    private final float[] infoTextY = new float[5];
    private boolean texturesInvalid = false;

    public SongMenu() {
        scroller.setMaxY(100500);
    }

    public static void stopMusicStatic() {
        var songService = GlobalManager.getInstance().getSongService();

        if (songService != null) {
            songService.stop();
        }
    }

    public void setScoringScene(final ScoringScene ss) {
        scoreScene = ss;
    }

    @Nullable
    public ArrayList<ScoreBoardItem> getBoard() {
        return board.getScoreBoardItems();
    }

    public boolean isBoardOnline() {
        return board.isShowOnlineScores();
    }

    public ArrayList<BeatmapSetItem> getMenuItems() {
        return items;
    }

    public void init(final Activity context, final Engine engine,
                     final GameScene pGame) {
        VerktexBridge.checkUpdateThread();

        this.engine = engine;
        game = pGame;
        this.context = context;
    }

    public void loadFilter(IFilterMenu filterMenu) {
        setFilter(
            filterMenu.getFilter(),
            filterMenu.getOrder(),
            filterMenu.isFavoritesOnly(),
            filterMenu.getFavoriteFolder().isEmpty() ? null : DatabaseManager.getBeatmapCollectionsTable().getBeatmaps(filterMenu.getFavoriteFolder())
        );
    }

    public void reload() {
        VerktexBridge.checkUpdateThread();

        load();
        GlobalManager.getInstance().getGameScene().setOldScene(new Destination.Verktex(this));
    }

    public synchronized void load() {
        Verktex.INSTANCE.withTreeLock(() -> {
            loadTree();
            return null;
        });
    }

    private void loadTree() {
        VerktexBridge.checkUpdateThread();

        // The rows hold avatars, which clearing an ancestor does not unload.
        if (board != null) {
            board.release();
        }
        clearChildren();
        if (backgroundTextureKey != null) {
            Textures.INSTANCE.unload(backgroundTextureKey);
        }
        backgroundTextureKey = null;
        bg = null;

        scroller.setCamY(0);
        scroller.setVelocityY(0);
        selectedItem = null;
        items = new ArrayList<>();
        selectedBeatmap = null;
        SongMenuPool.getInstance().init();
        loadFilterFragment();
        updateMusicEffects();

        backgroundLayer = new UIContainer();
        backgroundLayer.setWidth(DimensionKt.fill(1f));
        backgroundLayer.setHeight(DimensionKt.fill(1f));
        backgroundBox = new UIBox();
        backgroundBox.setWidth(DimensionKt.fill(1f));
        backgroundBox.setHeight(DimensionKt.fill(1f));
        backgroundLayer.plusAssign(backgroundBox);
        backgroundSprite = null;

        // The legacy scene registered the online panel's touch area only outside multiplayer.
        backLayer = new ListLayer();
        frontLayer = new ListLayer(true, child -> child != onlinePanel || !Multiplayer.isMultiplayer);

        plusAssign(backgroundLayer);
        plusAssign(backLayer);
        plusAssign(frontLayer);

        if (SkinTextures.get("menu-background") != null) {
            showMenuBackground();
        } else {
            setBackgroundColor(70 / 255f, 129 / 255f, 252 / 255f);
        }

        final UIBox bgDimRect = new UIBox();
        bgDimRect.setWidth(DimensionKt.getPx(Config.getRES_WIDTH() * LegacyUnits.getScale()));
        bgDimRect.setHeight(DimensionKt.getPx(Config.getRES_HEIGHT() * LegacyUnits.getScale()));
        bgDimRect.setBackgroundColor(Color4.Companion.getBlack());
        bgDimRect.setAlpha(0.2f);
        backLayer.plusAssign(bgDimRect);

        board = new ScoreBoard(backLayer, this);

//        float oy = 10;
        for (final BeatmapSetInfo i : LibraryManager.getLibrary()) {
            final BeatmapSetItem item = new BeatmapSetItem(this, i);
            items.add(item);
            item.attachTo(backLayer);
//            oy += item.getHeight();
        }
        sortOrder = SortOrder.Title;
        sort();

        scrollbar = null;
        songSelectTop = null;
        scoringSwitcher = null;
        onlinePanel = null;
        OnlineScoring.getInstance().setSongMenuPanel(null);
        emptyText = null;
        appliedScale = 0;
        isLibraryEmpty = items.size() == 0;

        if (items.size() == 0) {
            emptyText = new UIText();
            emptyText.setText("There are no songs in library, try using the beatmap downloader.");
            emptyText.setWrapText(false);
            emptyText.setAlignment(Anchor.Center);
            emptyText.setAnchor(Anchor.Center);
            emptyText.setOrigin(Anchor.Center);
            emptyText.setScale(1.5f);
            emptyText.setColor(Color4.Companion.getBlack());
            plusAssign(emptyText);
            return;
        }

        scrollbar = new ScrollBar(this);

        songSelectTop = new UISprite();
        songSelectTop.setScaleType(UISprite.ScaleType.Stretch);
        songSelectTop.setAlpha(0.6f);
        frontLayer.plusAssign(songSelectTop);

        beatmapMetadataText = infoText("title", new UIText());
        beatmapCreatorText = infoText("mapper", new UIText());
        beatmapLengthText = infoText("beatmapInfo", new BeatmapStatisticToggleText());
        beatmapHitObjectsText = infoText("beatmapInfo2", new BeatmapStatisticToggleText());
        beatmapDifficultyText = infoText("dimensionInfo", new BeatmapStatisticToggleText());
        computeInfoTextY();

        var clickShortSound = ResourceManager.getInstance().getSound("click-short");
        var clickShortConfirmSound = ResourceManager.getInstance().getSound("click-short-confirm");

        SkinLayout layoutBackButton = OsuSkin.get().getLayout("BackButton");
        SkinLayout layoutMods = null;

        if (!Multiplayer.isMultiplayer)
            layoutMods = OsuSkin.get().getLayout("ModsButton");

        SkinLayout layoutOptions = OsuSkin.get().getLayout("OptionsButton");
        SkinLayout layoutRandom = OsuSkin.get().getLayout("RandomButton");

        var backButton = new SkinButton("menu-back", null, true);
        backButton.setScaleWhenHold(true);

        if (layoutBackButton != null) {
            backButton.setScaleWhenHold(layoutBackButton.property.optBoolean("scaleWhenHold", true));
        }

        backButton.setScaleOrigin(Anchor.BottomLeft); // Bottom left corner
        backButton.setOnPress(() -> {
            BassSoundProvider playSnd = ResourceManager.getInstance().getSound("menuback");
            if (playSnd != null) {
                playSnd.play();
            }
            return Unit.INSTANCE;
        });
        backButton.setOnRelease(moved -> {
            if (selectedBeatmap != null && !moved) {
                backButton.setScale(1f);
                back();
            }
            return Unit.INSTANCE;
        });
        backButton.setOnCancel(() -> {
            backButton.setScale(1f);
            return Unit.INSTANCE;
        });

        SkinButton modSelection = null;

        if (!Multiplayer.isMultiplayer) {
            modSelection = new SkinButton("selection-mods", "selection-mods-over", false);
            modSelection.setOnRelease(moved -> {
                if (!moved) {
                    scroller.setVelocityY(0);
                    if (clickShortConfirmSound != null) {
                        clickShortConfirmSound.play();
                    }
                    ModMenu.INSTANCE.show();
                }
                return Unit.INSTANCE;
            });
            modSelection.setOnCancel(() -> {
                if (clickShortSound != null) {
                    clickShortSound.play();
                }
                return Unit.INSTANCE;
            });
        }

        var optionSelection = new SkinButton("selection-options", "selection-options-over", false);
        optionSelection.setOnRelease(moved -> {
            if (!moved) {
                scroller.setVelocityY(0);
                if (clickShortConfirmSound != null) {
                    clickShortConfirmSound.play();
                }

                searchBar.loadConfig(context);
                searchBar.showMenu(SongMenu.this);
            }
            return Unit.INSTANCE;
        });
        optionSelection.setOnCancel(() -> {
            if (clickShortSound != null) {
                clickShortSound.play();
            }
            return Unit.INSTANCE;
        });

        var randomMap = new SkinButton("selection-random", "selection-random-over", false);
        randomMap.setOnPress(() -> {
            if (previousSelectionTimer != null) {
                previousSelectionTimer.cancel();
            }

            previousSelectionTimer = new Timer();
            previousSelectionTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    // The timer thread only posts each step, which runs on the update thread while the button is held.
                    Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                        if (LegacyPressTracker.Companion.getCurrentPressedButton() != randomMap) {
                            return null;
                        }

                        BeatmapSetItem previousItem = previousSelectedItems.pollLast();
                        while (previousItem != null && previousItem.isDeleted()) {
                            previousItem = previousSelectedItems.pollLast();
                        }

                        if (previousItem == null) {
                            cancel();
                            return null;
                        }

                        previousSelectionPerformed = true;

                        if (clickShortConfirmSound != null) {
                            clickShortConfirmSound.play();
                        }

                        previousItem.select();
                        return null;
                    }));
                }

                @Override
                public boolean cancel() {
                    previousSelectionTimer = null;
                    return super.cancel();
                }
            }, previousSelectionInterval, previousSelectionInterval);

            previousSelectionPerformed = false;
            return Unit.INSTANCE;
        });
        randomMap.setOnRelease(moved -> {
            if (previousSelectionTimer != null) {
                previousSelectionTimer.cancel();
            }

            if (!moved && !previousSelectionPerformed) {
                scroller.setVelocityY(0);
                if (items.size() <= 1) {
                    return Unit.INSTANCE;
                }
                int rnd = MathUtils.random(0, items.size() - 1);
                int index = 0;
                while (rnd > 0) {
                    rnd--;
                    int oldIndex = index;
                    do {
                        index = (index + 1) % items.size();
                        if (index == oldIndex)
                            return Unit.INSTANCE;
                    } while (!items.get(index).isVisible());
                }
                if (!items.get(index).isVisible()) {
                    return Unit.INSTANCE;
                }
                if (selectedItem == items.get(index)) {
                    return Unit.INSTANCE;
                }
                if (clickShortConfirmSound != null) {
                    clickShortConfirmSound.play();
                }
                items.get(index).select();
            }

            previousSelectionPerformed = false;
            return Unit.INSTANCE;
        });
        randomMap.setOnCancel(() -> {
            if (clickShortSound != null) {
                clickShortSound.play();
            }

            if (previousSelectionTimer != null) {
                previousSelectionTimer.cancel();
            }
            return Unit.INSTANCE;
        });
        randomMap.setOnPressDropped(() -> {
            if (previousSelectionTimer != null) {
                previousSelectionTimer.cancel();
            }
            return Unit.INSTANCE;
        });

        float paddingBottom = Multiplayer.isConnected() ? Multiplayer.roomScene.getChat().getButtonHeight() : 0f;

        // These grow from their top left corner, as the AndEngine sprites did.
        if (modSelection != null) {
            modSelection.setScaleOrigin(Anchor.TopLeft);
            modSelection.setScale(1.5f);
        }

        optionSelection.setScaleOrigin(Anchor.TopLeft);
        optionSelection.setScale(1.5f);
        randomMap.setScaleOrigin(Anchor.TopLeft);
        randomMap.setScale(1.5f);

        var isNewLayout = OsuSkin.get().isUseNewLayout();

        if (isNewLayout && layoutBackButton != null) {
            layoutBackButton.apply(backButton);
        } else {
            backButton.setPosition(0, Config.getRES_HEIGHT() - backButton.getHeightScaled() - paddingBottom);
        }

        if (modSelection != null) {
            if (isNewLayout && layoutMods != null) {
                layoutMods.apply(modSelection, backButton);
            } else {
                modSelection.setPosition(backButton.getLegacyX() + backButton.getWidthScaled(), Config.getRES_HEIGHT() - modSelection.getHeightScaled() - paddingBottom);
            }
        }

        if (isNewLayout && layoutOptions != null) {
            layoutOptions.apply(optionSelection, modSelection != null ? modSelection : backButton);
        } else {
            var prevButton = modSelection != null ? modSelection : backButton;
            optionSelection.setPosition(prevButton.getLegacyX() + prevButton.getWidthScaled(), Config.getRES_HEIGHT() - optionSelection.getHeightScaled() - paddingBottom);
        }

        if (isNewLayout && layoutRandom != null) {
            layoutRandom.apply(randomMap, optionSelection);
        } else {
            randomMap.setPosition(optionSelection.getLegacyX() + optionSelection.getWidthScaled(), Config.getRES_HEIGHT() - randomMap.getHeightScaled() - paddingBottom);
        }

        frontLayer.plusAssign(backButton);

        if (modSelection != null)
        {
            frontLayer.plusAssign(modSelection);
        }
        frontLayer.plusAssign(optionSelection);
        frontLayer.plusAssign(randomMap);

        if (OnlineManager.getInstance().isStayOnline()) {
            OnlinePanel panel = new OnlinePanel();
            panel.setInfo();
            String avatarURL = OnlineManager.getInstance().getAvatarURL();
            panel.setProfile(OnlineScoring.getInstance().isAvatarLoaded() && avatarURL != null && !avatarURL.isEmpty() ? avatarURL : null);
            OnlineScoring.getInstance().setSongMenuPanel(panel);
            panel.setPosition(randomMap.getLegacyX() + randomMap.getWidthScaled() + 20, Config.getRES_HEIGHT() - 110 - paddingBottom);
            OnlineScoring.getInstance().loadProfileAssets(false);
            frontLayer.plusAssign(panel);
            onlinePanel = panel;

            scoringSwitcher = new UISprite() {
                @Override
                public boolean onInputEvent(InputEvent event) {
                    if (!(event instanceof PointerEvent pointerEvent) || pointerEvent.getAction() != PointerEvent.Action.Down) return false;
                    toggleScoringSwitcher();
                    return true;
                }
            };
            scoringSwitcher.setScaleType(UISprite.ScaleType.Stretch);
            scoringSwitcherTexture = "ranking_disabled";
            frontLayer.plusAssign(scoringSwitcher);
        }

        loadTextures();
    }

    private UIText infoText(final String initialText, final UIText text) {
        text.setText(initialText);
        text.setWrapText(false);
        text.setColor(Color4.Companion.getWhite());
        frontLayer.plusAssign(text);
        return text;
    }

    @Override
    protected void onDraw(final GraphicsRenderer renderer) {
        // UIScene.onDraw without the safe-area padding: the legacy scene covers the whole window.
        onPerformLayout();

        final Vec4 clip = renderer.getClipBounds();
        for (final UIComponent child : getChildren()) {
            if (child.intersectsSceneRect(clip.getX(), clip.getY(), clip.getZ(), clip.getW())) {
                onDrawChild(renderer, child);
            }
        }
    }

    @Override
    public void onPerformLayout() {
        final float resWidth = Config.getRES_WIDTH();
        final float resHeight = Config.getRES_HEIGHT();
        final float scale = LegacyUnits.getScale();

        if (backgroundSprite != null) {
            final GraphicsTexture.Region region = backgroundSprite.getTextureRegion();
            if (region != null) {
                final float height = region.getHeight() * (resWidth / region.getWidth());
                backgroundSprite.setX(DimensionKt.getPx(0f));
                backgroundSprite.setY(DimensionKt.getPx((resHeight - height) / 2 * scale));
                setLegacySize(backgroundSprite, resWidth, height, resWidth, resHeight);
            }
        }

        final boolean scaleChanged = appliedScale != scale;
        appliedScale = scale;

        if (scaleChanged && emptyText != null) {
            emptyText.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize("CaptionFont") * scale));
        }

        if (songSelectTop != null) {
            final GraphicsTexture.Region region = songSelectTop.getTextureRegion();
            if (scaleChanged || region != appliedTopRegion) {
                appliedTopRegion = region;
                setLegacySize(songSelectTop, (region != null ? region.getWidth() : 0) * (region != null ? region.getHeight() : 0) / 184f, 184f, resWidth, resHeight);
                songSelectTop.setX(DimensionKt.getPx(-1640f * scale));
            }
        }

        if (!isLibraryEmpty && beatmapDifficultyText != null) {
            layoutInfoTexts();
        }

        if (scoringSwitcher != null) {
            final GraphicsTexture.Region region = scoringSwitcher.getTextureRegion();
            scoringSwitcher.setX(DimensionKt.getPx(10f * scale));
            scoringSwitcher.setY(DimensionKt.getPx(10f * scale));
            scoringSwitcher.setWidth(DimensionKt.getPx((region != null ? region.getWidth() : 0) * scale));
            scoringSwitcher.setHeight(DimensionKt.getPx((region != null ? region.getHeight() : 0) * scale));
        }

        super.onPerformLayout();
    }

    /**
     * Sizes a sprite in virtual units. A child is measured at most as large as the window, so a
     * larger sprite is measured at a fraction of its size and stretched back by its scale.
     */
    private static void setLegacySize(final UISprite sprite, final float width, final float height, final float resWidth, final float resHeight) {
        final float stretchX = Math.max(1f, (float) Math.ceil(width / resWidth));
        final float stretchY = Math.max(1f, (float) Math.ceil(height / resHeight));
        final float scale = LegacyUnits.getScale();
        sprite.setScaleOrigin(Anchor.TopLeft);
        sprite.setScaleX(stretchX);
        sprite.setScaleY(stretchY);
        sprite.setWidth(DimensionKt.getPx(width / stretchX * scale));
        sprite.setHeight(DimensionKt.getPx(height / stretchY * scale));
    }

    private void layoutInfoTexts() {
        placeInfoText(beatmapMetadataText, 70, infoTextY[0], "font");
        placeInfoText(beatmapCreatorText, 70, infoTextY[1], "middleFont");
        placeInfoText(beatmapLengthText, 4, infoTextY[2], "middleFont");
        placeInfoText(beatmapHitObjectsText, 4, infoTextY[3], "middleFont");
        placeInfoText(beatmapDifficultyText, 4, infoTextY[4], "smallFont");
    }

    private static void placeInfoText(final UIText text, final int x, final float y, final String fontName) {
        final float scale = LegacyUnits.getScale();
        text.setFontSize(DimensionKt.getPx(LegacyUnits.fontSize(fontName) * scale));
        text.setX(DimensionKt.getPx(Utils.toRes(x) * scale));
        text.setY(DimensionKt.getPx(y * scale));
    }

    /**
     * The legacy texts were placed below each other from the line heights of their AndEngine fonts,
     * which are kept so the texts start where they did.
     */
    private void computeInfoTextY() {
        float y = Utils.toRes(2f);
        final String[] fontNames = { "font", "middleFont", "middleFont", "middleFont", "smallFont" };

        for (int i = 0; i < fontNames.length; i++) {
            infoTextY[i] = y;
            y += ResourceManager.getInstance().getFont(fontNames[i]).getLineHeight() + Utils.toRes(2f);
        }
    }

    public void loadFilterFragment() {
        searchBar = new SearchBarFragment();
        searchBar.loadConfig(context);
    }

    public void toggleScoringSwitcher() {
        if (board.isShowOnlineScores()) {
            switch (Config.getBeatmapLeaderboardScoringMode()) {
                case SCORE -> Config.setBeatmapLeaderboardScoringMode(BeatmapLeaderboardScoringMode.PP);
                case PP -> board.setShowOnlineScores(false);
            }

            board.init(selectedBeatmap);
        } else if (OnlineManager.getInstance().isStayOnline()) {
            board.setShowOnlineScores(true);
            Config.setBeatmapLeaderboardScoringMode(BeatmapLeaderboardScoringMode.SCORE);
            board.init(selectedBeatmap);
        }

        updateScoringSwitcherStatus(true);
    }

    public boolean isShown() {
        return Verktex.INSTANCE.getScene() == this;
    }
    public SearchBarFragment getSearchBar() { return searchBar; }

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

        if (previousSelectionTimer != null) {
            previousSelectionTimer.cancel();
        }
    }

    @Override
    public void onSkinChanged() {
        texturesInvalid = true;
    }

    private void loadTextures() {
        // Loaded internally by the legacy ResourceManager, a skin cannot override them.
        if (songSelectTop != null) {
            songSelectTop.setTextureRegion(SkinTextures.getAsset("songselect-top", "songselect-top.png"));
        }
        if (scoringSwitcher != null) {
            scoringSwitcher.setTextureRegion(SkinTextures.getAsset(scoringSwitcherTexture, scoringSwitcherTexture + ".png"));
        }

        if (isMenuBackground && backgroundSprite != null) {
            backgroundSprite.setTextureRegion(SkinTextures.get("menu-background"));
        }
    }

    public void setFilter(final String filter, final SortOrder order,
                          final boolean favsOnly, @Nullable List<String> limit) {
        Verktex.INSTANCE.withTreeLock(() -> {
            setFilterLocked(filter, order, favsOnly, limit);
            return null;
        });
    }

    private void setFilterLocked(final String filter, final SortOrder order,
                                 final boolean favsOnly, @Nullable List<String> limit) {
        VerktexBridge.checkUpdateThread();

        String beatmapFilename = "";
        if (selectedBeatmap != null) {
            beatmapFilename = selectedBeatmap.getFilename();
        }
        if (!order.equals(sortOrder)) {
            sortOrder = order;
            tryReloadMenuItems(sortOrder);
            sort();
            reSelectItem(beatmapFilename);
        }
        if (filter == null || filterText.equals(filter)) {
            if (favsOnly == this.favsOnly && limitC == limit) {
                return;
            }
        }
        limitC = limit;
        filterText = filter;
        scroller.setCamY(0);
        scroller.setVelocityY(0);
        final String lowerFilter = filter.toLowerCase();
        for (final BeatmapSetItem item : items) {
            item.applyFilter(lowerFilter, favsOnly, limit);
        }
        if (favsOnly != this.favsOnly) {
            this.favsOnly = favsOnly;
        } else {
            reSelectItem(beatmapFilename);
        }
        if (selectedItem != null && !selectedItem.isVisible()) {
            selectedItem = null;
        }
    }

    public void sort() {
        if (!sortOrder.equals(searchBar.getOrder())) {
            sortOrder = searchBar.getOrder();
        }
        Collections.sort(items, (i1, i2) -> {
            String s1;
            String s2;
            switch (sortOrder) {
                case Artist:
                    s1 = i1.getFirstBeatmap().getArtist();
                    s2 = i2.getFirstBeatmap().getArtist();
                    break;
                case Creator:
                    s1 = i1.getFirstBeatmap().getCreator();
                    s2 = i2.getFirstBeatmap().getCreator();
                    break;
                case Date:
                    final Long int1 = i1.getFirstBeatmap().getDateImported();
                    final Long int2 = i2.getFirstBeatmap().getDateImported();
                    return int2.compareTo(int1);
                case Bpm:
                    final float bpm1 = i1.getFirstBeatmap().getBpmMax();
                    final float bpm2 = i2.getFirstBeatmap().getBpmMax();
                    return Float.compare(bpm2, bpm1);
                case DroidStars:
                    final float droid1 = i1.getFirstBeatmap().getStarRating(DifficultyAlgorithm.droid);
                    final float droid2 = i2.getFirstBeatmap().getStarRating(DifficultyAlgorithm.droid);
                    return Float.compare(droid2, droid1);
                case StandardStars:
                    final float standard1 = i1.getFirstBeatmap().getStarRating(DifficultyAlgorithm.standard);
                    final float standard2 = i2.getFirstBeatmap().getStarRating(DifficultyAlgorithm.standard);
                    return Float.compare(standard2, standard1);
                case Length:
                    final Long length1 = i1.getFirstBeatmap().getLength();
                    final Long length2 = i2.getFirstBeatmap().getLength();
                    return length2.compareTo(length1);
                default:
                    s1 = i1.getFirstBeatmap().getTitle();
                    s2 = i2.getFirstBeatmap().getTitle();
            }

            return s1.compareToIgnoreCase(s2);
        });
    }

    @Override
    protected void onUpdate(final TickingClock clock) {
        if (texturesInvalid) {
            texturesInvalid = false;
            loadTextures();
        }

        // The mod menu was a modal child scene in AndEngine, which paused this scene.
        if (EngineRouter.getHasShownOverlay()) {
            return;
        }

        // The legacy scene registered its update handler only with a non-empty library.
        if (!isLibraryEmpty) {
            onUpdate(clock.getElapsedTickTime());
        }

        super.onUpdate(clock);
    }

    public void onUpdate(final float pSecondsElapsed) {
        secPassed += pSecondsElapsed;
        increaseBackgroundLuminance(pSecondsElapsed);

        if (Config.isPlayMusicPreview()) {
            var songService = GlobalManager.getInstance().getSongService();
            if (allowAutomaticPlaybackRestart && songService != null) {
                int length = songService.getLength();

                // We want to restart playback.
                // Checking for 0 length here sounds counterintuitive, but BASS returns -1 for streams with finished
                // playback, even without automatic freeing flag. Weird...
                if (selectedBeatmap != null && length == 0) {
                    allowAutomaticPlaybackRestart = false;
                    playMusic(selectedBeatmap.getAudioPath(), selectedBeatmap.getPreviewTime());
                }
            }
        }

        float oy = -scroller.getCamY();
        for (final BeatmapSetItem item : items) {
            final float cy = oy + Config.getRES_HEIGHT() / 2f + item.getHeight()
                    / 2;
            float ox = Config.getRES_WIDTH() / 1.85f + 200 * (float) Math.abs(Math.cos(cy * Math.PI
                    / (Config.getRES_HEIGHT() * 2)));
            ox = Utils.toRes(ox);
            item.setPos(ox, oy);
            oy += item.getHeight();
        }
        oy += scroller.getCamY();
        scroller.update(pSecondsElapsed, oy);

        expandSelectedItem(pSecondsElapsed);

        updateScrollbar(scroller.getCamY() + Config.getRES_HEIGHT() / 2f, oy);
    }

    private void setBackgroundColor(final float red, final float green, final float blue) {
        backgroundBox.setBackgroundColor(new Color4(red, green, blue, 1f));
        if (backgroundSprite != null) {
            backgroundSprite.removeSelf();
        }
        backgroundSprite = null;
        isMenuBackground = false;
    }

    private void showMenuBackground() {
        showBackgroundSprite(SkinTextures.get("menu-background"));
        isMenuBackground = true;
    }

    private UISprite showBackgroundSprite(final GraphicsTexture.Region region) {
        if (backgroundSprite != null) {
            backgroundSprite.removeSelf();
        }

        final UISprite sprite = new UISprite(region);
        sprite.setScaleType(UISprite.ScaleType.Stretch);
        backgroundSprite = sprite;
        isMenuBackground = false;
        backgroundLayer.plusAssign(sprite);
        return sprite;
    }

    public void increaseBackgroundLuminance(final float pSecondsElapsed) {
        if (bg != null && bg.getRed() < 1) {
            final float col = Math.min(1, bg.getRed() + pSecondsElapsed);
            bg.setColor(new Color4(col, col, col, bg.getAlpha()));
        }
    }

    /**
     * Hands the shown beatmap background to AndEngine's {@code ::background}, as the legacy menu loaded it
     * there when it was selected. Must be called on the update thread.
     */
    public void handOverBackground() {
        VerktexBridge.checkUpdateThread();

        final String path = handOverPath;
        if (path == null) {
            return;
        }
        final Bitmap bitmap = currentBackgroundBitmap;

        // Without a kept bitmap AndEngine decodes the file itself, as the legacy menu did.
        if (bitmap != null) {
            ResourceManager.getInstance().loadBackground(path, bitmap);
        } else {
            ResourceManager.getInstance().loadBackground(path);
        }
    }

    public void expandSelectedItem(float pSecondsElapsed) {
        if (selectedItem != null) {
            if (selectedItem.percentAppeared < 1) {
                selectedItem.percentAppeared += 2 * pSecondsElapsed;
            } else {
                selectedItem.percentAppeared = 1;
            }
            selectedItem.update(pSecondsElapsed);
        }
    }

    public void updateScrollbar(final float vy, final float maxy) {
        if (scrollbar == null) {
            return;
        }
        scrollbar.setPosition(vy, maxy);
        scrollbar.setVisible(scroller.isScrollbarVisible());
    }

    @Override
    public boolean onInputEvent(final InputEvent event) {
        // Keys arrive on the UI thread.
        if (event instanceof KeyboardEvent keyboardEvent && keyboardEvent.getAction() == KeyboardEvent.Action.Down) {
            switch (keyboardEvent.getKey()) {
                case Back -> {
                    Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                        back();
                        return null;
                    }));
                    return true;
                }
                case Menu -> {
                    Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                        stopScroll(0f);
                        showPropertiesMenu(null);
                        return null;
                    }));
                    return true;
                }
                default -> {
                }
            }
        }

        if (!(event instanceof PointerEvent pointerEvent)) {
            return super.onInputEvent(event);
        }

        // The legacy scene asked its touch areas first: the front layer was registered before the cards.
        if (frontLayer.onInputEvent(event) || backLayer.onInputEvent(event)) {
            return true;
        }

        // Without songs the legacy scene had no touch listener.
        if (isLibraryEmpty) {
            return false;
        }

        final float x = pointerEvent.getX() / LegacyUnits.getScale();
        if (!scroller.acceptsX(x)) {
            return false;
        }

        final float y = pointerEvent.getY() / LegacyUnits.getScale();
        switch (pointerEvent.getAction()) {
            case Down -> scroller.onDown(pointerEvent.getPointerId(), y, secPassed);
            case Move -> scroller.onMove(pointerEvent.getPointerId(), y, secPassed);
            default -> scroller.onUp(pointerEvent.getPointerId(), y, secPassed);
        }
        return true;
    }

    public void select(final BeatmapSetItem item) {
        VerktexBridge.checkUpdateThread();

        if (selectedItem != null) {
            selectedItem.deselect();

            if (!previousSelectionPerformed) {
                while (previousSelectedItems.size() >= 10) {
                    previousSelectedItems.pollFirst();
                }
                previousSelectedItems.addLast(selectedItem);
            }
        }

        selectedItem = item;
        selectedBeatmap = null;
        float height = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == selectedItem) {
                break;
            }
            height += items.get(i).getInitialHeight();
        }
        scroller.centerOn(height, item.getTotalHeight());
    }

    @SuppressLint("SimpleDateFormat")
    public synchronized void changeDimensionInfo(BeatmapInfo beatmapInfo) {
        Verktex.INSTANCE.withTreeLock(() -> {
            changeDimensionInfoLocked(beatmapInfo);
            return null;
        });
    }

    private void changeDimensionInfoLocked(BeatmapInfo beatmapInfo) {
        VerktexBridge.checkUpdateThread();

        if (beatmapInfo == null) {
            return;
        }

        var mods = ModMenu.INSTANCE.getEnabledMods();
        boolean isPreciseMod = mods.contains(ModPrecise.class);
        float totalSpeedMultiplier = ModUtils.calculateRate(mods.values(), Double.POSITIVE_INFINITY);

        var difficulty = beatmapInfo.getBeatmapDifficulty().clone();
        ModUtils.applyModsToBeatmapDifficulty(difficulty, GameMode.Droid, mods.values(), true);

        // Round to 2 decimal places.
        difficulty.gameplayCS = GameHelper.Round(difficulty.gameplayCS, 2);
        difficulty.setAR(GameHelper.Round(difficulty.getAR(), 2));
        difficulty.od = GameHelper.Round(difficulty.od, 2);
        difficulty.hp = GameHelper.Round(difficulty.hp, 2);

        // Update texts
        float originalCS = beatmapInfo.getCircleSize();
        float originalAR = beatmapInfo.getApproachRate();
        float originalOD = beatmapInfo.getOverallDifficulty();
        float originalHP = beatmapInfo.getHpDrainRate();

        beatmapDifficultyText.setColor(new Color4(1f, 1f, 1f, 1f));
        beatmapLengthText.setColor(new Color4(1f, 1f, 1f, 1f));

        if (mods.contains(ModDifficultyAdjust.class)) {
            if (isPreciseMod) {
                beatmapDifficultyText.setColor(new Color4(1f, 120 / 255f, 0f, 1f));
            } else {
                beatmapDifficultyText.setColor(new Color4(1f, 180 / 255f, 0f, 1f));
            }
        } else if (
            (!Precision.almostEquals(originalCS, difficulty.gameplayCS) && originalCS < difficulty.gameplayCS) ||
            (!Precision.almostEquals(originalAR, difficulty.getAR()) && originalAR < difficulty.getAR()) ||
            (!Precision.almostEquals(originalOD, difficulty.od) && originalOD < difficulty.od) ||
            (!Precision.almostEquals(originalOD, difficulty.hp) && originalHP < difficulty.hp)
        ) {
            if (isPreciseMod) {
                beatmapDifficultyText.setColor(new Color4(214 / 255f, 45 / 255f, 45 / 255f, 1f));
            } else {
                beatmapDifficultyText.setColor(new Color4(205 / 255f, 85 / 255f, 85 / 255f, 1f));
            }
        } else if (
            (!Precision.almostEquals(originalCS, difficulty.gameplayCS) && originalCS > difficulty.gameplayCS) ||
            (!Precision.almostEquals(originalAR, difficulty.getAR()) && originalAR > difficulty.getAR()) ||
            (!Precision.almostEquals(originalOD, difficulty.od) && originalOD > difficulty.od) ||
            (!Precision.almostEquals(originalOD, difficulty.hp) && originalHP > difficulty.hp)
        ) {
            if (isPreciseMod) {
                beatmapDifficultyText.setColor(new Color4(158 / 255f, 108 / 255f, 65 / 255f, 1f));
            } else {
                beatmapDifficultyText.setColor(new Color4(46 / 255f, 139 / 255f, 87 / 255f, 1f));
            }
        } else if (isPreciseMod) {
            beatmapDifficultyText.setColor(new Color4(205 / 255f, 85 / 255f, 85 / 255f, 1f));
        }

        if (totalSpeedMultiplier > 1) {
            beatmapLengthText.setColor(new Color4(205 / 255f, 85 / 255f, 85 / 255f, 1f));
        } else if (totalSpeedMultiplier < 1) {
            beatmapLengthText.setColor(new Color4(46 / 255f, 139 / 255f, 87 / 255f, 1f));
        }

        int minBpm = Math.round(beatmapInfo.getBpmMin() * totalSpeedMultiplier);
        int maxBpm = Math.round(beatmapInfo.getBpmMax() * totalSpeedMultiplier);
        int commonBpm = Math.round(beatmapInfo.getMostCommonBPM() * totalSpeedMultiplier);
        long length = (long) (beatmapInfo.getLength() / totalSpeedMultiplier);

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat(length > 3600 * 1000 ? "HH:mm:ss" : "mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String binfoStr = String.format(StringTable.get(com.osudroid.resources.R.string.binfoStr1), sdf.format(length),
                (minBpm == maxBpm ? commonBpm : minBpm + "-" + maxBpm + " (" + commonBpm + ")"),
                beatmapInfo.getMaxCombo());

        beatmapLengthText.setText(binfoStr);

        String str = beatmapDifficultyText.getText().toString();
        String[] strs = str.split("Stars: ");

        beatmapDifficultyText.setText(
            "AR: " + difficulty.getAR() + " " +
            "OD: " + difficulty.od + " " +
            "CS: " + difficulty.gameplayCS + " " +
            "HP: " + difficulty.hp + " " +
            "Stars: " + (strs.length == 2 ? strs[1] : GameHelper.Round(beatmapInfo.getStarRating(), 2))
        );
    }

    public void reloadCurrentSelection() {
        updateInfo(selectedBeatmap);

        if (selectedItem != null) {
            selectedItem.reloadBeatmaps();
        }
    }

    public synchronized void updateInfo(BeatmapInfo beatmapInfo) {
        Verktex.INSTANCE.withTreeLock(() -> {
            updateInfoLocked(beatmapInfo);
            return null;
        });
    }

    private void updateInfoLocked(BeatmapInfo beatmapInfo) {
        VerktexBridge.checkUpdateThread();

        if (beatmapInfo == null) {
            return;
        }

        String tinfoStr = beatmapInfo.getArtistText() + " - " + beatmapInfo.getTitleText() + " [" + beatmapInfo.getVersion() + "]";
        String mapperStr = "Beatmap by " + beatmapInfo.getCreator();
        String binfoStr2 = StringTable.format(com.osudroid.resources.R.string.binfoStr2,
                beatmapInfo.getHitCircleCount(),
                beatmapInfo.getSliderCount(),
                beatmapInfo.getSpinnerCount(),
                beatmapInfo.getSetId()
        );
        beatmapMetadataText.setText(tinfoStr);
        beatmapCreatorText.setText(mapperStr);
        beatmapHitObjectsText.setText(binfoStr2);
        changeDimensionInfo(beatmapInfo);
        setStarsDisplay(beatmapInfo.getStarRating());
        cancelCalculationJobs();

        calculationJob = Execution.async(scope -> {
            try {
                var mode = switch (Config.getDifficultyAlgorithm()) {
                    case droid -> GameMode.Droid;
                    case standard -> GameMode.Standard;
                };

                var beatmap = BeatmapCache.getBeatmap(beatmapInfo, true, mode, scope);

                // Do not update if the beatmap has been changed.
                var selected = selectedBeatmap;
                if (selected != null && !beatmap.getMd5().equals(selected.getMD5())) {
                    return;
                }

                var newInfo = BeatmapInfo(beatmap, beatmapInfo.getDateImported(), true, scope);
                beatmapInfo.apply(newInfo);
                DatabaseManager.getBeatmapInfoTable().update(newInfo);

                Execution.updateThread(() -> {
                    // A newer selection may have been made meanwhile.
                    if (selectedBeatmap == beatmapInfo) {
                        changeDimensionInfo(beatmapInfo);
                    }
                });

                // Copy the mods to avoid concurrent modification
                var mods = ModMenu.INSTANCE.getEnabledMods().deepCopy().values();

                var attributes = switch (Config.getDifficultyAlgorithm()) {
                    case droid -> BeatmapDifficultyCalculator.calculateDroidDifficulty(beatmap, mods, scope);
                    case standard -> BeatmapDifficultyCalculator.calculateStandardDifficulty(beatmap, mods, scope);
                };

                setStarsDisplayIfSelected(beatmapInfo, (float) attributes.starRating);
            } catch (IOException | IllegalArgumentException e) {
                Log.e("SongMenu", "Unable to calculate star rating", e);
                setStarsDisplayIfSelected(beatmapInfo, 0);
            }
        });
    }

    private void setStarsDisplayIfSelected(final BeatmapInfo beatmapInfo, final float star) {
        Execution.updateThread(() -> {
            if (selectedBeatmap == beatmapInfo) {
                setStarsDisplay(star);
            }
        });
    }

    @Override
    public void selectBeatmap(final BeatmapInfo beatmapInfo, boolean reloadBG) {
        Verktex.INSTANCE.withTreeLock(() -> {
            selectBeatmapLocked(beatmapInfo, reloadBG);
            return null;
        });
    }

    private void selectBeatmapLocked(final BeatmapInfo beatmapInfo, boolean reloadBG) {
        VerktexBridge.checkUpdateThread();

        // Playing corresponding audio for the selected track.
        var selectedAudio = selectedBeatmap != null ? selectedBeatmap : GlobalManager.getInstance().getSelectedBeatmap();

        if (selectedAudio == null || !Objects.equals(selectedAudio.getAudioPath(), beatmapInfo.getAudioPath())) {
            playMusic(beatmapInfo.getAudioPath(), beatmapInfo.getPreviewTime());
        }

        if (selectedBeatmap != null && selectedBeatmap.getFilename().equals(beatmapInfo.getFilename())) {
            // Do not initiate gameplay if the player is still holding one of the menu buttons.
            if (LegacyPressTracker.Companion.getCurrentPressedButton() != null) {
                return;
            }

            BassSoundProvider menuHitSound = ResourceManager.getInstance().getSound("menuhit");
            if (menuHitSound != null) {
                menuHitSound.play();
            }
            cancelCalculationJobs();
            cancelMapStatusLoadingJob();

            if (Multiplayer.isMultiplayer)
            {
                setMultiplayerRoomBeatmap(selectedBeatmap);
                back(false);
                return;
            }
            stopMusic();

            Execution.mainThread(() -> {
                ModMenu.INSTANCE.back();
                if (searchBar != null) {
                    searchBar.dismiss();
                }
            });

            handOverBackground();
            game.startGame(beatmapInfo, null, ModMenu.INSTANCE.getEnabledMods());
            return;
        }
        selectedBeatmap = beatmapInfo;
        GlobalManager.getInstance().setSelectedBeatmap(beatmapInfo);
        cancelCalculationJobs();
        cancelMapStatusLoadingJob();
        updateInfo(beatmapInfo);
        updateScoringSwitcherStatus(false);
        board.init(beatmapInfo);

        if (!reloadBG && (beatmapInfo.getBackgroundFilename() == null || backgroundPath.equals(beatmapInfo.getBackgroundPath()))) {
            return;
        }
        backgroundPath = beatmapInfo.getBackgroundPath();
        bg = null;
        setBackgroundColor(0, 0, 0);

        if (backgroundLoadingJob != null) {
            backgroundLoadingJob.cancel(new CancellationException("Background loading has been cancelled."));
        }

        final int generation = ++backgroundGeneration;
        final String path = backgroundPath;
        final boolean fromFile = !Config.isSafeBeatmapBg() && beatmapInfo.getBackgroundFilename() != null;

        backgroundLoadingJob = Execution.async(scope -> {
            JobKt.ensureActive(scope.getCoroutineContext());

            final GraphicsTexture.Source source = fromFile ? SkinTextures.decode(new File(path)) : null;

            JobKt.ensureActive(scope.getCoroutineContext());

            Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                // A newer selection started loading its background meanwhile.
                if (generation != backgroundGeneration) {
                    return null;
                }

                if (selectedBeatmap != null && !selectedBeatmap.getFilename().equals(beatmapInfo.getFilename())) {
                    return null;
                }

                onBackgroundLoaded(source, fromFile, path, generation);
                return null;
            }));
        });
    }

    private void onBackgroundLoaded(@Nullable final GraphicsTexture.Source source, final boolean fromFile,
                                    final String path, final int generation) {
        final String key = "::background#" + generation;

        GraphicsTexture.Region fileRegion = null;
        if (source != null) {
            try {
                fileRegion = SkinTextures.register(key, source, true);
            } catch (Exception e) {
                fileRegion = null;
            }
        }

        // The legacy loadBackground fell back to menu-background when the file did not decode.
        final GraphicsTexture.Region region = fileRegion != null ? fileRegion : SkinTextures.get("menu-background");

        if (fromFile) {
            handOverPath = path;
            // A decode Verktex could not register (above maxTextureSize) is not kept alive for the hand-over.
            currentBackgroundBitmap = fileRegion != null && source instanceof AndroidTextureSource androidSource ? androidSource.getBitmap() : null;
        }

        if (region == null) {
            backgroundPath = "";
            return;
        }

        final String previousKey = backgroundTextureKey;
        backgroundTextureKey = fileRegion != null ? key : null;

        final UISprite sprite = showBackgroundSprite(region);
        sprite.setColor(new Color4(0f, 0f, 0f, 1f));
        bg = sprite;

        if (previousKey != null) {
            Textures.INSTANCE.unload(previousKey);
        }
    }

    public void stopScroll(final float y) {
        scroller.stopScroll(y);
    }

    public void updateScore() {
        VerktexBridge.checkUpdateThread();

        Verktex.INSTANCE.withTreeLock(() -> {
            board.init(selectedBeatmap);
            if (selectedItem != null) {
                selectedItem.updateMarks();
            }
            return null;
        });
    }

    public void openScore(final int id, boolean showOnline, final String playerName) {
        VerktexBridge.checkUpdateThread();

        var difficulty = selectedBeatmap.getBeatmapDifficulty();

        if (showOnline) {
            LoadingScreen.show();
            ToastLogger.showText(com.osudroid.resources.R.string.online_loadrecord, false);

            cancelCalculationJobs();
            cancelMapStatusLoadingJob();

            Execution.async(() -> {
                try {
                    String scorePack = OnlineManager.getInstance().getScorePack(id);
                    String[] params = scorePack.split("\\s+");

                    if (params.length < 11) {
                        return;
                    }

                    boolean hasReplay = params[10].equals("1");
                    StatisticV2 stat = new StatisticV2(params, difficulty);

                    stat.setPlayerName(playerName);
                    Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                        try {
                            handOverBackground();
                            scoreScene.load(stat, null, null, hasReplay ? OnlineManager.getReplayURL(id) : null, null, selectedBeatmap);
                            scoreScene.show();
                        } catch (Exception e) {
                            Debug.e("Cannot load play info: " + e.getMessage(), e);
                            show();
                        }
                        return null;
                    }));
                } catch (Exception e) {
                    Debug.e("Cannot load play info: " + e.getMessage(), e);
                    Execution.updateThread(this::show);
                }
            });
            return;
        }

        var score = DatabaseManager.getScoreInfoTable().getScore(id);

        if (score == null) {
            ToastLogger.showText("Could not open score", true);
            return;
        }

        StatisticV2 stat;

        try {
            stat = score.toStatisticV2(difficulty);
        } catch (IllegalArgumentException e1) {
            // When this happens, the mods are likely in the old format (that somehow was not converted during
            // migration). Convert them.
            var convertedMods = LegacyModConverter.convert(score.getMods());

            // Scores that are using the legacy mods format are guaranteed to use these mods.
            convertedMods.put(new ModReplayV6());

            score.setMods(convertedMods.serializeMods());
            DatabaseManager.getScoreInfoTable().updateScore(score);

            try {
                stat = score.toStatisticV2(difficulty);
            } catch (IllegalArgumentException e2) {
                Debug.e("Cannot not open score after mod conversion: " + e2.getMessage(), e2);
                ToastLogger.showText("Could not open score", true);
                return;
            }
        }

        // Since the statistics will be parsed in ScoringScene.load, we take the chance to update the score with its
        // complete statistics.
        boolean scoreNeedsUpdate =
            stat.getSliderHeadHits() == -1 ||
            stat.getSliderTickHits() == -1 ||
            stat.getSliderRepeatHits() == -1 ||
            stat.getSliderEndHits() == -1;

        var replayFile = new File(Config.getScorePath() + stat.getReplayFilename());

        handOverBackground();
        scoreScene.load(stat, null, null, replayFile.exists() ? replayFile.getAbsolutePath() : null, null, selectedBeatmap);

        if (scoreNeedsUpdate) {
            score.setSliderHeadHits(stat.getSliderHeadHits() == -1 ? null : stat.getSliderHeadHits());
            score.setSliderTickHits(stat.getSliderTickHits() == -1 ? null : stat.getSliderTickHits());
            score.setSliderRepeatHits(stat.getSliderRepeatHits() == -1 ? null : stat.getSliderRepeatHits());
            score.setSliderEndHits(stat.getSliderEndHits() == -1 ? null : stat.getSliderEndHits());

            DatabaseManager.getScoreInfoTable().updateScore(score);
        }

        scoreScene.show();
    }


    public void onScroll(final float where) {
        scroller.setVelocityY(0);
        scroller.setCamY(where - Config.getRES_HEIGHT() / 2f);
    }

    public void back() {
        back(true);
    }

    private void back(boolean resetMultiplayerBeatmap) {
        VerktexBridge.checkUpdateThread();

        if (Multiplayer.isMultiplayer) {
            if (resetMultiplayerBeatmap) {
                resetMultiplayerRoomBeatmap();
            }

            // The room shows the background AndEngine holds.
            handOverBackground();
            Multiplayer.roomScene.show();
            return;
        }

        resetMusicEffects();
        startMusicVolumeAnimation(0.5f);
        GlobalManager.getInstance().getMainMenuV2().show();
    }

    private void resetMultiplayerRoomBeatmap() {
        if (!Multiplayer.isMultiplayer) {
            return;
        }

        // Locking host from change beatmap before the server responses to beatmapChange
        Multiplayer.roomScene.isWaitingForBeatmapChange.set(true);

        if (!Multiplayer.isConnected()) {
            return;
        }

        // Now we update the beatmap
        if (Multiplayer.room != null && Multiplayer.room.getPreviousBeatmap() != null) {
            var beatmap = Multiplayer.room.getPreviousBeatmap();

            RoomAPI.changeBeatmap(
                    beatmap.getMd5(),
                    beatmap.getTitle(),
                    beatmap.getArtist(),
                    beatmap.getVersion(),
                    beatmap.getCreator()
            );
        } else {
            RoomAPI.changeBeatmap();
        }
    }

    private void setMultiplayerRoomBeatmap(BeatmapInfo beatmapInfo) {
        if (!Multiplayer.isMultiplayer) {
            return;
        }

        // Locking host from change beatmap before the server responses to beatmapChange
        Multiplayer.roomScene.isWaitingForBeatmapChange.set(true);

        if (!Multiplayer.isConnected()) {
            return;
        }

        // Now we update the beatmap
        if (beatmapInfo != null) {
            RoomAPI.changeBeatmap(
                    beatmapInfo.getMD5(),
                    beatmapInfo.getTitle(),
                    beatmapInfo.getArtist(),
                    beatmapInfo.getVersion(),
                    beatmapInfo.getCreator()
            );
        } else {
            RoomAPI.changeBeatmap();
        }
    }

    public void reloadScoreboard() {
        VerktexBridge.checkUpdateThread();

        if (!isShown()) {
            return;
        }

        Verktex.INSTANCE.withTreeLock(() -> {
            board.init(selectedBeatmap);
            return null;
        });
    }

    public void setY(final float y) {
        scroller.setVelocityY(0);
        scroller.setCamY(y);
    }

    public void startMusicVolumeAnimation() {
        startMusicVolumeAnimation(0, 1);
    }

    public void startMusicVolumeAnimation(float initialVolume) {
        startMusicVolumeAnimation(initialVolume, 1);
    }

    public void startMusicVolumeAnimation(float initialVolume, float endVolume) {
        stopMusicVolumeAnimation();

        var songService = GlobalManager.getInstance().getSongService();
        if (songService == null || songService.getStatus() == Status.STOPPED) {
            return;
        }

        songService.setVolume(initialVolume * Config.getBgmVolume());

        if (initialVolume == endVolume) {
            return;
        }

        Execution.mainThread(() -> {
            musicVolumeAnimator = ValueAnimator.ofFloat(initialVolume, endVolume);
            // See https://github.com/ppy/osu/blob/790f863e0654fd563b57ab699d6be86895e756ab/osu.Game/Overlays/MusicController.cs#L529-L535
            // for the duration. Since SongService only supports one BASS channel at a time, we cannot apply part of the animation
            // to the previous song.
            musicVolumeAnimator.setDuration((long) (800 * Math.abs(endVolume - initialVolume)));
            musicVolumeAnimator.setInterpolator(input -> EasingKt.interpolate(Easing.Out, input));
            musicVolumeAnimator.addUpdateListener(animation -> {
                var animatorSongService = GlobalManager.getInstance().getSongService();
                if (animatorSongService == null) {
                    return;
                }

                if (animatorSongService.getStatus() == Status.STOPPED) {
                    musicVolumeAnimator.cancel();
                    return;
                }

                animatorSongService.setVolume(animation.getAnimatedFraction() * Config.getBgmVolume());
            });

            musicVolumeAnimator.start();
        });
    }

    public void stopMusicVolumeAnimation() {
        Execution.mainThread(() -> {
            if (musicVolumeAnimator != null) {
                musicVolumeAnimator.cancel();
            }
        });
    }

    public void stopMusic() {
        stopMusicVolumeAnimation();

        var songService = GlobalManager.getInstance().getSongService();

        if (songService != null) {
            GlobalManager.getInstance().getSongService().stop();
        }
    }

    public void playMusic(final String filePath, final int previewTime) {
        if (!Config.isPlayMusicPreview()) {
            return;
        }

        if (musicLoadingJob != null) {
            musicLoadingJob.cancel(new CancellationException("Music loading has been cancelled."));
        }

        stopMusicVolumeAnimation();
        allowAutomaticPlaybackRestart = false;
        musicLoadingJob = Execution.async(scope -> {
            var songService = GlobalManager.getInstance().getSongService();
            if (songService != null) {
                songService.stop();
            }

            try {
                JobKt.ensureActive(scope.getCoroutineContext());

                songService.preLoad(filePath);
                updateMusicEffects();

                JobKt.ensureActive(scope.getCoroutineContext());
                songService.play();
                startMusicVolumeAnimation();
                if (previewTime >= 0) {
                    songService.seekTo(previewTime);
                } else {
                    songService.seekTo((int) (songService.getLength() * 0.4f));
                }

                allowAutomaticPlaybackRestart = true;
            } catch (final Exception e) {
                if (e instanceof CancellationException) {
                    throw e;
                }

                Debug.e("LoadingMusic: " + e.getMessage(), e);
            }
        });
    }

    public void updateMusicEffects() {
        var songService = GlobalManager.getInstance().getSongService();
        if (songService == null) {
            return;
        }

        var enabledMods = ModMenu.INSTANCE.getEnabledMods();
        float speed = ModUtils.calculateRate(enabledMods.values(), Double.POSITIVE_INFINITY);
        boolean adjustPitch = Config.isShiftPitchInRateChange() || enabledMods.contains(ModNightCore.class);

        songService.setSpeed(speed);
        songService.setAdjustPitch(adjustPitch);
    }

    public void resetMusicEffects() {
        var songService = GlobalManager.getInstance().getSongService();
        if (songService == null) {
            return;
        }

        songService.setSpeed(1);
        songService.setAdjustPitch(false);
    }

    public boolean isSelectAllowed() {
        return true;
    }

    public void showPropertiesMenu(BeatmapSetItem item) {
        if (item == null) {
            if (selectedItem == null) {
                return;
            }
            item = selectedItem;
        }
        (new BeatmapPropertiesFragment()).show(SongMenu.this, item);
    }

    public void showDeleteScoreMenu(int scoreId) {
        (new ScoreMenuFragment()).show(selectedBeatmap, scoreId);
    }

    public void select() {
        Verktex.INSTANCE.withTreeLock(() -> {
            VerktexBridge.checkUpdateThread();

            if (GlobalManager.getInstance().getSelectedBeatmap() != null) {
                BeatmapInfo beatmapInfo = GlobalManager.getInstance().getSelectedBeatmap();

                var i = items.size() - 1;
                while (i >= 0) {
                    var item = items.get(i);
                    if (item.getBeatmapSetInfo().getDirectory().equals(beatmapInfo.getSetDirectory())) {
                        item.select();
                        break;
                    }
                    --i;
                }
            }
            return null;
        });
    }

    public BeatmapInfo getSelectedBeatmap() {
        return selectedBeatmap;
    }

    private void tryReloadMenuItems(SortOrder order) {
        switch (order) {
            case Title:
            case Artist:
            case Creator:
            case Date:
            case Bpm:
                reloadMenuItems(GroupType.MapSet);
                break;
            case DroidStars:
            case StandardStars:
            case Length:
                reloadMenuItems(GroupType.SingleDiff);
                break;
        }
    }

    private void reloadMenuItems(GroupType type) {
        if (!groupType.equals(type)) {
            groupType = type;
//            float oy = 10;
            for (BeatmapSetItem item : items) {
                item.removeFromScene();
            }
            items.clear();
            switch (type) {
                case MapSet:
                    for (final BeatmapSetInfo i : LibraryManager.getLibrary()) {
                        final BeatmapSetItem item = new BeatmapSetItem(this, i);
                        items.add(item);
                        item.attachTo(backLayer);
//                        oy += item.getHeight();
                    }
                    break;
                case SingleDiff:
                    for (final BeatmapSetInfo i : LibraryManager.getLibrary()) {
                        for (int j = 0; j < i.getCount(); j++) {
                            final BeatmapSetItem item = new BeatmapSetItem(this, i, j);
                            items.add(item);
                            item.attachTo(backLayer);
//                            oy += item.getHeight();
                        }
                    }
                    break;
            }
            final String lowerFilter = searchBar.getFilter().toLowerCase();
            final boolean favsOnly = searchBar.isFavoritesOnly();

            var limit = DatabaseManager.getBeatmapCollectionsTable().getBeatmaps(searchBar.getFavoriteFolder());
            for (final BeatmapSetItem item : items) {
                item.applyFilter(lowerFilter, favsOnly, limit);
            }
        }
    }

    public void setStarsDisplay(float star) {
        Verktex.INSTANCE.withTreeLock(() -> {
            VerktexBridge.checkUpdateThread();

            String str = beatmapDifficultyText.getText();
            String[] strs = str.split("Stars: ");
            if (strs.length == 2) {
                beatmapDifficultyText.setText(strs[0] + "Stars: " + GameHelper.Round(star, 2));
            }
            return null;
        });
    }

    private void reSelectItem(String beatmapFilename) {
        if (!beatmapFilename.isEmpty()) {
            if (selectedBeatmap != null && selectedBeatmap.getFilename().equals(beatmapFilename) && items.size() > 1 && selectedItem != null && selectedItem.isVisible()) {
                float height = 0;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i) == selectedItem) {
                        break;
                    }
                    height += items.get(i).getInitialHeight();
                }
                // The legacy centred on the total height of the first item, not of the selected one.
                scroller.centerOn(height, items.get(0).getTotalHeight());
                return;
            }
            for (int i = items.size() - 1; i >= 0; i--) {
                BeatmapSetItem item = items.get(i);
                if (item == null || !item.isVisible()) continue;
                int beatmapId = item.tryGetCorrespondingBeatmapId(beatmapFilename);
                if (beatmapId >= 0) {
                    item.select(false);
                    if (beatmapId != 0) {
                        item.selectBeatmap(item.getBeatmapSpritesById(beatmapId), false);
                    }
                    break;
                }
            }
        }
    }

    private void cancelCalculationJobs() {
        if (calculationJob != null) {
            calculationJob.cancel(new CancellationException("Difficulty calculation has been cancelled."));
        }

        ModMenu.INSTANCE.cancelCalculationJob();
    }

    private void cancelMapStatusLoadingJob() {
        if (mapStatusJob != null) {
            mapStatusJob.cancel(new CancellationException("Beatmap status check has been cancelled."));
        }
    }

    private void setScoringSwitcherTexture(final String name) {
        if (scoringSwitcher == null) {
            return;
        }
        scoringSwitcherTexture = name;
        scoringSwitcher.setTextureRegion(SkinTextures.getAsset(name, name + ".png"));
    }

    private void updateScoringSwitcherStatus(boolean forceUpdate) {
        if (scoringSwitcher == null) {
            return;
        }

        var selectedBeatmap = this.selectedBeatmap;
        if (selectedBeatmap == null || !board.isShowOnlineScores()) {
            setScoringSwitcherTexture("ranking_disabled");
            return;
        }

        var md5 = selectedBeatmap.getMD5();
        var cachedStatus = mapStatuses.get(md5);

        if (!forceUpdate && cachedStatus != null) {
            setScoringSwitcherTexture(
                "selection-" + switch (cachedStatus) {
                    case ranked, approved, loved -> cachedStatus.name().toLowerCase();
                    case qualified -> "approved";
                    default -> "question";
                }
            );
            return;
        }

        var textureName = "ranking_enabled_" + switch (Config.getBeatmapLeaderboardScoringMode()) {
            case SCORE -> "score";
            case PP -> "pp";
        };

        setScoringSwitcherTexture(textureName);

        cancelMapStatusLoadingJob();

        mapStatusJob = Execution.async(scope -> {
            var job = scope.getCoroutineContext().get(Job.Key);

            try {
                JobKt.ensureActive(scope.getCoroutineContext());

                var status = OnlineManager.getInstance().getBeatmapStatus(md5);

                JobKt.ensureActive(scope.getCoroutineContext());

                Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                    var selected = this.selectedBeatmap;

                    if (job != null && job.isCancelled() || !board.isShowOnlineScores() || status == null || scoringSwitcher == null || selected == null || selected != selectedBeatmap || !selected.getMD5().equals(md5)) {
                        return null;
                    }

                    mapStatuses.put(md5, status);

                    setScoringSwitcherTexture(
                        "selection-" + switch (status) {
                            case ranked, approved, loved -> status.name().toLowerCase();
                            default -> "question";
                        }
                    );
                    return null;
                }));
            } catch (OnlineManagerException e) {
                Debug.e("Cannot get beatmap status: " + e.getMessage(), e);

                JobKt.ensureActive(scope.getCoroutineContext());

                Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
                    if ((job == null || !job.isCancelled()) && this.selectedBeatmap == selectedBeatmap) {
                        setScoringSwitcherTexture(textureName);
                    }
                    return null;
                }));
            }
        });
    }

    /**
     * Called when the beatmap table changes. Most commonly during difficulty calculation.
     */
    public void onDifficultyCalculationEnd() {

        if (!isShown()) {
            return;
        }

        Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {

            // If the sort order is related to difficulty, we need to reload the menu items.
            if (sortOrder == SortOrder.DroidStars || sortOrder == SortOrder.StandardStars) {
                reload();
                show();
                select();
            } else {
                reloadCurrentSelection();
            }

            return null;
        }));
    }


    public enum SortOrder {
        Title,
        Artist,
        Creator,
        Date,
        Bpm,
        DroidStars,
        StandardStars,
        Length
    }

    public enum GroupType {
        MapSet, SingleDiff
    }

    /**
     * One of the three lower info texts. A tap opens the beatmap's attributes adjusted for the
     * enabled mods. Shares the buttons' press guard.
     */
    private class BeatmapStatisticToggleText extends UIText implements IPressable {
        private final LegacyPressTracker press = new LegacyPressTracker(this);

        @Override
        public boolean onInputEvent(final InputEvent inputEvent) {
            if (!(inputEvent instanceof PointerEvent pSceneTouchEvent)) {
                return false;
            }

            // Local coordinates in virtual units, the slop is in virtual units.
            final Vec2 local = toLocalSpaceFromScene(pSceneTouchEvent.getX(), pSceneTouchEvent.getY());
            final float pTouchAreaLocalX = local.getX() / LegacyUnits.getScale();
            final float pTouchAreaLocalY = local.getY() / LegacyUnits.getScale();

            if (pSceneTouchEvent.getAction() == PointerEvent.Action.Down) {
                press.down(pTouchAreaLocalX, pTouchAreaLocalY);
                return true;
            }

            if (pSceneTouchEvent.getAction() == PointerEvent.Action.Up) {
                if (press.up()) {
                    var selectedBeatmap = SongMenu.this.selectedBeatmap;

                    if (selectedBeatmap != null && !press.getMoved()) {
                        var mods = ModMenu.INSTANCE.getEnabledMods();
                        Verktex.INSTANCE.withTreeLock(() -> {
                            new BeatmapAttributeDisplay(selectedBeatmap.getBeatmapDifficulty(), mods.values()).show();
                            return null;
                        });
                    }
                }
                return true;
            }

            if (pSceneTouchEvent.getAction() == PointerEvent.Action.Move) {
                press.move(pTouchAreaLocalX, pTouchAreaLocalY);
                return false;
            }

            // Cancel events are dispatched to every component, so they are not consumed.
            press.cancel();
            return false;
        }

        @Override
        public void cancelPress() {
            press.cancel();
        }
    }

    /**
     * A full-window layer that offers events to its children. Children {@code accepts} rejects are skipped.
     * <p>
     * The list layer (the default) stands for the cards, each registered with the AndEngine scene,
     * whose back to front traversal asked them in registration order: children are asked in attach
     * order, those given to {@link #moveToDispatchEnd} after all others in the order they were given.
     * <p>
     * A {@code container} layer stands for the legacy front layer, a reco1l {@code UIContainer} registered as one
     * touch area. It asked its children last attached first, hit tests every event, and the child that
     * consumed a press gets every event of that pointer until it is lifted, the layer consuming them all.
     */
    static class ListLayer extends UIContainer implements IPressable {
        private final boolean container;
        private final Predicate<UIComponent> accepts;

        private final LinkedHashSet<UIComponent> dispatchEnd = new LinkedHashSet<>();
        private final HashMap<Integer, UIComponent> bindings = new HashMap<>();

        ListLayer() {
            this(false, child -> true);
        }

        ListLayer(final boolean container, final Predicate<UIComponent> accepts) {
            this.container = container;
            this.accepts = accepts;

            setWidth(DimensionKt.fill(1f));
            setHeight(DimensionKt.fill(1f));
        }

        /** Asks {@code child} after every other child, as an AndEngine touch area registered again. */
        void moveToDispatchEnd(final UIComponent child) {
            dispatchEnd.remove(child);
            dispatchEnd.add(child);
        }

        @Override
        public boolean onInputEvent(final InputEvent event) {
            if (container && event instanceof PointerEvent pointerEvent) {
                final UIComponent bound = bindings.get(pointerEvent.getPointerId());

                if (bound != null) {
                    if (pointerEvent.getAction() == PointerEvent.Action.Up || pointerEvent.getAction() == PointerEvent.Action.Unknown
                            || pointerEvent.getAction() == PointerEvent.Action.Cancel) {
                        bindings.remove(pointerEvent.getPointerId());
                    }

                    if (bound.getParent() == this) {
                        bound.onInputEvent(event);
                        return true;
                    }
                }
            }

            final List<UIComponent> children = getChildren();

            if (container) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    if (offer(children.get(i), event)) {
                        return true;
                    }
                }
                return false;
            }

            // By index: a child can attach new children while it handles the event.
            int i = 0;
            while (i < children.size()) {
                final UIComponent child = children.get(i++);

                if (!dispatchEnd.contains(child) && offer(child, event)) {
                    return true;
                }
            }

            for (final UIComponent child : new ArrayList<>(dispatchEnd)) {
                if (child.getParent() == this && offer(child, event)) {
                    return true;
                }
            }
            return false;
        }

        private boolean offer(final UIComponent child, final InputEvent event) {
            if (!accepts.test(child)) {
                return false;
            }

            if (event instanceof PointerEvent pointerEvent
                    && (container || pointerEvent.getAction() == PointerEvent.Action.Down)
                    && !child.containsScenePoint(pointerEvent.getX(), pointerEvent.getY())) {
                return false;
            }

            if (!child.onInputEvent(event)) {
                return false;
            }

            if (container && event instanceof PointerEvent pointerEvent && pointerEvent.getAction() == PointerEvent.Action.Down) {
                bindings.put(pointerEvent.getPointerId(), child);
            }
            return true;
        }

        @Override
        public void cancelPress() {
            bindings.clear();
        }
    }
}
