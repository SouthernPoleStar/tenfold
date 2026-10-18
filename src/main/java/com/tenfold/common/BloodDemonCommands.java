package com.tenfold.common;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * 血魔转化请求的两个按钮对应的命令：
 * <pre>
 * /tenfold blood accept   同意转化
 * /tenfold blood deny     拒绝转化
 * </pre>
 * 聊天栏里的「同意 / 拒绝」按钮就是用 {@code ClickEvent.RUN_COMMAND} 调这两条。
 */
public final class BloodDemonCommands {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tenfold")
                .then(Commands.literal("blood")
                        .then(Commands.literal("accept").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BloodDemonManager.acceptRequest(player);
                            return 1;
                        }))
                        .then(Commands.literal("deny").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BloodDemonManager.denyRequest(player);
                            return 1;
                        }))));
    }

    private BloodDemonCommands() {
    }
}
