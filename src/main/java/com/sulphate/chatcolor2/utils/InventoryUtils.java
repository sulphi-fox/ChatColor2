package com.sulphate.chatcolor2.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class InventoryUtils {

    public static String formatMaterialName(Material material) {
        String name = material.name();
        String[] parts = name.split("_");

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            builder.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ENGLISH));

            if (i < parts.length - 1) {
                builder.append(' ');
            }
        }

        return builder.toString();
    }

    public static String getDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() ? meta.getDisplayName() : GeneralUtils.colourise("&f" + formatMaterialName(item.getType()));
    }

    public static List<String> getLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta.hasLore() ? meta.getLore() : new ArrayList<>();
    }

    public static void setDisplayName(ItemStack item, String displayName) {
        ItemMeta meta = item.getItemMeta();

        // Force non-italic by prefixing reset
        meta.setDisplayName(GeneralUtils.colourise("§r" + displayName));
        item.setItemMeta(meta);
    }

    public static void setLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();

        List<String> colouredLore = lore.stream()
                .map(line -> GeneralUtils.colourise("§r" + line)) // reset italics each line
                .collect(Collectors.toList());

        meta.setLore(colouredLore);
        item.setItemMeta(meta);
    }

    public static void setEnchantments(ItemStack item, Map<Enchantment, Integer> enchantments) {
        ItemMeta meta = item.getItemMeta();

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }

        item.setItemMeta(meta);
    }

    public static void addFakeEnchantment(ItemStack item) {
        setEnchantments(item, Collections.singletonMap(Enchantment.FIRE_ASPECT, 1));

        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
    }

    public static <T, Z> void addPersistentItemData(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> dataType, Z data) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();

        dataContainer.set(key, dataType, data);
        item.setItemMeta(meta);
    }

    public static <T, Z> boolean hasPersistentItemData(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> dataType, Z data) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return false;
        }

        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();

        if (dataContainer.has(key, dataType)) {
            Z value = dataContainer.get(key, dataType);
            return value.equals(data);
        }

        return false;
    }

    public static void addToPlayerInventoryOrDrop(Player player, ItemStack itemStack, String droppedMessage) {
        Inventory inventory = player.getInventory();

        if (inventory.firstEmpty() == -1) {
            player.getLocation().getWorld().dropItemNaturally(player.getLocation(), itemStack);
            player.sendMessage(droppedMessage);
        }
        else {
            inventory.addItem(itemStack);
        }
    }

}
