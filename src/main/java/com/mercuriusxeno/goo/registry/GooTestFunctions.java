package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.gametest.CanisterInteractionTests;
import com.mercuriusxeno.goo.gametest.EffectExecutorTests;
import com.mercuriusxeno.goo.gametest.GasketHolderTests;
import com.mercuriusxeno.goo.gametest.GasketPusherTests;
import com.mercuriusxeno.goo.gametest.MachineTests;
import com.mercuriusxeno.goo.gametest.MobEffectTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Registers goo gametest functions via NeoForge's RegisterEvent.
 * TestFunctionLoader.runLoaders() fires during Bootstrap.bootStrap(),
 * before mod loading, so we use RegisterEvent instead to register
 * into the TEST_FUNCTION registry at the correct time.
 */
public final class GooTestFunctions {

    // --- Smoke ---
    private static final String SMOKE = "smoke";

    // --- GasketPusher ---
    private static final String PUSHER_EMPTY_RESERVOIR = "pusher_empty_reservoir";
    private static final String PUSHER_NO_PARTNER = "pusher_no_partner";
    private static final String PUSHER_DISPOSE_AND_TICK = "pusher_dispose_and_tick";
    private static final String PUSHER_DOUBLE_DISPOSE = "pusher_double_dispose";

    // --- IGasketHolder ---
    private static final String CRUCIBLE_ROLE_TRANSMITTER = "crucible_role_transmitter";
    private static final String CRUCIBLE_NO_GASKET = "crucible_no_gasket";
    private static final String CRUCIBLE_WITH_GASKET = "crucible_with_gasket";
    private static final String VAT_SUPPORTS_ROLE = "vat_supports_role";
    private static final String HUB_HAS_INTAKE = "hub_has_intake";
    private static final String DEFAULT_ALLOWS_TUNING = "default_allows_tuning";

    // --- Effect executors ---
    private static final String FX_BLAZE = "fx_blaze_mines";
    private static final String FX_ROCK = "fx_rock_mines";
    private static final String FX_FROST = "fx_frost_runs";
    private static final String FX_METAL = "fx_metal_runs";
    private static final String FX_CRYSTAL = "fx_crystal_runs";
    private static final String FX_NETHER = "fx_nether_implodes";
    private static final String FX_UNSTABLE = "fx_unstable_explodes";
    private static final String FX_GLOW = "fx_glow_runs";

    // --- Canister interactions ---
    private static final String IX_CANISTER_SHIFT_INSERT = "ix_canister_shift_insert";
    private static final String IX_CANISTER_CLICK_PICKUP = "ix_canister_click_pickup";
    private static final String IX_CANISTER_LAST_PICKUP = "ix_canister_last_pickup";
    private static final String IX_CANISTER_EMPTY_HAND = "ix_canister_empty_hand";

    // --- Machines ---
    private static final String MACHINE_CANISTER_INSERT = "machine_canister_insert";
    private static final String MACHINE_CANISTER_REMOVE = "machine_canister_remove";
    private static final String MACHINE_CANISTER_TICK = "machine_canister_tick";
    private static final String MACHINE_CANISTER_BREAK = "machine_canister_break";
    private static final String MACHINE_REACTOR_IDLE = "machine_reactor_idle";
    private static final String MACHINE_REACTOR_BREAK = "machine_reactor_break";
    private static final String MACHINE_PLEXER_IDLE = "machine_plexer_idle";

    // --- MobEffects ---
    private static final String MOB_METAL = "mob_metal_javelin";
    private static final String MOB_CRYSTAL = "mob_crystal_flechettes";
    private static final String MOB_LEAF = "mob_leaf_entangle";
    private static final String MOB_VITAL = "mob_vital_clone";
    private static final String MOB_SHROOM = "mob_shroom_debuff";
    private static final String MOB_ROCK = "mob_rock_petrify";
    private static final String MOB_BLAZE = "mob_blaze_ignite";
    private static final String MOB_FROST = "mob_frost_snap";
    private static final String MOB_TYPHOON = "mob_typhoon_levitate";
    private static final String MOB_GLOW = "mob_glow_laser";
    private static final String MOB_HEX = "mob_hex_charm";
    private static final String MOB_PULSE = "mob_pulse_stun";
    private static final String MOB_NETHER = "mob_nether_wither";
    private static final String MOB_ENDER = "mob_ender_teleport";
    private static final String MOB_UNSTABLE = "mob_unstable_explode";
    private static final String MOB_AEON = "mob_aeon_time_stop";
    private static final String MOB_DISPATCHER = "mob_dispatcher_routes";

    private GooTestFunctions() {}

