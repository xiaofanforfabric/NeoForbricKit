/*
 * Forbric Bukkit bridge — an {@code org.bukkit.entity.Player} backed by a Minecraft {@code ServerPlayer}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricPlayer.table. Editing this file by hand means the next
 * generation silently discards the edit; the change belongs in the table or the template.
 *
 * <p><b>This warning is not theoretical.</b> The permission methods and the {@code sendMessage}/{@code sendActionBar}
 * bodies used to live only in the generated file, so regenerating it for an unrelated reason reverted all of them to
 * {@code throw notYet(...)} — and the symptom was a player who could not join the server at all, because a command's
 * permission check is evaluated while the command tree is sent. Anyone adding an implementation here must add the
 * {@code .table} entry AND the body below, together.
 */
package org.cn.xiaofan.bukkit;

/**
 * One online player, as a plugin sees it.
 *
 * <p>A <b>live handle</b> on a connected client, the same way {@link ForbricBlock} is one on a corner: identity
 * is the account, not the wrapper. Bukkit's contract says a {@code Player} reflects the connection as it is now,
 * so this class holds the {@code ServerPlayer} and asks it every time. Two wrappers for one account are equal
 * and hash alike, which is what lets a plugin compare a player from an event with one from the server.
 *
 * <p><b>An account has three names and they are not interchangeable.</b> The game profile's name is the account
 * name a player logs in with; the scoreboard name is what team prefixes decorate; and the display name is what
 * chat shows, which a plugin may set. Bukkit keeps all three, and so does this. The profile name is the one to
 * key a map by, because the other two are presentation and change.
 *
 * <p><b>Identity is the UUID.</b> A name can be changed, and on an offline-mode server can be claimed by anyone,
 * so {@link #getUniqueId()} is the only stable key. The name is offered for display and for the common case; a
 * plugin that stores anything should store the UUID.
 */
final class ForbricPlayer implements org.bukkit.entity.Player {

        private final net.minecraft.server.level.ServerPlayer player;
        private final ServerImpl server;

        /**
         * Whatever this player has been granted at run time, in the order it was added.
         *
         * <p>Per wrapper, because a wrapper is built per call — see {@link #of}. A plugin that adds an
         * attachment holds the wrapper it added it to and reads it back through the same one, which is the only
         * usage the API describes; one that stores the attachment and re-reads through a NEW wrapper gets the
         * attachment's own {@code getPermissions()} back, which is the documented way to do that.
         */
        private final java.util.List<org.bukkit.permissions.PermissionAttachment> attachments =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        private ForbricPlayer(net.minecraft.server.level.ServerPlayer player, ServerImpl server) {
                this.player = player;
                this.server = server;
        }

        /**
         * Wraps a player. The one place a {@code ServerPlayer} becomes an {@code org.bukkit.entity.Player}.
         *
         * <p>A fresh wrapper per call rather than a cache, matching the rest of the bridge: equality is by
         * account, so nothing downstream can tell two wrappers apart — and a cache would have to be invalidated
         * when the player disconnects, which is a correctness problem bought for nothing.
         */
        static ForbricPlayer of(net.minecraft.server.level.ServerPlayer player, ServerImpl server) {
                return new ForbricPlayer(player, server);
        }

        /** The wrapped player, for the bridge's own use by {@link ForbricWorld} and the event layer. */
        net.minecraft.server.level.ServerPlayer player() {
                return player;
        }

        private static UnsupportedOperationException notYet(String what) {
                return new UnsupportedOperationException(
                                "[Forbric/Bukkit] Player." + what + "() is not implemented by this bridge yet");
        }

        /**
         * The account name this player logs in with. Not necessarily unique — see the class comment.
         *
         * <p>Read from the scoreboard name rather than from the game profile, so this bridge needs no authlib:
         * the profile is a Mojang authentication type the game never sends over the wire, and reaching it here
         * would put a second library on the game-side classpath for one string. Undecorated, the two are the
         * same name.
         */
        @Override
        public String getName() {
                return player.getScoreboardName();
        }

        /**
         * This player's account id.
         *
         * <p>The one stable key: a name can change, and on an offline-mode server can be claimed by anyone.
         */
        @Override
        public java.util.UUID getUniqueId() {
                return player.getUUID();
        }

        /** What the player list shows: the scoreboard name, which teams may decorate. */
        @Override
        public String getPlayerListName() {
                return player.getScoreboardName();
        }

        /**
         * Sets the player-list name.
         *
         * <p>Refuses rather than silently accepting: the game reads that name from the scoreboard, and a bridge
         * that stored it in a field of its own would make the getter and setter disagree with what other plugins
         * see. Marking it unimplemented keeps the two from drifting apart.
         */
        @Override
        public void setPlayerListName(String name) {
                throw notYet("setPlayerListName");
        }

        /** What chat shows this player as. */
        @Override
        public String getDisplayName() {
                return getPlayerListName();
        }

        /** Sets what chat shows this player as. Refuses for the same reason as {@link #setPlayerListName}. */
        @Override
        public void setDisplayName(String name) {
                throw notYet("setDisplayName");
        }

        /**
         * Whether this player is still connected.
         *
         * <p>Asked of the connection rather than of a flag this class keeps: a player object outlives the
         * connection, and a plugin holding one after a disconnect must not be told the player is fine.
         */
        @Override
        public boolean isOnline() {
                return !player.hasDisconnected();
        }

        /** Whether this player is connected, which for a server-side handle is the same question. */
        @Override
        public boolean isConnected() {
                return isOnline();
        }

        /** The world this player is in right now. */
        @Override
        public org.bukkit.World getWorld() {
                return ForbricWorld.of(player.level(), server);
        }

        /** Where this player is, to the precision the game keeps. */
        @Override
        public org.bukkit.Location getLocation() {
                net.minecraft.world.phys.Vec3 position = player.position();
                return new org.bukkit.Location(ForbricWorld.of(player.level(), server),
                                position.x(), position.y(), position.z(),
                                player.getYRot(), player.getXRot());
        }

        /**
         * Where this player is looking FROM — the eye, not the feet.
         *
         * <p>Separate from {@link #getLocation()} because the two differ by the eye height (about
         * 1.62 blocks standing), and a ray cast that started at the feet would hit the ground the
         * player is standing on instead of whatever is ahead. The game keeps the eye position
         * itself, so it is read rather than derived: an eye height the bridge computed would be
         * wrong for a sneaking, swimming or riding player, and the game's answer already accounts
         * for the pose.
         *
         * <p>The direction is the same yaw and pitch {@link #getLocation()} reports, which is what a
         * plugin expects: eye position and facing belong to one observation, and taking them from
         * two calls would let a player turn between them.
         */
        @Override
        public org.bukkit.Location getEyeLocation() {
                net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
                return new org.bukkit.Location(ForbricWorld.of(player.level(), server),
                                eye.x(), eye.y(), eye.z(),
                                player.getYRot(), player.getXRot());
        }

        /** The server this player is on. */
        @Override
        public ServerImpl getServer() {
                return server;
        }

        /**
         * This player's inventory, as a live view of the game's own.
         *
         * <p>A fresh wrapper per call, like every other handle the bridge hands out: it holds the
         * player and reads through on every access, so two wrappers are interchangeable and nothing
         * has to be invalidated when the player leaves. The contents are never copied — a plugin that
         * sets slot 5 has written into the game, which is what the API promises.
         */
        @Override
        public org.bukkit.inventory.PlayerInventory getInventory() {
                return new ForbricPlayerInventory(player, server);
        }

        /**
         * Sends a plain-text message to this player.
         *
         * <p><b>Plain text, not a component.</b> Bukkit and Paper hand a plugin a Kyori Adventure
         * {@code Component}; the game sends a {@code net.minecraft.network.chat.Component}. They model
         * the same JSON but are different object graphs, and there is no identity conversion — one has
         * to be serialised and the other parsed, which this bridge does not do yet.
         *
         * <p>Styling is not carried: the two component systems model the same JSON as different object
         * graphs, and a message sent as literal text loses its colour. That is a visible loss in the
         * chat, unlike the refusal it replaces, which took the whole reply with it.
         */
        @Override
        public void sendMessage(String message) {
                if (message == null) {
                        return;
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }

        /**
         * Sends a plain action-bar message.
         *
         * <p>The {@code String} overload is the one this bridge answers; the BaseComponent and
         * Adventure overloads are refused, for the same reason {@link #sendMessage} is — styling
         * that silently disappears reads as success.
         */
        @Override
        public void sendActionBar(String message) {
                // The game's action bar is the overlay form of a system message — the same component,
                // sent with the overlay flag set. Refusing here was costing a plugin its action bar
                // entirely; literal text is what a String message can carry, so that is what goes.
                if (message == null) {
                        return;
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message), true);
        }

        /**
         * Disconnects this player with a reason.
         *
         * <p>The two-argument form is the abstract one; Bukkit gives a one-argument {@code kick(Component)} as a
         * {@code default} that supplies a cause, so overriding it here would be answering a method that already
         * has an answer.
         */
        @Override
        public void kick(net.kyori.adventure.text.Component message, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
                throw notYet("kick");
        }

        /**
         * Equal to any other handle on the same account.
         *
         * <p>By account id, not object identity: a plugin can reach the same player two ways (from a world, from
         * an event) and must be able to compare the results — or put them in a set.
         */
        @Override
        public boolean equals(Object other) {
                if (this == other) {
                        return true;
                }
                if (!(other instanceof ForbricPlayer)) {
                        return false;
                }
                return getUniqueId().equals(((ForbricPlayer) other).getUniqueId());
        }

        /** Hashes by the account id, matching {@link #equals}. */
        @Override
        public int hashCode() {
                return getUniqueId().hashCode();
        }

        /** Names the player, so a failed assertion says who. */
        @Override
        public String toString() {
                return "ForbricPlayer[" + getName() + "]";
        }

        /**
         * Several plain-text messages, one after another.
         *
         * <p>Delegates to {@link #sendMessage(String)} per line, which is what the API means: the
         * array is a batch, not a different kind of message. A null array or a null element is
         * skipped rather than fatal, because Paper's own implementation tolerates both and a plugin
         * that passes one should not lose the rest of the batch.
         */
        @Override
        public void sendMessage(String... messages) {
                if (messages == null) {
                        return;
                }
                for (String message : messages) {
                        sendMessage(message);
                }
        }

        /** A message attributed to another sender; the attribution is dropped, as the String form has none. */
        @Override
        public void sendMessage(java.util.UUID sender, String message) {
                sendMessage(message);
        }

        /** A batch attributed to another sender; see {@link #sendMessage(java.util.UUID, String)}. */
        @Override
        public void sendMessage(java.util.UUID sender, String... messages) {
                sendMessage(messages);
        }

        /**
         * Whether this player holds a permission.
         *
         * <p>Three answers, in the order the API defines.
         *
         * <ol>
         *   <li>an attachment granted at run time;
         *   <li>the permission's own {@code default}, when it is registered;
         *   <li>{@code false}.
         * </ol>
         *
         * <p>A default of {@code OP} or {@code TRUE} therefore grants it to everyone or to operators, which is what
         * {@code permission: kits.kit} with {@code default: op} in a {@code plugin.yml} means. Reading only whether
         * the permission exists — which is what the console sender beside this class does — would let every player
         * through every gate.
         */
        @Override
        public boolean hasPermission(String name) {
                if (name == null) {
                        return false;
                }
                for (org.bukkit.permissions.PermissionAttachment attachment : attachments) {
                        java.util.Map<String, Boolean> values = attachment.getPermissions();
                        if (values.containsKey(name)) {
                                return Boolean.TRUE.equals(values.get(name));
                        }
                }
                org.bukkit.permissions.Permission permission = server.getPluginManager() == null
                                ? null : server.getPluginManager().getPermission(name);
                if (permission == null) {
                        return false;
                }
                return switch (permission.getDefault()) {
                        case TRUE -> true;
                        case OP -> isOp();
                        case FALSE, NOT_OP -> false;
                };
        }

