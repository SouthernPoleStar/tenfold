package com.tenfold.client;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

import java.util.Collections;

/**
 * 去掉头颅的马模型：杜拉奇是无头骑士的坐骑，只保留脖子以下的身体。
 * <p>
 * {@code AgeableListModel.renderToBuffer} 只会渲染 {@code headParts()} 与
 * {@code bodyParts()} 两部分，返回空集合就等于"头不存在"。
 */
public class HeadlessHorseModel<T extends AbstractHorse> extends HorseModel<T> {

    public HeadlessHorseModel(ModelPart root) {
        super(root);
    }

    @Override
    public Iterable<ModelPart> headParts() {
        return Collections.emptyList();
    }
}
