package ru.nsu.ccfit.zuev.osu.menu;

import com.osudroid.data.BeatmapSetInfo;
import com.osudroid.ui.verktex.SkinTextures;
import com.reco1l.verktex.graphics.GraphicsTexture;
import com.reco1l.verktex.ui.container.UIContainer;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.LibraryManager;
import com.osudroid.data.BeatmapInfo;
import com.osudroid.data.DatabaseManager;

import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.DifficultyAlgorithm;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;

/**
 * A beatmap set of the song list. Its cards are pooled Verktex components attached to the layer given in
 * {@link #attachTo}, positioned in virtual units by {@link #setPos}. Must be used on the update thread.
 */
public class BeatmapSetItem {
    private final BeatmapItem[] beatmapItems;
    private BeatmapSetInfo beatmapSetInfo;
    private final String beatmapSetDir;
    // Read from the texture when first needed (on the update thread), so constructing an item needs no texture.
    // Not cached while the texture is missing, so a later lookup can still succeed.
    private Integer bgHeight = null;
    private final String titleStr;
    private final String creatorStr;
    public float percentAppeared = 0;
    MenuItemBackground background;
    boolean selected = false;
    private static final Pattern FILTER_PATTERN = Pattern.compile("(ar|od|cs|hp|star)(=|<|>|<=|>=)(\\d+)");
    WeakReference<MenuItemListener> listener;
    private BeatmapItem selectedBeatmapItem = null;
    private boolean visible = true;
    // Queried from the database on first use, so the filter logic runs without one.
    private Boolean favorite;
    private boolean deleted = false;
    private UIContainer layer = null;
    private int beatmapId = -1;

    public BeatmapSetItem(final MenuItemListener listener, final BeatmapSetInfo beatmapSetInfo) {
        this.listener = new WeakReference<>(listener);
        this.beatmapSetInfo = beatmapSetInfo;
        beatmapSetDir = beatmapSetInfo.getDirectory();
//        titleStr = (beatmap.getArtistUnicode() == null ? beatmap.getArtist() : beatmap.getArtistUnicode()) + " - "
//                + (beatmap.getTitleUnicode() == null ? beatmap.getTitle() : beatmap.getTitleUnicode());
        var beatmapInfo = this.beatmapSetInfo.getBeatmaps().get(0);

        titleStr = beatmapInfo.getArtistText() + " - " + beatmapInfo.getTitleText();

        creatorStr = StringTable.format(com.osudroid.resources.R.string.menu_creator,
                beatmapInfo.getCreator());
        beatmapItems = new BeatmapItem[beatmapSetInfo.getCount()];

    }

    public BeatmapSetItem(final MenuItemListener listener, final BeatmapSetInfo beatmapSetInfo, int id) {
        this.listener = new WeakReference<>(listener);
        this.beatmapSetInfo = beatmapSetInfo;
        beatmapSetDir = this.beatmapSetInfo.getDirectory();
//        titleStr = (beatmap.getArtistUnicode() == null ? beatmap.getArtist() : beatmap.getArtistUnicode()) + " - "
//                + (beatmap.getTitleUnicode() == null ? beatmap.getTitle() : beatmap.getTitleUnicode());
        var beatmapInfo = this.beatmapSetInfo.getBeatmaps().get(0);

        titleStr = beatmapInfo.getArtistText() + " - " + beatmapInfo.getTitleText();

        creatorStr = StringTable.format(com.osudroid.resources.R.string.menu_creator, beatmapInfo.getCreator());
        beatmapItems = new BeatmapItem[1];
        beatmapId = id;

    }

    private int getBgHeight() {
        if (bgHeight != null) {
            return bgHeight;
        }
        final GraphicsTexture.Region texture = SkinTextures.get("menu-button-background");
        final int height = (int) ((texture != null ? texture.getHeight() : 0)
                - Utils.toRes(25));
        if (texture != null) {
            bgHeight = height;
        }
        return height;
    }


    public void setBeatmapSetInfo(BeatmapSetInfo beatmapSetInfo) {
        this.beatmapSetInfo = beatmapSetInfo;
    }