    /**
     * Subscribes the registration listener to the mod event bus.
     * Call once during mod construction.
     *
     * @param modEventBus the mod event bus
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(GooTestFunctions::onRegister);
    }

    /**
     * Registers all gametest functions into the TEST_FUNCTION registry.
     *
     * @param event the register event
     */
    private static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registrar -> {
            reg(registrar, SMOKE, h -> h.succeed());
            registerGasketTests(registrar);
            registerEffectExecutorTests(registrar);
            registerCanisterInteractionTests(registrar);
            registerMachineTests(registrar);
            registerMobEffectTests(registrar);
        });
    }

    private static void registerGasketTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, PUSHER_EMPTY_RESERVOIR, GasketPusherTests::emptyReservoirSkipsTick);
        reg(r, PUSHER_NO_PARTNER, GasketPusherTests::noPartnerSkipsTick);
        reg(r, PUSHER_DISPOSE_AND_TICK, GasketPusherTests::disposeAndTickIsSafe);
        reg(r, PUSHER_DOUBLE_DISPOSE, GasketPusherTests::doubleDisposeIsSafe);
        reg(r, CRUCIBLE_ROLE_TRANSMITTER, GasketHolderTests::crucibleResolveRoleAlwaysTransmitter);
        reg(r, CRUCIBLE_NO_GASKET, GasketHolderTests::crucibleNoGasketUnsupported);
        reg(r, CRUCIBLE_WITH_GASKET, GasketHolderTests::crucibleWithGasketSupported);
        reg(r, VAT_SUPPORTS_ROLE, GasketHolderTests::vatSupportsRoleMatchesBlockstate);
        reg(r, HUB_HAS_INTAKE, GasketHolderTests::hubHasIntake);
        reg(r, DEFAULT_ALLOWS_TUNING, GasketHolderTests::defaultAllowsTuningIsTrue);
    }

    private static void registerEffectExecutorTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, FX_BLAZE, EffectExecutorTests::blazeMinesBlock);
        reg(r, FX_ROCK, EffectExecutorTests::rockMinesBlock);
        reg(r, FX_FROST, EffectExecutorTests::frostRuns);
        reg(r, FX_METAL, EffectExecutorTests::metalRuns);
        reg(r, FX_CRYSTAL, EffectExecutorTests::crystalRuns);
        reg(r, FX_NETHER, EffectExecutorTests::netherImplodes);
        reg(r, FX_UNSTABLE, EffectExecutorTests::unstableExplodes);
        reg(r, FX_GLOW, EffectExecutorTests::glowRuns);
    }

    private static void registerCanisterInteractionTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, IX_CANISTER_SHIFT_INSERT, CanisterInteractionTests::shiftClickInserts);
        reg(r, IX_CANISTER_CLICK_PICKUP, CanisterInteractionTests::clickPicksUp);
        reg(r, IX_CANISTER_LAST_PICKUP, CanisterInteractionTests::lastPickupRemovesBlock);
        reg(r, IX_CANISTER_EMPTY_HAND, CanisterInteractionTests::emptyHandPicksUp);
    }

    private static void registerMachineTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, MACHINE_CANISTER_INSERT, MachineTests::canisterInsertCreatesHandler);
        reg(r, MACHINE_CANISTER_REMOVE, MachineTests::canisterRemoveClearsHandler);
        reg(r, MACHINE_CANISTER_TICK, MachineTests::canisterTicksWithSlot);
        reg(r, MACHINE_CANISTER_BREAK, MachineTests::canisterBreakWithSlotIsSafe);
        reg(r, MACHINE_REACTOR_IDLE, MachineTests::reactorIdleTick);
        reg(r, MACHINE_REACTOR_BREAK, MachineTests::reactorBreakIsSafe);
        reg(r, MACHINE_PLEXER_IDLE, MachineTests::plexerIdleTick);
    }

    private static void registerMobEffectTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, MOB_METAL, MobEffectTests::metalJavelin);
        reg(r, MOB_CRYSTAL, MobEffectTests::crystalFlechettes);
        reg(r, MOB_LEAF, MobEffectTests::leafEntangle);
        reg(r, MOB_VITAL, MobEffectTests::vitalClone);
        reg(r, MOB_SHROOM, MobEffectTests::shroomDebuff);
        reg(r, MOB_ROCK, MobEffectTests::rockPetrify);
        reg(r, MOB_BLAZE, MobEffectTests::blazeIgnite);
        reg(r, MOB_FROST, MobEffectTests::frostSnap);
        reg(r, MOB_TYPHOON, MobEffectTests::typhoonLevitate);
        reg(r, MOB_GLOW, MobEffectTests::glowLaser);
        reg(r, MOB_HEX, MobEffectTests::hexCharm);
        reg(r, MOB_PULSE, MobEffectTests::pulseStun);
        reg(r, MOB_NETHER, MobEffectTests::netherWither);
        reg(r, MOB_ENDER, MobEffectTests::enderTeleport);
        reg(r, MOB_UNSTABLE, MobEffectTests::unstableExplode);
        reg(r, MOB_AEON, MobEffectTests::aeonTimeStop);
        reg(r, MOB_DISPATCHER, MobEffectTests::dispatcherRoutes);
    }

    /**
     * Registers a single test function in the goo namespace.
     *
     * @param registrar the registry registrar
     * @param name      the function name
     * @param fn        the test function
     */
    private static void reg(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar,
                            String name, Consumer<GameTestHelper> fn) {
        registrar.register(Identifier.fromNamespaceAndPath(Goo.MODID, name), fn);
    }
}
