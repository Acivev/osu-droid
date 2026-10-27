package ru.nsu.ccfit.zuev.osu.online;

import com.edlplan.ui.fragment.WebViewFragment;
import com.osudroid.ui.verktex.LegacyUnits;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.utils.Execution;
import com.reco1l.osu.ui.MessageDialog;
import com.reco1l.verktex.Verktex;
import com.reco1l.verktex.data.Anchor;
import com.reco1l.verktex.data.Color4;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.input.InputEvent;
import com.reco1l.verktex.input.PointerEvent;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.UISprite;
import com.reco1l.verktex.ui.container.UIClickableContainer;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.verktex.ui.shape.UIBox;
import com.reco1l.verktex.ui.text.UIText;

import org.andengine.util.math.MathUtils;

import kotlin.Unit;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.helper.MD5Calculator;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;

import java.io.File;
import java.util.Locale;

/**
 * The online profile panel of the main menu and the song menu. Sizes and positions are virtual units, applied in window pixels
 * on the update thread whenever {@link LegacyUnits#getScale()} changes.
 */
public class OnlinePanel extends UIClickableContainer {
    private static final Color4 IDLE_COLOR = new Color4(0.2f, 0.2f, 0.2f, 0.5f);
    private static final Color4 PRESSED_COLOR = new Color4(0.3f, 0.3f, 0.3f, 0.9f);

    private final UIContainer onlineLayer = new UIContainer();
    private final UIContainer messageLayer = new UIContainer();
    private final UIContainer frontLayer = new UIContainer();

    private final UIBox avatarFooter;

    private final UIText rankText, nameText, ppText, accText;
    private final UIText messageText, submessageText;
    private UISprite profileBanner = null;
    private UISprite avatar = null;

    // Press start in virtual units, a press dragged further than 50 is not a tap.
    private boolean moved = false;
    private float dx = 0, dy = 0;

    // Legacy position, in virtual units.
    private float positionX = 0, positionY = 0;

    // LegacyUnits.scale the sizes were last applied for.
    private float appliedScale = 0;

    public OnlinePanel() {
        setOnActionDown(() -> {
            setBackgroundColor(PRESSED_COLOR);
            moved = false;
            return Unit.INSTANCE;
        });
        setOnActionUp(() -> {
            setBackgroundColor(IDLE_COLOR);
            if (!moved && OnlineManager.getInstance().isStayOnline()) {

                Execution.mainThread(() -> new MessageDialog()
                    .setMessage(StringTable.get(com.osudroid.resources.R.string.dialog_visit_osudroid_profile_page))
                    .addButton("Yes", dialog -> {
                        new WebViewFragment().setURL(WebViewFragment.PROFILE_URL + OnlineManager.getInstance().getUserId()).show();
                        dialog.dismiss();
                        return null;
                    })
                    .addButton("No", dialog -> {
                        dialog.dismiss();
                        return null;
                    })
                    .show());
            }
            return Unit.INSTANCE;
        });
        setOnActionCancel(() -> {
            setBackgroundColor(IDLE_COLOR);
            return Unit.INSTANCE;
        });
        setBackgroundColor(IDLE_COLOR);

        avatarFooter = new UIBox();
        avatarFooter.setBackgroundColor(new Color4(0.2f, 0.2f, 0.2f, 0.8f));
        plusAssign(avatarFooter);
		
		/*Rectangle rightFooter = new Rectangle(Utils.toRes(410), 0, Utils.toRes(614), Utils.toRes(110));
		rightFooter.setColor(0.3f, 0.3f, 0.3f, 0.35f);
		attachChild(rightFooter);*/

        rankText = new UIText();
        rankText.setText("#1");
        rankText.setWrapText(false);
        rankText.setColor(new Color4(0.6f, 0.6f, 0.6f, 0.9f));
        // Right edge at x = 400, scaled 1.7 from its right edge as the legacy text was laid out.
        rankText.setOrigin(Anchor.TopRight);
        rankText.setScaleOrigin(Anchor.CenterRight);
        rankText.setScaleX(1.7f);
        rankText.setScaleY(1.7f);
        onlineLayer.plusAssign(rankText);

        nameText = new UIText();
        nameText.setText("Guest");
        nameText.setWrapText(false);
        onlineLayer.plusAssign(nameText);
        ppText = new UIText();
        ppText.setText("Performance: 0pp");
        ppText.setWrapText(false);
        ppText.setColor(new Color4(0.85f, 0.85f, 0.9f, 1f));
        onlineLayer.plusAssign(ppText);

        accText = new UIText();
        accText.setText("Accuracy: 0.00%");
        accText.setWrapText(false);
        accText.setColor(new Color4(0.85f, 0.85f, 0.9f, 1f));
        onlineLayer.plusAssign(accText);

        messageText = new UIText();
        messageText.setText("Logging in...");
        messageText.setWrapText(false);
        messageLayer.plusAssign(messageText);

        submessageText = new UIText();
        submessageText.setText("Connecting to server...");
        submessageText.setWrapText(false);
        messageLayer.plusAssign(submessageText);

        // The legacy panel re-attached the shown text layer last, so it was drawn above the profile banner;
        // here both layers stay attached above it and only one is visible.
        for (final UIContainer layer : new UIContainer[] { frontLayer, onlineLayer, messageLayer }) {
            layer.setWidth(DimensionKt.fill(1f));
            layer.setHeight(DimensionKt.fill(1f));
            plusAssign(layer);
        }
        onlineLayer.setVisible(false);

    }

