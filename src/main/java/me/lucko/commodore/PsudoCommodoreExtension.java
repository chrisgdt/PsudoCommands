package me.lucko.commodore;

import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.Objects;

public class PsudoCommodoreExtension {
    private static final Method GET_ENTITY_METHOD,
                                GET_BUKKIT_SENDER_METHOD,
                                GET_BUKKIT_BASED_SENDER_METHOD;

    public static CommandSender getBukkitSender(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            Object entity = GET_ENTITY_METHOD.invoke(commandWrapperListener);
            Objects.requireNonNull(entity, "commandWrapperListener.entity");
            return (CommandSender) GET_BUKKIT_SENDER_METHOD.invoke(entity, commandWrapperListener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static CommandSender getBukkitBasedSender(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            return (CommandSender) GET_BUKKIT_BASED_SENDER_METHOD.invoke(commandWrapperListener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            Class<?> commandListenerWrapper;
            Class<?> commandListener;
            if (ReflectionUtil.minecraftVersion() > 16) {
                commandListenerWrapper = ReflectionUtil.mcClass("commands.CommandListenerWrapper");
                commandListener = ReflectionUtil.mcClass("commands.ICommandListener");
            } else {
                commandListenerWrapper = ReflectionUtil.nmsClass("CommandListenerWrapper");
                commandListener = ReflectionUtil.nmsClass("ICommandListener");
            }

            if (ReflectionUtil.minecraftVersion() >= 19) {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("g");
            } else if (ReflectionUtil.minecraftVersion() == 18) {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("f");
            } else {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("getEntity");
            }
            GET_ENTITY_METHOD.setAccessible(true);

            GET_BUKKIT_SENDER_METHOD = commandListener.getDeclaredMethod("getBukkitSender", commandListenerWrapper); // craftbukkit method
            GET_BUKKIT_SENDER_METHOD.setAccessible(true);

            GET_BUKKIT_BASED_SENDER_METHOD = commandListenerWrapper.getDeclaredMethod("getBukkitSender"); // craftbukkit method
            GET_BUKKIT_BASED_SENDER_METHOD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
