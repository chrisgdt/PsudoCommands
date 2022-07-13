package me.lucko.commodore;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.StringReader;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PsudoCommodoreExtension {

    private static final Method GET_ENTITY_METHOD, // Spigot: CommandListenerWrapper#getEntity(), Mojang: CommandSourceStack#getEntity()
                                GET_BUKKIT_SENDER_METHOD, // CraftBukkit: ICommandListener#getBukkiSender(CommandListenerWrapper) (Mojang class: CommandSource)
                                GET_BUKKIT_BASED_SENDER_METHOD; // CraftBukkit: CommandListenerWrapper#getBukkiSender() (Mojang class: CommandSourceStack)

    private static final Method ENTITY_ARGUMENT_ENTITIES, // Spigot: ArgumentEntity#multipleEntities(), Mojang: EntityArgument#entities()
                                ENTITY_ARGUMENT_PARSE; // Spigot: ArgumentEntity#a(StringReader, boolean), Mojang: EntityArgument#parse(StringReader arg0) (boolean added by CraftBukkit)

    private static final Method ENTITY_SELECTOR_FIND_ENTITIES; // Spigot: EntitySelector#getEntities(CommandListenerWrapper), Mojang: EntitySelector#findEntities(CommandSourceStack)

    private static final Method GET_BUKKIT_LOCATION; // Paper method from CommandListenerWrapper (mojang: CommandSourceStack)

    static {
        try {
            Class<?> commandListenerWrapper;
            Class<?> commandListener;
            Class<?> argumentEntity;
            Class<?> entitySelector;
            if (ReflectionUtil.minecraftVersion() > 16) {
                commandListenerWrapper = ReflectionUtil.mcClass("commands.CommandListenerWrapper");
                commandListener = ReflectionUtil.mcClass("commands.ICommandListener");
                argumentEntity = ReflectionUtil.mcClass("commands.arguments.ArgumentEntity");
                entitySelector = ReflectionUtil.mcClass("commands.arguments.selector.EntitySelector");
            } else {
                commandListenerWrapper = ReflectionUtil.nmsClass("CommandListenerWrapper");
                commandListener = ReflectionUtil.nmsClass("ICommandListener");
                argumentEntity = ReflectionUtil.nmsClass("ArgumentEntity");
                entitySelector = ReflectionUtil.nmsClass("EntitySelector");
            }

            if (ReflectionUtil.minecraftVersion() >= 19) {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("g");
                ENTITY_ARGUMENT_ENTITIES = argumentEntity.getDeclaredMethod("b");
                ENTITY_SELECTOR_FIND_ENTITIES = entitySelector.getDeclaredMethod("b", commandListenerWrapper);
            } else if (ReflectionUtil.minecraftVersion() == 18) {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("f");
                ENTITY_ARGUMENT_ENTITIES = argumentEntity.getDeclaredMethod("b");
                ENTITY_SELECTOR_FIND_ENTITIES = entitySelector.getDeclaredMethod("b", commandListenerWrapper);
            } else {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("getEntity");
                ENTITY_ARGUMENT_ENTITIES = argumentEntity.getDeclaredMethod("multipleEntities");
                ENTITY_SELECTOR_FIND_ENTITIES = entitySelector.getDeclaredMethod("getEntities", commandListenerWrapper);
            }
            GET_ENTITY_METHOD.setAccessible(true);
            ENTITY_ARGUMENT_ENTITIES.setAccessible(true);
            ENTITY_SELECTOR_FIND_ENTITIES.setAccessible(true);

            ENTITY_ARGUMENT_PARSE = argumentEntity.getDeclaredMethod("parse", StringReader.class, boolean.class); // craftbukkit method (without boolean, obf name is a)
            ENTITY_ARGUMENT_PARSE.setAccessible(true);

            GET_BUKKIT_SENDER_METHOD = commandListener.getDeclaredMethod("getBukkitSender", commandListenerWrapper); // craftbukkit method
            GET_BUKKIT_SENDER_METHOD.setAccessible(true);

            GET_BUKKIT_BASED_SENDER_METHOD = commandListenerWrapper.getDeclaredMethod("getBukkitSender"); // craftbukkit method
            GET_BUKKIT_BASED_SENDER_METHOD.setAccessible(true);

            GET_BUKKIT_LOCATION = commandListenerWrapper.getDeclaredMethod("getBukkitLocation"); // Paper method
            GET_BUKKIT_LOCATION.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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

    public static Location getBukkitLocation(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            return (Location) GET_BUKKIT_LOCATION.invoke(commandWrapperListener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Entity> selectEntities(Object commandSourceStack, String selector) {
        List<Object> nms;
        List<Entity> result = new ArrayList<>();

        try {
            Object arg_entities = ENTITY_ARGUMENT_ENTITIES.invoke(null);
            StringReader reader = new StringReader(selector);

            Object entitySelectorObject = ENTITY_ARGUMENT_PARSE.invoke(arg_entities, reader, true);
            nms = (List<Object>) ENTITY_SELECTOR_FIND_ENTITIES.invoke(entitySelectorObject, commandSourceStack);

            Preconditions.checkArgument(!reader.canRead(), "Spurious trailing data in selector: " + selector);

            for (Object entity : nms) {
                // use getBukkitSender because on entity it just returns the BukkitEntity
                result.add((Entity) GET_BUKKIT_SENDER_METHOD.invoke(entity, commandSourceStack));
            }

        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException("Could not parse selector: " + selector, ex);
        }

        return result;
    }

    /*
    public List<Entity> selectEntities(CommandSender sender, String selector) {
        Preconditions.checkArgument(selector != null, "Selector cannot be null");
        Preconditions.checkArgument(sender != null, "Sender cannot be null");

        EntityArgument arg = EntityArgument.entities();
        List<? extends net.minecraft.world.entity.Entity> nms;

        try {
            StringReader reader = new StringReader(selector);
            nms = arg.parse(reader, true).findEntities(VanillaCommandWrapper.getListener(sender));
            Preconditions.checkArgument(!reader.canRead(), "Spurious trailing data in selector: " + selector);
        } catch (CommandSyntaxException ex) {
            throw new IllegalArgumentException("Could not parse selector: " + selector, ex);
        }

        return new ArrayList<>(Lists.transform(nms, (entity) -> entity.getBukkitEntity()));
    }
     */
}
