package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver, in the same style as the home screen:
 *  - five bundled Skyline backgrounds, selected by local sunrise/sunset and weather, with a gentle zoom/pan
 *    and a crossfade when the selected sky changes; no movie recommendations or network image downloads;
 *  - centred date, large Liquid Glass clock (the photo shows through the digits; Shaders.GLASS_TEXT),
 *    and current weather icon/temperature. No prices or movie captions are displayed.
 * Dark scrims only where there is text, stronger where the photo is bright. Any key exits (as on Apple TV).
 * While loading a landscape or if an asset cannot be decoded: the centred clock over the night aurora.
 */
final class DreamView extends View implements Choreographer.FrameCallback, HomeData.Listener {
    private static final long FADE_MS = 3_000, FIRST_MS = 1_600;
    private static final float ZOOM = 0.1f;
    /** Photos 10% larger than the screen (the zoom goes up to 1.1 without upscaling). */
    private static final int PW = 2112, PH = 1188;
    private static final float CLOCK_SIZE = 280;
    private static final double DRIFT_PERIOD_S = 600;
    /** One landscape phase on screen: its photo, clock texture and gentle motion. */
    private static final class Slide {
        final String phase;
        final Bitmap image, frost;
        final BitmapShader shader;    // the frosted version, for the clock's glass
        final boolean zoomIn;
        final float panX, panY;
        long startedAt;

        Slide(String phase, Bitmap image, Bitmap frost, int n) {
            this.phase = phase;
            this.image = image;
            this.frost = frost;
            shader = new BitmapShader(frost, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Frost.filter(shader, BitmapShader.FILTER_MODE_LINEAR);
            zoomIn = n % 2 == 0;
            // Toward one of the corners, alternating, without repeating the previous one.
            panX = (n / 2) % 2 == 0 ? 0.8f : -0.8f;
            panY = n % 3 == 0 ? -0.6f : 0.6f;
        }
    }

    /** The glass clock's digits, rendered once a minute in the background. */
    private static final class Glass {
        final String text;
        final Bitmap slope, cover, shadow;
        final int x, y;
        final float inkTop, inkHeight;

        Glass(String text, Bitmap slope, Bitmap cover, Bitmap shadow, int x, int y, float inkTop, float inkHeight) {
            this.text = text;
            this.slope = slope;
            this.cover = cover;
            this.shadow = shadow;
            this.x = x;
            this.y = y;
            this.inkTop = inkTop;
            this.inkHeight = inkHeight;
        }
    }

