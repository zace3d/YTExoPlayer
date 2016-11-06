package xyz.edgarzavala.youtubeexoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelections;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.FileAsyncHttpResponseHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

import cz.msebera.android.httpclient.Header;
import xyz.edgarzavala.youtubeexoplayer.dialogues.Dialogues;
import xyz.edgarzavala.youtubeexoplayer.listeners.YTExoplayer;

/**
 * Created by Edgar Z. on 11/2/16.
 */

public class YTPlayerView extends TextureView implements ExoPlayer.EventListener,
        TrackSelector.EventListener<MappingTrackSelector.MappedTrackInfo>, SimpleExoPlayer.VideoListener,
        YTExoplayer, View.OnTouchListener {

    private static final String TAG = YTPlayerView.class.getName();
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    private Handler mainHandler;
    private Timeline.Window window;

    private DataSource.Factory mediaDataSourceFactory;
    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;
    private boolean playerNeedsSource;

    private boolean shouldAutoPlay;
    private boolean isTimelineStatic;
    private int playerWindow;
    private long playerPosition;

    private UriSimple uriSample;

    private String mYTVideoId;

    private boolean looping;
    private int loopCount;

    private boolean mute;

    public enum State {
        UNINITIALIZED, PLAY, STOP, PAUSE, END
    }

    private State mState;

    private boolean mIsDataSourceSet;
    private boolean mIsVideoPrepared;

    private GestureDetector mGestureDetector;

    private final VideoBufferingUpdater mBufferingUpdater = new VideoBufferingUpdater();
    private final VideoProgressUpdater mProgressUpdater = new VideoProgressUpdater();

    public YTPlayerView(Context context) {
        super(context);

        if (!isInEditMode())
            initPlayer();
    }

    public YTPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!isInEditMode())
            initPlayer();
    }

    public YTPlayerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isInEditMode())
            initPlayer();
    }

    private void initPlayer() {
        shouldAutoPlay = true;
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();
        window = new Timeline.Window();

        mState = State.UNINITIALIZED;
        mIsVideoPrepared = false;
        mIsDataSourceSet = false;

        mGestureDetector = new GestureDetector(getContext(), mGestureListener);
    }

    public void setYTVideoId(@NonNull String id) {
        mIsDataSourceSet = true;

        mYTVideoId = id;
    }

    public void prepare() {
        if (!mIsDataSourceSet) {
            Dialogues.Log(TAG, "prepare() was called but data source was not set.", Log.DEBUG);
            return;
        }

        fetchDashInfo(mYTVideoId);
    }

    private void fetchDashInfo(@NonNull String uid) {
        String getVideoInfoUrl = "http://www.youtube.com/get_video_info?&video_id=%s";
        AsyncHttpClient client = new AsyncHttpClient();
        client.get(String.format(Locale.getDefault(), getVideoInfoUrl, uid),
                new FileAsyncHttpResponseHandler(getContext()) {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, File response) {
                        Dialogues.Log(TAG, "/***dashmpd onSuccess", Log.ERROR);

                        // Do something with the file `response`
                        String text = null;
                        try {
                            text = getStringFromFile(response);
                            String dashmpd = findDashmpd(text);
                            uriSample = new UriSimple(dashmpd, "mpd");

                            initializePlayer();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, File file) {
                        Dialogues.Log(TAG, "/***dashmpd onFailure", Log.ERROR);

                        setOnError("Can not fetch video info");
                    }
                });
    }

    public void seekTo(int position) {
        player.seekTo(position);
        player.setPlayWhenReady(true);
    }

    public void play() {
        if (player == null)
            return;

        if (!mIsDataSourceSet) {
            Dialogues.Log(TAG, "play() was called but data source was not set.", Log.DEBUG);
            return;
        }

        if (!mIsVideoPrepared) {
            Dialogues.Log(TAG, "play() was called but video is not prepared yet, waiting.", Log.DEBUG);
            return;
        }

        if (mState == State.PLAY) {
            Dialogues.Log(TAG, "play() was called but video is already playing.", Log.DEBUG);
            return;
        }

        if (mState == State.PAUSE) {
            Dialogues.Log(TAG, "play() was called but video is paused, resuming.", Log.DEBUG);
            mState = State.PLAY;
            player.setPlayWhenReady(true);
            return;
        }

        if (mState == State.END || mState == State.STOP) {
            Dialogues.Log(TAG, "play() was called but video already ended, starting over.", Log.DEBUG);
            mState = State.PLAY;
            player.seekTo(0);
            player.setPlayWhenReady(true);
            return;
        }

        mState = State.PLAY;
        player.setPlayWhenReady(true);
        mBufferingUpdater.start();
        mProgressUpdater.start();
    }

    public void pause() {
        if (player == null)
            return;

        if (mState == State.PAUSE) {
            Dialogues.Log(TAG, "pause() was called but video already paused.", Log.DEBUG);
            return;
        }

        if (mState == State.STOP) {
            Dialogues.Log(TAG, "pause() was called but video already stopped.", Log.DEBUG);
            return;
        }

        if (mState == State.END) {
            Dialogues.Log(TAG, "pause() was called but video already ended.", Log.DEBUG);
            return;
        }

        mState = State.PAUSE;
        if (player.getPlayWhenReady()) {
            player.setPlayWhenReady(false);
        }
        mBufferingUpdater.stop();
        mProgressUpdater.stop();
    }

    public void stop() {
        if (mState == State.STOP) {
            Dialogues.Log(TAG, "stop() was called but video already stopped.", Log.DEBUG);
            return;
        }

        if (mState == State.END) {
            Dialogues.Log(TAG, "stop() was called but video already ended.", Log.DEBUG);
            return;
        }

        mState = State.STOP;
        if (player.getPlayWhenReady()) {
            player.setPlayWhenReady(false);
            player.seekTo(0);
        }
        mBufferingUpdater.stop();
        mProgressUpdater.stop();
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    public SimpleExoPlayer getPlayer() {
        return player;
    }

    private void initializePlayer() {
        Dialogues.Log(TAG, "/***initializePlayer", Log.ERROR);

        if (player == null) {
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);
            trackSelector.addListener(this);
            player = ExoPlayerFactory.newSimpleInstance(getContext(), trackSelector, new DefaultLoadControl());
            player.setVideoListener(this);
            player.addListener(this);
            player.setVideoTextureView(this);
            if (isTimelineStatic) {
                if (playerPosition == C.TIME_UNSET) {
                    player.seekToDefaultPosition(playerWindow);
                } else {
                    player.seekTo(playerWindow, playerPosition);
                }
            }
            player.setPlayWhenReady(shouldAutoPlay);
            playerNeedsSource = true;
        }
        if (playerNeedsSource) {
            MediaSource source = new DashMediaSource(Uri.parse(uriSample.uri), buildDataSourceFactory(false),
                    new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
            LoopingMediaSource loopingSource = null;
            if (looping) {
                if (loopCount > 0)
                    loopingSource = new LoopingMediaSource(source, loopCount);
                else
                    loopingSource = new LoopingMediaSource(source);
                player.prepare(loopingSource, !isTimelineStatic, !isTimelineStatic);
            } else {
                player.prepare(source, !isTimelineStatic, !isTimelineStatic);
            }

            playerNeedsSource = false;

            mIsVideoPrepared = true;
            setOnPrepared();

            mBufferingUpdater.start();
            mProgressUpdater.start();
        }
    }

    public void releasePlayer() {
        if (player != null) {
            shouldAutoPlay = player.getPlayWhenReady();
            playerWindow = player.getCurrentWindowIndex();
            playerPosition = C.TIME_UNSET;
            Timeline timeline = player.getCurrentTimeline();
            if (timeline != null && timeline.getWindow(playerWindow, window).isSeekable) {
                playerPosition = player.getCurrentPosition();
            }
            player.stop();
            player.release();
            player = null;
            trackSelector = null;
        }

        mBufferingUpdater.stop();
        mProgressUpdater.stop();
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void setLooping(boolean looping, int loopCount) {
        this.looping = looping;
        this.loopCount = loopCount;
    }

    public void mute(boolean mute) {
        if (player == null)
            return;

        this.mute = mute;

        player.setVolume(mute ? 0 : 1);
    }

    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return ((MyApplication) getContext().getApplicationContext())
                .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

    }

    @Override
    public void onRenderedFirstFrame() {
        setOnRenderedFirstFrame();
    }

    @Override
    public void onVideoTracksDisabled() {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        Dialogues.Log(TAG, "ExoPlayer.onLoadingChanged.isLoading: " + isLoading, Log.ERROR);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_IDLE) {
            Dialogues.Log(TAG, "ExoPlayer.STATE_IDLE", Log.ERROR);
        } else if (playbackState == ExoPlayer.STATE_READY) {
            Dialogues.Log(TAG, "ExoPlayer.STATE_READY", Log.ERROR);
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            Dialogues.Log(TAG, "ExoPlayer.STATE_BUFFERING", Log.ERROR);
        } else if (playbackState == ExoPlayer.STATE_ENDED) {
            Dialogues.Log(TAG, "ExoPlayer.STATE_ENDED", Log.ERROR);
            mState = State.END;
            mBufferingUpdater.stop();
            mProgressUpdater.stop();

            //setOnTouchListener(this);
        }
    }

    @Override
    public void onPositionDiscontinuity() {
        // Do nothing.
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        isTimelineStatic = timeline != null && timeline.getWindowCount() > 0
                && !timeline.getWindow(timeline.getWindowCount() - 1, window).isDynamic;
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        String errorString = null;
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                        (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getContext().getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getContext().getString(R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType);
                    } else {
                        errorString = getContext().getString(R.string.error_no_decoder,
                                decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getContext().getString(R.string.error_instantiating_decoder,
                            decoderInitializationException.decoderName);
                }
            }
        }
        if (errorString != null) {
            setOnError(errorString);
            Dialogues.Log(TAG, errorString, Log.ERROR);
        }
        playerNeedsSource = true;
    }

    // MappingTrackSelector.EventListener implementation

    @Override
    public void onTrackSelectionsChanged(TrackSelections<? extends MappingTrackSelector.MappedTrackInfo> trackSelections) {
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelections.info;
        if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_VIDEO)) {
            Dialogues.Log(TAG, getContext().getString(R.string.error_unsupported_video), Log.ERROR);
        }
        if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_AUDIO)) {
            Dialogues.Log(TAG, getContext().getString(R.string.error_unsupported_audio), Log.ERROR);
        }
    }

    public String getStringFromFile(File file) throws Exception {
        FileInputStream fin = new FileInputStream(file);
        String ret = getStringFromInputStream(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    public static String getStringFromInputStream(InputStream stream) throws IOException {
        int n = 0;
        char[] buffer = new char[1024 * 4];
        InputStreamReader reader = new InputStreamReader(stream, "UTF8");
        StringWriter writer = new StringWriter();
        while (-1 != (n = reader.read(buffer))) writer.write(buffer, 0, n);
        return writer.toString();
    }

    public static String findDashmpd(String url) {
        String dashUrl1 = url.substring(url.lastIndexOf("dashmpd"));
        String dashUrl2 = dashUrl1.substring(dashUrl1.lastIndexOf("dashmpd"), dashUrl1.indexOf("&"));
        String dashUrl = null;
        try {
            dashUrl = URLDecoder.decode(dashUrl2.substring(dashUrl2.indexOf("http")), "UTF-8");
            dashUrl = URLDecoder.decode(dashUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return dashUrl;
    }

    private static final class UriSimple {
        final String uri;
        final String extension;

        UriSimple(String uri, String extension) {
            this.uri = uri;
            this.extension = extension;
        }
    }

    private final class VideoBufferingUpdater extends Handler {

        void start() {
            sendEmptyMessage(0);
        }

        void stop() {
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message msg) {
            int percent = getBufferedPercentage();

            if (mIsVideoPrepared)
                setOnBufferingUpdate(percent);

            sendEmptyMessageDelayed(0, 500);
        }
    }

    private final class VideoProgressUpdater extends Handler {

        void start() {
            sendEmptyMessage(0);
        }

        void stop() {
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message msg) {
            long position = getCurrentPosition();

            if (mIsVideoPrepared)
                setOnProgressUpdate(position);

            sendEmptyMessageDelayed(0, 500);
        }
    }

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mState == State.END || mState == State.STOP)
                play();
            return true;
        }
    };

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    /**
     * OnPlayerListener
     */
    private OnYTPlayerListener onYTPlayerListener;

    public void setOnYTPlayerListener(OnYTPlayerListener onYTPlayerListener) {
        this.onYTPlayerListener = onYTPlayerListener;
    }

    private void setOnBufferingUpdate(int percent) {
        if (onYTPlayerListener == null)
            return;

        onYTPlayerListener.onBufferingUpdate(percent);
    }

    private void setOnProgressUpdate(long position) {
        if (onYTPlayerListener == null)
            return;

        onYTPlayerListener.onCurrentPosition(position);
    }

    private void setOnRenderedFirstFrame() {
        if (onYTPlayerListener == null)
            return;

        onYTPlayerListener.onRenderedFirstFrame();
    }

    /**
     * OnPreparedListener
     */
    private OnPreparedListener onPreparedListener;

    public void setOnPreparedListener(OnPreparedListener onPreparedListener) {
        this.onPreparedListener = onPreparedListener;
    }

    private void setOnPrepared() {
        if (onPreparedListener == null)
            return;

        onPreparedListener.onPrepared();
    }


    /**
     * OnErrorListener
     */
    private OnErrorListener onErrorListener;

    public void setOnErrorListener(OnErrorListener onErrorListener) {
        this.onErrorListener = onErrorListener;
    }

    private void setOnError(String error) {
        if (onErrorListener == null)
            return;

        onErrorListener.onError(error);
    }
}
