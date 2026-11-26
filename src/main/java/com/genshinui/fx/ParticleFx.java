package com.genshinui.fx;

import com.genshinui.ThemeManager;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Themed particles (feathers, petals, bubbles, leaves, sparks) drifting inside a GUI rectangle. */
public final class ParticleFx {
    private ParticleFx() {}

    private static final class P {
        float x, y, vx, vy, rot, vr, size, life, age, phase, sway;
    }

    private static final class Field {
        final List<P> ps = new ArrayList<>();
        long last;
        int w, h;
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Random RNG = new Random();

    public static void render(DrawContext ctx, String key, int rx, int ry, int rw, int rh, int count, float sizeScale) {
        if (!ThemeManager.live() || !ThemeManager.settings.particles) return;
        String style = ThemeManager.active().particle;
        if ("none".equals(style)) return;

        Field f = FIELDS.computeIfAbsent(key, k -> new Field());
        long now = Util.getMeasuringTimeMs();
        float dt = f.last == 0 ? 0 : Math.min(0.1f, (now - f.last) / 1000f);
        f.last = now;
        if (f.w != rw || f.h != rh) { // region changed: reseed
            f.ps.clear();
            f.w = rw;
            f.h = rh;
        }
        while (f.ps.size() < count) f.ps.add(spawn(style, rw, rh, f.ps.isEmpty() || dt == 0, sizeScale));

        ctx.enableScissor(rx, ry, rx + rw, ry + rh);
        Matrix3x2fStack m = ctx.getMatrices();
        for (int i = 0; i < f.ps.size(); i++) {
            P p = f.ps.get(i);
            p.age += dt;
            p.x += (p.vx + (float) Math.sin(p.age * 1.3f + p.phase) * p.sway) * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            boolean out = p.y > rh + 12 || p.y < -12 || p.x < -12 || p.x > rw + 12;
            if (p.age > p.life || out) {
                f.ps.set(i, p = spawn(style, rw, rh, false, sizeScale));
            }
            float fade = Math.min(1f, Math.min(p.age / 0.8f, (p.life - p.age) / 0.8f));
            if ("spark".equals(style)) fade *= 0.5f + 0.5f * (float) Math.sin(p.age * 4 + p.phase);
            int alpha = (int) (Math.max(0, fade) * 0.85f * 255);
            if (alpha <= 2) continue;
            m.pushMatrix();
            m.translate(rx + p.x, ry + p.y);
            m.rotate(p.rot);
            m.scale(p.size / 16f, p.size / 16f);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ThemeManager.PARTICLE, -8, -8, 0, 0, 16, 16, 64, 64, 64, 64, (alpha << 24) | 0xFFFFFF);
            m.popMatrix();
        }
        ctx.disableScissor();
    }

    private static P spawn(String style, int w, int h, boolean anywhere, float sizeScale) {
        P p = new P();
        p.phase = RNG.nextFloat() * 6.28f;
        p.rot = RNG.nextFloat() * 6.28f;
        p.size = (5 + RNG.nextFloat() * 5) * sizeScale;
        p.life = 6 + RNG.nextFloat() * 6;
        p.x = RNG.nextFloat() * w;
        switch (style) {
            case "bubble" -> { // rise
                p.y = anywhere ? RNG.nextFloat() * h : h + 6;
                p.vy = -(6 + RNG.nextFloat() * 8);
                p.sway = 4;
                p.vr = 0;
                p.rot = 0;
                p.size *= 0.8f;
            }
            case "spark" -> { // twinkle in place
                p.y = RNG.nextFloat() * h;
                p.vy = -1.5f;
                p.sway = 1;
                p.vr = 0.4f;
                p.life = 2 + RNG.nextFloat() * 3;
                p.size *= 0.7f;
            }
            default -> { // feather / petal / leaf: fall and sway
                p.y = anywhere ? RNG.nextFloat() * h : -6;
                p.vy = 5 + RNG.nextFloat() * 7;
                p.vx = -3 + RNG.nextFloat() * 2;
                p.sway = 6;
                p.vr = (RNG.nextFloat() - 0.5f) * 1.6f;
            }
        }
        if (anywhere) p.age = RNG.nextFloat() * p.life * 0.5f;
        return p;
    }
}
