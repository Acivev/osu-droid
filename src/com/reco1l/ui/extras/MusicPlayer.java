package com.reco1l.ui.extras;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.recyclerview.widget.LinearLayoutManager.VERTICAL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.reco1l.Game;
import com.reco1l.UI;
import com.reco1l.ui.extras.MusicPlayer.PlaylistAdapter.ViewHolder;
import com.reco1l.utils.Animation;
import com.reco1l.utils.ScheduledTask;
import com.reco1l.utils.ViewUtils;
import com.reco1l.utils.helpers.BeatmapHelper;
import com.reco1l.ui.platform.UIFragment;
import com.reco1l.utils.AsyncExec;
import com.reco1l.utils.Res;
import com.reco1l.utils.helpers.BitmapHelper;
import com.reco1l.utils.listeners.TouchListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import ru.nsu.ccfit.zuev.audio.Status;
import ru.nsu.ccfit.zuev.osu.BeatmapInfo;
import ru.nsu.ccfit.zuev.osu.TrackInfo;
import ru.nsu.ccfit.zuev.osuplus.R;

// Created by Reco1l on 1/7/22 22:45

public class MusicPlayer extends UIFragment {

    public static MusicPlayer instance;

    private SeekBar seekBar;
    private RecyclerView list;
    private View body, songBody;
    private ImageView play, songImage;
    private TextView title, artist, time, length;

    private AsyncExec bitmapTask;
    private SimpleDateFormat sdf;

    private int toPosition;

    private boolean
            isListVisible = false,
            isTrackingTouch = false,
            wasPlaying = false;

    //--------------------------------------------------------------------------------------------//

    @Override
    protected int getLayout() {
        return R.layout.music_player;
    }

    @Override
    protected String getPrefix() {
        return "mp";
    }

    @Override
    protected long getDismissTime() {
        return 10000;
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    protected void onLoad() {
        isListVisible = false;

        setDismissMode(true, true);
        sdf = new SimpleDateFormat("mm:ss");

        body = find("body");
        list = find("listRv");
        seekBar = find("seekBar");
        songBody = find("songBody");
        songImage = find("songImage");

        length = find("songLength");
        time = find("songProgress");
        artist = find("artist");
        title = find("title");
        play = find("play");

        Animation.of(body)
                .fromHeight(Res.dimen(R.dimen._30sdp))
                .toHeight(Res.dimen(R.dimen.musicPlayerHeight))
                .fromY(-30)
                .toY(0)
                .fromAlpha(0)
                .toAlpha(1)
                .play(200);

        Animation.of(find("innerBody"))
                .fromAlpha(0)
                .toAlpha(1)
                .play(200);

        bindTouchListener(play, new TouchListener() {
            public boolean hasTouchEffect() {
                return false;
            }

            public void onPressUp() {
                if (global.getSongService().getStatus() == Status.PLAYING) {
                    Game.musicManager.pause();
                } else {
                    Game.musicManager.play();
                }
            }
        });

        bindTouchListener(find("list"), this::switchListVisibility);
        bindTouchListener(find("prev"), Game.musicManager::previous);
        bindTouchListener(find("next"), Game.musicManager::next);

        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            public void onStartTrackingTouch(SeekBar seekBar) {
                isTrackingTouch = true;
                onTouchEventNotified(MotionEvent.ACTION_DOWN);
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
                if (fromTouch) {
                    toPosition = progress;
                    time.setText(sdf.format(progress));
                }
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                isTrackingTouch = false;
                Game.songService.setPosition(toPosition);
                onTouchEventNotified(MotionEvent.ACTION_UP);
            }
        });

        ViewUtils.width(find("listBody"), 0);

        BeatmapInfo beatmap = Game.library.getBeatmap();

        list.setLayoutManager(new LinearLayoutManager(Game.activity, VERTICAL, false));
        list.setAdapter(new PlaylistAdapter(Game.library.getLibrary()));

