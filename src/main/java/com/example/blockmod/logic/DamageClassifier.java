package com.example.blockmod.logic;

import com.example.blockmod.data.DamageClass;
import com.example.blockmod.registry.ModTags;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Runtime adapter mapping a {@link DamageSource} onto the FR-08 decision table.
 * The decision itself lives in {@link GuardRules#classifyDamage} (pure, unit-tested).
 */
public final class DamageClassifier {
    private DamageClassifier() {}

    public static DamageClass classify(DamageSource source) {
        boolean directProjectile = source.getDirectEntity() instanceof Projectile;
        boolean causingLiving = source.getEntity() instanceof net.minecraft.world.entity.LivingEntity;
        return GuardRules.classifyDamage(
                source.is(ModTags.DAMAGE_GUARD_IGNORED),
                source.is(DamageTypeTags.BYPASSES_SHIELD),
                source.is(DamageTypeTags.BYPASSES_ARMOR),
                source.is(DamageTypeTags.IS_EXPLOSION),
                source.is(DamageTypeTags.IS_PROJECTILE),
                directProjectile,
                source.is(ModTags.DAMAGE_PARRYABLE),
                causingLiving);
    }
}
