package me.d4t.lorepages;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LorePagesCommand implements CommandExecutor {

    private final LorePages plugin;

    public LorePagesCommand(LorePages plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lorepages.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "[LorePages] Configuration reloaded successfully!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== LorePages Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/lorepages reload " + ChatColor.WHITE + "- Reloads the configuration.");
        return true;
    }
}