    public BeatmapSetInfo getBeatmapSetInfo() {
        return beatmapSetInfo;
    }


    public void updateMarks() {
        for (final BeatmapItem tr : beatmapItems) {
            if (tr != null) {
                tr.updateMark();
            }
        }
    }

    public void attachTo(final UIContainer layer) {
        this.layer = layer;
        // initBackground();
    }

    public float getHeight() {
        if (!visible) {
            return 0;
        }
        if (selected) {
            return getBgHeight() + percentAppeared * (getBgHeight())
                    * (beatmapItems.length - 1);
        }
        return getBgHeight() - Utils.toRes(5);
    }

    public float getInitialHeight() {
        if (!visible) {
            return 0;
        }
        return getBgHeight() - Utils.toRes(5);
    }

    public float getTotalHeight() {
        return (getBgHeight()) * (beatmapItems.length);
    }

    /** Positions the cards, {@code x} and {@code y} are in virtual units. */
    public void setPos(final float x, final float y) {
        if (background != null) {
            background.setPosition(x, y);
            if (y > Config.getRES_HEIGHT() || y < -background.getVirtualHeight()) {
                freeBackground();
            }
        }
        if (!selected) {
            if (visible && background == null
                    && y < Config.getRES_HEIGHT() && y > -getBgHeight()) {
                initBackground();
                background.setPosition(x, y);
            }
            return;
        }
        float oy = 0;
        for (final BeatmapItem s : beatmapItems) {
            if (s == null) {
                continue;
            }
            final float cy = y + oy + Config.getRES_HEIGHT() / 2f
                    + s.getVirtualHeight() / 2;
            final float ox = x
                    + Utils.toRes(170 * (float) Math.abs(Math.cos(cy * Math.PI
                    / (Config.getRES_HEIGHT() * 2))));
            s.setPosition(ox - Utils.toRes(100), y + oy);
            oy += (s.getVirtualHeight() - Utils.toRes(25)) * percentAppeared;
        }
    }

    public void select() {
        select(true);
    }

    public void select(boolean reloadBG) {
        if (listener.get() == null || !listener.get().isSelectAllowed() || layer == null) {
            return;
        }

        freeBackground();
        selected = true;
        listener.get().select(this);
        initBeatmaps();
        percentAppeared = 0;

        selectBeatmap(beatmapItems[0], reloadBG);
        beatmapItems[0].setSelectedColor();
    }

    public void deselect() {
        if (layer == null ) {
            return;
        }

        if (deleted) {
            return;
        }
        initBackground();
        selected = false;
        percentAppeared = 0;
        deselectBeatmap();
        freeBeatmaps();
    }

    public void deselectBeatmap() {
        if (layer == null) {
            return;
        }
        if (selectedBeatmapItem != null) {
            selectedBeatmapItem.setDeselectColor();
        }
        selectedBeatmapItem = null;
    }

    public void applyFilter(final String filter, final boolean favs, List<String> limit) {
        if ((favs && !isFavorite())
                || (limit != null && !limit.isEmpty() && !limit.contains(beatmapSetDir))) {
            //System.out.println(trackDir);
            if (selected) {
                deselect();
            }
            freeBackground();
            visible = false;
            return;
        }

        var beatmapInfo = beatmapSetInfo.getBeatmaps().get(0);

        final StringBuilder builder = new StringBuilder();
        builder.append(beatmapInfo.getTitle());
        builder.append(' ');
        builder.append(beatmapInfo.getArtist());
        builder.append(' ');
        builder.append(beatmapInfo.getCreator());
        builder.append(' ');
        builder.append(beatmapInfo.getTags());
        builder.append(' ');
        builder.append(beatmapInfo.getSource());
        builder.append(' ');
        builder.append(beatmapInfo.getId());
        for (var i = beatmapSetInfo.getCount() - 1; i >= 0; i--) {
            builder.append(' ');
            builder.append(beatmapSetInfo.getBeatmap(i).getVersion());
        }

        boolean canVisible = true;
        final String lowerText = builder.toString().toLowerCase();
        final String[] lowerFilterTexts = filter.toLowerCase().split("[ ]");
        for (String filterText : lowerFilterTexts) {
            Matcher matcher = FILTER_PATTERN.matcher(filterText);
            if (matcher.find()) {
                String key = matcher.group(1);
                String opt = matcher.group(2);
                String value = matcher.group(3);
                boolean vis = false;
                if(beatmapId < 0){
                    for (var i = beatmapSetInfo.getCount() - 1; i >= 0; i--) {
                        if (key != null) {
                            vis |= visibleBeatmap(beatmapSetInfo.getBeatmap(i), key, opt, value);
                        }
                    }
                }
                else{
                    if (key != null) {
                        vis = visibleBeatmap(beatmapSetInfo.getBeatmap(beatmapId), key, opt, value);
                    }
                }
                canVisible &= vis;
            } else {
                if (!lowerText.contains(filterText)) {
                    canVisible = false;
                    break;
                }
            }
        }

        if (filter.isEmpty()) {
            canVisible = true;
        }

        if (canVisible) {
            if (!visible) {
                visible = true;
                // initBackground();
                selected = false;
                percentAppeared = 0;
            }
            return;
        }

        if (selected) {
            deselect();
        }
        freeBackground();
        visible = false;
    }

