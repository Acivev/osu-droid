package ru.nsu.ccfit.zuev.osu;

import static kotlin.collections.ArraysKt.any;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.osudroid.resources.SoundStore;
import com.osudroid.resources.TextureEntry;
import com.osudroid.resources.TextureStore;
import com.osudroid.resources.skin.AnimatableFrameMatch;
import com.osudroid.resources.skin.SkinFileCatalog;
import com.osudroid.ui.verktex.SkinTextures;
import com.osudroid.resources.skin.SkinTextureRules;
import com.osudroid.resources.skin.SoundResolution;
import com.osudroid.resources.skin.TextureResolution;
import com.osudroid.ui.skinning.IniReader;
import com.osudroid.ui.skinning.SkinConverter;
import com.reco1l.andengine.UIEngine;
import org.andengine.engine.Engine;
import ru.nsu.ccfit.zuev.osu.helper.Debug;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

import ru.nsu.ccfit.zuev.audio.BassSoundProvider;
import ru.nsu.ccfit.zuev.osu.helper.FileUtils;
import ru.nsu.ccfit.zuev.osu.helper.MD5Calculator;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osuplus.BuildConfig;
import ru.nsu.ccfit.zuev.skins.OsuSkin;
import ru.nsu.ccfit.zuev.skins.SkinJsonReader;
import ru.nsu.ccfit.zuev.skins.BeatmapSkinManager;

public class ResourceManager {

    /**
     * The textures that can be animated.
     */
    private static final String[] ANIMATABLE_TEXTURES = SkinTextureRules.ANIMATABLE_TEXTURES;

    private final static ResourceManager mgr = new ResourceManager();

    // These caches are written from background loading threads (e.g. beatmap skin loading during
    // gameplay start, see BeatmapSkinManager) while being read concurrently from the update/GL thread
    // during rendering - the stores are ConcurrentHashMap-backed internally specifically to stay safe
    // under that access pattern.
    private final SoundStore soundStore = new SoundStore();
    private final SoundStore customSoundStore = new SoundStore();
    private final TextureStore textureStore = new TextureStore();
    private final TextureStore customTextureStore = new TextureStore();
    /**
     * The beatmap file behind each {@link #customTextureStore} key, for the Verktex HUD (SkinTextures).
     */
    private final Map<String, File> customTextureFiles = new ConcurrentHashMap<>();

    private Engine engine;
    private Context context;
    private String backgroundFile;

    private ResourceManager() {
    }

    public static ResourceManager getInstance() {
        return mgr;
    }

    public Engine getEngine() {
        return engine;
    }

    public void Init(final Engine engine, final Context context) {
        this.engine = engine;
        this.context = context;

        soundStore.init(context);
        customSoundStore.init(context);
        textureStore.init(context);
        customTextureStore.init(context);

        textureStore.clear();
        soundStore.clear();

        customSoundStore.clear();
        customTextureStore.clear();
        customTextureFiles.clear();

        initSecurityUtils();
    }

    public void loadSkin(String folder) {

        if (!folder.endsWith("/"))
            folder = folder + "/";

        loadCustomSkin(folder);

        loadTexture("defaultapproachcircle", "gfx/approachcircle.png", false);
        loadTexture("ranking_enabled_score", "ranking_enabled_score.png", false);
        loadTexture("ranking_enabled_pp", "ranking_enabled_pp.png", false);
        loadTexture("ranking_disabled", "ranking_disabled.png", false);
        loadTexture("flashlight_cursor", "flashlight_cursor.png", false);

        textureStore.markAbsent("lighting");

        UIEngine.getCurrent().onSkinChange();
    }

