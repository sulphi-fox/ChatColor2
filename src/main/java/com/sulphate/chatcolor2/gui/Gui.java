package com.sulphate.chatcolor2.gui;

import com.sulphate.chatcolor2.data.PlayerData;
import com.sulphate.chatcolor2.exception.InvalidGuiException;
import com.sulphate.chatcolor2.exception.InvalidMaterialException;
import com.sulphate.chatcolor2.gui.item.impl.*;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.gui.item.*;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.stream.Collectors;

public class Gui {

    private static final List<String> REQUIRED_GUI_KEYS = Arrays.asList(
            "title",
            "size",
            "items"
    );

    private static ItemStackTemplate fillerItemTemplate = new ItemStackTemplate(
            Material.GRAY_STAINED_GLASS_PANE,
            "&f",
            null,
            null
    );
    private static ItemStackTemplate noPermissionItemTemplate = null;
    private static Sound selectSound = null;
    private static Sound errorSound = null;

    private final GuiManager guiManager;
    private final GeneralUtils generalUtils;
    private final CustomColoursManager customColoursManager;
    private final Messages M;

    private final Map<Integer, GuiItem> items;
    private final String name;
    private final String title;
    private final int size;
    private final boolean fillEmptySlots;
    private final InventoryType inventoryType;
    private final Player owner;
    private final PlayerData playerData;

    public Gui(String name, ConfigurationSection section, Player owner, PlayerData playerData, GuiManager guiManager, GeneralUtils generalUtils, CustomColoursManager customColoursManager, Messages M) {
        if (section == null) {
            throw new InvalidGuiException(String.format(Messages.INVALID_GUI_ERROR, name));
        }

        for (String requiredKey : REQUIRED_GUI_KEYS) {
            if (!section.contains(requiredKey)) {
                throw new InvalidGuiException(M.PREFIX + String.format(Messages.MISSING_REQUIRED_KEY, requiredKey, name));
            }
        }

        this.guiManager = guiManager;
        this.generalUtils = generalUtils;
        this.customColoursManager = customColoursManager;
        this.M = M;
        this.name = name;
        this.owner = owner;
        this.playerData = playerData;

        if (section.contains("fill-empty-slots")) {
            fillEmptySlots = section.getBoolean("fill-empty-slots");
        }
        else {
            fillEmptySlots = false;
        }

        InventoryType inventoryType = InventoryType.STATIC;

        if (section.contains("inventory-type")) {
            try {
                inventoryType = InventoryType.valueOf(section.getString("inventory-type"));
            }
            catch (IllegalArgumentException ignored) {}
        }

        this.inventoryType = inventoryType;

        title = GeneralUtils.colourise(parsePrefixedColouredString(section.getString("title")));
        size = section.getInt("size");
        items = parseItems(section.getConfigurationSection("items"), owner, playerData);
    }

    private static class ParseResult {

        final GuiItem item;
        final int slot;
        final boolean shouldPrintHexWarning;

        ParseResult(GuiItem item, int slot, boolean shouldPrintHexWarning) {
            this.item = item;
            this.slot = slot;
            this.shouldPrintHexWarning = shouldPrintHexWarning;
        }

    }

    private Map<Integer, GuiItem> parseItems(ConfigurationSection section, Player player, PlayerData playerData) {
        if (section == null) {
            return new HashMap<>();
        }

        Map<Integer, GuiItem> items = new HashMap<>();
        Set<String> itemKeys = section.getKeys(false);
        List<GuiItem> dynamicItems = new ArrayList<>();
        boolean sendLegacyHexWarning = false;

        for (String itemKey : itemKeys) {
            ParseResult result;

            try {
                result = parseSingleItem(itemKey, section);
            }
            catch (InvalidMaterialException | InvalidGuiException ex) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + String.format(Messages.INVALID_GUI_ITEM, ex.getMessage()));
                continue;
            }

            sendLegacyHexWarning = result.shouldPrintHexWarning;
            GuiItem item = result.item;

