package io.github.aubergineux.riposte.kit;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/** The ten built-in modes. Written to kits.yml on first start, after which kits.yml is the source of truth. */
public final class DefaultKits {

    private DefaultKits() {
    }

    public static List<Kit> create() {
        List<Kit> kits = new ArrayList<>();
        kits.add(noDebuff());
        kits.add(sword());
        kits.add(axe());
        kits.add(crystal());
        kits.add(uhc());
        kits.add(buildUhc());
        kits.add(sumo());
        kits.add(boxing());
        kits.add(bridge());
        kits.add(mace());
        for (int i = 0; i < kits.size(); i++) {
            kits.get(i).order(i);
        }
        return kits;
    }

    public static Kit byId(String id) {
        for (Kit kit : create()) {
            if (kit.id().equalsIgnoreCase(id)) {
                return kit;
            }
        }
        return null;
    }

    private static Kit noDebuff() {
        Kit kit = kit("nodebuff", "<#ff5d73>NoDebuff", Material.SPLASH_POTION, "Diamond gear, instant health II and pearls.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3);
        c[1] = ItemStack.of(Material.ENDER_PEARL, 16);
        c[2] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        c[3] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        c[8] = ItemStack.of(Material.COOKED_BEEF, 64);
        c[17] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        c[26] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        c[35] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        for (int i = 0; i < 36; i++) {
            if (c[i] == null) {
                c[i] = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING);
            }
        }
        armor(c, "DIAMOND", Enchantment.PROTECTION, 2);
        kit.contents(c);
        kit.rules().hunger = true;
        kit.rules().pearlCooldownSeconds = 15;
        return kit;
    }

