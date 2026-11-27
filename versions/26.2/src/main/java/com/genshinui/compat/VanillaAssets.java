package com.genshinui.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;

/** Minecraft 26.2: the vanilla pack is itself a PackResources. */
public final class VanillaAssets {
    private VanillaAssets() {}

    public static IoSupplier<InputStream> open(Identifier id) {
        return Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, id);
    }
}
