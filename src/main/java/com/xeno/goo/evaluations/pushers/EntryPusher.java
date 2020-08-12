package com.xeno.goo.evaluations.pushers;

import com.xeno.goo.evaluations.*;
import com.xeno.goo.library.*;
import net.minecraft.world.server.ServerWorld;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

public abstract class EntryPusher
{
    private final File file;
    private boolean isResolved;
    private boolean isInitialized;
    protected File file() {
        return this.file;
    }

    protected EntryPusher(EntryPhase phase, String fileName, ServerWorld world) {
        this.phase = phase;
        this.file =  FileHelper.openWorldFile(world, fileName);
        this.isInitialized = false;
    }

    public void initialize(boolean isFactoryReset, boolean isRegenerating) {
        if (!isFactoryReset && !isRegenerating && isInitialized) {
            return;
        }
        values = new TreeMap<>(Compare.stringLexicographicalComparator);
        clearProcessing();
        // don't rewrite on regen, just on factory reset
        if (isFactoryReset || !load()) {
            seedDefaults();
            save();
        }
        isResolved = false;
        isInitialized = true;
    }

    protected abstract void clearProcessing();

    public void resolve() {
        this.isResolved = true;
    }

    public boolean isResolved() {
        return this.isResolved;
    }

    /**
     * Must-override method which is called on initialization.
     * If there is no file and no seed defaults, return true.
     * If there is a file, return true if the file apparently loaded.
     * If there is no file, but needs seed defaults, return false to force a seed and then save can be NO OP.
     * @return true if we need to seed defaults and/or save.
     */
    protected abstract boolean load();

    /**
     * Called when the abstraction thinks it's appropriate timing to save the file (after seeding defaults)
     * It's perfectly normal to not save anything (NO OP) when your pusher doesn't need to make a file.
     */
    protected abstract void save();

    public final EntryPhase phase;

    public Map<String, GooEntry> values;

    public ProgressState pushTo(Map<String, GooEntry> target) { return EntryHelper.trackedPush(this.values, target); }

    /**
     * When seeding the values has to occur in a deferred state (or strictly after something has fired)
     * mappings may need to be "processed" before pushTo should be invoked. Use this method to defer
     * mapping invocation when the mappings are derivative or deferred, or depend on other mappings.
     */
    public abstract void process();

    /**
     * Called when the mapping file can't be loaded, which is then used to create the mapping file.
     */
    protected abstract void seedDefaults();
}
