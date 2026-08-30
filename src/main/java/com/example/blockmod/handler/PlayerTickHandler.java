package com.example.blockmod.handler;

import com.example.blockmod.config.Config;
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
 * 0. creative/spectator exemption → 1. power guard drain → 2. parry window
 * expiry → (3. shield bash tick, M5) → 4. regen → 5. depletion edge side
 * effects → 6. throttled sync. Depletion is judged after deductions, so the
 * hit that crosses zero is still blocked (FR-04 acceptance 6, resolved in M3).
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

        // 0. creative / spectator exemption (FR-26)
        if (!Config.affectCreative() && (player.isCreative() || player.isSpectator())) {
            if (stamina.stamina() != Config.maxStamina()) {
                stamina.setStamina(Config.maxStamina());
                SyncThrottler.forceSync(player);
            }
            return;
        }

        // 1. power guard continuous drain (deduct first: this tick may already enter depletion)
        if (guardState.isPowerGuarding() && guardState.isGuarding()) {
            float drain = Config.maxStamina() * Config.pgStaminaDrainPercent() / 100.0f / 20.0f;
            stamina.setStamina(stamina.stamina() - drain);
            if (stamina.stamina() <= 0f) {
                guardState.setPowerGuarding(false); // FR-16: PG closes itself on depletion
            } else if (Config.pgDisableJump()) {
                // §5.7: jump clamp — upward velocity is removed each tick while PG holds
                double y = player.getDeltaMovement().y;
                player.setDeltaMovement(0.0, Math.min(y, 0.0), 0.0);
            }
        }

        // 2. parry window expiry
        if (guardState.parryWindowEndTick() >= 0 && now >= guardState.parryWindowEndTick()) {
            guardState.setParryWindowEndTick(-1L);
        }

        // 2.5 E-07: opening a container exits the guard immediately (malus removed).
        if (guardState.isGuarding() && player.containerMenu != player.inventoryMenu) {
            guardState.setGuarding(false);
            com.example.blockmod.logic.MovementService.remove(player, guardState);
            com.example.blockmod.logic.PowerGuardService.disarm(player, guardState); // §5.7: PG ends with the guard
            SyncThrottler.forceSync(player);
            com.example.blockmod.BlockModLogger.info("GUARD_INPUT", "action", "container_exit",
                    "player", player.getGameProfile().getName());
        }

        // 3. shield bash windup/cooldown resolution (FR-15 / Spec §5.6)
        com.example.blockmod.logic.ShieldBashService.tick(player, guardState, now);

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

    private PlayerTickHandler() {}
}