    /** Legacy {@code setPosition}, in virtual units. */
    public void setPosition(final float x, final float y) {
        positionX = x;
        positionY = y;
        setX(DimensionKt.getPx(x * LegacyUnits.getScale()));
        setY(DimensionKt.getPx(y * LegacyUnits.getScale()));
    }

    @Override
    protected void onUpdate(final TickingClock clock) {
        final float scale = LegacyUnits.getScale();
        if (appliedScale != scale) {
            appliedScale = scale;

            setX(DimensionKt.getPx(positionX * scale));
            setY(DimensionKt.getPx(positionY * scale));
            setWidth(DimensionKt.getPx(410 * scale));
            setHeight(DimensionKt.getPx(110 * scale));

            avatarFooter.setWidth(DimensionKt.getPx(110 * scale));
            avatarFooter.setHeight(DimensionKt.getPx(110 * scale));

            final var captionFont = DimensionKt.getPx(LegacyUnits.fontSize("CaptionFont") * scale);
            final var smallFont = DimensionKt.getPx(LegacyUnits.fontSize("smallFont") * scale);

            rankText.setFontSize(captionFont);
            rankText.setX(DimensionKt.getPx((390 + 10) * scale));
            rankText.setY(DimensionKt.getPx(55 * scale));

            nameText.setFontSize(captionFont);
            nameText.setX(DimensionKt.getPx(120 * scale));
            nameText.setY(DimensionKt.getPx(5 * scale));

            ppText.setFontSize(smallFont);
            ppText.setX(DimensionKt.getPx(120 * scale));
            ppText.setY(DimensionKt.getPx(50 * scale));

            accText.setFontSize(smallFont);
            accText.setX(DimensionKt.getPx(120 * scale));
            accText.setY(DimensionKt.getPx(75 * scale));

            messageText.setFontSize(captionFont);
            messageText.setX(DimensionKt.getPx(110 * scale));
            messageText.setY(DimensionKt.getPx(5 * scale));

            submessageText.setFontSize(smallFont);
            submessageText.setX(DimensionKt.getPx(110 * scale));
            submessageText.setY(DimensionKt.getPx(60 * scale));

            if (profileBanner != null) {
                profileBanner.setWidth(DimensionKt.getPx(410 * scale));
                profileBanner.setHeight(DimensionKt.getPx(110 * scale));
            }

            if (avatar != null) {
                avatar.setWidth(DimensionKt.getPx(110 * scale));
                avatar.setHeight(DimensionKt.getPx(110 * scale));
            }
        }

        super.onUpdate(clock);
    }

