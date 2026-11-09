package com.freefirecomunity677del.mobpowers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MobPowersPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private PowerCatalog catalog;
    private final Map<UUID, String> powers = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> rouletteRunning = new HashSet<>();
    private final Random random = new Random();
    private File dataFile;

    @Override public void onEnable() {
        saveDefaultConfig(); catalog = new PowerCatalog(this); dataFile = new File(getDataFolder(), "players.yml"); loadData();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("mobpowers")).setExecutor(this);
        getCommand("mobpowers").setTabCompleter(this);
        getServer().getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::ensurePowerItem), 20L, 40L);
    }
    @Override public void onDisable() { saveData(); for (Player p : Bukkit.getOnlinePlayers()) { p.setAllowFlight(false); p.setFlying(false); } }

    private void loadData() {
        if (!dataFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        for (String id : y.getConfigurationSection("players") == null ? List.<String>of() : y.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id); String mob = y.getString("players." + id + ".mob");
                if (mob != null) powers.put(uuid, mob);
                var section = y.getConfigurationSection("players." + id + ".cooldowns");
                if (section != null) { Map<String, Long> entries = new HashMap<>(); for (String ability : section.getKeys(false)) entries.put(ability, section.getLong(ability)); cooldowns.put(uuid, entries); }
            } catch (IllegalArgumentException ignored) { getLogger().warning("Ignoring invalid player data: " + id); }
        }
    }
    private void saveData() {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        powers.forEach((id, mob) -> { y.set("players." + id + ".mob", mob); Map<String,Long> cd = cooldowns.get(id); if (cd != null) cd.forEach((ability, time) -> y.set("players." + id + ".cooldowns." + ability, time)); });
        try { getDataFolder().mkdirs(); y.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save player data: " + e.getMessage()); }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!powers.containsKey(p.getUniqueId())) { String mob = randomMob(); powers.put(p.getUniqueId(), mob); saveData(); startRoulette(p, mob); }
        else Bukkit.getScheduler().runTask(this, () -> ensurePowerItem(p));
    }
    private String randomMob() { List<String> types = catalog.mobTypes(); return types.get(random.nextInt(types.size())); }

    private void startRoulette(Player p, String chosen) {
        if (!rouletteRunning.add(p.getUniqueId())) return;
        var cfg = getConfig();
        if (cfg.getBoolean("selection-area.enabled")) {
            World world = Bukkit.getWorld(cfg.getString("selection-area.world", "world"));
            if (world != null) p.teleport(new Location(world, cfg.getDouble("selection-area.x"), cfg.getDouble("selection-area.y"), cfg.getDouble("selection-area.z"), (float)cfg.getDouble("selection-area.yaw"), (float)cfg.getDouble("selection-area.pitch")));
        }
        boolean hide = cfg.getBoolean("roulette.hide-player", true);
        if (hide) for (Player other : Bukkit.getOnlinePlayers()) if (!other.equals(p)) other.hidePlayer(this, p);
        List<String> mobs = catalog.mobTypes(); int ticks = Math.max(20, cfg.getInt("roulette.ticks", 100));
        int[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!p.isOnline()) { task.cancel(); rouletteRunning.remove(p.getUniqueId()); return; }
            int progress = elapsed[0]; String show = progress >= ticks ? chosen : mobs.get(random.nextInt(mobs.size()));
            p.sendTitle(ChatColor.GOLD + "MOB POWER ROULETTE", ChatColor.YELLOW + PowerCatalog.title(show), 0, 3, 0);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .7f, 0.7f + (progress / (float)ticks));
            int interval = Math.max(2, cfg.getInt("roulette.start-interval", 2) + progress * Math.max(1, cfg.getInt("roulette.end-interval", 12) - cfg.getInt("roulette.start-interval", 2)) / ticks);
            elapsed[0] += interval;
            if (elapsed[0] >= ticks) {
                task.cancel(); rouletteRunning.remove(p.getUniqueId());
                if (hide) for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(this, p);
                reward(p, chosen);
            }
        }, 0L, 1L);
    }

    private void reward(Player p, String mob) {
        PowerCatalog.Power power = catalog.create(mob); ensurePowerItem(p);
        ChatColor c = PowerCatalog.color(power.rarity());
        p.sendTitle(c + "POWER UNLOCKED!", ChatColor.WHITE + PowerCatalog.title(mob) + " - " + power.ability(), 10, 70, 20);
        p.sendMessage(ChatColor.GOLD + "MOB POWER UNLOCKED! " + ChatColor.WHITE + "Mob: " + PowerCatalog.title(mob) + " | Ability: " + power.ability() + " | Rarity: " + c + power.rarity() + ChatColor.WHITE + " | Cooldown: " + power.cooldown() + "s");
        p.playSound(p.getLocation(), power.rarity() == PowerCatalog.Rarity.LEGENDARY ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.getWorld().spawnParticle(power.rarity() == PowerCatalog.Rarity.LEGENDARY ? Particle.TOTEM_OF_UNDYING : Particle.END_ROD, p.getLocation().add(0, 1, 0), power.rarity() == PowerCatalog.Rarity.LEGENDARY ? 80 : 24, .8, 1, .8, .1);
        saveData();
    }
    private void ensurePowerItem(Player p) {
        String mob = powers.get(p.getUniqueId()); if (mob == null) return;
        ItemStack slot = p.getInventory().getItem(0);
        if (catalog.isPowerItem(slot) && mob.equals(slot.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this,"mob_power"), org.bukkit.persistence.PersistentDataType.STRING))) {
            p.getInventory().setItem(0, catalog.item(catalog.create(mob), remaining(p, mob))); return;
        }
        if (slot != null && !slot.getType().isAir()) {
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(slot.clone());
            if (!leftover.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), leftover.values().iterator().next());
        }
        p.getInventory().setItem(0, catalog.item(catalog.create(mob), remaining(p, mob)));
    }
    private long remaining(Player p, String mob) { long expiry = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(mob, 0L); return Math.max(0, (expiry - System.currentTimeMillis() + 999) / 1000); }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !catalog.isPowerItem(e.getItem())) return;
        e.setCancelled(true); activate(e.getPlayer());
    }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (catalog.isPowerItem(e.getItemDrop().getItemStack())) e.setCancelled(true); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) { if (catalog.isPowerItem(e.getMainHandItem()) || catalog.isPowerItem(e.getOffHandItem())) e.setCancelled(true); }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Your Mob Power")) { e.setCancelled(true); return; }
        if (catalog.isPowerItem(e.getCurrentItem()) && (e.getSlot() != 0 || e.getClickedInventory() != p.getInventory())) e.setCancelled(true);
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() == 0) e.setCancelled(true);
    }
    @EventHandler public void onDrag(InventoryDragEvent e) { if (catalog.isPowerItem(e.getOldCursor()) && e.getRawSlots().stream().anyMatch(slot -> slot != 0)) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String mob = powers.get(p.getUniqueId());
        if (("CREEPER".equals(mob) || "MAGMA_CUBE".equals(mob)) && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) e.setDamage(e.getDamage() * .35);
        if ("SLIME".equals(mob) && e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setDamage(0);
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || !e.getPlayer().isOnGround() || e.getFrom().getY() == e.getTo().getY()) return;
        String mob = powers.get(e.getPlayer().getUniqueId());
        if ("SLIME".equals(mob) || "MAGMA_CUBE".equals(mob)) { e.getPlayer().setVelocity(e.getPlayer().getVelocity().setY(1.05)); e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, .8f, 1f); }
    }

    private void activate(Player p) {
        String mob = powers.get(p.getUniqueId()); if (mob == null) return;
        PowerCatalog.Power power = catalog.create(mob); long seconds = remaining(p, mob);
        if (seconds > 0) { p.sendMessage(ChatColor.RED + power.ability() + " is on cooldown for " + seconds + " seconds. " + PowerCatalog.color(power.rarity()) + "Rarity: " + power.rarity()); return; }
        boolean success = applyAbility(p, mob);
        if (!success) { p.sendMessage(ChatColor.RED + "You cannot use this power right now."); return; }
        cooldowns.computeIfAbsent(p.getUniqueId(), ignored -> new HashMap<>()).put(mob, System.currentTimeMillis() + power.cooldown() * 1000L);
        saveData(); ensurePowerItem(p);
        p.sendMessage(PowerCatalog.color(power.rarity()) + power.ability() + " activated! " + ChatColor.GRAY + "Rarity: " + power.rarity() + " | Cooldown: " + power.cooldown() + "s");
    }

    private boolean applyAbility(Player p, String mob) {
        Location at = p.getLocation(); World w = p.getWorld(); Vector dir = at.getDirection().normalize();
        p.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.1f);
        w.spawnParticle(mob.contains("DRAGON") || mob.equals("ENDERMAN") ? Particle.PORTAL : Particle.CRIT, at.add(0, 1, 0), 30, .6, .7, .6, .15);
        if (mob.contains("CREEPER")) { w.createExplosion(p.getLocation(), (float)getConfig().getDouble("explosions.power", 3), false, getConfig().getBoolean("explosions.break-blocks", false), getConfig().getBoolean("explosions.damage-entities", true) ? p : null); return true; }
        if (mob.equals("GHAST") || mob.equals("BLAZE") || mob.equals("BREEZE") || mob.equals("WARDEN") || mob.contains("DRAGON") || mob.equals("WITHER")) {
            Fireball ball = p.launchProjectile(mob.equals("GHAST") || mob.contains("DRAGON") || mob.equals("WITHER") ? LargeFireball.class : SmallFireball.class, dir.multiply(1.5)); ball.setIsIncendiary(false); ball.setYield(0); return true;
        }
        if (mob.equals("ENDERMAN") || mob.equals("ENDERMITE") || mob.equals("VEX") || mob.equals("FOX") || mob.equals("CAT") || mob.equals("OCELOT")) {
            Location target = at.clone().add(dir.multiply(6)); target.setY(w.getHighestBlockYAt(target) + 1); p.teleport(target); p.setVelocity(dir.multiply(1.1)); return true;
        }
        if (mob.equals("PHANTOM") || mob.equals("HAPPY_GHAST") || mob.equals("BEE") || mob.equals("ALLAY") || mob.equals("BAT") || mob.equals("PARROT") || mob.equals("HORSE") || mob.equals("VEX")) {
            int duration = getConfig().getInt("flight.duration-seconds", 8); p.setAllowFlight(true); p.setFlying(true);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) { p.setFlying(false); p.setAllowFlight(false); } }, duration * 20L); return true;
        }
        if (mob.equals("SPIDER") || mob.equals("CAVE_SPIDER")) { p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 2)); p.setVelocity(new Vector(0, .8, 0)); return true; }
        if (mob.equals("TURTLE") || mob.equals("IRON_GOLEM") || mob.equals("PANDA") || mob.equals("ARMADILLO") || mob.equals("POLAR_BEAR")) { p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2)); p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0)); return true; }
        if (mob.equals("WOLF") || mob.equals("RABBIT") || mob.equals("CAMEL") || mob.equals("GOAT") || mob.equals("DOLPHIN") || mob.equals("STRIDER")) { p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2)); p.setVelocity(new Vector(dir.getX() * 1.2, getConfig().getDouble("flight.vertical-strength", .8), dir.getZ() * 1.2)); return true; }
        if (mob.equals("WITCH") || mob.equals("AXOLOTL") || mob.equals("MOOSHROOM")) { p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1)); p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1)); return true; }
        if (mob.equals("SKELETON") || mob.equals("STRAY") || mob.equals("BOGGED") || mob.equals("PILLAGER") || mob.equals("SNOW_GOLEM")) { Arrow arrow = p.launchProjectile(Arrow.class, dir.multiply(2)); if (mob.equals("BOGGED") || mob.equals("STRAY")) arrow.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true); return true; }
        if (mob.equals("SHULKER") || mob.equals("EVOKER") || mob.equals("GUARDIAN") || mob.equals("ELDER_GUARDIAN")) {
            LivingEntity target = w.getNearbyLivingEntities(at, 10, 5, 10).stream().filter(e -> e != p).min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(at))).orElse(null);
            if (target == null) { p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1)); return true; }
            target.damage(6, p); target.addPotionEffect(new PotionEffect(mob.equals("SHULKER") ? PotionEffectType.LEVITATION : PotionEffectType.SLOWNESS, 60, 1)); return true;
        }
        if (mob.equals("DOLPHIN") || mob.equals("SQUID") || mob.equals("SALMON") || mob.equals("COD") || mob.equals("TROPICAL_FISH")) p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 1));
        else { p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 1)); p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0)); }
        p.setVelocity(new Vector(dir.getX() * getConfig().getDouble("flight.horizontal-strength", 1.4), Math.max(.45, dir.getY() * 1.1 + .35), dir.getZ() * 1.4));
        return true;
    }

    private void openMenu(Player p) {
        String mob = powers.get(p.getUniqueId()); if (mob == null) { p.sendMessage(ChatColor.RED + "You do not have a power yet."); return; }
        PowerCatalog.Power power = catalog.create(mob); Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Your Mob Power");
        ItemStack icon = catalog.item(power, remaining(p, mob)); ItemMeta meta = icon.getItemMeta(); List<String> lore = new ArrayList<>(meta.getLore()); lore.add(""); lore.add(ChatColor.GRAY + power.description()); lore.add(ChatColor.GRAY + "Duration: " + getConfig().getInt("abilities.default-duration-seconds", 5) + " seconds"); lore.add(ChatColor.GRAY + "Status: " + (remaining(p,mob) > 0 ? ChatColor.RED + "Cooldown" : ChatColor.GREEN + "Ready")); meta.setLore(lore); icon.setItemMeta(meta); inv.setItem(13, icon); p.openInventory(inv);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("menu")) { if (sender instanceof Player p) openMenu(p); else sender.sendMessage("Players only."); return true; }
        if (!sender.hasPermission("mobpowers.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (a[0].equalsIgnoreCase("reload")) { reloadConfig(); sender.sendMessage(ChatColor.GREEN + "MobPowers config reloaded."); return true; }
        if (a.length < 2) return usage(sender);
        Player target = Bukkit.getPlayerExact(a[1]);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "info" -> { if (target == null) return offline(sender); String mob = powers.get(target.getUniqueId()); sender.sendMessage(mob == null ? "No power" : format(catalog.create(mob), remaining(target,mob))); }
            case "give", "reset" -> { if (target == null) return offline(sender); if (a.length < 3 && a[0].equalsIgnoreCase("give")) return usage(sender); String mob = a[0].equalsIgnoreCase("reset") ? randomMob() : a[2].toUpperCase(Locale.ROOT); if (!catalog.mobTypes().contains(mob)) { sender.sendMessage(ChatColor.RED + "Unknown mob EntityType: " + mob); return true; } powers.put(target.getUniqueId(), mob); cooldowns.remove(target.getUniqueId()); saveData(); reward(target,mob); sender.sendMessage("Assigned " + mob + " to " + target.getName()); }
            case "remove" -> { if (target == null) return offline(sender); powers.remove(target.getUniqueId()); cooldowns.remove(target.getUniqueId()); target.getInventory().setItem(0, null); saveData(); sender.sendMessage("Power removed."); }
            case "roulette" -> { if (target == null) return offline(sender); String mob = randomMob(); powers.put(target.getUniqueId(), mob); saveData(); startRoulette(target,mob); }
            case "setrarity" -> { if (a.length < 3) return usage(sender); try { PowerCatalog.Rarity.valueOf(a[2].toUpperCase(Locale.ROOT)); getConfig().set("mobs." + a[1].toUpperCase(Locale.ROOT) + ".rarity", a[2].toUpperCase(Locale.ROOT)); saveConfig(); sender.sendMessage("Rarity updated."); } catch (IllegalArgumentException ex) { sender.sendMessage("Rarity must be COMMON, UNCOMMON, RARE, EPIC or LEGENDARY."); } }
            case "setcooldown" -> { if (a.length < 3) return usage(sender); try { int cd = Integer.parseInt(a[2]); if (cd < 1) throw new NumberFormatException(); getConfig().set("mobs." + a[1].toUpperCase(Locale.ROOT) + ".cooldown", cd); saveConfig(); sender.sendMessage("Cooldown updated."); } catch (NumberFormatException ex) { sender.sendMessage("Cooldown must be a positive number of seconds."); } }
            default -> { return usage(sender); }
        }
        return true;
    }
    private boolean offline(CommandSender s) { s.sendMessage(ChatColor.RED + "Player must be online."); return true; }
    private boolean usage(CommandSender s) { s.sendMessage(ChatColor.YELLOW + "/mobpowers reload|give <player> <mob>|remove <player>|reset <player>|info <player>|roulette <player>|setrarity <mob> <rarity>|setcooldown <mob> <seconds>"); return true; }
    private String format(PowerCatalog.Power p,long cd) { return "Mob: " + p.mob() + " | Ability: " + p.ability() + " | Rarity: " + p.rarity() + " | Cooldown: " + cd + "s"; }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("menu","reload","give","remove","reset","info","roulette","setrarity","setcooldown").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && List.of("give","remove","reset","info","roulette").contains(args[0].toLowerCase(Locale.ROOT))) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if ((args.length == 2 && List.of("setrarity","setcooldown").contains(args[0].toLowerCase(Locale.ROOT))) || (args.length == 3 && args[0].equalsIgnoreCase("give"))) return catalog.mobTypes().stream().filter(m -> m.toLowerCase(Locale.ROOT).startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("setrarity")) return Arrays.stream(PowerCatalog.Rarity.values()).map(Enum::name).toList();
        return List.of();
    }
}
