package com.rui.techproject.addon;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.TechBookService;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.util.ItemFactoryUtil;

public record TechAddonContext(
        TechMCPlugin plugin,
        TechRegistry registry,
        TechBookService techBookService,
        ItemFactoryUtil itemFactory,
        TechAddonService addonService
) {
}
