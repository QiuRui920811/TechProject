package com.rui.techproject.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ItemStackSerializer {

    private static final Logger LOGGER = Logger.getLogger(ItemStackSerializer.class.getName());

    private ItemStackSerializer() {
    }

    public static String toBase64(final ItemStack[] items) {
        if (items == null) {
            return "";
        }
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream();
             final BukkitObjectOutputStream data = new BukkitObjectOutputStream(out)) {
            data.writeInt(items.length);
            for (final ItemStack item : items) {
                data.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (final Exception exception) {
            return "";
        }
    }

    public static ItemStack[] fromBase64(final String base64, final int fallbackSize) {
        if (base64 == null || base64.isBlank()) {
            return new ItemStack[fallbackSize];
        }
        try (final ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             final BukkitObjectInputStream data = new BukkitObjectInputStream(in)) {
            final int size = data.readInt();
            final ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) data.readObject();
            }
            return items;
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "反序列化 ItemStack 陣列失敗 (fallbackSize=" + fallbackSize + ")", exception);
            return new ItemStack[fallbackSize];
        }
    }
}
