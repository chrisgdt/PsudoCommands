package me.lucko.commodore;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.StringReader;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class PsudoCommodoreExtension {

    // CLASS NAME : Spigot: CommandListenerWrapper, Mojang: CommandSourceStack
    private static final Method GET_ENTITY_METHOD, // Method getEntity() (for both Spigot and Mojang)
                                GET_BUKKIT_BASED_SENDER_METHOD, // CraftBukkit method: getBukkitSender()
                                GET_BUKKIT_LOCATION_METHOD, // Paper method: getBukkitLocation()
                                GET_POSITION, GET_LEVEL, GET_ROTATION; // if getBukkitLocation is found, these three methods are null

    private static final Method GET_BUKKIT_SENDER_METHOD; // CraftBukkit method ICommandListener#getBukkitSender(), Mojang class name : CommandSource

    // CLASS NAME : Spigot: ArgumentEntity, Mojang: EntityArgument
    static final Method ENTITY_ARGUMENT_ENTITIES_METHOD, // Spigot: multipleEntities(), Mojang: entities()
                        ENTITY_ARGUMENT_PARSE_METHOD; // Spigot: a(StringReader, boolean), Mojang: parse(StringReader arg0) (boolean added by CraftBukkit)

    private static final Method ENTITY_SELECTOR_FIND_ENTITIES_METHOD; // Spigot: EntitySelector#getEntities(CommandListenerWrapper), Mojang: EntitySelector#findEntities(CommandSourceStack)
    private static final Method LOCAL_COORD_GET_POSITION_METHOD; // Spigot: ArgumentVectorPosition#a(CommandListenerWrapper), Mojang: LocalCoordinates#getPosition(CommandSourceStack)
    private static final Method GET_X, GET_Y, GET_Z; // Spigot: Vec3D#a(), Mojang: Vec3#getX()
    private static final Method GET_COMMAND_MAP_METHOD; // craftbukkit package: CraftServer#getCommandMap()
    private static final Method GET_KNOW_COMMANDS_METHOD; // craftbukkit package: CraftCommandMap#getKnownCommand()
    private static final Method GET_LISTENER; // craftbukkit package: VanillaCommandWrapper#getListener(CommandSender)

    private static final Constructor<?> LOCAL_COORD_CONSTRUCTOR;

    private static final Method SERVER_LEVEL_GET_WORLD; // craftbukkit method ServerLevel#getWorld(), null if getBukkitLocation is found
    private static final Field X, Y; // craftbukkit method ServerLevel#getWorld(), null if getBukkitLocation is found


    static {
        try {
            Class<?> commandListenerWrapper, commandListener, argumentEntity, entitySelector,
                    localCoordinates, vec3;
            if (ReflectionUtil.minecraftVersion() > 16) {
                commandListenerWrapper = ReflectionUtil.mcClass("commands.CommandListenerWrapper");
                commandListener = ReflectionUtil.mcClass("commands.ICommandListener");
                argumentEntity = ReflectionUtil.mcClass("commands.arguments.ArgumentEntity");
                entitySelector = ReflectionUtil.mcClass("commands.arguments.selector.EntitySelector");
                localCoordinates = ReflectionUtil.mcClass("commands.arguments.coordinates.ArgumentVectorPosition");
                vec3 = ReflectionUtil.mcClass("world.phys.Vec3D");
            } else {
                commandListenerWrapper = ReflectionUtil.nmsClass("CommandListenerWrapper");
                commandListener = ReflectionUtil.nmsClass("ICommandListener");
                argumentEntity = ReflectionUtil.nmsClass("ArgumentEntity");
                entitySelector = ReflectionUtil.nmsClass("EntitySelector");
                localCoordinates = ReflectionUtil.nmsClass("ArgumentVectorPosition");
                vec3 = ReflectionUtil.nmsClass("Vec3D");
            }
            Class<?> vanillaCommandWrapper = ReflectionUtil.obcClass("command.VanillaCommandWrapper");
            Class<?> craftCommandMap = ReflectionUtil.obcClass("command.CraftCommandMap");
            Class<?> craftServer = ReflectionUtil.obcClass("CraftServer");

            // distinct obfuscated names
            if (ReflectionUtil.minecraftVersion() >= 19) {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "g");
            } else if (ReflectionUtil.minecraftVersion() == 18) {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "f");
            } else {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "getEntity");
            }
            // same obfuscated names
            if (ReflectionUtil.minecraftVersion() > 17) {
                ENTITY_ARGUMENT_ENTITIES_METHOD = getMethod(argumentEntity, "b");
                ENTITY_SELECTOR_FIND_ENTITIES_METHOD = getMethod(entitySelector, "b", commandListenerWrapper);
                GET_X = getMethod(vec3, "a");
                GET_Y = getMethod(vec3, "b");
                GET_Z = getMethod(vec3, "c");
            } else {
                ENTITY_ARGUMENT_ENTITIES_METHOD = argumentEntity.getDeclaredMethod("multipleEntities");
                ENTITY_SELECTOR_FIND_ENTITIES_METHOD = entitySelector.getDeclaredMethod("getEntities", commandListenerWrapper);
                GET_X = getMethod(vec3, "getX");
                GET_Y = getMethod(vec3, "getY");
                GET_Z = getMethod(vec3, "getZ");
            }
            LOCAL_COORD_GET_POSITION_METHOD = getMethod(localCoordinates, "a", commandListenerWrapper);
            ENTITY_ARGUMENT_PARSE_METHOD = getMethod(argumentEntity, "parse", StringReader.class, boolean.class); // craftbukkit method (without boolean, obf name is a)
            GET_BUKKIT_SENDER_METHOD = getMethod(commandListener, "getBukkitSender", commandListenerWrapper); // craftbukkit method
            GET_BUKKIT_BASED_SENDER_METHOD = getMethod(commandListenerWrapper, "getBukkitSender"); // craftbukkit method
            GET_LISTENER = getMethod(vanillaCommandWrapper, "getListener", CommandSender.class); // not NMS, craftbukkit package
            GET_KNOW_COMMANDS_METHOD = getMethod(craftCommandMap, "getKnownCommands"); // not NMS, craftbukkit package
            GET_COMMAND_MAP_METHOD = getMethod(craftServer, "getCommandMap");

            LOCAL_COORD_CONSTRUCTOR = localCoordinates.getConstructor(double.class, double.class, double.class);
            LOCAL_COORD_CONSTRUCTOR.setAccessible(true);

            // If getBukkitLocation doesn't exist (i.e. server is not running on Paper), use obfuscated methods
            if (Arrays.stream(commandListenerWrapper.getDeclaredMethods())
                    .map(Method::getName)
                    .anyMatch(m -> m.equals("getBukkitLocation"))) {
                GET_BUKKIT_LOCATION_METHOD = getMethod(commandListenerWrapper, "getBukkitLocation"); // Paper method
                GET_POSITION = null;
                GET_LEVEL = null;
                GET_ROTATION = null;
                SERVER_LEVEL_GET_WORLD = null;
                X = null;
                Y = null;
            } else {
                GET_BUKKIT_LOCATION_METHOD = null;
                Class<?> level, vec2;
                if (ReflectionUtil.minecraftVersion() > 16) {
                    level = ReflectionUtil.mcClass("world.level.World");
                    vec2 = ReflectionUtil.mcClass("world.phys.Vec2F");
                } else {
                    level = ReflectionUtil.nmsClass("World");
                    vec2 = ReflectionUtil.nmsClass("Vec2F");
                }
                SERVER_LEVEL_GET_WORLD = getMethod(level, "getWorld");
                X = vec2.getDeclaredField("i");
                Y = vec2.getDeclaredField("j");
                X.setAccessible(true);
                Y.setAccessible(true);
                if (ReflectionUtil.minecraftVersion() >= 19) {
                    GET_POSITION = getMethod(commandListenerWrapper, "e");
                    GET_LEVEL = getMethod(commandListenerWrapper, "f");
                    GET_ROTATION = getMethod(commandListenerWrapper, "l");
                } else if (ReflectionUtil.minecraftVersion() == 18) {
                    GET_POSITION = getMethod(commandListenerWrapper, "d");
                    GET_LEVEL = getMethod(commandListenerWrapper, "e");
                    GET_ROTATION = getMethod(commandListenerWrapper, "i");
                } else {
                    GET_POSITION = getMethod(commandListenerWrapper, "getPosition");
                    GET_LEVEL = getMethod(commandListenerWrapper, "getWorld");
                    GET_ROTATION = getMethod(commandListenerWrapper, "i");
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(name, params);
        method.setAccessible(true);
        return method;
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
            if (GET_BUKKIT_LOCATION_METHOD != null) {
                // problem with local coordinates that does not work because pos has nul yaw & pitch. We use local coordinates
                // with the commandWrapperListener (cf. getLocalCoord) to avoid to get rotation and anchor by hand.
                return (Location) GET_BUKKIT_LOCATION_METHOD.invoke(commandWrapperListener);
            } else {
                World world = (World) SERVER_LEVEL_GET_WORLD.invoke(GET_LEVEL.invoke(commandWrapperListener));
                Object pos = GET_POSITION.invoke(commandWrapperListener);
                Object rot = GET_ROTATION.invoke(commandWrapperListener);
                if (world == null || pos == null) {
                    return null;
                }
                float yaw = rot != null ? (float) X.get(rot) : 0;
                float pitch = rot != null ? (float) Y.get(rot) : 0;
                return new Location(world, (double) GET_X.invoke(pos), (double) GET_Y.invoke(pos), (double) GET_Z.invoke(pos), yaw, pitch);
            }
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
