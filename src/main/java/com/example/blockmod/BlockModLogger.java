package com.example.blockmod;

import java.util.StringJoiner;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Central logger for Block &amp; Parry.
 * Emits structured lines: {@code [BP] t=<tick> p=<player> <EVENT> key=value ...}
 * Optional tick/player context is passed as leading {@code "t", <tick>} / {@code "p", <name>} pairs.
 */
public final class BlockModLogger {
    public static final Logger LOG = LogUtils.getLogger();

    private BlockModLogger() {}

    /** Formats trailing key/value pairs as {@code key=value key=value}. */
    public static String kv(Object... pairs) {
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            joiner.add(pairs[i] + "=" + String.valueOf(pairs[i + 1]));
        }
        return joiner.toString();
    }

    public static void info(String event, Object... kvs) {
        LOG.info("[BP] {} {}", event, kv(kvs));
    }

    public static void warn(String event, Object... kvs) {
        LOG.warn("[BP] {} {}", event, kv(kvs));
    }

    public static void error(String event, Object... kvs) {
        LOG.error("[BP] {} {}", event, kv(kvs));
    }
}
