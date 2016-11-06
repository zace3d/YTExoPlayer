package xyz.edgarzavala.youtubeexoplayer.dialogues;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import xyz.edgarzavala.youtubeexoplayer.BuildConfig;

public class Dialogues {
    private static Toast toast;

    public static void Toast(Context context, String text, int duration) {
        if (toast != null)
            toast.cancel();

        toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    public static void Log(String tag, String text, int type) {
        if (BuildConfig.DEBUG) {
            if (type == Log.DEBUG) {
                Log.d(tag, text + "");
            } else if (type == Log.ERROR) {
                Log.e(tag, text + "");
            } else if (type == Log.INFO) {
                Log.i(tag, text + "");
            } else if (type == Log.VERBOSE) {
                Log.v(tag, text + "");
            } else if (type == Log.WARN) {
                Log.w(tag, text + "");
            }
        }
    }
}
