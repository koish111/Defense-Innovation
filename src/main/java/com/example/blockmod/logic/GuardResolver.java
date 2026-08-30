package com.example.blockmod.logic;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.DamageClass;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.logic.GuardEquipmentResolver.GuardEquipment;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * T-24: the guard arbitration entry point (Spec §5.4).
 *
 * <p>{@link LivingIncomingDamageEvent} runs at {@link EventPriority#LOWEST}: the M0
 * probe proved higher priorities run FIRST in NeoForge 21.1, so LOWEST is the only
 * point where {@code getContainer().getNewDamage()} reflects other mods' changes
 * (API-03). A successful guard cancels the event — M0 also proved the vanilla
 * shield path never runs after a cancellation, so there is no double mitigation
 * from our own handling (API-01).
 *
 * <p>The double insurance (§5.4.5) lives in {@link LivingShieldBlockEvent}: when
 * the vanilla shield WOULD block a mod-managed player (held shield, not cancelled
 * by us — e.g. depleted or back-stab), it is forced off so the mod's verdict is
 * the only one that counts.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class GuardResolver {
    /** E-10: re-entrancy lock — a cancelled event must not re-enter the same judgement. */
    private static final Set<UUID> REENTRANCY = new HashSet<>();
    /** Players whose successful explosion block this tick halves the knockback (T-28). */
    private static final Set<UUID> EXPLOSION_BLOCKED = new HashSet<>();

    // ------------------------------------------------------------------
    // event entry points

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return; // step 1: only real players guard
            }
            if (REENTRANCY.contains(player.getUUID())) {
                return; // step 3 (E-10)
            }
            GuardContext ctx = buildContext(player, event.getSource(), event.getAmount());
            int result = GuardRules.resolveGuard(ctx.hasProfile(), ctx.guarding(), ctx.staminaPositive(),
                    ctx.frontal(), ctx.damageClass().ordinal(), ctx.inParryWindow());
            if (Config.verboseLogging() && result != GuardRules.RESULT_GUARDED) {
                BlockModLogger.info("GUARD", "result", result, "source", event.getSource().getMsgId(),
                        "class", com.example.blockmod.logic.DamageClassifier.classify(event.getSource()));
            }
            if (result == GuardRules.RESULT_PARRIED) {
                // Full parry semantics (deflect/stun/boss counter) land in M4 (T-31..T-34);
                // the M3 slice already guarantees the cost-free full cancel.
                REENTRANCY.add(player.getUUID());
                try {
                    event.setCanceled(true);
                    BlockModLogger.info("GUARD", "result", "PARRIED", "player", player.getGameProfile().getName());
                    SyncThrottler.forceSync(player);
                } finally {
                    REENTRANCY.remove(player.getUUID());
                }
                return;
            }
            if (result == GuardRules.RESULT_GUARDED) {
                REENTRANCY.add(player.getUUID());
                try {
                    event.setCanceled(true);
                    applyGuardCost(ctx);
                } finally {
                    REENTRANCY.remove(player.getUUID());
                }
                applyKnockbackReduction(ctx);
            }
        } catch (Throwable t) {
            // E-24: a guard bug must never make a player invulnerable — let the damage through.
            BlockModLogger.error("GUARD", "phase", "exception", "threw", t.toString());
        }
    }

    /** §5.4.5 double insurance: vanilla shield blocking never applies to mod-managed players. */
    @SubscribeEvent
    static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (GuardEquipmentResolver.resolve(player) != null) {
            event.setBlocked(false);
            event.setShieldDamage(0.0f);
        }
    }

    /** T-28: blocked explosions remove the knockback vector per {@code explosion_knockback_reduction}. */
    @SubscribeEvent
    static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (!(event.getAffectedEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (EXPLOSION_BLOCKED.remove(player.getUUID())) {
            float reduction = Config.explosionKnockbackReduction();
            event.setKnockbackVelocity(event.getKnockbackVelocity().scale(1.0f - reduction));
        }
    }

    /** Safety valve: stale explosion markers must never survive into the next tick. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!EXPLOSION_BLOCKED.isEmpty()) {
            EXPLOSION_BLOCKED.clear();
        }
    }

    // ------------------------------------------------------------------
    // context + arbitration

    private static GuardContext buildContext(ServerPlayer player, DamageSource source, float damage) {
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        GuardEquipment equipment = GuardEquipmentResolver.resolve(player);

        DamageClass damageClass = DamageClassifier.classify(source);
        boolean frontal = isFrontal(player, source);
        boolean pvp = source.getEntity() instanceof net.minecraft.world.entity.player.Player;
        boolean creativeExempt = !Config.affectCreative() && (player.isCreative() || player.isSpectator());

        return new GuardContext(player, source, equipment, stamina, guardState,
                damage, damageClass, frontal, pvp, creativeExempt);
    }

    /** FR-07 C4 with the vanilla feel: head-yaw view vector, horizontal dot, configurable half angle. */
    private static boolean isFrontal(ServerPlayer player, DamageSource source) {
        Vec3 srcPos = source.getSourcePosition();
        if (srcPos == null) {
            return false; // E-03: no direction information
        }
        // FR-07: vectorTo(player) — points FROM the source TOWARD the player; facing the
        // source yields dot ≈ -1, which is what the blocked check expects.
        Vec3 toSelf = new Vec3(player.getX() - srcPos.x, 0.0, player.getZ() - srcPos.z);
        double len = toSelf.horizontalDistance();
        if (len < 1.0e-4) {
            return false; // E-04: directly above/below — no horizontal direction
        }
        Vec3 look = player.calculateViewVector(0.0F, player.getYHeadRot());
        double dot = (toSelf.x * look.x + toSelf.z * look.z) / len;
        boolean blocked = GuardRules.frontalBlocked(false, dot, len, Config.frontHalfAngleDeg());
        if (Config.verboseLogging()) {
            BlockModLogger.info("GUARD", "phase", "direction", "dot", dot, "halfAngle", Config.frontHalfAngleDeg(),
                    "frontal", blocked);
        }
        return blocked;
    }

    /** §5.4.3 guard settlement. Only called for GUARDED (stamina was positive at step 10). */
    private static void applyGuardCost(GuardContext ctx) {
        StaminaData stamina = ctx.stamina();
        GuardStateData guardState = ctx.guardState();
        GuardEquipment equipment = ctx.equipment();

        // 1-3. effective gb, pfix, cost (formula clamps internally per §5.4.3 step 4)
        float effectiveGb = EffectiveStrengthResolver.resolve(equipment.profile(), guardState.isPowerGuarding());
        float pfix = resolvePfix(ctx.source());
        float cost = GuardFormulas.staminaCost(ctx.damage(), effectiveGb, pfix);

        // 5. deduct without a lower clamp (FR-09: negative depth is allowed)
        stamina.setStamina(stamina.stamina() - cost);

        // 6. reset the regen delay
        stamina.setLastEventTick(ctx.player().level().getGameTime());

        // 7. durability (floor(dmg)+1, threshold-gated, sword-exempt)
        DurabilityService.consume(ctx.player(), equipment, ctx.damage());

        // 8. same-tick depletion: the blocking hit itself stays blocked (FR-04 acceptance 6)
        if (StaminaService.depletionEdgeFlipped(guardState, stamina.stamina())) {
            StaminaService.refreshDepletedState(ctx.player(), guardState, guardState.wasDepleted());
        }

        // 9. immediate sync (sound/particles land here with M6 T-40)
        SyncThrottler.forceSync(ctx.player());
        BlockModLogger.info("GUARD", "result", "GUARDED", "player", ctx.player().getGameProfile().getName(),
                "damage", ctx.damage(), "cost", cost, "gb", effectiveGb, "pfix", pfix,
                "class", ctx.damageClass(), "pvp", ctx.pvp());
    }

    /** §5.9.3: PvE vs PvP exponent from the pvp_mode whitelist. */
    private static float resolvePfix(DamageSource source) {
        return switch (Config.pvpMode()) {
            case "always_pvp" -> Config.pfixPvp();
            case "always_pve" -> Config.pfixPve();
            default -> source.getEntity() instanceof net.minecraft.world.entity.player.Player
                    ? Config.pfixPvp()
                    : Config.pfixPve();
        };
    }

    /**
     * §5.4.4 (as amended by the 2026-08-30 designer ruling): melee/projectile
     * knockback dies with the cancelled event; blocked explosions ignore the
     * knockback entirely — the vanilla push is zeroed in {@link #onExplosionKnockback}
     * (ExplosionKnockbackEvent fires right after the hurt call in the same
     * explosion loop iteration, so the marker set here is guaranteed to be seen).
     */
    private static void applyKnockbackReduction(GuardContext ctx) {
        if (ctx.damageClass() != DamageClass.EXPLOSION) {
            return; // full reduction: the cancelled event removes vanilla knockback entirely
        }
        EXPLOSION_BLOCKED.add(ctx.player().getUUID()); // zero the explosion's own push this tick
    }

    // ------------------------------------------------------------------
    // context record — everything the arbitration needs, snapshotted (E-25)

    private record GuardContext(
            ServerPlayer player,
            DamageSource source,
            GuardEquipment equipment,
            StaminaData stamina,
            GuardStateData guardState,
            float damage,
            DamageClass damageClass,
            boolean frontal,
            boolean pvp,
            boolean creativeExempt) {

        boolean hasProfile() {
            return equipment != null;
        }

        boolean guarding() {
            return guardState.isGuarding();
        }

        boolean staminaPositive() {
            return stamina.canDefend(); // C3: pre-deduction value (FR-04 acceptance 6)
        }

        boolean inParryWindow() {
            long now = player.level().getGameTime();
            return guardState.parryWindowEndTick() >= 0 && now < guardState.parryWindowEndTick();
        }
    }

    private GuardResolver() {}
}
