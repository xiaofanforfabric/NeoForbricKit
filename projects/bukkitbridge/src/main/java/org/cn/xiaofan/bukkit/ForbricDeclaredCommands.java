/*
 * Forbric Bukkit bridge — plugin.yml commands, in the command map and in the game's dispatcher.
 */
package org.cn.xiaofan.bukkit;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.forbric.kernel.util.ForbricLog;
import net.minecraft.commands.CommandSourceStack;

/**
 * Turns a plugin's {@code plugin.yml} {@code commands:} section into commands this server can run.
 *
 * <h2>Why this is a separate path from {@code Commands.register}</h2>
 *
 * <p>Bukkit has two ways to declare a command and they are not the same thing:
 *
 * <ul>
 *   <li>A modern Paper plugin calls {@code LifecycleEvents.COMMANDS} and builds a Brigadier tree. That
 *       path goes through {@link ForbricCommands}, and the plugin gets the API's argument types and
 *       resolvers.
 *   <li>A traditional plugin writes the command's name, aliases and permission in {@code plugin.yml}
 *       and then calls {@code getCommand("name").setExecutor(...)} in {@code onEnable}. There is no
 *       tree: the plugin receives {@code (sender, label, String[] args)} and parses the words itself.
 * </ul>
 *
 * <p>Only the first was wired up. The second declared nothing the game could see, and the failure was
 * quiet in the worst way: {@code getCommand(name)} answered <b>null</b> on a server whose plugin had
 * just declared that command, so a plugin calling {@code setExecutor} on the result threw
 * {@code NullPointerException} inside its own {@code onEnable} — a stack trace naming the plugin, not
 * the bridge. Every plugin written before Paper's Brigadier API was affected, which is most of them.
 *
 * <h2>Two jobs, and why both are needed</h2>
 *
 * <p>A declared command has to exist in two places at once:
 *
 * <ol>
 *   <li><b>The command map</b>, so {@code plugin.getCommand(name)} answers with a command the plugin
 *       can attach its executor to. This is Bukkit's own registry and the only thing a traditional
 *       plugin ever touches.
 *   <li><b>The game's dispatcher</b>, so typing the command reaches that executor at all.
 * </ol>
 *
 * <p>Doing the first alone leaves a command that exists and cannot be run; the second alone leaves a
 * node with no executor and a {@code getCommand} that still answers null. Both are done here, and the
 * node looks the command up per invocation rather than capturing an executor — so
 * {@code setExecutor} works whenever the plugin calls it, which is the order every plugin uses.
 *
 * <h2>The {@link PluginCommand} is built reflectively</h2>
 *
 * <p>Its constructor is package-private in {@code org.bukkit.command}, so it cannot be called from
 * here, and a subclass is not an option either: the class is {@code final}. Reflection is what
 * remains, and it is not a hazard here — the class, the constructor and this bridge all come from the
 * same paper-api jar on the same loader, so there is no lookup that can resolve to a different class
 * than {@link SimpleCommandMap#register} expects.
 */
final class ForbricDeclaredCommands {

    /** The greedy argument's name. */
    private static final String ARGUMENTS = "args";

    /** {@code PluginCommand(String, Plugin)}, resolved once — a miss here is a broken paper-api. */
    private static Constructor<PluginCommand> constructor;

    private ForbricDeclaredCommands() {
    }

    /**
     * Registers every command this plugin declared.
     *
     * <p>Called after the lifecycle handlers, so a plugin that declares a command in both places has
     * already built its Brigadier tree and this cannot shadow it: an existing label is left alone and
     * said so in the log. On a real server the {@code plugin.yml} entries go in first and a Brigadier
     * registration merges into the same label, so the tree is the richer definition either way.
     */
    /**
     * Registers the Bukkit command objects before {@code JavaPlugin.onEnable()}.
     * Traditional plugins obtain these objects in onEnable and attach their executor and completer there.
     */
    static void installCommandMap(PluginDescriptionFile description, Plugin plugin, ServerImpl server) {
        Map<String, Map<String, Object>> declared = description.getCommands();
        if (declared == null || declared.isEmpty()) return;

        SimpleCommandMap commandMap = commandMapOf(server);
        if (commandMap == null) {
            ForbricLog.warn("[Forbric/Bukkit] %s declares %d command(s) in plugin.yml but no command map is "
                    + "available, so getCommand() keeps answering null and the plugin's onEnable may fail on it",
                    description.getName(), declared.size());
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : declared.entrySet()) {
            String label = entry.getKey();
            Map<String, Object> properties = entry.getValue() == null ? Map.of() : entry.getValue();

            PluginCommand command = newPluginCommand(label, plugin, properties);
            if (command == null) continue;

            // Bukkit's own registry, under the fallback prefix the plugin's name gives it. register() is what
            // makes getCommand() and the alias lookup work.
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
        }
    }

