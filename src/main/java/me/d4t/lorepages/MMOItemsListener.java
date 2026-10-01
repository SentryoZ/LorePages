package me.d4t.lorepages;

import net.Indyuce.mmoitems.api.event.ItemBuildEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class MMOItemsListener implements Listener {

    private final ItemLoreListener loreListener;

    public MMOItemsListener(ItemLoreListener loreListener) {
        this.loreListener = loreListener;
    }

    @EventHandler
    public void onItemBuild(ItemBuildEvent event) {
        ItemStack item = event.getItemStack();
        if (item == null || item.getType().isAir()) return;

        loreListener.checkAndBake(item);
        event.setItemStack(item);
    }
}
