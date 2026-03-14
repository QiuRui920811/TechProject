package com.rui.techproject.addon;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.service.TechBookService;
import com.rui.techproject.service.TechRegistry;
import com.rui.techproject.util.ItemFactoryUtil;

public record TechAddonContext(
        TechProjectPlugin plugin,
        TechRegistry registry,
        TechBookService techBookService,
        ItemFactoryUtil itemFactory,
        TechAddonService addonService
) {
}
