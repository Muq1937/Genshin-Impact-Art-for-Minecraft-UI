package com.genshinui.gen;

import java.awt.image.BufferedImage;

/** Reads unmodified vanilla assets (paths like "textures/gui/container/inventory.png" in the minecraft namespace). */
public interface VanillaSource {
    BufferedImage image(String path);

    String text(String path);
}