    @Override
    public boolean onInputEvent(final InputEvent inputEvent) {
        if (inputEvent instanceof PointerEvent event) {
            // The slop is in virtual units, as the AndEngine touch area's local coordinates were.
            final float x = event.getX() / LegacyUnits.getScale();
            final float y = event.getY() / LegacyUnits.getScale();

            if (event.getAction() == PointerEvent.Action.Down && !isPressed()) {
                dx = x;
                dy = y;
            } else if (event.getAction() == PointerEvent.Action.Move && isPressed() && !moved
                    && MathUtils.distance(dx, dy, x, y) > 50) {
                moved = true;
                setBackgroundColor(IDLE_COLOR);
            }
        }
        return super.onInputEvent(inputEvent);
    }

    // Called from the login coroutine, so the change is handed to the update thread.
    public void setMessage(final String message, final String submessage) {
        Execution.updateThread(() -> Verktex.INSTANCE.withTreeLock(() -> {
            messageText.setText(message);
            submessageText.setText(submessage);

            onlineLayer.setVisible(false);
            messageLayer.setVisible(true);
            return null;
        }));
    }

    public void setInfo() {
        Verktex.INSTANCE.withTreeLock(() -> {
            nameText.setText(OnlineManager.getInstance().getUsername());

            ppText.setText(String.format(Locale.US, "Performance: %,dpp", Math.round(OnlineManager.getInstance().getPP())));

            accText.setText(String.format("Accuracy: %.2f%%",
                    OnlineManager.getInstance().getAccuracy() * 100f));
            rankText.setText(String.format("#%d", OnlineManager.getInstance().getRank()));

            messageLayer.setVisible(false);
            onlineLayer.setVisible(true);
            return null;
        });
    }

    // Must be called on the update thread, the textures are loaded here.
    public void setProfile(final String avatarTexName) {
        Verktex.INSTANCE.withTreeLock(() -> {
            if (profileBanner != null) {
                profileBanner.removeSelf();
            }

            if (avatar != null) {
                avatar.removeSelf();
            }

            profileBanner = null;
            avatar = null;
            appliedScale = 0;

            var profileBannerUrl = OnlineManager.getInstance().getProfileBannerURL();

            if (profileBannerUrl != null && !profileBannerUrl.isEmpty()) {
                var bannerTexture = loadCachedTexture("profile-banner:", profileBannerUrl);

                if (bannerTexture != null) {
                    profileBanner = new UISprite(bannerTexture);
                    profileBanner.setScaleType(UISprite.ScaleType.Stretch);
                    profileBanner.setColor(new Color4(0.5f, 0.5f, 0.5f, 1f));
                    frontLayer.plusAssign(profileBanner);
                }
            }

            if (avatarTexName == null) {
                return null;
            }

            var avatarTexture = loadCachedTexture("avatar:", avatarTexName);

            if (avatarTexture == null) {
                avatarTexture = loadCachedTexture("avatar:", OnlineManager.defaultAvatarURL);
            }

            if (avatarTexture == null) {
                return null;
            }

            avatar = new UISprite(avatarTexture);
            avatar.setScaleType(UISprite.ScaleType.Stretch);
            frontLayer.plusAssign(avatar);
            return null;
        });
    }

    /**
     * Loads the file {@link OnlineManager} cached for {@code url} under "&lt;prefix&gt;&lt;md5&gt;", null when it is
     * missing or does not decode.
     */
    private static GraphicsTexture.Region loadCachedTexture(final String prefix, final String url) {
        final String md5 = MD5Calculator.getStringMD5(url);
        final File file = new File(Config.getCachePath(), md5);

        if (!file.exists()) {
            return null;
        }

        try {
            return SkinTextures.loadFile(prefix + md5, file);
        } catch (final Exception e) {
            return null;
        }
    }
}