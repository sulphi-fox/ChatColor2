package com.sulphate.chatcolor2.schedulers;

import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class AutoSaveScheduler {

    private final ChatColor plugin;

    private SchedulerAdapter.TaskWrapper task;
    private final ConcurrentHashMap<String, YamlConfiguration> configsToSave;
    private int saveInterval;

    public AutoSaveScheduler(int saveInterval) {
        this.plugin = ChatColor.getPlugin();
        this.configsToSave = new ConcurrentHashMap<>();
        this.saveInterval = saveInterval;

        run();
    }

    private void run() {
        long intervalTicks = (long) saveInterval * 20 * 60;
        task = SchedulerAdapter.runTimer(plugin, this::saveAllConfigs, intervalTicks, intervalTicks);
    }

    public void setSaveInterval(int saveInterval) {
        this.saveInterval = saveInterval;
        restart();
    }

    private void restart() {
        task.cancel();
        run();
    }

    public void stop() {
        task.cancel();
        saveAllConfigs();
    }

    // Adds a config to the HashMap to be saved.
    public void saveConfigWithDelay(String configName, YamlConfiguration config) {
        configsToSave.put(configName, config);
    }

    // Saves all pending configs to files.
    private void saveAllConfigs() {
        for (String configName : configsToSave.keySet()) {
            YamlConfiguration config = configsToSave.get(configName);
            File file = new File(plugin.getDataFolder(), configName);

            try {
                config.save(file);
            }
            catch (IOException ex) {
                Bukkit.getConsoleSender().sendMessage(GeneralUtils.colourise("&cError: Failed to save a config (" + configName + ")!"));
            }

            configsToSave.remove(configName);
        }
    }

}
