package com.example.blockmod.logic;

import com.example.blockmod.BlockMod;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.state.GuardStateData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * FR-17: mounts and removes the transient guarding move-speed penalty on
 * {@code Attributes.MOVEMENT_SPEED} ({@code ADD_MULTIPLIED_TOTAL}).
 *
 * <p>One fixed modifier id is used for every shield class — re-adding a modifier
 * with the same id replaces the previous one, so switching shield types is a
 * plain re-apply. Transient modifiers never persist, guaranteeing no residue
 * after a crash or restart (FR-17 acceptance 6).
 */
public final class MovementService {
    private static final ResourceLocation MALUS_ID =
            ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard_move_malus");

    /** FR-17 decision table (v2.0): mount only while guarding with stamina left and an actual penalty. */
    public static boolean shouldApplyMalus(boolean guarding, boolean staminaPositive, float moveSpeedMalus) {
        return guarding && staminaPositive && moveSpeedMalus != 0f;
    }

    /** Mounts (or updates) the penalty for the given profile; a no-op for zero-malus profiles like swords. */
    public static void apply(ServerPlayer player, GuardStateData guardState, GuardProfile profile) {
        if (!shouldApplyMalus(guardState.isGuarding(), player.getData(ModAttachments.STAMINA_RESOLVER.get()).canDefend(), profile.moveSpeedMalus())) {
            return;
        }
        mount(player, profile.moveSpeedMalus());
        guardState.setActiveMoveMalusId(MALUS_ID);
    }

    /** Removes the penalty if it is mounted; safe to call repeatedly. */
    public static void remove(ServerPlayer player, GuardStateData guardState) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.removeModifier(MALUS_ID)) {
            BlockModLogger.info("MOVE_MALUS", "action", "removed", "player", player.getGameProfile().getName());
        }
        guardState.setActiveMoveMalusId(null);
    }

    /** Mounts the modifier unconditionally (used by the depletion-edge remount after FR-04 exit). */
    private static void mount(ServerPlayer player, float moveSpeedMalus) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(MALUS_ID);
        speed.addTransientModifier(new AttributeModifier(MALUS_ID, moveSpeedMalus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        BlockModLogger.info("MOVE_MALUS", "action", "mounted", "player", player.getGameProfile().getName(),
                "malus", moveSpeedMalus);
    }

    private MovementService() {}
}
