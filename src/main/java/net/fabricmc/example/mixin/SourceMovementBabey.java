package net.fabricmc.example.mixin;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.tag.EntityTypeTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ClientPlayerEntity.class, priority = 0)
public abstract class SourceMovementBabey extends AbstractClientPlayerEntity {
    private SourceMovementBabey(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Shadow
    public int ticksSinceSprintingChanged;
    @Shadow
    protected int ticksLeftToDoubleTapSprint;
    @Shadow
    private void updateNausea() {}
    @Shadow
    public Input input;
    @Shadow
    private boolean isWalking() { return false;}
    @Shadow
    private boolean inSneakingPose;
    @Shadow
    public boolean shouldSlowDown() { return false; }
    @Final
    @Shadow
    protected MinecraftClient client;
    @Shadow
    private int ticksToNextAutojump;
    @Shadow
    private void pushOutOfBlocks(double x, double z) {}
    @Final
    @Shadow
    public ClientPlayNetworkHandler networkHandler;
    @Shadow
    private int underwaterVisibilityTicks;
    @Shadow
    private boolean falling;
    @Shadow
    protected boolean isCamera() { return false; }
    @Shadow
    public boolean hasJumpingMount() { return false; }
    @Shadow
    private int field_3938;
    @Shadow
    private float mountJumpStrength;
    @Shadow
    public float getMountJumpStrength() { return 0.0F; }
    @Shadow
    protected void startRidingJump() {}

    // Should be an @Overwrite, but fabric-entity-events-v1 is a bitch.

    @Shadow public abstract void move(MovementType movementType, Vec3d movement);

    /**
     * @author Jack Papel, on behalf of God
     * @reason 🥺👉👈
     */
    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    public void tickMovement(CallbackInfo ci) {
        ++this.ticksSinceSprintingChanged;
        if (this.ticksLeftToDoubleTapSprint > 0) {
            --this.ticksLeftToDoubleTapSprint;
        }

        this.updateNausea();
        boolean jumping = this.input.jumping;
        boolean sneaking = this.input.sneaking;
        boolean walking = this.isWalking();
        this.inSneakingPose = !this.getAbilities().flying && !this.isSwimming() && this.wouldPoseNotCollide(EntityPose.CROUCHING) && (this.isSneaking() || !this.isSleeping() && !this.wouldPoseNotCollide(EntityPose.STANDING));
        this.input.tick(this.shouldSlowDown());
        this.client.getTutorialManager().onMovement(this.input);

        boolean isAutoJumping = false;
        if (this.ticksToNextAutojump > 0) {
            --this.ticksToNextAutojump;
            isAutoJumping = true;
            this.input.jumping = true;
        }

        if (!this.noClip) {
            this.pushOutOfBlocks(this.getX() - (double)this.getWidth() * 0.35D, this.getZ() + (double)this.getWidth() * 0.35D);
            this.pushOutOfBlocks(this.getX() - (double)this.getWidth() * 0.35D, this.getZ() - (double)this.getWidth() * 0.35D);
            this.pushOutOfBlocks(this.getX() + (double)this.getWidth() * 0.35D, this.getZ() - (double)this.getWidth() * 0.35D);
            this.pushOutOfBlocks(this.getX() + (double)this.getWidth() * 0.35D, this.getZ() + (double)this.getWidth() * 0.35D);
        }

        if (sneaking) {
            this.ticksLeftToDoubleTapSprint = 0;
        }

        boolean canSprint = (float)this.getHungerManager().getFoodLevel() > 6.0F || this.getAbilities().allowFlying;
        if ((this.onGround || this.isSubmergedInWater()) && !sneaking && !walking && this.isWalking() && !this.isSprinting() && canSprint && !this.isUsingItem() && !this.hasStatusEffect(StatusEffects.BLINDNESS)) {
            if (this.ticksLeftToDoubleTapSprint <= 0 && !this.client.options.keySprint.isPressed()) {
                this.ticksLeftToDoubleTapSprint = 7;
            } else {
                this.setSprinting(true);
            }
        }
        this.world.getProfiler().push("travel");
        if (this.noClip){
            this.fullNoClipMove(SV_NOCLIPSPEED, SV_NOCLIPACCELERATE);
        } else if (this.getAbilities().flying) {
            //this.fullTossMove();
        } else if (this.isClimbing()){
            //this.fullLadderMove();
        } else if(this.isWalking()) {
            //this.fullWalkMove();
        } else if (this.isSpectator()) {
            //this.fullObserverMove();
        }
        this.world.getProfiler().pop();
        /*TODO figure out sprinting
        if (!this.isSprinting() && (!this.isTouchingWater() || this.isSubmergedInWater()) && this.isWalking() && canSprint && !this.isUsingItem() && !this.hasStatusEffect(StatusEffects.BLINDNESS) && this.client.options.keySprint.isPressed()) {
            this.setSprinting(true);
        }

        boolean maybeShouldStopSprinting;
        if (this.isSprinting()) {
            maybeShouldStopSprinting = !this.input.hasForwardMovement() || !canSprint;
            boolean shouldStopSprinting = maybeShouldStopSprinting || this.horizontalCollision && !this.collidedSoftly || this.isTouchingWater() && !this.isSubmergedInWater();
            if (this.isSwimming()) {
                if (!this.onGround && !this.input.sneaking && maybeShouldStopSprinting || !this.isTouchingWater()) {
                    this.setSprinting(false);
                }
            } else if (shouldStopSprinting) {
                this.setSprinting(false);
            }
        }*/

        boolean bl6 = false;
        if (this.getAbilities().allowFlying) {
            if (this.client.interactionManager.isFlyingLocked()) {
                if (!this.getAbilities().flying) {
                    this.getAbilities().flying = true;
                    bl6 = true;
                    this.sendAbilitiesUpdate();
                }
            } else if (!jumping && this.input.jumping && !isAutoJumping) {
                if (this.abilityResyncCountdown == 0) {
                    this.abilityResyncCountdown = 7;
                } else if (!this.isSwimming()) {
                    this.getAbilities().flying = !this.getAbilities().flying;
                    bl6 = true;
                    this.sendAbilitiesUpdate();
                    this.abilityResyncCountdown = 0;
                }
            }
        }

        /*
        FLYING
         */
        /* TODO Elytra Flight
        if (this.input.jumping && !bl6 && !jumping && !this.getAbilities().flying && !this.hasVehicle() && !this.isClimbing()) {
            *//*
             * net.fabricmc.fabric.mixin.entity.event.elytra.ClientPlayerEntityMixin
             * I'll please them even if they can't please me.
             *//*
            if (this.checkFallFlying()) {
                this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }*/
        int i;
        if (this.isSubmergedIn(FluidTags.WATER)) {
            i = this.isSpectator() ? 10 : 1;
            this.underwaterVisibilityTicks = MathHelper.clamp(this.underwaterVisibilityTicks + i, 0, 600);
        } else if (this.underwaterVisibilityTicks > 0) {
            this.isSubmergedIn(FluidTags.WATER);
            this.underwaterVisibilityTicks = MathHelper.clamp(this.underwaterVisibilityTicks - 10, 0, 600);
        }
        /* TODO figure out mounts
        if (this.hasJumpingMount()) {
            JumpingMount jumpingMount = (JumpingMount)this.getVehicle();
            if (this.field_3938 < 0) {
                ++this.field_3938;
                if (this.field_3938 == 0) {
                    this.mountJumpStrength = 0.0F;
                }
            }

            if (jumping && !this.input.jumping) {
                this.field_3938 = -10;
                jumpingMount.setJumpStrength(MathHelper.floor(this.getMountJumpStrength() * 100.0F));
                this.startRidingJump();
            } else if (!jumping && this.input.jumping) {
                this.field_3938 = 0;
                this.mountJumpStrength = 0.0F;
            } else if (jumping) {
                ++this.field_3938;
                if (this.field_3938 < 10) {
                    this.mountJumpStrength = (float)this.field_3938 * 0.1F;
                } else {
                    this.mountJumpStrength = 0.8F + 2.0F / (float)(this.field_3938 - 9) * 0.1F;
                }
            }
        } else {
            this.mountJumpStrength = 0.0F;
        }*/

        superTickMovement();

        if (this.onGround && this.getAbilities().flying && !this.client.interactionManager.isFlyingLocked()) {
            this.getAbilities().flying = false;
            this.sendAbilitiesUpdate();
        }

        ci.cancel();
    }

    // Required parts of super.tickMovement()
    @Unique
    private void superTickMovement() {
        if (this.abilityResyncCountdown > 0) {
            --this.abilityResyncCountdown;
        }

        if (this.world.getDifficulty() == Difficulty.PEACEFUL && this.world.getGameRules().getBoolean(GameRules.NATURAL_REGENERATION)) {
            if (this.getHealth() < this.getMaxHealth() && this.age % 20 == 0) {
                this.heal(1.0F);
            }

            if (this.hungerManager.isNotFull() && this.age % 10 == 0) {
                this.hungerManager.setFoodLevel(this.hungerManager.getFoodLevel() + 1);
            }
        }

        this.getInventory().updateItems();
        //this.travel(new Vec3d(this.sidewaysSpeed, this.upwardSpeed, this.forwardSpeed));

        if (this.bodyTrackingIncrements > 0) {
            double d = this.getX() + (this.serverX - this.getX()) / (double)this.bodyTrackingIncrements;
            double e = this.getY() + (this.serverY - this.getY()) / (double)this.bodyTrackingIncrements;
            double f = this.getZ() + (this.serverZ - this.getZ()) / (double)this.bodyTrackingIncrements;
            double g = MathHelper.wrapDegrees(this.serverYaw - (double)this.getYaw());
            this.setYaw(this.getYaw() + (float)g / (float)this.bodyTrackingIncrements);
            this.setPitch(this.getPitch() + (float)(this.serverPitch - (double)this.getPitch()) / (float)this.bodyTrackingIncrements);
            --this.bodyTrackingIncrements;
            this.setPosition(d, e, f);
            this.setRotation(this.getYaw(), this.getPitch());
        }
        if (this.headTrackingIncrements > 0) {
            this.headYaw = (float)((double)this.headYaw + MathHelper.wrapDegrees(this.serverHeadYaw - (double)this.headYaw) / (double)this.headTrackingIncrements);
            --this.headTrackingIncrements;
        }

        this.world.getProfiler().push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.sidewaysSpeed = 0.0F;
            this.forwardSpeed = 0.0F;
        } else if (this.canMoveVoluntarily()) {
            this.world.getProfiler().push("newAi");
            this.tickNewAi();
            this.world.getProfiler().pop();
        }
        this.world.getProfiler().pop();

        this.world.getProfiler().push("jump");
        this.world.getProfiler().pop();

        this.world.getProfiler().push("freezing");
        boolean bl2 = this.getType().isIn(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES);
        int m;
        if (!this.world.isClient && !this.isDead()) {
            m = this.getFrozenTicks();
            if (this.inPowderSnow && this.canFreeze()) {
                this.setFrozenTicks(Math.min(this.getMinFreezeDamageTicks(), m + 1));
            } else {
                this.setFrozenTicks(Math.max(0, m - 2));
            }
        }
        this.removePowderSnowSlow();
        this.addPowderSnowSlowIfNeeded();
        if (!this.world.isClient && this.age % 40 == 0 && this.isFreezing() && this.canFreeze()) {
            m = bl2 ? 5 : 1;
            this.damage(DamageSource.FREEZE, (float)m);
        }
        this.tickCramming();
        this.world.getProfiler().pop();
        if (!this.world.isClient && this.hurtByWater() && this.isWet()) {
            this.damage(DamageSource.DROWN, 1.0F);
        }



        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            Box box;
            if (this.hasVehicle() && !this.getVehicle().isRemoved()) {
                box = this.getBoundingBox().union(this.getVehicle().getBoundingBox()).expand(1.0D, 0.0D, 1.0D);
            } else {
                box = this.getBoundingBox().expand(1.0D, 0.5D, 1.0D);
            }

            List<Entity> list = this.world.getOtherEntities(this, box);
            List<Entity> list2 = Lists.newArrayList();

            for(int i = 0; i < list.size(); ++i) {
                Entity entity = (Entity)list.get(i);
                if (entity.getType() == EntityType.EXPERIENCE_ORB) {
                    list2.add(entity);
                } else if (!entity.isRemoved()) {
                    entity.onPlayerCollision(this);
                }
            }

            if (!list2.isEmpty()) {
                Util.getRandom(list2, this.random).onPlayerCollision(this);
            }
        }

