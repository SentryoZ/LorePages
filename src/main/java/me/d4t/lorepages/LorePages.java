package me.d4t.lorepages;

import org.bukkit.plugin.java.JavaPlugin;

public final class LorePages extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Register listeners
        ItemLoreListener loreListener = new ItemLoreListener(this);
        getServer().getPluginManager().registerEvents(loreListener, this);

        // Only load the MMOItems listener when MMOItems is installed, since it
        // references MMOItems API classes that must not be resolved otherwise.
        if (getServer().getPluginManager().getPlugin("MMOItems") != null) {
            try {
                getServer().getPluginManager().registerEvents(new MMOItemsListener(loreListener), this);
            } catch (Throwable throwable) {
                getLogger().warning("Could not hook into MMOItems: " + throwable.getMessage());
            }
        }

        // Register commands
        getCommand("lorepages").setExecutor(new LorePagesCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