    public void loadCustomSkin(String folder) {

        if (!folder.endsWith("/")) folder += "/";

        File[] skinFiles = null;
        File skinFolder = new File(folder);
        if (!skinFolder.exists()) {
            skinFolder = null;
        } else {
            skinFiles = FileUtils.listFiles(skinFolder);
        }
        if (skinFiles != null) {
            JSONObject skinjson = null;
            File jsonFile = new File(folder, "skin.json");
            if (jsonFile.exists()) {
                try {
                    skinjson = new JSONObject(OsuSkin.readFull(jsonFile));

                    // Backfill any skin.ini derived keys missing
                    var iniFile = new File(folder, "skin.ini");

                    if (iniFile.exists()) {
                        try (var ini = new IniReader(iniFile)) {
                            SkinConverter.mergeMissing(skinjson, SkinConverter.convertToJson(ini));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                var iniFile = new File(folder, "skin.ini");

                if (iniFile.exists()) {
                    GlobalManager.getInstance().setInfo("Reading skin.ini...");

                    try (var ini = new IniReader(iniFile)) {
                        skinjson = SkinConverter.convertToJson(ini);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    SkinConverter.ensureOptionalTexture(new File(folder, "sliderendcircle.png"));
                    SkinConverter.ensureOptionalTexture(new File(folder, "sliderendcircleoverlay.png"));

                    SkinConverter.ensureTexture(new File(folder, "selection-mods.png"));
                    SkinConverter.ensureTexture(new File(folder, "selection-random.png"));
                    SkinConverter.ensureTexture(new File(folder, "selection-options.png"));

                    skinFiles = FileUtils.listFiles(skinFolder);
                }
            }
            if (skinjson == null) skinjson = new JSONObject();
            SkinJsonReader.getReader().supplyJson(skinjson);
        }
        final Map<String, File> availableFiles = skinFiles != null
                ? SkinFileCatalog.buildAvailableFiles(Arrays.asList(skinFiles))
                : Collections.emptyMap();

        // Removing loaded animatable textures from the previous skin.
        for (var key : textureStore.getKeys().toArray(new String[0])) {
            if (any(ANIMATABLE_TEXTURES, key::startsWith)) {
                unloadTexture(key);
            }
        }

        textureStore.clearFrameCounts();
        customTextureStore.clearFrameCounts();

        try {

            List<String> availableAnimatableFilenames = SkinFileCatalog.animatableFilenames(availableFiles);

            boolean isDefaultSkin = Objects.equals(folder, Config.getSkinTopPath());

            List<String> gfxAssetFileNames = Arrays.asList(Objects.requireNonNull(context.getAssets().list("gfx")));

            for (var entry : SkinFileCatalog.resolveDefaultTextures(gfxAssetFileNames, availableFiles, availableAnimatableFilenames, isDefaultSkin)) {
                applyTextureResolution(entry.getFirst(), entry.getSecond());
            }

            for (var entry : SkinFileCatalog.resolveKidangerTextures(availableFiles)) {
                applyTextureResolution(entry.getFirst(), entry.getSecond());
            }

            for (var entry : SkinFileCatalog.resolveComboburstTextures(availableFiles)) {
                applyTextureResolution(entry.getFirst(), entry.getSecond());
            }

            for (var entry : SkinFileCatalog.resolveAnimatableTextures(availableFiles, availableAnimatableFilenames)) {
                applyAnimatableTextureResolution(entry.getFirst(), entry.getSecond());
            }

        } catch (final IOException e) {
            Debug.e("Resources: " + e.getMessage(), e);
        }

        try {
            // TODO: buggy?
            List<String> sfxAssetFileNames = Arrays.asList(Objects.requireNonNull(context.getAssets().list("sfx")));

            for (var entry : SkinFileCatalog.resolveDefaultSounds(sfxAssetFileNames, availableFiles)) {
                applySoundResolution(entry.getFirst(), entry.getSecond());
            }

            if (skinFolder != null) {
                for (var entry : SkinFileCatalog.comboburstSoundPaths(folder)) {
                    loadSound(entry.getFirst(), entry.getSecond(), true);
                }
            }
        } catch (final IOException e) {
            Debug.e("Resources: " + e.getMessage(), e);
        }

        loadTexture("ranking_button", "ranking_button.png", false);
        loadTexture("ranking_enabled_score", "ranking_enabled_score.png", false);
        loadTexture("ranking_enabled_pp", "ranking_enabled_pp.png", false);
        loadTexture("ranking_disabled", "ranking_disabled.png", false);
        loadTexture("selection-approved", "selection-approved.png", false);
        loadTexture("selection-loved", "selection-loved.png", false);
        loadTexture("selection-question", "selection-question.png", false);
        loadTexture("selection-ranked", "selection-ranked.png", false);
        textureStore.markAbsent("lighting");
    }

    /**
     * Applies a {@link TextureResolution} produced by {@link SkinFileCatalog#resolveDefaultTextures},
     * {@link SkinFileCatalog#resolveKidangerTextures}, or {@link SkinFileCatalog#resolveComboburstTextures}.
     * Only the {@link TextureResolution.FromDefaultAsset} branch parses a frame index - matching
     * {@code loadCustomSkin}'s original behavior, where a custom skin file never triggered
     * {@code parseFrameIndex} in this part of the loading sequence (only the animatable pass did,
     * see {@link #applyAnimatableTextureResolution}).
     */
    private void applyTextureResolution(String name, TextureResolution resolution) {
        if (resolution instanceof TextureResolution.FromFile) {
            loadTexture(name, ((TextureResolution.FromFile) resolution).getFile().getPath(), true);
        } else if (resolution instanceof TextureResolution.FromDefaultAsset) {
            loadTexture(name, "gfx/" + ((TextureResolution.FromDefaultAsset) resolution).getAssetFileName(), false);
            parseFrameIndex(name, false, false);
        } else {
            unloadTexture(name);
        }
    }

    /**
     * Applies a {@link TextureResolution} produced by {@link SkinFileCatalog#resolveAnimatableTextures}.
     * Unlike {@link #applyTextureResolution}, the {@link TextureResolution.FromFile} branch parses a
     * frame index here - matching {@code loadCustomSkin}'s original animatable-texture pass.
     */
    private void applyAnimatableTextureResolution(String name, TextureResolution resolution) {
        if (resolution instanceof TextureResolution.FromFile) {
            loadTexture(name, ((TextureResolution.FromFile) resolution).getFile().getPath(), true);
            parseFrameIndex(name, false, false);
        } else {
            unloadTexture(name);
        }
    }

    private void applySoundResolution(String name, SoundResolution resolution) {
        if (resolution instanceof SoundResolution.FromFile) {
            loadSound(name, ((SoundResolution.FromFile) resolution).getFile().getPath(), true);
        } else {
            loadSound(name, "sfx/" + ((SoundResolution.FromDefaultAsset) resolution).getAssetFileName(), false);
        }
    }

    /**
     * Parses the frame count from the filename and updates the relevant TextureStore's frame count.
     *
     * @param filename The filename, this shouldn't contain the file extension.
     * @param checkFirstFrameExists Whether to check if the first frame is loaded or not,
     *                              if this is set to true and the first frame is not
     *                              loaded, the frame count will not be parsed.
     * @param isBeatmapSkin Whether the frame is from a beatmap skin or not.
     *
     * @return The frame index parsed from the filename, or -1 if the frame count could not be parsed.
     */
    private int parseFrameIndex(String filename, boolean checkFirstFrameExists, boolean isBeatmapSkin) {

        // If match is null, the filename does not belong to any animatable texture.
        AnimatableFrameMatch match = SkinTextureRules.matchAnimatableFrame(filename);

        String textureName = match != null ? match.getTextureName() : filename;
        int frameIndex = match != null ? match.getFrameIndex() : 0;

        var skinTextureStore = isBeatmapSkin ? customTextureStore : textureStore;

        if (match == null || checkFirstFrameExists
                && !skinTextureStore.containsKey(textureName)
                && !skinTextureStore.containsKey(textureName + "-0")
                && !skinTextureStore.containsKey(textureName + "0")) {
            skinTextureStore.removeFrameCount(textureName);
            return -1;
        }

        skinTextureStore.growFrameCount(textureName, frameIndex + 1);

        if (BuildConfig.DEBUG) {
            Log.v("ResourceManager", "Parsed frame index: " + frameIndex + " from " + filename);
        }

        return frameIndex;
    }

    public TextureEntry loadTexture(final String resname, final String file,
                                    final boolean external) {
        return textureStore.load(resname, file, external);
    }

    public TextureEntry loadBackground(final String file) {
        backgroundFile = file;
        if (textureStore.containsKey("::background")) {
            // Deliberately not textureStore.unload(name) here, which would also remove the map
            // entry - the original code only unloaded the GL texture at this point and left the
            // (now stale, about to be overwritten below) entry in the map, since getTexture/
            // getTextureIfLoaded("::background") is read externally (GameScene, ScoringScene,
            // GameLoaderScene, RoomScene, LobbyScene) and must keep seeing *something* until this
            // method's own textureStore.put(...) calls below replace it. The catalog has no GL
            // texture; the check (and its throw for an absent slot) stays.
            Objects.requireNonNull(textureStore.get("::background"));
        }
        if (file == null) {
            return textureStore.get("menu-background");
        }

        final TextureEntry entry = TextureStore.decodeFile(new File(file), 1, TextureEntry.Source.SkinFile);
        if (entry == null) {
            TextureEntry fallback = textureStore.get("menu-background");
            textureStore.put("::background", fallback);
            return fallback;
        }

        textureStore.put("::background", entry);
        return entry;
    }

    /**
     * {@link #loadBackground(String)} with the background already decoded, as the song menu hands it
     * over. The bitmap is only measured, not decoded again.
     */
    public TextureEntry loadBackground(final String file, final Bitmap bitmap) {
        backgroundFile = file;
        if (textureStore.containsKey("::background")) {
            // See loadBackground(String).
            Objects.requireNonNull(textureStore.get("::background"));
        }
        if (file == null) {
            return textureStore.get("menu-background");
        }

        if (bitmap == null) {
            TextureEntry fallback = textureStore.get("menu-background");
            textureStore.put("::background", fallback);
            return fallback;
        }

        final TextureEntry entry = new TextureEntry(TextureEntry.Source.HighQualityFile, new File(file), bitmap.getWidth(), bitmap.getHeight());
        textureStore.put("::background", entry);
        return entry;
    }

    /**
     * The file of the last {@link #loadBackground} call, null before any.
     */
    public String getBackgroundFile() {
        return backgroundFile;
    }

    public TextureEntry loadHighQualityAsset(final String resname,
                                             final String file) {
        return textureStore.loadHighQualityAsset(resname, file);
    }

    public TextureEntry loadHighQualityFile(final String resname, final File file) {
        return textureStore.loadHighQualityFile(resname, file);
    }

    public TextureEntry getTexture(final String resname) {
        if (BeatmapSkinManager.isSkinEnabled() && customTextureStore.containsKey(resname)) {
            return customTextureStore.get(resname);
        }
        if (!textureStore.containsKey(resname)) {
            Debug.i("Loading texture: " + resname);

            return loadTexture(resname, "gfx/" + resname + ".png", false);
        }
        return textureStore.get(resname);
    }

    public TextureEntry getAvatarTextureIfLoaded(final String avatarURL) {
        var region = getTextureIfLoaded(MD5Calculator.getStringMD5(avatarURL));

        if (region == null) {
            region = getTextureIfLoaded(MD5Calculator.getStringMD5(OnlineManager.defaultAvatarURL));
        }

        return region;
    }

    public TextureEntry getProfileBannerTextureIfLoaded(final String bannerURL) {
        if (bannerURL == null || bannerURL.length() == 0) {
            return null;
        }

        return getTextureIfLoaded(MD5Calculator.getStringMD5(bannerURL));
    }

    public TextureEntry getTextureIfLoaded(final String resname) {
        if (textureStore.containsKey(resname)) {
            return textureStore.get(resname);
        }
        return null;
    }

    public boolean isTextureLoaded(final String resname) {
        return textureStore.containsKey(resname);
    }

    public BassSoundProvider loadSound(final String resname, final String file,
                                       final boolean external) {
        return soundStore.load(resname, file, external);
    }

    public BassSoundProvider getSound(final String name) {
        return getSound(name, true);
    }

    public BassSoundProvider getSound(final String name, final boolean defaultToEmpty) {
        var sound = soundStore.get(name);

        if (sound == null && defaultToEmpty) {
            return BassSoundProvider.EMPTY;
        }

        return sound;
    }

    public void loadCustomSound(final File file) {
        BassSoundProvider snd = new BassSoundProvider();
        String resName = file.getName();
        resName = resName.substring(0, resName.length() - 4);
        if (resName.length() == 0) {
            return;
        }
        Pattern pattern = Pattern.compile("([^\\d.]+)");
        Matcher matcher = pattern.matcher(resName);
        if (matcher.find()) {
            String setName = matcher.group(1);
            if (!soundStore.containsKey(setName)) {
                // 剔除未知的音频文件
                return;
            }
        }
        try {
            if (!snd.prepare(file.getPath())) {
                return;
            }
        } catch (final Exception e) {
            Debug.e("ResourceManager.loadCustomSound: " + e.getMessage(), e);
            return;
        }

        customSoundStore.put(resName, snd);
    }

    public BassSoundProvider getCustomSound(final String name, final boolean defaultToEmpty) {
        if (BeatmapSkinManager.isSkinEnabled() && customSoundStore.containsKey(name)) {
            return customSoundStore.get(name);
        }

        return getSound(name, defaultToEmpty);
    }

    public BassSoundProvider getCustomSound(final String resname, final int set) {
        if (!BeatmapSkinManager.isSkinEnabled()) {
            return getSound(resname);
        }
        if (set >= 2) {
            String fullName = resname + set;
            if (customSoundStore.containsKey(fullName)) {
                return customSoundStore.get(fullName);
            } else {
                return soundStore.get(resname);
            }
        }
        if (customSoundStore.containsKey(resname)) {
            return customSoundStore.get(resname);
        }

        return soundStore.get(resname);
    }

    public TextureEntry loadCustomTexture(final File file) {
        String resname = file.getName();
        resname = resname.substring(0, resname.length() - 4).toLowerCase();
        boolean multiframe = false;

        String delimiter = "-";

        if (parseFrameIndex(resname, true, true) < 0 && !textureStore.containsKey(resname)) {
            if (textureStore.containsKey(resname + "-0") || textureStore.containsKey(resname + "0")) {
                if (textureStore.containsKey(resname + "0")) {
                    delimiter = "";
                }
                multiframe = true;
            } else {
                return null;
            }
        }
        TextureEntry region = TextureStore.decodeFile(file, 1, TextureEntry.Source.BeatmapSkin);

        if (region == null) {
            return null;
        }
        if (multiframe) {
            int i = 0;
            while (textureStore.containsKey(resname + delimiter + i)) {
                customTextureStore.put(resname + delimiter + i, region);
                customTextureFiles.put(resname + delimiter + i, file);
                i++;
            }
        } else {
            customTextureStore.put(resname, region);
            customTextureFiles.put(resname, file);

            if (resname.equals("hitcircle")) {
                if (!customTextureStore.containsKey("sliderstartcircle")) {
                    customTextureStore.put("sliderstartcircle", region);
                    customTextureFiles.put("sliderstartcircle", file);
                }

                if (!customTextureStore.containsKey("sliderendcircle")) {
                    customTextureStore.put("sliderendcircle", region);
                    customTextureFiles.put("sliderendcircle", file);
                }
            }

            if (resname.equals("hitcircleoverlay")) {
                if (!customTextureStore.containsKey("sliderstartcircleoverlay")) {
                    customTextureStore.put("sliderstartcircleoverlay", region);
                    customTextureFiles.put("sliderstartcircleoverlay", file);
                }

                if (!customTextureStore.containsKey("sliderendcircleoverlay")) {
                    customTextureStore.put("sliderendcircleoverlay", region);
                    customTextureFiles.put("sliderendcircleoverlay", file);
                }
            }
        }
        return region;
    }

    public void unloadTexture(final String name) {
        textureStore.unload(name);
    }

    public void initSecurityUtils() {
        SecurityUtils.getAppSignature(context, context.getPackageName());
    }

    public void clearCustomResources() {
        for (final BassSoundProvider s : customSoundStore.getValues()) {
            s.free();
        }
        customTextureStore.clear();
        customTextureFiles.clear();
        customSoundStore.clear();

        SkinTextures.releaseBeatmapSkin();
    }

    /**
     * The beatmap skin file loaded for a texture name, or null when the beatmap skin has none.
     */
    public File getCustomTextureFile(final String resname) {
        return customTextureFiles.get(resname);
    }

    public int getFrameCount(final String texname) {
        if (BeatmapSkinManager.isSkinEnabled()) {
            int custom = customTextureStore.getFrameCount(texname);
            if (custom != -1) {
                return custom;
            }
        }

        return textureStore.getFrameCount(texname);
    }
}
