package com.example.blockmod.verify;

import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.handler.PlayerTickHandler;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * TEMPORARY M2 verification harness (T-15/T-16 acceptance) — remove at M3.
 *
 * <p>Drives {@link PlayerTickHandler#tick} on a {@link FakePlayer} through the FR-02
 * branch table and the depletion edge, logging one line per assertion so the
 * dedicated-server log doubles as the acceptance record:
 * <pre>
 * M2VERIFY ok=true case=... expected=... actual=...
 * </pre>
 * Real-player feel (HUD, LAN sync) stays with the manual G2 checklist.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class M2Verify {
    private record Result(String name, boolean ok, String expected, String actual) {}

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b2"), "M2Probe"));

        BlockModLogger.info("M2VERIFY", "note", "=== regen branches (FR-02) ===");
        // 正常: st=20, out of delay -> 4/s -> 20 ticks add 4.0
        Result r = drive(player, 20.0f, false, false, 0L, 20, 24.0f);
        log(r);
        // 延迟中: lastEventTick = now -> no regen
        r = driveAtDelay(player, 20.0f, 20, 20.0f);
        log(r);
        // 格挡中: 2/s -> 20 ticks add 2.0
        r = drive(player, 20.0f, true, false, 0L, 20, 22.0f);
        log(r);
        // 枯竭负值 + 格挡: 8/s beats guard halving
        r = drive(player, -10.0f, true, false, 0L, 20, -2.0f);
        log(r);
        // 枯竭零值: st=0 -> depleted branch
        r = drive(player, 0.0f, false, false, 0L, 20, 8.0f);
        log(r);
        // 枯竭且PG: depleted beats the PG suppression
        r = drive(player, -5.0f, true, true, 0L, 20, 3.0f);
        log(r);
        // 强力防御: 0 regen
        r = drive(player, 20.0f, true, true, 0L, 20, 20.0f);
        log(r);
        // 上限钳制
        r = drive(player, 39.9f, false, false, 0L, 40, 40.0f);
        log(r);

        BlockModLogger.info("M2VERIFY", "note", "=== depletion edge (§5.3.2) ===");
        edgeCase(player, 0.5f, -0.2f, true);
        edgeCase(player, -0.2f, 0.5f, false);
        // no-thrash: 10 consecutive depleted ticks must not re-fire
        int fired = 0;
        player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get()).setWasDepleted(true);
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        s.setStamina(-5.0f);
        for (int i = 0; i < 10; i++) {
            s.setStamina(-5.0f + 0.2f * (i + 1));
            if (StaminaEdgeProbe.check(player)) {
                fired++;
            }
        }
        log(new Result("edge_无跳变", fired == 0, "0 fires", fired + " fires"));
        BlockModLogger.info("M2VERIFY", "note", "=== complete ===");
    }

    /** Drives N ticks with lastEventTick fixed in the past (no delay). */
    private static Result drive(ServerPlayer player, float start, boolean guarding, boolean powerGuarding,
            long lastEventTick, int ticks, float expected) {
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        s.setStamina(start);
        s.setLastEventTick(lastEventTick);
        g.setGuarding(guarding);
        g.setPowerGuarding(powerGuarding);
        g.setWasDepleted(start <= 0f);
        long now = player.level().getGameTime();
        for (int i = 0; i < ticks; i++) {
            now++;
            PlayerTickHandler.tick(player);
        }
        boolean ok = Math.abs(s.stamina() - expected) < 0.01f;
        return new Result(String.format("st=%.1f guard=%s pg=%s -> after %d ticks", start, guarding, powerGuarding, ticks),
                ok, String.valueOf(expected), String.format("%.3f", s.stamina()));
    }

    /** Drives N ticks with lastEventTick = now-5 (inside the 2 s delay). */
    private static Result driveAtDelay(ServerPlayer player, float start, int ticks, float expected) {
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        s.setStamina(start);
        s.setLastEventTick(player.level().getGameTime() - 5L);
        g.setGuarding(false);
        g.setPowerGuarding(false);
        g.setWasDepleted(false);
        for (int i = 0; i < ticks; i++) {
            PlayerTickHandler.tick(player);
        }
        boolean ok = Math.abs(s.stamina() - expected) < 0.01f;
        return new Result("delay window holds regen", ok, String.valueOf(expected), String.format("%.3f", s.stamina()));
    }

    private static void edgeCase(ServerPlayer player, float before, float after, boolean expectedDepleted) {
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        s.setStamina(before);
        g.setWasDepleted(before <= 0f);
        s.setStamina(after);
        boolean fired = StaminaEdgeProbe.check(player);
        log(new Result(String.format("edge %.2f -> %.2f", before, after),
                fired == (expectedDepleted != (before <= 0f)), expectedDepleted ? "enter" : "exit",
                fired ? "fired" : "not fired"));
    }

    private static void log(Result r) {
        BlockModLogger.info("M2VERIFY", "case", r.name(), "ok", r.ok(), "expected", r.expected(), "actual", r.actual());
    }

    /** Reflection-free edge probe: re-runs the same edge check PlayerTickHandler uses. */
    private static final class StaminaEdgeProbe {
        static boolean check(ServerPlayer player) {
            return com.example.blockmod.logic.StaminaService.depletionEdgeFlipped(
                    player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get()),
                    player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get()).stamina());
        }
    }

    private M2Verify() {}
}
