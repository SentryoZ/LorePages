package me.d4t.lorepages;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.lumine.mythic.bukkit.events.MythicMobItemGenerateEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class ItemLoreListener implements Listener {

    private final LorePages plugin;
    private final Gson gson = new Gson();
    private final NamespacedKey pagesKey;
    private final NamespacedKey indexKey;

    public ItemLoreListener(LorePages plugin) {
        this.plugin = plugin;
        this.pagesKey = new NamespacedKey(plugin, "lore_pages");
        this.indexKey = new NamespacedKey(plugin, "lore_page_index");
    }

    @EventHandler
    public void onItemGenerate(MythicMobItemGenerateEvent event) {
        ItemStack itemStack = event.getItemStack();
        checkAndBake(itemStack);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        cleanInventory(event.getInventory());
        cleanInventory(event.getPlayer().getInventory());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item != null && !item.getType().isAir()) {
            checkAndBake(item);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        checkAndBake(item);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        checkAndBake(item);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        ItemStack oldCursor = event.getOldCursor();
        if (!oldCursor.getType().isAir()) {
            checkAndBake(oldCursor);
        }
        ItemStack newCursor = event.getCursor();
        if (newCursor != null && !newCursor.getType().isAir()) {
            checkAndBake(newCursor);
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (item != null && !item.getType().isAir()) {
                checkAndBake(item);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Clean up raw lore if present on clicked or cursor items
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (currentItem != null && !currentItem.getType().isAir()) {
            checkAndBake(currentItem);
        }
        if (!cursorItem.getType().isAir()) {
            checkAndBake(cursorItem);
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;

            boolean isNavigable = false;
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.getPersistentDataContainer().has(pagesKey, PersistentDataType.STRING)) {
                    isNavigable = true;
                } else {
                    List<Component> lore = meta.lore();
                    if (lore != null) {
                        for (Component comp : lore) {
                            String plain = PlainTextComponentSerializer.plainText().serialize(comp);
                            if (plain.contains("---page---")) {
                                isNavigable = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (isNavigable) {
                event.setCancelled(true); // Prevent swap to offhand

                Player player = (Player) event.getWhoClicked();
                Inventory clickedInv = event.getClickedInventory();
                int slot = event.getSlot();

                // Run 1 tick later to let the click action finish and then apply the page change
                SchedulerUtil.runOnEntityNextTick(plugin, player, () -> {
                    if (clickedInv == null) return;
                    ItemStack targetItem = clickedInv.getItem(slot);
                    if (targetItem == null || targetItem.getType().isAir()) return;

                    // Ensure it is baked
                    checkAndBake(targetItem);

                    ItemMeta meta = targetItem.getItemMeta();
                    if (meta == null) return;

                    if (meta.getPersistentDataContainer().has(pagesKey, PersistentDataType.STRING)) {
                        String json = meta.getPersistentDataContainer().get(pagesKey, PersistentDataType.STRING);
                        List<List<String>> pages = gson.fromJson(json, new TypeToken<List<List<String>>>(){}.getType());

                        if (pages != null && pages.size() > 1) {
                            int currentIndex = meta.getPersistentDataContainer().getOrDefault(indexKey, PersistentDataType.INTEGER, 0);
                            int nextIndex = (currentIndex + 1) % pages.size();

                            meta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, nextIndex);
                            updateLoreDisplay(meta, pages, nextIndex);
                            targetItem.setItemMeta(meta);

                            player.updateInventory();

                            if (plugin.getConfig().getBoolean("lore-navigation.enable-sound", true)) {
                                String soundStr = plugin.getConfig().getString("lore-navigation.sound", "UI_BUTTON_CLICK");
                                double volume = plugin.getConfig().getDouble("lore-navigation.sound-volume", 1.0);
                                double pitch = plugin.getConfig().getDouble("lore-navigation.sound-pitch", 1.5);
                                try {
                                    Sound sound = Sound.valueOf(soundStr.toUpperCase());
                                    player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
                                } catch (Exception e) {
                                    // Ignore invalid sound configuration
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    private void cleanInventory(Inventory inventory) {
        if (inventory == null) return;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                checkAndBake(item);
            }
        }
    }

    public void checkAndBake(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) return;

        // 1. Process and clean up any {itemData:...} conditional lines
        List<Component> cleanedLore = new ArrayList<>();
        boolean modified = false;

        for (Component comp : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(comp);
            if (plain.startsWith("{itemData:")) {
                int closeIndex = plain.indexOf('}');
                if (closeIndex != -1) {
                    String tagName = plain.substring(10, closeIndex);
                    boolean hasTag = false;

                    if (tagName.equals("*")) {
                        // Check if the item has any custom keys (excluding system keys)
                        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                            String k = key.getKey().toLowerCase();
                            if (!k.equals("id") && !k.equals("type") && !k.equals("version") && 
                                !k.equals("lore_pages") && !k.equals("lore_page_index")) {
                                
                                String val = null;
                                try {
                                    val = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                                } catch (Exception ignored) {}
                                if (val == null) {
                                    try {
                                        Integer i = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
                                        if (i != null) val = String.valueOf(i);
                                    } catch (Exception ignored) {}
                                }
                                if (val == null) {
                                    try {
                                        Double d = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
                                        if (d != null) val = String.valueOf(d);
                                    } catch (Exception ignored) {}
                                }
                                if (val != null && !val.trim().isEmpty()) {
                                    hasTag = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        // Specific tag check
                        NamespacedKey foundKey = null;
                        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                            if (key.getKey().equalsIgnoreCase(tagName)) {
                                foundKey = key;
                                break;
                            }
                        }

                        if (foundKey != null) {
                            String val = null;
                            try {
                                val = meta.getPersistentDataContainer().get(foundKey, PersistentDataType.STRING);
                            } catch (Exception ignored) {}
                            if (val == null) {
                                try {
                                    Integer i = meta.getPersistentDataContainer().get(foundKey, PersistentDataType.INTEGER);
                                    if (i != null) val = String.valueOf(i);
                                } catch (Exception ignored) {}
                            }
                            if (val == null) {
                                try {
                                    Double d = meta.getPersistentDataContainer().get(foundKey, PersistentDataType.DOUBLE);
                                    if (d != null) val = String.valueOf(d);
                                } catch (Exception ignored) {}
                            }
                            if (val != null && !val.trim().isEmpty()) {
                                hasTag = true;
                            }
                        }
                    }

                    if (hasTag) {
                        // Keep line and strip prefix
                        Component cleanedComp = comp.replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                                .matchLiteral("{itemData:" + tagName + "}")
                                .replacement("")
                                .build());
                        cleanedLore.add(cleanedComp);
                    }
                    modified = true;
                    continue;
                }
            }
            cleanedLore.add(comp);
        }

        if (modified) {
            lore = cleanedLore;
            meta.lore(lore);
            item.setItemMeta(meta); // Save changes back to item
        }

        // 2. Process page splitting
        boolean hasSeparator = false;
        for (Component comp : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(comp);
            if (plain.contains("---page---")) {
                hasSeparator = true;
                break;
            }
        }
        if (!hasSeparator) return;

        List<List<Component>> pages = new ArrayList<>();
        List<Component> currentPage = new ArrayList<>();
        for (Component comp : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(comp);
            if (plain.contains("---page---")) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            } else {
                currentPage.add(comp);
            }
        }
        pages.add(currentPage);

        List<List<String>> serializedPages = new ArrayList<>();
        for (List<Component> page : pages) {
            List<String> serializedPage = new ArrayList<>();
            for (Component comp : page) {
                serializedPage.add(GsonComponentSerializer.gson().serialize(comp));
            }
            serializedPages.add(serializedPage);
        }

        int targetIndex = 0;
        if (meta.getPersistentDataContainer().has(indexKey, PersistentDataType.INTEGER)) {
            Integer existing = meta.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER);
            if (existing != null) {
                targetIndex = existing % pages.size();
            }
        }

        meta.getPersistentDataContainer().set(pagesKey, PersistentDataType.STRING, gson.toJson(serializedPages));
        meta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, targetIndex);

        updateLoreDisplay(meta, serializedPages, targetIndex);
        item.setItemMeta(meta);
    }

    private void updateLoreDisplay(ItemMeta meta, List<List<String>> pages, int pageIndex) {
        List<Component> displayLore = new ArrayList<>();
        for (String json : pages.get(pageIndex)) {
            displayLore.add(GsonComponentSerializer.gson().deserialize(json));
        }

        if (pages.size() > 1 && plugin.getConfig().getBoolean("lore-navigation.enable-footer", true)) {
            String footerFormat = plugin.getConfig().getString("lore-navigation.footer-format", "&8[ {dots} &8]");
            String activeDot = plugin.getConfig().getString("lore-navigation.active-dot", "&a●");
            String inactiveDot = plugin.getConfig().getString("lore-navigation.inactive-dot", "&7○");

            StringBuilder dotsBuilder = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) {
                    dotsBuilder.append(" ");
                }
                if (i == pageIndex) {
                    dotsBuilder.append(activeDot);
                } else {
                    dotsBuilder.append(inactiveDot);
                }
            }

            String footer = footerFormat
                    .replace("{dots}", dotsBuilder.toString())
                    .replace("{current}", String.valueOf(pageIndex + 1))
                    .replace("{total}", String.valueOf(pages.size()));

            // Force the footer to be non-italic
            Component footerComponent = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(footer)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);

            // Center the footer relative to the longest lore line if enabled
            if (plugin.getConfig().getBoolean("lore-navigation.align-footer-middle", true)) {
                int maxPixelWidth = 0;
                for (Component line : displayLore) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(line);
                    int width = getStringWidth(plain);
                    if (width > maxPixelWidth) {
                        maxPixelWidth = width;
                    }
                }

                String plainFooter = PlainTextComponentSerializer.plainText().serialize(footerComponent);
                int footerPixelWidth = getStringWidth(plainFooter);

                if (maxPixelWidth > footerPixelWidth) {
                    int paddingWidth = (maxPixelWidth - footerPixelWidth) / 2;
                    int spacesCount = paddingWidth / 4; // space is 4 pixels wide in default Minecraft font
                    if (spacesCount > 0) {
                        Component spaces = Component.text(" ".repeat(spacesCount));
                        footerComponent = spaces.append(footerComponent);
                    }
                }
            }

            displayLore.add(footerComponent);
        }

        meta.lore(displayLore);
    }

    private int getStringWidth(String text) {
        int width = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '<') {
                int closeIndex = text.indexOf('>', i);
                if (closeIndex != -1) {
                    String tag = text.substring(i + 1, closeIndex).toLowerCase();
                    if (tag.startsWith("sprite:") || tag.startsWith("image:")) {
                        width += 12; // Standard icon width
                    }
                    i = closeIndex + 1;
                    continue;
                }
            }
            width += getCharWidth(c);
            i++;
        }
        return width;
    }

    private int getCharWidth(char c) {
        // Unicode Private Use Area (PUA) check - these are standard ranges for custom font/sprite characters
        if ((c >= '\uE000' && c <= '\uF8FF') || Character.getType(c) == Character.PRIVATE_USE) {
            return 12; // Custom sprite icons are typically square/wider
        }
        if (c == ' ' || c == '_') return 4;
        if (c == 'i' || c == '!' || c == '.' || c == ',' || c == ':' || c == ';') return 2;
        if (c == 'l' || c == '\'') return 3;
        if (c == 't' || c == 'I' || c == '[' || c == ']') return 4;
        if (c == 'f' || c == 'k' || c == '\"' || c == '<' || c == '>') return 5;
        return 6;
    }
}
