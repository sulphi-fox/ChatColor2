package com.sulphate.chatcolor2.listeners;

import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.managers.GroupColoursManager;
import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.sulphate.chatcolor2.utils.Messages;

import java.util.UUID;

public class PlayerJoinListener implements Listener, Reloadable {

    private final Messages M;
    private final ConfigsManager configsManager;
    private final GeneralUtils generalUtils;
    private final CustomColoursManager customColoursManager;
    private final GroupColoursManager groupColoursManager;
    private final PlayerDataStore dataStore;

    private YamlConfiguration mainConfig;

    public PlayerJoinListener(
            Messages M, ConfigsManager configsManager, GeneralUtils generalUtils,
            CustomColoursManager customColoursManager, GroupColoursManager groupColoursManager,
            PlayerDataStore dataStore
    ) {
        this.M = M;
        this.configsManager = configsManager;
        this.generalUtils = generalUtils;
        this.customColoursManager = customColoursManager;
        this.groupColoursManager = groupColoursManager;
        this.dataStore = dataStore;

        reload();
    }

    public void reload() {
        mainConfig = configsManager.getConfig(Config.MAIN_CONFIG);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        dataStore.loadPlayerData(uuid, loaded -> {
            if (loaded) {
                if (dataStore.getColour(uuid) == null) {
                    setInitialColour(uuid);
                }

                checkCustomColour(uuid);

                // Check their default colour.
                generalUtils.checkDefault(uuid);

                sendJoinMessage(player);

                if (GeneralUtils.check(player)) {
                    player.sendMessage(M.PREFIX + M.PLUGIN_NOTIFICATION.replace("[version]", ChatColor.getPlugin().getDescription().getVersion()));
                }
            }
        });
    }

    private void sendJoinMessage(Player player) {
        if (mainConfig.getBoolean(Setting.JOIN_MESSAGE.getConfigPath())) {
            // Check if they have a group colour, and if it should be enforced (copied code from chat listener, may abstract it at some point).
            String groupColour = groupColoursManager.getGroupColourForPlayer(player);
            String colour = dataStore.getColour(player.getUniqueId());

            if (groupColour != null) {
                // If it should be forced, set it so.
                if (mainConfig.getBoolean(Setting.FORCE_GROUP_COLORS.getConfigPath())
                        && !player.hasPermission("chatcolor.admin.ignore-group-colors")
                ) {
                    colour = groupColour;
                }
            }

            player.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.CURRENT_COLOR, colour));
        }
    }

    private void checkCustomColour(UUID uuid) {
        String colour = dataStore.getColour(uuid);

        if (colour.startsWith("%")) {
            if (customColoursManager.getCustomColour(colour) == null) {
                setInitialColour(uuid);
            }
        }
    }

    private void setInitialColour(UUID uuid) {
        String colour = dataStore.getColour(uuid);

        if (colour == null) {
            if (mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
                dataStore.setColour(uuid, mainConfig.getString("default.color"));
            }
            else {
                dataStore.setColour(uuid, "");
            }
        }
    }

}