        /** The same question, asked with the permission object. */
        @Override
        public boolean hasPermission(org.bukkit.permissions.Permission permission) {
                return permission != null && hasPermission(permission.getName());
        }

        /**
         * Grants a permission to this player for as long as the attachment lives.
         *
         * <p>Kept on the player and consulted by {@link #hasPermission(String)} before the permission's own default,
         * which is the order Bukkit defines: a plugin that grants {@code kits.kit} to one player must not be overruled
         * by the default that denies it to everyone else.
         *
         * <p>The ticking overloads are refused rather than accepted and ignored. A timed attachment is removed by
         * the server's scheduler, and this bridge does not host one; accepting the call would leave a permission in
         * place forever, which reads as a security hole and not as a missing feature.
         */
        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin,
                        String name, boolean value) {
                org.bukkit.permissions.PermissionAttachment attachment =
                                new org.bukkit.permissions.PermissionAttachment(plugin, this);
                attachment.setPermission(name, value);
                attachments.add(attachment);
                return attachment;
        }

        /** An empty attachment, which the plugin then fills. */
        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin) {
                org.bukkit.permissions.PermissionAttachment attachment =
                                new org.bukkit.permissions.PermissionAttachment(plugin, this);
                attachments.add(attachment);
                return attachment;
        }

        /** Refuses the timed form; see {@link #addAttachment(org.bukkit.plugin.Plugin, String, boolean)}. */
        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin,
                        String name, boolean value, int ticks) {
                throw new UnsupportedOperationException("[Forbric/Bukkit] Player.addAttachment(..., ticks) is not "
                                + "implemented by this bridge yet: the timed removal needs a scheduler this bridge "
                                + "does not host. Use addAttachment(plugin, name, value) and remove it yourself");
        }

        /** Refuses the timed form; see {@link #addAttachment(org.bukkit.plugin.Plugin, String, boolean)}. */
        @Override
        public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, int ticks) {
                throw new UnsupportedOperationException("[Forbric/Bukkit] Player.addAttachment(..., ticks) is not "
                                + "implemented by this bridge yet: the timed removal needs a scheduler this bridge "
                                + "does not host. Use addAttachment(plugin) and remove it yourself");
        }

        /**
         * Whether this player is a server operator.
         *
         * <p>Read from the game's own ops list rather than from a copy kept here: an operator can be added or removed
         * while the server runs ({@code /op}, {@code /deop}, or an edit to {@code ops.json} the game reloads), and a
         * bridge that cached the answer at join would keep granting a permission the server had already taken away.
         *
         * <p>This is what a {@code permission: x} with {@code default: op} in a {@code plugin.yml} resolves to — see
         * {@link #hasPermission(String)} — so it is the answer to "may this player run /kit", not a separate notion
         * of privilege.
         */
        @Override
        public boolean isOp() {
                return server.isOperator(getUniqueId());
        }

        /**
         * Whether this player's client can see another player.
         *
         * <p>Answered by the game's own per-entity visibility test, {@code Entity.broadcastToPlayer}, which
         * is what decides whether a packet about an entity is sent to a connection at all. Reading the
         * game's answer keeps this correct for hiding done by a mod or another plugin, which a hidden-set
         * kept here would miss.
         *
         * <p><b>Not a cosmetic query.</b> {@code Command.tabComplete0} calls it while building the
         * player-name fallback for a command whose own completer answered nothing. Throwing here failed the
         * whole completion packet, so a client's TAB key went dead and the server logged a warning per
         * keystroke — the symptom that made a plugin command un-completable.
         */
        @Override
        public boolean canSee(org.bukkit.entity.Player other) {
                return canSee((org.bukkit.entity.Entity) other);
        }

        /** The entity overload, which is the same visibility rule. */
        @Override
        public boolean canSee(org.bukkit.entity.Entity other) {
                if (other == null) {
                        return false;
                }
                // A player always sees themselves: the game skips the broadcast test for the connection's own
                // entity, so asking it here would answer "no" for the one entity a player is certain to see.
                if (other.getUniqueId().equals(getUniqueId())) {
                        return true;
                }
                net.minecraft.world.entity.Entity target = gameEntity(other);
                if (target == null) {
                        // Not a wrapper this bridge built. Treating it as visible would be the wrong default:
                        // a plugin asking about an entity it never obtained here is asking about something this
                        // player's connection was never told about.
                        return false;
                }
                return target.broadcastToPlayer(player);
        }

        /**
         * The game entity behind a Bukkit one, for the visibility check.
         *
         * <p>Only the wrappers this bridge builds can answer. A null return is handled by the caller as
         * "not visible", which is the honest answer for an entity this bridge does not own.
         */
        private net.minecraft.world.entity.Entity gameEntity(org.bukkit.entity.Entity other) {
                if (other instanceof ForbricEntity bukkitEntity) {
                        return bukkitEntity.entity();
                }
                if (other instanceof net.minecraft.server.level.ServerPlayer gamePlayer) {
                        return gamePlayer;
                }
                return null;
        }

	@Override
	public java.lang.Iterable<? extends net.kyori.adventure.bossbar.BossBar> activeBossBars() {
		throw notYet("activeBossBars");
	}

	@Override
	public net.kyori.adventure.text.Component displayName() {
		throw notYet("displayName");
	}

	@Override
	public void displayName(net.kyori.adventure.text.Component arg0) {
		throw notYet("displayName");
	}

	@Override
	public void playerListName(net.kyori.adventure.text.Component arg0) {
		throw notYet("playerListName");
	}

	@Override
	public net.kyori.adventure.text.Component playerListName() {
		throw notYet("playerListName");
	}

	@Override
	public net.kyori.adventure.text.Component playerListHeader() {
		throw notYet("playerListHeader");
	}

	@Override
	public net.kyori.adventure.text.Component playerListFooter() {
		throw notYet("playerListFooter");
	}

	@Override
	public int getPlayerListOrder() {
		throw notYet("getPlayerListOrder");
	}

	@Override
	public void setPlayerListOrder(int arg0) {
		throw notYet("setPlayerListOrder");
	}

	@Override
	public java.lang.String getPlayerListHeader() {
		throw notYet("getPlayerListHeader");
	}

	@Override
	public java.lang.String getPlayerListFooter() {
		throw notYet("getPlayerListFooter");
	}

	@Override
	public void setPlayerListHeader(java.lang.String arg0) {
		throw notYet("setPlayerListHeader");
	}

	@Override
	public void setPlayerListFooter(java.lang.String arg0) {
		throw notYet("setPlayerListFooter");
	}

	@Override
	public void setPlayerListHeaderFooter(java.lang.String arg0, java.lang.String arg1) {
		throw notYet("setPlayerListHeaderFooter");
	}

	@Override
	public void setCompassTarget(org.bukkit.Location arg0) {
		throw notYet("setCompassTarget");
	}

	@Override
	public org.bukkit.Location getCompassTarget() {
		throw notYet("getCompassTarget");
	}

	@Override
	public java.net.InetSocketAddress getAddress() {
		throw notYet("getAddress");
	}

	@Override
	public java.net.InetSocketAddress getHAProxyAddress() {
		throw notYet("getHAProxyAddress");
	}

	@Override
	public boolean isTransferred() {
		throw notYet("isTransferred");
	}

	@Override
	public java.util.concurrent.CompletableFuture<byte[]> retrieveCookie(org.bukkit.NamespacedKey arg0) {
		throw notYet("retrieveCookie");
	}

	@Override
	public void storeCookie(org.bukkit.NamespacedKey arg0, byte[] arg1) {
		throw notYet("storeCookie");
	}

	@Override
	public void transfer(java.lang.String arg0, int arg1) {
		throw notYet("transfer");
	}

	@Override
	public void sendRawMessage(java.lang.String arg0) {
		throw notYet("sendRawMessage");
	}

	@Override
	public void kickPlayer(java.lang.String arg0) {
		throw notYet("kickPlayer");
	}

	@Override
	public <E extends org.bukkit.BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> E ban(java.lang.String arg0, java.util.Date arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("ban");
	}

	@Override
	public <E extends org.bukkit.BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> E ban(java.lang.String arg0, java.time.Instant arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("ban");
	}

	@Override
	public <E extends org.bukkit.BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> E ban(java.lang.String arg0, java.time.Duration arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("ban");
	}

	@Override
	public org.bukkit.BanEntry<java.net.InetAddress> banIp(java.lang.String arg0, java.util.Date arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("banIp");
	}

	@Override
	public org.bukkit.BanEntry<java.net.InetAddress> banIp(java.lang.String arg0, java.time.Instant arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("banIp");
	}

	@Override
	public org.bukkit.BanEntry<java.net.InetAddress> banIp(java.lang.String arg0, java.time.Duration arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("banIp");
	}

	@Override
	public void chat(java.lang.String arg0) {
		throw notYet("chat");
	}

	@Override
	public boolean performCommand(java.lang.String arg0) throws org.bukkit.command.CommandException {
		throw notYet("performCommand");
	}

	@Override
	public boolean isOnGround() {
		throw notYet("isOnGround");
	}

	@Override
	public boolean isSneaking() {
		throw notYet("isSneaking");
	}

	@Override
	public void setSneaking(boolean arg0) {
		throw notYet("setSneaking");
	}

	@Override
	public boolean isSprinting() {
		throw notYet("isSprinting");
	}

	@Override
	public void setSprinting(boolean arg0) {
		throw notYet("setSprinting");
	}

	@Override
	public void saveData() {
		throw notYet("saveData");
	}

	@Override
	public void loadData() {
		throw notYet("loadData");
	}

	@Override
	public void setSleepingIgnored(boolean arg0) {
		throw notYet("setSleepingIgnored");
	}

	@Override
	public boolean isSleepingIgnored() {
		throw notYet("isSleepingIgnored");
	}

	@Override
	public void setRespawnLocation(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("setRespawnLocation");
	}

	@Override
	public java.util.Collection<org.bukkit.entity.EnderPearl> getEnderPearls() {
		throw notYet("getEnderPearls");
	}

	@Override
	public org.bukkit.Input getCurrentInput() {
		throw notYet("getCurrentInput");
	}

	@Override
	public void playNote(org.bukkit.Location arg0, org.bukkit.Instrument arg1, org.bukkit.Note arg2) {
		throw notYet("playNote");
	}

	@Override
	public void playSound(org.bukkit.Location arg0, org.bukkit.Sound arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.Location arg0, java.lang.String arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.Location arg0, org.bukkit.Sound arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4, long arg5) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.Location arg0, java.lang.String arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4, long arg5) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.entity.Entity arg0, org.bukkit.Sound arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.entity.Entity arg0, java.lang.String arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.entity.Entity arg0, org.bukkit.Sound arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4, long arg5) {
		throw notYet("playSound");
	}

	@Override
	public void playSound(org.bukkit.entity.Entity arg0, java.lang.String arg1, org.bukkit.SoundCategory arg2, float arg3, float arg4, long arg5) {
		throw notYet("playSound");
	}

	@Override
	public void stopSound(java.lang.String arg0, org.bukkit.SoundCategory arg1) {
		throw notYet("stopSound");
	}

	@Override
	public void stopSound(org.bukkit.SoundCategory arg0) {
		throw notYet("stopSound");
	}

	@Override
	public void stopAllSounds() {
		throw notYet("stopAllSounds");
	}

	@Override
	public void playEffect(org.bukkit.Location arg0, org.bukkit.Effect arg1, int arg2) {
		throw notYet("playEffect");
	}

	@Override
	public <T> void playEffect(org.bukkit.Location arg0, org.bukkit.Effect arg1, T arg2) {
		throw notYet("playEffect");
	}

	@Override
	public boolean breakBlock(org.bukkit.block.Block arg0) {
		throw notYet("breakBlock");
	}

	@Override
	public void sendBlockChange(org.bukkit.Location arg0, org.bukkit.Material arg1, byte arg2) {
		throw notYet("sendBlockChange");
	}

	@Override
	public void sendBlockChange(org.bukkit.Location arg0, org.bukkit.block.data.BlockData arg1) {
		throw notYet("sendBlockChange");
	}

	@Override
	public void sendBlockChanges(java.util.Collection<org.bukkit.block.BlockState> arg0) {
		throw notYet("sendBlockChanges");
	}

	@Override
	public void sendMultiBlockChange(java.util.Map<? extends io.papermc.paper.math.Position, org.bukkit.block.data.BlockData> arg0) {
		throw notYet("sendMultiBlockChange");
	}

	@Override
	public void sendBlockDamage(org.bukkit.Location arg0, float arg1, org.bukkit.entity.Entity arg2) {
		throw notYet("sendBlockDamage");
	}

	@Override
	public void sendBlockDamage(org.bukkit.Location arg0, float arg1, int arg2) {
		throw notYet("sendBlockDamage");
	}

	@Override
	public void sendEquipmentChange(org.bukkit.entity.LivingEntity arg0, org.bukkit.inventory.EquipmentSlot arg1, org.bukkit.inventory.ItemStack arg2) {
		throw notYet("sendEquipmentChange");
	}

	@Override
	public void sendEquipmentChange(org.bukkit.entity.LivingEntity arg0, java.util.Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> arg1) {
		throw notYet("sendEquipmentChange");
	}

	@Override
	public void sendSignChange(org.bukkit.Location arg0, java.util.List<? extends net.kyori.adventure.text.Component> arg1, org.bukkit.DyeColor arg2, boolean arg3) throws java.lang.IllegalArgumentException {
		throw notYet("sendSignChange");
	}

	@Override
	public void sendSignChange(org.bukkit.Location arg0, java.lang.String[] arg1) throws java.lang.IllegalArgumentException {
		throw notYet("sendSignChange");
	}

	@Override
	public void sendSignChange(org.bukkit.Location arg0, java.lang.String[] arg1, org.bukkit.DyeColor arg2) throws java.lang.IllegalArgumentException {
		throw notYet("sendSignChange");
	}

	@Override
	public void sendSignChange(org.bukkit.Location arg0, java.lang.String[] arg1, org.bukkit.DyeColor arg2, boolean arg3) throws java.lang.IllegalArgumentException {
		throw notYet("sendSignChange");
	}

	@Override
	public void sendBlockUpdate(org.bukkit.Location arg0, org.bukkit.block.TileState arg1) throws java.lang.IllegalArgumentException {
		throw notYet("sendBlockUpdate");
	}

	@Override
	public void sendPotionEffectChange(org.bukkit.entity.LivingEntity arg0, org.bukkit.potion.PotionEffect arg1) {
		throw notYet("sendPotionEffectChange");
	}

	@Override
	public void sendPotionEffectChangeRemove(org.bukkit.entity.LivingEntity arg0, org.bukkit.potion.PotionEffectType arg1) {
		throw notYet("sendPotionEffectChangeRemove");
	}

	@Override
	public void sendMap(org.bukkit.map.MapView arg0) {
		throw notYet("sendMap");
	}

	@Override
	public void showWinScreen() {
		throw notYet("showWinScreen");
	}

	@Override
	public boolean hasSeenWinScreen() {
		throw notYet("hasSeenWinScreen");
	}

	@Override
	public void setHasSeenWinScreen(boolean arg0) {
		throw notYet("setHasSeenWinScreen");
	}

	@Override
	public void sendActionBar(char arg0, java.lang.String arg1) {
		throw notYet("sendActionBar");
	}

	@Override
	public void sendActionBar(net.md_5.bungee.api.chat.BaseComponent... arg0) {
		throw notYet("sendActionBar");
	}

	@Override
	public void setPlayerListHeaderFooter(net.md_5.bungee.api.chat.BaseComponent[] arg0, net.md_5.bungee.api.chat.BaseComponent[] arg1) {
		throw notYet("setPlayerListHeaderFooter");
	}

	@Override
	public void setPlayerListHeaderFooter(net.md_5.bungee.api.chat.BaseComponent arg0, net.md_5.bungee.api.chat.BaseComponent arg1) {
		throw notYet("setPlayerListHeaderFooter");
	}

	@Override
	public void setTitleTimes(int arg0, int arg1, int arg2) {
		throw notYet("setTitleTimes");
	}

	@Override
	public void setSubtitle(net.md_5.bungee.api.chat.BaseComponent[] arg0) {
		throw notYet("setSubtitle");
	}

	@Override
	public void setSubtitle(net.md_5.bungee.api.chat.BaseComponent arg0) {
		throw notYet("setSubtitle");
	}

	@Override
	public void showTitle(net.md_5.bungee.api.chat.BaseComponent[] arg0) {
		throw notYet("showTitle");
	}

	@Override
	public void showTitle(net.md_5.bungee.api.chat.BaseComponent arg0) {
		throw notYet("showTitle");
	}

	@Override
	public void showTitle(net.md_5.bungee.api.chat.BaseComponent[] arg0, net.md_5.bungee.api.chat.BaseComponent[] arg1, int arg2, int arg3, int arg4) {
		throw notYet("showTitle");
	}

	@Override
	public void showTitle(net.md_5.bungee.api.chat.BaseComponent arg0, net.md_5.bungee.api.chat.BaseComponent arg1, int arg2, int arg3, int arg4) {
		throw notYet("showTitle");
	}

	@Override
	public void sendTitle(com.destroystokyo.paper.Title arg0) {
		throw notYet("sendTitle");
	}

	@Override
	public void updateTitle(com.destroystokyo.paper.Title arg0) {
		throw notYet("updateTitle");
	}

	@Override
	public void hideTitle() {
		throw notYet("hideTitle");
	}

	@Override
	public void sendHurtAnimation(float arg0) {
		throw notYet("sendHurtAnimation");
	}

	@Override
	public void sendLinks(org.bukkit.ServerLinks arg0) {
		throw notYet("sendLinks");
	}

	@Override
	public void addCustomChatCompletions(java.util.Collection<java.lang.String> arg0) {
		throw notYet("addCustomChatCompletions");
	}

	@Override
	public void removeCustomChatCompletions(java.util.Collection<java.lang.String> arg0) {
		throw notYet("removeCustomChatCompletions");
	}

	@Override
	public void setCustomChatCompletions(java.util.Collection<java.lang.String> arg0) {
		throw notYet("setCustomChatCompletions");
	}

	@Override
	public void updateInventory() {
		throw notYet("updateInventory");
	}

	@Override
	public org.bukkit.GameMode getPreviousGameMode() {
		throw notYet("getPreviousGameMode");
	}

	@Override
	public void setPlayerTime(long arg0, boolean arg1) {
		throw notYet("setPlayerTime");
	}

	@Override
	public long getPlayerTime() {
		throw notYet("getPlayerTime");
	}

	@Override
	public long getPlayerTimeOffset() {
		throw notYet("getPlayerTimeOffset");
	}

	@Override
	public boolean isPlayerTimeRelative() {
		throw notYet("isPlayerTimeRelative");
	}

	@Override
	public void resetPlayerTime() {
		throw notYet("resetPlayerTime");
	}

	@Override
	public void setPlayerWeather(org.bukkit.WeatherType arg0) {
		throw notYet("setPlayerWeather");
	}

	@Override
	public org.bukkit.WeatherType getPlayerWeather() {
		throw notYet("getPlayerWeather");
	}

	@Override
	public void resetPlayerWeather() {
		throw notYet("resetPlayerWeather");
	}

	@Override
	public int getExpCooldown() {
		throw notYet("getExpCooldown");
	}

	@Override
	public void setExpCooldown(int arg0) {
		throw notYet("setExpCooldown");
	}

	@Override
	public void giveExp(int arg0, boolean arg1) {
		throw notYet("giveExp");
	}

	@Override
	public int applyMending(int arg0) {
		throw notYet("applyMending");
	}

	@Override
	public void giveExpLevels(int arg0) {
		throw notYet("giveExpLevels");
	}

	@Override
	public float getExp() {
		throw notYet("getExp");
	}

	@Override
	public void setExp(float arg0) {
		throw notYet("setExp");
	}

	@Override
	public int getLevel() {
		throw notYet("getLevel");
	}

	@Override
	public void setLevel(int arg0) {
		throw notYet("setLevel");
	}

	@Override
	public int getTotalExperience() {
		throw notYet("getTotalExperience");
	}

	@Override
	public void setTotalExperience(int arg0) {
		throw notYet("setTotalExperience");
	}

	@Override
	public int calculateTotalExperiencePoints() {
		throw notYet("calculateTotalExperiencePoints");
	}

	@Override
	public void setExperienceLevelAndProgress(int arg0) {
		throw notYet("setExperienceLevelAndProgress");
	}

	@Override
	public int getExperiencePointsNeededForNextLevel() {
		throw notYet("getExperiencePointsNeededForNextLevel");
	}

	@Override
	public void sendExperienceChange(float arg0) {
		throw notYet("sendExperienceChange");
	}

	@Override
	public void sendExperienceChange(float arg0, int arg1) {
		throw notYet("sendExperienceChange");
	}

	@Override
	public boolean getAllowFlight() {
		throw notYet("getAllowFlight");
	}

	@Override
	public void setAllowFlight(boolean arg0) {
		throw notYet("setAllowFlight");
	}

	@Override
	public void setFlyingFallDamage(net.kyori.adventure.util.TriState arg0) {
		throw notYet("setFlyingFallDamage");
	}

	@Override
	public net.kyori.adventure.util.TriState hasFlyingFallDamage() {
		throw notYet("hasFlyingFallDamage");
	}

	@Override
	public void resetFlyingTicks() {
		throw notYet("resetFlyingTicks");
	}

	@Override
	public void hidePlayer(org.bukkit.entity.Player arg0) {
		throw notYet("hidePlayer");
	}

	@Override
	public void showPlayer(org.bukkit.entity.Player arg0) {
		throw notYet("showPlayer");
	}

	@Override
	public void hideEntity(org.bukkit.plugin.Plugin arg0, org.bukkit.entity.Entity arg1) {
		throw notYet("hideEntity");
	}

	@Override
	public void showEntity(org.bukkit.plugin.Plugin arg0, org.bukkit.entity.Entity arg1) {
		throw notYet("showEntity");
	}

	@Override
	public boolean isListed(org.bukkit.entity.Player arg0) {
		throw notYet("isListed");
	}

	@Override
	public boolean unlistPlayer(org.bukkit.entity.Player arg0) {
		throw notYet("unlistPlayer");
	}

	@Override
	public boolean listPlayer(org.bukkit.entity.Player arg0) {
		throw notYet("listPlayer");
	}

	@Override
	public boolean isFlying() {
		throw notYet("isFlying");
	}

	@Override
	public void setFlying(boolean arg0) {
		throw notYet("setFlying");
	}

	@Override
	public void setFlySpeed(float arg0) throws java.lang.IllegalArgumentException {
		throw notYet("setFlySpeed");
	}

	@Override
	public void setWalkSpeed(float arg0) throws java.lang.IllegalArgumentException {
		throw notYet("setWalkSpeed");
	}

	@Override
	public float getFlySpeed() {
		throw notYet("getFlySpeed");
	}

	@Override
	public float getWalkSpeed() {
		throw notYet("getWalkSpeed");
	}

	@Override
	public void setResourcePack(java.lang.String arg0, byte[] arg1, java.lang.String arg2, boolean arg3) {
		throw notYet("setResourcePack");
	}

	@Override
	public void setResourcePack(java.util.UUID arg0, java.lang.String arg1, byte[] arg2, java.lang.String arg3, boolean arg4) {
		throw notYet("setResourcePack");
	}

	@Override
	public void setResourcePack(java.util.UUID arg0, java.lang.String arg1, byte[] arg2, net.kyori.adventure.text.Component arg3, boolean arg4) {
		throw notYet("setResourcePack");
	}

	@Override
	public org.bukkit.event.player.PlayerResourcePackStatusEvent.Status getResourcePackStatus() {
		throw notYet("getResourcePackStatus");
	}

	@Override
	public void addResourcePack(java.util.UUID arg0, java.lang.String arg1, byte[] arg2, java.lang.String arg3, boolean arg4) {
		throw notYet("addResourcePack");
	}

	@Override
	public void removeResourcePack(java.util.UUID arg0) {
		throw notYet("removeResourcePack");
	}

	@Override
	public void removeResourcePacks() {
		throw notYet("removeResourcePacks");
	}

	@Override
	public org.bukkit.scoreboard.Scoreboard getScoreboard() {
		throw notYet("getScoreboard");
	}

	@Override
	public void setScoreboard(org.bukkit.scoreboard.Scoreboard arg0) throws java.lang.IllegalArgumentException, java.lang.IllegalStateException {
		throw notYet("setScoreboard");
	}

	@Override
	public org.bukkit.WorldBorder getWorldBorder() {
		throw notYet("getWorldBorder");
	}

	@Override
	public void setWorldBorder(org.bukkit.WorldBorder arg0) {
		throw notYet("setWorldBorder");
	}

	@Override
	public void sendHealthUpdate(double arg0, int arg1, float arg2) {
		throw notYet("sendHealthUpdate");
	}

	@Override
	public void sendHealthUpdate() {
		throw notYet("sendHealthUpdate");
	}

	@Override
	public boolean isHealthScaled() {
		throw notYet("isHealthScaled");
	}

	@Override
	public void setHealthScaled(boolean arg0) {
		throw notYet("setHealthScaled");
	}

	@Override
	public void setHealthScale(double arg0) throws java.lang.IllegalArgumentException {
		throw notYet("setHealthScale");
	}

	@Override
	public double getHealthScale() {
		throw notYet("getHealthScale");
	}

	@Override
	public org.bukkit.entity.Entity getSpectatorTarget() {
		throw notYet("getSpectatorTarget");
	}

	@Override
	public void setSpectatorTarget(org.bukkit.entity.Entity arg0) {
		throw notYet("setSpectatorTarget");
	}

	@Override
	public void sendTitle(java.lang.String arg0, java.lang.String arg1) {
		throw notYet("sendTitle");
	}

	@Override
	public void sendTitle(java.lang.String arg0, java.lang.String arg1, int arg2, int arg3, int arg4) {
		throw notYet("sendTitle");
	}

	@Override
	public void resetTitle() {
		throw notYet("resetTitle");
	}

	@Override
	public <T> void spawnParticle(org.bukkit.Particle arg0, double arg1, double arg2, double arg3, int arg4, double arg5, double arg6, double arg7, double arg8, T arg9, boolean arg10) {
		throw notYet("spawnParticle");
	}

	@Override
	public org.bukkit.advancement.AdvancementProgress getAdvancementProgress(org.bukkit.advancement.Advancement arg0) {
		throw notYet("getAdvancementProgress");
	}

	@Override
	public int getClientViewDistance() {
		throw notYet("getClientViewDistance");
	}

	@Override
	public java.util.Locale locale() {
		throw notYet("locale");
	}

	@Override
	public int getPing() {
		throw notYet("getPing");
	}

	@Override
	public java.lang.String getLocale() {
		throw notYet("getLocale");
	}

	@Override
	public boolean getAffectsSpawning() {
		throw notYet("getAffectsSpawning");
	}

	@Override
	public void setAffectsSpawning(boolean arg0) {
		throw notYet("setAffectsSpawning");
	}

	@Override
	public int getViewDistance() {
		throw notYet("getViewDistance");
	}

	@Override
	public void setViewDistance(int arg0) {
		throw notYet("setViewDistance");
	}

	@Override
	public int getSimulationDistance() {
		throw notYet("getSimulationDistance");
	}

	@Override
	public void setSimulationDistance(int arg0) {
		throw notYet("setSimulationDistance");
	}

	@Override
	public int getSendViewDistance() {
		throw notYet("getSendViewDistance");
	}

	@Override
	public void setSendViewDistance(int arg0) {
		throw notYet("setSendViewDistance");
	}

	@Override
	public void updateCommands() {
		throw notYet("updateCommands");
	}

	@Override
	public void openBook(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("openBook");
	}

	@Override
	public void openSign(org.bukkit.block.Sign arg0, org.bukkit.block.sign.Side arg1) {
		throw notYet("openSign");
	}

	@Override
	public void openVirtualSign(io.papermc.paper.math.Position arg0, org.bukkit.block.sign.Side arg1) {
		throw notYet("openVirtualSign");
	}

	@Override
	public void showDemoScreen() {
		throw notYet("showDemoScreen");
	}

	@Override
	public boolean isAllowingServerListings() {
		throw notYet("isAllowingServerListings");
	}

	@Override
	public com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile() {
		throw notYet("getPlayerProfile");
	}

	@Override
	public void setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile arg0) {
		throw notYet("setPlayerProfile");
	}

	@Override
	public float getCooldownPeriod() {
		throw notYet("getCooldownPeriod");
	}

	@Override
	public float getCooledAttackStrength(float arg0) {
		throw notYet("getCooledAttackStrength");
	}

	@Override
	public void resetCooldown() {
		throw notYet("resetCooldown");
	}

	@Override
	public <T> T getClientOption(com.destroystokyo.paper.ClientOption<T> arg0) {
		throw notYet("getClientOption");
	}

	@Override
	public void sendOpLevel(byte arg0) {
		throw notYet("sendOpLevel");
	}

	@Override
	public void addAdditionalChatCompletions(java.util.Collection<java.lang.String> arg0) {
		throw notYet("addAdditionalChatCompletions");
	}

	@Override
	public void removeAdditionalChatCompletions(java.util.Collection<java.lang.String> arg0) {
		throw notYet("removeAdditionalChatCompletions");
	}

	@Override
	public java.lang.String getClientBrandName() {
		throw notYet("getClientBrandName");
	}

	@Override
	public void setRotation(float arg0, float arg1) {
		throw notYet("setRotation");
	}

	@Override
	public void setRotation(io.papermc.paper.math.Angle arg0, io.papermc.paper.math.Angle arg1) {
		throw notYet("setRotation");
	}

	@Override
	public void lookAt(org.bukkit.entity.Entity arg0, io.papermc.paper.entity.LookAnchor arg1, io.papermc.paper.entity.LookAnchor arg2) {
		throw notYet("lookAt");
	}

	@Override
	public void showElderGuardian(boolean arg0) {
		throw notYet("showElderGuardian");
	}

	@Override
	public int getWardenWarningCooldown() {
		throw notYet("getWardenWarningCooldown");
	}

	@Override
	public void setWardenWarningCooldown(int arg0) {
		throw notYet("setWardenWarningCooldown");
	}

	@Override
	public int getWardenTimeSinceLastWarning() {
		throw notYet("getWardenTimeSinceLastWarning");
	}

	@Override
	public void setWardenTimeSinceLastWarning(int arg0) {
		throw notYet("setWardenTimeSinceLastWarning");
	}

	@Override
	public int getWardenWarningLevel() {
		throw notYet("getWardenWarningLevel");
	}

	@Override
	public void setWardenWarningLevel(int arg0) {
		throw notYet("setWardenWarningLevel");
	}

	@Override
	public void increaseWardenWarningLevel() {
		throw notYet("increaseWardenWarningLevel");
	}

	@Override
	public java.time.Duration getIdleDuration() {
		throw notYet("getIdleDuration");
	}

	@Override
	public void resetIdleDuration() {
		throw notYet("resetIdleDuration");
	}

	@Override
	public java.util.Set<java.lang.Long> getSentChunkKeys() {
		throw notYet("getSentChunkKeys");
	}

	@Override
	public java.util.Set<org.bukkit.Chunk> getSentChunks() {
		throw notYet("getSentChunks");
	}

	@Override
	public boolean isChunkSent(long arg0) {
		throw notYet("isChunkSent");
	}

	@Override
	public org.bukkit.entity.Player.Spigot spigot() {
		throw notYet("spigot");
	}

	@Override
	public void sendEntityEffect(org.bukkit.EntityEffect arg0, org.bukkit.entity.Entity arg1) {
		throw notYet("sendEntityEffect");
	}

	@Override
	public io.papermc.paper.entity.PlayerGiveResult give(java.util.Collection<org.bukkit.inventory.ItemStack> arg0, boolean arg1) {
		throw notYet("give");
	}

	@Override
	public int getDeathScreenScore() {
		throw notYet("getDeathScreenScore");
	}

	@Override
	public void setDeathScreenScore(int arg0) {
		throw notYet("setDeathScreenScore");
	}

	@Override
	public io.papermc.paper.connection.PlayerGameConnection getConnection() {
		throw notYet("getConnection");
	}

	@Override
	public void unsetFixedPose() {
		throw notYet("unsetFixedPose");
	}

	@Override
	public org.bukkit.inventory.EntityEquipment getEquipment() {
		throw notYet("getEquipment");
	}

	@Override
	public org.bukkit.inventory.Inventory getEnderChest() {
		throw notYet("getEnderChest");
	}

	@Override
	public org.bukkit.inventory.MainHand getMainHand() {
		throw notYet("getMainHand");
	}

	@Override
	public boolean setWindowProperty(org.bukkit.inventory.InventoryView.Property arg0, int arg1) {
		throw notYet("setWindowProperty");
	}

	@Override
	public int getEnchantmentSeed() {
		throw notYet("getEnchantmentSeed");
	}

	@Override
	public void setEnchantmentSeed(int arg0) {
		throw notYet("setEnchantmentSeed");
	}

	@Override
	public org.bukkit.inventory.InventoryView getOpenInventory() {
		throw notYet("getOpenInventory");
	}

	@Override
	public org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory arg0) {
		throw notYet("openInventory");
	}

	@Override
	public org.bukkit.inventory.InventoryView openWorkbench(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openWorkbench");
	}

	@Override
	public org.bukkit.inventory.InventoryView openEnchanting(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openEnchanting");
	}

	@Override
	public void openInventory(org.bukkit.inventory.InventoryView arg0) {
		throw notYet("openInventory");
	}

	@Override
	public org.bukkit.inventory.InventoryView openMerchant(org.bukkit.inventory.Merchant arg0, boolean arg1) {
		throw notYet("openMerchant");
	}

	@Override
	public org.bukkit.inventory.InventoryView openAnvil(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openAnvil");
	}

	@Override
	public org.bukkit.inventory.InventoryView openCartographyTable(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openCartographyTable");
	}

	@Override
	public org.bukkit.inventory.InventoryView openGrindstone(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openGrindstone");
	}

	@Override
	public org.bukkit.inventory.InventoryView openLoom(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openLoom");
	}

	@Override
	public org.bukkit.inventory.InventoryView openSmithingTable(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openSmithingTable");
	}

	@Override
	public org.bukkit.inventory.InventoryView openStonecutter(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("openStonecutter");
	}

	@Override
	public void closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason arg0) {
		throw notYet("closeInventory");
	}

	@Override
	public org.bukkit.inventory.ItemStack getItemInHand() {
		throw notYet("getItemInHand");
	}

	@Override
	public void setItemInHand(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("setItemInHand");
	}

	@Override
	public org.bukkit.inventory.ItemStack getItemOnCursor() {
		throw notYet("getItemOnCursor");
	}

	@Override
	public void setItemOnCursor(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("setItemOnCursor");
	}

	@Override
	public boolean hasCooldown(org.bukkit.Material arg0) {
		throw notYet("hasCooldown");
	}

	@Override
	public int getCooldown(org.bukkit.Material arg0) {
		throw notYet("getCooldown");
	}

	@Override
	public void setHurtDirection(float arg0) {
		throw notYet("setHurtDirection");
	}

	@Override
	public boolean isDeeplySleeping() {
		throw notYet("isDeeplySleeping");
	}

	@Override
	public boolean hasCooldown(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("hasCooldown");
	}

	@Override
	public int getCooldown(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("getCooldown");
	}

	@Override
	public void setCooldown(org.bukkit.inventory.ItemStack arg0, int arg1) {
		throw notYet("setCooldown");
	}

	@Override
	public int getCooldown(net.kyori.adventure.key.Key arg0) {
		throw notYet("getCooldown");
	}

	@Override
	public void setCooldown(net.kyori.adventure.key.Key arg0, int arg1) {
		throw notYet("setCooldown");
	}

	@Override
	public int getSleepTicks() {
		throw notYet("getSleepTicks");
	}

	@Override
	public org.bukkit.Location getPotentialRespawnLocation() {
		throw notYet("getPotentialRespawnLocation");
	}

	@Override
	public org.bukkit.entity.FishHook getFishHook() {
		throw notYet("getFishHook");
	}

	@Override
	public boolean sleep(org.bukkit.Location arg0, boolean arg1) {
		throw notYet("sleep");
	}

	@Override
	public void wakeup(boolean arg0) {
		throw notYet("wakeup");
	}

	@Override
	public void startRiptideAttack(int arg0, float arg1, org.bukkit.inventory.ItemStack arg2) {
		throw notYet("startRiptideAttack");
	}

	@Override
	public org.bukkit.Location getBedLocation() {
		throw notYet("getBedLocation");
	}

	@Override
	public org.bukkit.GameMode getGameMode() {
		throw notYet("getGameMode");
	}

	@Override
	public void setGameMode(org.bukkit.GameMode arg0) {
		throw notYet("setGameMode");
	}

	@Override
	public boolean isBlocking() {
		throw notYet("isBlocking");
	}

	@Override
	public boolean isHandRaised() {
		throw notYet("isHandRaised");
	}

	@Override
	public int getExpToLevel() {
		throw notYet("getExpToLevel");
	}

	@Override
	public org.bukkit.entity.Entity releaseLeftShoulderEntity() {
		throw notYet("releaseLeftShoulderEntity");
	}

	@Override
	public org.bukkit.entity.Entity releaseRightShoulderEntity() {
		throw notYet("releaseRightShoulderEntity");
	}

	@Override
	public float getAttackCooldown() {
		throw notYet("getAttackCooldown");
	}

	@Override
	public int discoverRecipes(java.util.Collection<org.bukkit.NamespacedKey> arg0) {
		throw notYet("discoverRecipes");
	}

	@Override
	public int undiscoverRecipes(java.util.Collection<org.bukkit.NamespacedKey> arg0) {
		throw notYet("undiscoverRecipes");
	}

	@Override
	public boolean hasDiscoveredRecipe(org.bukkit.NamespacedKey arg0) {
		throw notYet("hasDiscoveredRecipe");
	}

	@Override
	public java.util.Set<org.bukkit.NamespacedKey> getDiscoveredRecipes() {
		throw notYet("getDiscoveredRecipes");
	}

	@Override
	public org.bukkit.entity.Entity getShoulderEntityLeft() {
		throw notYet("getShoulderEntityLeft");
	}

	@Override
	public void setShoulderEntityLeft(org.bukkit.entity.Entity arg0) {
		throw notYet("setShoulderEntityLeft");
	}

	@Override
	public org.bukkit.entity.Entity getShoulderEntityRight() {
		throw notYet("getShoulderEntityRight");
	}

	@Override
	public void setShoulderEntityRight(org.bukkit.entity.Entity arg0) {
		throw notYet("setShoulderEntityRight");
	}

	@Override
	public boolean dropItem(boolean arg0) {
		throw notYet("dropItem");
	}

	@Override
	public org.bukkit.entity.Item dropItem(int arg0, int arg1, boolean arg2, java.util.function.Consumer<org.bukkit.entity.Item> arg3) {
		throw notYet("dropItem");
	}

	@Override
	public org.bukkit.entity.Item dropItem(org.bukkit.inventory.EquipmentSlot arg0, int arg1, boolean arg2, java.util.function.Consumer<org.bukkit.entity.Item> arg3) {
		throw notYet("dropItem");
	}

	@Override
	public org.bukkit.entity.Item dropItem(org.bukkit.inventory.ItemStack arg0, boolean arg1, java.util.function.Consumer<org.bukkit.entity.Item> arg2) {
		throw notYet("dropItem");
	}

	@Override
	public float getExhaustion() {
		throw notYet("getExhaustion");
	}

	@Override
	public void setExhaustion(float arg0) {
		throw notYet("setExhaustion");
	}

	@Override
	public float getSaturation() {
		throw notYet("getSaturation");
	}

	@Override
	public void setSaturation(float arg0) {
		throw notYet("setSaturation");
	}

	@Override
	public int getFoodLevel() {
		throw notYet("getFoodLevel");
	}

	@Override
	public void setFoodLevel(int arg0) {
		throw notYet("setFoodLevel");
	}

	@Override
	public int getSaturatedRegenRate() {
		throw notYet("getSaturatedRegenRate");
	}

	@Override
	public void setSaturatedRegenRate(int arg0) {
		throw notYet("setSaturatedRegenRate");
	}

	@Override
	public int getUnsaturatedRegenRate() {
		throw notYet("getUnsaturatedRegenRate");
	}

	@Override
	public void setUnsaturatedRegenRate(int arg0) {
		throw notYet("setUnsaturatedRegenRate");
	}

	@Override
	public int getStarvationRate() {
		throw notYet("getStarvationRate");
	}

	@Override
	public void setStarvationRate(int arg0) {
		throw notYet("setStarvationRate");
	}

	@Override
	public org.bukkit.Location getLastDeathLocation() {
		throw notYet("getLastDeathLocation");
	}

	@Override
	public void setLastDeathLocation(org.bukkit.Location arg0) {
		throw notYet("setLastDeathLocation");
	}

	@Override
	public org.bukkit.entity.Firework fireworkBoost(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("fireworkBoost");
	}

	@Override
	public double getEyeHeight() {
		throw notYet("getEyeHeight");
	}

	@Override
	public double getEyeHeight(boolean arg0) {
		throw notYet("getEyeHeight");
	}

	@Override
	public java.util.List<org.bukkit.block.Block> getLineOfSight(java.util.Set<org.bukkit.Material> arg0, int arg1) {
		throw notYet("getLineOfSight");
	}

	@Override
	public org.bukkit.block.Block getTargetBlock(java.util.Set<org.bukkit.Material> arg0, int arg1) {
		throw notYet("getTargetBlock");
	}

	@Override
	public org.bukkit.block.Block getTargetBlock(int arg0, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode arg1) {
		throw notYet("getTargetBlock");
	}

	@Override
	public org.bukkit.block.BlockFace getTargetBlockFace(int arg0, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode arg1) {
		throw notYet("getTargetBlockFace");
	}

	@Override
	public org.bukkit.block.BlockFace getTargetBlockFace(int arg0, org.bukkit.FluidCollisionMode arg1) {
		throw notYet("getTargetBlockFace");
	}

	@Override
	public com.destroystokyo.paper.block.TargetBlockInfo getTargetBlockInfo(int arg0, com.destroystokyo.paper.block.TargetBlockInfo.FluidMode arg1) {
		throw notYet("getTargetBlockInfo");
	}

	@Override
	public org.bukkit.entity.Entity getTargetEntity(int arg0, boolean arg1) {
		throw notYet("getTargetEntity");
	}

	@Override
	public com.destroystokyo.paper.entity.TargetEntityInfo getTargetEntityInfo(int arg0, boolean arg1) {
		throw notYet("getTargetEntityInfo");
	}

	@Override
	public org.bukkit.util.RayTraceResult rayTraceEntities(int arg0, boolean arg1) {
		throw notYet("rayTraceEntities");
	}

	@Override
	public java.util.List<org.bukkit.block.Block> getLastTwoTargetBlocks(java.util.Set<org.bukkit.Material> arg0, int arg1) {
		throw notYet("getLastTwoTargetBlocks");
	}

	@Override
	public org.bukkit.block.Block getTargetBlockExact(int arg0, org.bukkit.FluidCollisionMode arg1) {
		throw notYet("getTargetBlockExact");
	}

	@Override
	public org.bukkit.util.RayTraceResult rayTraceBlocks(double arg0, org.bukkit.FluidCollisionMode arg1) {
		throw notYet("rayTraceBlocks");
	}

	@Override
	public int getRemainingAir() {
		throw notYet("getRemainingAir");
	}

	@Override
	public void setRemainingAir(int arg0) {
		throw notYet("setRemainingAir");
	}

	@Override
	public int getMaximumAir() {
		throw notYet("getMaximumAir");
	}

	@Override
	public void setMaximumAir(int arg0) {
		throw notYet("setMaximumAir");
	}

	@Override
	public org.bukkit.inventory.ItemStack getItemInUse() {
		throw notYet("getItemInUse");
	}

	@Override
	public int getItemInUseTicks() {
		throw notYet("getItemInUseTicks");
	}

	@Override
	public void setItemInUseTicks(int arg0) {
		throw notYet("setItemInUseTicks");
	}

	@Override
	public int getArrowCooldown() {
		throw notYet("getArrowCooldown");
	}

	@Override
	public void setArrowCooldown(int arg0) {
		throw notYet("setArrowCooldown");
	}

	@Override
	public int getArrowsInBody() {
		throw notYet("getArrowsInBody");
	}

	@Override
	public void setArrowsInBody(int arg0, boolean arg1) {
		throw notYet("setArrowsInBody");
	}

	@Override
	public int getBeeStingerCooldown() {
		throw notYet("getBeeStingerCooldown");
	}

	@Override
	public void setBeeStingerCooldown(int arg0) {
		throw notYet("setBeeStingerCooldown");
	}

	@Override
	public int getBeeStingersInBody() {
		throw notYet("getBeeStingersInBody");
	}

	@Override
	public void setBeeStingersInBody(int arg0) {
		throw notYet("setBeeStingersInBody");
	}

	@Override
	public int getMaximumNoDamageTicks() {
		throw notYet("getMaximumNoDamageTicks");
	}

	@Override
	public void setMaximumNoDamageTicks(int arg0) {
		throw notYet("setMaximumNoDamageTicks");
	}

	@Override
	public double getLastDamage() {
		throw notYet("getLastDamage");
	}

	@Override
	public void setLastDamage(double arg0) {
		throw notYet("setLastDamage");
	}

	@Override
	public int getNoDamageTicks() {
		throw notYet("getNoDamageTicks");
	}

	@Override
	public void setNoDamageTicks(int arg0) {
		throw notYet("setNoDamageTicks");
	}

	@Override
	public int getNoActionTicks() {
		throw notYet("getNoActionTicks");
	}

	@Override
	public void setNoActionTicks(int arg0) {
		throw notYet("setNoActionTicks");
	}

	@Override
	public org.bukkit.entity.Player getKiller() {
		throw notYet("getKiller");
	}

	@Override
	public void setKiller(org.bukkit.entity.Player arg0) {
		throw notYet("setKiller");
	}

	@Override
	public boolean addPotionEffect(org.bukkit.potion.PotionEffect arg0) {
		throw notYet("addPotionEffect");
	}

	@Override
	public boolean addPotionEffects(java.util.Collection<org.bukkit.potion.PotionEffect> arg0) {
		throw notYet("addPotionEffects");
	}

	@Override
	public boolean hasPotionEffect(org.bukkit.potion.PotionEffectType arg0) {
		throw notYet("hasPotionEffect");
	}

	@Override
	public org.bukkit.potion.PotionEffect getPotionEffect(org.bukkit.potion.PotionEffectType arg0) {
		throw notYet("getPotionEffect");
	}

	@Override
	public void removePotionEffect(org.bukkit.potion.PotionEffectType arg0) {
		throw notYet("removePotionEffect");
	}

	@Override
	public java.util.Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects() {
		throw notYet("getActivePotionEffects");
	}

	@Override
	public boolean clearActivePotionEffects() {
		throw notYet("clearActivePotionEffects");
	}

	@Override
	public boolean hasLineOfSight(org.bukkit.entity.Entity arg0) {
		throw notYet("hasLineOfSight");
	}

	@Override
	public boolean hasLineOfSight(org.bukkit.Location arg0) {
		throw notYet("hasLineOfSight");
	}

	@Override
	public boolean getRemoveWhenFarAway() {
		throw notYet("getRemoveWhenFarAway");
	}

	@Override
	public void setRemoveWhenFarAway(boolean arg0) {
		throw notYet("setRemoveWhenFarAway");
	}

	@Override
	public void setCanPickupItems(boolean arg0) {
		throw notYet("setCanPickupItems");
	}

	@Override
	public boolean getCanPickupItems() {
		throw notYet("getCanPickupItems");
	}

	@Override
	public boolean isLeashed() {
		throw notYet("isLeashed");
	}

	@Override
	public org.bukkit.entity.Entity getLeashHolder() throws java.lang.IllegalStateException {
		throw notYet("getLeashHolder");
	}

	@Override
	public boolean setLeashHolder(org.bukkit.entity.Entity arg0) {
		throw notYet("setLeashHolder");
	}

	@Override
	public boolean isGliding() {
		throw notYet("isGliding");
	}

	@Override
	public void setGliding(boolean arg0) {
		throw notYet("setGliding");
	}

	@Override
	public boolean isSwimming() {
		throw notYet("isSwimming");
	}

	@Override
	public void setSwimming(boolean arg0) {
		throw notYet("setSwimming");
	}

	@Override
	public boolean isRiptiding() {
		throw notYet("isRiptiding");
	}

	@Override
	public void setRiptiding(boolean arg0) {
		throw notYet("setRiptiding");
	}

	@Override
	public boolean isSleeping() {
		throw notYet("isSleeping");
	}

	@Override
	public boolean isClimbing() {
		throw notYet("isClimbing");
	}

	@Override
	public void setAI(boolean arg0) {
		throw notYet("setAI");
	}

	@Override
	public boolean hasAI() {
		throw notYet("hasAI");
	}

	@Override
	public void attack(org.bukkit.entity.Entity arg0) {
		throw notYet("attack");
	}

	@Override
	public void swingMainHand() {
		throw notYet("swingMainHand");
	}

	@Override
	public void swingOffHand() {
		throw notYet("swingOffHand");
	}

	@Override
	public void playHurtAnimation(float arg0) {
		throw notYet("playHurtAnimation");
	}

	@Override
	public void setCollidable(boolean arg0) {
		throw notYet("setCollidable");
	}

	@Override
	public boolean isCollidable() {
		throw notYet("isCollidable");
	}

	@Override
	public java.util.Set<java.util.UUID> getCollidableExemptions() {
		throw notYet("getCollidableExemptions");
	}

	@Override
	public <T> T getMemory(org.bukkit.entity.memory.MemoryKey<T> arg0) {
		throw notYet("getMemory");
	}

	@Override
	public <T> void setMemory(org.bukkit.entity.memory.MemoryKey<T> arg0, T arg1) {
		throw notYet("setMemory");
	}

	@Override
	public float getSoundVolume() {
		throw notYet("getSoundVolume");
	}

	@Override
	public float getSoundPitch() {
		throw notYet("getSoundPitch");
	}

	@Override
	public org.bukkit.Sound getHurtSound() {
		throw notYet("getHurtSound");
	}

	@Override
	public org.bukkit.Sound getHurtSound(org.bukkit.damage.DamageSource arg0) {
		throw notYet("getHurtSound");
	}

	@Override
	public org.bukkit.Sound getDeathSound() {
		throw notYet("getDeathSound");
	}

	@Override
	public org.bukkit.Sound getFallDamageSound(int arg0) {
		throw notYet("getFallDamageSound");
	}

	@Override
	public org.bukkit.Sound getFallDamageSoundSmall() {
		throw notYet("getFallDamageSoundSmall");
	}

	@Override
	public org.bukkit.Sound getFallDamageSoundBig() {
		throw notYet("getFallDamageSoundBig");
	}

	@Override
	public org.bukkit.Sound getDrinkingSound(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("getDrinkingSound");
	}

	@Override
	public org.bukkit.Sound getEatingSound(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("getEatingSound");
	}

	@Override
	public boolean canBreatheUnderwater() {
		throw notYet("canBreatheUnderwater");
	}

	@Override
	public org.bukkit.entity.EntityCategory getCategory() {
		throw notYet("getCategory");
	}

	@Override
	public float getSidewaysMovement() {
		throw notYet("getSidewaysMovement");
	}

	@Override
	public float getUpwardsMovement() {
		throw notYet("getUpwardsMovement");
	}

	@Override
	public float getForwardsMovement() {
		throw notYet("getForwardsMovement");
	}

	@Override
	public void startUsingItem(org.bukkit.inventory.EquipmentSlot arg0) {
		throw notYet("startUsingItem");
	}

	@Override
	public void completeUsingActiveItem() {
		throw notYet("completeUsingActiveItem");
	}

	@Override
	public org.bukkit.inventory.ItemStack getActiveItem() {
		throw notYet("getActiveItem");
	}

	@Override
	public void clearActiveItem() {
		throw notYet("clearActiveItem");
	}

	@Override
	public int getActiveItemRemainingTime() {
		throw notYet("getActiveItemRemainingTime");
	}

	@Override
	public void setActiveItemRemainingTime(int arg0) {
		throw notYet("setActiveItemRemainingTime");
	}

	@Override
	public boolean hasActiveItem() {
		throw notYet("hasActiveItem");
	}

	@Override
	public int getActiveItemUsedTime() {
		throw notYet("getActiveItemUsedTime");
	}

	@Override
	public org.bukkit.inventory.EquipmentSlot getActiveItemHand() {
		throw notYet("getActiveItemHand");
	}

	@Override
	public boolean isJumping() {
		throw notYet("isJumping");
	}

	@Override
	public void setJumping(boolean arg0) {
		throw notYet("setJumping");
	}

	@Override
	public void playPickupItemAnimation(org.bukkit.entity.Item arg0, int arg1) {
		throw notYet("playPickupItemAnimation");
	}

	@Override
	public float getHurtDirection() {
		throw notYet("getHurtDirection");
	}

	@Override
	public void knockback(double arg0, double arg1, double arg2) {
		throw notYet("knockback");
	}

	@Override
	public void broadcastSlotBreak(org.bukkit.inventory.EquipmentSlot arg0) {
		throw notYet("broadcastSlotBreak");
	}

	@Override
	public void broadcastSlotBreak(org.bukkit.inventory.EquipmentSlot arg0, java.util.Collection<org.bukkit.entity.Player> arg1) {
		throw notYet("broadcastSlotBreak");
	}

	@Override
	public org.bukkit.inventory.ItemStack damageItemStack(org.bukkit.inventory.ItemStack arg0, int arg1) {
		throw notYet("damageItemStack");
	}

	@Override
	public void damageItemStack(org.bukkit.inventory.EquipmentSlot arg0, int arg1) {
		throw notYet("damageItemStack");
	}

	@Override
	public float getBodyYaw() {
		throw notYet("getBodyYaw");
	}

	@Override
	public void setBodyYaw(float arg0) {
		throw notYet("setBodyYaw");
	}

	@Override
	public boolean canUseEquipmentSlot(org.bukkit.inventory.EquipmentSlot arg0) {
		throw notYet("canUseEquipmentSlot");
	}

	@Override
	public io.papermc.paper.world.damagesource.CombatTracker getCombatTracker() {
		throw notYet("getCombatTracker");
	}

	@Override
	public void setWaypointStyle(net.kyori.adventure.key.Key arg0) {
		throw notYet("setWaypointStyle");
	}

	@Override
	public void setWaypointColor(org.bukkit.Color arg0) {
		throw notYet("setWaypointColor");
	}

	@Override
	public net.kyori.adventure.key.Key getWaypointStyle() {
		throw notYet("getWaypointStyle");
	}

	@Override
	public org.bukkit.Color getWaypointColor() {
		throw notYet("getWaypointColor");
	}

	@Override
	public org.bukkit.attribute.AttributeInstance getAttribute(org.bukkit.attribute.Attribute arg0) {
		throw notYet("getAttribute");
	}

	@Override
	public void registerAttribute(org.bukkit.attribute.Attribute arg0) {
		throw notYet("registerAttribute");
	}

	@Override
	public void damage(double arg0) {
		throw notYet("damage");
	}

	@Override
	public void damage(double arg0, org.bukkit.entity.Entity arg1) {
		throw notYet("damage");
	}

	@Override
	public void damage(double arg0, org.bukkit.damage.DamageSource arg1) {
		throw notYet("damage");
	}

	@Override
	public void kill(org.bukkit.damage.DamageSource arg0) {
		throw notYet("kill");
	}

	@Override
	public double getHealth() {
		throw notYet("getHealth");
	}

	@Override
	public void setHealth(double arg0) {
		throw notYet("setHealth");
	}

	@Override
	public void heal(double arg0, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason arg1) {
		throw notYet("heal");
	}

	@Override
	public double getAbsorptionAmount() {
		throw notYet("getAbsorptionAmount");
	}

	@Override
	public void setAbsorptionAmount(double arg0) {
		throw notYet("setAbsorptionAmount");
	}

	@Override
	public double getMaxHealth() {
		throw notYet("getMaxHealth");
	}

	@Override
	public void setMaxHealth(double arg0) {
		throw notYet("setMaxHealth");
	}

	@Override
	public void resetMaxHealth() {
		throw notYet("resetMaxHealth");
	}

	@Override
	public org.bukkit.Location getLocation(org.bukkit.Location arg0) {
		throw notYet("getLocation");
	}

	@Override
	public void setVelocity(org.bukkit.util.Vector arg0) {
		throw notYet("setVelocity");
	}

	@Override
	public org.bukkit.util.Vector getVelocity() {
		throw notYet("getVelocity");
	}

	@Override
	public double getHeight() {
		throw notYet("getHeight");
	}

	@Override
	public double getWidth() {
		throw notYet("getWidth");
	}

	@Override
	public org.bukkit.util.BoundingBox getBoundingBox() {
		throw notYet("getBoundingBox");
	}

	@Override
	public boolean isInWater() {
		throw notYet("isInWater");
	}

	@Override
	public boolean teleport(org.bukkit.Location arg0, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause arg1, io.papermc.paper.entity.TeleportFlag... arg2) {
		throw notYet("teleport");
	}

	@Override
	public void lookAt(double arg0, double arg1, double arg2, io.papermc.paper.entity.LookAnchor arg3) {
		throw notYet("lookAt");
	}

	@Override
	public boolean teleport(org.bukkit.Location arg0) {
		throw notYet("teleport");
	}

	@Override
	public boolean teleport(org.bukkit.Location arg0, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause arg1) {
		throw notYet("teleport");
	}

	@Override
	public boolean teleport(org.bukkit.entity.Entity arg0) {
		throw notYet("teleport");
	}

	@Override
	public boolean teleport(org.bukkit.entity.Entity arg0, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause arg1) {
		throw notYet("teleport");
	}

	@Override
	public java.util.concurrent.CompletableFuture<java.lang.Boolean> teleportAsync(org.bukkit.Location arg0, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause arg1, io.papermc.paper.entity.TeleportFlag... arg2) {
		throw notYet("teleportAsync");
	}

	@Override
	public java.util.List<org.bukkit.entity.Entity> getNearbyEntities(double arg0, double arg1, double arg2) {
		throw notYet("getNearbyEntities");
	}

	@Override
	public int getEntityId() {
		throw notYet("getEntityId");
	}

	@Override
	public int getFireTicks() {
		throw notYet("getFireTicks");
	}

	@Override
	public int getMaxFireTicks() {
		throw notYet("getMaxFireTicks");
	}

	@Override
	public void setFireTicks(int arg0) {
		throw notYet("setFireTicks");
	}

	@Override
	public void setVisualFire(boolean arg0) {
		throw notYet("setVisualFire");
	}

	@Override
	public void setVisualFire(net.kyori.adventure.util.TriState arg0) {
		throw notYet("setVisualFire");
	}

	@Override
	public boolean isVisualFire() {
		throw notYet("isVisualFire");
	}

	@Override
	public net.kyori.adventure.util.TriState getVisualFire() {
		throw notYet("getVisualFire");
	}

	@Override
	public int getFreezeTicks() {
		throw notYet("getFreezeTicks");
	}

	@Override
	public int getMaxFreezeTicks() {
		throw notYet("getMaxFreezeTicks");
	}

	@Override
	public void setFreezeTicks(int arg0) {
		throw notYet("setFreezeTicks");
	}

	@Override
	public boolean isFrozen() {
		throw notYet("isFrozen");
	}

	@Override
	public void setInvisible(boolean arg0) {
		throw notYet("setInvisible");
	}

	@Override
	public boolean isInvisible() {
		throw notYet("isInvisible");
	}

	@Override
	public void setNoPhysics(boolean arg0) {
		throw notYet("setNoPhysics");
	}

	@Override
	public boolean hasNoPhysics() {
		throw notYet("hasNoPhysics");
	}

	@Override
	public boolean isFreezeTickingLocked() {
		throw notYet("isFreezeTickingLocked");
	}

	@Override
	public void lockFreezeTicks(boolean arg0) {
		throw notYet("lockFreezeTicks");
	}

	@Override
	public void remove() {
		throw notYet("remove");
	}

	@Override
	public org.bukkit.event.entity.EntityRemoveEvent.Cause getRemoveEventCause() {
		throw notYet("getRemoveEventCause");
	}

	@Override
	public io.papermc.paper.entity.RemovalReason getRemovalReason() {
		throw notYet("getRemovalReason");
	}

	@Override
	public boolean isDead() {
		throw notYet("isDead");
	}

	@Override
	public boolean isValid() {
		throw notYet("isValid");
	}

	@Override
	public boolean isPersistent() {
		throw notYet("isPersistent");
	}

	@Override
	public void setPersistent(boolean arg0) {
		throw notYet("setPersistent");
	}

	@Override
	public org.bukkit.entity.Entity getPassenger() {
		throw notYet("getPassenger");
	}

	@Override
	public boolean setPassenger(org.bukkit.entity.Entity arg0) {
		throw notYet("setPassenger");
	}

	@Override
	public java.util.List<org.bukkit.entity.Entity> getPassengers() {
		throw notYet("getPassengers");
	}

	@Override
	public boolean addPassenger(org.bukkit.entity.Entity arg0) {
		throw notYet("addPassenger");
	}

	@Override
	public boolean removePassenger(org.bukkit.entity.Entity arg0) {
		throw notYet("removePassenger");
	}

	@Override
	public boolean isEmpty() {
		throw notYet("isEmpty");
	}

	@Override
	public boolean eject() {
		throw notYet("eject");
	}

	@Override
	public org.bukkit.inventory.ItemStack getPickItemStack() {
		throw notYet("getPickItemStack");
	}

	@Override
	public float getFallDistance() {
		throw notYet("getFallDistance");
	}

	@Override
	public void setFallDistance(float arg0) {
		throw notYet("setFallDistance");
	}

	@Override
	public void setLastDamageCause(org.bukkit.event.entity.EntityDamageEvent arg0) {
		throw notYet("setLastDamageCause");
	}

	@Override
	public org.bukkit.event.entity.EntityDamageEvent getLastDamageCause() {
		throw notYet("getLastDamageCause");
	}

	@Override
	public int getTicksLived() {
		throw notYet("getTicksLived");
	}

	@Override
	public void setTicksLived(int arg0) {
		throw notYet("setTicksLived");
	}

	@Override
	public void playEffect(org.bukkit.EntityEffect arg0) {
		throw notYet("playEffect");
	}

	@Override
	public org.bukkit.entity.EntityType getType() {
		throw notYet("getType");
	}

	@Override
	public org.bukkit.SoundCategory getSoundCategory() {
		throw notYet("getSoundCategory");
	}

	@Override
	public org.bukkit.Sound getSwimSound() {
		throw notYet("getSwimSound");
	}

	@Override
	public org.bukkit.Sound getSwimSplashSound() {
		throw notYet("getSwimSplashSound");
	}

	@Override
	public org.bukkit.Sound getSwimHighSpeedSplashSound() {
		throw notYet("getSwimHighSpeedSplashSound");
	}

	@Override
	public boolean isInsideVehicle() {
		throw notYet("isInsideVehicle");
	}

	@Override
	public boolean leaveVehicle() {
		throw notYet("leaveVehicle");
	}

	@Override
	public org.bukkit.entity.Entity getVehicle() {
		throw notYet("getVehicle");
	}

	@Override
	public void setCustomNameVisible(boolean arg0) {
		throw notYet("setCustomNameVisible");
	}

	@Override
	public boolean isCustomNameVisible() {
		throw notYet("isCustomNameVisible");
	}

	@Override
	public void setVisibleByDefault(boolean arg0) {
		throw notYet("setVisibleByDefault");
	}

	@Override
	public boolean isVisibleByDefault() {
		throw notYet("isVisibleByDefault");
	}

	@Override
	public java.util.Set<org.bukkit.entity.Player> getTrackedBy() {
		throw notYet("getTrackedBy");
	}

	@Override
	public boolean isTrackedBy(org.bukkit.entity.Player arg0) {
		throw notYet("isTrackedBy");
	}

	@Override
	public void setGlowing(boolean arg0) {
		throw notYet("setGlowing");
	}

	@Override
	public boolean isGlowing() {
		throw notYet("isGlowing");
	}

	@Override
	public void setInvulnerable(boolean arg0) {
		throw notYet("setInvulnerable");
	}

	@Override
	public boolean isInvulnerable() {
		throw notYet("isInvulnerable");
	}

	@Override
	public boolean isSilent() {
		throw notYet("isSilent");
	}

	@Override
	public void setSilent(boolean arg0) {
		throw notYet("setSilent");
	}

	@Override
	public boolean hasGravity() {
		throw notYet("hasGravity");
	}

	@Override
	public void setGravity(boolean arg0) {
		throw notYet("setGravity");
	}

	@Override
	public int getPortalCooldown() {
		throw notYet("getPortalCooldown");
	}

	@Override
	public void setPortalCooldown(int arg0) {
		throw notYet("setPortalCooldown");
	}

	@Override
	public java.util.Set<java.lang.String> getScoreboardTags() {
		throw notYet("getScoreboardTags");
	}

	@Override
	public boolean addScoreboardTag(java.lang.String arg0) {
		throw notYet("addScoreboardTag");
	}

	@Override
	public boolean removeScoreboardTag(java.lang.String arg0) {
		throw notYet("removeScoreboardTag");
	}

	@Override
	public org.bukkit.block.PistonMoveReaction getPistonMoveReaction() {
		throw notYet("getPistonMoveReaction");
	}

	@Override
	public org.bukkit.block.BlockFace getFacing() {
		throw notYet("getFacing");
	}

	@Override
	public org.bukkit.entity.Pose getPose() {
		throw notYet("getPose");
	}

	@Override
	public void setPose(org.bukkit.entity.Pose arg0, boolean arg1) {
		throw notYet("setPose");
	}

	@Override
	public boolean hasFixedPose() {
		throw notYet("hasFixedPose");
	}

	@Override
	public org.bukkit.entity.SpawnCategory getSpawnCategory() {
		throw notYet("getSpawnCategory");
	}

	@Override
	public boolean isInWorld() {
		throw notYet("isInWorld");
	}

	@Override
	public java.lang.String getAsString() {
		throw notYet("getAsString");
	}

	@Override
	public org.bukkit.entity.EntitySnapshot createSnapshot() {
		throw notYet("createSnapshot");
	}

	@Override
	public org.bukkit.entity.Entity copy() {
		throw notYet("copy");
	}

	@Override
	public org.bukkit.entity.Entity copy(org.bukkit.Location arg0) {
		throw notYet("copy");
	}

	@Override
	public net.kyori.adventure.text.Component teamDisplayName() {
		throw notYet("teamDisplayName");
	}

	@Override
	public org.bukkit.Location getOrigin() {
		throw notYet("getOrigin");
	}

	@Override
	public boolean fromMobSpawner() {
		throw notYet("fromMobSpawner");
	}

	@Override
	public org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason getEntitySpawnReason() {
		throw notYet("getEntitySpawnReason");
	}

	@Override
	public boolean isUnderWater() {
		throw notYet("isUnderWater");
	}

	@Override
	public boolean isInRain() {
		throw notYet("isInRain");
	}

	@Override
	public boolean isInLava() {
		throw notYet("isInLava");
	}

	@Override
	public boolean isTicking() {
		throw notYet("isTicking");
	}

	@Override
	public java.util.Set<org.bukkit.entity.Player> getTrackedPlayers() {
		throw notYet("getTrackedPlayers");
	}

	@Override
	public boolean spawnAt(org.bukkit.Location arg0, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason arg1) {
		throw notYet("spawnAt");
	}

	@Override
	public boolean isInPowderedSnow() {
		throw notYet("isInPowderedSnow");
	}

	@Override
	public double getX() {
		throw notYet("getX");
	}

	@Override
	public double getY() {
		throw notYet("getY");
	}

	@Override
	public double getZ() {
		throw notYet("getZ");
	}

	@Override
	public float getPitch() {
		throw notYet("getPitch");
	}

	@Override
	public float getYaw() {
		throw notYet("getYaw");
	}

	@Override
	public boolean collidesAt(org.bukkit.Location arg0) {
		throw notYet("collidesAt");
	}

	@Override
	public boolean wouldCollideUsing(org.bukkit.util.BoundingBox arg0) {
		throw notYet("wouldCollideUsing");
	}

	@Override
	public io.papermc.paper.threadedregions.scheduler.EntityScheduler getScheduler() {
		throw notYet("getScheduler");
	}

	@Override
	public java.lang.String getScoreboardEntryName() {
		throw notYet("getScoreboardEntryName");
	}

	@Override
	public void broadcastHurtAnimation(java.util.Collection<org.bukkit.entity.Player> arg0) {
		throw notYet("broadcastHurtAnimation");
	}

	@Override
	public void setMetadata(java.lang.String arg0, org.bukkit.metadata.MetadataValue arg1) {
		throw notYet("setMetadata");
	}

	@Override
	public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(java.lang.String arg0) {
		throw notYet("getMetadata");
	}

	@Override
	public boolean hasMetadata(java.lang.String arg0) {
		throw notYet("hasMetadata");
	}

	@Override
	public void removeMetadata(java.lang.String arg0, org.bukkit.plugin.Plugin arg1) {
		throw notYet("removeMetadata");
	}

	@Override
	public net.kyori.adventure.text.Component name() {
		throw notYet("name");
	}

	@Override
	public boolean isPermissionSet(java.lang.String arg0) {
		throw notYet("isPermissionSet");
	}

	@Override
	public boolean isPermissionSet(org.bukkit.permissions.Permission arg0) {
		throw notYet("isPermissionSet");
	}

	@Override
	public void removeAttachment(org.bukkit.permissions.PermissionAttachment arg0) {
		throw notYet("removeAttachment");
	}

	@Override
	public void recalculatePermissions() {
		throw notYet("recalculatePermissions");
	}

	@Override
	public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
		throw notYet("getEffectivePermissions");
	}

	@Override
	public void setOp(boolean arg0) {
		throw notYet("setOp");
	}

	@Override
	public net.kyori.adventure.text.Component customName() {
		throw notYet("customName");
	}

	@Override
	public void customName(net.kyori.adventure.text.Component arg0) {
		throw notYet("customName");
	}

	@Override
	public java.lang.String getCustomName() {
		throw notYet("getCustomName");
	}

	@Override
	public void setCustomName(java.lang.String arg0) {
		throw notYet("setCustomName");
	}

	@Override
	public org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer() {
		throw notYet("getPersistentDataContainer");
	}

	@Override
	public net.kyori.adventure.sound.Sound.Source soundSource() {
		throw notYet("soundSource");
	}

	@Override
	public <T> T getData(io.papermc.paper.datacomponent.DataComponentType.Valued<T> arg0) {
		throw notYet("getData");
	}

	@Override
	public <T> T getDataOrDefault(io.papermc.paper.datacomponent.DataComponentType.Valued<? extends T> arg0, T arg1) {
		throw notYet("getDataOrDefault");
	}

	@Override
	public boolean hasData(io.papermc.paper.datacomponent.DataComponentType arg0) {
		throw notYet("hasData");
	}

	@Override
	public <T extends org.bukkit.entity.Projectile> T launchProjectile(java.lang.Class<? extends T> arg0, org.bukkit.util.Vector arg1, java.util.function.Consumer<? super T> arg2) {
		throw notYet("launchProjectile");
	}

	@Override
	public net.kyori.adventure.util.TriState getFrictionState() {
		throw notYet("getFrictionState");
	}

	@Override
	public void setFrictionState(net.kyori.adventure.util.TriState arg0) {
		throw notYet("setFrictionState");
	}

	@Override
	public boolean isConversing() {
		throw notYet("isConversing");
	}

	@Override
	public void acceptConversationInput(java.lang.String arg0) {
		throw notYet("acceptConversationInput");
	}

	@Override
	public boolean beginConversation(org.bukkit.conversations.Conversation arg0) {
		throw notYet("beginConversation");
	}

	@Override
	public void abandonConversation(org.bukkit.conversations.Conversation arg0) {
		throw notYet("abandonConversation");
	}

	@Override
	public void abandonConversation(org.bukkit.conversations.Conversation arg0, org.bukkit.conversations.ConversationAbandonedEvent arg1) {
		throw notYet("abandonConversation");
	}

	@Override
	public void sendRawMessage(java.util.UUID arg0, java.lang.String arg1) {
		throw notYet("sendRawMessage");
	}

	@Override
	public boolean isBanned() {
		throw notYet("isBanned");
	}

	@Override
	public <E extends org.bukkit.BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> E ban(java.lang.String arg0, java.util.Date arg1, java.lang.String arg2) {
		throw notYet("ban");
	}

	@Override
	public <E extends org.bukkit.BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> E ban(java.lang.String arg0, java.time.Instant arg1, java.lang.String arg2) {
		throw notYet("ban");
	}

	@Override
	public <E extends org.bukkit.BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> E ban(java.lang.String arg0, java.time.Duration arg1, java.lang.String arg2) {
		throw notYet("ban");
	}

	@Override
	public boolean isWhitelisted() {
		throw notYet("isWhitelisted");
	}

	@Override
	public void setWhitelisted(boolean arg0) {
		throw notYet("setWhitelisted");
	}

	@Override
	public org.bukkit.entity.Player getPlayer() {
		throw notYet("getPlayer");
	}

	@Override
	public long getFirstPlayed() {
		throw notYet("getFirstPlayed");
	}

	@Override
	public long getLastPlayed() {
		throw notYet("getLastPlayed");
	}

	@Override
	public boolean hasPlayedBefore() {
		throw notYet("hasPlayedBefore");
	}

	@Override
	public long getLastLogin() {
		throw notYet("getLastLogin");
	}

	@Override
	public long getLastSeen() {
		throw notYet("getLastSeen");
	}

	@Override
	public org.bukkit.Location getRespawnLocation(boolean arg0) {
		throw notYet("getRespawnLocation");
	}

	@Override
	public void incrementStatistic(org.bukkit.Statistic arg0) throws java.lang.IllegalArgumentException {
		throw notYet("incrementStatistic");
	}

	@Override
	public void decrementStatistic(org.bukkit.Statistic arg0) throws java.lang.IllegalArgumentException {
		throw notYet("decrementStatistic");
	}

	@Override
	public void incrementStatistic(org.bukkit.Statistic arg0, int arg1) throws java.lang.IllegalArgumentException {
		throw notYet("incrementStatistic");
	}

	@Override
	public void decrementStatistic(org.bukkit.Statistic arg0, int arg1) throws java.lang.IllegalArgumentException {
		throw notYet("decrementStatistic");
	}

	@Override
	public void setStatistic(org.bukkit.Statistic arg0, int arg1) throws java.lang.IllegalArgumentException {
		throw notYet("setStatistic");
	}

	@Override
	public int getStatistic(org.bukkit.Statistic arg0) throws java.lang.IllegalArgumentException {
		throw notYet("getStatistic");
	}

	@Override
	public void incrementStatistic(org.bukkit.Statistic arg0, org.bukkit.Material arg1) throws java.lang.IllegalArgumentException {
		throw notYet("incrementStatistic");
	}

	@Override
	public void decrementStatistic(org.bukkit.Statistic arg0, org.bukkit.Material arg1) throws java.lang.IllegalArgumentException {
		throw notYet("decrementStatistic");
	}

	@Override
	public int getStatistic(org.bukkit.Statistic arg0, org.bukkit.Material arg1) throws java.lang.IllegalArgumentException {
		throw notYet("getStatistic");
	}

	@Override
	public void incrementStatistic(org.bukkit.Statistic arg0, org.bukkit.Material arg1, int arg2) throws java.lang.IllegalArgumentException {
		throw notYet("incrementStatistic");
	}

	@Override
	public void decrementStatistic(org.bukkit.Statistic arg0, org.bukkit.Material arg1, int arg2) throws java.lang.IllegalArgumentException {
		throw notYet("decrementStatistic");
	}

	@Override
	public void setStatistic(org.bukkit.Statistic arg0, org.bukkit.Material arg1, int arg2) throws java.lang.IllegalArgumentException {
		throw notYet("setStatistic");
	}

	@Override
	public void incrementStatistic(org.bukkit.Statistic arg0, org.bukkit.entity.EntityType arg1) throws java.lang.IllegalArgumentException {
		throw notYet("incrementStatistic");
	}

	@Override
	public void decrementStatistic(org.bukkit.Statistic arg0, org.bukkit.entity.EntityType arg1) throws java.lang.IllegalArgumentException {
		throw notYet("decrementStatistic");
	}

	@Override
	public int getStatistic(org.bukkit.Statistic arg0, org.bukkit.entity.EntityType arg1) throws java.lang.IllegalArgumentException {
		throw notYet("getStatistic");
	}

	@Override
	public void incrementStatistic(org.bukkit.Statistic arg0, org.bukkit.entity.EntityType arg1, int arg2) throws java.lang.IllegalArgumentException {
		throw notYet("incrementStatistic");
	}

	@Override
	public void decrementStatistic(org.bukkit.Statistic arg0, org.bukkit.entity.EntityType arg1, int arg2) {
		throw notYet("decrementStatistic");
	}

	@Override
	public void setStatistic(org.bukkit.Statistic arg0, org.bukkit.entity.EntityType arg1, int arg2) {
		throw notYet("setStatistic");
	}

	@Override
	public java.util.Map<java.lang.String, java.lang.Object> serialize() {
		throw notYet("serialize");
	}

	@Override
	public void applySkinToPlayerHeadContents(net.kyori.adventure.text.object.PlayerHeadObjectContents.Builder arg0) {
		throw notYet("applySkinToPlayerHeadContents");
	}

	@Override
	public net.kyori.adventure.text.object.ObjectContents asObjectContents() {
		throw notYet("asObjectContents");
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
	public net.kyori.adventure.identity.Identity identity() {
		throw notYet("identity");
	}

	@Override
	public int getProtocolVersion() {
		throw notYet("getProtocolVersion");
	}

	@Override
	public java.net.InetSocketAddress getVirtualHost() {
		throw notYet("getVirtualHost");
	}}