    private final Scene scene = new Scene(false);
    private final HomeData data;
    private final HomeClimate homeClimate=HomeClimate.get();
    private final IndoorClimateRow indoorClimateRow=new IndoorClimateRow();
    private final HomeCalendars homeCalendars=HomeCalendars.get();
    private final CalendarVisual calendarVisual=new CalendarVisual();
    private final Runnable calendarListener=()->{
        calendarVisual.update(homeCalendars.agenda); dirty=chromeDirty=true; invalidate();
    };
    private final Runnable climateListener=()->{ dirty=true; chromeDirty=true; invalidate(); };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Recording content = Recording.create("clock");     // without photos: the clock over the aurora
    private final Recording chrome = Recording.create("landscape-overlay"); // centred date, weather and clock shadow
    private final float timeOffset = (float) (Math.random() * 300);
    private final Matrix matrix = new Matrix();
    private final float[] placeA = new float[3], placeB = new float[3];
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint black = new Paint();
    private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glassPaint = new Paint();
    private final Set<String> failed = new HashSet<>();     // Retry unavailable bundled phases once per minute.
    private RuntimeShader glassShader;
    private boolean running, dirty = true, chromeDirty = true;
    private boolean fallback;       // the aurora with the clock (no photos)
    private boolean decoding;
    private int generation, slides;
    private Slide current, incoming, prepared;
    private Glass glass;
    private String glassFor = "";
    private long startedAt, firstAt, lastFrame, lastSky;
    private int w, h;
    private float k = 1;
    private long minute = -1;
    private String clock = "", date = "";
    private Paint datePaint, clockPaint, weatherPaint;
    private Paint cornerDate, cornerClock;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            data.refreshIfStale();
            dirty = true;
            chromeDirty = true;
            failed.clear();
            prepareNext();
            handler.postDelayed(this, 60_000);
        }
    };

    DreamView(Context c) {
        super(c);
        data = HomeData.get(c);
        Shaders.dream(scene.palette);
        scene.setMotion(0.4f, 0.32f);
        scene.applyPalette();
        content.setUseCompositingLayer(true, null);
        try {
            glassShader = new RuntimeShader(Shaders.GLASS_TEXT);
            glassPaint.setShader(glassShader);
        } catch (RuntimeException | LinkageError e) {   // Android 8 to 12: no AGSL
            AppLog.w("glass clock unavailable: " + e.getMessage());
            glassShader = null;
        }
        black.setColor(0xFF000000);
        shadowPaint.setColor(0xFF000000);
    }

    void start() {
        if (running) return;
        running = true;
        if (worker.isShutdown()) worker = Executors.newSingleThreadExecutor();
        startedAt = clock();
        lastFrame = 0;
        failed.clear();
        fallback = true;
        data.addListener(this);
        homeClimate.addListener(climateListener);
        homeCalendars.addListener(calendarListener);
        data.refreshIfStale();
        prepareNext();
        handler.postDelayed(tick, 60_000);
        Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    void stop() {
        if (!running) return;
        running = false;
        generation++;
        decoding = false;
        Choreographer.getInstance().removeFrameCallback(this);
        handler.removeCallbacks(tick);
        data.removeListener(this);
        homeClimate.removeListener(climateListener);
        homeCalendars.removeListener(calendarListener);
        // The photos stay in GPU memory until the garbage collector runs: release them now (after the last frame).
        final java.util.Set<Bitmap> gone = new java.util.HashSet<>();
        for (Slide s : new Slide[] {current, incoming, prepared}) {
            if (s == null) continue;
            gone.add(s.image);
            gone.add(s.frost);
        }
        if (!gone.isEmpty()) handler.postDelayed(() -> { for (Bitmap b : gone) b.recycle(); }, 1000);
        current = incoming = prepared = null;
        glass = null;
        slides = 0;
        glassFor = "";
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        worker.shutdownNow();
    }

    @Override public void onData() {
        dirty = true;
        chromeDirty = true;
        if (running) prepareNext();
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        calendarVisual.size(k,w-160*k);
        indoorClimateRow.size(k);
        scene.setSize(w, h);
        content.setPosition(0, 0, w, h);
        chrome.setPosition(0, 0, w, h);
        // Without photos (the clock over the aurora).
        datePaint = Ui.text(Ui.semibold, 36 * k, 0xD9FFFFFF);
        datePaint.setTextAlign(Paint.Align.CENTER);
        clockPaint = Ui.numbers(Ui.thin, 280 * k, 0xF2FFFFFF);
        clockPaint.setTextAlign(Paint.Align.CENTER);
        clockPaint.setLetterSpacing(-0.01f);
        weatherPaint = Ui.numbers(Ui.medium, 30 * k, 0xC7FFFFFF);
        weatherPaint.setShadowLayer(18*k,0,2*k,0x66000000);
        // Date and glass clock over the landscape.
        cornerDate = Ui.numbers(Ui.semibold, 30 * k, 0xEBFFFFFF);
        cornerClock = Ui.numbers(Ui.displaySemibold, CLOCK_SIZE * k, 0xF2FFFFFF);
        for (Paint paint : new Paint[]{cornerDate,cornerClock}) paint.setShadowLayer(18*k,0,2*k,0x66000000);
        glassFor = "";
        dirty = true;
        chromeDirty = true;
    }

    // MARK: - Photos

    private String selectedPhase() {
        return SkylineLandscape.phase(System.currentTimeMillis(),data.weather.sunrise,data.weather.sunset,data.weather.code,java.time.ZoneId.systemDefault());
    }

    /** Load only the current sky, from bundled artwork, on the existing background worker. */
    private void prepareNext() {
        if(!running || decoding) return;
        String phase=selectedPhase();
        Slide last=incoming!=null?incoming:current;
        if(prepared!=null) {
            if(prepared.phase.equals(phase)) return;
            prepared.image.recycle(); prepared.frost.recycle(); prepared=null;
        }
        if(last!=null && last.phase.equals(phase) || failed.contains(phase)) return;
        decoding=true; final int gen=generation;
        worker.execute(()->{
            Bitmap[] images=null;
            try {
                File file=SkylineLandscape.image(getContext(),phase);
                images=Backdrop.photo(file,PW,PH,null);
                images[0]=Backdrop.gpu(withScrims(images[0]));
            } catch(Exception | OutOfMemoryError failure) {
                if(images!=null) for(Bitmap bitmap:images) if(bitmap!=null) bitmap.recycle();
                images=null; AppLog.w("screensaver: Skyline background unavailable ("+phase+")");
            }
            final Bitmap[] ready=images;
            handler.post(()->{
                if(gen!=generation) { if(ready!=null) for(Bitmap bitmap:ready) bitmap.recycle(); return; }
                decoding=false;
                if(ready==null) { failed.add(phase); return; }
                if(!phase.equals(selectedPhase())) {
                    for(Bitmap bitmap:ready) bitmap.recycle(); prepareNext(); return;
                }
                prepared=new Slide(phase,ready[0],ready[1],slides++); invalidate();
            });
        });
    }

    /** Change photographs only when sunlight or weather selects a different sky. */
    private void advance(long up) {
        if (current == null) {
            if (prepared == null) return;
            current = prepared;
            prepared = null;
            current.startedAt = up;
            firstAt = up;
            bindPhotos();
            prepareNext();
            return;
        }
        if (incoming == null && prepared != null) {
            incoming = prepared;
            prepared = null;
            incoming.startedAt = up;
            bindPhotos();
            return;
        }
        if (incoming != null && up >= incoming.startedAt + FADE_MS) {
            Slide old = current;
            current = incoming;
            incoming = null;
            bindPhotos();
            if (old.image != current.image) {
                // Once the last frame that uses them has been drawn.
                Bitmap gone = old.image, goneFrost = old.frost;
                handler.postDelayed(() -> {
                    gone.recycle();
                    goneFrost.recycle();
                }, 1000);
            }
            prepareNext();
        }
    }

    /** The photos the clock's glass shows (the current one and, during a crossfade, the incoming one). */
    private void bindPhotos() {
        if (glassShader == null || current == null) return;
        glassShader.setInputShader("imageA", current.shader);
        glassShader.setInputShader("imageB", incoming != null ? incoming.shader : current.shader);
    }

    /** Photo position and scale now: zoom from 1 to 1.1 (or back) and a pan toward a corner, never showing an edge. */
    private void place(Slide s, long up, float[] out) {
        float u=0.5f-0.5f*(float)Math.cos(2*Math.PI*(up-s.startedAt)/180_000d);
        float f = s.zoomIn ? u : 1 - u;
        float z = 1 + ZOOM * f;
        float rx = w * (z - 1) / 2, ry = h * (z - 1) / 2;
        out[0] = rx * (s.panX * f - 1);
        out[1] = ry * (s.panY * f - 1);
        out[2] = (float) w / s.image.getWidth() * z;
    }

    private void drawPhoto(Canvas c, Slide s, float[] place, float alpha) {
        if (alpha <= 0.004f) return;
        matrix.setScale(place[2], place[2]);
        matrix.postTranslate(place[0], place[1]);
        photoPaint.setAlpha(Math.round(255 * Math.min(1, alpha)));
        c.drawBitmap(s.image, matrix, photoPaint);
    }

    // MARK: - Frames: 20 per second, locked to every 3rd display frame (60 Hz)
    // Motion is slow (under half a pixel per frame). Measured on the TV: ~4 ms of GPU per frame of ours, but drawing
    // every frame, it only reaches ~50 fps (the system also composites the UI on the GPU); at 20 there's room to spare.

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (frameTimeNanos - lastFrame >= 45_000_000L) {
            lastFrame = frameTimeNanos;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** The clock that drives the animations. */
    private long clock() {
        long up = SystemClock.uptimeMillis();
        return up;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (w == 0) return;
        long up = clock();
        long now = System.currentTimeMillis();
        if (now / 60_000 != minute) {
            minute = now / 60_000;
            clock = Ui.clock(now);
            date = Ui.date(now);
            dirty = true;
            chromeDirty = true;
        }
        if (running) advance(up);
        if (!glassFor.equals(clock)) buildGlass(clock);

        float first = current == null ? 0 : fadeIn(up - firstAt, FIRST_MS);
        if (current == null || first < 1) {
            if (fallback) drawFallback(canvas, up, current == null ? 1 : 1 - first);
            else canvas.drawRect(0, 0, w, h, black);
        }
        if (current == null) return;
        if (fallback && first >= 1) fallback = false;

        place(current, up, placeA);
        float e = incoming == null ? 0 : fadeIn(up - incoming.startedAt, FADE_MS);
        drawPhoto(canvas, current, placeA, first);
        if (incoming != null) {
            place(incoming, up, placeB);
            drawPhoto(canvas, incoming, placeB, e);
        }

        if (chromeDirty) recordChrome();
        chrome.setAlpha(first);
        chrome.draw(canvas);
        drawGlass(canvas, first, e);
        // Landscape screensaver overlays contain only date, time and current weather.
    }

    private static float fadeIn(long elapsed, long duration) {
        float t = Ui.clamp(elapsed / (float) duration);
        return t * t * (3 - 2 * t);
    }

    /**
     * Black scrims where there is text, stronger where the photo is bright, painted once onto the photo itself (in the
     * background): each frame draws only the photo (the TV's GPU feels every full-screen layer). With the slow zoom,
     * they move a little along with the photo, which makes no difference to the text.
     */
    private static Bitmap withScrims(Bitmap photo) {
        int w=photo.getWidth(),h=photo.getHeight();
        float luminance=0;
        for(int y=0;y<6;y++) for(int x=0;x<10;x++) {
            int pixel=photo.getPixel(w*(30+x*4)/100,h*(32+y*7)/100);
            luminance+=(0.2126f*((pixel>>16)&255)+0.7152f*((pixel>>8)&255)+0.0722f*(pixel&255))/255;
        }
        float strength=scrimStrength(luminance/60,0.24f);
        Paint paint=new Paint(Paint.DITHER_FLAG);
        paint.setShader(new RadialGradient(w/2f,h/2f,w*0.42f,
                new int[]{black(strength),black(strength*0.55f),0},new float[]{0,0.5f,1},Shader.TileMode.CLAMP));
        new Canvas(photo).drawRect(0,0,w,h,paint);
        return photo;
    }

    private static int black(float a) {
        return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24;
    }

    private static float scrimStrength(float light, float base) {
        return Math.max(base - 0.1f, Math.min(0.78f, base + (light - 0.3f) * 0.9f));
    }

    // MARK: - Centred date, glass clock and weather

    private float centeredClockTop() { return h*0.5f-Ui.cap(cornerClock)/2-10*k; }

    private void recordChrome() {
        Canvas c=chrome.beginRecording(w,h);
        try {
            float cx=w/2f,top=centeredClockTop();
            drawDateWeather(c,cx,top-34*k,cornerDate);
            Glass g=glass;
            if(g!=null && glassShader!=null) {
                shadowPaint.setAlpha(140); c.drawBitmap(g.shadow,g.x,g.y+6*k,shadowPaint);
            } else {
                Rect ink=new Rect(); cornerClock.getTextBounds(clock,0,clock.length(),ink);
                c.drawText(clock,cx-ink.exactCenterX(),top-ink.top,cornerClock);
            }
            if(HomeClimate.enabled()) indoorClimateRow.centered(c,cx,top+Ui.cap(cornerClock)+76*k,homeClimate);
            calendarVisual.summary(c,cx,h-78*k,true);
        } finally { chrome.endRecording(); }
        chromeDirty=false;
    }

    /** Centre the whole date/weather row, keeping weather directly to the right of the date. */
    private void drawDateWeather(Canvas c,float cx,float base,Paint dateStyle) {
        HomeData.Weather wx=data.weather;
        dateStyle.setTextAlign(Paint.Align.CENTER);
        if(Double.isNaN(wx.temp) || !Prefs.weather()) {
            c.drawText(date,cx,base,dateStyle);
            return;
        }
        String symbol=Symbols.weather(wx.code,wx.isDay),temperature=HomeData.degrees(wx.temp);
        float sw=Symbols.width(getContext(),symbol,32*k);
        float dateWidth=dateStyle.measureText(date),weatherWidth=sw+12*k+weatherPaint.measureText(temperature);
        float left=cx-(dateWidth+28*k+weatherWidth)/2;
        c.drawText(date,left+dateWidth/2,base,dateStyle);
        float x=left+dateWidth+28*k,centerY=base-Ui.halfCap(dateStyle);
        Symbols.draw(getContext(),c,symbol,x+sw/2,centerY,32*k,0xC7FFFFFF);
        c.drawText(temperature,x+sw+12*k,centerY+Ui.halfCap(weatherPaint),weatherPaint);
    }

    private void drawGlass(Canvas c, float alpha, float e) {
        Glass g = glass;
        if (g == null || glassShader == null || alpha <= 0.004f) return;
        // The glass samples the frosted version (1/6 of the photo): same position, with the scale 6 times larger.
        float f = Backdrop.PHOTO_FROST;
        glassShader.setFloatUniform("placeA", placeA[0], placeA[1], placeA[2] * f);
        if (incoming != null) glassShader.setFloatUniform("placeB", placeB[0], placeB[1], placeB[2] * f);
        else glassShader.setFloatUniform("placeB", placeA[0], placeA[1], placeA[2] * f);
        glassShader.setFloatUniform("mixB", incoming == null ? 0 : e);
        glassShader.setFloatUniform("origin", g.x, g.y);
        glassShader.setFloatUniform("ink", g.inkTop, g.inkHeight);
        glassShader.setFloatUniform("refraction", 14 * k);
        glassShader.setFloatUniform("specular", 1);
        glassShader.setFloatUniform("alpha", alpha);
        c.drawRect(g.x, g.y, g.x + g.cover.getWidth(), g.y + g.cover.getHeight(), glassPaint);
    }

    /** Builds the glass clock's digits in the background (once a minute) and swaps them in when they're ready. */
    private void buildGlass(String text) {
        glassFor = text;
        if (glassShader == null || text.isEmpty()) {
            chromeDirty = true;
            return;
        }
        final Paint paint = new Paint(cornerClock);
        paint.clearShadowLayer();
        paint.setColor(0xFFFFFFFF);
        Rect clockInk=new Rect(); paint.getTextBounds(text,0,text.length(),clockInk);
        final float right=w/2f+clockInk.width()/2f,top=centeredClockTop(),scale=k;
        final int gen = generation;
        worker.execute(() -> {
            Glass g = makeGlass(text, paint, right, top, scale);
            handler.post(() -> {
                if (gen != generation || !text.equals(glassFor)) return;
                glass = g;
                glassShader.setInputShader("slope", nearest(g.slope));
                glassShader.setInputShader("cover", nearest(g.cover));
                chromeDirty = true;
                invalidate();
            });
        });
    }

    private static BitmapShader nearest(Bitmap b) {
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Frost.filter(s, BitmapShader.FILTER_MODE_NEAREST);
        return s;
    }

    /**
     * The sharp rendering of the digits, the surface height (the blurred rendering: 0.5 at the edge, 1 in the middle of
     * the strokes), its slope (for the lens and the highlight) and the shadow.
     */
    private static Glass makeGlass(String text, Paint paint, float right, float top, float k) {
        Rect ink = new Rect();
        paint.getTextBounds(text, 0, text.length(), ink);
        int pad = Math.round(40 * k);
        int w = ink.width() + 2 * pad, h = ink.height() + 2 * pad;
        Bitmap cover = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(cover).drawText(text, pad - ink.left, pad - ink.top, paint);
        int[] px = new int[w * h];
        cover.getPixels(px, 0, w, 0, 0, w, h);
        int[] alpha = new int[px.length];
        for (int i = 0; i < px.length; i++) alpha[i] = (px[i] >>> 24) * 16;
        int[] height = blur(alpha, w, h, Math.max(2, Math.round(6 * k)), 3);
        int[] soft = blur(alpha, w, h, Math.max(3, Math.round(12 * k)), 3);
        float max = 1;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float gx = height[i + 1] - height[i - 1], gy = height[i + w] - height[i - w];
                max = Math.max(max, (float) Math.sqrt(gx * gx + gy * gy));
            }
        }
        int[] slope = new int[px.length], shadow = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float gx = x > 0 && x < w - 1 ? height[i + 1] - height[i - 1] : 0;
                float gy = y > 0 && y < h - 1 ? height[i + w] - height[i - w] : 0;
                int r = 128 + Math.round(127 * gx / max), g = 128 + Math.round(127 * gy / max);
                int b = Math.min(255, height[i] / 16);
                slope[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                shadow[i] = Math.min(255, soft[i] / 16) << 24;
            }
        }
        int x = Math.round(right - ink.width()) - pad, y = Math.round(top) - pad;
        return new Glass(text, Bitmap.createBitmap(slope, w, h, Bitmap.Config.ARGB_8888), cover,
                Bitmap.createBitmap(shadow, w, h, Bitmap.Config.ARGB_8888), x, y, pad, Math.max(1, ink.height()));
    }

    /** Box blur (passes of radius r in both directions); anything outside the image counts as empty. */
    private static int[] blur(int[] src, int w, int h, int r, int passes) {
        int[] a = src.clone(), b = new int[src.length];
        for (int pass = 0; pass < passes; pass++) {
            box(a, b, w, h, r, true);
            box(b, a, w, h, r, false);
        }
        return a;
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w, step = horizontal ? 1 : w, lineStep = horizontal ? w : 1;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep, sum = 0;
            for (int i = 0; i <= r && i < n; i++) sum += in[base + i * step];
            for (int i = 0; i < n; i++) {
                out[base + i * step] = (sum + div / 2) / div;
                int add = i + r + 1, sub = i - r;
                if (add < n) sum += in[base + add * step];
                if (sub >= 0) sum -= in[base + sub * step];
            }
        }
    }

    // MARK: - Without photos: the big clock over the aurora

    private void drawFallback(Canvas canvas, long up, float alpha) {
        float t = (up - startedAt) / 1000f;
        if (up - lastSky >= 100) {
            lastSky = up;
            scene.advance(timeOffset + t);
        }
        scene.drawBackground(canvas);
        if (dirty) {
            Canvas rc = content.beginRecording(w, h);
            try {
                drawContent(rc);
            } finally {
                content.endRecording();
            }
            dirty = false;
        }
        double phase = 2 * Math.PI * t / DRIFT_PERIOD_S;
        content.setTranslationX((float) (40 * k * Math.sin(phase)));
        content.setTranslationY((float) (26 * k * Math.sin(2 * phase + 0.7)));
        content.setAlpha(alpha);
        content.draw(canvas);
    }

    private void drawContent(Canvas c) {
        float cx = w / 2f;
        float clockCap = Ui.cap(clockPaint);
        float clockBase = h * 0.5f + clockCap / 2 - 10 * k;
        drawDateWeather(c, cx, clockBase - clockCap - 34 * k, datePaint);
        c.drawText(clock, cx, clockBase, clockPaint);
        if(HomeClimate.enabled()) indoorClimateRow.centered(c,cx,clockBase+76*k,homeClimate);
        calendarVisual.summary(c,cx,h-78*k,true);
    }
}