            if (item == null) {
                continue;
            }

            if (item instanceof PermissibleItem) {
                PermissibleItem permissible = (PermissibleItem) item;
                permissible.checkPermission(player);

                // If permissible, the no-permissions template is null, and the player has no permission, skip the item.
                // Or, if it's a dynamic inventory and no permission, skip it as well.
                if ((noPermissionItemTemplate == null || inventoryType.equals(InventoryType.DYNAMIC))  && !permissible.hasPermission()) {
                    continue;
                }
            }

            if (inventoryType.equals(InventoryType.DYNAMIC) && !(item instanceof InventoryItem || item instanceof SimpleGuiItem)) {
                dynamicItems.add(item);
            }
            else {
                items.put(result.slot, item);
            }
        }

        if (inventoryType.equals(InventoryType.DYNAMIC)) {
            int itemsAdded = 0;

            for (int i = 0; i < size && itemsAdded < dynamicItems.size(); i++) {
                if (!items.containsKey(i)) {
                    items.put(i, dynamicItems.get(itemsAdded++));
                }
            }

            if (itemsAdded < dynamicItems.size()) {
                GeneralUtils.sendConsoleMessage(String.format(M.PREFIX + String.format(Messages.DYNAMIC_INVENTORY_OVERFLOW, name, (dynamicItems.size() - itemsAdded))));
            }
        }

        if (fillEmptySlots) {
            for (int i = 0; i < size; i++) {
                if (!items.containsKey(i)) {
                    items.put(i, new SimpleGuiItem(fillerItemTemplate));
                }
            }
        }

        if (sendLegacyHexWarning) {
            GeneralUtils.sendConsoleMessage(M.PREFIX + Messages.HEX_IN_GUI_WARNING);
        }

        return items;
    }

    private ParseResult parseSingleItem(String itemKey, ConfigurationSection section) {
        int slot;

        try {
            slot = Integer.parseInt(itemKey);
        }
        catch (NumberFormatException ex) {
            throw new InvalidGuiException(String.format(Messages.INVALID_ITEM_KEY, itemKey, name, "must be a number"));
        }

        if (slot < 0 || slot > size - 1) {
            throw new InvalidGuiException(String.format(Messages.INVALID_ITEM_KEY, itemKey, name, "must be between 0 and %d" + size));
        }

        ConfigurationSection itemSection = section.getConfigurationSection(itemKey);

        if (itemSection == null) {
            throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "should be a config section"));
        }

        if (!itemSection.contains("type")) {
            throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'type' config value"));
        }

        String typeString = itemSection.getString("type");
        ItemType type;

        try {
            type = ItemType.getTypeFromName(typeString);
        }
        catch (IllegalArgumentException ex) {
            throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "invalid item type " + typeString));
        }

        GuiItem item;

        if (type.equals(ItemType.FILLER)) {
            item = new SimpleGuiItem(fillerItemTemplate);
        }
        else if (type.equals(ItemType.CLOSE_GUI)) {
            item = new CloseGUIItem(
                    ItemStackTemplate.fromConfigSection(itemSection)
            );
        }
        else {
            if (!itemSection.contains("data")) {
                throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'data' config value"));
            }

            String data = itemSection.getString("data");
            List<String> noPermissionLore = itemSection.getStringList("no-permission-lore");

            if (type.equals(ItemType.INVENTORY)) {
                if (!itemSection.contains("name")) {
                    throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'name' config value"));
                }

                if (!itemSection.contains("material")) {
                    throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'material' config value"));
                }

                ItemStackTemplate itemTemplate = ItemStackTemplate.fromConfigSection(itemSection);

                if (!guiManager.guiExists(data)) {
                    throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "targeting non-existent GUI " + data));
                }

                item = new InventoryItem(data, itemTemplate, owner, guiManager);
            }
            else if (type.equals(ItemType.COLOUR)) {
                if (!itemSection.contains("material")) {
                    throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'material' config value"));
                }

                if (GeneralUtils.isCustomColour(data)) {
                    String colour = customColoursManager.getCustomColour(data);

                    if (colour == null) {
                        GeneralUtils.sendConsoleMessage(M.PREFIX + String.format(Messages.INVALID_CUSTOM_COLOUR, name, data));
                        return new ParseResult(null, -1, false);
                    }
                }

                ItemStackTemplate itemTemplate = ItemStackTemplate.fromConfigSection(itemSection);

                // Default display name is auto-generated, but allow them to override it if they want.
                if (itemTemplate.getDisplayName() == null) {
                    itemTemplate.setDisplayName(getColourName(data));
                }
                else {
                    itemTemplate.setDisplayName(parsePrefixedColouredString(itemTemplate.getDisplayName()));
                }

                item = new ColourItem(data, itemTemplate, playerData, noPermissionLore, generalUtils);
            }
            else if (type.equals(ItemType.COMMAND)) {
                if (!itemSection.contains("material")) {
                    throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'material' config value"));
                }
                else if (!itemSection.contains("name")) {
                    throw new InvalidGuiException(String.format(Messages.INVALID_ITEM, itemKey, name, "missing 'name' config value"));
                }

                ItemStackTemplate template = ItemStackTemplate.fromConfigSection(itemSection);
                CommandItem.CommandSource source;

                if (itemSection.contains("command-source")) {
                    String rawSource = itemSection.getString("command-source");

                    try {
                        source = CommandItem.CommandSource.valueOf(rawSource);
                    }
                    catch (IllegalArgumentException _) {
                        source = CommandItem.CommandSource.CONSOLE;
                    }
                }
                else {
                    source = CommandItem.CommandSource.CONSOLE;
                }

                data = data.replace("%player%", owner.getName());
                item = new CommandItem(data, template, source);
            }
            else {
                String name = itemSection.contains("name") ?
                        itemSection.getString("name") :
                        String.format("&f&%s%s", data, generalUtils.getModifierName(data));

                item = new ModifierItem(data, name, playerData, noPermissionLore);
            }
        }

        return new ParseResult(item, slot, false);
    }

    // This checks for custom colours at the start of a string
    private String parsePrefixedColouredString(String prefixedString) {
        if (!prefixedString.startsWith("%")) {
            return prefixedString;
        }

        StringBuilder customColour = new StringBuilder("%");

        for (int i = 1; i < prefixedString.length(); i++) {
            customColour.append(prefixedString.charAt(i));
            String value = customColour.toString();

            if (customColoursManager.hasCustomColour(value)) {
                return customColoursManager.getCustomColour(value) + prefixedString.substring(value.length());
            }
        }

        return prefixedString;
    }

    private String getColourName(String colour) {
        String colourName;

        if (colour.equalsIgnoreCase("default")) {
            colourName = generalUtils.getDefaultColourForPlayer(owner.getUniqueId()) + M.DEFAULT;
        }
        else if (colour.startsWith("%")) {
            String customColourName = Arrays.stream(colour.substring(1).split("[^0-9a-zA-Z]"))
                    .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));

            colourName = customColoursManager.getCustomColour(colour) + customColourName;
        }
        else {
            colour = '&' + colour;
            colourName = String.format("%s%s", colour, generalUtils.getColorName(colour, true));
        }

        // White colours get replaced by nothing by Minecraft in item names (default colour)!
        // Prefacing it with another colour fixes this problem.
        if (colourName.startsWith("&f")) {
            colourName = "&a" + colourName;
        }

        return colourName;
    }

    public void open() {
        Inventory inventory = Bukkit.createInventory(owner, size, title);

        for (Map.Entry<Integer, GuiItem> item : items.entrySet()) {
            inventory.setItem(item.getKey(), item.getValue().buildItem());
        }

        owner.openInventory(inventory);
    }

    // Performs an interaction within the GUI, updating the passed inventory with any effects of the interaction.
    public void onInteract(int slot, Inventory inventory, Player who) {
        // This means they clicked outside the actual GUI.
        if (slot >= size) {
            return;
        }

        GuiItem clicked = items.get(slot);

        if (clicked instanceof SelectableItem) {
            SelectableItem selectable = (SelectableItem) clicked;

            if (doPreSelectChecks(selectable, slot, inventory)) {
                if (selectable.select()) {
                    String colour = playerData.getColour();

                    if (selectable instanceof ColourItem) {
                        colour = ((ColourItem) selectable).buildItem().getItemMeta().getDisplayName();
                    }

                    playSound(selectSound);
                    owner.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, colour, true));
                }
                else {
                    playSound(errorSound);
                    owner.sendMessage(M.PREFIX + Messages.INTERNAL_GUI_ERROR);
                }
            }
        }
        else if (clicked instanceof ClickableItem) {
            ((ClickableItem) clicked).click(who);
        }

        inventory.setItem(slot, clicked.buildItem());
    }

    // Returns true if it should select, false if not.
    private boolean doPreSelectChecks(SelectableItem item, int slot, Inventory inventory) {
        String displayName = ((GuiItem) item).buildItem().getItemMeta().getDisplayName();

        if (item instanceof ColourItem) {
            if (!((PermissibleItem) item).hasPermission()) {
                owner.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", displayName));
                playSound(errorSound);
                return false;
            }

            unselectCurrentColour(slot, inventory);
        }
        else if (item instanceof ModifierItem) {
            if (!canModifyColour()) {
                owner.sendMessage(M.PREFIX + M.CANNOT_MODIFY_CUSTOM_COLOR);
                playSound(errorSound);
                return false;
            }
            else if (!((PermissibleItem) item).hasPermission()) {
                owner.sendMessage(M.PREFIX + M.NO_MOD_PERMS.replace("[modifier]", displayName));
                playSound(errorSound);
                return false;
            }

            if (item.isSelected()) {
                item.unselect();
                owner.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, playerData.getColour()));
                playSound(selectSound);

                return false;
            }
        }

        return true;
    }

    private void playSound(Sound sound) {
        if (sound != null) {
            owner.playSound(owner.getLocation(), sound, 0.5f, 1.0f);
        }
    }

    private void unselectCurrentColour(int interactedSlot, Inventory inventory) {
        Optional<Map.Entry<Integer, GuiItem>> selectedOptional = getSelectedColourItemEntry();

        // Deselect any currently selected colour item.
        selectedOptional.ifPresent(selectedEntry -> {
            // Only if it's not the current selection.
            if (selectedEntry.getKey() != interactedSlot) {
                ((SelectableItem) selectedEntry.getValue()).unselect();
                inventory.setItem(selectedEntry.getKey(), selectedEntry.getValue().buildItem());
            }
        });
    }

    private boolean canModifyColour() {
        return !customColoursManager.hasCustomColour(playerData.getColourName());
    }

    private Optional<Map.Entry<Integer, GuiItem>> getSelectedColourItemEntry() {
        return items.entrySet().stream()
                .filter(i -> i.getValue() instanceof ColourItem)
                .filter(i -> ((SelectableItem) i.getValue()).isSelected())
                .findFirst();
    }

    public static void setFillerItemMaterial(Material material) {
        fillerItemTemplate = new ItemStackTemplate(
                material,
                "&f",
                null,
                null
        );
    }

    public static void setSelectSound(Sound sound) {
        Gui.selectSound = sound;
    }

    public static void setErrorSound(Sound sound) {
        Gui.errorSound = sound;
    }

    public static void setNoPermissionItemTemplate(ItemStackTemplate template) {
        Gui.noPermissionItemTemplate = template;
    }

    public static ItemStackTemplate getNoPermissionItemTemplate() {
        return Gui.noPermissionItemTemplate;
    }

}
