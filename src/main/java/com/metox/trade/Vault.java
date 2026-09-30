package com.metox.trade;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Teslim edilemeyen esyalarin kuyrugu.
 *
 * Bir oyuncu takas sirasinda cikarsa esyalari buraya yazilir ve
 * bir dahaki girisinde teslim edilir. Boylece hicbir sey kaybolmaz.
 */
final class Vault {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    Vault(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Esyalari oyuncunun teslim kuyruguna ekler. */
    @SuppressWarnings("unchecked")
    void store(UUID id, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;

        List<ItemStack> all = new ArrayList<ItemStack>();
        List<?> existing = data.getList("pending." + id);
        if (existing != null) {
            for (Object o : existing) if (o instanceof ItemStack) all.add((ItemStack) o);
        }
        for (ItemStack it : items) {
            if (it != null && it.getType() != org.bukkit.Material.AIR) all.add(it);
        }
        if (all.isEmpty()) return;

        data.set("pending." + id, all);
        save();
    }

    /** Girişte bekleyen esyalari teslim eder. */
    boolean deliver(Player p) {
        List<?> raw = data.getList("pending." + p.getUniqueId());
        if (raw == null || raw.isEmpty()) return false;

        List<ItemStack> items = new ArrayList<ItemStack>();
        for (Object o : raw) if (o instanceof ItemStack) items.add((ItemStack) o);

        for (ItemStack it : items) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack rem : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
            }
        }
        data.set("pending." + p.getUniqueId(), null);
        save();
        return !items.isEmpty();
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("pending.yml kaydedilemedi: " + ex.getMessage());
        }
    }
}
