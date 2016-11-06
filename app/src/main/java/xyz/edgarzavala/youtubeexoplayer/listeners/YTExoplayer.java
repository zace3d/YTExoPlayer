package xyz.edgarzavala.youtubeexoplayer.listeners;

/**
 * Created by Edgar Z. on 11/6/16.
 */

public interface YTExoplayer {

    /**
     * OnPreparedListener
     */
    interface OnPreparedListener {
        void onPrepared();
    }

    /**
     * OnPlayerListener
     */
    interface OnYTPlayerListener {
        void onBufferingUpdate(int percent);

        void onCurrentPosition(long position);

        void onRenderedFirstFrame();
    }

    /**
     * OnErrorListener
     */
    interface OnErrorListener {
        void onError(String error);
    }
}
