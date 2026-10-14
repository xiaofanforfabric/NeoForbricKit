/*
 * Forbric Bukkit bridge — the {@code Server} a plugin receives.  *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ServerImpl.table. Editing this file by hand means the next
 * generation silently discards the edit; the change belongs in the table or the template.
 */
package org.cn.xiaofan.bukkit;

/**
 * The {@code org.bukkit.Server} a plugin receives from {@code getServer()}.
 *
 * <p>Paper's {@code Server} declares ~200 abstract methods. This bridge answers the ones a plugin needs to
 * start — its identity, its logger, the plugin manager, and the worlds and players it will actually look for —
 * and refuses the rest with a named {@link UnsupportedOperationException}. Refusing loudly is deliberate: a
 * stubbed {@code getWorld()} returning {@code null} turns "this bridge has no worlds" into a
 * {@code NullPointerException} inside the plugin, several frames away from the cause.
 *
 * <p>Every method carries the interface's own signature, generated from the API jar, so a plugin links against
 * this class exactly as it would against Paper's.
 */
final class ServerImpl implements org.bukkit.Server {

	static final String BRAND = "Forbric";
	static final String BUKKIT_VERSION = "26.2-R0.1-SNAPSHOT";
	static final String MINECRAFT_VERSION = "26.2";

	private final java.util.logging.Logger logger;
	/**
	 * The plugin manager, or null until {@link #installPluginManager()} has run.
	 *
	 * <p>Not final, because it CANNOT be built before this instance exists: Paper's own
	 * {@code SimplePluginManager} takes a {@code Server} in its constructor, and every command it
	 * registers is registered against a {@code SimpleCommandMap} that also takes one. The way out is to
	 * construct this object with null and fill the field in immediately afterwards, which is what
	 * {@link BridgeBootstrap} does before any plugin loads. A plugin never sees the null state; the
	 * only caller that could was the constructor.
	 */
	private org.bukkit.plugin.PluginManager pluginManager;
	private org.bukkit.command.SimpleCommandMap commandMap;
	private final java.nio.file.Path pluginsFolder;
	private final String version;

	/**
	 * The running server, or null when the kernel had none to hand over.
	 *
	 * <p>Typed as the game's own class rather than as this bridge's wrapper of it: every {@code World} a plugin
	 * asks for is one of this server's {@code ServerLevel}s, and the conversion belongs at the point of use.
	 */
	private final net.minecraft.server.MinecraftServer minecraft;

	/**
	 * The region, global and async schedulers.
	 *
	 * <p>One instance for the server's life rather than one per call: it owns the timer threads, and
	 * handing out a fresh one per {@code getRegionScheduler()} would leak a thread pool every time a plugin
	 * asked. The tasks it holds belong to plugins, so it is also what a shutdown has to stop.
	 */
	private final ForbricSchedulers schedulers;

	ServerImpl(java.util.logging.Logger logger, org.bukkit.plugin.PluginManager pluginManager,
			java.nio.file.Path pluginsFolder, String version, net.minecraft.server.MinecraftServer minecraft) {
		this.logger = logger;
		this.pluginManager = pluginManager;
		this.pluginsFolder = pluginsFolder;
		this.version = version;
		this.minecraft = minecraft;
		this.schedulers = new ForbricSchedulers(this);
	}

	/**
	 * Builds the plugin manager and the command map behind it, now that {@code this} exists to hand to them.
	 *
	 * <p>Both are Bukkit's own classes — {@code SimplePluginManager} and {@code SimpleCommandMap} live in
	 * paper-api and are the implementations the real server uses. Writing our own would mean re-deriving
	 * event-priority ordering, permission default recalculation, and the plugin-file-to-loader table, each of
	 * which has behaviour a plugin can observe. There is nothing Forbric-specific about any of them, so the
	 * honest thing is to use the ones Paper ships.
	 *
	 * <p>The command map is created here rather than passed in because a plugin registering a command through
	 * the lifecycle events needs it to reach the same map the dispatcher reads. One instance, kept.
	 */
	void installPluginManager(java.util.List<PluginHandle> handles) {
		this.commandMap = new org.bukkit.command.SimpleCommandMap(this, new java.util.HashMap<>());
		// Bukkit's manager for the events and permissions, wrapped so the six methods that read its
		// plugin list are answered from the handles the bridge actually loaded.
		// Bukkit's manager for the events and permissions, wrapped so the methods that read its
		// plugin list are answered from the handles the bridge actually loaded.
		org.bukkit.plugin.SimplePluginManager simple =
				new org.bukkit.plugin.SimplePluginManager(this, this.commandMap);
		ForbricPluginManager manager = new ForbricPluginManager(simple, handles, this);
		// SimplePluginManager is a SHADOW: 29 of its methods forward to this public field, which
		// nothing fills in -- on Paper the server jar assigns it a CraftBukkit implementation this
		// bridge does not have. Left null, the very first registerEvents from a plugin dies on a
		// NullPointerException inside SimplePluginManager and the plugin fails to enable.
		//
		// Pointing it back at the wrapper closes the loop: ForbricPluginManager implements every
		// PluginManager method, and the few it delegates are the ones SimplePluginManager really
		// implements itself, so the forward terminates.
		simple.paperPluginManager = manager;
		this.pluginManager = manager;
	}

