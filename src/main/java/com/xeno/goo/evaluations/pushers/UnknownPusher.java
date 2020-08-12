package com.xeno.goo.evaluations.pushers;

import com.xeno.goo.evaluations.EntryPhase;
import com.xeno.goo.evaluations.pushers.EntryPusher;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

import static com.xeno.goo.evaluations.GooEntry.UNKNOWN;
import static com.xeno.goo.evaluations.EntryHelper.name;

/**
 * Ground zero map pusher, sets all items equal to unknown so there are no missing keys.
 */
public class UnknownPusher extends EntryPusher
{
    public UnknownPusher(ServerWorld world)
    {
        super(EntryPhase.INITIALIZE, "", world);
    }

    @Override
    protected boolean load()
    {
        return false;
    }

    @Override
    protected void save()
    {
        // NO OP
    }

    @Override
    public void process()
    {
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
        ForgeRegistries.ITEMS.getValues().forEach(i -> values.put(name(i), values.getOrDefault(name(i), UNKNOWN)));
    }
}
