package com.sulphate.chatcolor2.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Utils for managing cross-api-version compatability.
public class CompatabilityUtils {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)+)");

    private static boolean isMaterialLegacy;
    private static boolean isHexLegacy;
    private static HashMap<String, Short> blockColourToDataMap;
    private static HashMap<String, Short> dyeColourToDataMap;

    private CompatabilityUtils() {
        // Empty private constructor.
    }

    public static void init() {
        isHexLegacy = isHexLegacyVersion(Bukkit.getBukkitVersion());
        isMaterialLegacy = Material.getMaterial("INK_SAC") == null;

        blockColourToDataMap = new HashMap<>();
        dyeColourToDataMap = new HashMap<>();

        String[] blockColourNames = { "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK" };
        for (int i = 0; i < blockColourNames.length; i++) {
            blockColourToDataMap.put(blockColourNames[i], (short) i);
        }

        String[] dyeColourNames = { "INK", "RED", "GREEN", "COCOA", "LAPIS", "PURPLE", "CYAN", "LIGHT_GRAY", "GRAY", "PINK", "LIME", "YELLOW", "LIGHT_BLUE", "MAGENTA", "ORANGE", "BONE", "BLACK", "BROWN", "BLUE", "WHITE" };
        for (int i = 0; i < dyeColourNames.length; i++) {
            dyeColourToDataMap.put(dyeColourNames[i], (short) i);
        }
    }

    private static boolean isHexLegacyVersion(String version) {
        int[] versionParts = parseVersionParts(version);

        if (versionParts.length < 2) {
            return false;
        }

        return versionParts[0] == 1 && versionParts[1] < 16;
    }

    private static int[] parseVersionParts(String version) {
        if (version == null) {
            return new int[0];
        }

        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) {
            return new int[0];
        }

        String[] rawParts = matcher.group(1).split("\\.");
        int[] versionParts = new int[rawParts.length];

        for (int i = 0; i < rawParts.length; i++) {
            try {
                versionParts[i] = Integer.parseInt(rawParts[i]);
            }
            catch (NumberFormatException ex) {
                return new int[0];
            }
        }

        return versionParts;
    }

    public static ItemStack getColouredItem(String materialName) {
        if (!isMaterialLegacy) {
            return new ItemStack(Material.getMaterial(materialName), 1);
        }

        boolean isLightColour = materialName.startsWith("LIGHT");
        int underscoreIndex = materialName.indexOf('_');

        Material legacyMaterial = getLegacyMaterial(materialName);
        String colourName = underscoreIndex == -1 ? null : materialName.substring(0, isLightColour ? materialName.indexOf('_', underscoreIndex + 1) : underscoreIndex);
        Short legacyColourData = colourName == null ? null : materialName.contains("DYE") ? dyeColourToDataMap.get(colourName) : blockColourToDataMap.get(colourName);

        if (legacyMaterial == null) {
            GeneralUtils.sendConsoleMessage("&6[ChatColor] &cError: Failed to resolve legacy material: " + materialName);
            return new ItemStack(Material.AIR);
        }

        // If null, then it's not a coloured item.
        if (legacyColourData == null) {
            return new ItemStack(legacyMaterial, 1);
        }
        // Use legacy colour constructor.
        else {
            return new ItemStack(legacyMaterial, 1, legacyColourData);
        }
    }

    private static Material getLegacyMaterial(String materialName) {
        if (materialName.contains("DYE")) {
            return Material.getMaterial("INK_SACK");
        }
        else if (materialName.contains("STAINED_GLASS_PANE")) {
            return Material.getMaterial("STAINED_GLASS_PANE");
        }
        else if (materialName.contains("STAINED_GLASS")) {
            return Material.getMaterial("GLASS");
        }
        else if (materialName.contains("GLASS_PANE")) {
            return Material.getMaterial("THIN_GLASS");
        }
        else if (materialName.equals("INK_SAC")) {
            return Material.getMaterial("INK_SACK");
        }
        else {
            try {
                // Just return the material if there is no legacy (if possible).
                return Material.getMaterial(materialName);
            }
            catch (Exception ex) {
                return null;
            }
        }
    }

    public static boolean isMaterialLegacy() {
        return isMaterialLegacy;
    }

    public static boolean isHexLegacy() {
        return isHexLegacy;
    }

}
