package com.example.blockmod.handler;

import com.example.blockmod.config.Config;
import com.example.blockmod.logic.GuardFormulas;
import com.example.blockmod.logic.StaminaService;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-side per-tick stamina driver (Spec §5.3.1, order is normative):
 * 0. creative/spectator exemption (stamina economy only — combat state machines
 * in 2/2.5/3 still run) → 1. power guard drain → 2. parry window expiry
 * → 2.5 container-exit guard drop → (3. shield bash tick, M5) → 4. regen
 * → 5. depletion edge side effects → 6. throttled sync. Depletion is judged
 * after deductions, so the hit that crosses zero is still blocked (FR-04
 * acceptance 6, resolved in M3).
 */
@EventBusSubscriber(modid = com.example.blockmod.BlockMod.MODID)
public final class PlayerTickHandler {
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tick(player);
    }

    public static void tick(ServerPlayer player) {
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        long now = player.level().getGameTime();

        // 0. creative / spectator exemption (FR-26) — stamina ECONOMY only. The
        // combat state machines (parry expiry, container exit, bash resolution)
        // must still run: skipping them left a creative player's armed bash
        // windup unresolved forever (2026-09-11 bug report).
        if (!Config.affectCreative() && (player.isCreative() || player.isSpectator())) {
            if (stamina.stamina() != Config.maxStamina()) {
                stamina.setStamina(Config.maxStamina());
                SyncThrottler.forceSync(player);
            }
            tickCombatState(player, guardState, now);
            return;
        }

        // 1. power guard continuous drain (deduct first: this tick may already enter depletion)
        if (guardState.isPowerGuarding() && guardState.isGuarding()) {
            // FR-16 (2026-08-30 ruling): drain = max x percent + flat, per second; the max is
            // read per tick so a future dynamic maximum is honoured automatically
            float drain = GuardFormulas.powerGuardDrainPerSecond(Config.maxStamina(),
                    Config.pgStaminaDrainPercent(), Config.pgStaminaDrainFlat()) / 20.0f;
            stamina.setStamina(stamina.stamina() - drain);
            if (stamina.stamina() <= 0f) {
                com.example.blockmod.logic.PowerGuardService.disarm(player, guardState, now); // FR-16: PG closes itself on depletion
            } else if (Config.pgDisableJump()) {
                // §5.7: jump clamp — upward velocity is removed each tick while PG holds
                double y = player.getDeltaMovement().y;
                player.setDeltaMovement(0.0, Math.min(y, 0.0), 0.0);
            }
        }

        tickCombatState(player, guardState, now);

        // 4. regeneration (FR-02 three-branch selection)
        StaminaService.applyRegenTick(player, stamina, guardState, now);

        // 5. depletion edge side effects (exactly once per zero crossing, ADR-15)
        if (StaminaService.depletionEdgeFlipped(guardState, stamina.stamina())) {
            StaminaService.refreshDepletedState(player, guardState, guardState.wasDepleted());
            SyncThrottler.forceSync(player);
        }

        // 6. throttled sync (FR-23)
        SyncThrottler.maybeSync(player, now);
    }

    /**
     * Steps 2 / 2.5 / 3 of the normative order — the combat state machines
     * (parry window expiry, container-exit guard drop, shield bash resolution).
     * Deliberately outside the FR-26 stamina exemption: they are not economy.
     */
    private static void tickCombatState(ServerPlayer player, GuardStateData guardState, long now) {
        // 2. parry window expiry
        if (guardState.parryWindowEndTick() >= 0 && now >= guardState.parryWindowEndTick()) {
            guardState.setParryWindowEndTick(-1L);
        }

        // 2.5 E-07: opening a container exits the guard immediately (malus removed).
        if (guardState.isGuarding() && player.containerMenu != player.inventoryMenu) {
            guardState.setGuarding(false);
            com.example.blockmod.logic.MovementService.remove(player, guardState);
            com.example.blockmod.logic.PowerGuardService.disarm(player, guardState, now); // §5.7: PG ends with the guard
            SyncThrottler.forceSync(player);
            com.example.blockmod.BlockModLogger.info("GUARD_INPUT", "action", "container_exit",
                    "player", player.getGameProfile().getName());
        }

        // 3. shield bash windup/cooldown resolution (FR-15 / Spec §5.6)
        com.example.blockmod.logic.ShieldBashService.tick(player, guardState, now);
    }

    private PlayerTickHandler() {}
}
