package me.lucko.commodore;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.StringReader;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class PsudoCommodoreExtension {

    private static final Method GET_ENTITY_METHOD, // Spigot: CommandListenerWrapper#getEntity(), Mojang: CommandSourceStack#getEntity()
                                GET_BUKKIT_SENDER_METHOD, // CraftBukkit: ICommandListener#getBukkiSender(CommandListenerWrapper) (Mojang class: CommandSource)
                                GET_BUKKIT_BASED_SENDER_METHOD; // CraftBukkit: CommandListenerWrapper#getBukkiSender() (Mojang class: CommandSourceStack)
    private static final Method ENTITY_ARGUMENT_ENTITIES_METHOD, // Spigot: ArgumentEntity#multipleEntities(), Mojang: EntityArgument#entities()
                                ENTITY_ARGUMENT_PARSE_METHOD; // Spigot: ArgumentEntity#a(StringReader, boolean), Mojang: EntityArgument#parse(StringReader arg0) (boolean added by CraftBukkit)
    private static final Method ENTITY_SELECTOR_FIND_ENTITIES_METHOD; // Spigot: EntitySelector#getEntities(CommandListenerWrapper), Mojang: EntitySelector#findEntities(CommandSourceStack)
    private static final Method GET_BUKKIT_LOCATION_METHOD; // Paper method from CommandListenerWrapper (mojang: CommandSourceStack)
    private static final Method LOCAL_COORD_GET_POSITION_METHOD; // Spigot: ArgumentVectorPosition#a(CommandListenerWrapper), Mojang: LocalCoordinates#getPosition(CommandSourceStack)
    private static final Constructor<?> LOCAL_COORD_CONSTRUCTOR;
    private static final Method GET_X, GET_Y, GET_Z;
    private static final Method GET_LISTENER;
    private static final Method GET_COMMAND_MAP_METHOD;
    private static final Method GET_KNOW_COMMANDS_METHOD;

    static {
        try {
            Class<?> commandListenerWrapper;
            Class<?> commandListener;
            Class<?> argumentEntity;
            Class<?> entitySelector;
            Class<?> localCoordinates;
            Class<?> vec3;
            Class<?> vanillaCommandWrapper;
            Class<?> craftServer;
            Class<?> craftCommandMap;
            if (ReflectionUtil.minecraftVersion() > 16) {
                commandListenerWrapper = ReflectionUtil.mcClass("commands.CommandListenerWrapper");
                commandListener = ReflectionUtil.mcClass("commands.ICommandListener");
                argumentEntity = ReflectionUtil.mcClass("commands.arguments.ArgumentEntity");
                entitySelector = ReflectionUtil.mcClass("commands.arguments.selector.EntitySelector");
                localCoordinates = ReflectionUtil.mcClass("commands.arguments.coordinates.ArgumentVectorPosition");
                vec3 = ReflectionUtil.mcClass("world.phys.Vec3D");
                vanillaCommandWrapper = ReflectionUtil.obcClass("command.VanillaCommandWrapper");
                craftCommandMap = ReflectionUtil.obcClass("command.CraftCommandMap");
            } else {
                commandListenerWrapper = ReflectionUtil.nmsClass("CommandListenerWrapper");
                commandListener = ReflectionUtil.nmsClass("ICommandListener");
                argumentEntity = ReflectionUtil.nmsClass("ArgumentEntity");
                entitySelector = ReflectionUtil.nmsClass("EntitySelector");
                localCoordinates = ReflectionUtil.nmsClass("ArgumentVectorPosition");
                vec3 = ReflectionUtil.mcClass("Vec3D");
                vanillaCommandWrapper = ReflectionUtil.obcClass("VanillaCommandWrapper");
                craftCommandMap = ReflectionUtil.obcClass("CraftCommandMap");
            }
            craftServer = ReflectionUtil.obcClass("CraftServer");

            // separate obfuscated names
            if (ReflectionUtil.minecraftVersion() >= 19) {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("g");
            } else if (ReflectionUtil.minecraftVersion() == 18) {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("f");
            } else {
                GET_ENTITY_METHOD = commandListenerWrapper.getDeclaredMethod("getEntity");
            }
            // same obfuscated names
            if (ReflectionUtil.minecraftVersion() > 17) {
                ENTITY_ARGUMENT_ENTITIES_METHOD = argumentEntity.getDeclaredMethod("b");
                ENTITY_SELECTOR_FIND_ENTITIES_METHOD = entitySelector.getDeclaredMethod("b", commandListenerWrapper);
                GET_X = vec3.getDeclaredMethod("a");
                GET_Y = vec3.getDeclaredMethod("b");
                GET_Z = vec3.getDeclaredMethod("c");
            } else {
                ENTITY_ARGUMENT_ENTITIES_METHOD = argumentEntity.getDeclaredMethod("multipleEntities");
                ENTITY_SELECTOR_FIND_ENTITIES_METHOD = entitySelector.getDeclaredMethod("getEntities", commandListenerWrapper);
                GET_X = vec3.getDeclaredMethod("getX");
                GET_Y = vec3.getDeclaredMethod("getY");
                GET_Z = vec3.getDeclaredMethod("getZ");
            }

            ENTITY_ARGUMENT_PARSE_METHOD = argumentEntity.getDeclaredMethod("parse", StringReader.class, boolean.class); // craftbukkit method (without boolean, obf name is a)
            GET_BUKKIT_SENDER_METHOD = commandListener.getDeclaredMethod("getBukkitSender", commandListenerWrapper); // craftbukkit method
            GET_BUKKIT_BASED_SENDER_METHOD = commandListenerWrapper.getDeclaredMethod("getBukkitSender"); // craftbukkit method
            GET_BUKKIT_LOCATION_METHOD = commandListenerWrapper.getDeclaredMethod("getBukkitLocation"); // Paper method
            LOCAL_COORD_GET_POSITION_METHOD = localCoordinates.getMethod("a", commandListenerWrapper);
            LOCAL_COORD_CONSTRUCTOR = localCoordinates.getConstructor(double.class, double.class, double.class);
            GET_LISTENER = vanillaCommandWrapper.getDeclaredMethod("getListener", CommandSender.class);

            GET_KNOW_COMMANDS_METHOD = craftCommandMap.getDeclaredMethod("getKnownCommands");
            GET_COMMAND_MAP_METHOD = craftServer.getDeclaredMethod("getCommandMap");

            GET_ENTITY_METHOD.setAccessible(true);
            LOCAL_COORD_CONSTRUCTOR.setAccessible(true);
            GET_LISTENER.setAccessible(true);
            ENTITY_ARGUMENT_ENTITIES_METHOD.setAccessible(true);
            ENTITY_SELECTOR_FIND_ENTITIES_METHOD.setAccessible(true);
            GET_X.setAccessible(true);
            GET_Y.setAccessible(true);
            GET_Z.setAccessible(true);
            GET_BUKKIT_LOCATION_METHOD.setAccessible(true);
            LOCAL_COORD_GET_POSITION_METHOD.setAccessible(true);
            GET_BUKKIT_BASED_SENDER_METHOD.setAccessible(true);
            GET_BUKKIT_SENDER_METHOD.setAccessible(true);
            ENTITY_ARGUMENT_PARSE_METHOD.setAccessible(true);

            GET_COMMAND_MAP_METHOD.setAccessible(true);
            GET_KNOW_COMMANDS_METHOD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static CommandSender getBukkitSender(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            Object entity = GET_ENTITY_METHOD.invoke(commandWrapperListener);
            if (entity == null) {
                return null;
            } else {
                return (CommandSender) GET_BUKKIT_SENDER_METHOD.invoke(entity, commandWrapperListener);
            }
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
            // problem with local coordinates that does not work because pos has nul yaw & pitch. We use local coordinates
            // with the commandWrapperListener (cf. getLocalCoord) to avoid to get rotation and anchor by hand.
            return (Location) GET_BUKKIT_LOCATION_METHOD.invoke(commandWrapperListener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // Partially extracted from CraftServer class
    public static List<Entity> selectEntities(Object commandSourceStack, String selector) {
        List<Object> nms;
        List<Entity> result = new ArrayList<>();

        try {
            Object arg_entities = ENTITY_ARGUMENT_ENTITIES_METHOD.invoke(null);
            StringReader reader = new StringReader(selector);

            Object entitySelectorObject = ENTITY_ARGUMENT_PARSE_METHOD.invoke(arg_entities, reader, true);
            nms = (List<Object>) ENTITY_SELECTOR_FIND_ENTITIES_METHOD.invoke(entitySelectorObject, commandSourceStack);

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

    public static Location getLocalCoord(double x, double y, double z, Object commandWrapperListener) {
        try {
            Object localCoordObject = LOCAL_COORD_CONSTRUCTOR.newInstance(x, y, z);
            Object pos = LOCAL_COORD_GET_POSITION_METHOD.invoke(localCoordObject, commandWrapperListener);

            Location loc = getBukkitLocation(commandWrapperListener);

            return new Location(loc.getWorld(), (double) GET_X.invoke(pos), (double) GET_Y.invoke(pos), (double) GET_Z.invoke((pos)));
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getCommandWrapperListenerObject(CommandSender sender) {
        try {
            return GET_LISTENER.invoke(null, sender);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Command> getKnownCommands() {
        try {
            Object craftCommandMap = GET_COMMAND_MAP_METHOD.invoke(Bukkit.getServer());
            return (Map<String, Command>) GET_KNOW_COMMANDS_METHOD.invoke(craftCommandMap);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean dispatchCommandIgnorePerms(CommandSender sender, String commandstr) {
        String[] args = StringUtils.split(commandstr, ' ');
        if (args.length == 0) {
            return false;
        }
        String sentCommandLabel = args[0].toLowerCase(java.util.Locale.ENGLISH);
        Command command = getKnownCommands().get(sentCommandLabel.toLowerCase(java.util.Locale.ENGLISH));
        if (command == null) {
            return false;
        }

        try {
            //if (command.timings != null) command.timings.startTiming();
            executeIgnorePerms(command, sender, sentCommandLabel, Arrays.copyOfRange(args, 1, args.length));
            //if (command.timings != null) command.timings.stopTiming();
        } catch (CommandException ex) {
            //if (command.timings != null) command.timings.stopTiming();
            throw ex;
        } catch (Throwable ex) {
            String msg = "Unhandled exception executing '" + commandstr + "' in " + command;
            //if (command.timings != null) command.timings.stopTiming();
            throw new CommandException(msg, ex) ;
        }
        return true;
    }

    private static boolean executeIgnorePerms(Command command, CommandSender sender, String label, String[] args) {
        if (command instanceof PluginCommand) {
            PluginCommand pluginCommand = (PluginCommand) command;
            boolean success;
            if (!pluginCommand.getPlugin().isEnabled()) {
                throw new CommandException("Cannot execute command '" + label + "' in plugin " + pluginCommand.getPlugin().getDescription().getFullName() + " - plugin is disabled.");
            }
            try {
                success = pluginCommand.getExecutor().onCommand(sender, pluginCommand, label, args);
            } catch (Throwable ex) {
                throw new CommandException("Unhandled exception executing command '" + label + "' in plugin " + pluginCommand.getPlugin().getDescription().getFullName(), ex);
            }
            if (!success && pluginCommand.getUsage().length() > 0) {
                for (String line : pluginCommand.getUsage().replace("<command>", label).split("\n")) {
                    sender.sendMessage(line);
                }
            }
            return success;
        } else {
            // don't check VanillaCommandWrapper type because we can ignore psudo and use vanilla behavior
            return command.execute(sender, label, args);
        }
    }
}
