package com.tenfold.common;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * 原版 {@code generic.max_health} 是 {@link RangedAttribute}，上限写死 1024
 * （{@code new RangedAttribute("generic.max_health", 20.0, 1.0, 1024.0)}），
 * {@link RangedAttribute#sanitizeValue} 会把 8000/7500/2000 全部压成 1024。
 * <p>
 * 后果：三只 Boss 实际只有 1024 血，而二阶段阈值写死 5000 —— 血量永远低于阈值，
 * 掉一两刀就触发二阶段。这里把上限抬到 {@link #MAX_HEALTH_LIMIT}，让设计血量生效。
 * <p>
 * 客户端也必须放宽：同步过来的 base 值会在客户端再走一次 sanitize。
 * 反射按"字段类型 + 值域"匹配而不依赖字段名，避免正式包 SRG 重命名后失效；
 * 失败时只打警告，二阶段阈值已改成比例，仍然能在 1024 血的时候正确触发。
 */
public final class AttributeLimits {

    /** 放宽后的血量上限。够用即可，不要取 Double.MAX_VALUE（会溢出部分伤害计算）。 */
    public static final double MAX_HEALTH_LIMIT = 1.0E7D;

    private static final Logger LOG = LoggerFactory.getLogger("Tenfold");

    private AttributeLimits() {
    }

    public static void raiseMaxHealthLimit() {
        Attribute maxHealth = Attributes.MAX_HEALTH;
        if (!(maxHealth instanceof RangedAttribute ranged)) {
            LOG.warn("Attributes.MAX_HEALTH is not a RangedAttribute; health limit left unchanged");
            return;
        }
        try {
            boolean raised = false;
            for (Field field : RangedAttribute.class.getDeclaredFields()) {
                if (field.getType() != double.class) {
                    continue;
                }
                field.setAccessible(true);
                double current = field.getDouble(ranged);
                // minValue 是 1.0，maxValue 是 1024.0：按值域挑出上限字段，不依赖字段名。
                if (current > 1000.0D && current < 1.0E9D) {
                    field.setDouble(ranged, MAX_HEALTH_LIMIT);
                    raised = true;
                }
            }
            if (raised) {
                LOG.info("Raised entity max-health limit from 1024 to {}", MAX_HEALTH_LIMIT);
            } else {
                LOG.warn("Could not locate the RangedAttribute max-value field; bosses stay capped at 1024 HP");
            }
        } catch (Throwable t) {
            LOG.warn("Failed to raise the entity max-health limit; bosses stay capped at 1024 HP", t);
        }
    }
}
