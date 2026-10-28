package com.sshovpn.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Grafik garis sederhana untuk kecepatan unduh (hijau) dan unggah (biru), 60 sampel terakhir. */
public class BandwidthView extends View {
    private static final int N = 60;
    private static final float MIN_SCALE = 10 * 1024f; // minimal skala 10 KB/s agar grafik tidak "loncat"

    private final float[] down = new float[N];
    private final float[] up = new float[N];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Path path = new Path();

    public BandwidthView(Context c) { super(c); init(); }
    public BandwidthView(Context c, AttributeSet a) { super(c, a); init(); }
    public BandwidthView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        float dp = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2f * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        grid.setColor(0xFF2B313C);
        grid.setStrokeWidth(1f * dp);
    }

    public void push(float downBps, float upBps) {
        System.arraycopy(down, 1, down, 0, N - 1);
        System.arraycopy(up, 1, up, 0, N - 1);
        down[N - 1] = downBps;
        up[N - 1] = upBps;
        invalidate();
    }

    public void clear() {
        java.util.Arrays.fill(down, 0f);
        java.util.Arrays.fill(up, 0f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            c.drawLine(0, y, w, y, grid);
        }

        float max = MIN_SCALE;
        for (int i = 0; i < N; i++) max = Math.max(max, Math.max(down[i], up[i]));
        max *= 1.15f;

        drawSeries(c, up, max, w, h, 0xFF3B82F6);
        drawSeries(c, down, max, w, h, 0xFF22C55E);
    }

    private void drawSeries(Canvas c, float[] data, float max, float w, float h, int color) {
        float step = w / (N - 1);
        path.reset();
        for (int i = 0; i < N; i++) {
            float x = i * step;
            float y = h - (data[i] / max) * (h - 2f) - 1f;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        line.setColor(color);
        c.drawPath(path, line);

        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        fill.setColor((color & 0x00FFFFFF) | 0x26000000);
        c.drawPath(path, fill);
    }
}
