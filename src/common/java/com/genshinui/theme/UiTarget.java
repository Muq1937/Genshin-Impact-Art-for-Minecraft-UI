package com.genshinui.theme;

import java.util.List;

/**
 * A themeable piece of UI. Each target gets its own crop of the theme art.
 * CONTAINER targets map to one or more vanilla textures (paths relative to textures/gui/, no extension).
 */
public enum UiTarget {
    INVENTORY("inventory", "Inventory", Kind.CONTAINER, 176, 166, "container/inventory"),
    CHEST("chest", "Chest / Barrel", Kind.CONTAINER, 176, 222, "container/generic_54"),
    CRAFTING("crafting", "Crafting Table / Crafter", Kind.CONTAINER, 176, 166, "container/crafting_table", "container/crafter"),
    FURNACE("furnace", "Furnace / Smoker / Blast", Kind.CONTAINER, 176, 166, "container/furnace", "container/blast_furnace", "container/smoker"),
    STORAGE("storage", "Shulker / Dispenser / Hopper", Kind.CONTAINER, 176, 166, "container/shulker_box", "container/dispenser", "container/hopper"),
    CREATIVE("creative", "Creative Inventory", Kind.CONTAINER, 195, 136,
            "container/creative_inventory/tab_items", "container/creative_inventory/tab_inventory", "container/creative_inventory/tab_item_search"),
    STATIONS("stations", "Workstations & Villager", Kind.CONTAINER, 176, 166,
            "container/anvil", "container/smithing", "container/grindstone", "container/stonecutter", "container/loom", "container/cartography_table",
            "container/enchanting_table", "container/brewing_stand", "container/beacon", "container/villager", "container/horse", "container/nautilus"),
    RECIPE_BOOK("recipe_book", "Recipe Book: Inventory", Kind.CONTAINER, 147, 166, "recipe_book"),
    RECIPE_BOOK_CRAFTING("recipe_book_crafting", "Recipe Book: Crafting", Kind.CONTAINER, 147, 166, "recipe_book"),
    RECIPE_BOOK_FURNACE("recipe_book_furnace", "Recipe Book: Furnaces", Kind.CONTAINER, 147, 166, "recipe_book"),
    HOTBAR("hotbar", "Hotbar", Kind.HOTBAR, 182, 22),
    MENU("menu", "Menu Background", Kind.MENU, 16, 9);

    public enum Kind { CONTAINER, HOTBAR, MENU }

    public final String id;
    public final String label;
    public final Kind kind;
    /** Logical size of the primary UI element; defines the crop aspect ratio. */
    public final int width, height;
    public final List<String> textures;

    UiTarget(String id, String label, Kind kind, int width, int height, String... textures) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.textures = List.of(textures);
    }

    /** Targets that re-skin an already-used vanilla texture are written to their own mod texture instead. */
    public String customOutput() {
        return switch (this) {
            case RECIPE_BOOK_CRAFTING -> "assets/genshinui/textures/gui/recipe_book_crafting.png";
            case RECIPE_BOOK_FURNACE -> "assets/genshinui/textures/gui/recipe_book_furnace.png";
            default -> null;
        };
    }

    /** A target whose crop is copied when this one has none yet (older themes). */
    public UiTarget cropFallback() {
        return this == RECIPE_BOOK_CRAFTING || this == RECIPE_BOOK_FURNACE ? RECIPE_BOOK : null;
    }

    /** For recipe book targets: the screen they sit next to. */
    public UiTarget recipeHost() {
        return switch (this) {
            case RECIPE_BOOK -> INVENTORY;
            case RECIPE_BOOK_CRAFTING -> CRAFTING;
            case RECIPE_BOOK_FURNACE -> FURNACE;
            default -> null;
        };
    }

    public boolean isRecipeBook() {
        return this == RECIPE_BOOK || this == RECIPE_BOOK_CRAFTING || this == RECIPE_BOOK_FURNACE;
    }

    public double aspect() {
        return (double) width / height;
    }

    public static UiTarget byId(String id) {
        for (UiTarget t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