        this.thizUpdateShoulderEntity(this.getShoulderEntityLeft());
        this.thizUpdateShoulderEntity(this.getShoulderEntityRight());
        if (!this.world.isClient && (this.fallDistance > 0.5F || this.isTouchingWater()) || this.getAbilities().flying || this.isSleeping() || this.inPowderSnow) {
            this.dropShoulderEntities();
        }

    }

    @Unique
    private void thizUpdateShoulderEntity(@Nullable NbtCompound entityNbt) {
        if (entityNbt != null && (!entityNbt.contains("Silent") || !entityNbt.getBoolean("Silent")) && this.world.random.nextInt(200) == 0) {
            String string = entityNbt.getString("id");
            EntityType.get(string).filter((entityType) -> {
                return entityType == EntityType.PARROT;
            }).ifPresent((entityType) -> {
                if (!ParrotEntity.imitateNearbyMob(this.world, this)) {
                    this.world.playSound((PlayerEntity)null, this.getX(), this.getY(), this.getZ(), ParrotEntity.getRandomSound(this.world, this.world.random), this.getSoundCategory(), 1.0F, ParrotEntity.getSoundPitch(this.world.random));
                }
            });
        }
    }

    /*
    SOURCE
     */
    @Unique
    private static final float SV_MAXSPEED = 320F * 0.01905F * 0.333333333F; // Conversion from source units to meters. Divided by three because one source meter is like, about 3 blocks.
    @Unique
    private static final float SV_NOCLIPSPEED = 5F;
    @Unique
    private static final float SV_NOCLIPACCELERATE = 5F;
    @Unique
    private static final float SV_FRICTION = 1.0F; // 1.0F is no friction. 0.0F is instant stop.
    @Unique
    private float player_m_surfaceFriction = 1.0F;

    @Unique
    private void fullNoClipMove(float factor, float maxacceleration) {
        Vec3d wishvel;
        Vec3d wishdir;
        float wishspeed;
        float maxspeed = SV_MAXSPEED * factor;

        if (!this.input.sneaking) {
            factor /= 2.0f;
        }

        /*
        There's a pretty good chance that this angle math isn't the same as source's.
        Oh well, it's close enough if it isn't.
         */
        // Copy movement amounts
        float fmove = (float) (this.input.movementForward * Math.cos(this.getPitch() * 0.017453292F) * factor);
        float smove = this.input.movementSideways * factor;
        float umove = (float) (this.input.movementForward * -Math.sin(this.getPitch() * 0.017453292F) * factor);

        // Convert basis from forward-facing to absolute
        float sine = MathHelper.sin(this.getYaw() * 0.017453292F);
        float cosine = MathHelper.cos(this.getYaw() * 0.017453292F);

        wishvel = new Vec3d(smove * cosine - fmove * sine, umove, fmove * cosine + smove * sine);

        wishdir = wishvel.normalize();   // Determine maginitude of speed of move // **magnitude
        wishspeed = (float) wishvel.length();

        // Clamp to server defined max speed
        if (wishspeed > maxspeed ) {
            wishvel = wishdir.multiply(maxspeed);
            wishspeed = maxspeed;
        }

        if (maxacceleration > 0.0) {
            // Set pmove velocity
            this.accelerate(wishdir, wishspeed, maxacceleration);

            float spd = (float) this.getVelocity().lengthSquared();
            if (spd < 0.01905f * 0.01905f) {
                this.setVelocity(Vec3d.ZERO);
                return;
            }

            // Bleed off some speed, but if we have less than the bleed
            //  threshhold, bleed the theshold amount.
            float control = Math.max(spd, maxspeed / 4.0F);

            float friction = SV_FRICTION * player_m_surfaceFriction;

            // Add the amount to the drop amount.
            float drop = control * friction * deltaTime();

            // scale the velocity
            float newspeed = spd - drop;
            if (newspeed < 0) newspeed = 0;

            // Determine proportion of old speed we are using.
            newspeed /= spd;
            this.setVelocity(this.getVelocity().multiply(newspeed));
            this.velocityDirty = true;
        } else {
            this.setVelocity(wishvel);
        }

        // Just move ( don't clip or anything )
        this.setPosition(this.getPos().add(this.getVelocity()));
        this.updateLimbs(this, this instanceof Flutterer);

        // Zero out velocity if in noaccel mode
        if (maxacceleration < 0.0f) {
            this.setVelocity(Vec3d.ZERO);
        }
    }

    @Unique
    private boolean canAccelerate()
    {
        // Dead players don't accelerate.
        if (this.isRemoved())
            return false;

        // If waterjumping, don't accelerate
        if (this.touchingWater && this.jumping)
            return false;

        return true;
    }

    @Unique
    private float deltaTime() {
        return this.client.getTickDelta();
    }

    @Unique
    private void accelerate(Vec3d wishdir, float wishspeed, float accel){
        float addspeed, accelspeed, currentspeed;

        // This gets overridden because some games (CSPort) want to allow dead (observer) players
        // to be able to move around.
        if (!canAccelerate())
            return;

        // See if we are changing direction a bit
        currentspeed = (float) this.getVelocity().dotProduct(wishdir);

        // Reduce wishspeed by the amount of veer.
        addspeed = wishspeed - currentspeed;

        // If not going to add any speed, done.
        if (addspeed <= 0)
            return;

        // Determine amount of accleration.
        accelspeed = accel * deltaTime() * wishspeed * player_m_surfaceFriction;

        // Cap at addspeed
        if (accelspeed > addspeed)
            accelspeed = addspeed;

        // Adjust velocity.
        this.setVelocity(this.getVelocity().add(wishdir.multiply(accelspeed)));
    }
}
