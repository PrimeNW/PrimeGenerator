package com.weark.itemgenerator.listener;

import com.weark.itemgenerator.ItemGeneratorPlugin;
import com.weark.itemgenerator.model.ItemGenerator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GeneratorCommand implements CommandExecutor {

    private final ItemGeneratorPlugin plugin;

    public GeneratorCommand(ItemGeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Bu komut sadece oyuncular tarafindan kullanilabilir.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("itemgenerator.admin")) {
            player.sendMessage(ChatColor.RED + "Bu islemi yapmaya yetkin yok.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Kullanim: /itemgen <create|remove|reload> [seviye]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create": {
                Block target = player.getTargetBlock((java.util.Set<Material>) null, 6);
                if (target == null || target.getType() == Material.AIR) {
                    player.sendMessage(ChatColor.RED + "Baktigin yerde gecerli bir blok yok.");
                    return true;
                }

                int level = 1;
                if (args.length >= 2) {
                    try {
                        level = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(ChatColor.RED + "Gecersiz seviye.");
                        return true;
                    }
                }

                if (!plugin.getGeneratorManager().getLevelConfig().hasLevel(level)) {
                    player.sendMessage(ChatColor.RED + "Bu seviye config.yml icinde tanimli degil.");
                    return true;
                }

                Material material = player.getItemInHand() != null
                        && player.getItemInHand().getType() != Material.AIR
                        ? player.getItemInHand().getType()
                        : Material.valueOf(plugin.getConfig().getString("default-material", "DIAMOND"));

                if (plugin.getGeneratorManager().getGeneratorAt(target.getLocation()) != null) {
                    player.sendMessage(ChatColor.RED + "Bu blokta zaten bir generator var.");
                    return true;
                }

                ItemGenerator gen = plugin.getGeneratorManager().createGenerator(
                        target.getLocation(), player.getUniqueId(), player.getName(), level, material);

                player.sendMessage(ChatColor.GREEN + "Generator olusturuldu! Seviye: " + gen.getLevel()
                        + " | Item: " + gen.getProducedMaterial().name());
                return true;
            }
            case "remove": {
                Block target = player.getTargetBlock((java.util.Set<Material>) null, 6);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Baktigin yerde gecerli bir blok yok.");
                    return true;
                }
                ItemGenerator gen = plugin.getGeneratorManager().getGeneratorAt(target.getLocation());
                if (gen == null) {
                    player.sendMessage(ChatColor.RED + "Bu blokta generator yok.");
                    return true;
                }
                plugin.getGeneratorManager().removeGenerator(gen);
                player.sendMessage(ChatColor.GREEN + "Generator kaldirildi.");
                return true;
            }
            case "reload": {
                plugin.getGeneratorManager().reloadConfigs();
                player.sendMessage(ChatColor.GREEN + "Config yeniden yuklendi.");
                return true;
            }
            default:
                player.sendMessage(ChatColor.YELLOW + "Kullanim: /itemgen <create|remove|reload> [seviye]");
                return true;
        }
    }
}
