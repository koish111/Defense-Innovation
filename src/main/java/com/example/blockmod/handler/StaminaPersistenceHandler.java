package com.example.blockmod.handler;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * T-20 / FR-24: persistence wiring around the clone/login lifecycle.
 *
 * <ul>
 *   <li>Death and dimension change both pass through {@link PlayerEvent.Clone};
 *       the stamina attachment itself does not copy on death (no {@code copyOnDeath}),
 *       so the config decides: reset to max (default) or carry the value over.</li>
 *   <li>{@code wasDepleted} of the fresh guard-state attachment is aligned with the
 *       carried stamina, otherwise the depletion cue would replay after a dimension
 *       change on negative stamina (§9.4 checklist #28).</li>
 *   <li>Login aligns the same edge state and forces an initial sync so the HUD is
 *       correct before the first throttled tick.</li>
 *   <li>Logout drops the sync bookkeeping (bounded-cache hygiene).</li>
 * </ul>
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class StaminaPersistenceHandler {
    @SubscribeEvent
    static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)
                || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }
        StaminaData newStamina = newPlayer.getData(ModAttachments.STAMINA.get());
        boolean resetOnDeath = event.isWasDeath() && Config.resetStaminaOnDeath();
        boolean resetOnDimension = !event.isWasDeath() && Config.resetStaminaOnDimensionChange();
        if (resetOnDeath || resetOnDimension) {
            newStamina.setStamina(Config.maxStamina());
            newStamina.setLastEventTick(0L);
        } else {
            // The original's attachments are still alive here at Clone time.
            StaminaData oldStamina = original.getData(ModAttachments.STAMINA.get());
            newStamina.setStamina(oldStamina.stamina());
            newStamina.setLastEventTick(oldStamina.lastEventTick());
        }
        // Align the depletion edge with the carried value: no cue replay after travel.
        newPlayer.getData(ModAttachments.GUARD_STATE.get()).setWasDepleted(newStamina.isDepleted());
        BlockModLogger.info("PERSIST", "phase", event.isWasDeath() ? "death" : "dimension",
                "stamina", newStamina.stamina());
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        player.getData(ModAttachments.GUARD_STATE.get()).setWasDepleted(stamina.isDepleted());
        SyncThrottler.forceSync(player);
    }

    @SubscribeEvent
    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SyncThrottler.clear(player.getUUID());
        }
    }

    private StaminaPersistenceHandler() {}
}
