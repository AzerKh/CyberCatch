package com.zer.simplegame;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Catch the apples with the basket. Avoid the bombs.
 * Missing an apple or catching a bomb costs one life.
 */
public class GameView extends SurfaceView implements Runnable {

    private static class Item {
        float x, y, speed;
        boolean bomb;
    }

    private Thread thread;
    private volatile boolean running;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random random = new Random();
    private final SharedPreferences prefs;

    private final List<Item> items = new ArrayList<>();

    private volatile float targetX = -1;
    private volatile boolean restartRequested = false;

    private float basketX;
    private float spawnTimer;
    private int score;
    private int best;
    private int lives = 3;
    private boolean gameOver;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        prefs = context.getSharedPreferences("catch_game", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
    }

    // ---------- lifecycle ----------

    public void resume() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
        try {
            if (thread != null) thread.join();
        } catch (InterruptedException ignored) {
        }
    }

    // ---------- game loop ----------

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1_000_000_000f, 0.05f);
            last = now;

            if (holder.getSurface().isValid()) {
                update(dt);
                Canvas canvas = holder.lockCanvas();
                if (canvas != null) {
                    try {
                        drawGame(canvas);
                    } finally {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void resetGame() {
        items.clear();
        score = 0;
        lives = 3;
        spawnTimer = 0.5f;
        gameOver = false;
    }

    private void update(float dt) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        if (restartRequested) {
            restartRequested = false;
            resetGame();
        }

        if (targetX < 0) {
            targetX = w / 2f;
            basketX = w / 2f;
        }
        basketX += (targetX - basketX) * Math.min(1f, dt * 18f);

        if (gameOver) return;

        float radius = w * 0.04f;
        float basketW = w * 0.22f;
        float basketH = h * 0.03f;
        float basketY = h * 0.88f;

        // spawn
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            Item it = new Item();
            it.x = radius + random.nextFloat() * (w - 2 * radius);
            it.y = -radius;
            it.speed = h * (0.30f + random.nextFloat() * 0.20f + score * 0.008f);
            it.bomb = random.nextFloat() < 0.2f;
            items.add(it);
            spawnTimer = Math.max(0.35f, 1.0f - score * 0.02f);
        }

        // move + collide
        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item it = iterator.next();
            it.y += it.speed * dt;

            boolean inBasketY = it.y + radius >= basketY && it.y - radius <= basketY + basketH;
            boolean inBasketX = Math.abs(it.x - basketX) <= basketW / 2f + radius / 2f;

            if (inBasketY && inBasketX) {
                iterator.remove();
                if (it.bomb) lives--;
                else score++;
            } else if (it.y - radius > h) {
                iterator.remove();
                if (!it.bomb) lives--;
            }
        }

        if (lives <= 0) {
            gameOver = true;
            if (score > best) {
                best = score;
                prefs.edit().putInt("best", best).apply();
            }
        }
    }

    // ---------- drawing ----------

    private void drawGame(Canvas canvas)  {
        int w = getWidth();
        int h = getHeight();
        float radius = w * 0.04f;

        canvas.drawColor(Color.rgb(24, 32, 56));

        // items
        for (Item it : items) {
            if (it.bomb) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(40, 40, 40));
                canvas.drawCircle(it.x, it.y, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(radius * 0.15f);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(it.x, it.y, radius, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(230, 57, 70));
                canvas.drawCircle(it.x, it.y, radius, paint);
                paint.setColor(Color.rgb(80, 180, 80));
                canvas.drawCircle(it.x, it.y - radius, radius * 0.25f, paint);
            }
        }

        // basket
        float basketW = w * 0.22f;
        float basketH = h * 0.03f;
        float basketY = h * 0.88f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 193, 7));
        rect.set(basketX - basketW / 2f, basketY, basketX + basketW / 2f, basketY + basketH);
        canvas.drawRoundRect(rect, basketH / 2f, basketH / 2f, paint);

        // HUD
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(w * 0.06f);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Score: " + score, w * 0.04f, h * 0.06f, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Lives: " + Math.max(lives, 0), w * 0.96f, h * 0.06f, paint);
        paint.setTextSize(w * 0.04f);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Best: " + best, w * 0.04f, h * 0.10f, paint);

        if (gameOver) {
            paint.setColor(Color.argb(170, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(w * 0.12f);
            canvas.drawText("Game Over", w / 2f, h * 0.42f, paint);
            paint.setTextSize(w * 0.06f);
            canvas.drawText("Score: " + score, w / 2f, h * 0.49f, paint);
            canvas.drawText("Tap to play again", w / 2f, h * 0.56f, paint);
        }
    }

    // ---------- input ----------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (gameOver) restartRequested = true;
                targetX = event.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                targetX = event.getX();
                break;
        }
        return true;
    }
}
