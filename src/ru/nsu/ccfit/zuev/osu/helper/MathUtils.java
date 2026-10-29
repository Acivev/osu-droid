package ru.nsu.ccfit.zuev.osu.helper;

import java.util.Random;

/**
 * App-side twin of the parts of AndEngine's {@code MathUtils} the app uses.
 */
public final class MathUtils {
    public static final Random RANDOM = new Random(System.nanoTime());

    private MathUtils() {}

    /**
     * @param pMin inclusive!
     * @param pMax inclusive!
     */
    public static int random(final int pMin, final int pMax) {
        return pMin + RANDOM.nextInt(pMax - pMin + 1);
    }

    /**
     * @return the euclidean distance between the points (pX1, pY1) and (pX2, pY2).
     */
    public static float distance(final float pX1, final float pY1, final float pX2, final float pY2) {
        final float dX = pX2 - pX1;
        final float dY = pY2 - pY1;
        return (float) Math.sqrt((dX * dX) + (dY * dY));
    }
}
