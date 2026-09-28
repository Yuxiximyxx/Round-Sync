package ca.pkay.rcloneexplorer.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import ca.pkay.rcloneexplorer.R;

/**
 * 文件列表右侧的可拖动快速滚动条。
 * <p>
 * 跟随 RecyclerView 的滚动位置显示，手指按住滑块拖动即可快速滚动列表，
 * 停止滚动一段时间后自动淡出。
 */
public class DragScrollBarView extends View {

    private static final long AUTO_HIDE_DELAY_MS = 1500;
    private static final float THUMB_WIDTH_DP = 5f;
    private static final float TOUCH_WIDTH_DP = 24f;
    private static final float MIN_THUMB_HEIGHT_DP = 48f;

    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbRect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RecyclerView recyclerView;
    private boolean dragging;
    private float lastTouchY;
    private float thumbTop;
    private float thumbHeight;
    private boolean scrollable;

    private final float thumbWidthPx;
    private final float touchWidthPx;
    private final float minThumbHeightPx;

    private final Runnable hideRunnable = this::hide;

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            updateThumb();
            show();
            scheduleHide();
        }
    };

    public DragScrollBarView(Context context) {
        this(context, null);
    }

    public DragScrollBarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragScrollBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        thumbWidthPx = THUMB_WIDTH_DP * density;
        touchWidthPx = TOUCH_WIDTH_DP * density;
        minThumbHeightPx = MIN_THUMB_HEIGHT_DP * density;
        thumbPaint.setColor(resolveThumbColor(context));
        setAlpha(0f);
    }

    private int resolveThumbColor(Context context) {
        TypedValue typedValue = new TypedValue();
        int color = 0x88888888;
        if (context.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true)) {
            color = typedValue.data;
        }
        // 半透明，避免遮挡列表内容
        return (color & 0x00FFFFFF) | 0x99000000;
    }

    /** 绑定到 RecyclerView，开始跟随其滚动状态 */
    public void attachTo(@NonNull RecyclerView recyclerView) {
        if (this.recyclerView != null) {
            this.recyclerView.removeOnScrollListener(scrollListener);
        }
        this.recyclerView = recyclerView;
        recyclerView.addOnScrollListener(scrollListener);
        // 初次布局后计算一次滑块位置
        post(this::updateThumb);
    }

    public void detach() {
        handler.removeCallbacks(hideRunnable);
        if (recyclerView != null) {
            recyclerView.removeOnScrollListener(scrollListener);
            recyclerView = null;
        }
    }

    private void updateThumb() {
        if (recyclerView == null) {
            scrollable = false;
            invalidate();
            return;
        }
        int range = recyclerView.computeVerticalScrollRange();
        int extent = recyclerView.computeVerticalScrollExtent();
        int offset = recyclerView.computeVerticalScrollOffset();
        float trackHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        scrollable = range > extent && trackHeight > 0;
        if (scrollable) {
            thumbHeight = Math.max(minThumbHeightPx, trackHeight * extent / (float) range);
            float scrollableTrack = trackHeight - thumbHeight;
            float scrollableRange = range - extent;
            thumbTop = getPaddingTop() + (scrollableRange <= 0 ? 0
                    : scrollableTrack * offset / scrollableRange);
        }
        invalidate();
    }

    private void show() {
        if (!scrollable) {
            return;
        }
        handler.removeCallbacks(hideRunnable);
        if (getAlpha() < 1f) {
            animate().alpha(1f).setDuration(150).setListener(null).start();
        }
    }

    private void scheduleHide() {
        if (dragging) {
            return;
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void hide() {
        if (dragging) {
            return;
        }
        animate().alpha(0f).setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!dragging) {
                            setAlpha(0f);
                        }
                    }
                }).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!scrollable || recyclerView == null) {
            return;
        }
        float right = getWidth() - (touchWidthPx - thumbWidthPx) / 2f;
        thumbRect.set(right - thumbWidthPx, thumbTop, right, thumbTop + thumbHeight);
        canvas.drawRoundRect(thumbRect, thumbWidthPx / 2f, thumbWidthPx / 2f, thumbPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumb();
    }

    private boolean isOnThumb(float x, float y) {
        return x >= getWidth() - touchWidthPx
                && y >= thumbTop - touchWidthPx / 2f
                && y <= thumbTop + thumbHeight + touchWidthPx / 2f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (recyclerView == null || !scrollable) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isOnThumb(event.getX(), event.getY())) {
                    dragging = true;
                    lastTouchY = event.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    handler.removeCallbacks(hideRunnable);
                    show();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dy = event.getY() - lastTouchY;
                    lastTouchY = event.getY();
                    dragBy(dy);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    scheduleHide();
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void dragBy(float dy) {
        float trackHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float scrollableTrack = trackHeight - thumbHeight;
        if (scrollableTrack <= 0) {
            return;
        }
        int range = recyclerView.computeVerticalScrollRange();
        int extent = recyclerView.computeVerticalScrollExtent();
        int scrollableRange = range - extent;
        if (scrollableRange <= 0) {
            return;
        }
        float newThumbTop = thumbTop + dy;
        float minTop = getPaddingTop();
        float maxTop = getPaddingTop() + scrollableTrack;
        newThumbTop = Math.max(minTop, Math.min(maxTop, newThumbTop));
        int newOffset = Math.round((newThumbTop - minTop) / scrollableTrack * scrollableRange);
        int currentOffset = recyclerView.computeVerticalScrollOffset();
        recyclerView.scrollBy(0, newOffset - currentOffset);
        // scrollBy 会触发 onScrolled 更新 thumbTop，这里先同步避免跳动
        thumbTop = newThumbTop;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }
}