    private boolean visibleBeatmap(BeatmapInfo beatmapInfo, String key, String opt, String value) {
        return switch (key) {
            case "ar" -> calOpt(beatmapInfo.getApproachRate(), Float.parseFloat(value), opt);
            case "od" -> calOpt(beatmapInfo.getOverallDifficulty(), Float.parseFloat(value), opt);
            case "cs" -> calOpt(beatmapInfo.getCircleSize(), Float.parseFloat(value), opt);
            case "hp" -> calOpt(beatmapInfo.getHpDrainRate(), Float.parseFloat(value), opt);
            case "droidstar" ->
                calOpt(beatmapInfo.getStarRating(DifficultyAlgorithm.droid), Float.parseFloat(value), opt);
            case "standardstar", "star" ->
                calOpt(beatmapInfo.getStarRating(DifficultyAlgorithm.standard), Float.parseFloat(value), opt);
            default -> false;
        };
    }

    private boolean calOpt(float val1, float val2, String opt) {
        return switch (opt) {
            case "=" -> val1 == val2;
            case "<" -> val1 < val2;
            case ">" -> val1 > val2;
            case "<=" -> val1 <= val2;
            case ">=" -> val1 >= val2;
            default -> false;
        };
    }

    public void delete() {

        if (selected) {
            deselect();
        }
        freeBackground();
        visible = false;
        deleted = true;
        LibraryManager.deleteBeatmapSet(beatmapSetInfo);
    }

