package ru.nsu.ccfit.zuev.osu.menu;

import com.osudroid.ui.verktex.LegacyUnits;
import com.reco1l.verktex.data.DimensionKt;
import com.reco1l.verktex.time.TickingClock;
import com.reco1l.verktex.ui.container.UIContainer;
import com.reco1l.verktex.ui.shape.UIBox;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.Utils;

/**
 * The scroll bar of the song list, a rectangle at the right edge of the layer. Must be used on the update thread.
 */
public class ScrollBar {
    private final Bar barRectangle;
    private boolean visible;

    public ScrollBar(final UIContainer layer) {
        visible = false;

        barRectangle = new Bar();
        barRectangle.setAlpha(0.8f);
        barRectangle.setRed(1);
        barRectangle.setGreen(1);
        barRectangle.setBlue(1);

        layer.plusAssign(barRectangle);
        barRectangle.setVisible(false);
    }

    public void setPosition(final float vy, final float maxy) {
        if (!visible) {
            return;
        }

        barRectangle
                .setPosition(barRectangle.getPositionX(),
                        (Config.getRES_HEIGHT() - barRectangle.getVirtualHeight())
                                * vy / maxy);
    }

    public void setVisible(final boolean vis) {
        barRectangle.setVisible(vis);
        if (vis && !visible) {
            final UIContainer parent = barRectangle.getParent();
            parent.moveToFront(barRectangle);
        }
        visible = vis;
    }

    /** The rectangle, positioned and sized in virtual units. */
    private static class Bar extends UIBox {
        private float positionX = Config.getRES_WIDTH() - Utils.toRes(20f);
        private float positionY = 0;

        private final float virtualHeight = Utils.toRes(50f);
        private final float virtualWidth = Utils.toRes(20f);

        // LegacyUnits.scale the sizes were last applied for.
        private float appliedScale = 0;

        float getPositionX() {
            return positionX;
        }

        float getVirtualHeight() {
            return virtualHeight;
        }

        void setPosition(final float x, final float y) {
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
                setWidth(DimensionKt.getPx(virtualWidth * scale));
                setHeight(DimensionKt.getPx(virtualHeight * scale));
            }

            super.onUpdate(clock);
        }
    }
}
