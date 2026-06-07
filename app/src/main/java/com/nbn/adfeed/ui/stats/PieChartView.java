package com.nbn.adfeed.ui.stats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class PieChartView extends View {
    private static final int DEFAULT_SIZE_DP = 116;
    private static final int EMPTY_STATE_COLOR = 0xFFE0E0E0;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pieBounds = new RectF();
    private int[] values = new int[0];
    private int[] colors = new int[0];

    public PieChartView(Context context) {
        this(context, null);
    }

    public PieChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PieChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setSlices(int[] values, int[] colors) {
        this.values = values == null ? new int[0] : values.clone();
        this.colors = colors == null ? new int[0] : colors.clone();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredSize = dpToPx(DEFAULT_SIZE_DP);
        int width = resolveSize(desiredSize, widthMeasureSpec);
        int height = resolveSize(desiredSize, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = 1f;
        pieBounds.set(inset, inset, w - inset, h - inset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int count = Math.min(values.length, colors.length);
        if (count == 0) {
            drawEmptyState(canvas);
            return;
        }

        int total = 0;
        for (int i = 0; i < count; i++) {
            if (values[i] > 0) {
                total += values[i];
            }
        }

        if (total <= 0) {
            drawEmptyState(canvas);
            return;
        }

        float startAngle = -90f;
        for (int i = 0; i < count; i++) {
            int value = values[i];
            if (value <= 0) {
                continue;
            }
            float sweepAngle = value * 360f / total;
            paint.setColor(colors[i]);
            canvas.drawArc(pieBounds, startAngle, sweepAngle, true, paint);
            startAngle += sweepAngle;
        }
    }

    private void drawEmptyState(Canvas canvas) {
        paint.setColor(EMPTY_STATE_COLOR);
        canvas.drawOval(pieBounds, paint);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
