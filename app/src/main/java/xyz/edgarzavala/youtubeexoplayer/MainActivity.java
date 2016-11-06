package xyz.edgarzavala.youtubeexoplayer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.exoplayer2.util.Util;

import xyz.edgarzavala.youtubeexoplayer.dialogues.Dialogues;

public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener, View.OnClickListener,
        YTPlayerView.OnPreparedListener, YTPlayerView.OnYTPlayerListener {

    private static final String TAG = MainActivity.class.getName();

    private YTPlayerView mYTPlayer;
    private ToggleButton mMuteButton;
    private Button mReplayButton;

    private boolean mute = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mYTPlayer = (YTPlayerView) findViewById(R.id.main_yt_player);

        mMuteButton = (ToggleButton) findViewById(R.id.main_mute_button);
        mMuteButton.setOnCheckedChangeListener(this);
        mMuteButton.setChecked(mute);

        mReplayButton = (Button) findViewById(R.id.main_replay_button);
        mReplayButton.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23) {
            mYTPlayer.play();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            mYTPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mYTPlayer.releasePlayer();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.main_mute_button:
                if (isChecked)
                    mYTPlayer.pause();
                else
                    mYTPlayer.play();
                //mYTPlayer.mute(isChecked);
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.main_button:
                mYTPlayer.setYTVideoId("9xT4tdKwqUc");
                //mYTPlayer.setLooping(true, 2);
                mYTPlayer.setOnPreparedListener(this);
                mYTPlayer.setOnYTPlayerListener(this);
                mYTPlayer.prepare();
                break;
            case R.id.main_replay_button:
                mYTPlayer.seekTo(0);
                break;
        }
    }

    @Override
    public void onPrepared() {
        Toast.makeText(this, "onPrepared!!!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBufferingUpdate(int percent) {
        Dialogues.Log(TAG, "ExoPlayer.onBufferingUpdate: " + percent, Log.ERROR);
    }

    @Override
    public void onCurrentPosition(long position) {
        Dialogues.Log(TAG, "ExoPlayer.onCurrentPosition: " + position, Log.ERROR);
    }

    @Override
    public void onRenderedFirstFrame() {
        Dialogues.Log(TAG, "ExoPlayer.onRenderedFirstFrame", Log.ERROR);
    }
}
