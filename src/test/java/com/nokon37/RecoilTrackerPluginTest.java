package com.nokon37;

import net.runelite.client.externalplugins.ExternalPluginManager;
import com.nokon37.recoiltracker.RecoilTrackerPlugin;
import org.junit.Test;

public class RecoilTrackerPluginTest
{
    @Test
    public void testLoad() throws Exception
    {
        ExternalPluginManager.loadBuiltin(RecoilTrackerPlugin.class);
    }
}