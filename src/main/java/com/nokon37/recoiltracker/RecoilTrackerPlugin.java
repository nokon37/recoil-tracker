package com.nokon37.recoiltracker;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@PluginDescriptor(
        name = "Recoil Tracker",
        description = "Tracks recoil damage dealt by the player (Ring of Recoil, Ring of Suffering, Echo Boots)",
        tags = {"combat", "recoil", "hitsplat", "damage", "osrs"}
)
public class RecoilTrackerPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(RecoilTrackerPlugin.class);

    private static final long DETECTION_WINDOW_MS = 1200;
    private static final long DEDUP_WINDOW_MS = 200;
    private static final int COMBAT_END_TICKS = 3;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private RecoilOverlay overlay;

    @Inject
    private RecoilPanel panel;

    @Inject
    private ItemManager itemManager;

    private volatile int currentFightTotal = 0;
    private volatile int lastFightTotal = 0;

    private volatile int lastRecoilValue = 0;

    private volatile long lastPlayerHitTime = 0L;
    private volatile int lastPlayerHitAmount = 0;
    private volatile Actor currentTarget = null;
    private volatile boolean inCombat = false;
    private volatile int ticksWithoutCombat = 0;

    private volatile int pendingRecoilAmount = 0;
    private volatile boolean pendingIsEchoBoots = false;

    private final Map<Actor, Long> lastProcessedTime = new ConcurrentHashMap<>();

    @Override
    protected void startUp() throws Exception
    {
        log.info("RecoilTrackerPlugin started");
        overlayManager.add(overlay);
        resetCurrentFight();
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("RecoilTrackerPlugin stopped");
        overlayManager.remove(overlay);
        lastProcessedTime.clear();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        Actor actor = event.getActor();
        Hitsplat hitsplat = event.getHitsplat();
        long now = Instant.now().toEpochMilli();
        int amount = hitsplat.getAmount();
        int hitsplatType = hitsplat.getHitsplatType();

        if (actor.equals(client.getLocalPlayer()))
        {
            if (hitsplatType == HitsplatID.DAMAGE_ME || hitsplatType == HitsplatID.DAMAGE_OTHER || hitsplatType == 16)
            {
                lastPlayerHitTime = now;
                lastPlayerHitAmount = amount;
                ticksWithoutCombat = 0;

                String recoilSource = getRecoilSource();
                if (recoilSource != null)
                {
                    if (recoilSource.equals("Echo Boots"))
                    {
                        pendingRecoilAmount = 1;
                        pendingIsEchoBoots = true;
                    }
                    else
                    {
                        pendingRecoilAmount = (int) Math.ceil(amount * 0.1);
                        pendingIsEchoBoots = false;
                    }
                }
                else
                {
                    pendingRecoilAmount = 0;
                    pendingIsEchoBoots = false;
                }

                Actor interacting = client.getLocalPlayer().getInteracting();
                if (interacting != null)
                {
                    if (interacting != currentTarget)
                    {
                        endCurrentFight();
                        startNewFight(interacting);
                    }
                    else if (!inCombat)
                    {
                        if (lastFightTotal > 0)
                        {
                            lastFightTotal = 0;
                        }
                        startNewFight(interacting);
                    }
                    else
                    {
                        ticksWithoutCombat = 0;
                    }
                }
            }
            return;
        }

        if (lastPlayerHitTime == 0 || now - lastPlayerHitTime > DETECTION_WINDOW_MS)
        {
            return;
        }

        if (pendingRecoilAmount <= 0)
        {
            return;
        }

        Actor interacting = actor.getInteracting();
        if (interacting == null || !interacting.equals(client.getLocalPlayer()))
        {
            return;
        }

        if (hitsplatType != 16 && hitsplatType != HitsplatID.DAMAGE_OTHER)
        {
            return;
        }

        if (amount <= 0)
        {
            return;
        }

        boolean amountMatches;
        if (pendingIsEchoBoots)
        {
            amountMatches = (amount == 1);
        }
        else
        {
            int expectedRecoil = (int) Math.ceil(lastPlayerHitAmount * 0.1);
            amountMatches = (amount == expectedRecoil) ||
                    (amount == (int)(lastPlayerHitAmount * 0.1)) ||
                    (amount >= expectedRecoil - 1 && amount <= expectedRecoil + 1);
        }

        if (!amountMatches)
        {
            return;
        }

        Long lastTime = lastProcessedTime.get(actor);
        if (lastTime != null && (now - lastTime) < DEDUP_WINDOW_MS)
        {
            return;
        }
        lastProcessedTime.put(actor, now);

        String source = getRecoilSource();
        if (source == null)
        {
            return;
        }

        if (!inCombat)
        {
            startNewFight(actor);
        }
        else
        {
            ticksWithoutCombat = 0;
        }

        recordRecoil(amount);
        currentTarget = actor;

        pendingRecoilAmount = 0;
        pendingIsEchoBoots = false;

        if (lastProcessedTime.size() > 50)
        {
            lastProcessedTime.entrySet().removeIf(entry -> (now - entry.getValue()) > DEDUP_WINDOW_MS * 3);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        long now = Instant.now().toEpochMilli();

        if (pendingRecoilAmount > 0 && now - lastPlayerHitTime > DETECTION_WINDOW_MS)
        {
            pendingRecoilAmount = 0;
            pendingIsEchoBoots = false;
        }

        if (inCombat)
        {
            checkCombatState();
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        Actor actor = event.getActor();
        if (actor != null && (actor.equals(currentTarget) || actor.equals(client.getLocalPlayer())))
        {
            endCurrentFight();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();
        if (npc != null && npc.equals(currentTarget))
        {
            endCurrentFight();
        }
    }

    private void checkCombatState()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            endCurrentFight();
            return;
        }

        Actor interacting = localPlayer.getInteracting();

        if (interacting != null && interacting.equals(currentTarget))
        {
            ticksWithoutCombat = 0;
            return;
        }

        if (currentTarget != null)
        {
            Actor targetInteracting = currentTarget.getInteracting();
            if (targetInteracting != null && targetInteracting.equals(localPlayer))
            {
                ticksWithoutCombat = 0;
                return;
            }
        }

        ticksWithoutCombat++;

        if (ticksWithoutCombat >= COMBAT_END_TICKS)
        {
            endCurrentFight();
        }
    }

    private void startNewFight(Actor target)
    {
        if (!inCombat && currentFightTotal > 0)
        {
            lastFightTotal = currentFightTotal;
            currentFightTotal = 0;
        }

        if (inCombat && target != currentTarget)
        {
            endCurrentFight();
        }

        currentTarget = target;
        inCombat = true;
        ticksWithoutCombat = 0;

        updatePanel();
    }

    private void endCurrentFight()
    {
        if (!inCombat)
        {
            return;
        }

        lastFightTotal = currentFightTotal;
        inCombat = false;
        ticksWithoutCombat = 0;
        pendingRecoilAmount = 0;
        pendingIsEchoBoots = false;
        lastProcessedTime.clear();

        updatePanel();
    }

    private void recordRecoil(int amount)
    {
        currentFightTotal += amount;
        lastRecoilValue = amount;

        updatePanel();
    }

    private void updatePanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null)
            {
                panel.update();
            }
        });
    }

    private String getRecoilSource()
    {
        boolean hasRecoil = false;
        boolean hasSuffering = false;
        boolean hasEcho = false;

        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment != null)
        {
            for (var item : equipment.getItems())
            {
                if (item == null || item.getId() <= 0)
                {
                    continue;
                }

                ItemComposition comp = itemManager.getItemComposition(item.getId());
                if (comp == null)
                {
                    continue;
                }

                String name = comp.getName();
                if (name == null)
                {
                    continue;
                }

                String lower = name.toLowerCase();
                if (lower.contains("ring of suffering"))
                {
                    hasSuffering = true;
                }
                else if (lower.contains("ring of recoil"))
                {
                    hasRecoil = true;
                }
                else if (lower.contains("echo boots"))
                {
                    hasEcho = true;
                }
            }
        }

        if (hasSuffering) return "Ring of Suffering";
        if (hasRecoil) return "Ring of Recoil";
        if (hasEcho) return "Echo Boots";

        return null;
    }

    public void resetCurrentFight()
    {
        currentFightTotal = 0;
        lastFightTotal = 0;
        lastRecoilValue = 0;
        currentTarget = null;
        inCombat = false;
        ticksWithoutCombat = 0;
        pendingRecoilAmount = 0;
        pendingIsEchoBoots = false;
        lastProcessedTime.clear();

        updatePanel();
    }

    public int getCurrentFightTotal()
    {
        return inCombat ? currentFightTotal : lastFightTotal;
    }

    public int getLastRecoilValue()
    {
        return lastRecoilValue;
    }

    public RecoilTrackerConfig getConfig()
    {
        return configManager.getConfig(RecoilTrackerConfig.class);
    }

    public Actor getCurrentTarget()
    {
        return currentTarget;
    }

    public boolean isInCombat()
    {
        return inCombat;
    }
}