package com.sulphate.chatcolor2.gui.item.impl;

import com.sulphate.chatcolor2.gui.item.ClickableItem;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import org.bukkit.entity.Player;

public class CloseGUIItem extends SimpleGuiItem implements ClickableItem {

    public CloseGUIItem(ItemStackTemplate itemTemplate) {
        super(itemTemplate);
    }

    @Override
    public void click(Player who) {
        who.closeInventory();
    }

}
