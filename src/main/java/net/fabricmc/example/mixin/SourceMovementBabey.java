package net.fabricmc.example.mixin;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
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
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.tag.EntityTypeTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockStateRaycastContext;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
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

    @Shadow public abstract boolean isSneaking();

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
            this.fullWalkMove();
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
    private static final float SV_MAXSPEED = convertUnitsSource2MC(320F); // Conversion from source units to meters. Divided by three because one source meter is like, about 3 blocks.
    @Unique
    private static final float SV_NOCLIPSPEED = 5F;
    @Unique
    private static final float SV_NOCLIPACCELERATE = 5F;
    @Unique
    private static final float SV_FRICTION = 1.0F; // 1.0F is no friction. 0.0F is instant stop.
    @Unique
    private float player_m_surfaceFriction = 1.0F;
    @Unique
    private float GAMEMOVEMENT_JUMP_HEIGHT;
    @Unique
    private static final float SV_ACCELERATE = 5.5F; //
    @Unique
    private static final float SV_STOPSPEED = 80F;
    @Unique
    private static final float SV_AIRACCELERATE = 150F; // varies game to game. 150 is apparently TF's default.
    @Unique
    private float gravity;
    @Unique
    private float getGravity() {
        return gravity;
    }
    @Unique
    private static float getCurrentGravity() { return 600F; }
    @Unique
    private Vec3d m_vecWaterJumpVel = Vec3d.ZERO;
    @Unique
    private Vec3d m_outJumpVel = Vec3d.ZERO;
    // TODO determine all of these
    @Unique
    private float m_outStepHeight = 0;
    @Unique
    private static final float WATERJUMP_HEIGHT = 0;
    @Unique
    private float m_flClientMaxSpeed = 0;
    @Unique
    private float m_flUpMove = 0;
    @Unique
    private final float m_flMaxSpeed = 0;
    @Unique
    private static final float PLAYER_FALL_PUNCH_THRESHOLD = 0;
    @Unique
    private float m_Local_m_flStepSize = 0;
    @Unique
    private float m_Local_m_flFallVelocity = 0;
    @Unique
    private Vec3d m_outWishVel = Vec3d.ZERO;
    @Unique
    private boolean m_Local_m_bAllowAutoMovement = false;
    @Unique
    private Vec3d getBaseVelocity() {
        return Vec3d.ZERO;
    }

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

    @Unique
    public void startGravity() {
        float ent_gravity;

        if (this.getGravity() != 0)
            ent_gravity = this.getGravity();
        else
            ent_gravity = 1.0F;

        // Add gravity so they'll be in the correct position during movement
        // yes, this 0.5 looks wrong, but it's not.
        this.setVelocity(this.getVelocity().add(0, -ent_gravity * getCurrentGravity() * 0.5 * deltaTime(), 0));
        //this.setVelocity(this.getVelocity().add(0, this.getBaseVelocity().getY() * this.deltaTime()), 0);

        //Vec3d temp = this.getBaseVelocity();
        //temp = temp.multiply(1F, 0F, 1F);
        //this.setBaseVelocity(temp);

        this.checkVelocity();
    }

    @Unique
    private long m_flWaterJumpTime;

    @Unique
    public void waterJump() {
        if (this.m_flWaterJumpTime > 10000)
            this.m_flWaterJumpTime = 10000;

        if (this.m_flWaterJumpTime == 0)
            return;

        this.m_flWaterJumpTime -= 1000.0f * this.deltaTime();

        if (this.m_flWaterJumpTime <= 0 || !this.touchingWater) {
            this.m_flWaterJumpTime = 0;
        }

        this.setVelocity(this.getVelocity().add(this.m_vecWaterJumpVel.multiply(1F, 0F, 1F)));
    }

    @Unique
    public int tryPlayerMove() {
        // Yeah I'm not porting all of this.
        // This is collision stuff
        return 0;
    }

    @Unique
    public boolean checkJumpButton() {
        if (this.isRemoved()) {
            //mv->m_nOldButtons |= IN_JUMP ;	// don't jump again until released
            return false;
        }

        // See if we are waterjumping.  If so, decrement count and return.
        if (this.m_flWaterJumpTime != 0) {
            this.m_flWaterJumpTime -= this.deltaTime();
            if (this.m_flWaterJumpTime < 0)
                this.m_flWaterJumpTime = 0;

            return false;
        }

        // If we are in the water most of the way...
        if (this.waistDeepInWater) {
            // swimming, not jumping
            this.onGround = false;

            if(this.world.getFluidState(new BlockPos(this.getBoundingBox().getCenter())).isIn(FluidTags.WATER))    // We move up a certain amount
                this.setVelocity(this.getVelocity().add(0F, 100F, 0F));
            //else if (player->GetWaterType() == CONTENTS_SLIME)
            //    mv->m_vecVelocity[2] = 80;

            // play swiming sound
            //if ( this.m_flSwimSoundTime <= 0 )
            //{
            //    // Don't play sound again for 1 second
            //    player->m_flSwimSoundTime = 1000;
            //    PlaySwimSound();
            //}

            return false;
        }

        // No more effect
        if (!this.onGround)
        {
            //mv->m_nOldButtons |= IN_JUMP;
            return false;		// in air, so no effect
        }
/*

        // Don't allow jumping when the player is in a stasis field.
#ifndef HL2_EPISODIC
        if ( player->m_Local.m_bSlowMovement )
            return false;
#endif
*/

//        if ( mv->m_nOldButtons & IN_JUMP )
//            return false;		// don't pogo stick

        // Cannot jump will in the unduck transition.
        if (this.isSneaking())
            return false;

        // Still updating the eye position.
        if (Math.abs(this.getEyeY() - this.getEyeHeight(this.getPose())) > 0.1)
            return false;

        // In the air now.
        this.onGround = false;

        this.playStepSound(new BlockPos(this.getPos()), this.world.getBlockState(new BlockPos(this.getPos().subtract(0F, 1F, 0F))));
//
//        MoveHelper()->PlayerSetAnimation( PLAYER_JUMP );

        float flGroundFactor = 1.0f;
//        if (player->m_pSurfaceData)
//        {
//            flGroundFactor = player->m_pSurfaceData->game.jumpFactor;
//        }

        float flMul;
        /*if ( g_bMovementOptimizations ) {
            assert (this.getCurrentGravity() == 800.0f );
            flMul = 268.3281572999747f;
        } else {*/
            flMul = (float) Math.sqrt(2 * getCurrentGravity() * GAMEMOVEMENT_JUMP_HEIGHT);
        //}

        // Acclerate upward
        // If we are ducking...
        float startz = (float) this.getVelocity().y;
        if (this.isSneaking())
        {
            // d = 0.5 * g * t^2		- distance traveled with linear accel
            // t = sqrt(2.0 * 45 / g)	- how long to fall 45 units
            // v = g * t				- velocity at the end (just invert it to jump up that high)
            // v = g * sqrt(2.0 * 45 / g )
            // v^2 = g * g * 2.0 * 45 / g
            // v = sqrt( g * 2.0 * 45 )
            this.setVelocity(this.getVelocity().add(0F, flGroundFactor * flMul, 0F)); // 2 * gravity * height
        } else {
            this.setVelocity(this.getVelocity().add(0F, flGroundFactor * flMul, 0F)); // 2 * gravity * height
        }

        // Add a little forward velocity based on your current forward velocity - if you are not sprinting.

        this.finishGravity();

        // Some debug thing I think. Probably unimportant
        // CheckV( player->CurrentCommandNumber(), "CheckJump", mv->m_vecVelocity );

        this.m_outJumpVel = this.m_outJumpVel.add(0, this.getVelocity().y - startz, 0);
        this.m_outStepHeight += 0.15f;

        this.onJump(this.m_outJumpVel.getY());

        //mv->m_nOldButtons |= IN_JUMP;	// don't jump again until released
        return true;
    }

    @Unique
    public boolean inBlockAtPoint(Vec3d point) {
        BlockPos pos = new BlockPos(point);
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).getBoundingBox().contains(point.subtract(Vec3d.of(pos)));
    }

    @Unique
    public void checkWaterJump() {
        Vec3d	flatforward;
        Vec3d   forward;
        Vec3d	flatvelocity;
        float curspeed;

        forward = new Vec3d(Math.sin(this.getYaw()), 0, Math.cos(this.getYaw())); // Determine movement angles

        // Already water jumping.
        if (this.m_flWaterJumpTime != 0)
            return;

        // Don't hop out if we just jumped in
        if (this.getVelocity().y < convertUnitsSource2MC(-180))
            return; // only hop out if we are moving up

        // See if we are backing up
        flatvelocity = new Vec3d(this.getVelocity().x, 0, this.getVelocity().z);

        // Must be moving
        curspeed = (float) flatvelocity.length();
        flatvelocity = flatvelocity.normalize();

        // see if near an edge
        flatforward = new Vec3d(forward.x, 0, forward.z);
        flatforward = flatforward.normalize();

        // Are we backing into water from steps or something?  If so, don't pop forward
        if ( curspeed != 0.0 && flatvelocity.dotProduct(flatforward) < 0.0 ) return;

        Vec3d vecStart;
        // Start line trace at waist height (using the center of the player for this here)
        vecStart = this.getPos().add(this.getBoundingBox().getCenter());

        Vec3d vecEnd = vecStart.add(flatforward.multiply(24.0F));

        if (inBlockAtPoint(vecStart))		// solid at waist
        {
            vecStart = vecStart.multiply(1F, 0F, 1F).add(0F, this.getEyePos().y + WATERJUMP_HEIGHT,  0F);

            vecEnd = vecStart.add(flatforward.multiply(24F));

            this.m_vecWaterJumpVel = this.vec3_origin.subtract(new Vec3d(0, 1, 0).multiply(50F));

            if (!inBlockAtPoint(vecStart))		// open at eye level
            {
                // Now trace down to see if we would actually land on a standable surface.
                vecStart = vecEnd;
                vecEnd = vecEnd.subtract(0F, convertUnitsSource2MC(1024.0F), 0F);
                BlockPos pos = new BlockPos(vecStart);

                if (!world.raycastBlock(vecStart, vecEnd, pos, world.getBlockState(pos).getCollisionShape(world, pos), world.getBlockState(pos)).getType().equals(HitResult.Type.MISS))
                {
                    this.setVelocity(this.getVelocity().add(0F, convertUnitsSource2MC(256F), 0F)); // Push up
                    /*mv->m_nOldButtons |= IN_JUMP;		// Don't jump again until released
                    player->AddFlag( FL_WATERJUMP );*/
                    this.m_flWaterJumpTime = 2000L;	// Do this for 2 seconds
                }
            }
        }
    }

    @Unique
    public void waterMove() {
        int	    i;
        Vec3d	wishvel;
        float	wishspeed;
        Vec3d	wishdir;
        Vec3d	start, dest;
        Vec3d   temp;
        Box	    pm;
        float speed, newspeed, addspeed, accelspeed;
        Vec3d forward, right, up;

        forward = new Vec3d(Math.sin(this.getYaw()), 0, Math.cos(this.getYaw())); // Determine movement angles

        //
        // user intentions
        //
        wishvel = forward.multiply(this.input.movementForward).add(right.multiply(this.input.movementSideways));

        // if we have the jump key down, move us up as well
        if (this.input.jumping) {
            wishvel = wishvel.multiply(1F, 0F, 1F).add(0F, this.m_flClientMaxSpeed, 0F);
        }
        // Sinking after no other movement occurs
        else if (!this.input.hasForwardMovement() && (0F == this.input.movementSideways) && this.m_flUpMove == 0) {
            wishvel = wishvel.subtract(0F, convertUnitsSource2MC(60), 0F); // drift towards bottom
        } else {// Go straight up by upmove amount.
            // exaggerate upward movement along forward as well
            float upwardMovememnt = (float) (this.input.movementForward * forward.y * 2);
            upwardMovememnt = MathHelper.clamp( upwardMovememnt, 0.f, this.m_flClientMaxSpeed );
            wishvel.add(0F, this.m_flUpMove + upwardMovememnt, 0F);
        }

        // Copy it over and determine speed
        wishdir = wishvel.normalize();
        wishspeed = (float) wishvel.length();

        // Cap speed.
        if (wishspeed > this.m_flMaxSpeed) {
            wishvel = wishdir.multiply(this.m_flMaxSpeed);
            wishspeed = this.m_flMaxSpeed;
        }

        // Slow us down a bit.
        wishspeed *= 0.8;

        // Water friction
        temp = this.getVelocity().normalize();
        speed = (float) this.getVelocity().length();
        if (speed != 0) {
            newspeed = speed - this.deltaTime() * speed * SV_FRICTION * this.player_m_surfaceFriction;
            if (newspeed < 0.1f) {
                newspeed = 0;
            }

            this.setVelocity(this.getVelocity().multiply(newspeed/speed));
        } else {
            newspeed = 0;
        }

        // water acceleration
        if (wishspeed >= 0.1f) { // old !
            addspeed = wishspeed - newspeed;
            if (addspeed > 0) {
                wishvel = wishvel.normalize();
                accelspeed = SV_ACCELERATE * wishspeed * this.deltaTime() * this.player_m_surfaceFriction;
                if (accelspeed > addspeed) {
                    accelspeed = addspeed;
                }

                this.setVelocity(this.getVelocity().add(wishvel.multiply(accelspeed)));
                //m_outWishVel += wishvel.multiply(accelspeed);
            }
        }

        this.setVelocity(this.getVelocity().add(this.getBaseVelocity()));

        // Now move
        // assume it is a stair or a slope, so press down from stepheight above
        dest = this.getPos().add(this.getVelocity().multiply(this.deltaTime()));

        if (this.inBlockAtPoint(this.getPos())) {
            start = dest;
            if ( this.m_Local_m_bAllowAutoMovement ) {
                start = start.add(0F, this.m_Local_m_flStepSize + 1, 0F);
            }

            if (!this.inBlockAtPoint(start)) {
                Vec3d castPos = this.world.raycastBlock(start, dest, new BlockPos(start), world.getBlockState(new BlockPos(start)).getCollisionShape(world, new BlockPos(start)), world.getBlockState(new BlockPos(start))).getPos();
                if (castPos != null) {
                    float stepDist = (float) (castPos.y - this.getPos().y);
                    this.m_outStepHeight += stepDist;
                    // walked up the step, so just keep result and exit
                    this.setPos(castPos.x, castPos.y, castPos.z);
                    this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));
                    return;
                }
            }

            // Try moving straight along out normal path.
            this.tryPlayerMove();
        } else {
            if ( !this.onGround ) {
                this.tryPlayerMove();
                this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));
                return;
            }

            this.stepMove( dest, pm );
        }

        this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));
    }

    @Unique
    public void categorizePosition() {
        Vec3d   point;
        Box     pm;

        // Reset this each time we-recategorize, otherwise we have bogus friction when we jump into water and plunge downward really quickly
        this.player_m_surfaceFriction = 1.0f;

        // if the player hull point one unit down is solid, the player
        // is on ground

        // see if standing on something solid

        // Doing this before we move may introduce a potential latency in water detection, but
        // doing it after can get us stuck on the bottom in water if the amount we move up
        // is less than the 1 pixel 'threshold' we're about to snap to.	Also, we'll call
        // this several times per frame, so we really need to avoid sticking to the bottom of
        // water on each call, and the converse case will correct itself if called twice.
        this.checkWater();

        // observers don't have a ground entity
        if (this.isSpectator()) return;

        float flOffset = 2.0f;

        point = this.getPos().subtract(0F, flOffset, 0F);
        Vec3d bumpOrigin;
        bumpOrigin = this.getPos();

        // Shooting up really fast.  Definitely not on ground.
        // On ladder moving up, so not on ground either
        // NOTE: 145 is a jump.
        float NON_JUMP_VELOCITY = 140.0f;

        float zvel = (float) this.getVelocity().y;
        boolean bMovingUp = zvel > 0.0f;
        boolean bMovingUpRapidly = zvel > NON_JUMP_VELOCITY;
        float flGroundEntityVelZ = 0.0f;
        if ( bMovingUpRapidly ) {
            // Tracker 73219, 75878:  ywb 8/2/07
            // After save/restore (and maybe at other times), we can get a case where we were saved on a lift and
            //  after restore we'll have a high local velocity due to the lift making our abs velocity appear high.
            // We need to account for standing on a moving ground object in that case in order to determine if we really
            //  are moving away from the object we are standing on at too rapid a speed.  Note that CheckJump already sets
            //  ground entity to NULL, so this wouldn't have any effect unless we are moving up rapidly not from the jump button.
            if (this.onGround) {
                flGroundEntityVelZ = this.ground_getAbsVelocity().y;
                bMovingUpRapidly = ( zvel - flGroundEntityVelZ ) > NON_JUMP_VELOCITY;
            }
        }

        // Was on ground, but now suddenly am not
        if ( bMovingUpRapidly || ( bMovingUp && this.isClimbing() ) ) {
            this.setGroundEntity(null);
        } else {
            // Try and move down.
            Box box = this.getBoundingBox();
            this.tryTouchGround( bumpOrigin, point, new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ), pm);

            // Was on ground, but now suddenly am not.  If we hit a steep plane, we are not on ground
            /* THIS CANNOT HAPPEN IN MINECRAFT
            if ( !pm.m_pEnt || false ) {
                // Test four sub-boxes, to see if any of them would have found shallower slope we could actually stand on
                TryTouchGroundInQuadrants( bumpOrigin, point, MASK_PLAYERSOLID, COLLISION_GROUP_PLAYER_MOVEMENT, pm );

                if ( !pm.m_pEnt || pm.plane.normal[2] < 0.7 )
                {
                    SetGroundEntity( NULL );
                    // probably want to add a check for a +z velocity too!
                    if ( ( mv->m_vecVelocity.z > 0.0f ) &&
                            ( player->GetMoveType() != MOVETYPE_NOCLIP ) )
                    {
                        player->m_surfaceFriction = 0.25f;
                    }
                }
                else
                {
                    SetGroundEntity( &pm );
                }
            }
            else
            {*/
                this.onGround = true;  // Otherwise, point to index of ent under us.
            //}

            //Adrian: vehicle code handles for us.
            if (!this.hasVehicle()) {
                // If our gamematerial has changed, tell any player surface triggers that are watching
                //IPhysicsSurfaceProps *physprops = MoveHelper()->GetSurfaceProps();
                //surfacedata_t *pSurfaceProp = physprops->GetSurfaceData( pm.surface.surfaceProps );
                /* uhh I don't think there's a minecraft analog for this. Maybe soul sand.
                char cCurrGameMaterial = pSurfaceProp->game.material;
                if ( !player->GetGroundEntity() )
                {
                    cCurrGameMaterial = 0;
                }

                // Changed?
                if ( player->m_chPreviousTextureType != cCurrGameMaterial )
                {
                    CEnvPlayerSurfaceTrigger::SetPlayerSurface( player, cCurrGameMaterial );
                }

                player->m_chPreviousTextureType = cCurrGameMaterial;*/
            }
        }
    }

    @Unique
    public void friction() {
        float	speed, newspeed, control;
        float	friction;
        float	drop;

        // If we are in water jump cycle, don't apply friction
        if (this.m_flWaterJumpTime != 0)
            return;

        // Calculate speed
        speed = (float) this.getVelocity().length();

        // If too slow, return
        if (speed < 0.1f) {
            return;
        }

        drop = 0;

        // apply ground friction
        if (this.onGround) { // On an entity that is the ground
            friction = SV_FRICTION * this.player_m_surfaceFriction;

            // Bleed off some speed, but if we have less than the bleed
            //  threshold, bleed the threshold amount.

            // I don't suppose we're adding Xbox support.
            /*if ( IsX360() )
            {
                if( player->m_Local.m_bDucked )
                {
                    control = (speed < sv_stopspeed.GetFloat()) ? sv_stopspeed.GetFloat() : speed;
                }
                else
                {
#if defined ( TF_DLL ) || defined ( TF_CLIENT_DLL )
                    control = (speed < sv_stopspeed.GetFloat()) ? sv_stopspeed.GetFloat() : speed;
#else
                    control = (speed < sv_stopspeed.GetFloat()) ? (sv_stopspeed.GetFloat() * 2.0f) : speed;
#endif
                }
            }
            else
            {*/
            control = (speed < SV_STOPSPEED) ? SV_STOPSPEED : speed;
            //}

            // Add the amount to the drop amount.
            drop += control * friction * this.deltaTime();
        }

        // scale the velocity
        newspeed = speed - drop;
        if (newspeed < 0) newspeed = 0;

        if ( newspeed != speed ) {
            // Determine proportion of old speed we are using.
            newspeed /= speed;
            // Adjust velocity according to proportion.
            this.setVelocity(this.getVelocity().multiply(newspeed));
        }

        this.m_outWishVel = this.m_outWishVel.subtract(this.getVelocity().multiply((1.f-newspeed)));
    }

    @Unique
    public void checkVelocity() {
        int i;

        //
        // bound velocity
        //

        Vec3d org = this.getPos();



        /*
        No thanks.
        for (i=0; i < 3; i++) {
            // See if it's bogus.
            if (IS_NAN(mv->m_vecVelocity[i])) {
                LOGGER.error( "PM  Got a NaN velocity %s\n");
                mv->m_vecVelocity[i] = 0;
            }

            if (IS_NAN(org[i])) {
                LOGGER.error( "PM  Got a NaN origin %s\n");
                org[ i ] = 0;
                mv->SetAbsOrigin( org );
            }

            // Bound it.
            if (mv->m_vecVelocity[i] > sv_maxvelocity.GetFloat()) {
                LOGGER.error( "PM  Got a velocity too high on %s\n");
                mv->m_vecVelocity[i] = sv_maxvelocity.GetFloat();
            } else if (mv->m_vecVelocity[i] < -sv_maxvelocity.GetFloat()) {
                LOGGER.error( "PM  Got a velocity too low on %s\n");
                mv->m_vecVelocity[i] = -sv_maxvelocity.GetFloat();
            }
        }*/

        // Alternate, less reliable solution.
        if (this.getVelocity().length() > SV_MAXSPEED) {
            this.setVelocity(Vec3d.ZERO);
        }
    }

    @Unique
    public void walkMove() {
        int i;

        Vec3d wishvel;
        float spd;
        float fmove, smove;
        Vec3d wishdir;
        float wishspeed;

        Vec3d dest;
        Box pm;
        Vec3d forward, right, up;

        // Copy movement amounts
        fmove = this.input.movementForward;
        smove = this.input.movementSideways;

        // Convert basis from forward-facing to absolute
        float sine = MathHelper.sin(this.getYaw() * 0.017453292F);
        float cosine = MathHelper.cos(this.getYaw() * 0.017453292F);

        wishvel = new Vec3d(smove * cosine - fmove * sine, 0, fmove * cosine + smove * sine); // Determine movement angles

        boolean oldground;
        oldground = this.onGround;


        // Zero out z components of movement vectors
        /*if ( g_bMovementOptimizations ) {
            if ( forward[2] != 0 )
            {
                forward[2] = 0;
                VectorNormalize( forward );
            }

            if ( right[2] != 0 )
            {
                right[2] = 0;
                VectorNormalize( right );
            }
        }
        else
        {
            forward[2] = 0;
            right[2]   = 0;

            VectorNormalize (forward);  // Normalize remainder of vectors.
            VectorNormalize (right);    //
        }*/

        /*for (i=0 ; i<2 ; i++)       // Determine x and y parts of velocity
            wishvel[i] = forward[i]*fmove + right[i]*smove;*/

       // wishvel[2] = 0;             // Zero out z part of velocity

        wishdir = wishvel.normalize(); // Determine maginitude of speed of move
        wishspeed = (float) wishvel.length();

        //
        // Clamp to server defined max speed
        //
        if ((wishspeed != 0.0f) && (wishspeed > this.m_flMaxSpeed))
        {
            wishvel = wishdir.multiply(this.m_flMaxSpeed);
            wishspeed = this.m_flMaxSpeed;
        }

        // Set pmove velocity
        this.setVelocity(this.getVelocity().multiply(1F, 0F, 1F));
        this.accelerate(wishdir, wishspeed, SV_ACCELERATE);
        this.setVelocity(this.getVelocity().multiply(1F, 0F, 1F));

        // Add in any base velocity to the current velocity.
        this.setVelocity(this.getVelocity().add(this.getBaseVelocity()));

        spd = (float) this.getVelocity().length();

        if ( spd < convertUnitsSource2MC(1.0F) ) {
            // Now pull the base velocity back out.   Base velocity is set if you are on a moving object, like a conveyor (or maybe another monster?)
            this.setVelocity(Vec3d.ZERO.subtract(this.getBaseVelocity()));
            return;
        }

        // first try just moving to the destination
        dest = this.getPos().add(this.getVelocity().x * this.deltaTime(), 0F, this.getVelocity().z * this.deltaTime());

        // first try moving directly to the next spot
        //TracePlayerBBox( mv->GetAbsOrigin(), dest, PlayerSolidMask(), COLLISION_GROUP_PLAYER_MOVEMENT, pm );

        // If we made it all the way, then copy trace end as new player position.
        this.m_outWishVel = this.m_outWishVel.add(wishdir.multiply(wishspeed));

        if (this.inBlockAtPoint(this.getPos())) {
            Vec3d castPos = this.world.raycastBlock(this.getPos(), dest, this.getBlockPos(), world.getBlockState(this.getBlockPos()).getCollisionShape(world, this.getBlockPos()), world.getBlockState(this.getBlockPos())).getPos();
            if (castPos != null) {
                this.setPosition(castPos);
                // Now pull the base velocity back out.   Base velocity is set if you are on a moving object, like a conveyor (or maybe another monster?)
                this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));

                this.stayOnGround();
                return;
            }
        }

        // Don't walk up stairs if not on ground.
        if ( !oldground && !this.touchingWater ) {
            // Now pull the base velocity back out.   Base velocity is set if you are on a moving object, like a conveyor (or maybe another monster?)
            this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));
            return;
        }

        // If we are jumping out of water, don't do anything more.
        if ( this.m_flWaterJumpTime != 0) {
            // Now pull the base velocity back out.   Base velocity is set if you are on a moving object, like a conveyor (or maybe another monster?)
            this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));
            return;
        }

        this.stepMove( dest, pm );

        // Now pull the base velocity back out.   Base velocity is set if you are on a moving object, like a conveyor (or maybe another monster?)
        this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));

        this.stayOnGround();
    }

    @Unique
    public float getAirSpeedCap() {
        return 100000; // TODO determine
    }

    @Unique
    public void airAccelerate(Vec3d wishdir, float wishspeed, float accel) {
        int i;
        float addspeed, accelspeed, currentspeed;
        float wishspd;

        wishspd = wishspeed;

        if (this.isRemoved())
            return;

        if (this.m_flWaterJumpTime != 0)
            return;

        // Cap speed
        if ( wishspd > this.getAirSpeedCap() )
            wishspd = this.getAirSpeedCap();

        // Determine veer amount
        currentspeed = (float) this.getVelocity().dotProduct(wishdir);

        // See how much to add
        addspeed = wishspd - currentspeed;

        // If not adding any, done.
        if (addspeed <= 0)
            return;

        // Determine acceleration speed after acceleration
        accelspeed = accel * wishspeed * this.deltaTime() * this.player_m_surfaceFriction;

        // Cap it
        if (accelspeed > addspeed)
            accelspeed = addspeed;

        // Adjust pmove vel.
        this.setVelocity(this.getVelocity().add(wishdir.multiply(accelspeed)));
        this.m_outWishVel = this.m_outWishVel.add(wishdir.multiply(accelspeed));
    }

    @Unique
    public void airMove() {
        int			i;
        Vec3d		wishvel;
        float		fmove, smove;
        Vec3d		wishdir;
        float		wishspeed;
        Vec3d forward, right, up;

        // Copy movement amounts
        fmove = this.input.movementForward;
        smove = this.input.movementSideways;

        // Convert basis from forward-facing to absolute
        float sine = MathHelper.sin(this.getYaw() * 0.017453292F);
        float cosine = MathHelper.cos(this.getYaw() * 0.017453292F);

        wishvel = new Vec3d(smove * cosine - fmove * sine, 0, fmove * cosine + smove * sine); // Determine movement angles

        wishdir = wishvel.normalize(); // Determine maginitude of speed of move
        wishspeed = (float) wishvel.length();

        //
        // clamp to server defined max speed
        //
        if ( wishspeed != 0 && (wishspeed > this.m_flMaxSpeed)) {
            wishvel = wishdir.multiply(this.m_flMaxSpeed);
            wishspeed = this.m_flMaxSpeed;
        }

        this.airAccelerate( wishdir, wishspeed, SV_AIRACCELERATE );

        // Add in any base velocity to the current velocity.
        this.setVelocity(this.getVelocity().add(this.getBaseVelocity()));

        this.tryPlayerMove();

        // Now pull the base velocity back out.   Base velocity is set if you are on a moving object, like a conveyor (or maybe another monster?)
        this.setVelocity(this.getVelocity().subtract(this.getBaseVelocity()));
    }

    @Unique
    public void finishGravity() {
        float ent_gravity;

        if ( this.m_flWaterJumpTime != 0 ) return;

        if ( this.getGravity() != 0)
            ent_gravity = this.getGravity();
        else
            ent_gravity = 1.0F;

        // Get the correct velocity for the end of the dt
        this.setVelocity(this.getVelocity().subtract(0F, ent_gravity * this.getCurrentGravity() * this.deltaTime() * 0.5, 0F));

        this.checkVelocity();
    }

    @Unique
    public void checkFalling() {
        // this function really deals with landing, not falling, so early out otherwise
        if ( !this.isOnGround() || this.m_Local_m_flFallVelocity <= 0 )
            return;

        if ( !this.isRemoved() && this.m_Local_m_flFallVelocity >= PLAYER_FALL_PUNCH_THRESHOLD ) {
            boolean bAlive = true;
            float fvol = 0.5f;

            if ( this.touchingWater ) {
                // They landed in water.
            } else {
                // Scale it down if we landed on something that's floating...
                /* NO MINECRAFT ANALOG
                if ( player->GetGroundEntity()->IsFloating() )
                {
                    player->m_Local.m_flFallVelocity -= PLAYER_LAND_ON_FLOATING_OBJECT;
                }
                 */
                //
                // They hit the ground.
                //
                /* NO MINECRAFT ANALOG
                if( player->GetGroundEntity()->GetAbsVelocity().z < 0.0f ) {
                    // Player landed on a descending object. Subtract the velocity of the ground entity.
                    player->m_Local.m_flFallVelocity += player->GetGroundEntity()->GetAbsVelocity().z;
                    player->m_Local.m_flFallVelocity = MAX( 0.1f, player->m_Local.m_flFallVelocity );
                }*/
                /* TODO Fall damage
                if ( this.m_Local_m_flFallVelocity > PLAYER_MAX_SAFE_FALL_SPEED ) {
                    //
                    // If they hit the ground going this fast they may take damage (and die).
                    //
                    bAlive = MoveHelper( )->PlayerFallingDamage();
                    fvol = 1.0;
                }
                else if ( player->m_Local.m_flFallVelocity > PLAYER_MAX_SAFE_FALL_SPEED / 2 )
                {
                    fvol = 0.85;
                }
                else if ( player->m_Local.m_flFallVelocity < PLAYER_MIN_BOUNCE_SPEED )
                {
                    fvol = 0;
                }*/
            }

            this.playerRoughLandingEffects( fvol );

//            if (bAlive) {
//                MoveHelper( )->PlayerSetAnimation( PLAYER_WALK );
//            }
        }

        // let any subclasses know that the player has landed and how hard
        this.onLanding();

        //
        // Clear the fall velocity so the impact doesn't happen again.
        //
        // this.m_Local_m_flFallVelocity = 0;
    }

    @Unique
    public void fullWalkMove() {
        if (!this.checkWater()) {
            this.startGravity();
        }

        // If we are leaping out of the water, just update the counters.
        if (this.m_flWaterJumpTime > 0) {
            this.waterJump();
            this.tryPlayerMove();
            // See if we are still in water?
            this.checkWater();
            return;
        }

        // If we are swimming in the water, see if we are nudging against a place we can jump up out
        //  of, and, if so, start out jump.  Otherwise, if we are not moving up, then reset jump timer to 0
        if (this.waistDeepInWater) {
            if (!this.submergedInWater) {
                this.checkWaterJump();
            }

            // If we are falling again, then we must not trying to jump out of water any more.
            if (this.getVelocity().getY() < 0 && this.m_flWaterJumpTime != 0 ) {
                this.m_flWaterJumpTime = 0;
            }

            // Was jump button pressed?
            if (this.input.jumping) {
                this.checkJumpButton();
            } /*else {
                mv->m_nOldButtons &= ~IN_JUMP;
            }*/

            // Perform regular water movement
            this.waterMove();

            // Redetermine position vars
            this.categorizePosition();

            // If we are on ground, no downward velocity.
            if (this.onGround) {
                this.setVelocity(this.getVelocity().multiply(1F, 0F, 1F));
            }
        } else { // Not fully underwater
            // Was jump button pressed?
            if (this.input.jumping) {
                this.checkJumpButton();
            }/* else {
                mv->m_nOldButtons &= ~IN_JUMP;
            }*/

            // Fricion is handled before we add in any base velocity. That way, if we are on a conveyor,
            //  we don't slow when standing still, relative to the conveyor.
            if (this.onGround)
            {
                this.setVelocity(this.getVelocity().multiply(1F, 0F, 1F));
                this.friction();
            }

            // Make sure velocity is valid.
            this.checkVelocity();

            if (this.onGround) {
                this.walkMove();
            } else {
                this.airMove();  // Take into account movement when in air.
            }

            // Set final flags.
            this.categorizePosition();

            // Make sure velocity is valid.
            this.checkVelocity();

            // Add any remaining gravitational component.
            if (!this.checkWater()) {
                this.finishGravity();
            }

            // If we are on ground, no downward velocity.
            if (this.onGround) {
                this.setVelocity(this.getVelocity().multiply(1F, 0F, 1F));
            }
            this.checkFalling();
        }
        /*
        if  ( ( m_nOldWaterLevel == WL_NotInWater && this.getWaterLevel() != WL_NotInWater ) ||
                ( m_nOldWaterLevel != WL_NotInWater && this.getWaterLevel() == WL_NotInWater ) ) {
            this.sourcePlaySwimSound();
        }*/
    }

    @Unique
    private static float convertUnitsSource2MC(float input) {
        return input * 0.01905F * 0.333333333F;
    }

    @Unique
    private boolean waistDeepInWater;

    @Unique
    private long m_flWaterEntryTime;

    @Unique
    public boolean checkWater() {
        Vec3d	point;

        boolean wasTouchingWater = this.touchingWater;

        Box box = this.getBoundingBox();
        Vec3d vPlayerMins = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d vPlayerMaxs = new Vec3d(box.maxX, box.maxY, box.maxZ);

        // Pick a spot just above the players feet.
        point = this.getPos().add(box.getCenter());
        point = new Vec3d(point.x, this.getPos().y + box.minY + convertUnitsSource2MC(1.0F), point.z);

        this.touchingWater = !world.getFluidState(new BlockPos(point)).getFluid().matchesType(Fluids.EMPTY);
        // Are we under water? (not solid and not empty?)
        if (this.touchingWater) {
            // Now check a point that is at the player hull midpoint.
            point = this.getPos().add(box.getCenter());
            this.waistDeepInWater = !world.getFluidState(new BlockPos(point)).getFluid().matchesType(Fluids.EMPTY);
            // If that point is also under water...
            if (this.waistDeepInWater) {
                // Now check the eye position.  (view_ofs is relative to the origin)
                point = this.getEyePos();
                this.submergedInWater = !world.getFluidState(new BlockPos(point)).getFluid().matchesType(Fluids.EMPTY);
            }

            // Adjust velocity based on water current, if any.
            FluidState fluidState = world.getFluidState(new BlockPos(point));
            if (!fluidState.isStill()) {
                Vec3d v = new Vec3d(0F, 0F, 0F);

                float fluidHeight = 0F;
                if (this.touchingWater) fluidHeight += 0.2F;
                if (this.waistDeepInWater) fluidHeight += 0.4F;
                if (this.submergedInWater) fluidHeight += 0.4F;

                this.setVelocity(this.getVelocity().add(fluidState.getVelocity(world, new BlockPos(point)).multiply(50F * fluidHeight)));
            }
        }

        // if we just transitioned from not in water to in water, record the time it happened
        if (!wasTouchingWater && this.touchingWater) {
            if (this.client.world != null) {
                m_flWaterEntryTime = System.currentTimeMillis();
            }
        }

        return (this.waistDeepInWater);
    }
}
