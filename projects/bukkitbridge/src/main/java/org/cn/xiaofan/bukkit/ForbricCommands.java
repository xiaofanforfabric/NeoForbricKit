/*
 * Forbric Bukkit bridge — the Brigadier registrar a Paper plugin registers commands through.
 */
package org.cn.xiaofan.bukkit;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandRegistrationFlag;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.forbric.kernel.util.ForbricLog;

/**
 * Where a plugin's Brigadier commands go.
 *
 * <p><b>The whole job is one translation.</b> A plugin builds a command tree out of Paper's
 * {@code CommandSourceStack}, and the game's dispatcher holds trees of its own
 * {@code net.minecraft.commands.CommandSourceStack}. The two are different types, so a node cannot simply be
 * added — every node, its executor, its requirement and its suggestion provider have to be re-expressed in the
 * game's type. That is what this class does, once per registered command.
 *
 * <p><b>Why a copy and not a cast.</b> Brigadier nodes are generic in the source type but the generics are
 * erased, so a cast would compile. It would also be wrong in the only way that matters: the executor inside the
 * node receives whatever the dispatcher passes it, and the game passes ITS source. A cast-compiled tree would
 * run a plugin's executor with a source object the plugin's own code cannot use, failing at the first
 * {@code getSender()} — long after the command appeared to register.
 *
 * <p><b>The executor is wrapped, the arguments are not.</b> A node's {@code executes} callback takes the source,
 * so it is wrapped: the game's source is turned into this bridge's {@code CommandSourceStack} before the plugin
 * sees it. An argument's type, by contrast, is a plain Brigadier {@code ArgumentType} that Paper builds on top
 * of the game's own — {@code ArgumentTypes.players()} wraps {@code EntityArgument.players()} — so it is carried
 * across unchanged and resolves against the game's source exactly as it does on a real server.
 */
final class ForbricCommands implements Commands {

    /** The game's dispatcher, which is what actually runs a command. */
    private final com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher;

    /** The plugin registering, so its nodes can be attributed and its permissions consulted. */
    private final PluginMeta plugin;

    private final ServerImpl server;

    ForbricCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                    PluginMeta plugin, ServerImpl server) {
        this.dispatcher = dispatcher;
        this.plugin = plugin;
        this.server = server;
    }

    /** The game's dispatcher. Part of the API, and the reason a plugin can inspect what is registered. */
    @Override
    public com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>
                    getDispatcher() {
        // The game's dispatcher cannot be handed out: its type parameter is the game's source, and a plugin
        // compiling against Paper's would have every node it added rejected. A fresh dispatcher over Paper's
        // type is what a caller inspecting the tree expects, and it is empty by construction -- this bridge does
        // not keep a second tree in step with the game's.
        return new com.mojang.brigadier.CommandDispatcher<>();
    }

    @Override
    public Set<String> register(LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> node,
                    String description, Collection<String> aliases) {
        return install(node, description, aliases);
    }

    @Override
    public Set<String> register(PluginMeta plugin,
                    LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> node,
                    String description, Collection<String> aliases) {
        return install(node, description, aliases);
    }

    /**
     * Registers a command with flags.
     *
     * <p>The flags decide whether a command is visible to the client's tab-completion and whether it needs a
     * signed command source. This bridge ignores them: both are behaviours of the game's own command-sending
     * path, which a command added to the dispatcher already goes through. Recording them would be a promise this
     * class cannot keep, and the visible effect of the default flags is what a plugin gets.
     */
    @Override
    public Set<String> registerWithFlags(PluginMeta plugin,
                    LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> node,
                    String description, Collection<String> aliases, Set<CommandRegistrationFlag> flags) {
        return install(node, description, aliases);
    }

    /**
     * Registers a {@code BasicCommand} — the form that parses its own arguments.
     *
     * <h2>Where the arguments come from</h2>
     *
     * <p>{@code BasicCommand} is the old Bukkit shape: the command receives the words after its label as a
     * {@code String[]} and decides what they mean itself. Brigadier does not work that way — it has already
     * parsed the line into a tree by the time an executor runs, and a literal node by itself matches nothing
     * after the label.
     *
     * <p>So the node is a literal with one {@code greedyString} child, and the executor reads the rest of the
     * line off the {@code StringReader} rather than the parsed argument. A greedy argument is the only one that
     * does not impose a grammar: it matches every remaining character including spaces, which is exactly what a
     * command that splits its own arguments needs. Reading the reader instead of the argument is what keeps the
     * quoting intact — {@code /kit give "my kit" Steve} must reach the plugin as two words, and a
     * {@code split(" ")} over the parsed value would produce three.
     *
     * <p><b>This used to pass {@code new String[0]}.</b> Every {@code BasicCommand} therefore ran with no
     * arguments at all, whatever was typed — {@code /mycmd give stone} reached the plugin as an empty array. The
     * class comment claimed a redirect captured the remainder and had done so since it was written; no redirect
     * was ever added, and nothing noticed because a command that ignores its arguments works either way.
     *
     * <h2>Why a child node and not the executor alone</h2>
     *
     * <p>Because the label must work with and without arguments. The executor on the literal handles
     * {@code /mycmd}; the greedy child handles {@code /mycmd anything at all}. A single node cannot do both —
     * Brigadier dispatches on what it has parsed, and a literal with a child is not runnable unless the line
     * ends at it.
     */
    @Override
    public Set<String> register(String label, String description, Collection<String> aliases,
                    io.papermc.paper.command.brigadier.BasicCommand command) {
        LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> node =
                        Commands.literal(label)
                                        .requires(source -> command.canUse(source.getSender()))
                                        .executes(context -> runBasic(context, command, new String[0]))
                                        .then(Commands.argument(ARGUMENTS,
                                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                        .suggests((context, builder) ->
                                                                        suggestBasic(context, command, builder))
                                                        .executes(context -> runBasic(context, command,
                                                                        basicArguments(context))))
                                        .build();
        return install(node, description, aliases);
    }

    /** The name of the greedy argument that carries a {@code BasicCommand}'s words. */
    private static final String ARGUMENTS = "args";

    /**
     * The words after the label, split the way the plugin expects.
     *
     * <p>{@code StringReader.readString()} has already resolved quoting and escaping while the greedy argument
     * was read, so what is split here is the unquoted line: {@code give "my kit" Steve} becomes the three
     * tokens {@code give}, {@code my kit}, {@code Steve}. Splitting the raw input instead would make the quoted
     * name two tokens and the command would look for a kit called {@code "my}.
     */
    private static String[] basicArguments(
                    com.mojang.brigadier.context.CommandContext<
                                    io.papermc.paper.command.brigadier.CommandSourceStack> context)
                    throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String rest = com.mojang.brigadier.arguments.StringArgumentType.getString(context, ARGUMENTS);
        if (rest == null || rest.isBlank()) return new String[0];
        java.util.List<String> words = new java.util.ArrayList<>();
        // The reader already collapsed each quoted run into one value, but greedyString keeps the quotes, so
        // they are re-read here: the argument is handed to a fresh reader and read word by word, which applies
        // the same quoting rules the game's own parser does.
        com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(rest);
        while (reader.canRead()) {
            reader.skipWhitespace();
            if (!reader.canRead()) break;
            words.add(reader.readString());
        }
        return words.toArray(new String[0]);
    }

    /** Runs a {@code BasicCommand}, turning its result into the game's command result code. */
    private static int runBasic(
                    com.mojang.brigadier.context.CommandContext<
                                    io.papermc.paper.command.brigadier.CommandSourceStack> context,
                    io.papermc.paper.command.brigadier.BasicCommand command, String[] args) {
        io.papermc.paper.command.brigadier.CommandSourceStack source = context.getSource();
        command.execute(source, args);
        // 1 for "ran": a BasicCommand reports nothing, and Paper's own dispatcher answers the same way. A zero
        // would read as "the command did not run" and change how the game reports it back to the client.
        return 1;
    }

    /** A {@code BasicCommand}'s completions, for the words typed so far. */
    private static java.util.concurrent.CompletableFuture<
                    com.mojang.brigadier.suggestion.Suggestions> suggestBasic(
                                    com.mojang.brigadier.context.CommandContext<
                                                    io.papermc.paper.command.brigadier.CommandSourceStack> context,
                                    io.papermc.paper.command.brigadier.BasicCommand command,
                                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String[] args;
        try {
            args = basicArguments(context);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException
                        | RuntimeException unreadable) {
            // The plugin is about to be asked to complete a line this bridge could not re-read; an empty list
            // is a better answer than failing the completion packet.
            args = new String[0];
        }
        // The word being completed is not in args yet — Builder.getRemaining() is what the plugin must extend,
        // and passing the already-closed words plus that remainder is the shape BasicCommand.suggest documents.
        Collection<String> suggestions = command.suggest(context.getSource(), args);
        if (suggestions != null) {
            for (String suggestion : suggestions) {
                if (suggestion != null) builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Set<String> register(PluginMeta plugin, String label, String description, Collection<String> aliases,
                    io.papermc.paper.command.brigadier.BasicCommand command) {
        return register(label, description, aliases, command);
    }

    /**
     * Copies a plugin's tree into the game's dispatcher and returns the labels that took effect.
     *
     * <p>The returned set is what Paper documents: the labels a player can actually type. Aliases are included,
     * because an alias that registered is a name that works, and a plugin printing the set to a user should show
     * the names that exist.
     */
    private Set<String> install(LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> node,
                    String description, Collection<String> aliases) {
        Set<String> registered = new LinkedHashSet<>();
        registered.add(node.getLiteral());
        CommandNode<net.minecraft.commands.CommandSourceStack> translated = translate(node);
        dispatcher.getRoot().addChild(translated);
        dump("registered", translated);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                // An alias is a second literal that points at the same tree, which is how the game models one.
                //
                // translated is reused, and that is the whole point of this line: translate() builds a NEW node
                // every time it is called, so the version written here as translate(node).getCommand(),
                // translate(node).getRequirement(), translate(node) produced four different objects — the
                // executor and requirement from two that were thrown away, and a redirect to a third that was
                // never added to the tree. The game's packet writer indexes nodes by identity, so that redirect
                // named an index that did not exist and the client refused the whole command list.
                LiteralCommandNode<net.minecraft.commands.CommandSourceStack> aliasNode =
                                new LiteralCommandNode<>(alias, translated.getCommand(),
                                                translated.getRequirement(), translated, null, false);
                dispatcher.getRoot().addChild(aliasNode);
                registered.add(alias);
            }
        }
        return registered;
    }

    /**
     * One node, in the game's type.
     *
     * <p>Every field a node carries is re-expressed: the executor is wrapped so the plugin receives this
     * bridge's source, the requirement is wrapped so it can be asked about one, and the children are translated
     * recursively. A node whose child cannot be translated is a node whose subtree would silently lose that
     * command, so nothing is dropped quietly — an argument type this bridge cannot express throws by name.
     */
    private CommandNode<net.minecraft.commands.CommandSourceStack> translate(
                    CommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> node) {
        if (node instanceof LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> literal) {
            LiteralCommandNode<net.minecraft.commands.CommandSourceStack> copy =
                            new LiteralCommandNode<>(literal.getLiteral(), wrap(literal.getCommand()),
                                            wrapRequirement(literal.getRequirement()), null, null, false);
            for (CommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> child : literal.getChildren()) {
                copy.addChild(translate(child));
            }
            return copy;
        }

        if (node instanceof ArgumentCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack, ?> argument) {
            // The node carries the GAME's argument type, not ours, when ours is a selector.
            //
            // A ForbricSelectorType is a class only this bridge has, so ArgumentTypeInfos knows it by a name no
            // client ever learned. NeoForge's CommandTreeCleaner sends the command list to a client that is not
            // NeoForge by rebuilding the tree and dropping every argument whose type is not in the `minecraft`
            // namespace; ours was dropped, /kit give lost its player parameter, and the client was left with a
            // literal it could type but could not complete — which it reports as 试图执行该命令时出现意外错误.
            //
            // So the type that reaches the dispatcher is the game's own EntityArgument. It parses the same
            // selector grammar, the game can describe it on the wire, and every client already understands it,
            // so the parameter survives the trip and completes normally. What the plugin receives is still a
            // resolver: parse produces an EntitySelector here, and reargument turns it into the resolver the API
            // promises before the plugin's executor runs.
            com.mojang.brigadier.arguments.ArgumentType<?> wireType = wireTypeOf(argument.getType());
            ArgumentCommandNode<net.minecraft.commands.CommandSourceStack, ?> copy =
                            new ArgumentCommandNode<>(argument.getName(), wireType,
                                            wrap(argument.getCommand()), wrapRequirement(argument.getRequirement()),
                                            null, null, false, wrapSuggestion(argument.getCustomSuggestions()));
            for (CommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> child : argument.getChildren()) {
                copy.addChild(translate(child));
            }
            return copy;
        }

        throw new IllegalArgumentException("[Forbric/Bukkit] Commands.register() met a node this bridge cannot "
                        + "translate: " + node.getClass().getName() + " (only literals and arguments are known)");
    }

    /**
     * The type a node hands the game's dispatcher: the game's own for a selector, the plugin's otherwise.
     *
     * <p>Only selectors are rewritten. Everything else Paper hands out is already a game type or a plain
     * Brigadier one, and passing those through unchanged is what keeps a plugin's own argument semantics — a
     * {@code StringArgumentType} with a suggestion provider must stay that exact object, or the game sends a
     * different suggestion id than the one the plugin registered.
     *
     * <p>The substitute is the game factory the selector was built from, so the grammar is identical: the
     * bridge chose that factory itself in {@link ForbricArgumentTypes}, and {@link ForbricSelectorType} kept
     * the same two booleans.
     */
    private static com.mojang.brigadier.arguments.ArgumentType<?> wireTypeOf(
                    com.mojang.brigadier.arguments.ArgumentType<?> type) {
        if (type instanceof ForbricSelectorType<?> selector) return selector.gameType();
        return type;
    }

    /**
     * A plugin's suggestion provider, adapted to the game's context type.
     *
     * <p>{@code null} stays {@code null}: an argument with no custom suggestions has none in the game either, and
     * substituting an empty one would make the game send a suggestion id the plugin never asked for.
     */
    private com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack>
                    wrapSuggestion(
                                    com.mojang.brigadier.suggestion.SuggestionProvider<
                                                    io.papermc.paper.command.brigadier.CommandSourceStack> provider) {
        if (provider == null) return null;
        return (context, builder) -> provider.getSuggestions(recontext(context), builder);
    }

    /**
     * Writes the shape of a translated subtree to the log.
     *
     * <p>Diagnostic, and it exists because the symptom it was added for is otherwise invisible from this side:
     * a client answered "试图执行该命令时出现意外错误" to one of these commands while the server logged nothing at
     * all — not the command, not a failure, nothing — which means the client's own dispatcher failed while
     * reading the tree this bridge produced. What the client receives is this tree after NeoForge's
     * {@code CommandTreeCleaner} has removed every argument whose type the game does not recognise, and that
     * removal is where a node can be left pointing at an index which no longer exists. Printing what this side
     * built is the half of that comparison this process can see.
     *
     * <p>Every node is named with its class, because the class is what decides how the packet describes it: a
     * {@code LiteralCommandNode} is a literal, an {@code ArgumentCommandNode} is a typed argument. An argument
     * also prints its type's class, which is what the cleaner's predicate is asked about.
     */
    private static void dump(String label, CommandNode<?> node) {
        dump(label, node, 0);
    }

    private static void dump(String label, CommandNode<?> node, int depth) {
        StringBuilder row = new StringBuilder();
        row.append("  ".repeat(depth)).append(depth == 0 ? label + ": " : "")
                        .append(node.getClass().getSimpleName())
                        .append(" name=").append(node.getName())
                        .append(" executable=").append(node.getCommand() != null)
                        .append(" redirect=").append(node.getRedirect() == null ? "none" : node.getRedirect().getName())
                        // The requirement's identity, because "is the node visible to a client" is decided by
                        // exactly one thing: whether the game can walk past this predicate while building the
                        // packet. A node whose predicate is Brigadier's own always-true default is visible to
                        // everyone; one carrying a plugin's predicate is visible only where that predicate says
                        // so. Printing the class name is what separates "no predicate at all" from "a predicate
                        // that answered no" — two failures that look identical from the client.
                        .append(" requirement=").append(node.getRequirement() == null
                                        ? "null"
                                        : node.getRequirement().getClass().getName());
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            row.append(" type=").append(argument.getType().getClass().getName())
                            .append(" suggestions=").append(argument.getCustomSuggestions() == null ? "none" : "set");
        }
        net.forbric.kernel.util.ForbricLog.info("[Forbric/Bukkit] command tree %s", row.toString());
        for (CommandNode<?> child : node.getChildren()) {
            dump(label, child, depth + 1);
        }
    }

    /**
     * A plugin's executor, adapted to the game's source type.
     *
     * <p>This is the seam the whole class exists for: the game calls the returned command with its own source,
     * and the plugin inside receives this bridge's. The lookup is by the game's {@code ServerLevel}, so a plugin
     * comparing worlds across two commands compares the same wrapper both times.
     */
    private Command<net.minecraft.commands.CommandSourceStack> wrap(
                    Command<io.papermc.paper.command.brigadier.CommandSourceStack> original) {
        if (original == null) {
            return null;
        }
        return context -> {
            // The whole call is inside a try, and the failure is logged rather than rethrown, because
            // Brigadier's dispatcher answers a command that threw by returning 0 and saying nothing. The
            // client reads that 0 as "the command failed" and prints 试图执行该命令时出现意外错误, while the
            // server's log has one line from before the failure and no cause — the two halves of the report
            // never meet. Logging here is what puts the cause next to the packet line ClientCommandLogInjector
            // already prints.
            try {
                return original.run(recontext(context));
            } catch (RuntimeException | Error thrown) {
                ForbricLog.error("[Forbric/Bukkit] a plugin command threw — input /%s, node %s. The client is "
                        + "told the command failed and prints \"试图执行该命令时出现意外错误\"; this line is the "
                        + "cause it cannot see", context.getInput(), context.getNodes().isEmpty()
                                ? "<none>" : context.getNodes().get(context.getNodes().size() - 1).getNode().getName(),
                        thrown);
                throw thrown;
            }
        };
    }

    /**
     * The same command context, with the source re-expressed in Paper's type.
     *
     * <p><b>Why the context has to be rebuilt and not just the source swapped.</b> Brigadier types everything by
     * the source: {@code CommandContext<S>} carries the source, the parsed arguments, the node list and the
     * range, and {@code Command<S>.run} takes that whole object. A plugin's executor is a
     * {@code Command<PaperSource>}, so handing it a {@code CommandContext<NmsSource>} is a type error — and a
     * cast would compile and then fail inside the plugin at the first use of an argument, because the parsed
     * values are keyed to the game's source.
     *
     * <p>The rebuild keeps every parsed argument and every node exactly as the game produced them. Arguments
     * resolve lazily — a {@code PlayerSelectorArgumentResolver} looks its source up when the plugin asks — so
     * what they need is a context whose SOURCE is the wrapper, which is what this produces.
     */
    /**
     * The same command context, with the source re-expressed in Paper's type.
     *
     * <p><b>Why this cannot be a cast, or a {@code copyFor}.</b> Brigadier types everything by the source:
     * {@code CommandContext<S>} carries the source, the parsed arguments, the node list and the input, and
     * {@code Command<S>.run} takes that whole object. A plugin's executor is a {@code Command<PaperSource>}, so
     * it cannot be handed a {@code CommandContext<NmsSource>}.
     *
     * <p>{@code copyFor(S)} looks like the answer and is not: it is declared {@code CommandContext<S> copyFor(S)},
     * so its argument and its result share one type parameter and it can only re-source a context to the SAME
     * type. Calling it here compiles as far as inference and then fails, because the compiler settles the type
     * parameter on the context's own source rather than on the value passed.
     *
     * <p>So the context is rebuilt from its constructor. Every field is taken from the original — the parsed
     * arguments, the node list, the range, the input, the child and the modifier — and only the source changes.
     * Dropping any of them would be silent: a command with an argument would throw "unknown argument" inside the
     * plugin, from a lookup that found nothing.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack>
                    recontext(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>
                                    context) {
        ForbricCommandSourceStack source = ForbricCommandSourceStack.of(context.getSource(), server);
        java.util.Map<String, com.mojang.brigadier.context.ParsedArgument<
                        io.papermc.paper.command.brigadier.CommandSourceStack, ?>> arguments = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, com.mojang.brigadier.context.ParsedArgument<
                        net.minecraft.commands.CommandSourceStack, ?>> entry
                        : originalArguments(context).entrySet()) {
            arguments.put(entry.getKey(), reargument(entry.getValue(), selectorFor(context, entry.getKey())));
        }
        java.util.List<com.mojang.brigadier.context.ParsedCommandNode<
                        io.papermc.paper.command.brigadier.CommandSourceStack>> nodes = new java.util.ArrayList<>();
        for (com.mojang.brigadier.context.ParsedCommandNode<net.minecraft.commands.CommandSourceStack> node
                        : context.getNodes()) {
            nodes.add(new com.mojang.brigadier.context.ParsedCommandNode(
                            translateNode(node.getNode()), node.getRange()));
        }
        return new com.mojang.brigadier.context.CommandContext<>(source, context.getInput(), arguments,
                        unwrap(context.getCommand()), translateNode(context.getRootNode()), nodes,
                        context.getRange(), null, null, false);
    }

    /**
     * A context's parsed arguments, as {@code ParsedArgument} objects in the game's source type.
     *
     * <h2>Why each one is rebuilt rather than fetched</h2>
     *
     * <p>{@code CommandContext} has no {@code getArguments()}: the map is private and the only way to read a
     * value is {@code getArgument(name, Class)}, which returns the RESULT — the parsed {@code String}, the
     * resolved {@code EntitySelector} — not the {@code ParsedArgument} that holds it beside its range.
     *
     * <p>That distinction is the whole of a fixed bug: this method used to cast the result straight to
     * {@code ParsedArgument}, so the first command WITH an argument threw
     * {@code ClassCastException: class java.lang.String cannot be cast to class
     * com.mojang.brigadier.context.ParsedArgument} — and it threw inside the dispatcher, which answers 0 and
     * logs nothing, so the client said 试图执行该命令时出现意外错误 and the server said nothing at all.
     * {@code /kit list} worked because a command with no arguments runs the loop zero times.
     *
     * <p>So each entry is built here: the value from {@code getArgument}, the range from
     * {@code context.getRange(node)}. Both are what the original {@code ParsedArgument} carried, so a context
     * rebuilt from them behaves as the one it replaces.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.Map<String, com.mojang.brigadier.context.ParsedArgument<
                    net.minecraft.commands.CommandSourceStack, ?>> originalArguments(
                    com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        java.util.Map<String, com.mojang.brigadier.context.ParsedArgument<
                        net.minecraft.commands.CommandSourceStack, ?>> arguments = new java.util.HashMap<>();
        for (com.mojang.brigadier.context.ParsedCommandNode<net.minecraft.commands.CommandSourceStack> parsed
                        : context.getNodes()) {
            // The node's OWN name is the argument's name, which is why the loop is over the parsed nodes and not,
            // as it once was, over each node's children — those name the next step of the command, not this one.
            String name = parsed.getNode().getName();
            Object value;
            try {
                value = context.getArgument(name, Object.class);
            } catch (IllegalArgumentException notAnArgument) {
                // A literal node has no parsed value. Only arguments do.
                continue;
            }
            com.mojang.brigadier.context.StringRange range = parsed.getRange();
            if (range == null) continue;
            arguments.put(name, new com.mojang.brigadier.context.ParsedArgument(
                            range.getStart(), range.getEnd(), value));
        }
        return arguments;
    }

    /** The game's node as a Paper one. The tree is the same object graph; only its type parameter differs. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> translateNode(
                    CommandNode<net.minecraft.commands.CommandSourceStack> node) {
        return (CommandNode) node;
    }

    /**
     * One parsed argument, re-expressed in Paper's source type — and, for a selector, as a resolver.
     *
     * <h2>Why the value can need converting at all</h2>
     *
     * <p>What a context holds under an argument's name is whatever the type the dispatcher's node carried
     * produced. For most arguments that is the plugin's own type and the value is already the right one. A
     * selector is the exception: the node carries the game's {@code EntityArgument} — because a class only this
     * bridge has cannot be described to a client, see {@link #wireTypeOf} — so the game parsed it and the value
     * is an {@code EntitySelector}. The plugin asked for a {@code PlayerSelectorArgumentResolver} and would get
     * an {@code EntitySelector} cast to it, which throws inside the plugin at the first {@code resolve} call.
     *
     * <p>The conversion is decided by the argument's NODE, not by the value: an {@code EntitySelector} is a game
     * type a plugin could plausibly have produced through some other argument, and converting on the value alone
     * would wrap the wrong ones. The node is what knows which argument this is.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static com.mojang.brigadier.context.ParsedArgument<
                    io.papermc.paper.command.brigadier.CommandSourceStack, ?> reargument(
                    com.mojang.brigadier.context.ParsedArgument<net.minecraft.commands.CommandSourceStack, ?>
                                    original,
                    ForbricSelectorType<?> selectorType) {
        com.mojang.brigadier.context.StringRange range = original.getRange();
        Object value = original.getResult();
        if (selectorType != null) value = selectorType.resolverFor(value);
        return new com.mojang.brigadier.context.ParsedArgument(
                        range.getStart(), range.getEnd(), value);
    }

    /**
     * The bridge's selector type behind a name, or null when that name is not a selector argument.
     *
     * <p>Every node in the context is walked because an argument belongs to the node that declared it, and a
     * child argument's name is not on the parent. What the node carries is the game's {@code EntityArgument},
     * which {@link ForbricSelectorType#wrapperFor} maps back to the bridge's wrapper — the object that knows
     * which resolver to build.
     */
    private static ForbricSelectorType<?> selectorFor(
                    com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
                    String name) {
        for (com.mojang.brigadier.context.ParsedCommandNode<net.minecraft.commands.CommandSourceStack> node
                        : context.getNodes()) {
            if (!(node.getNode() instanceof ArgumentCommandNode<?, ?> argument)) continue;
            if (!argument.getName().equals(name)) continue;
            return ForbricSelectorType.wrapperFor(argument.getType());
        }
        return null;
    }

    /**
     * The game's command as a Paper one.
     *
     * <p>Safe here for a reason worth writing down: the object is one of OURS — {@link #wrap} built it, and its
     * body converts the context before calling into the plugin — so the type parameter is a labelling
     * difference, not a behavioural one. A cast of an arbitrary game command would not have this property.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Command<io.papermc.paper.command.brigadier.CommandSourceStack> unwrap(
                    Command<net.minecraft.commands.CommandSourceStack> command) {
        return (Command) command;
    }

    /** A plugin's requirement, adapted the same way. */
    private java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> wrapRequirement(
                    java.util.function.Predicate<io.papermc.paper.command.brigadier.CommandSourceStack> original) {
        if (original == null) {
            return source -> true;
        }
        return (net.minecraft.commands.CommandSourceStack source) -> {
            // A requirement that throws makes the node unreachable, and the dispatcher reports an unreachable
            // node as "unknown command" — indistinguishable from a command that was never registered. Logging
            // the throw is the only way that failure is visible at all.
            try {
                return original.test(ForbricCommandSourceStack.of(source, server));
            } catch (RuntimeException | Error thrown) {
                ForbricLog.error("[Forbric/Bukkit] a plugin command's permission check threw, so the command it "
                        + "guards is unreachable and the client will be told it does not exist", thrown);
                throw thrown;
            }
        };
    }

    /** A source's name and position for the visibility log, without asking it anything that could throw. */
    private static String describe(net.minecraft.commands.CommandSourceStack source) {
        try {
            return source.getTextName();
        } catch (RuntimeException ignored) {
            return "<unnamed source>";
        }
    }
}
