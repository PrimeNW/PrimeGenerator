package com.weark.itemgenerator;

import com.weark.itemgenerator.listener.GeneratorCommand;
import com.weark.itemgenerator.listener.GeneratorListener;
import com.weark.itemgenerator.manager.GeneratorManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemGeneratorPlugin extends JavaPlugin {

    private GeneratorManager generatorManager;

    @Override
    public void onEnable() {
        generatorManager = new GeneratorManager(this);
        generatorManager.init();

        getServer().getPluginManager().registerEvents(new GeneratorListener(this), this);
        getCommand("itemgen").setExecutor(new GeneratorCommand(this));

        getLogger().info("ItemGenerator etkinlestirildi.");
    }

    @Override
    public void onDisable() {
        if (generatorManager != null) {
            generatorManager.shutdown();
        }
        getLogger().info("ItemGenerator devre disi birakildi.");
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }
}
