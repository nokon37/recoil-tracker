package com.nokon37.recoiltracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("recoiltracker")
public interface RecoilTrackerConfig extends Config
{
    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Display the recoil tracker overlay",
            position = 1
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "overlayColor",
            name = "Overlay Color",
            description = "Color of the overlay text",
            position = 2
    )
    default java.awt.Color overlayColor()
    {
        return java.awt.Color.WHITE;
    }
}