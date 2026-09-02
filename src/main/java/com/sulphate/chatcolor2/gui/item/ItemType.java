package com.sulphate.chatcolor2.gui.item;

public enum ItemType {
    COLOUR,
    MODIFIER,
    INVENTORY,
    FILLER,
    COMMAND,
    CLOSE_GUI;

    public static ItemType getTypeFromName(String name) {
        if (name.equals("COLOR")) {
            name = "COLOUR";
        }

        return valueOf(name);
    }
}
