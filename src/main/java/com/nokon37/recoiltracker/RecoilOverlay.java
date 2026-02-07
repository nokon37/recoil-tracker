package com.nokon37.recoiltracker;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class RecoilOverlay extends Overlay
{
    private final Client client;
    private final RecoilTrackerPlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private RecoilOverlay(Client client, RecoilTrackerPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(150, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        RecoilTrackerConfig config = plugin.getConfig();

        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Recoil Damage:")
                .right(String.valueOf(plugin.getCurrentFightTotal()))
                .leftColor(config.overlayColor())
                .rightColor(config.overlayColor())
                .build());

        return panelComponent.render(graphics);
    }
}