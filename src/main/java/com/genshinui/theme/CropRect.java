package com.genshinui.theme;

import java.awt.Rectangle;

/**
 * A crop of a theme image, stored resolution-independently as a center point and a height,
 * all as fractions of the source image. The width follows from the target's aspect ratio,
 * so a crop can never be distorted.
 */
public class CropRect {
    /** Index into {@link Theme#images}. */
    public int image = 0;
    public double cx = 0.5, cy = 0.5;
    /** Crop height as a fraction of image height. */
    public double h = 1.0;

    public CropRect() {}

    public CropRect(int image, double cx, double cy, double h) {
        this.image = image;
        this.cx = cx;
        this.cy = cy;
        this.h = h;
    }

    public CropRect copy() {
        return new CropRect(image, cx, cy, h);
    }

    /** Pixel rectangle inside an imgW x imgH image, for a target of the given aspect (w/h). Always in bounds. */
    public Rectangle toPixels(int imgW, int imgH, double aspect) {
        double hp = Math.max(4, h * imgH);
        double wp = hp * aspect;
        if (wp > imgW) { wp = imgW; hp = wp / aspect; }
        if (hp > imgH) { hp = imgH; wp = hp * aspect; }
        double x = cx * imgW - wp / 2.0;
        double y = cy * imgH - hp / 2.0;
        x = Math.max(0, Math.min(imgW - wp, x));
        y = Math.max(0, Math.min(imgH - hp, y));
        return new Rectangle((int) Math.round(x), (int) Math.round(y),
                Math.max(1, (int) Math.round(wp)), Math.max(1, (int) Math.round(hp)));
    }

    /** Re-normalizes this crop so it fits inside the image (used after drag/zoom in the editor). */
    public void clamp(int imgW, int imgH, double aspect) {
        Rectangle r = toPixels(imgW, imgH, aspect);
        h = (double) r.height / imgH;
        cx = (r.x + r.width / 2.0) / imgW;
        cy = (r.y + r.height / 2.0) / imgH;
    }

    /** Largest crop of the given aspect, centered. */
    public static CropRect fill(int imgW, int imgH, double aspect) {
        CropRect c = new CropRect(0, 0.5, 0.5, 1.0);
        c.clamp(imgW, imgH, aspect);
        return c;
    }
}
