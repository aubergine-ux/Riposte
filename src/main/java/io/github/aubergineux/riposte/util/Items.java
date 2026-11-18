package io.github.aubergineux.riposte.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/** Small fluent builder for menu and kit items. */
public final class Items {

    private final ItemStack stack;
    private final List<Consumer<ItemMeta>> edits = new ArrayList<>();

    private Items(ItemStack stack) {
        this.stack = stack;
    }

    public static Items of(Material material) {
        return new Items(ItemStack.of(material));
    }

    public static Items of(Material material, int amount) {
        return new Items(ItemStack.of(material, Math.max(1, Math.min(amount, material.getMaxStackSize()))));
    }

    public static Items of(ItemStack stack) {
        return new Items(stack.clone());
    }

    public Items amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(amount, 99)));
        return this;
    }

    public Items name(Component name) {
        edits.add(meta -> meta.displayName(name));
        return this;
    }

    public Items name(String miniMessage, TagResolver... resolvers) {
        return name(Text.item(miniMessage, resolvers));
    }

    public Items lore(List<Component> lore) {
        edits.add(meta -> meta.lore(lore));
        return this;
    }

    public Items loreStrings(List<String> lore, TagResolver... resolvers) {
        return lore(Text.itemLines(lore, resolvers));
    }

    public Items enchant(Enchantment enchantment, int level) {
        edits.add(meta -> meta.addEnchant(enchantment, level, true));
        return this;
    }

    public Items glow(boolean glow) {
        if (glow) {
            edits.add(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return this;
    }

    public Items unbreakable() {
        edits.add(meta -> meta.setUnbreakable(true));
        return this;
    }

    public Items hideAll() {
        edits.add(meta -> meta.addItemFlags(ItemFlag.values()));
        return this;
    }

    public Items tag(NamespacedKey key, String value) {
        edits.add(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value));
        return this;
    }

    public Items skull(OfflinePlayer owner) {
        edits.add(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(owner);
            }
        });
        return this;
    }

    public Items meta(Consumer<ItemMeta> edit) {
        edits.add(edit);
        return this;
    }

    public ItemStack build() {
        if (!edits.isEmpty()) {
            stack.editMeta(meta -> edits.forEach(e -> e.accept(meta)));
        }
        return stack;
    }

    public static String tagOf(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static String encode(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? ItemStack.empty() : items[i];
        }
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(copy));
    }

    public static ItemStack[] decode(String data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.isEmpty()) {
            return out;
        }
        ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));
        for (int i = 0; i < Math.min(size, decoded.length); i++) {
            out[i] = decoded[i] == null || decoded[i].isEmpty() ? null : decoded[i];
        }
        return out;
    }

    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() ? fallback : m;
    }
}
