package com.nbn.adfeed.ui.feed;

import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 左右滑动手势频道切换委托，从 FeedFragment 中提取。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>在 RecyclerView 上监听水平拖拽手势（竖滑不拦截）。</li>
 *   <li>手指横拖时 RecyclerView 跟随平移（setTranslationX）。</li>
 *   <li>松手时位移过半 → 切频道 + 飞出动画；未过半 → 弹回原位。</li>
 *   <li>首尾频道拖拽有 30% 阻力，防止误滑出界。</li>
 *   <li>横滑确认后锁定手势链：阻止父 SwipeRefreshLayout 和子 View 反抢。</li>
 * </ul>
 */
final class FeedSwipeChannelDelegate {

    private float swipeStartX;
    private float swipeStartY;
    private boolean swipeHorizontal;
    private boolean swipeTracking;
    private int swipeTouchSlop;
    private int screenWidth;

    private RecyclerView recyclerView;
    private FeedDataController dataController;
    private TextView[] tabButtons;

    /**
     * 绑定外部依赖。在 Fragment.wireDelegates 中调用。
     */
    void bind(RecyclerView recyclerView, FeedDataController dataController, TextView[] tabButtons) {
        this.recyclerView = recyclerView;
        this.dataController = dataController;
        this.tabButtons = tabButtons;
    }

    /**
     * 在 RecyclerView 上注册手势监听。必须在 {@link #bind} 之后、数据首屏加载前调用。
     */
    void attach() {
        swipeTouchSlop = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();
        screenWidth = recyclerView.getResources().getDisplayMetrics().widthPixels;

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                float dx = e.getRawX() - swipeStartX;
                float dy = e.getRawY() - swipeStartY;
                int action = e.getActionMasked();

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        swipeStartX = e.getRawX();
                        swipeStartY = e.getRawY();
                        swipeHorizontal = false;
                        swipeTracking = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (swipeTracking) return true;
                        if (!swipeHorizontal) {
                            float absDx = Math.abs(dx);
                            float absDy = Math.abs(dy);
                            if (absDx > swipeTouchSlop && absDx > absDy * 1.2f) {
                                swipeHorizontal = true;
                                swipeTracking = true;
                                rv.getParent().requestDisallowInterceptTouchEvent(true);
                                rv.requestDisallowInterceptTouchEvent(false);
                                return true;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!swipeTracking) swipeHorizontal = false;
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                float dx = e.getRawX() - swipeStartX;
                int action = e.getActionMasked();

                switch (action) {
                    case MotionEvent.ACTION_MOVE:
                        if (swipeTracking) {
                            int idx = dataController.getCurrentChannelIndex();
                            int count = FeedDataController.CHANNELS.size();
                            float adjustedX = dx;
                            if ((idx == 0 && dx > 0) || (idx == count - 1 && dx < 0)) {
                                adjustedX *= 0.3f;
                            }
                            recyclerView.setTranslationX(adjustedX);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (swipeTracking) {
                            finishSwipeGesture();
                            swipeTracking = false;
                            swipeHorizontal = false;
                            rv.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        break;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                if (swipeTracking && disallowIntercept) {
                    recyclerView.requestDisallowInterceptTouchEvent(false);
                }
            }
        });
    }

    // ---- 手势收尾 ----

    /** 手指抬起：过半切频道，否则弹回。 */
    private void finishSwipeGesture() {
        float currentX = recyclerView.getTranslationX();
        int idx = dataController.getCurrentChannelIndex();
        int count = FeedDataController.CHANNELS.size();
        boolean goNext = currentX < -screenWidth / 2f && idx < count - 1;
        boolean goPrev = currentX > screenWidth / 2f && idx > 0;

        if (goNext) {
            animateSwipeOut(-screenWidth);
            switchToChannel(idx + 1);
        } else if (goPrev) {
            animateSwipeOut(screenWidth);
            switchToChannel(idx - 1);
        } else {
            animateSwipeBack();
        }
    }

    private void animateSwipeBack() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(recyclerView, "translationX",
                recyclerView.getTranslationX(), 0f);
        anim.setDuration(200);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    private void animateSwipeOut(float targetX) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(recyclerView, "translationX",
                recyclerView.getTranslationX(), targetX);
        anim.setDuration(200);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                recyclerView.setTranslationX(0f);
            }
        });
        anim.start();
    }

    /** 切换到指定频道索引，同步更新胶囊按钮选中态。 */
    private void switchToChannel(int index) {
        for (int j = 0; j < tabButtons.length; j++) {
            tabButtons[j].setSelected(j == index);
        }
        dataController.selectChannel(index, null);
    }
}
