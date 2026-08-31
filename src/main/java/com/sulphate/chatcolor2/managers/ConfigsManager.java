package com.sulphate.chatcolor2.managers;

import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.ConfigUtils;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

public class ConfigsManager implements Reloadable {

    private final ConfigUtils configUtils;

    private final HashMap<String, YamlConfiguration> configs;

    public ConfigsManager(ConfigUtils configUtils) {
        this.configUtils = configUtils;
        configs = new HashMap<>();

        reload();
    }

    // (re)loads all configs.
    public void reload() {
        configUtils.clearCache();
        configs.clear();

        for (Config config : Config.values()) {
            String fileName = config.getFilename();
            configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName));
        }
    }

    // (re)loads a specific config.
    public void reloadSingle(Config config) {
        String fileName = config.getFilename();
        configs.put(fileName, configUtils.getConfigOrCopyDefault(fileName));
    }

    // Returns a given config.
    public YamlConfiguration getConfig(Config config) {
        return configs.get(config.getFilename());
    }

    // Saves a config.
    public void saveConfig(Config config) {
        configUtils.saveConfig(config.getFilename());
    }

    // Loads a player config.
    public void loadPlayerConfig(UUID uuid) {
        String filePath = "players" + File.separator + uuid + ".yml";
        configs.put(uuid + ".yml", configUtils.getConfigOrCreateBlank(filePath));
    }

    // Gets a player config.
    public YamlConfiguration getPlayerConfig(UUID uuid) {
        return configs.get(uuid + ".yml");
    }

}
