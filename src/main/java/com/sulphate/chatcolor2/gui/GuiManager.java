package com.sulphate.chatcolor2.gui;

import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.exception.InvalidGuiException;
import com.sulphate.chatcolor2.exception.InvalidItemTemplateException;
import com.sulphate.chatcolor2.exception.InvalidMaterialException;
import com.sulphate.chatcolor2.gui.item.impl.CommandItem;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import com.sulphate.chatcolor2.gui.item.impl.ColourItem;
import com.sulphate.chatcolor2.gui.item.impl.ModifierItem;
import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Messages;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class GuiManager implements Reloadable, Listener {

    private static final String GUI_CONFIG_KEY = "config";

    private static boolean shouldCopyNoPermissionItemMaterial;

    private final ConfigsManager configsManager;
    private final PlayerDataStore dataStore;
    private final GeneralUtils generalUtils;
    private final CustomColoursManager customColoursManager;
    private final Messages M;

    private final Map<String, ConfigurationSection> guiConfigs;
    private final Map<Player, Gui> openGuis;
    private final List<Player> transitioningPlayers;

    private String mainConfigName = "main";

    public GuiManager(ConfigsManager configsManager, PlayerDataStore dataStore, GeneralUtils generalUtils, CustomColoursManager customColoursManager, Messages M) {
        this.configsManager = configsManager;
        this.dataStore = dataStore;
        this.generalUtils = generalUtils;
        this.customColoursManager = customColoursManager;
        this.M = M;

        guiConfigs = new HashMap<>();
        openGuis = new HashMap<>();
        transitioningPlayers = new ArrayList<>();
        shouldCopyNoPermissionItemMaterial = false;

        reload();
    }

    public static boolean shouldCopyNoPermissionItemMaterial() {
        return shouldCopyNoPermissionItemMaterial;
    }

    public void closeOpenGuis() {
        for (Player player : openGuis.keySet()) {
            player.closeInventory();
        }
    }

    @Override
    public void reload() {
        closeOpenGuis();

        transitioningPlayers.clear();
        openGuis.clear();
        guiConfigs.clear();

        YamlConfiguration config = configsManager.getConfig(Config.GUI);
        Set<String> keys = config.getKeys(false);

        if (keys.contains(GUI_CONFIG_KEY)) {
            ConfigurationSection configSection = config.getConfigurationSection(GUI_CONFIG_KEY);

            CommandItem.clickToRunMessage = M.CLICK_TO_RUN;

            if (configSection.contains("main-inventory")) {
                mainConfigName = configSection.getString("main-inventory");
            }

            if (configSection.contains("filler-item-material")) {
                String materialName = configSection.getString("filler-item-material");

                try {
                    Gui.setFillerItemMaterial(Material.valueOf(materialName));
                }
                catch (IllegalArgumentException ex) {
                    GeneralUtils.sendConsoleMessage(M.PREFIX + String.format(Messages.INVALID_FILLER_MATERIAL, materialName));
                }
            }

            if (configSection.contains("no-permission-item")) {
                ConfigurationSection noPermissionSection = configSection.getConfigurationSection("no-permission-item");

                if (noPermissionSection == null) {
                    throw new InvalidGuiException("no-permission-item section is missing from the config.");
                }

                String material = noPermissionSection.getString("material");

                if (material != null && material.equals("COPY")) {
                    shouldCopyNoPermissionItemMaterial = true;
                    // Setting the default material so that it has something to work with.
                    noPermissionSection.set("material", Material.BARRIER);
                }

                try {
                    Gui.setNoPermissionItemTemplate(ItemStackTemplate.fromConfigSection(noPermissionSection));
                }
                catch (InvalidItemTemplateException | InvalidMaterialException ex) {
                    // Do nothing, a null no-permissions item is fine!
                }
            }

            Gui.setSelectSound(tryGetSound(configSection, "select-sound"));
            Gui.setErrorSound(tryGetSound(configSection, "error-sound"));

            if (configSection.contains("color.selected-text")) {
                ColourItem.setSelectedText(configSection.getString("color.selected-text"));
            }

            if (configSection.contains("color.unselected-text")) {
                ColourItem.setUnselectedText(configSection.getString("color.unselected-text"));
            }

            if (configSection.contains("modifier.selected-text")) {
                ModifierItem.setSelectedText(configSection.getString("color.selected-text"));
            }

            if (configSection.contains("modifier.unselected-text")) {
                ModifierItem.setUnselectedText(configSection.getString("color.unselected-text"));
            }

            try {
                if (configSection.contains("modifier.selected-material")) {
                    ModifierItem.setSelectedMaterial(Material.valueOf(configSection.getString("modifier.selected-material")));
                }

                if (configSection.contains("modifier.unselected-material")) {
                    ModifierItem.setUnselectedMaterial(Material.valueOf(configSection.getString("modifier.unselected-material")));
                }
            }
            catch (IllegalArgumentException ex) {
                throw new InvalidGuiException(M.PREFIX + Messages.INVALID_MODIFIER_MATERIAL);
            }
        }
        else {
            GeneralUtils.sendConsoleMessage(M.PREFIX + Messages.NO_GUI_CONFIG_SECTION);
            GeneralUtils.sendConsoleMessage(M.PREFIX + Messages.REGENERATE_CONFIG_MESSAGE);
        }

        for (String inventoryName : keys) {
            if (inventoryName.equals(GUI_CONFIG_KEY)) {
                continue;
            }

            guiConfigs.put(inventoryName, config.getConfigurationSection(inventoryName));
        }

        if (!guiConfigs.containsKey(mainConfigName)) {
            GeneralUtils.sendConsoleMessage(M.PREFIX + String.format(Messages.MAIN_GUI_NOT_FOUND, mainConfigName));
        }
    }

    private Sound tryGetSound(ConfigurationSection section, String key) {
        if (section.contains(key)) {
            String soundName = section.getString(key);

            try {
                return Sound.valueOf(soundName);
            }
            catch (IllegalArgumentException ex) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + String.format(Messages.INVALID_SOUND_NAME, soundName));
            }
        }

        return null;
    }

    public void openMainGui(Player player) {
        Gui main = createGui(mainConfigName, player);
        openGui(main, player);
    }

    public Gui createGui(String name, Player player) {
        if (!guiConfigs.containsKey(name)) {
            player.sendMessage(M.PREFIX + M.INVALID_GUI.replace("[name]", name));
            return null;
        }

        try {
            ConfigurationSection section = guiConfigs.get(name);
            return new Gui(name, section, player, dataStore.getPlayerData(player.getUniqueId()), this, generalUtils, customColoursManager, M);
        }
        catch (InvalidGuiException ex) {
            player.sendMessage(M.PREFIX + M.GUI_ERROR.replace("[name]", name));
            GeneralUtils.sendConsoleMessage(String.format(Messages.INVALID_GUI_ERROR_MESSAGE, ex.getMessage()));
            return null;
        }
    }

    public boolean guiExists(String name) {
        return guiConfigs.containsKey(name);
    }

    public void openGui(Gui gui, Player player) {
        if (gui == null) {
            return;
        }

        if (openGuis.containsKey(player)) {
            transitioningPlayers.add(player);
            gui.open();
            transitioningPlayers.remove(player);
        }
        else {
            gui.open();
        }

        openGuis.put(player, gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getView().getTopInventory();

        if (openGuis.containsKey(player)) {
            event.setCancelled(true);

            InventoryAction action = event.getAction();
            ItemStack clicked = event.getCurrentItem();

            if (action.equals(InventoryAction.PICKUP_ALL) && clicked != null && !clicked.getType().equals(Material.AIR)) {
                // Perform the interaction, persist any changes to their colour.
                openGuis.get(player).onInteract(event.getRawSlot(), inventory, (Player) event.getWhoClicked());
                dataStore.savePlayerData(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();

        if (!transitioningPlayers.contains(player)) {
            openGuis.remove(player);
        }
    }

}
