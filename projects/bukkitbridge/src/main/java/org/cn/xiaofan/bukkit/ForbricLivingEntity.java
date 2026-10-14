/*
 * Forbric Bukkit bridge — a living entity, as {@code org.bukkit.entity.LivingEntity}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricLivingEntity.table. Edits belong in the table or
 * this template.
 */
package org.cn.xiaofan.bukkit;

/**
 * A mob, an animal, a player-as-a-mob — anything with health, as a plugin sees it.
 *
 * <h2>Why the bridge needs its own wrapper for this</h2>
 *
 * <p>{@code LivingEntity} extends {@code Entity}, so at first glance this class could just be a method on
 * the entity wrapper. It cannot, and the reason is {@code instanceof}:
 *
 * <pre>{@code
 * for (Entity entity : world.getNearbyEntities(center, r, r, r)) {
 *     if (!(entity instanceof LivingEntity living)) continue;   // <-- here
 *     living.damage(power, shooter);
 * }
 * }</pre>
 *
 * <p>That shape is how essentially every plugin applies area damage, and it is the one idiom this bridge
 * has to satisfy. A wrapper that implements only {@code Entity} fails the test — silently, because
 * {@code instanceof} is not an error — so the loop skips every mob and the blast hurts nobody, with no
 * exception anywhere to explain it.
 *
 * <p>So the type hierarchy is mirrored: {@code ForbricEntity} wraps the game's {@code Entity},
 * {@code ForbricLivingEntity} extends it and implements {@code LivingEntity}, and
 * {@code ForbricEntity.of} hands back the narrower one when the game's object is a
 * {@code net.minecraft.world.entity.LivingEntity}. This is why {@code ForbricEntity} is no longer
 * {@code final}.
 *
 * <h2>Health and damage live on a supertype</h2>
 *
 * <p>{@code getHealth}, {@code setHealth} and {@code damage} are declared by
 * {@code org.bukkit.entity.Damageable}, which {@code LivingEntity} extends. The generator collects
 * inherited methods, so they are declared in the table under the interface that owns them and appear here
 * with their own bodies — a plugin calling {@code living.getHealth()} reaches a real answer.
 *
 * <h2>What refuses, and why</h2>
 *
 * <p>Everything else in the interface's ~118 methods refuses by name. That is the point of the
 * generator: an attribute, an equipment slot or an effect this bridge does not model answers with a
 * message naming the method, rather than with a plausible value a plugin would act on.
 */
class ForbricLivingEntity extends ForbricEntity implements org.bukkit.entity.LivingEntity {

    /**
     * The same game object as the superclass holds, but typed as a living entity.
     *
     * <p>Kept separately because the game's {@code LivingEntity} API is not on {@code Entity} — health,
     * damage and healing all need this type, and casting the superclass's field at each use would hide
     * the fact that the two are the same object.
     */
    private final net.minecraft.world.entity.LivingEntity living;

    ForbricLivingEntity(net.minecraft.world.entity.LivingEntity living, ServerImpl server) {
        super(living, server);
        this.living = living;
    }

    /** The wrapped entity, for the bridge's own use by the event layer. */
    net.minecraft.world.entity.LivingEntity livingEntity() {
        return living;
    }

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                "[Forbric/Bukkit] LivingEntity." + what + "() is not implemented by this bridge yet");
    }

    /**
     * How much health this entity has left.
     *
     * <p>Read through on every call, which is the {@code LivingEntity} contract: the value changes as the
     * game ticks, and a cached one would report the health a plugin saw when it first asked.
     */
    @Override
    public double getHealth() {
        return living.getHealth();
    }

    /**
     * Sets health directly.
     *
     * <p>Distinct from {@link #damage(double)} in a way plugins rely on: this writes the number, so armour,
     * absorption and a damage event are all bypassed. That is what "damage that ignores armour" means in
     * a plugin, and it is why the healing and the clamp are applied here rather than delegated — the game
     * would step in and undo them.
     */
    @Override
    public void setHealth(double health) {
        // The game's own clamp is 0..max and it refuses a value outside it, so a plugin passing a
        // negative number would otherwise be silently ignored rather than killing the entity.
        float clamped = (float) Math.max(0.0, Math.min(health, getMaxHealth()));
        living.setHealth(clamped);
    }

    /** This entity's maximum health, which for a player is the attribute the game tracks. */
    @Override
    public double getMaxHealth() {
        return living.getMaxHealth();
    }

    /**
     * Hurts this entity by an amount, attributed to nobody in particular.
     *
     * <p>The game's own hurt path is used, so armour, enchantments, absorption and the damage event all
     * apply — which is what a plugin calling {@code damage(n)} expects, and the reason
     * {@link #setHealth(double)} exists separately for the case where they should not.
     */
    @Override
    public void damage(double amount) {
		// Cast the null: Damageable declares damage(double, Entity) and damage(double, DamageSource), and
		// a bare null matches both, which javac rejects as ambiguous.
		damage(amount, (org.bukkit.entity.Entity) null);
    }
    /**
     * Hurts this entity, attributed to another.
     *
     * <p>The source matters beyond credit: the game builds the damage type from it, and a player-attributed
     * hit is the one that drops experience and triggers a death message naming them. A null or unknown
     * source becomes the generic damage type, which is exactly what "nobody in particular did this" is.
     */
    @Override
    public void damage(double amount, org.bukkit.entity.Entity source) {
        if (amount <= 0) {
            // Zero and negative damage are nothing; the game would treat them as a no-op, and skipping
            // here avoids firing a damage event that reports no damage.
            return;
        }
        net.minecraft.world.damagesource.DamageSources sources =
                server().minecraft() == null ? null : server().minecraft().overworld().damageSources();
        if (sources == null) {
            // No running server to build a damage source from. Writing health directly keeps the call's
            // effect rather than dropping it, and is the same thing setHealth would do.
            setHealth(getHealth() - amount);
            return;
        }
        living.hurt(sources.generic(), (float) amount);
    }

    /** Absorption hearts, which the game tracks as a separate pool from health. */
    @Override
    public double getAbsorptionAmount() {
        return living.getAbsorptionAmount();
    }

    /** Sets the absorption pool. */
    @Override
    public void setAbsorptionAmount(double amount) {
        living.setAbsorptionAmount((float) Math.max(0.0, amount));
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
	public org.bukkit.Location getEyeLocation() {
		throw notYet("getEyeLocation");
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
	public org.bukkit.inventory.EntityEquipment getEquipment() {
		throw notYet("getEquipment");
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
	public void setHurtDirection(float arg0) {
		throw notYet("setHurtDirection");
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
	public void damage(double arg0, org.bukkit.damage.DamageSource arg1) {
		throw notYet("damage");
	}

	@Override
	public void kill(org.bukkit.damage.DamageSource arg0) {
		throw notYet("kill");
	}

	@Override
	public void heal(double arg0, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason arg1) {
		throw notYet("heal");
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
	}}
