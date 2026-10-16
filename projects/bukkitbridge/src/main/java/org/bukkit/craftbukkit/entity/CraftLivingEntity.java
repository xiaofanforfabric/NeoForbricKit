/*
 * Forbric Bukkit bridge — CraftBukkit compatibility layer stub.
 */
package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.LivingEntity;

/**
 * Base class for living entity wrappers.
 */
public abstract class CraftLivingEntity extends CraftEntity {

	public CraftLivingEntity(org.bukkit.entity.LivingEntity entity) {
		super(entity);
	}

	@Override
	public abstract LivingEntity getHandle();
}
