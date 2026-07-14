package com.weark.itemgenerator.listener;

import com.weark.itemgenerator.ItemGeneratorPlugin;
import com.weark.itemgenerator.gui.GeneratorGUI;
import com.weark.itemgenerator.model.ItemGenerator;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class GeneratorListener implements Listener {

    private final ItemGeneratorPlugin plugin;

    public GeneratorListener(ItemGeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        ItemGenerator gen = plugin.getGeneratorManager().getGeneratorAt(e.getClickedBlock().getLocation());
        if (gen == null) return;

        e.setCancelled(true);
        Player player = e.getPlayer();

        if (!player.hasPermission("itemgenerator.use")) {
            player.sendMessage(ChatColor.RED + "Bu islemi yapmaya yetkin yok.");
            return;
        }

        if (gen.getStorage() <= 0) {
            player.sendMessage(ChatColor.GRAY + "Cofre su an bos.");
            return;
        }

        GeneratorGUI.open(player, gen);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player player = (Player) e.getPlayer();

        UUID genId = GeneratorGUI.getOpenGeneratorId(player);
        if (genId == null) return;

        ItemGenerator gen = plugin.getGeneratorManager().getAllGenerators().stream()
                .filter(g -> g.getId().equals(genId))
                .findFirst()
                .orElse(null);

        GeneratorGUI.clear(player);
        if (gen == null) return;

        // Oyuncu envanterinden itemleri gercekten aldi (Inventory GUI direkt Player Inventory ile
        // etkilesime girdigi icin - burada kalan miktari sayip storage'i guncelliyoruz).
        int remaining = GeneratorGUI.countRemainingItems(e.getInventory());
        gen.setStorage(remaining);
        plugin.getGeneratorManager().saveGenerator(gen);

        player.sendMessage(ChatColor.GOLD + "Cofre guncellendi. " + ChatColor.WHITE
                + "Kalan depo: " + gen.getStorage() + "/" + gen.getMaxStorage());
    }
}
