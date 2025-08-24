package com.sulphate.chatcolor2.gui.item.impl;

import com.sulphate.chatcolor2.gui.item.ClickableItem;
import com.sulphate.chatcolor2.gui.item.ComplexGuiItem;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import com.sulphate.chatcolor2.utils.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;


import java.util.List;

public class CommandItem extends ComplexGuiItem implements ClickableItem {

    public static String clickToRunMessage;
    private final Player owner;

    public CommandItem(String data, ItemStackTemplate itemTemplate, Player owner) {
        super(data, itemTemplate);
        this.owner = owner; // Initialize the owner

    }

    @Override
    public ItemStack buildItem() {
        ItemStack item = itemTemplate.build(1);

        if (!clickToRunMessage.isEmpty()) {
            List<String> lore = InventoryUtils.getLore(item);

            lore.add("");
            lore.add(clickToRunMessage);

            InventoryUtils.setLore(item, lore);
        }

        return item;
    }

    @Override
    public void click() {
        if (data.startsWith("[player]")) {
            String playerCommand = data.substring("[player]".length()).trim();
            owner.performCommand(playerCommand); // Execute the command as the player
        } else {
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, data); // Execute the command as the console
        }
    }
}