	/**
	 * The command map the plugin manager dispatches through, or null before {@link #installPluginManager()}.
	 *
	 * <p>Kept rather than reached for through the manager: a plugin registering a command must land in the
	 * SAME map the console dispatcher reads, and that is this instance.
	 */
	org.bukkit.command.SimpleCommandMap commandMap() {
		return commandMap;
	}

	private static UnsupportedOperationException notYet(String what) {
		return new UnsupportedOperationException(
				"[Forbric/Bukkit] org.bukkit.Server." + what + "() is not implemented by this bridge yet");
	}

	/**
	 * Every loaded world, wrapped.
	 *
	 * <p>{@link ForbricWorld#of} is the one place a {@code ServerLevel} becomes an {@code org.bukkit.World}.
	 * Wrapping per call rather than caching, so a world reloaded under the same name is not answered from a
	 * stale wrapper.
	 */
	private java.util.List<org.bukkit.World> worlds() {
		if (minecraft == null) return java.util.List.of();
		java.util.List<org.bukkit.World> wrapped = new java.util.ArrayList<>();
		for (net.minecraft.server.level.ServerLevel level : minecraft.getAllLevels()) {
			wrapped.add(ForbricWorld.of(level, this));
		}
		return wrapped;
        }

	/**
         * One world by name, unique id, or key.
         *
         * <p>Bukkit names a world by its folder name and by a UUID; Minecraft identifies one by a
         * {@code ResourceKey}, whose identifier is the world's dimension id ({@code minecraft:overworld}, or a
         * pack's own). All three reach the same lookup because each is a different spelling of "which world do
         * you mean", and a plugin holding a saved reference may have any of them.
         *
         * <p>The identifier case compares against the level's dimension id AND its folder name: they agree for a
         * vanilla overworld and differ for a custom dimension, and a plugin asking by key means the dimension.
         */
        private org.bukkit.World worldByNameOrUuid(Object name) {
                String wanted = name instanceof java.util.UUID ? name.toString() : String.valueOf(name);
                for (org.bukkit.World world : worlds()) {
                        if (world.getName().equals(wanted) || world.getUID().toString().equals(wanted)) return world;
                }
                for (net.minecraft.server.level.ServerLevel level : minecraft.getAllLevels()) {
                        if (level.dimension().identifier().toString().equals(wanted)) {
                                return ForbricWorld.of(level, this);
                        }
                }
		return null;
	}

	private java.util.Collection<? extends org.bukkit.entity.Player> onlinePlayers() {
		if (minecraft == null) return java.util.List.of();
		java.util.List<org.bukkit.entity.Player> players = new java.util.ArrayList<>();
		for (net.minecraft.server.level.ServerPlayer player : minecraft.getPlayerList().getPlayers()) {
			players.add(ForbricPlayer.of(player, this));
		}
		return players;
	}

