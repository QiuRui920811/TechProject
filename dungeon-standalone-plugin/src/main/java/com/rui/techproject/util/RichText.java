package com.rui.techproject.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Rich text parser for dungeon plugin messages.
 * Supports MiniMessage tags (including hex) and legacy color codes.
 */
public final class RichText {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private RichText() {
    }

    public static Component parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.indexOf('<') >= 0 && raw.indexOf('>') > raw.indexOf('<')) {
            try {
                return MINI.deserialize(raw);
            } catch (final Exception ignored) {
                // Fall through to legacy parser.
            }
        }
        if (raw.indexOf('§') >= 0) {
            return LEGACY_SECTION.deserialize(raw);
        }
        if (raw.indexOf('&') >= 0) {
            return LEGACY_AMP.deserialize(raw);
        }
        return Component.text(raw);
    }

    public static Component mini(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(raw);
    }
}
