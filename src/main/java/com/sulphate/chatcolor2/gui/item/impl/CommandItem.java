package com.sulphate.chatcolor2.gui.item.impl;

import com.sulphate.chatcolor2.gui.item.ClickableItem;
import com.sulphate.chatcolor2.gui.item.ComplexGuiItem;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import com.sulphate.chatcolor2.utils.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CommandItem extends ComplexGuiItem implements ClickableItem {

    public enum CommandSource {
        PLAYER, CONSOLE
    }

    public static String clickToRunMessage;
    private final CommandSource source;

    public CommandItem(String data, ItemStackTemplate itemTemplate, CommandSource source) {
        super(data, itemTemplate);
        this.source = source;
    }

    @Override
    public ItemStack buildItem() {
        ItemStack item = itemTemplate.build(1);

        // Allow people to remove it if they want to - this is a simple way to do so.
        if (!clickToRunMessage.isEmpty()) {
            List<String> lore = InventoryUtils.getLore(item);

            lore.add("");
            lore.add(clickToRunMessage);

            InventoryUtils.setLore(item, lore);
        }

        return item;
    }

    @Override
    public void click(Player who) {
        switch (source) {
            case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), data);
            case PLAYER -> Bukkit.dispatchCommand(who, data);
        }
    }

}