    /** Installs the already-created command objects into the game's dispatcher after onEnable. */
    static void install(CommandDispatcher<CommandSourceStack> dispatcher, PluginDescriptionFile description,
                    Plugin plugin, ServerImpl server) {
        Map<String, Map<String, Object>> declared = description.getCommands();
        if (declared == null || declared.isEmpty()) return;

        SimpleCommandMap commandMap = commandMapOf(server);
        if (commandMap == null) return;

        for (Map.Entry<String, Map<String, Object>> entry : declared.entrySet()) {
            String label = entry.getKey();
            Map<String, Object> properties = entry.getValue() == null ? Map.of() : entry.getValue();
            List<String> aliases = aliasesOf(properties);

            // The game's dispatcher, once per label. A Brigadier tree that already owns one of these names keeps
            // it: the tree is a richer definition of the same command and the plugin asked for both.
            for (String name : labels(label, aliases)) {
                if (dispatcher.getRoot().getChild(name) != null) {
                    ForbricLog.info("[Forbric/Bukkit] %s declares /%s in plugin.yml, but a Brigadier tree "
                            + "already defines it — the tree stays", description.getName(), name);
                    continue;
                }
                LiteralCommandNode<CommandSourceStack> built = node(name, commandMap, server);
                dispatcher.getRoot().addChild(built);
                ForbricLog.info("[Forbric/Bukkit] installed /%s for %s — children=%s runnable=%s",
                        name, description.getName(),
                        built.getChildren().stream().map(node -> node.getName()).toList(),
                        built.getCommand() != null);
            }
        }
    }

    /** The server's command map, or null when the bridge has not built one. */
    private static SimpleCommandMap commandMapOf(ServerImpl server) {
        try {
            return server.commandMap();
        } catch (RuntimeException | Error unavailable) {
            return null;
        }
    }

    /**
     * One {@link PluginCommand}, built through the package-private constructor.
     *
     * <p>The permission and its message come off the {@code plugin.yml} entry because
     * {@code PluginCommand.execute} is where Bukkit checks the permission before running an executor.
     * Leaving them off would let a sender who was declared out of the command run it.
     *
     * <p><b>The aliases have to be set here, not only in the dispatcher tree.</b>
     * {@code SimpleCommandMap.register} reads {@code command.getAliases()} to decide which extra names go
     * into {@code knownCommands}, so a command whose aliases were left empty is one the map can only answer
     * for its primary name. The symptom was precise and misleading: {@code /osc} appeared in the client's
     * completion and its packet arrived (the tree does carry the alias), but the lookup
     * {@code commandMap.getCommand("osc")} inside the executor answered null, so the command reported "no
     * longer loaded" and did nothing. The alias was reachable in one registry and absent from the other.
     */
    private static PluginCommand newPluginCommand(String label, Plugin plugin, Map<String, Object> properties) {
        try {
            if (constructor == null) {
                constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                constructor.setAccessible(true);
            }
            PluginCommand command = constructor.newInstance(label, plugin);
            // Before any registration: register() copies getAliases() into knownCommands, so this list is
            // read once, at register time, and setting it later would leave the map still missing the names.
            List<String> aliases = aliasesOf(properties);
            if (!aliases.isEmpty()) command.setAliases(aliases);
            String usage = string(properties.get("usage"));
            if (usage != null) command.setUsage(usage);
            String description = string(properties.get("description"));
            if (description != null) command.setDescription(description);
            String permission = string(properties.get("permission"));
            if (permission != null) {
                command.setPermission(permission);
                String message = string(properties.get("permission-message"));
                if (message != null) command.setPermissionMessage(message);
            }
            return command;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            ForbricLog.warn("[Forbric/Bukkit] could not build a PluginCommand for /%s declared by %s (%s) — "
                    + "getCommand(\"%s\") answers null for it, and a plugin that calls setExecutor on that "
                    + "result fails in its own onEnable", label, plugin.getName(),
                    unavailable.getClass().getName(), label);
            return null;
        }
    }

    /** A {@code plugin.yml} string value, or null when absent or blank. */
    private static String string(Object raw) {
        if (raw == null) return null;
        String value = raw.toString();
        return value.isBlank() ? null : value;
    }

