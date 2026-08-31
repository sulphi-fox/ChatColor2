package com.sulphate.chatcolor2.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import com.sulphate.chatcolor2.commands.ChatColorCommand;
import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.data.DatabaseConnectionSettings;
import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.data.SqlStorageImpl;
import com.sulphate.chatcolor2.data.YamlStorageImpl;
import com.sulphate.chatcolor2.gui.item.ItemStackTemplate;
import com.sulphate.chatcolor2.listeners.*;
import com.sulphate.chatcolor2.managers.*;
import com.sulphate.chatcolor2.gui.GuiManager;
import com.sulphate.chatcolor2.utils.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sulphate.chatcolor2.schedulers.ConfirmScheduler;
import com.sulphate.chatcolor2.commands.ConfirmHandler;

public class ChatColor extends JavaPlugin {

    private static final String EXAMPLE_HEAD_DATA = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzMzYWU4ZGU3ZWQwNzllMzhkMmM4MmRkNDJiNzRjZmNiZDk0YjM0ODAzNDhkYmI1ZWNkOTNkYThiODEwMTVlMyJ9fX0=";

    private static ChatColor plugin;
    private static List<Reloadable> reloadables;

    private HandlersManager handlersManager;
    private ConfigUtils configUtils;
    private ConfigsManager configsManager;
    private CustomColoursManager customColoursManager;
    private GroupColoursManager groupColoursManager;
    private GeneralUtils generalUtils;
    private GuiManager guiManager;
    private ConfirmationsManager confirmationsManager;
    private PlayerDataStore playerDataStore;
    private Messages M;

    private PlayerJoinListener joinListener;
    private ChatListener chatListener;
    private YamlConfiguration config;

    private final ConsoleCommandSender console = Bukkit.getConsoleSender();
    private PluginManager manager;

    public static ChatColor getPlugin() {
        return plugin;
    }

    public static List<Reloadable> getReloadables() {
        return reloadables;
    }

    @Override
    public void onEnable() {
        plugin = this;
        reloadables = new ArrayList<>();
        manager = Bukkit.getPluginManager();

        // Setup objects. commands & listeners.
        setupObjects();
        setupListeners();
        setupCommands();

        //Checking if Metrics is allowed for this plugin
        boolean metrics = getConfig().getBoolean("stats");
        if (metrics) {
            new Metrics(this, 826);
        }

        // Startup messages.
        for (String message : M.STARTUP_MESSAGES) {
            message = message.replace("[version]", getDescription().getVersion());
            message = message.replace("[version-description]", "Support for latest MC version");
            console.sendMessage(M.PREFIX + GeneralUtils.colourise(message));
        }

        // Check for player head compatibility
        ItemStackTemplate head = new ItemStackTemplate(Material.PLAYER_HEAD, null, null, EXAMPLE_HEAD_DATA);
        head.build(1);

        if (head.failedToApplyHeadData()) {
            console.sendMessage(M.PREFIX + Messages.PLAYER_HEADS_NOT_SUPPORTED);
        }

        // Check whether PlaceholderAPI is installed, if it is load the expansion.
        if (manager.getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(
                    this, generalUtils, customColoursManager, groupColoursManager, playerDataStore, M
            ).register();

            console.sendMessage(M.PREFIX + M.PLACEHOLDERS_ENABLED);
        }
        else {
            console.sendMessage(M.PREFIX + M.PLACEHOLDERS_DISABLED);
        }

        // Send the relevant metrics message.
        if (!metrics) {
            console.sendMessage(M.PREFIX + M.METRICS_DISABLED);
        }
        else {
            console.sendMessage(M.PREFIX + M.METRICS_ENABLED);
        }

        // Call a fake join event for each online player.
        for (Player player : Bukkit.getOnlinePlayers()) {
            joinListener.onEvent(new PlayerJoinEvent(player, ""));
        }
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeOpenGuis();
        }

        if (playerDataStore != null) {
            playerDataStore.shutdown();
        }

        plugin = null;

