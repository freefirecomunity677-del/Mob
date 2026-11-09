package com.freefirecomunity677del.mobpowers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

final class PowerCatalog {
    enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }
    record Power(String mob, String ability, String description, Rarity rarity, int cooldown) {}

    private static final Map<String, String> ABILITIES = Map.ofEntries(
        Map.entry("SLIME", "Mega Bounce"), Map.entry("MAGMA_CUBE", "Magma Bounce"),
        Map.entry("ENDERMAN", "Shadow Teleport"), Map.entry("CREEPER", "Controlled Blast"),
        Map.entry("BLAZE", "Flame Dash"), Map.entry("GHAST", "Ghast Fireball"),
        Map.entry("PHANTOM", "Phantom Flight"), Map.entry("WARDEN", "Sonic Boom"),
        Map.entry("WITHER", "Wither Skull"), Map.entry("ENDER_DRAGON", "Dragon Flight"),
        Map.entry("IRON_GOLEM", "Ground Slam"), Map.entry("SPIDER", "Web Climb"),
        Map.entry("CAVE_SPIDER", "Venom Strike"), Map.entry("WOLF", "Pack Sprint"),
        Map.entry("BEE", "Honey Flight"), Map.entry("TURTLE", "Turtle Shell"),
        Map.entry("DOLPHIN", "Dolphin's Grace"), Map.entry("SQUID", "Ink Escape"),
        Map.entry("GLOW_SQUID", "Glowing Veil"), Map.entry("GOAT", "Ramming Charge"),
        Map.entry("RABBIT", "Moon Leap"), Map.entry("FOX", "Fox Dash"),
        Map.entry("POLAR_BEAR", "Arctic Slam"), Map.entry("SNOW_GOLEM", "Frost Volley"),
        Map.entry("STRIDER", "Lava Stride"), Map.entry("PIGLIN", "Golden Rage"),
        Map.entry("HOGLIN", "Hoglin Charge"), Map.entry("ZOGLIN", "Zoglin Rampage"),
        Map.entry("SHULKER", "Levitation Shot"), Map.entry("SILVERFISH", "Burrow Escape"),
        Map.entry("ENDERMITE", "Rift Blink"), Map.entry("VEX", "Vex Phase"),
        Map.entry("ALLAY", "Allay's Call"), Map.entry("CAMEL", "Desert Long Jump"),
        Map.entry("ARMADILLO", "Defensive Roll"), Map.entry("BREEZE", "Wind Burst"),
        Map.entry("BOGGED", "Poison Arrow"), Map.entry("CREAKING", "Creaking Stare"),
        Map.entry("HAPPY_GHAST", "Happy Ghast Flight"), Map.entry("COW", "Bovine Charge"),
        Map.entry("MOOSHROOM", "Fungal Bloom"), Map.entry("PIG", "Piggy Dash"),
        Map.entry("SHEEP", "Woolly Guard"), Map.entry("CHICKEN", "Feather Fall"),
        Map.entry("HORSE", "Gallop"), Map.entry("DONKEY", "Pack Mule's Leap"),
        Map.entry("MULE", "Mule Kick"), Map.entry("CAT", "Nine Lives"),
        Map.entry("OCELOT", "Jungle Prowl"), Map.entry("PARROT", "Parrot's Flight"),
        Map.entry("VILLAGER", "Hero of the Village"), Map.entry("WANDERING_TRADER", "Wandering Escape"),
        Map.entry("PANDA", "Bamboo Roll"), Map.entry("LLAMA", "Llama Spit"),
        Map.entry("FROG", "Frog Tongue Leap"), Map.entry("TADPOLE", "Tadpole Slip"),
        Map.entry("BAT", "Cave Glide"), Map.entry("PUFFERFISH", "Puffer Defense"),
        Map.entry("TROPICAL_FISH", "Reef Rush"), Map.entry("SALMON", "Upstream Surge"),
        Map.entry("COD", "Ocean Current"), Map.entry("AXOLOTL", "Regeneration"),
        Map.entry("STRAY", "Frost Shot"), Map.entry("HUSK", "Desert Resilience"),
        Map.entry("DROWNED", "Tidal Trident"), Map.entry("ZOMBIE", "Undead Strength"),
        Map.entry("ZOMBIE_VILLAGER", "Plague Touch"), Map.entry("SKELETON", "Arrow Barrage"),
        Map.entry("PILLAGER", "Quick Shot"), Map.entry("VINDICATOR", "Axe Charge"),
        Map.entry("EVOKER", "Fang Summon"), Map.entry("RAVAGER", "Ravager Rush"),
        Map.entry("WITCH", "Witch's Brew"), Map.entry("GUARDIAN", "Guardian Beam"),
        Map.entry("ELDER_GUARDIAN", "Elder's Curse"), Map.entry("TRIAL_SPAWNER", "Trial Pulse")
    );
    private static final Map<String, Rarity> RARITIES = Map.ofEntries(
        Map.entry("CREEPER", Rarity.LEGENDARY), Map.entry("GHAST", Rarity.LEGENDARY),
        Map.entry("PHANTOM", Rarity.LEGENDARY), Map.entry("WARDEN", Rarity.LEGENDARY),
        Map.entry("WITHER", Rarity.LEGENDARY), Map.entry("ENDER_DRAGON", Rarity.LEGENDARY),
        Map.entry("VEX", Rarity.LEGENDARY), Map.entry("BREEZE", Rarity.LEGENDARY),
        Map.entry("HAPPY_GHAST", Rarity.LEGENDARY), Map.entry("SLIME", Rarity.EPIC),
        Map.entry("MAGMA_CUBE", Rarity.EPIC), Map.entry("IRON_GOLEM", Rarity.EPIC),
        Map.entry("HOGLIN", Rarity.EPIC), Map.entry("ZOGLIN", Rarity.EPIC),
        Map.entry("SHULKER", Rarity.EPIC), Map.entry("CREAKING", Rarity.EPIC),
        Map.entry("ENDERMAN", Rarity.RARE), Map.entry("BLAZE", Rarity.RARE),
        Map.entry("CAVE_SPIDER", Rarity.RARE), Map.entry("BEE", Rarity.RARE),
        Map.entry("GLOW_SQUID", Rarity.RARE), Map.entry("GOAT", Rarity.RARE),
        Map.entry("POLAR_BEAR", Rarity.RARE), Map.entry("SNOW_GOLEM", Rarity.RARE),
        Map.entry("STRIDER", Rarity.RARE), Map.entry("PIGLIN", Rarity.RARE),
        Map.entry("ENDERMITE", Rarity.RARE), Map.entry("ALLAY", Rarity.RARE),
        Map.entry("ARMADILLO", Rarity.RARE), Map.entry("BOGGED", Rarity.RARE)
    );
    private final JavaPlugin plugin;
    private final NamespacedKey key;

    PowerCatalog(JavaPlugin plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "mob_power"); }

    List<String> mobTypes() {
        return Arrays.stream(EntityType.values()).filter(t -> t.getEntityClass() != null)
            .filter(t -> LivingEntity.class.isAssignableFrom(t.getEntityClass()))
            .filter(t -> t != EntityType.PLAYER && t.isSpawnable())
            .map(Enum::name).sorted().toList();
    }

    Power create(String mob) {
        String name = mob.toUpperCase(Locale.ROOT);
        String ability = plugin.getConfig().getString("mobs." + name + ".ability", ABILITIES.getOrDefault(name, title(name) + " Aspect"));
        Rarity rarity;
        try { rarity = Rarity.valueOf(plugin.getConfig().getString("mobs." + name + ".rarity", RARITIES.getOrDefault(name, defaultRarity(name)).name()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { rarity = Rarity.COMMON; }
        int cooldown = plugin.getConfig().getInt("mobs." + name + ".cooldown", plugin.getConfig().getInt("abilities.cooldown-seconds." + rarity.name(), switch(rarity) { case COMMON -> 15; case UNCOMMON -> 20; case RARE -> 40; default -> 60; }));
        return new Power(name, ability, plugin.getConfig().getString("mobs." + name + ".description", description(name, ability)), rarity, Math.max(1, cooldown));
    }

    ItemStack item(Power power, long remaining) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("power-item.material", "NETHER_STAR"));
        ItemStack item = new ItemStack(material == null ? Material.NETHER_STAR : material);
        ItemMeta meta = item.getItemMeta();
        ChatColor color = color(power.rarity());
        meta.setDisplayName(color + "" + ChatColor.BOLD + title(power.mob()) + " Power");
        meta.setLore(List.of(ChatColor.GRAY + "Ability: " + ChatColor.WHITE + power.ability(),
            ChatColor.GRAY + "Rarity: " + color + power.rarity(),
            ChatColor.GRAY + "Cooldown: " + ChatColor.WHITE + power.cooldown() + " seconds",
            ChatColor.GRAY + "Status: " + (remaining > 0 ? ChatColor.RED + remaining + "s" : ChatColor.GREEN + "Ready"),
            ChatColor.DARK_GRAY + power.description(), ChatColor.YELLOW + "Right-click to activate"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, power.mob());
        item.setItemMeta(meta);
        return item;
    }

    boolean isPowerItem(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING); }
    static ChatColor color(Rarity rarity) { return switch(rarity) { case COMMON -> ChatColor.WHITE; case UNCOMMON -> ChatColor.GREEN; case RARE -> ChatColor.BLUE; case EPIC -> ChatColor.LIGHT_PURPLE; case LEGENDARY -> ChatColor.GOLD; }; }
    static String title(String input) { return Arrays.stream(input.toLowerCase(Locale.ROOT).split("_")).map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1)).reduce((a,b) -> a + " " + b).orElse(input); }
    private static Rarity defaultRarity(String mob) { return mob.contains("BOSS") || mob.equals("ELDER_GUARDIAN") ? Rarity.LEGENDARY : Rarity.UNCOMMON; }
    private static String description(String mob, String ability) { return ability + " channels the natural traits of the " + title(mob) + "."; }
}
