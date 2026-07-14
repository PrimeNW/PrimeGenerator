package com.weark.itemgenerator.manager;

import com.weark.itemgenerator.ItemGeneratorPlugin;
import com.weark.itemgenerator.model.ItemGenerator;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GeneratorManager {

    private final ItemGeneratorPlugin plugin;
    private final LevelConfig levelConfig = new LevelConfig();

    private final Map<UUID, ItemGenerator> generators = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public GeneratorManager(ItemGeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.saveDefaultConfig();
        levelConfig.load(plugin.getConfig());

        dataFile = new File(plugin.getDataFolder(), "generators.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("generators.yml olusturulamadi: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        loadAll();
        startProductionTask();
        startHologramTask();
    }

    public LevelConfig getLevelConfig() {
        return levelConfig;
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        levelConfig.load(plugin.getConfig());
    }

    // ----------------- CRUD -----------------

    public ItemGenerator createGenerator(Location location, UUID owner, String ownerName,
                                          int level, Material material) {
        LevelConfig.LevelData data = levelConfig.get(level);
        UUID id = UUID.randomUUID();

        ItemGenerator gen = new ItemGenerator(
                id, location, owner, ownerName, level, material,
                data.maxStorage, data.produceAmount, data.intervalSeconds
        );

        generators.put(id, gen);
        spawnHologram(gen);
        saveGenerator(gen);
        return gen;
    }

    public void removeGenerator(ItemGenerator gen) {
        removeHologram(gen);
        generators.remove(gen.getId());
        dataConfig.set(gen.getId().toString(), null);
        saveToDisk();
    }

    public ItemGenerator getGeneratorAt(Location loc) {
        for (ItemGenerator gen : generators.values()) {
            Location genLoc = gen.getLocation();
            if (genLoc.getWorld() == null || loc.getWorld() == null) continue;
            if (!genLoc.getWorld().equals(loc.getWorld())) continue;
            if (genLoc.getBlockX() == loc.getBlockX()
                    && genLoc.getBlockY() == loc.getBlockY()
                    && genLoc.getBlockZ() == loc.getBlockZ()) {
                return gen;
            }
        }
        return null;
    }

    public Collection<ItemGenerator> getAllGenerators() {
        return generators.values();
    }

    public List<ItemGenerator> getGeneratorsOf(UUID owner) {
        List<ItemGenerator> result = new ArrayList<>();
        for (ItemGenerator gen : generators.values()) {
            if (gen.getOwner().equals(owner)) result.add(gen);
        }
        return result;
    }

    // ----------------- Uretim Tick -----------------

    private void startProductionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (ItemGenerator gen : generators.values()) {
                    tickGenerator(gen, now);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // her saniye
    }

    private void tickGenerator(ItemGenerator gen, long now) {
        // Combustivel kontrolu (fuel varsa tuketilir, yoksa uretim durur)
        if (gen.getFuelSeconds() > 0) {
            gen.setFuelSeconds(gen.getFuelSeconds() - 1);
        } else {
            // Fuel sistemi aktif kullanilmiyorsa (0 = sinirsiz) bu kontrolu kaldirabilirsiniz.
            // Su an fuel = 0 iken uretim durmasin diye asagidaki satiri yorumda birakiyoruz:
            // return;
        }

        if (gen.isFull()) return;
        if (now < gen.getNextProductionTime()) return;

        int amount = gen.getProduceAmount();
        if (gen.isBoostActive()) {
            amount = (int) Math.ceil(amount * gen.getBoostMultiplier());
        }

        gen.addStorage(amount);

        double effectiveInterval = gen.getIntervalSeconds();
        if (gen.isBoostActive()) {
            effectiveInterval = effectiveInterval / gen.getBoostMultiplier();
        }
        gen.setNextProductionTime(now + (long) (effectiveInterval * 1000L));
    }

    // ----------------- Hologram -----------------

    private void startHologramTask() {
        int interval = plugin.getConfig().getInt("hologram-update-interval", 20);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ItemGenerator gen : generators.values()) {
                    updateHologram(gen);
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void spawnHologram(ItemGenerator gen) {
        World world = gen.getLocation().getWorld();
        if (world == null) return;

        Location base = gen.getLocation().clone().add(0.5, 1.5, 0.5);
        String[] lines = buildHologramLines(gen);

        ArmorStand[] stands = new ArmorStand[lines.length];
        for (int i = 0; i < lines.length; i++) {
            Location lineLoc = base.clone().add(0, (lines.length - i) * 0.25, 0);
            ArmorStand stand = (ArmorStand) world.spawnEntity(lineLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(lines[i]);
            stand.setMarker(true);
            stand.setSmall(true);
            stands[i] = stand;
        }
        gen.setHologramLines(stands);
    }

    private void updateHologram(ItemGenerator gen) {
        ArmorStand[] stands = gen.getHologramLines();
        String[] lines = buildHologramLines(gen);

        if (stands == null || stands.length != lines.length) {
            removeHologram(gen);
            spawnHologram(gen);
            return;
        }

        for (int i = 0; i < stands.length; i++) {
            if (stands[i] == null || stands[i].isDead()) {
                removeHologram(gen);
                spawnHologram(gen);
                return;
            }
            stands[i].setCustomName(lines[i]);
        }
    }

    private void removeHologram(ItemGenerator gen) {
        ArmorStand[] stands = gen.getHologramLines();
        if (stands == null) return;
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        gen.setHologramLines(null);
    }

    private String[] buildHologramLines(ItemGenerator gen) {
        long remainingMs = gen.getNextProductionTime() - System.currentTimeMillis();
        if (remainingMs < 0) remainingMs = 0;
        String timeLeft = formatDuration(remainingMs / 1000);

        int filled = (int) (((double) gen.getStorage() / gen.getMaxStorage()) * 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? ChatColor.GREEN : ChatColor.RED).append("■");
        }

        String boostText = gen.isBoostActive()
                ? formatDuration((gen.getBoostExpireTime() - System.currentTimeMillis()) / 1000)
                : "Yok";

        String fuelText = gen.getFuelSeconds() > 0
                ? formatDuration(gen.getFuelSeconds())
                : "0s";

        return new String[]{
                ChatColor.WHITE + gen.getOwnerName(),
                ChatColor.GOLD + "Farm de Itens NV." + gen.getLevel(),
                bar.toString() + ChatColor.GRAY + " - " + timeLeft,
                ChatColor.LIGHT_PURPLE + "Impulso: " + ChatColor.WHITE + boostText,
                ChatColor.GRAY + "Combustivel: " + ChatColor.WHITE + fuelText,
                ChatColor.GOLD + "Cofre: " + ChatColor.WHITE + gen.getStorage() + "/" + gen.getMaxStorage(),
                ChatColor.YELLOW + "" + ChatColor.BOLD + "CLIQUE PARA ABRIR"
        };
    }

    private String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
    }

    // ----------------- Persistence -----------------

    public void saveGenerator(ItemGenerator gen) {
        String path = gen.getId().toString();
        dataConfig.set(path + ".world", gen.getLocation().getWorld().getName());
        dataConfig.set(path + ".x", gen.getLocation().getBlockX());
        dataConfig.set(path + ".y", gen.getLocation().getBlockY());
        dataConfig.set(path + ".z", gen.getLocation().getBlockZ());
        dataConfig.set(path + ".owner", gen.getOwner().toString());
        dataConfig.set(path + ".owner-name", gen.getOwnerName());
        dataConfig.set(path + ".level", gen.getLevel());
        dataConfig.set(path + ".material", gen.getProducedMaterial().name());
        dataConfig.set(path + ".storage", gen.getStorage());
        dataConfig.set(path + ".max-storage", gen.getMaxStorage());
        dataConfig.set(path + ".produce-amount", gen.getProduceAmount());
        dataConfig.set(path + ".interval-seconds", gen.getIntervalSeconds());
        dataConfig.set(path + ".fuel-seconds", gen.getFuelSeconds());
        dataConfig.set(path + ".boost-expire", gen.getBoostExpireTime());
        saveToDisk();
    }

    public void saveAll() {
        for (ItemGenerator gen : generators.values()) {
            saveGenerator(gen);
        }
    }

    private void saveToDisk() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("generators.yml kaydedilemedi: " + e.getMessage());
        }
    }

    private void loadAll() {
        generators.clear();
        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String worldName = dataConfig.getString(key + ".world");
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) continue;

                int x = dataConfig.getInt(key + ".x");
                int y = dataConfig.getInt(key + ".y");
                int z = dataConfig.getInt(key + ".z");
                Location loc = new Location(world, x, y, z);

                UUID owner = UUID.fromString(dataConfig.getString(key + ".owner"));
                String ownerName = dataConfig.getString(key + ".owner-name", "Unknown");
                int level = dataConfig.getInt(key + ".level", 1);
                Material material = Material.valueOf(dataConfig.getString(key + ".material", "DIAMOND"));
                int maxStorage = dataConfig.getInt(key + ".max-storage", 128);
                int produceAmount = dataConfig.getInt(key + ".produce-amount", 1);
                int interval = dataConfig.getInt(key + ".interval-seconds", 5);

                ItemGenerator gen = new ItemGenerator(id, loc, owner, ownerName, level, material,
                        maxStorage, produceAmount, interval);
                gen.setStorage(dataConfig.getInt(key + ".storage", 0));
                gen.setFuelSeconds(dataConfig.getInt(key + ".fuel-seconds", 0));
                gen.setBoostExpireTime(dataConfig.getLong(key + ".boost-expire", 0L));

                generators.put(id, gen);
                spawnHologram(gen);
            } catch (Exception e) {
                plugin.getLogger().warning("Generator yuklenemedi (" + key + "): " + e.getMessage());
            }
        }
        plugin.getLogger().info(generators.size() + " generator yuklendi.");
    }

    public void shutdown() {
        saveAll();
        for (ItemGenerator gen : generators.values()) {
            removeHologram(gen);
        }
    }
}
