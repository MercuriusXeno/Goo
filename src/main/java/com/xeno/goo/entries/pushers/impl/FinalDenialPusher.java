package com.xeno.goo.entries.pushers.impl;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.GooEntry;
import com.xeno.goo.entries.EntryPhase;
import com.xeno.goo.entries.pushers.EntryPusher;
import net.minecraft.world.server.ServerWorld;

import java.util.Map;

import static com.xeno.goo.library.GooEntry.DENIED;
import static com.xeno.goo.library.EntryHelper.name;

/**
 * Ground zero map pusher, sets all items equal to unknown so there are no missing keys.
 */
public class FinalDenialPusher extends EntryPusher
{
    public FinalDenialPusher(ServerWorld world)
    {
        super(EntryPhase.FINAL, "", world);
    }

    @Override
    protected boolean load()
    {
        return true;
    }

    @Override
    protected void save()
    {
        // NO OP
    }

    @Override
    public void process()
    {
        for(Map.Entry<String, GooEntry> e : GooMod.mappingHandler.values().entrySet()) {
            if (e.getValue().isUnknown()) {
                GooMod.debug("Unknown mapping will be denied: " + e.getKey());
                values.put(e.getKey(), DENIED);
            }
        }
        this.resolve();
    }

    @Override
    protected void clearProcessing()
    {
        // NO OP
    }

    @Override
    protected void seedDefaults()
    {
        // NO OP
    }
}