        if (beatmap != null) {
            loadMetadata(beatmap);

            ScheduledTask.run(() -> loadBitmap(beatmap.getTrack(0)), 200);
            list.postDelayed(() -> select(beatmap), 200);
        }
    }

    @Override
    protected void onUpdate(float elapsed) {
        int length = Game.songService.getLength();
        int position = Game.songService.getPosition();

        if (!isTrackingTouch) {
            seekBar.setMax(length);
            seekBar.setProgress(position);
            time.setText(sdf.format(position));
        }

        if (Game.musicManager.isPlaying() && !wasPlaying) {
            wasPlaying = true;

            Animation.of(play)
                    .fromRotation(180)
                    .toRotation(0)
                    .runOnEnd(() -> play.setImageDrawable(Res.drw(R.drawable.v_pause_xl)))
                    .play(160);

        } else if (!Game.musicManager.isPlaying() && wasPlaying) {
            wasPlaying = false;

            Animation.of(play)
                    .fromRotation(180)
                    .toRotation(0)
                    .runOnEnd(() -> play.setImageDrawable(Res.drw(R.drawable.v_play_xl_circle)))
                    .play(160);
        }
    }

    //--------------------------------------------------------------------------------------------//

    private void switchListVisibility() {
        if (!isListVisible) {
            isListVisible = true;
            Animation.of(find("listBody"))
                    .toWidth(Res.dimen(R.dimen.musicPlayerListWidth))
                    .play(300);
        } else {
            isListVisible = false;
            Animation.of(find("listBody"))
                    .toWidth(0)
                    .play(300);
        }
    }

    //--------------------------------------------------------------------------------------------//

    public void changeMusic(BeatmapInfo beatmap) {
        if (isShowing()) {
            select(beatmap);
            loadBitmap(beatmap.getTrack(0));

            Animation.of(songBody)
                    .toAlpha(0)
                    .runOnEnd(() -> {
                        loadMetadata(beatmap);

                        Animation.of(songBody)
                                .toX(0)
                                .toAlpha(1)
                                .play(200);
                    })
                    .play(200);
        }
    }

    private void loadMetadata(BeatmapInfo beatmap) {
        title.setText(BeatmapHelper.getTitle(beatmap));
        artist.setText(BeatmapHelper.getArtist(beatmap));
        length.setText(sdf.format(Game.songService.getLength()));
    }

    private void loadBitmap(TrackInfo track) {
        if (bitmapTask != null) {
            bitmapTask.cancel(true);
        }

        bitmapTask = new AsyncExec() {
            Bitmap bitmap;

            public void run() {
                if (track.getBackground() != null) {
                    bitmap = BitmapFactory.decodeFile(track.getBackground());

                    float scale = (float) songImage.getWidth() / bitmap.getWidth();

                    bitmap = BitmapHelper.resize(bitmap, bitmap.getWidth() * scale, bitmap.getHeight() * scale);
                    bitmap = BitmapHelper.cropInCenter(bitmap, songImage.getWidth(), songImage.getHeight());
                }
            }

            public void onComplete() {
                Animation.of(songImage)
                        .toAlpha(0)
                        .runOnEnd(() -> {
                            songImage.setImageBitmap(bitmap);

                            Animation.of(songImage)
                                    .toAlpha(1)
                                    .play(200);
                        })
                        .play(100);
            }
        };
        bitmapTask.execute();
    }

    //--------------------------------------------------------------------------------------------//

    public void select(BeatmapInfo beatmap) {
        int i = 0;
        while (i < list.getChildCount()) {
            View child = list.getChildAt(i);
            ViewHolder holder = (ViewHolder) list.getChildViewHolder(child);

            if (beatmap.equals(holder.beatmap)) {
                holder.onSelect();
            }
            ++i;
        }
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    public void show() {
        if (!isShowing) {
            Game.platform.close(UI.getExtras());
            UI.topBar.musicButton.animateButton(true);
            super.show();
        }
    }

    @Override
    public void close() {
        if (isShowing) {

            if (isListVisible) {
                switchListVisibility();
            }
            UI.topBar.musicButton.animateButton(false);

            Animation.of(find("innerBody"))
                    .toAlpha(0)
                    .play(100);

            Animation.of(body)
                    .fromHeight(Res.dimen(R.dimen.musicPlayerHeight))
                    .toHeight(Res.dimen(R.dimen._30sdp))
                    .runOnEnd(super::close)
                    .toY(-30)
                    .toAlpha(0)
                    .play(240);
        }
    }

    //--------------------------------------------------------------------------------------------//

    static class PlaylistAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final ArrayList<BeatmapInfo> beatmaps;

        private ViewHolder selected;

        //----------------------------------------------------------------------------------------//

        public PlaylistAdapter(ArrayList<BeatmapInfo> beatmaps) {
            this.beatmaps = beatmaps;

            setHasStableIds(true);
        }

        //----------------------------------------------------------------------------------------//

        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView view = new TextView(Game.activity);

            view.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            Drawable drawable = Res.drw(R.drawable.shape_rounded).mutate();
            drawable.setTint(Res.color(R.color.accent));
            drawable.setAlpha(0);

            view.setBackground(drawable);
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setSingleLine(true);

            int m = Res.dimen(R.dimen.M);
            int s = Res.dimen(R.dimen.S);
            view.setPadding(m, s, m, s);

            return new ViewHolder(view, this);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(beatmaps.get(position));
        }

        //----------------------------------------------------------------------------------------//

        @Override
        public int getItemCount() {
            return beatmaps.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        //----------------------------------------------------------------------------------------//

        static class ViewHolder extends RecyclerView.ViewHolder {

            private final PlaylistAdapter parent;
            private final TextView text;

            private BeatmapInfo beatmap;

            //------------------------------------------------------------------------------------//

            public ViewHolder(@NonNull View itemView, PlaylistAdapter parent) {
                super(itemView);
                this.parent = parent;
                this.text = (TextView) itemView;
            }

            //------------------------------------------------------------------------------------//

            public void bind(BeatmapInfo beatmap) {
                this.beatmap = beatmap;

                text.setText(BeatmapHelper.getArtist(beatmap) + " - " + BeatmapHelper.getTitle(beatmap));

                UI.musicPlayer.bindTouchListener(text, () -> {
                    if (Game.library.getBeatmap() != beatmap) {
                        Game.musicManager.change(beatmap);
                        onSelect();
                    }
                });
            }

            //------------------------------------------------------------------------------------//

            public void onSelect() {
                if (parent.selected != null && parent.selected != this) {
                    parent.selected.onDeselect();
                }
                if (parent.selected == this) {
                    return;
                }
                parent.selected = this;

                Drawable background = text.getBackground();

                Animation.ofInt(background.getAlpha(), 60)
                        .runOnUpdate(value -> {
                            background.setAlpha((int) value);
                            text.setBackground(background);
                        })
                        .play(200);

                Animation.ofColor(Color.WHITE, Res.color(R.color.accent))
                        .runOnUpdate(value -> text.setTextColor((int) value))
                        .play(200);
            }

            public void onDeselect() {
                if (parent.selected != this) {
                    return;
                }
                parent.selected = null;

                Drawable background = text.getBackground();

                Animation.ofInt(background.getAlpha(), 0)
                        .runOnUpdate(value -> {
                            background.setAlpha((int) value);
                            text.setBackground(background);
                        })
                        .play(200);

                Animation.ofColor(Res.color(R.color.accent), Color.WHITE)
                        .runOnUpdate(value -> text.setTextColor((int) value))
                        .play(200);
            }
        }
    }
}
