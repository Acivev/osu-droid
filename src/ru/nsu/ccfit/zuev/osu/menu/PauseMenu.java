package ru.nsu.ccfit.zuev.osu.menu;

import androidx.annotation.NonNull;

import com.osudroid.ui.verktex.FullWindowContainer;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.andengine.UIEngine;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.data.Vec2;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.rian.andengine.modifier.ModifierRegistry;
import com.rian.andengine.modifier.ModifierTicker;
import com.rian.andengine.modifier.VerktexModifiers;

import java.util.ArrayList;
import java.util.List;

import ru.nsu.ccfit.zuev.audio.BassSoundProvider;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.ToastLogger;
import ru.nsu.ccfit.zuev.osu.game.GameScene;

public class PauseMenu {

    private static final String TEX_SAVE_REPLAY = "pause-save-replay";
    private static final String TEX_CONTINUE    = "pause-continue";
    private static final String TEX_RETRY       = "pause-retry";
    private static final String TEX_BACK        = "pause-back";

    private final FullWindowContainer scene;
    private final GameScene   game;
    private final boolean     fail;
    private boolean replaySaved;

    // The AndEngine itemSelected and initialSelectX/Y.
    private final SelectionTracker selection = new SelectionTracker();

    // Verktex offers moves and releases to every child, AndEngine bound them to the touched area.
    private int selectPointerId = -1;

    // The child scene ran its children's modifiers at 2.5x; the menu is their time base.
    private final ModifierTicker ticker = new ModifierTicker();

    private final List<UISprite> buttons = new ArrayList<>();

    private UISprite btnSaveReplay;
    private UISprite btnContinue;
    private final UISprite btnRetry;
    private final UISprite btnBack;

    /**
     * Builds the menu. Must be called on the update thread under the Verktex tree lock.
     */
    public PauseMenu(final GameScene game, final boolean fail) {
        this.game = game;
        this.fail = fail;

        final float units = LegacyUnits.getScale();

        final GraphicsTexture.Region texReplay   = SkinTextures.getWithBeatmapSkin(TEX_SAVE_REPLAY);
        final GraphicsTexture.Region texContinue = SkinTextures.getWithBeatmapSkin(TEX_CONTINUE);
        final GraphicsTexture.Region texRetry    = SkinTextures.getWithBeatmapSkin(TEX_RETRY);
        final GraphicsTexture.Region texBack     = SkinTextures.getWithBeatmapSkin(TEX_BACK);

        final List<String> visible = visibleButtons(fail, game.getReplaying());
        final boolean showSaveReplay = visible.contains(TEX_SAVE_REPLAY);
        final boolean showContinue   = visible.contains(TEX_CONTINUE);

        // Compute proportional scale so all visible buttons fit in 85% of screen height.
        float totalNaturalH = heightOf(texRetry) + heightOf(texBack);
        int   count         = 2;
        if (showSaveReplay) { totalNaturalH += heightOf(texReplay);   count++; }
        if (showContinue)   { totalNaturalH += heightOf(texContinue); count++; }
        totalNaturalH += (count - 1); // 1 px spacing between items

        final float scale = layoutScale(totalNaturalH, Config.getRES_HEIGHT());

        scene = new FullWindowContainer() {
            @Override
            public boolean onInputEvent(@NonNull final InputEvent event) {
                if (!(event instanceof PointerEvent)) {
                    return false;
                }
                onPointerEvent((PointerEvent) event);
                // Intercept touch events to prevent them from passing through to the game scene.
                return true;
            }

            @Override
            protected void onUpdate(@NonNull final TickingClock clock) {
                if (UIEngine.getCurrent().getScene() != game.getScene()) {
                    // Removed on the next tick so the overlay list stays stable throughout this one.
                    GlobalManager.getInstance().getEngine().runOnUpdateThread(PauseMenu.this::close);
                    return;
                }
                // Speed-up the fade-in animation (original was 1 s; 2.5× makes it ~0.4 s).
                ticker.tick(clock.getElapsedTickTime() * 2.5f);
                super.onUpdate(clock);
            }

            @Override
            public void onDetached() {
                super.onDetached();
                ModifierRegistry.bindTicker(this, null);
            }
        };

        ModifierRegistry.bindTicker(scene, ticker);

        // Background overlay
        final GraphicsTexture.Region bgTex = fail
                ? SkinTextures.getWithBeatmapSkin("fail-background")
                : SkinTextures.getWithBeatmapSkin("pause-overlay");

        if (bgTex != null) {
            float bgH = bgTex.getHeight() * Config.getRES_WIDTH() / bgTex.getWidth();
            final UISprite background = new UISprite(bgTex);
            background.setScaleType(UISprite.ScaleType.Stretch);
            background.setX(DimensionKt.getPx(0f));
            background.setY(DimensionKt.getPx((Config.getRES_HEIGHT() - bgH) / 2f * units));
            background.setWidth(DimensionKt.getPx(Config.getRES_WIDTH() * units));
            background.setHeight(DimensionKt.getPx(bgH * units));
            scene.plusAssign(background);
        }

        // Position all visible buttons centred on screen
        final float displayTotalH = totalNaturalH * scale;
        final float cx = Config.getRES_WIDTH() / 2f;
        float y = (Config.getRES_HEIGHT() - displayTotalH) / 2f;

        if (showSaveReplay) {
            btnSaveReplay = addButton(texReplay, cx, y, scale, units);
            y += heightOf(texReplay) * scale + scale;
        }
        if (showContinue) {
            btnContinue = addButton(texContinue, cx, y, scale, units);
            y += heightOf(texContinue) * scale + scale;
        }
        btnRetry = addButton(texRetry, cx, y, scale, units);
        y += heightOf(texRetry) * scale + scale;
        btnBack  = addButton(texBack, cx, y, scale, units);
    }