    /** The labels one declared command answers to: its own name first, then its aliases. */
    private static Collection<String> labels(String label, List<String> aliases) {
        List<String> all = new ArrayList<>();
        all.add(label);
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank() && !all.contains(alias)) all.add(alias);
        }
        return all;
    }

    /** The {@code aliases} list from a {@code plugin.yml} entry, which is a YAML list or a single string. */
    private static List<String> aliasesOf(Map<String, Object> properties) {
        Object raw = properties.get("aliases");
        if (raw instanceof String single) {
            return single.isBlank() ? List.of() : List.of(single.trim());
        }
        if (raw instanceof Collection<?> many) {
            List<String> aliases = new ArrayList<>();
            for (Object one : many) {
                if (one != null) aliases.add(one.toString());
            }
            return aliases;
        }
        return List.of();
    }

    /**
     * One label's node: the bare label, plus a greedy child for everything typed after it.
     *
     * <p>The child is {@code greedyString} because a {@code CommandExecutor} parses its own words and a
     * greedy argument is the only one that imposes no grammar — it takes the rest of the line, spaces
     * included. Nothing about what the command accepts can be known here; the plugin decides.
     *
     * <p>The child also carries {@code suggests}, which is what makes {@code /label <TAB>} offer the
     * plugin's own completions. Without it the client is sent a tree whose only child is an argument
     * with no completer, so the game answers with the player names it always suggests for a bare string
     * (or nothing at all) and the plugin's {@code TabCompleter} is asked for nothing. The suggestions
     * come from the {@code PluginCommand}, so they reflect whatever {@code setTabCompleter} was given.
     */
    private static LiteralCommandNode<CommandSourceStack> node(String name, SimpleCommandMap commandMap,
                    ServerImpl server) {
        LiteralArgumentBuilder<CommandSourceStack> builder = LiteralArgumentBuilder
                .<CommandSourceStack>literal(name)
                .executes(context -> run(context, commandMap, name, new String[0], server))
                .then(RequiredArgumentBuilder
                        .<CommandSourceStack, String>argument(ARGUMENTS, StringArgumentType.greedyString())
                        .suggests((context, proposals) ->
                                suggest(context, commandMap, name, server, proposals))
                        .executes(context -> run(context, commandMap, name,
                                words(StringArgumentType.getString(context, ARGUMENTS)), server)));
        return builder.build();
    }

    /**
     * The plugin's own completions for the word being typed.
     *
     * <p><b>The parameter shape is Bukkit's, and getting it wrong makes every completion empty.</b>
     * {@code Command.tabComplete0} reads {@code args[args.length - 1]} as the word under the cursor and
     * {@code args[0] .. args[length - 2]} as the words already closed — so the array handed in must
     * ALREADY carry that last partial word. The greedy argument holds the whole typed tail, so splitting
     * it is what produces that shape.
     *
     * <p>The mistake this replaces was appending {@code proposals.getRemaining()} on top of the words
     * parsed from the greedy argument — but that argument already contains it, so every request arrived
     * one word too long. A completer that branches on {@code args.length} (as most do,
     * {@code args.length == 1} for the first word) fell through to its default case and answered nothing,
     * for every keystroke, with no error anywhere. The command completed to nothing and read as a bridge
     * failure.
     *
     * <p>Never throws: a completer that does is the plugin's bug, and failing the completion packet
     * leaves the client with a dead TAB key. An empty answer is the honest one.
     */
    private static java.util.concurrent.CompletableFuture<
                    com.mojang.brigadier.suggestion.Suggestions> suggest(
                                    CommandContext<CommandSourceStack> context, SimpleCommandMap commandMap,
                                    String name, ServerImpl server,
                                    com.mojang.brigadier.suggestion.SuggestionsBuilder proposals) {
        org.bukkit.command.Command command = commandMap.getCommand(name);
        if (command == null) {
            return proposals.buildFuture();
        }
        String[] args;
        try {
            String typed = StringArgumentType.getString(context, ARGUMENTS);
            args = words(typed);
            // The array must END at the word under the cursor, because that is what
            // Command.tabComplete0 tells a completer it is getting: it reads args[args.length - 1] as the
            // partial word. Two shapes reach here and they need different treatment:
            //
            //   "give ni"  -> words() gives ["give", "ni"]  -- already ended at the partial word. Done.
            //   "give "    -> words() gives ["give"]        -- the cursor is past a space, so the partial
            //                                                 word is EMPTY and must be appended.
            //   ""         -> words() gives []              -- same thing at position 1: the player has
            //                                                 typed nothing yet and is asking what may start
            //                                                 the command.
            //
            // Without the append the plugin sees one word fewer than the position it is being asked about,
            // so a completer branching on `args.length == 1` for the FIRST word is asked with length 0 and
            // answers nothing -- for every keystroke. The client then falls back to the vanilla player-name
            // suggestion (Server.suggestPlayerNamesWhenNullTabCompletions, true by default) and offers
            // names where the subcommands should be.
            if (typed.isEmpty() || typed.endsWith(" ")) {
                args = java.util.Arrays.copyOf(args, args.length + 1);
                args[args.length - 1] = "";
            }
        } catch (RuntimeException | Error unreadable) {
            args = new String[] { "" };
        }
        try {
            CommandSender sender = ForbricCommandSender.senderFor(context.getSource(), server);
            java.util.List<String> answers = command.tabComplete(sender, name, args);
            // Keep this at INFO while the legacy command bridge is being validated: an empty answer is
            // otherwise indistinguishable from a missing TabCompleter, and Bukkit then falls back to player
            // names. The parameter shape is the contract we need to verify against the plugin.
            ForbricLog.info("[Forbric/Bukkit] tabComplete /%s args=%s answers=%s", name,
                            java.util.Arrays.toString(args), answers);
            if (answers != null) {
                for (String completion : answers) {
                    if (completion != null) proposals.suggest(completion);
                }
            }
        } catch (RuntimeException | Error pluginBug) {
            ForbricLog.warn("[Forbric/Bukkit] %s threw while completing /%s: %s", name,
                    pluginBug.getClass().getName(), pluginBug.getMessage());
        }
        return proposals.buildFuture();
    }

    /**
     * Runs the command through the map, not through a captured executor.
     *
     * <p>Looking it up per invocation is what makes {@code setExecutor} work whenever the plugin calls
     * it, including after its {@code onEnable} has returned. A node holding the executor it saw at
     * registration time would keep running the first one, and for a plugin that sets its executors
     * late it would be running null.
     *
     * <p>The map is asked for the typed name, so an alias runs under the alias: Bukkit's own dispatch
     * passes the label as typed, and a plugin that branches on it must see what the player wrote.
     */
    private static int run(CommandContext<CommandSourceStack> context, SimpleCommandMap commandMap,
                    String name, String[] args, ServerImpl server) {
        org.bukkit.command.Command command = commandMap.getCommand(name);
        if (command == null) {
            // Registered at enable and gone now: the plugin was disabled. Saying so beats a silent no-op that
            // looks like a permission refusal.
            ForbricLog.warn("[Forbric/Bukkit] /%s was declared by a plugin that is no longer loaded", name);
            return 0;
        }
        // senderFor, NOT of: a player typing the command must reach the plugin AS that player. Every
        // traditional plugin branches on `sender instanceof Player`, and a bare CommandSender fails
        // that test, so the plugin would take its console path -- refusing a player-only subcommand,
        // or answering into a source the player's client never renders. This is the same reason
        // ForbricCommands hands out senderFor, and the two paths must agree.
        CommandSender sender = ForbricCommandSender.senderFor(context.getSource(), server);
        boolean handled;
        try {
            handled = command.execute(sender, name, args);
        } catch (RuntimeException | Error pluginBug) {
            // A plugin that throws here would otherwise be a silently dead command: Brigadier turns the
            // exception into a failed dispatch with no message to anyone. Naming the plugin is the only
            // way the operator learns which one it was.
            ForbricLog.warn("[Forbric/Bukkit] %s threw while running /%s %s: %s",
                    command instanceof PluginCommand plugin ? plugin.getPlugin().getName() : name,
                    name, java.util.Arrays.toString(args), pluginBug);
            if (pluginBug instanceof org.bukkit.command.CommandException commandException
                            && commandException.getCause() != null) {
                server.getLogger().log(java.util.logging.Level.SEVERE,
                                "[Forbric/Bukkit] command cause for /" + name, commandException.getCause());
            } else {
                server.getLogger().log(java.util.logging.Level.SEVERE,
                                "[Forbric/Bukkit] command failure for /" + name, pluginBug);
            }
            return 0;
        }
        return handled ? 1 : 0;
    }

    /**
     * The words after the label, with quoting resolved.
     *
     * <p>{@code greedyString} keeps the quotes as typed, so the run is handed to a fresh reader and read
     * word by word — the same rules the game's parser applied to the line before this node saw it.
     * {@code /kit give "my kit"} is two words, and splitting the raw tail on spaces would make it three.
     */
    private static String[] words(String rest) {
        if (rest == null || rest.isBlank()) return new String[0];
        com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(rest);
        List<String> words = new ArrayList<>();
        while (reader.canRead()) {
            reader.skipWhitespace();
            if (!reader.canRead()) break;
            try {
                words.add(reader.readString());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException unreadable) {
                // The line could not be re-read even though the game already read it, so the tail is one word.
                // Losing the quoting is better than losing the argument.
                words.add(rest.substring(reader.getCursor()).trim());
                break;
            }
        }
        return words.toArray(new String[0]);
    }
}
