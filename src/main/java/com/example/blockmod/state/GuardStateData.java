package com.example.blockmod.state;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.example.blockmod.data.ShieldType;
import com.example.blockmod.data.GuardProfile;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player guard state attachment ({@code blockmod:guard_state}, NOT serialized —
 * Spec §4.3.3). All ticks are game time values; {@code -1} means "no window pending".
 * The attachment is server-side only: the client keeps a mirror fed by S2C payloads.
 */
public final class GuardStateData {
    private boolean guarding;
    private InteractionHand guardHand = InteractionHand.MAIN_HAND;
    private ItemStack guardStack = ItemStack.EMPTY;
    private ShieldType guardType = ShieldType.SWORD;
    private long parryWindowEndTick = -1L;
    private boolean parryUsed;
    private long parryReadyTick = -1L;
    private boolean powerGuarding;
    private ItemStack secondaryGuardStack = ItemStack.EMPTY;
    private GuardProfile secondaryGuardProfile;
    private long powerGuardReadyTick = -1L;
    private long bashWindupEndTick = -1L;
    private long bashReadyTick = -1L;
    private ResourceLocation activeMoveMalusId;
    private boolean wasDepleted;

    public boolean isGuarding() {
        return guarding;
    }

    public void setGuarding(boolean guarding) {
        this.guarding = guarding;
        if (!guarding) {
            guardStack = ItemStack.EMPTY;
        }
    }

    public ItemStack guardStack() { return guardStack; }

    public ShieldType guardType() { return guardType; }

    public void setGuardEquipment(ItemStack stack, ShieldType type) {
        guardStack = stack;
        guardType = type;
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
        if (!powerGuarding) {
            secondaryGuardStack = ItemStack.EMPTY;
            secondaryGuardProfile = null;
        }
    }

    public ItemStack secondaryGuardStack() { return secondaryGuardStack; }

    @Nullable
    public GuardProfile secondaryGuardProfile() { return secondaryGuardProfile; }

    public void setSecondaryGuardEquipment(ItemStack stack, @Nullable GuardProfile profile) {
        secondaryGuardStack = stack;
        secondaryGuardProfile = profile;
    }

    public long powerGuardReadyTick() {
        return powerGuardReadyTick;
    }

    public void setPowerGuardReadyTick(long powerGuardReadyTick) {
        this.powerGuardReadyTick = powerGuardReadyTick;
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
                + ", powerGuardReady=" + powerGuardReadyTick
                + ", bashWindupEnd=" + bashWindupEndTick + ", bashReady=" + bashReadyTick
                + ", malusId=" + activeMoveMalusId + ", wasDepleted=" + wasDepleted + "]";
    }
}
