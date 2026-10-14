/*
 * Forbric Bukkit bridge — an entity that is not a player.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricEntity.table. Edits belong in the table or this template.
 */
package org.cn.xiaofan.bukkit;

/**
 * A game entity, as a plugin sees it.
 *
 * <p><b>Not a full {@code Entity} implementation, and it says so.</b> Bukkit's {@code Entity} is a large
 * interface covering inventory-holders, projectiles, mobs and everything else; the parts a command source needs
 * are identity, position and the world. Those are answered, and the rest of the interface refuses with a named
 * exception — a plugin calling {@code getFireTicks()} is told the bridge does not implement it yet rather than
 * being handed a plausible wrong answer.
 *
 * <p><b>Why this class exists at all.</b> A command's source is often an entity that is not a player — a mob ran
 * a function, {@code @e} resolved by an argument, a command block's marker armour stand. Paper's
 * {@code CommandSourceStack.getEntityOrThrow()} has to return one, and an argument resolver needs the game's own
 * entity back. A wrapper that keeps the {@code net.minecraft} object is the only thing that can satisfy both.
 */
class ForbricEntity implements org.bukkit.entity.Entity {

        private final net.minecraft.world.entity.Entity entity;
        private final ServerImpl server;

        ForbricEntity(net.minecraft.world.entity.Entity entity, ServerImpl server) {
                this.entity = entity;
                this.server = server;
        }

