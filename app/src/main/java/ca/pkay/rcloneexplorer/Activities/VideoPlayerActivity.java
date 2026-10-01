package ca.pkay.rcloneexplorer.Activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.util.ArrayList;

import ca.pkay.rcloneexplorer.Items.FileItem;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Services.StreamingService;
import ca.pkay.rcloneexplorer.util.FLog;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 内置视频播放器（NovaPlayer 风格）。
 *
 * 特性：
 * - 上 / 下滑动切换同一目录下的上一个 / 下一个视频（刷视频）
 * - 单击切换控制栏显示 / 隐藏
 * - 左右滑动快进 / 快退
 * - 通过 StreamingService（rclone serve http）串流播放网盘视频
 */
public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";

    public static final String EXTRA_REMOTE = "extra_remote";
    public static final String EXTRA_PLAYLIST = "extra_playlist";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    private PlayerView playerView;
    private ProgressBar loadingSpinner;
    private TextView titleView;
    private TextView positionView;
    private ExoPlayer player;

    private RemoteItem remote;
    private ArrayList<FileItem> playlist;
    private int currentIndex;

    private Intent serveIntent;
    private int servePort;
    private StreamTask streamTask;
    private GestureDetector gestureDetector;

    /** 滑动判定阈值 */
    private static final int SWIPE_THRESHOLD_DP = 80;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        remote = getIntent().getParcelableExtra(EXTRA_REMOTE);
        playlist = getIntent().getParcelableArrayListExtra(EXTRA_PLAYLIST);
        currentIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

        if (remote == null || playlist == null || playlist.isEmpty()) {
            Toast.makeText(this, R.string.streaming_task_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (currentIndex < 0 || currentIndex >= playlist.size()) {
            currentIndex = 0;
        }

        playerView = findViewById(R.id.player_view);
        loadingSpinner = findViewById(R.id.loading_spinner);
        titleView = findViewById(R.id.video_title);
        positionView = findViewById(R.id.video_position);

        // 沉浸式全屏
        enterImmersive();

        // ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                loadingSpinner.setVisibility(
                        state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
            }
        });

        // 手势：单击切换控制栏，上下滑动切集，左右滑动快进退
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (playerView.isControllerFullyVisible()) {
                    playerView.hideController();
                } else {
                    playerView.showController();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float threshold = SWIPE_THRESHOLD_DP * getResources().getDisplayMetrics().density;
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > threshold) {
                    // 纵滑：NovaPlayer 式切集
                    if (dy < 0) {
                        playNext();
                    } else {
                        playPrevious();
                    }
                    return true;
                } else if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                    // 横滑：快进 / 快退 10 秒
                    long pos = player.getCurrentPosition();
                    if (dx > 0) {
                        player.seekTo(Math.min(pos + 10_000, player.getDuration()));
                    } else {
                        player.seekTo(Math.max(pos - 10_000, 0));
                    }
                    return true;
                }
                return false;
            }
        });
        playerView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        updatePositionLabel();
        playAt(currentIndex);
    }

    private void enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersive();
        }
    }

    private void updatePositionLabel() {
        if (playlist.size() > 1) {
            positionView.setVisibility(View.VISIBLE);
            positionView.setText((currentIndex + 1) + " / " + playlist.size());
        } else {
            positionView.setVisibility(View.GONE);
        }
    }

    private void playNext() {
        if (currentIndex + 1 < playlist.size()) {
            playAt(currentIndex + 1);
        }
    }

    private void playPrevious() {
        if (currentIndex - 1 >= 0) {
            playAt(currentIndex - 1);
        }
    }

    private void playAt(int index) {
        currentIndex = index;
        FileItem item = playlist.get(index);
        titleView.setText(item.getName());
        updatePositionLabel();

        // 停掉旧流
        stopStream();
        player.stop();
        player.clearMediaItems();
        loadingSpinner.setVisibility(View.VISIBLE);

        // 起新流
        streamTask = new StreamTask();
        streamTask.execute(item);
    }

    private void stopStream() {
        if (streamTask != null) {
            streamTask.cancel(true);
            streamTask = null;
        }
        if (serveIntent != null) {
            stopService(serveIntent);
            serveIntent = null;
        }
    }

    /** 与 FileExplorerFragment.StreamTask 同逻辑：起 rclone serve，轮询到可用后返回 URL */
    @SuppressLint("StaticFieldLeak")
    private class StreamTask extends AsyncTask<FileItem, Void, Uri> {

        @Override
        protected Uri doInBackground(FileItem... fileItems) {
            Context context = VideoPlayerActivity.this;
            FileItem fileItem = fileItems[0];
            int port = allocatePort(8080, true);
            servePort = port;
            serveIntent = new Intent(context, StreamingService.class);
            serveIntent.putExtra(StreamingService.SERVE_PATH_ARG, fileItem.getPath());
            serveIntent.putExtra(StreamingService.REMOTE_ARG, remote);
            serveIntent.putExtra(StreamingService.SHOW_NOTIFICATION_TEXT, false);
            serveIntent.putExtra(StreamingService.SERVE_PORT, port);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serveIntent);
            } else {
                context.startService(serveIntent);
            }

            Uri uri = Uri.parse("http://127.0.0.1:" + port)
                    .buildUpon()
                    .appendPath(fileItem.getName())
                    .build();

            OkHttpClient client = new OkHttpClient.Builder().build();
            Request request = new Request.Builder().url(uri.toString()).head().build();

            long waitTime = 30 * 1000;
            while (waitTime > 0 && !isCancelled()) {
                long waitStart = System.nanoTime();
                try {
                    Response response = client.newCall(request).execute();
                    if (response.code() == 200) {
                        return uri;
                    }
                } catch (IOException e) {
                    FLog.v(TAG, "StreamTask: server not (yet) online");
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    return null;
                }
                waitTime -= (System.nanoTime() - waitStart) / 1_000_000;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (isCancelled() || isFinishing()) {
                return;
            }
            if (uri != null) {
                player.setMediaItem(MediaItem.fromUri(uri));
                player.prepare();
                player.play();
            } else {
                loadingSpinner.setVisibility(View.GONE);
                Toast.makeText(VideoPlayerActivity.this,
                        R.string.streaming_task_failed, Toast.LENGTH_LONG).show();
                stopService(serveIntent);
                serveIntent = null;
            }
        }
    }

    /** 取自 FileExplorerFragment 的端口分配逻辑 */
    private static int allocatePort(int port, boolean allocateFallback) {
        try {
            java.net.ServerSocket socket = new java.net.ServerSocket(port);
            int allocated = socket.getLocalPort();
            socket.close();
            return allocated;
        } catch (IOException e) {
            if (allocateFallback) {
                return allocatePort(0, false);
            }
            return port;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStream();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    /** 对外启动入口 */
    public static void start(Context context, RemoteItem remote,
                             ArrayList<FileItem> playlist, int startIndex) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(EXTRA_REMOTE, remote);
        intent.putParcelableArrayListExtra(EXTRA_PLAYLIST, playlist);
        intent.putExtra(EXTRA_START_INDEX, startIndex);
        context.startActivity(intent);
    }
}
