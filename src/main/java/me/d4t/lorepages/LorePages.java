package me.d4t.lorepages;

import org.bukkit.plugin.java.JavaPlugin;

public final class LorePages extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Register listeners
        getServer().getPluginManager().registerEvents(new ItemLoreListener(this), this);

        // Register commands
        getCommand("lorepages").setExecutor(new LorePagesCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
