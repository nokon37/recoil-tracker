package com.nokon37.recoiltracker;

import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

public class RecoilPanel extends PluginPanel
{
    private final RecoilTrackerPlugin plugin;

    private final JLabel fightTotalLabel = new JLabel("0");

    @Inject
    private RecoilPanel(RecoilTrackerPlugin plugin)
    {
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel fightPanel = createStatPanel("Recoil Damage:", fightTotalLabel);
        container.add(fightPanel);
        container.add(Box.createVerticalStrut(10));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 1));

        JButton resetFightBtn = new JButton("Reset");
        resetFightBtn.addActionListener(this::resetFight);
        buttonPanel.add(resetFightBtn);

        container.add(buttonPanel);

        add(container, BorderLayout.NORTH);
    }

    private JPanel createStatPanel(String labelText, JLabel valueLabel)
    {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        panel.add(label, BorderLayout.WEST);
        panel.add(valueLabel, BorderLayout.EAST);
        return panel;
    }

    public void update()
    {
        SwingUtilities.invokeLater(() -> {
            fightTotalLabel.setText(String.valueOf(plugin.getCurrentFightTotal()));
        });
    }

    private void resetFight(ActionEvent e)
    {
        plugin.resetCurrentFight();
        update();
    }
}