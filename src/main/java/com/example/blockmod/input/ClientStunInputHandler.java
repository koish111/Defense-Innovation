package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.client.player.Input;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Client-side half of the stun lockdown (FR-05).
 *
 * <p>Vanilla player movement is client-authoritative: the local player's travel()
 * rebuilds its velocity from keyboard input every tick, and the server accepts the
 * resulting move packets. {@code StunHandler}'s delta-movement zeroing alone therefore
 * only produces slow drift / low jumps for players. Zeroing the input here removes
 * the movement, the jump impulse and the sprint state (sprint requires forward
 * impulse, so the sprint FOV effect also disappears) at the source.
 *
 * <p>Server entities (parried mobs) stay covered by {@code StunHandler}; a hacked
 * client could still send move packets while stunned — vanilla's trust model, out
 * of MVP scope.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ClientStunInputHandler {
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!event.getEntity().hasEffect(ModEffects.STUN)) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
    }

    private ClientStunInputHandler() {}
}
