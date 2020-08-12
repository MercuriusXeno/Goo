package com.xeno.goo.evaluations;

import com.xeno.goo.evaluations.pushers.SolvedState;
import com.xeno.goo.evaluations.pushers.EntryPusher;
import com.xeno.goo.network.GooValueSyncPacket;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.server.ServerWorld;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.xeno.goo.library.Compare.*;
import static com.xeno.goo.evaluations.GooEntry.*;
import static com.xeno.goo.evaluations.pushers.SolvedState.SOLVED;
import static com.xeno.goo.evaluations.pushers.SolvedState.UNSOLVED;


public class EntryHandler
{
    private Map<String, GooEntry> values = new TreeMap<>(stringLexicographicalComparator);
    private final Map<Class<? extends EntryPusher>, EntryPusher> pushers = new HashMap<>();

    // registers a mapping pusher with the handler which is what enables it to use it for
    // creating goo equivalencies.
    public void register(EntryPusher mappingPusher)
    {
        pushers.put(mappingPusher.getClass(), mappingPusher);
    }

    private static final EntryPhase[] INITIALIZE_PHASE_ORDERED = new EntryPhase[] {
            EntryPhase.INITIALIZE,
            EntryPhase.BASELINE,
            EntryPhase.DENIED,
    };

    // try to pull from a final file, but if it's unsolved, regenerate the mappings.
    public void reloadEntries(@Nonnull ServerWorld world, boolean isFactoryReset, boolean isRegenerating)
    {
        if (isFactoryReset || isRegenerating) {
            values.clear();
        }

        for (EntryPhase phase : INITIALIZE_PHASE_ORDERED) {
            List<EntryPusher> initializerPushers = pushers.values().stream()
                    .filter(p -> p.phase == phase)
                    .collect(Collectors.toList());
            for (EntryPusher initializerPusher : initializerPushers) {
                // initializers re-read on regen, but rewrite on factory reset
                initializerPusher.initialize(isFactoryReset, isRegenerating);
                initializerPusher.process();
                initializerPusher.pushTo(values);
            }
        }

        File finalEntryFile = FileHelper.openWorldFile(world, "goo-mappings-final.json");

        // only read from final mappings if we're not forcing regeneration.
        if (!isFactoryReset && !isRegenerating) {
            Map<String, GooEntry> finalValues = FileHelper.readEntryFile(finalEntryFile);

            EntryHelper.trackedPush(finalValues, values);
        }

        SolvedState solvedState = solvedStateOf(values);

        if (solvedState == UNSOLVED) {
            regenerateEntries(isFactoryReset, isRegenerating);
            FileHelper.writeEntryFile(finalEntryFile, values);
        }
    }

    /**
     * An ordered array of phases we want to iterate over, repeatedly, until the progress state stagnates.
     *
     * Note that "FINAL" is excluded, deliberately. We only call final when the result is stagnant!
     * Note that "BASELINE", "DENIED", "INITIALIZE" are excluded, deliberately.
     * We call initialize early so we can test the solved state of our final
     * mapping file before deciding whether it needs tuning.
     */
    private static final EntryPhase[] DERIVATIVE_PHASE_ORDERED = new EntryPhase[] {
            EntryPhase.DERIVED,
            EntryPhase.DEFERRED
    };

    private void regenerateEntries(boolean isFactoryReset, boolean isRegenerating) {
        // initialize pushers to an unresolved, freshly loaded state with no implicit values.
        // note, some pushers do not resolve. resolution is a way to prevent repeat calculations/loads
        ProgressState outerState = ProgressState.IMPROVED;
        ProgressState innerState;
        while (outerState == ProgressState.IMPROVED) {
            outerState = ProgressState.STAGNANT;
            for (EntryPhase phase : DERIVATIVE_PHASE_ORDERED) {
                // assume inner state isn't stagnant yet.
                innerState = ProgressState.IMPROVED;
                List<EntryPusher> phasePushers = pushers.values().stream()
                        .filter(mappingPusher -> mappingPusher.phase == phase && (!mappingPusher.isResolved() || isFactoryReset))
                        .collect(Collectors.toList());
                while (innerState == ProgressState.IMPROVED) {
                    innerState = ProgressState.STAGNANT;
                    for (EntryPusher pusher : phasePushers) {
                        pusher.initialize(isFactoryReset, isRegenerating);
                        pusher.process();
                        ProgressState runState = pusher.pushTo(values);
                        if (runState == ProgressState.IMPROVED) {
                            innerState = ProgressState.IMPROVED;
                            outerState = ProgressState.IMPROVED;
                        }
                    }
                }
            }
        }

        // call final pushers on the now stagnant map. Finals run once, so they'd better live up to their name.
        pushers.values().stream()
                .filter(p -> p.phase == EntryPhase.FINAL)
                .forEach(p -> {
                    p.initialize(isFactoryReset, isRegenerating);
                    p.process();
                    p.pushTo(values);
                });

        scanForExploitsAndReport();
    }

    private void scanForExploitsAndReport()
    {
        // STUB
        // todo scan recipes for exploit loops or disparate quantity yields?
    }

    private SolvedState solvedStateOf(Map<String, GooEntry> values) {
        return values.entrySet().stream().noneMatch(v -> v.getValue().isUnknown()) ? SOLVED : UNSOLVED;
    }

    public GooValueSyncPacket createPacketData()
    {
        return new GooValueSyncPacket(this.values);
    }

    public void fromPacket(Map<String, GooEntry> data) {
        this.values = data;
    }

    public GooEntry get(ItemStack stack) { return get(stack.getItem()); }

    public GooEntry get(Item item) {
        return get(Objects.requireNonNull(item.getRegistryName()).toString());
    }

    public GooEntry get(String registryName)
    {
        if (values.containsKey(registryName)) {
            return values.get(registryName);
        }

        return UNKNOWN;
    }

    public boolean has(ItemStack stack)
    {
        return has(stack.getItem());
    }

    public boolean has(Item item)
    {
        return has(Objects.requireNonNull(item.getRegistryName()).toString());
    }

    public boolean has(String string) {
        return values.containsKey(string);
    }

    public Map<String, GooEntry> values()
    {
        return this.values;
    }
}