	/**
	 * Whether a player is an operator, asked of the game's own op list.
	 *
	 * <p>Not a Bukkit permission: the game decides what is privileged, and a bridge that consulted the API's
	 * table instead would let a plugin's own default grant op powers. The name is needed because the op list
	 * is keyed by name-and-id, and a player who changed their name is listed under both.
	 *
	 * <p><b>The online-player lookup must not be the only path.</b> This is asked while the command list is
	 * being built for a joining player — {@code PlayerList.sendPlayerPermissionLevel} runs about 180 bytecode
	 * instructions BEFORE {@code PlayerList.players.add(player)}, so at that moment the player is genuinely not
	 * in the list yet and {@code getPlayer(uuid)} returns null. Answering "not an operator" there refused every
	 * {@code default: op} command, and the refusal is not a message: {@code CommandHelper.mergeCommandNode}
	 * DROPS a node whose {@code canUse} says no, so the client received a command tree with no plugin command
	 * in it at all. The client then reports the fully-typed line as "unknown or incomplete command at position
	 * 1" while the server would have run it happily once the player was listed — which is exactly the split
	 * symptom this fix removes.
	 *
	 * <p>So the op list is searched by account id when the player is not online yet. The id is the stable half
	 * of the key; a name is only needed for an entry written before the id was recorded, and entries the game
	 * itself writes always carry both.
	 */
	boolean isOperator(java.util.UUID player) {
		if (minecraft == null) return false;
		// The normal path: an online player, whose name the op list wants beside the id.
		net.minecraft.server.level.ServerPlayer online = minecraft.getPlayerList().getPlayer(player);
		if (online != null) {
			return minecraft.getPlayerList().isOp(
					new net.minecraft.server.players.NameAndId(player, online.getScoreboardName()));
		}
		// The joining-player path, where the list does not hold them yet. Match the id half of every entry,
		// which is the same question the name-based lookup answers for a listed player.
		for (net.minecraft.server.players.ServerOpListEntry entry
				: minecraft.getPlayerList().getOps().getEntries()) {
			net.minecraft.server.players.NameAndId key = entry.getUser();
			if (key != null && player.equals(key.id())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Enables a plugin, firing the lifecycle handlers it registered during its own onEnable.
	 *
	 * <p>Lives here rather than in the plugin manager because the handlers need the game's Brigadier
	 * dispatcher, and the only object holding a {@code MinecraftServer} is this one.
	 */
	void enablePlugin(PluginHandle handle) {
		if (minecraft == null) {
			throw new IllegalStateException("[Forbric/Bukkit] cannot enable a plugin without a running "
							+ "server: lifecycle handlers are fired against the game's command dispatcher");
		}
		handle.enable(minecraft.getCommands().getDispatcher(), this);
	}

	private int maxPlayers() {
		return minecraft == null ? 0 : minecraft.getPlayerList().getMaxPlayers();
	}

	/** The game's own server, or null. A bridge with no worlds answers from this being absent. */
	net.minecraft.server.MinecraftServer minecraft() {
		return minecraft;
	}

	@Override
	public java.io.File getPluginsFolder() {
		return pluginsFolder.toFile();
	}

	@Override
	public java.lang.String getName() {
		return BRAND;
	}

	@Override
	public java.lang.String getVersion() {
		return version;
	}

	@Override
	public java.lang.String getBukkitVersion() {
		return BUKKIT_VERSION;
	}

	@Override
	public java.lang.String getMinecraftVersion() {
		return MINECRAFT_VERSION;
	}

	@Override
	public java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers() {
		return onlinePlayers();
	}

	@Override
	public int getMaxPlayers() {
		return maxPlayers();
	}

	@Override
	public void setMaxPlayers(int arg0) {
		throw notYet("setMaxPlayers");
	}

	@Override
	public int getPort() {
		throw notYet("getPort");
	}

	@Override
	public int getViewDistance() {
		throw notYet("getViewDistance");
	}

	@Override
	public int getSimulationDistance() {
		throw notYet("getSimulationDistance");
	}

	@Override
	public java.lang.String getIp() {
		throw notYet("getIp");
	}

	@Override
	public java.lang.String getWorldType() {
		throw notYet("getWorldType");
	}

	@Override
	public boolean getGenerateStructures() {
		throw notYet("getGenerateStructures");
	}

	@Override
	public int getMaxWorldSize() {
		throw notYet("getMaxWorldSize");
	}

	@Override
	public boolean getAllowEnd() {
		throw notYet("getAllowEnd");
	}

	@Override
	public boolean getAllowNether() {
		throw notYet("getAllowNether");
	}

	@Override
	public boolean isLoggingIPs() {
		throw notYet("isLoggingIPs");
	}

	@Override
	public java.util.List<java.lang.String> getInitialEnabledPacks() {
		throw notYet("getInitialEnabledPacks");
	}

	@Override
	public java.util.List<java.lang.String> getInitialDisabledPacks() {
		throw notYet("getInitialDisabledPacks");
	}

	@Override
	public org.bukkit.ServerTickManager getServerTickManager() {
		throw notYet("getServerTickManager");
	}

	@Override
	public org.bukkit.packs.ResourcePack getServerResourcePack() {
		throw notYet("getServerResourcePack");
	}

	@Override
	public java.lang.String getResourcePack() {
		throw notYet("getResourcePack");
	}

	@Override
	public java.lang.String getResourcePackHash() {
		throw notYet("getResourcePackHash");
	}

	@Override
	public java.lang.String getResourcePackPrompt() {
		throw notYet("getResourcePackPrompt");
	}

	@Override
	public boolean isResourcePackRequired() {
		throw notYet("isResourcePackRequired");
	}

	@Override
	public boolean hasWhitelist() {
		throw notYet("hasWhitelist");
	}

	@Override
	public void setWhitelist(boolean arg0) {
		throw notYet("setWhitelist");
	}

	@Override
	public boolean isWhitelistEnforced() {
		throw notYet("isWhitelistEnforced");
	}

	@Override
	public void setWhitelistEnforced(boolean arg0) {
		throw notYet("setWhitelistEnforced");
	}

	@Override
	public java.util.Set<org.bukkit.OfflinePlayer> getWhitelistedPlayers() {
		throw notYet("getWhitelistedPlayers");
	}

	@Override
	public void reloadWhitelist() {
		throw notYet("reloadWhitelist");
	}

	@Override
	public java.lang.String getUpdateFolder() {
		return "update";
	}

	@Override
	public java.io.File getUpdateFolderFile() {
		throw notYet("getUpdateFolderFile");
	}

	@Override
	public long getConnectionThrottle() {
		throw notYet("getConnectionThrottle");
	}

	@Override
	public int getTicksPerSpawns(org.bukkit.entity.SpawnCategory arg0) {
		throw notYet("getTicksPerSpawns");
	}

	@Override
	public org.bukkit.entity.Player getPlayer(java.lang.String arg0) {
		// Bukkit's contract: an exact name wins, and only when there is none does a unique prefix
		// match answer. A prefix matching more than one player is ambiguous and must NOT be guessed,
		// which is why matchPlayer is consulted rather than a first-hit search.
		org.bukkit.entity.Player exact = getPlayerExact(arg0);
		if (exact != null) {
			return exact;
		}
		java.util.List<org.bukkit.entity.Player> matches = matchPlayer(arg0);
		return matches.size() == 1 ? matches.get(0) : null;
	}

	@Override
	public org.bukkit.entity.Player getPlayerExact(java.lang.String arg0) {
		if (minecraft == null || arg0 == null) {
			return null;
		}
		// Compared case-insensitively because the game's own player-name commands do, and a plugin
		// accepting only the exact casing would refuse a name the server itself accepts.
		for (net.minecraft.server.level.ServerPlayer player : minecraft.getPlayerList().getPlayers()) {
			if (player.getScoreboardName().equalsIgnoreCase(arg0)) {
				return ForbricPlayer.of(player, this);
			}
		}
		return null;
	}

	@Override
	public java.util.List<org.bukkit.entity.Player> matchPlayer(java.lang.String arg0) {
		java.util.List<org.bukkit.entity.Player> matches = new java.util.ArrayList<>();
		if (minecraft == null || arg0 == null) {
			return matches;
		}
		for (net.minecraft.server.level.ServerPlayer player : minecraft.getPlayerList().getPlayers()) {
			if (player.getScoreboardName().toLowerCase(java.util.Locale.ROOT)
					.startsWith(arg0.toLowerCase(java.util.Locale.ROOT))) {
				matches.add(ForbricPlayer.of(player, this));
			}
		}
		return matches;
	}

	@Override
	public org.bukkit.entity.Player getPlayer(java.util.UUID arg0) {
		if (minecraft == null || arg0 == null) {
			return null;
		}
		net.minecraft.server.level.ServerPlayer player = minecraft.getPlayerList().getPlayer(arg0);
		return player == null ? null : ForbricPlayer.of(player, this);
	}

	@Override
	public java.util.UUID getPlayerUniqueId(java.lang.String arg0) {
		// Online only, which is what the game's name lookup can answer. An offline player has no
		// connection to read a profile from, and inventing an id would let a plugin key a map by it.
		org.bukkit.entity.Player online = getPlayerExact(arg0);
		return online == null ? null : online.getUniqueId();
	}

	@Override
	public org.bukkit.plugin.PluginManager getPluginManager() {
		return pluginManager;
	}

	@Override
	public org.bukkit.scheduler.BukkitScheduler getScheduler() {
		throw notYet("getScheduler");
	}

	@Override
	public org.bukkit.plugin.ServicesManager getServicesManager() {
		throw notYet("getServicesManager");
	}

	@Override
	public java.util.List<org.bukkit.World> getWorlds() {
		return worlds();
	}

	@Override
	public boolean isTickingWorlds() {
		throw notYet("isTickingWorlds");
	}

	@Override
	public org.bukkit.World createWorld(org.bukkit.WorldCreator arg0) {
		throw notYet("createWorld");
	}

	@Override
	public boolean unloadWorld(java.lang.String arg0, boolean arg1) {
		throw notYet("unloadWorld");
	}

	@Override
	public boolean unloadWorld(org.bukkit.World arg0, boolean arg1) {
		throw notYet("unloadWorld");
	}

	@Override
	public org.bukkit.World getRespawnWorld() {
		throw notYet("getRespawnWorld");
	}

	@Override
	public void setRespawnWorld(org.bukkit.World arg0) {
		throw notYet("setRespawnWorld");
	}

	@Override
	public org.bukkit.World getWorld(java.lang.String name) {
		return worldByNameOrUuid(name);
	}

	@Override
	public org.bukkit.World getWorld(java.util.UUID arg0) {
		throw notYet("getWorld");
	}

	@Override
	public org.bukkit.World getWorld(net.kyori.adventure.key.Key arg0) {
		throw notYet("getWorld");
	}

	@Override
	public org.bukkit.WorldBorder createWorldBorder() {
		throw notYet("createWorldBorder");
	}

	@Override
	public org.bukkit.map.MapView getMap(int arg0) {
		throw notYet("getMap");
	}

	@Override
	public org.bukkit.map.MapView createMap(org.bukkit.World arg0) {
		throw notYet("createMap");
	}

	@Override
	public org.bukkit.inventory.ItemStack createExplorerMap(org.bukkit.World arg0, org.bukkit.Location arg1, org.bukkit.StructureType arg2, int arg3, boolean arg4) {
		throw notYet("createExplorerMap");
	}

	@Override
	public org.bukkit.inventory.ItemStack createExplorerMap(org.bukkit.World arg0, org.bukkit.Location arg1, org.bukkit.generator.structure.StructureType arg2, org.bukkit.map.MapCursor.Type arg3, int arg4, boolean arg5) {
		throw notYet("createExplorerMap");
	}

	@Override
	public void reload() {
		throw notYet("reload");
	}

	@Override
	public void reloadData() {
		throw notYet("reloadData");
	}

	@Override
	public void updateResources() {
		throw notYet("updateResources");
	}

	@Override
	public void updateRecipes() {
		throw notYet("updateRecipes");
	}

	@Override
	public java.util.logging.Logger getLogger() {
		return logger;
	}

/**
         * The command a plugin declared in its {@code plugin.yml}, by the name the caller asked for.
         *
         * <p>This is what {@code JavaPlugin.getCommand(name)} calls, and it is the traditional way a plugin
         * connects an executor to a declaration: {@code getCommand("x").setExecutor(...)}. It reads the same
         * map {@link ForbricDeclaredCommands} filled, so the answer here and the command the dispatcher runs
         * are the same object.
         *
         * <p>The argument arrives lower-cased from {@code JavaPlugin} and is looked up as-is. A Bukkit lookup
         * also accepts the {@code plugin:label} form, which the map already holds (the fallback prefix is the
         * plugin's name), so a name that only matches under that prefix still resolves.
         *
         * <p>Only a {@code PluginCommand} is returned: the map may hold the vanilla commands too, and
         * handing one of those back as a plugin command would let a plugin retarget a game command.
         */
        @Override
        public org.bukkit.command.PluginCommand getPluginCommand(java.lang.String arg0) {
                if (commandMap == null || arg0 == null) {
                        return null;
                }
                org.bukkit.command.Command command = commandMap.getCommand(arg0.toLowerCase(java.util.Locale.ROOT));
                return command instanceof org.bukkit.command.PluginCommand pluginCommand ? pluginCommand : null;
	}

	@Override
	public void savePlayers() {
		throw notYet("savePlayers");
	}

	@Override
	public boolean dispatchCommand(org.bukkit.command.CommandSender arg0, java.lang.String arg1) throws org.bukkit.command.CommandException {
		throw notYet("dispatchCommand");
	}

	@Override
	public boolean addRecipe(org.bukkit.inventory.Recipe arg0, boolean arg1) {
		throw notYet("addRecipe");
	}

	@Override
	public java.util.List<org.bukkit.inventory.Recipe> getRecipesFor(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("getRecipesFor");
	}

	@Override
	public org.bukkit.inventory.Recipe getRecipe(org.bukkit.NamespacedKey arg0) {
		throw notYet("getRecipe");
	}

	@Override
	public org.bukkit.inventory.Recipe getCraftingRecipe(org.bukkit.inventory.ItemStack[] arg0, org.bukkit.World arg1) {
		throw notYet("getCraftingRecipe");
	}

	@Override
	public org.bukkit.inventory.ItemCraftResult craftItemResult(org.bukkit.inventory.ItemStack[] arg0, org.bukkit.World arg1, org.bukkit.entity.Player arg2) {
		throw notYet("craftItemResult");
	}

	@Override
	public org.bukkit.inventory.ItemCraftResult craftItemResult(org.bukkit.inventory.ItemStack[] arg0, org.bukkit.World arg1) {
		throw notYet("craftItemResult");
	}

	@Override
	public java.util.Iterator<org.bukkit.inventory.Recipe> recipeIterator() {
		throw notYet("recipeIterator");
	}

	@Override
	public void clearRecipes() {
		throw notYet("clearRecipes");
	}

	@Override
	public void resetRecipes() {
		throw notYet("resetRecipes");
	}

	@Override
	public boolean removeRecipe(org.bukkit.NamespacedKey arg0, boolean arg1) {
		throw notYet("removeRecipe");
	}

	@Override
	public java.util.Map<java.lang.String, java.lang.String[]> getCommandAliases() {
		throw notYet("getCommandAliases");
	}

	@Override
	public int getSpawnRadius() {
		throw notYet("getSpawnRadius");
	}

	@Override
	public void setSpawnRadius(int arg0) {
		throw notYet("setSpawnRadius");
	}

	@Override
	public boolean isEnforcingSecureProfiles() {
		throw notYet("isEnforcingSecureProfiles");
	}

	@Override
	public boolean isAcceptingTransfers() {
		throw notYet("isAcceptingTransfers");
	}

	@Override
	public boolean getHideOnlinePlayers() {
		throw notYet("getHideOnlinePlayers");
	}

	@Override
	public boolean getOnlineMode() {
		throw notYet("getOnlineMode");
	}

	@Override
	public io.papermc.paper.configuration.ServerConfiguration getServerConfig() {
		throw notYet("getServerConfig");
	}

	@Override
	public boolean getAllowFlight() {
		throw notYet("getAllowFlight");
	}

	@Override
	public boolean isHardcore() {
		throw notYet("isHardcore");
	}

	@Override
	public void shutdown() {
		throw notYet("shutdown");
	}

	@Override
	public int broadcast(net.kyori.adventure.text.Component arg0, java.lang.String arg1) {
		throw notYet("broadcast");
	}

	@Override
	public org.bukkit.OfflinePlayer getOfflinePlayer(java.lang.String arg0) {
		throw notYet("getOfflinePlayer");
	}

	@Override
	public org.bukkit.OfflinePlayer getOfflinePlayerIfCached(java.lang.String arg0) {
		throw notYet("getOfflinePlayerIfCached");
	}

	@Override
	public org.bukkit.OfflinePlayer getOfflinePlayer(java.util.UUID arg0) {
		throw notYet("getOfflinePlayer");
	}

	@Override
	public org.bukkit.profile.PlayerProfile createPlayerProfile(java.util.UUID arg0, java.lang.String arg1) {
		throw notYet("createPlayerProfile");
	}

	@Override
	public org.bukkit.profile.PlayerProfile createPlayerProfile(java.util.UUID arg0) {
		throw notYet("createPlayerProfile");
	}

	@Override
	public org.bukkit.profile.PlayerProfile createPlayerProfile(java.lang.String arg0) {
		throw notYet("createPlayerProfile");
	}

	@Override
	public java.util.Set<java.lang.String> getIPBans() {
		throw notYet("getIPBans");
	}

	@Override
	public void banIP(java.lang.String arg0) {
		throw notYet("banIP");
	}

	@Override
	public void unbanIP(java.lang.String arg0) {
		throw notYet("unbanIP");
	}

	@Override
	public void banIP(java.net.InetAddress arg0) {
		throw notYet("banIP");
	}

	@Override
	public void unbanIP(java.net.InetAddress arg0) {
		throw notYet("unbanIP");
	}

	@Override
	public java.util.Set<org.bukkit.OfflinePlayer> getBannedPlayers() {
		throw notYet("getBannedPlayers");
	}

	@Override
	public <T extends org.bukkit.BanList<?>> T getBanList(org.bukkit.BanList.Type arg0) {
		throw notYet("getBanList");
	}

	@Override
	public <B extends org.bukkit.BanList<E>, E> B getBanList(io.papermc.paper.ban.BanListType<B> arg0) {
		throw notYet("getBanList");
	}

	@Override
	public java.util.Set<org.bukkit.OfflinePlayer> getOperators() {
		throw notYet("getOperators");
	}

	@Override
	public org.bukkit.GameMode getDefaultGameMode() {
		throw notYet("getDefaultGameMode");
	}

	@Override
	public void setDefaultGameMode(org.bukkit.GameMode arg0) {
		throw notYet("setDefaultGameMode");
	}

	@Override
	public boolean forcesDefaultGameMode() {
		throw notYet("forcesDefaultGameMode");
	}

	@Override
	public org.bukkit.command.ConsoleCommandSender getConsoleSender() {
		throw notYet("getConsoleSender");
	}

	@Override
	public org.bukkit.command.CommandSender createCommandSender(java.util.function.Consumer<? super net.kyori.adventure.text.Component> arg0) {
		throw notYet("createCommandSender");
	}

	@Override
	public java.io.File getWorldContainer() {
		throw notYet("getWorldContainer");
	}

	@Override
	public java.nio.file.Path getLevelDirectory() {
		throw notYet("getLevelDirectory");
	}

	@Override
	public org.bukkit.OfflinePlayer[] getOfflinePlayers() {
		throw notYet("getOfflinePlayers");
	}

	@Override
	public org.bukkit.plugin.messaging.Messenger getMessenger() {
		throw notYet("getMessenger");
	}

	@Override
	public org.bukkit.help.HelpMap getHelpMap() {
		throw notYet("getHelpMap");
	}

	@Override
	public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder arg0, org.bukkit.event.inventory.InventoryType arg1) {
		throw notYet("createInventory");
	}

	@Override
	public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder arg0, org.bukkit.event.inventory.InventoryType arg1, net.kyori.adventure.text.Component arg2) {
		throw notYet("createInventory");
	}

	@Override
	public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder arg0, org.bukkit.event.inventory.InventoryType arg1, java.lang.String arg2) {
		throw notYet("createInventory");
	}

