package com.rui.techproject.addon;

import com.rui.techproject.model.GuideCategory;
import com.rui.techproject.model.TechTier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TechAddonService {
    private final Map<String, TechAddon> addons = new LinkedHashMap<>();
    private final Map<String, TechInteractionDefinition> interactions = new LinkedHashMap<>();

    public void registerAddon(final TechAddon addon, final TechAddonContext context) {
        if (addon == null || addon.id() == null || addon.id().isBlank()) {
            return;
        }
        this.addons.put(addon.id().toLowerCase(), addon);
        addon.register(context);
    }

    public void registerInteraction(final TechInteractionDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            return;
        }
        this.interactions.put(definition.id().toLowerCase(), definition);
    }

    public Collection<TechAddon> allAddons() {
        return List.copyOf(this.addons.values());
    }

    public Collection<TechInteractionDefinition> allInteractions() {
        return List.copyOf(this.interactions.values());
    }

    public Collection<TechInteractionDefinition> getInteractionsByCategory(final GuideCategory category) {
        return this.interactions.values().stream()
                .filter(definition -> definition.guideCategory() == category)
                .toList();
    }

    public Collection<TechInteractionDefinition> getInteractionsByCategoryAndTier(final GuideCategory category, final TechTier tier) {
        return this.interactions.values().stream()
                .filter(definition -> definition.guideCategory() == category && definition.tier() == tier)
                .toList();
    }

    public TechInteractionDefinition getInteraction(final String id) {
        return id == null ? null : this.interactions.get(id.toLowerCase());
    }
}