    private static Kit sword() {
        Kit kit = kit("sword", "<#7cc4ff>Sword", Material.DIAMOND_SWORD, "Pure swordplay in full diamond.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1);
        armor(c, "DIAMOND", Enchantment.PROTECTION, 1);
        kit.contents(c);
        return kit;
    }

    private static Kit axe() {
        Kit kit = kit("axe", "<#ffb547>Axe", Material.DIAMOND_AXE, "Axe, shield and sword. Disable shields, then strike.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ItemStack.of(Material.DIAMOND_AXE);
        c[1] = ItemStack.of(Material.DIAMOND_SWORD);
        c[2] = ItemStack.of(Material.CROSSBOW);
        c[3] = ItemStack.of(Material.BOW);
        c[8] = ItemStack.of(Material.ARROW, 6);
        c[Kit.OFFHAND] = ItemStack.of(Material.SHIELD);
        armor(c, "DIAMOND", null, 0);
        kit.contents(c);
        return kit;
    }

    private static Kit crystal() {
        Kit kit = kit("crystal", "<#d17cff>Crystal", Material.END_CRYSTAL, "Netherite, crystals, anchors and totems.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        c[1] = ItemStack.of(Material.END_CRYSTAL, 64);
        c[2] = ItemStack.of(Material.OBSIDIAN, 64);
        c[3] = ItemStack.of(Material.RESPAWN_ANCHOR, 32);
        c[4] = ItemStack.of(Material.GLOWSTONE, 64);
        c[5] = ItemStack.of(Material.ENDER_PEARL, 16);
        c[6] = ItemStack.of(Material.GOLDEN_APPLE, 64);
        c[7] = ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5);
        c[8] = ItemStack.of(Material.TOTEM_OF_UNDYING);
        c[9] = ItemStack.of(Material.EXPERIENCE_BOTTLE, 64);
        c[10] = ItemStack.of(Material.END_CRYSTAL, 64);
        c[11] = ItemStack.of(Material.OBSIDIAN, 64);
        for (int i = 27; i < 36; i++) {
            c[i] = ItemStack.of(Material.TOTEM_OF_UNDYING);
        }
        c[Kit.OFFHAND] = ItemStack.of(Material.TOTEM_OF_UNDYING);
        armor(c, "NETHERITE", Enchantment.PROTECTION, 4);
        c[38] = ench(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3);
        c[39] = ench(Material.NETHERITE_HELMET, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3);
        kit.contents(c);
        kit.rules().build = true;
        kit.rules().buildHeight = 20;
        kit.rules().hunger = true;
        return kit;
    }

    private static Kit uhc() {
        Kit kit = kit("uhc", "<#ffd166>UHC", Material.GOLDEN_APPLE, "No natural regeneration. Golden apples heal.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3);
        c[1] = ItemStack.of(Material.FISHING_ROD);
        c[2] = ench(Material.BOW, Enchantment.POWER, 3);
        c[3] = ItemStack.of(Material.GOLDEN_APPLE, 6);
        c[8] = ItemStack.of(Material.COOKED_BEEF, 64);
        c[9] = ItemStack.of(Material.ARROW, 16);
        armor(c, "DIAMOND", Enchantment.PROTECTION, 2);
        kit.contents(c);
        kit.rules().naturalRegen = false;
        kit.rules().hunger = true;
        return kit;
    }

    private static Kit buildUhc() {
        Kit kit = kit("builduhc", "<#ff8a3d>BuildUHC", Material.LAVA_BUCKET, "UHC with blocks, buckets and building.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3);
        c[1] = ItemStack.of(Material.FISHING_ROD);
        c[2] = ench(Material.BOW, Enchantment.POWER, 3);
        c[3] = ItemStack.of(Material.COBBLESTONE, 64);
        c[4] = ItemStack.of(Material.WATER_BUCKET);
        c[5] = ItemStack.of(Material.LAVA_BUCKET);
        c[6] = ItemStack.of(Material.GOLDEN_APPLE, 6);
        c[7] = ItemStack.of(Material.OAK_PLANKS, 64);
        c[8] = ItemStack.of(Material.COOKED_BEEF, 64);
        c[9] = ItemStack.of(Material.ARROW, 16);
        c[10] = ItemStack.of(Material.DIAMOND_PICKAXE);
        c[11] = ItemStack.of(Material.DIAMOND_AXE);
        c[12] = ItemStack.of(Material.WATER_BUCKET);
        c[13] = ItemStack.of(Material.LAVA_BUCKET);
        c[14] = ItemStack.of(Material.COBBLESTONE, 64);
        armor(c, "DIAMOND", Enchantment.PROTECTION, 2);
        kit.contents(c);
        kit.rules().naturalRegen = false;
        kit.rules().hunger = true;
        kit.rules().build = true;
        return kit;
    }

    private static Kit sumo() {
        Kit kit = kit("sumo", "<#5ee6a8>Sumo", Material.SLIME_BALL, "Knock your opponent off the platform.");
        kit.contents(new ItemStack[Kit.SIZE]);
        kit.rules().winCondition = KitRules.WinCondition.SUMO;
        kit.rules().arenaTag = "sumo";
        kit.rules().fallDamage = false;
        kit.rules().roundsToWin = 1;
        return kit;
    }

    private static Kit boxing() {
        Kit kit = kit("boxing", "<#ff6fb5>Boxing", Material.GOLDEN_SWORD, "First to 100 hits wins. No damage.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1);
        kit.contents(c);
        kit.rules().winCondition = KitRules.WinCondition.BOXING;
        kit.rules().boxingHits = 100;
        kit.rules().fallDamage = false;
        kit.effects().add(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        return kit;
    }

    private static Kit bridge() {
        Kit kit = kit("bridge", "<#4da3ff>Bridge", Material.BLUE_TERRACOTTA, "Bridge across and jump into the enemy goal. First to 5.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ItemStack.of(Material.IRON_SWORD);
        c[1] = ItemStack.of(Material.BOW);
        c[2] = ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 2);
        c[3] = ItemStack.of(Material.BLUE_TERRACOTTA, 64);
        c[4] = ItemStack.of(Material.BLUE_TERRACOTTA, 64);
        c[5] = ItemStack.of(Material.GOLDEN_APPLE, 8);
        c[8] = ItemStack.of(Material.ARROW);
        c[36] = ItemStack.of(Material.LEATHER_BOOTS);
        c[37] = ItemStack.of(Material.LEATHER_LEGGINGS);
        c[38] = ItemStack.of(Material.LEATHER_CHESTPLATE);
        kit.contents(c);
        kit.rules().winCondition = KitRules.WinCondition.BRIDGE;
        kit.rules().arenaTag = "bridge";
        kit.rules().roundsToWin = 5;
        kit.rules().build = true;
        kit.rules().buildHeight = 8;
        kit.rules().fallDamage = false;
        kit.rules().respawnSeconds = 2;
        return kit;
    }

    private static Kit mace() {
        Kit kit = kit("mace", "<#9aa7b8>Mace", Material.MACE, "Wind charges, smash attacks and a shield.");
        ItemStack[] c = new ItemStack[Kit.SIZE];
        c[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5);
        c[1] = ench(Material.MACE, Enchantment.DENSITY, 4, Enchantment.WIND_BURST, 1);
        c[2] = ItemStack.of(Material.WIND_CHARGE, 64);
        c[3] = ItemStack.of(Material.ENDER_PEARL, 16);
        c[4] = ItemStack.of(Material.GOLDEN_APPLE, 16);
        c[8] = ItemStack.of(Material.TOTEM_OF_UNDYING);
        c[Kit.OFFHAND] = ItemStack.of(Material.SHIELD);
        armor(c, "NETHERITE", Enchantment.PROTECTION, 3);
        kit.contents(c);
        kit.rules().hunger = true;
        return kit;
    }

    private static Kit kit(String id, String name, Material icon, String description) {
        Kit kit = new Kit(id);
        kit.displayName(name);
        kit.icon(icon);
        kit.description(description);
        return kit;
    }

    private static void armor(ItemStack[] c, String tier, Enchantment enchantment, int level) {
        String[] pieces = {"BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET"};
        for (int i = 0; i < 4; i++) {
            Material material = Material.valueOf(tier + "_" + pieces[i]);
            ItemStack item = ItemStack.of(material);
            item.editMeta(meta -> {
                if (enchantment != null) {
                    meta.addEnchant(enchantment, level, true);
                }
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            });
            c[36 + i] = item;
        }
    }

    private static ItemStack ench(Material material, Object... pairs) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            for (int i = 0; i < pairs.length; i += 2) {
                meta.addEnchant((Enchantment) pairs[i], (Integer) pairs[i + 1], true);
            }
        });
        return item;
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return item;
    }
}
