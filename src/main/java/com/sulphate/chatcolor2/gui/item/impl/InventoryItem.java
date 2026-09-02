package com.sulphate.chatcolor2.gui.item.impl;

import com.sulphate.chatcolor2.gui.Gui;
import com.sulphate.chatcolor2.gui.GuiManager;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import com.sulphate.chatcolor2.gui.item.ClickableItem;
import com.sulphate.chatcolor2.gui.item.ComplexGuiItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryItem extends ComplexGuiItem implements ClickableItem {

    private final Player player;
    private final GuiManager guiManager;

    public InventoryItem(String data, ItemStackTemplate itemTemplate, Player player, GuiManager guiManager) {
        super(data, itemTemplate);

        this.player = player;
        this.guiManager = guiManager;
    }

    @Override
    public ItemStack buildItem() {
        return itemTemplate.build(1);
    }

    @Override
    public void click(Player who) {
        // It is necessary to create the GUI here as otherwise it will result in a stack overflow if two inventories
        // refer to each other (as opposed to when the item is created).
        Gui targetGui = guiManager.createGui(data, player);

        if (targetGui != null) {
            guiManager.openGui(targetGui, player);
        }
    }

}
