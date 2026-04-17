package com.rui.techproject.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 浮動文字工具 — 在世界中生成一段短暫上飄後消失的 {@link TextDisplay}。
 *
 * <p>典型用途：傷害數字、XP 獲取提示、升級特效。
 * 使用 Transformation 插值讓文字在 1.5 秒內平滑上飄，到時自動移除。
 */
public final class FloatingTextUtil {

    private static final AxisAngle4f NO_ROTATION = new AxisAngle4f(0f, 0f, 1f, 0f);
    /** 浮動持續 tick 數（30 tick ≈ 1.5 秒） */
    private static final int LIFETIME_TICKS = 30;
    /** 上飄高度（方塊） */
    private static final float RISE_HEIGHT = 1.2f;

    private FloatingTextUtil() {}

    /**
     * 在指定位置生成浮動文字。
     *
     * @param location 生成位置（通常為實體頭頂或玩家上方）
     * @param text     要顯示的 Adventure Component
     * @param scale    文字縮放（建議 0.4~0.8）
     * @param scheduler Folia 安全排程器
     */
    public static void spawn(final Location location, final Component text,
                             final float scale, final SafeScheduler scheduler) {
        if (location.getWorld() == null) return;

        location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setTextOpacity((byte) -1);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setViewRange(0.3f);
            display.setBrightness(new Display.Brightness(15, 15));
            display.addScoreboardTag("techproject_floater");

            // 初始 Transformation：指定縮放，位移 (0,0,0)
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),  // translation
                    NO_ROTATION,                // left rotation
                    new Vector3f(scale, scale, scale), // scale
                    NO_ROTATION                 // right rotation
            ));

            // 1 tick 後啟動插值：平滑上飄
            scheduler.runEntityDelayed(display, () -> {
                if (!display.isValid()) return;
                display.setTransformation(new Transformation(
                        new Vector3f(0f, RISE_HEIGHT, 0f),
                        NO_ROTATION,
                        new Vector3f(scale, scale, scale),
                        NO_ROTATION
                ));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(LIFETIME_TICKS);
            }, 1L);

            // 到期後移除
            scheduler.runEntityDelayed(display, () -> {
                if (display.isValid()) display.remove();
            }, LIFETIME_TICKS + 2L);
        });
    }

    /**
     * 在實體頭頂生成浮動文字（隨機水平偏移避免重疊）。
     */
    public static void spawnAbove(final Location eyeLocation, final Component text,
                                  final float scale, final SafeScheduler scheduler) {
        final double offsetX = (Math.random() - 0.5) * 0.6;
        final double offsetZ = (Math.random() - 0.5) * 0.6;
        spawn(eyeLocation.clone().add(offsetX, 0.3, offsetZ), text, scale, scheduler);
    }
}
