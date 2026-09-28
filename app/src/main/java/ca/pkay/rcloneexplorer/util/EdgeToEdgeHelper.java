package ca.pkay.rcloneexplorer.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 沉浸式（edge-to-edge）辅助类。
 * <p>
 * 让 Activity 内容延伸到状态栏与导航栏下方实现沉浸式显示，
 * 并按需给指定 View 加上对应的 inset padding / margin，避免内容被系统栏遮挡。
 */
public final class EdgeToEdgeHelper {

    private EdgeToEdgeHelper() {
    }

    /**
     * 对 Activity 启用 edge-to-edge：内容绘制到状态栏/导航栏下方，
     * 同时根据当前主题设置状态栏与导航栏图标的明暗。
     * 必须在 setContentView 之前调用。
     */
    public static void enable(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        boolean darkTheme = ActivityHelper.isDarkTheme(activity);
        controller.setAppearanceLightStatusBars(!darkTheme);
        controller.setAppearanceLightNavigationBars(!darkTheme);
    }

    /**
     * 给 view 顶部加上状态栏高度的 padding（保留原有 paddingTop）。
     * 适用于顶部的 Toolbar / AppBar。
     */
    public static void applyStatusBarPadding(View view) {
        if (view == null) {
            return;
        }
        final int baseTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), baseTop + statusBars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * 给 view 底部加上导航栏高度的 padding（保留原有 paddingBottom）。
     * 适用于列表等可滚动内容：内容可滑到导航栏下方，末项不会被遮挡。
     */
    public static void applyNavigationBarPadding(View view) {
        if (view == null) {
            return;
        }
        final int baseBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), baseBottom + navigationBars.bottom);
            return insets;
        });
    }

    /**
     * 给 view 底部加上导航栏高度的 margin（保留原有 bottomMargin）。
     * 适用于悬浮在底部的操作条（bottom bar）。
     */
    public static void applyNavigationBarMargin(View view) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final int baseBottomMargin = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = baseBottomMargin + navigationBars.bottom;
            v.setLayoutParams(params);
            return insets;
        });
    }
}