    private UISprite addButton(GraphicsTexture.Region tex, float cx, float y,
                               float scale, float units) {
        final float w = widthOf(tex)  * scale;
        final float h = heightOf(tex) * scale;
        final UISprite btn = new UISprite(tex);
        btn.setScaleType(UISprite.ScaleType.Stretch);
        btn.setX(DimensionKt.getPx((cx - w / 2f) * units));
        btn.setY(DimensionKt.getPx(y * units));
        btn.setWidth(DimensionKt.getPx(w * units));
        btn.setHeight(DimensionKt.getPx(h * units));
        btn.setAlpha(0f);
        // Attached first: the fade takes its start time from the menu's ticker.
        scene.plusAssign(btn);
        VerktexModifiers.fadeTo(btn, 1f, 0.4f);
        buttons.add(btn);
        return btn;
    }

    public FullWindowContainer getComponent() {
        return scene;
    }

    // The CameraScene back(): the menu leaves the gameplay layer.
    private void close() {
        game.gameplayLayer.hideTopOverlay(scene);
    }

    private void onPointerEvent(final PointerEvent event) {
        final PointerEvent.Action action = event.getAction();

        if (action != PointerEvent.Action.Down && event.getPointerId() != selectPointerId) {
            return;
        }

        if (action != PointerEvent.Action.Down && action != PointerEvent.Action.Move
                && action != PointerEvent.Action.Up) {
            // Cancelled: released without a click.
            selection.cancel();
            selectPointerId = -1;
            return;
        }

        if (action == PointerEvent.Action.Up) {
            selectPointerId = -1;
        }

        // AndEngine only told the touch area under the pointer, in that area's coordinates.
        final UISprite btn = buttonAt(event.getX(), event.getY());
        if (btn == null) {
            return;
        }

        final float units = LegacyUnits.getScale();
        final Vec2 local = btn.toLocalSpaceFromScene(event.getX(), event.getY());
        final float localX = local.getX() / units;
        final float localY = local.getY() / units;

        if (action == PointerEvent.Action.Down) {
            selectPointerId = event.getPointerId();
            selection.down(localX, localY);
        } else if (action == PointerEvent.Action.Move) {
            selection.move(localX, localY);
        } else if (selection.up()) {
            handleClick(btn);
        }
    }

    private UISprite buttonAt(final float x, final float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            final UISprite btn = buttons.get(i);
            if (btn.containsScenePoint(x, y)) {
                return btn;
            }
        }
        return null;
    }

    private void handleClick(final UISprite btn) {
        BassSoundProvider playSnd;

        if (btn == btnSaveReplay) {
            if (fail && !replaySaved && !game.getReplaying() && game.saveFailedReplay()) {
                ToastLogger.showText(
                        com.osudroid.resources.R.string.message_save_replay_successful, true);
                replaySaved = true;
            }

        } else if (btn == btnContinue) {
            if (fail) return;
            playSnd = ResourceManager.getInstance().getSound("menuback");
            if (playSnd != null) playSnd.play();
            game.resume();

        } else if (btn == btnBack) {
            GlobalManager.getInstance().getScoring().setReplayID(-1);
            playSnd = ResourceManager.getInstance().getSound("menuback");
            if (playSnd != null) playSnd.play();
            game.quit();

        } else if (btn == btnRetry) {
            ResourceManager.getInstance().getSound("failsound").stop();
            playSnd = ResourceManager.getInstance().getSound("menuhit");
            if (playSnd != null) playSnd.play();
            game.restartGame();
        }
    }

    // The AndEngine TextureRegion sizes; the textures always exist in the assets.
    private static float widthOf(final GraphicsTexture.Region region) {
        return region != null ? region.getWidth() : 0f;
    }

    private static float heightOf(final GraphicsTexture.Region region) {
        return region != null ? region.getHeight() : 0f;
    }

    /**
     * The scale that fits buttons {@code totalNaturalH} high (spacing included) into 85% of
     * {@code resHeight}. Public for the tests, which live in another package.
     */
    public static float layoutScale(final float totalNaturalH, final float resHeight) {
        final float maxH = resHeight * 0.85f;
        return (totalNaturalH > maxH) ? maxH / totalNaturalH : 1.0f;
    }

    /**
     * The texture names of the buttons the menu shows, top to bottom.
     */
    public static List<String> visibleButtons(final boolean fail, final boolean replaying) {
        final List<String> names = new ArrayList<>(4);
        if (fail && !replaying) names.add(TEX_SAVE_REPLAY);
        if (!fail)              names.add(TEX_CONTINUE);
        names.add(TEX_RETRY);
        names.add(TEX_BACK);
        return names;
    }

    /**
     * The touch rule of the menu: a press selects, a move farther than 50 px from the press point
     * deselects, and a release on a selected button clicks it.
     */
    public static final class SelectionTracker {

        private boolean itemSelected;
        private float   initialSelectX, initialSelectY;

        public void down(final float x, final float y) {
            itemSelected   = true;
            initialSelectX = x;
            initialSelectY = y;
        }

        public void move(final float x, final float y) {
            if (itemSelected && distance(initialSelectX, initialSelectY, x, y) > 50) {
                itemSelected = false;
            }
        }

        /**
         * @return Whether the release clicks.
         */
        public boolean up() {
            if (!itemSelected) {
                return false;
            }
            itemSelected = false;
            return true;
        }

        public void cancel() {
            itemSelected = false;
        }

        // The AndEngine MathUtils.distance.
        private static float distance(final float x1, final float y1, final float x2, final float y2) {
            final float dX = x2 - x1;
            final float dY = y2 - y1;
            return (float) Math.sqrt((dX * dX) + (dY * dY));
        }
    }
}