        if (M != null) {
            console.sendMessage(M.PREFIX + M.SHUTDOWN.replace("[version]", getDescription().getVersion()));
        }
        else {
            console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &eChatColor 2 Version &b" + getDescription().getVersion() + " &ehas been &cdisabled&e."));
        }
    }

    private void setupObjects() {
        configUtils = new ConfigUtils(this, GeneralUtils::sendConsoleMessage);
        configsManager = new ConfigsManager(configUtils);
        config = configsManager.getConfig(Config.MAIN_CONFIG);

        // Validate the main config.
        if (!validateConfig()) {
            manager.disablePlugin(this);
            return;
        }

        handlersManager = new HandlersManager();
        customColoursManager = new CustomColoursManager(configsManager);
        groupColoursManager = new GroupColoursManager(configsManager);
        M = new Messages(configsManager);

        // Scan messages here to avoid any null messages when upgrading versions.
        scanMessages();

        // Initialise player data store.
        String pdcType = config.getString("storage.type");

        if (pdcType != null && pdcType.equals("sql")) {
            ConfigurationSection dbSection = config.getConfigurationSection("storage.database");

            if (dbSection == null) {
                GeneralUtils.sendConsoleMessage(M.PREFIX + M.MISSING_DB_CONFIG_SECTION);
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            // Since v1.14.2 - mariadb support.
            else if (!dbSection.contains("type")) {
                dbSection.set("type", "mysql");
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }

            playerDataStore = new SqlStorageImpl(new DatabaseConnectionSettings(dbSection), M);
        }
        else {
            int saveInterval = config.getInt(Setting.SAVE_INTERVAL.getConfigPath());
            playerDataStore = new YamlStorageImpl(configsManager, saveInterval, M);
        }

        generalUtils = new GeneralUtils(configsManager, customColoursManager, playerDataStore, groupColoursManager, M);
        guiManager = new GuiManager(configsManager, playerDataStore, generalUtils, customColoursManager, M);
        confirmationsManager = new ConfirmationsManager();

        reloadables.add(customColoursManager);
        reloadables.add(groupColoursManager);
        reloadables.add(M);
        reloadables.add(generalUtils);
        reloadables.add(guiManager);

        // Scan settings and other values to make sure all are present.
        scanSettings();
        scanOther();
    }

    private void setupCommands() {
        ChatColorCommand command = new ChatColorCommand(
                M, generalUtils, confirmationsManager, configsManager, handlersManager, guiManager,
                customColoursManager, groupColoursManager, playerDataStore, chatListener
        );
        ConfirmHandler confirmHandler = new ConfirmHandler(
                M, confirmationsManager, configsManager, customColoursManager, guiManager, generalUtils,
                playerDataStore
        );

        getCommand("chatcolor").setExecutor(command);
        reloadables.add(command);
        reloadables.add(confirmHandler);
        handlersManager.registerHandler(ConfirmHandler.class, confirmHandler);
    }

    private void setupListeners() {
        EventPriority chatPriority = EventPriority.valueOf(config.getString("settings.event-priority"));
        chatListener = new ChatListener(configsManager, generalUtils, groupColoursManager, playerDataStore);

        EventExecutor executor = (listener, event) -> {
            if (listener instanceof ChatListener && event instanceof AsyncPlayerChatEvent) {
                ((ChatListener) listener).onEvent((AsyncPlayerChatEvent) event);
            }
        };

        // Attempt to register
        manager.registerEvent(AsyncPlayerChatEvent.class, chatListener, chatPriority, executor, this);

        joinListener = new PlayerJoinListener(
                M, configsManager, generalUtils, customColoursManager, groupColoursManager, playerDataStore
        );
        CustomCommandListener commandListener = new CustomCommandListener(configsManager);

        manager.registerEvents(joinListener, this);
        manager.registerEvents(commandListener, this);
        manager.registerEvents(guiManager, this);

        reloadables.add(joinListener);
        reloadables.add(chatListener);
        reloadables.add(commandListener);
    }

    private boolean validateConfig() {
        File dataFolder = getDataFolder();
        File configFile = new File(dataFolder, "config.yml");

        // Save default config if it doesn't exist.
        if (configFile.exists()) {
            // Check if the old config version is less than 1.14 (SQL Update):
            // If it is, backup the old config and load the new format.
            String version = config.getString("version");
            String latest = getDescription().getVersion();

            if (!compareVersions(version, "1.15")) {
                if (!backupOldConfig("gui.yml")) return false;
                saveResource("gui.yml", true);
                configsManager.reloadSingle(Config.GUI);

                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cWarning: &eAn old GUI config was found. It has been copied to &aold-gui.yml&e."));
            }

            if (!compareVersions(version, "1.14")) {
                if (!backupOldConfig("config.yml")) return false;
                saveResource("config.yml", true);
                configsManager.reloadSingle(Config.MAIN_CONFIG);

                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cWarning: &eAn old version of the config was found. It has been copied to &aold-config.yml&e."));
            }
            else if (!compareVersions(version, "1.12")) {
                File legacyGroupConfigFile = new File(dataFolder, "groups.yml");

                if (legacyGroupConfigFile.exists()) {
                    YamlConfiguration legacyGroupConfig = YamlConfiguration.loadConfiguration(legacyGroupConfigFile);
                    File newGroupConfigFile = new File(dataFolder, "groups.yml");

                    try {
                        newGroupConfigFile.createNewFile();
                        legacyGroupConfig.save(newGroupConfigFile);
                        legacyGroupConfigFile.delete();

                        GeneralUtils.sendConsoleMessage("&b[ChatColor] &bInfo: &eCopied legacy groups config to a new file, groups.yml.");
                    }
                    catch (IOException ex) {
                        GeneralUtils.sendConsoleMessage("&b[ChatColor] &cWarning: &eFailed to copy legacy groups config to new file: " + ex.getMessage());
                    }
                }
            }
            // Update the version if it's behind.
            else if (!version.equals(latest)) {
                config.set("version", latest);
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }
        }

        return true;
    }

    // Compares two version strings, returning true if the first is greater than or equal to the second (in format x.x.x...x).
    private boolean compareVersions(String version1, String version2) {
        // This happens on VERY old versions of the plugin (2017).
        if (version1 == null) {
            return false;
        }

        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        // Iterating up to first version's length, as the version string may be shorter.
        for (int i = 0; i < parts1.length; i++) {
            if (i == parts2.length) {
                return true;
            }

            int intPart1 = Integer.parseInt(parts1[i]);
            int intPart2 = Integer.parseInt(parts2[i]);

            // If greater, this is a newer version.
            if (intPart1 > intPart2) return true;
            // If less, this is an older version.
            if (intPart1 < intPart2) return false;
            // If equal, continue to compare.
        }

        return true;
    }

    // Backs up an old version of the config to a separate file, so it can be copied from to the new format.
    private boolean backupOldConfig(String configName) {
        File oldConfig = new File(getDataFolder(), configName);
        File backupFile = new File(getDataFolder(), "old-" + configName);

        try {
            // Create the backup file, load the old config and save it to the file.
            if (!backupFile.exists()) {
                if (!backupFile.createNewFile()) return false;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(oldConfig);
            config.save(backupFile);
        }
        catch (IOException ex) {
            console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: Failed to create backup file."));
            return false;
        }

        return true;
    }

    // Scans the current messages.yml to make sure all messages are present (compared with current default).
    private void scanMessages() {
        InputStream defaultStream = getResource("messages.yml");

        if (defaultStream == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: Failed to load default messages resource. Messages will not be scanned."));
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        YamlConfiguration currentConfig = configsManager.getConfig(Config.MESSAGES);

        Set<String> keys = defaultConfig.getKeys(false);
        boolean needsReload = false;
        for (String key : keys) {
            // Check all messages are present.
            if (!currentConfig.contains(key)) {
                // If not, set the message and save the config.
                // Yes, this ruins the formatting, but at least the plugin works.
                currentConfig.set(key, defaultConfig.getString(key));
                configsManager.saveConfig(Config.MESSAGES);

                console.sendMessage(M.PREFIX + GeneralUtils.colourise("&eAdded new message: &a" + key));
                needsReload = true;
            }
        }

        // Reload messages if necessary.
        if (needsReload) {
            M.reloadMessages();
        }
    }

    // Scans the current config.yml for settings differences (any new settings will be added).
    private void scanSettings() {
        InputStream defaultStream = getResource("config.yml");

        if (defaultStream == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: Failed to load default config resource. Settings will not be scanned."));
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        Set<String> keys = defaultConfig.getConfigurationSection("settings").getKeys(false);

        for (String key : keys) {
            // Check all settings are present.
            if (!config.contains("settings." + key)) {
                // If not, set the default and save the config.
                config.set("settings." + key, defaultConfig.get("settings." + key));
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }
        }
    }

    private void scanOther() {
        if (!config.contains("placeholders")) {
            config.set("placeholders", Collections.singletonList("[item]"));
            configsManager.saveConfig(Config.MAIN_CONFIG);
        }
    }

    // Adds a player to the confirming list, and starts the scheduler.
    public void createConfirmScheduler(Player player, Setting setting, Object value) {
        ConfirmScheduler scheduler = new ConfirmScheduler(M, confirmationsManager, configsManager, player, setting, value);
        confirmationsManager.addConfirmingPlayer(player, scheduler);
    }

}
