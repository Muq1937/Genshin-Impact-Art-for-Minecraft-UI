package com.genshinui.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;

/** Minecraft 26.3: the vanilla pack exposes its resources through fullResources(). */
public final class VanillaAssets {
    private VanillaAssets() {}

    public static IoSupplier<InputStream> open(Identifier id) {
        return Minecraft.getInstance().getVanillaPackResources().fullResources().getResource(PackType.CLIENT_RESOURCES, id);
    }
}