        /**
         * Wraps a game entity. The one place a {@code net.minecraft.world.entity.Entity} becomes a Bukkit entity.
         *
         * <p>A player becomes a {@link ForbricPlayer} instead, because that is what a plugin will cast the result
         * to — and a {@code Player} failing an {@code instanceof Player} check would be a worse answer than none.
         */
        static org.bukkit.entity.Entity of(net.minecraft.world.entity.Entity entity, ServerImpl server) {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                        return ForbricPlayer.of(player, server);
                }
                // A bolt is still an Entity, but Bukkit has a richer interface for it and a plugin that
                // strikes lightning then asks what it got expects a LightningStrike back. Returning the
                // plain wrapper would fail that check with no hint as to why.
                if (entity instanceof net.minecraft.world.entity.LightningBolt bolt) {
                        return new ForbricLightningStrike(bolt, server);
                }
                // A living entity gets the richer interface for the same reason: a plugin asks every
                // candidate from a nearby-entity search `instanceof LivingEntity` before damaging it, and
                // the plain wrapper fails that test — so a blast that should hurt the mob next to it
                // would silently skip every one of them.
                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                        return new ForbricLivingEntity(living, server);
                }
                return new ForbricEntity(entity, server);
        }

        /** The game's own entity, for the bridge's use — command arguments resolve against it. */
        net.minecraft.world.entity.Entity entity() {
                return entity;
        }

        /**
         * The server this wrapper belongs to, for a subclass that has to build another wrapper.
         *
         * <p>Package-visible rather than private for exactly that reason: a specialised entity
         * ({@code ForbricLightningStrike}) hands its cause back as a player, and building one needs the
         * server the way every other wrapper does.
         */
        ServerImpl server() {
                return server;
        }

        private static UnsupportedOperationException notYet(String what) {
                return new UnsupportedOperationException(
                                "[Forbric/Bukkit] Entity." + what + "() is not implemented by this bridge yet");
        }

        @Override
        public java.util.UUID getUniqueId() {
                return entity.getUUID();
        }

        @Override
        public org.bukkit.Location getLocation() {
                net.minecraft.world.phys.Vec3 position = entity.position();
                return new org.bukkit.Location(world(), position.x(), position.y(), position.z(),
                                entity.getYRot(), entity.getXRot());
        }

        @Override
        public org.bukkit.World getWorld() {
                return world();
        }

        /** The level this entity is in, wrapped. Shared by the position and world accessors. */
        private ForbricWorld world() {
                return ForbricWorld.of((net.minecraft.server.level.ServerLevel) entity.level(), server);
        }

        @Override
        public boolean isDead() {
                return !entity.isAlive();
        }

        @Override
        public boolean isValid() {
                return entity.isAlive() && !entity.isRemoved();
        }

        /**
         * The entity's type, as a Bukkit enum constant.
         *
         * <p>Read through the registry id, the same bridge {@link ForbricBlock#getType()} uses: the id is stable
         * across versions where an ordinal is not, and {@code EntityType} is named after the same id.
         */
        @Override
        public org.bukkit.entity.EntityType getType() {
                net.minecraft.resources.Identifier id =
                                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                try {
                        return org.bukkit.entity.EntityType.valueOf(id.getPath().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                        // A mod-added entity, or one newer than this bridge's copy of the API knows. Naming it
                        // beats answering UNKNOWN, which would look like a real type.
                        throw notYet("getType() for entity " + id);
                }
        }

        /** Refuses: the Spigot view is a legacy surface this bridge does not build. */
        @Override
        public org.bukkit.entity.Entity.Spigot spigot() {
                throw notYet("spigot()");
        }

        @Override
        public java.util.List<org.bukkit.entity.Entity> getPassengers() {
                java.util.List<org.bukkit.entity.Entity> wrapped = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.Entity passenger : entity.getPassengers()) {
                        wrapped.add(of(passenger, server));
                }
                return wrapped;
        }

        @Override
        public boolean addPassenger(org.bukkit.entity.Entity passenger) {
                if (!(passenger instanceof ForbricEntity forbric)) {
                        throw new IllegalArgumentException("[Forbric/Bukkit] Entity.addPassenger() needs an entity "
                                        + "this bridge produced, got " + passenger);
                }
                // Three arguments, not two: the game's two-argument form does not exist, and the third is
                // "force" -- a passenger that refuses (a player with a dismount flag) still gets seated,
                // which is what Bukkit's addPassenger promises.
                return forbric.entity().startRiding(entity, true, true);
        }

        @Override
        public boolean removePassenger(org.bukkit.entity.Entity passenger) {
                if (!(passenger instanceof ForbricEntity forbric)) {
                        return false;
                }
                if (!entity.getPassengers().contains(forbric.entity())) {
                        return false;
                }
                forbric.entity().stopRiding();
                return true;
        }

        /**
         * Equal to any other handle on the same entity.
         *
         * <p>By the game's entity object, not by identity or by UUID alone: one wrapper is handed out from several
         * places (a command source, a world, an event) and a plugin must be able to compare the results.
         */
        @Override
        public boolean equals(Object other) {
                if (this == other) {
                        return true;
                }
                if (!(other instanceof ForbricEntity)) {
                        return false;
                }
                return entity == ((ForbricEntity) other).entity;
        }

        @Override
        public int hashCode() {
                return System.identityHashCode(entity);
        }

        @Override
        public String toString() {
                return "ForbricEntity[" + entity.getUUID() + "]";
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
	public boolean isOnGround() {
		throw notYet("isOnGround");
	}

	@Override
	public boolean isInWater() {
		throw notYet("isInWater");
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
	public org.bukkit.Server getServer() {
		throw notYet("getServer");
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
	public boolean isSneaking() {
		throw notYet("isSneaking");
	}

	@Override
	public void setSneaking(boolean arg0) {
		throw notYet("setSneaking");
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
	public void sendMessage(java.lang.String arg0) {
		throw notYet("sendMessage");
	}

	@Override
	public void sendMessage(java.lang.String... arg0) {
		throw notYet("sendMessage");
	}

	@Override
	public void sendMessage(java.util.UUID arg0, java.lang.String arg1) {
		throw notYet("sendMessage");
	}

	@Override
	public void sendMessage(java.util.UUID arg0, java.lang.String... arg1) {
		throw notYet("sendMessage");
	}

	@Override
	public java.lang.String getName() {
		throw notYet("getName");
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
	public boolean hasPermission(java.lang.String arg0) {
		throw notYet("hasPermission");
	}

	@Override
	public boolean hasPermission(org.bukkit.permissions.Permission arg0) {
		throw notYet("hasPermission");
	}

	@Override
	public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin arg0, java.lang.String arg1, boolean arg2) {
		throw notYet("addAttachment");
	}

	@Override
	public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin arg0) {
		throw notYet("addAttachment");
	}

	@Override
	public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin arg0, java.lang.String arg1, boolean arg2, int arg3) {
		throw notYet("addAttachment");
	}

	@Override
	public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin arg0, int arg1) {
		throw notYet("addAttachment");
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
	public boolean isOp() {
		throw notYet("isOp");
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
	}}