    public boolean isVisible() {
        return visible && (!deleted);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void stopScroll(final float y) {
        if (listener.get() != null) {
            listener.get().stopScroll(y);
        }
    }

    public void selectBeatmap(final BeatmapItem beatmapInfo, boolean reloadBG) {
        selectedBeatmapItem = beatmapInfo;
        if (listener.get() != null) {
            listener.get().selectBeatmap(beatmapInfo.getBeatmapInfo(), reloadBG);
        }
    }

    public boolean isBeatmapSelected(final BeatmapItem beatmapItem) {
        return selectedBeatmapItem == beatmapItem;
    }

    private synchronized void freeBackground() {
        // scene.unregisterTouchArea(background);
        if (background == null) {
            return;
        }
        background.setVisible(false);
        SongMenuPool.getInstance().putBackground(background);
        background = null;

    }

    private synchronized void initBackground() {
        if (background == null) {
            background = SongMenuPool.getInstance().newBackground();
        }
        background.setItem(this);
        background.setTitle(titleStr);
        background.setAuthor(creatorStr);
        background.setVisible(true);
        if (background.getParent() == null) {
            layer.plusAssign(background);
        }
    }

    private synchronized void freeBeatmaps() {
        for (var item : beatmapItems) {
            if (item != null) {
                // Invisible items ignore input, as the unregistered AndEngine touch area did.
                item.setVisible(false);
                SongMenuPool.getInstance().putBeatmapItem(item);
            }
        }
    }

    public synchronized void reloadBeatmaps() {
        if (beatmapId == -1) {
            // Tracks are originally sorted by osu!droid difficulty, so for osu!standard difficulty they need to be sorted again.
            beatmapSetInfo.getBeatmaps().sort((o1, o2) -> Float.compare(o1.getStarRating(), o2.getStarRating()));

            var selectedBeatmap = selectedBeatmapItem != null ? selectedBeatmapItem.getBeatmapInfo() : null;

            for (int i = 0; i < beatmapItems.length; i++) {
                var item = beatmapItems[i];

                if (item == null) {
                    continue;
                }

                item.setBeatmapInfo(beatmapSetInfo.getBeatmap(i));

                // Ensure the selected track is still selected after reloading.
                if (selectedBeatmap != null && selectedBeatmap == beatmapSetInfo.getBeatmap(i)) {
                    item.setSelectedColor();
                    selectedBeatmapItem = item;
                } else {
                    item.setDeselectColor();
                }
            }
        } else {
            beatmapItems[0].setBeatmapInfo(beatmapSetInfo.getBeatmap(beatmapId));
        }
    }

    private synchronized void initBeatmaps() {
        if (beatmapId == -1) {
            // Tracks are originally sorted by osu!droid difficulty, so for osu!standard difficulty they need to be sorted again.
            beatmapSetInfo.getBeatmaps().sort((o1, o2) -> Float.compare(o1.getStarRating(), o2.getStarRating()));

            for (int i = 0; i < beatmapItems.length; i++) {
                var item = SongMenuPool.getInstance().newBeatmapItem();
                item.setItem(this);
                item.setBeatmapInfo(beatmapSetInfo.getBeatmap(i));
                if (item.getParent() == null) {
                    layer.plusAssign(item);
                }
                // The AndEngine touch area was registered again, after all the others.
                if (layer instanceof SongMenu.ListLayer listLayer) {
                    listLayer.moveToDispatchEnd(item);
                }
                item.setVisible(true);

                beatmapItems[i] = item;
            }
        } else {
            var item = SongMenuPool.getInstance().newBeatmapItem();
            item.setItem(this);
            item.setBeatmapInfo(beatmapSetInfo.getBeatmap(beatmapId));
            if (item.getParent() == null) {
                layer.plusAssign(item);
            }
            // The AndEngine touch area was registered again, after all the others.
            if (layer instanceof SongMenu.ListLayer listLayer) {
                listLayer.moveToDispatchEnd(item);
            }
            item.setVisible(true);

            beatmapItems[0] = item;
        }
    }

    public boolean isFavorite() {
        if (favorite != null) {
            return favorite;
        }

        var props = DatabaseManager.getBeatmapOptionsTable().getOptions(beatmapSetInfo.getDirectory());
        favorite = props != null && props.isFavorite();
        return favorite;
    }

    public void setFavorite(final boolean favorite) {
        this.favorite = favorite;
    }

    public void showPropertiesMenu() {
        if (listener.get() != null) {
            listener.get().showPropertiesMenu(this);
        }
    }

    public void update(final float dt) {
        if (deleted) {
            return;
        }
        for (final BeatmapItem tr : beatmapItems) {
            if (tr != null) {
                tr.update(dt);
            }
        }
    }

    public BeatmapInfo getFirstBeatmap(){
        return beatmapSetInfo.getBeatmap(Math.max(beatmapId, 0));
    }

    public void removeFromScene() {
        if (layer == null) {
            return;
        }
        if (selected) {
            deselect();
        }
        freeBackground();
        visible = false;
        layer = null;
    }

    public int tryGetCorrespondingBeatmapId(String beatmapFilename){
        if (beatmapId <= -1){
            for (var i = beatmapSetInfo.getCount() - 1; i >= 0; i--) {
                if (beatmapSetInfo.getBeatmap(i).getFilename().equals(beatmapFilename)){
                    return i;
                }
            }
        } else if (beatmapSetInfo.getBeatmap(beatmapId).getFilename().equals(beatmapFilename)){
            return beatmapId;
        }
        return -1;
    }

    public synchronized BeatmapItem getBeatmapSpritesById(int index){
        return beatmapItems[index % beatmapItems.length];
    }
}
