package com.rui.techproject.addon;

public interface TechAddon {
    String id();

    String displayName();

    default void register(final TechAddonContext context) {
    }
}
