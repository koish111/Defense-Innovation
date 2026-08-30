package com.example.blockmod.state;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/**
 * Per-player guard state attachment ({@code blockmod:guard_state}, NOT serialized —
 * Spec §4.3.3). All ticks are game time values; {@code -1} means "no window pending".
 * The attachment is server-side only: the client keeps a mirror fed by S2C payloads.
 */
public final class GuardStateData {
    private boolean guarding;
    private InteractionHand guardHand = InteractionHand.MAIN_HAND;
    private long parryWindowEndTick = -1L;
    private boolean parryUsed;
    private long parryReadyTick = -1L;
    private boolean powerGuarding;
    private long bashWindupEndTick = -1L;
    private long bashReadyTick = -1L;
    private ResourceLocation activeMoveMalusId;
    private boolean wasDepleted;

    public boolean isGuarding() {
        return guarding;
    }

    public void setGuarding(boolean guarding) {
        this.guarding = guarding;
    }

    public InteractionHand guardHand() {
        return guardHand;
    }

    public void setGuardHand(InteractionHand guardHand) {
        this.guardHand = guardHand;
    }

    public long parryWindowEndTick() {
        return parryWindowEndTick;
    }

    public void setParryWindowEndTick(long parryWindowEndTick) {
        this.parryWindowEndTick = parryWindowEndTick;
    }

    public boolean isParryUsed() {
        return parryUsed;
    }

    public void setParryUsed(boolean parryUsed) {
        this.parryUsed = parryUsed;
    }

    public long parryReadyTick() {
        return parryReadyTick;
    }

    public void setParryReadyTick(long parryReadyTick) {
        this.parryReadyTick = parryReadyTick;
    }

    public boolean isPowerGuarding() {
        return powerGuarding;
    }

    public void setPowerGuarding(boolean powerGuarding) {
        this.powerGuarding = powerGuarding;
    }

    public long bashWindupEndTick() {
        return bashWindupEndTick;
    }

    public void setBashWindupEndTick(long bashWindupEndTick) {
        this.bashWindupEndTick = bashWindupEndTick;
    }

    public long bashReadyTick() {
        return bashReadyTick;
    }

    public void setBashReadyTick(long bashReadyTick) {
        this.bashReadyTick = bashReadyTick;
    }

    /**
     * Id of the currently mounted move-speed modifier; null = none.
     * Spec §4.3.3 named this field a UUID, but 1.20.5+ keys attribute modifiers by
     * ResourceLocation (verified in M0, API-10) so the type follows the platform.
     */
    public ResourceLocation activeMoveMalusId() {
        return activeMoveMalusId;
    }

    public void setActiveMoveMalusId(ResourceLocation activeMoveMalusId) {
        this.activeMoveMalusId = activeMoveMalusId;
    }

    /** Previous tick's depletion state, for detecting the crossing of zero (v2.0). */
    public boolean wasDepleted() {
        return wasDepleted;
    }

    public void setWasDepleted(boolean wasDepleted) {
        this.wasDepleted = wasDepleted;
    }

    @Override
    public String toString() {
        return "GuardStateData[guarding=" + guarding + ", hand=" + guardHand
                + ", parryWindowEnd=" + parryWindowEndTick + ", parryUsed=" + parryUsed
                + ", parryReady=" + parryReadyTick + ", powerGuarding=" + powerGuarding
                + ", bashWindupEnd=" + bashWindupEndTick + ", bashReady=" + bashReadyTick
                + ", malusId=" + activeMoveMalusId + ", wasDepleted=" + wasDepleted + "]";
    }
}