	@Override
	public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder arg0, int arg1) throws java.lang.IllegalArgumentException {
		throw notYet("createInventory");
	}

	@Override
	public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder arg0, int arg1, net.kyori.adventure.text.Component arg2) throws java.lang.IllegalArgumentException {
		throw notYet("createInventory");
	}

	@Override
	public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder arg0, int arg1, java.lang.String arg2) throws java.lang.IllegalArgumentException {
		throw notYet("createInventory");
	}

	@Override
	public org.bukkit.inventory.Merchant createMerchant(net.kyori.adventure.text.Component arg0) {
		throw notYet("createMerchant");
	}

	@Override
	public org.bukkit.inventory.Merchant createMerchant(java.lang.String arg0) {
		throw notYet("createMerchant");
	}

	@Override
	public int getMaxChainedNeighborUpdates() {
		throw notYet("getMaxChainedNeighborUpdates");
	}

	@Override
	public org.bukkit.inventory.Merchant createMerchant() {
		throw notYet("createMerchant");
	}

	@Override
	public int getSpawnLimit(org.bukkit.entity.SpawnCategory arg0) {
		throw notYet("getSpawnLimit");
	}

	/**
	 * Whether the calling thread is the server's own tick thread.
	 *
	 * <p><b>Asked of the game, not assumed.</b> This used to return {@code true} unconditionally, which was
	 * true for every command and event and wrong for anything else: a plugin's async task, a Netty IO
	 * thread or a shutdown hook all got "yes, you are the main thread". A Bukkit plugin uses this to decide
	 * whether it may touch the world directly, so answering yes on a thread the game is not ticking meant
	 * a plugin wrote to the world from a foreign thread with no warning.
	 *
	 * <p>{@code isSameThread} is the game's own answer and compares against the thread its tick loop runs
	 * on, which is the definition Bukkit's method wants. A null server (the wiring fault reported at
	 * startup) keeps the old permissive answer, because with no game there is no tick thread to compare
	 * against and refusing would break plugins that only read the API's own state.
	 */
	@Override
	public boolean isPrimaryThread() {
		return minecraft == null || minecraft.isSameThread();
	}

	@Override
	public net.kyori.adventure.text.Component motd() {
		throw notYet("motd");
	}

	@Override
	public void motd(net.kyori.adventure.text.Component arg0) {
		throw notYet("motd");
	}

	@Override
	public net.kyori.adventure.text.Component shutdownMessage() {
		throw notYet("shutdownMessage");
	}

	@Override
	public java.lang.String getMotd() {
		throw notYet("getMotd");
	}

	@Override
	public void setMotd(java.lang.String arg0) {
		throw notYet("setMotd");
	}

	@Override
	public org.bukkit.ServerLinks getServerLinks() {
		throw notYet("getServerLinks");
	}

	@Override
	public java.lang.String getShutdownMessage() {
		throw notYet("getShutdownMessage");
	}

	@Override
	public org.bukkit.Warning.WarningState getWarningState() {
		throw notYet("getWarningState");
	}

	@Override
	public org.bukkit.inventory.ItemFactory getItemFactory() {
		throw notYet("getItemFactory");
	}

	@Override
	public org.bukkit.entity.EntityFactory getEntityFactory() {
		throw notYet("getEntityFactory");
	}

	@Override
	public org.bukkit.scoreboard.ScoreboardManager getScoreboardManager() {
		throw notYet("getScoreboardManager");
	}

	@Override
	public org.bukkit.scoreboard.Criteria getScoreboardCriteria(java.lang.String arg0) {
		throw notYet("getScoreboardCriteria");
	}

	@Override
	public org.bukkit.util.CachedServerIcon getServerIcon() {
		throw notYet("getServerIcon");
	}

	@Override
	public org.bukkit.util.CachedServerIcon loadServerIcon(java.io.File arg0) throws java.lang.IllegalArgumentException, java.lang.Exception {
		throw notYet("loadServerIcon");
	}

	@Override
	public org.bukkit.util.CachedServerIcon loadServerIcon(java.awt.image.BufferedImage arg0) throws java.lang.IllegalArgumentException, java.lang.Exception {
		throw notYet("loadServerIcon");
	}

	@Override
	public void setIdleTimeout(int arg0) {
		throw notYet("setIdleTimeout");
	}

	@Override
	public int getIdleTimeout() {
		throw notYet("getIdleTimeout");
	}

	@Override
	public int getPauseWhenEmptyTime() {
		throw notYet("getPauseWhenEmptyTime");
	}

	@Override
	public void setPauseWhenEmptyTime(int arg0) {
		throw notYet("setPauseWhenEmptyTime");
	}

	@Override
	public org.bukkit.generator.ChunkGenerator.ChunkData createChunkData(org.bukkit.World arg0) {
		throw notYet("createChunkData");
	}

	@Override
	public org.bukkit.boss.BossBar createBossBar(java.lang.String arg0, org.bukkit.boss.BarColor arg1, org.bukkit.boss.BarStyle arg2, org.bukkit.boss.BarFlag... arg3) {
		throw notYet("createBossBar");
	}

	@Override
	public org.bukkit.boss.KeyedBossBar createBossBar(org.bukkit.NamespacedKey arg0, java.lang.String arg1, org.bukkit.boss.BarColor arg2, org.bukkit.boss.BarStyle arg3, org.bukkit.boss.BarFlag... arg4) {
		throw notYet("createBossBar");
	}

	@Override
	public java.util.Iterator<org.bukkit.boss.KeyedBossBar> getBossBars() {
		throw notYet("getBossBars");
	}

	@Override
	public org.bukkit.boss.KeyedBossBar getBossBar(org.bukkit.NamespacedKey arg0) {
		throw notYet("getBossBar");
	}

	@Override
	public boolean removeBossBar(org.bukkit.NamespacedKey arg0) {
		throw notYet("removeBossBar");
	}

	@Override
	public org.bukkit.entity.Entity getEntity(java.util.UUID arg0) {
		throw notYet("getEntity");
	}

	@Override
	public double[] getTPS() {
		throw notYet("getTPS");
	}

	@Override
	public long[] getTickTimes() {
		throw notYet("getTickTimes");
	}

	@Override
	public double getAverageTickTime() {
		throw notYet("getAverageTickTime");
	}

	/**
	 * The command map the plugin manager dispatches through.
	 *
	 * <p>This is the same instance {@link #commandMap()} returns, and the same one the lifecycle
	 * registration path writes into. A plugin that registers its command by hand instead of declaring it
	 * in {@code plugin.yml} — which is what {@code getCommandMap().register(...)} is for — therefore lands
	 * where the dispatcher will find it.
	 *
	 * <p>It is not null by the time a plugin can ask: {@link #installPluginManager} runs before any plugin
	 * is enabled. A null here would mean a plugin was enabled before the bridge was built, which is a
	 * bridge bug and not something to paper over with an empty map.
	 */
	@Override
	public org.bukkit.command.CommandMap getCommandMap() {
		if (commandMap == null) {
			throw new IllegalStateException("[Forbric/Bukkit] a plugin asked for the command map before the "
					+ "bridge built one — plugins are enabled after installPluginManager, so this is a bridge bug");
		}
		return commandMap;
	}

	@Override
	public org.bukkit.advancement.Advancement getAdvancement(org.bukkit.NamespacedKey arg0) {
		throw notYet("getAdvancement");
	}

	@Override
	public java.util.Iterator<org.bukkit.advancement.Advancement> advancementIterator() {
		throw notYet("advancementIterator");
	}

	@Override
	public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material arg0) {
		throw notYet("createBlockData");
	}

	@Override
	public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material arg0, java.util.function.Consumer<? super org.bukkit.block.data.BlockData> arg1) {
		throw notYet("createBlockData");
	}

	@Override
	public org.bukkit.block.data.BlockData createBlockData(java.lang.String arg0) throws java.lang.IllegalArgumentException {
		throw notYet("createBlockData");
	}

	@Override
	public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material arg0, java.lang.String arg1) throws java.lang.IllegalArgumentException {
		throw notYet("createBlockData");
	}

	@Override
	public <T extends org.bukkit.Keyed> org.bukkit.Tag<T> getTag(java.lang.String arg0, org.bukkit.NamespacedKey arg1, java.lang.Class<T> arg2) {
		throw notYet("getTag");
	}

	@Override
	public <T extends org.bukkit.Keyed> java.lang.Iterable<org.bukkit.Tag<T>> getTags(java.lang.String arg0, java.lang.Class<T> arg1) {
		throw notYet("getTags");
	}

	@Override
	public org.bukkit.loot.LootTable getLootTable(org.bukkit.NamespacedKey arg0) {
		throw notYet("getLootTable");
	}

	@Override
	public java.util.List<org.bukkit.entity.Entity> selectEntities(org.bukkit.command.CommandSender arg0, java.lang.String arg1) throws java.lang.IllegalArgumentException {
		throw notYet("selectEntities");
	}

	@Override
	public org.bukkit.structure.StructureManager getStructureManager() {
		throw notYet("getStructureManager");
	}

	@Override
	public <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(java.lang.Class<T> arg0) {
		throw notYet("getRegistry");
	}

	@Override
	public org.bukkit.UnsafeValues getUnsafe() {
		throw notYet("getUnsafe");
	}

	@Override
	public org.bukkit.Server.Spigot spigot() {
		throw notYet("spigot");
	}

	@Override
	public void restart() {
		throw notYet("restart");
	}

	@Override
	public void reloadPermissions() {
		throw notYet("reloadPermissions");
	}

	@Override
	public boolean reloadCommandAliases() {
		throw notYet("reloadCommandAliases");
	}

	@Override
	public boolean suggestPlayerNamesWhenNullTabCompletions() {
		// true, which is what Paper's own config defaults to and what the game already does for its
		// own commands: a completer that answers nothing leaves the player-name suggestion in place.
		//
		// This is not a cosmetic switch. Command.tabComplete0 asks THIS before it suggests anything, so
		// throwing here (what the bridge used to do) turned every completion whose plugin completer
		// answered null into a failed completion packet -- the client's TAB key went dead and the
		// server logged a warning per keystroke. Answering true restores the vanilla behaviour.
		return true;
	}

	@Override
	public java.lang.String getPermissionMessage() {
		throw notYet("getPermissionMessage");
	}

	@Override
	public net.kyori.adventure.text.Component permissionMessage() {
		throw notYet("permissionMessage");
	}

	@Override
	public com.destroystokyo.paper.profile.PlayerProfile createProfile(java.util.UUID arg0) {
		throw notYet("createProfile");
	}

	@Override
	public com.destroystokyo.paper.profile.PlayerProfile createProfile(java.lang.String arg0) {
		throw notYet("createProfile");
	}

	@Override
	public com.destroystokyo.paper.profile.PlayerProfile createProfile(java.util.UUID arg0, java.lang.String arg1) {
		throw notYet("createProfile");
	}

	@Override
	public com.destroystokyo.paper.profile.PlayerProfile createProfileExact(java.util.UUID arg0, java.lang.String arg1) {
		throw notYet("createProfileExact");
	}

	@Override
	public int getCurrentTick() {
		throw notYet("getCurrentTick");
	}

	@Override
	public boolean isStopping() {
		throw notYet("isStopping");
	}

	@Override
	public com.destroystokyo.paper.entity.ai.MobGoals getMobGoals() {
		throw notYet("getMobGoals");
	}

	@Override
	public io.papermc.paper.datapack.DatapackManager getDatapackManager() {
		throw notYet("getDatapackManager");
	}

	@Override
	public org.bukkit.potion.PotionBrewer getPotionBrewer() {
		throw notYet("getPotionBrewer");
	}

	@Override
	public io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler() {
		return schedulers.region();
	}

	@Override
	public io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler() {
		return schedulers.async();
	}

	@Override
	public io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler() {
		return schedulers.global();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.World arg0, io.papermc.paper.math.Position arg1) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.World arg0, io.papermc.paper.math.Position arg1, int arg2) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.Location arg0) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.Location arg0, int arg1) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.World arg0, int arg1, int arg2) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.World arg0, int arg1, int arg2, int arg3) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.World arg0, int arg1, int arg2, int arg3, int arg4) {
		return ownedByCurrentRegion();
	}

	@Override
	public boolean isOwnedByCurrentRegion(org.bukkit.entity.Entity arg0) {
		return ownedByCurrentRegion();
	}

	/**
	 * Whether the calling thread may touch a given region.
	 *
	 * <p><b>On this server the answer is "am I on the server thread", and every coordinate argument is
	 * ignored.</b> That is not a shortcut: region ownership exists so a Folia region thread can be told
	 * apart from every other one, and this bridge runs the game on ONE thread. That thread owns every
	 * region there is, so the question "does the current thread own the region containing (x, z)" has
	 * the same answer for every coordinate — the thread's own identity.
	 *
	 * <p>The practical effect is the one a plugin needs. A Folia-aware plugin guards its world work with
	 * {@code if (server.isOwnedByCurrentRegion(loc)) runNow(); else schedule();}, and on a single-threaded
	 * server the guard is TRUE while a command or event handler is running — which is exactly the case
	 * where doing the work inline is correct and scheduling it would only add a tick of latency.
	 */
	private boolean ownedByCurrentRegion() {
		return isPrimaryThread();
	}

	@Override
	public boolean isGlobalTickThread() {
		throw notYet("isGlobalTickThread");
	}

	@Override
	public boolean isPaused() {
		throw notYet("isPaused");
	}

	@Override
	public void allowPausing(org.bukkit.plugin.Plugin arg0, boolean arg1) {
		throw notYet("allowPausing");
	}

	@Override
	public void sendPluginMessage(org.bukkit.plugin.Plugin arg0, java.lang.String arg1, byte[] arg2) {
		throw notYet("sendPluginMessage");
	}

	@Override
	public java.util.Set<java.lang.String> getListeningPluginChannels() {
		throw notYet("getListeningPluginChannels");
	}

	@Override
	public java.lang.Iterable<? extends net.kyori.adventure.audience.Audience> audiences() {
		throw notYet("audiences");
	}}
