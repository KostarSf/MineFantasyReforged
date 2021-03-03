package minefantasy.mfr.entity;

import minefantasy.mfr.api.armour.ArmourDesign;
import minefantasy.mfr.api.armour.IPowerArmour;
import minefantasy.mfr.config.ConfigArmour;
import minefantasy.mfr.constants.Tool;
import minefantasy.mfr.init.MineFantasyBlocks;
import minefantasy.mfr.init.MineFantasyItems;
import minefantasy.mfr.init.MineFantasySounds;
import minefantasy.mfr.material.CustomMaterial;
import minefantasy.mfr.network.CogworkControlPacket;
import minefantasy.mfr.network.NetworkHandler;
import minefantasy.mfr.proxy.ClientProxy;
import minefantasy.mfr.util.ArmourCalculator;
import minefantasy.mfr.util.CustomToolHelper;
import minefantasy.mfr.util.PowerArmour;
import minefantasy.mfr.util.TacticalManager;
import minefantasy.mfr.util.ToolHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class EntityCogwork extends EntityLivingBase implements IPowerArmour {
	private static final DataParameter<String> CUSTOM_MATERIAL = EntityDataManager.<String>createKey(EntityCogwork.class, DataSerializers.STRING);
	private static final DataParameter<Float> FUEL = EntityDataManager.<Float>createKey(EntityCogwork.class, DataSerializers.FLOAT);
	private static final DataParameter<Integer> BOLTS = EntityDataManager.<Integer>createKey(EntityCogwork.class, DataSerializers.VARINT);
	public static final float base_armour_units = 30F;
	private static final float general_step_height = 1.0F;
	private static final float base_frame_weight = 100F;
	public static float base_fuel_minutes = 20F;
	public static int maxBolts = 16;
	public static int allowedBulk = 1;
	public static float rating_modifier = 1.0F;
	public static float health_modifier = 1.0F;
	private int noMoveTime = 0;
	private float forwardControl, strafeControl;
	private boolean jumpControl;
	private int jumpTimer = 0;
	private boolean alternateStep;

	public EntityCogwork(World world) {
		super(world);
		this.stepHeight = general_step_height;
		this.preventEntitySpawning = true;
		this.setSize(1.5F, 2.5F);
	}

	public EntityCogwork(World world, double posX, double posY, double posZ) {
		this(world);
		this.setPosition(posX, posY, posZ);
	}

	@SideOnly(Side.CLIENT)
	public static int getArmourRating(CustomMaterial base) {
		if (base != null) {
			float ratio = base.hardness * ArmourDesign.COGWORK.getRating() * rating_modifier;
			return (int) (ratio * ArmourCalculator.armourRatingScale);
		}
		return 0;
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataManager.register(CUSTOM_MATERIAL, "");
		this.dataManager.register(FUEL, Float.valueOf(0F));
		this.dataManager.register(BOLTS, Integer.valueOf(0));
	}

	/**
	 * For vehicles, the first passenger is generally considered the controller and "drives" the vehicle. For example,
	 * Pigs, Horses, and Boats are generally "steered" by the controlling passenger.
	 */
	@Override
	@Nullable
	public Entity getControllingPassenger() {
		return this.getPassengers().isEmpty() ? null : (Entity) this.getPassengers().get(0);
	}

	public int getBolts() {
		return this.dataManager.get(BOLTS);
	}

	public void setBolts(int value) {
		this.dataManager.set(BOLTS, value);
	}

	public float getFuel() {
		return this.dataManager.get(FUEL);
	}

	public void setFuel(float level) {
		this.dataManager.set(FUEL, level);
	}

	public String getCustomMaterial() {
		return this.dataManager.get(CUSTOM_MATERIAL);
	}

	public void setCustomMaterial(String name) {
		if (name.length() == 0) {
			setHealth(getMaxHealth());
		}
		this.dataManager.set(CUSTOM_MATERIAL, name);
	}

	@Override
	public ItemStack getHeldItemMainhand() {
		return ItemStack.EMPTY;
	}

	public Iterable<ItemStack> getArmorInventoryList() {
		return NonNullList.<ItemStack>withSize(4, ItemStack.EMPTY);
	}

	@Override
	public ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemStackToSlot(EntityEquipmentSlot slotIn, ItemStack stack) {

	}

	@Override
	public ItemStack getActiveItemStack() {
		return activeItemStack;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		if (noMoveTime > 0) {
			moveForward = moveStrafing = 0;
			--noMoveTime;
		}

		float fuel = this.getFuel();
		float maxfuel = this.getMaxFuel();
		if (isPowered()) {
			fuel -= getFuelDecay();
		}
		fuel = MathHelper.clamp(fuel, 0F, maxfuel);
		setFuel(fuel);

		/*
		 * if(isSprinting()) { if(riddenByEntity == null || this.getMoveForward() <= 0
		 * || !isPowered()) { setSprinting(false); } }
		 */
		if (this.getControllingPassenger() != null) {
			stepHeight = general_step_height;
			updateRider();
			if (getControllingPassenger().isBurning() && this.isFullyArmoured()) {
				getControllingPassenger().extinguish();
			}

			// ARROWS
			if (getControllingPassenger() instanceof EntityLivingBase) {
				int arrows = ((EntityLivingBase) getControllingPassenger()).getArrowCountInEntity();
				((EntityLivingBase) getControllingPassenger()).setArrowCountInEntity(0);
				int my_arrows = this.getArrowCountInEntity();
				this.setArrowCountInEntity(my_arrows + arrows);

				if (this.isFullyArmoured()) {
					getControllingPassenger().setAir(300);
				}

				if (ticksExisted % 20 == 0) {
					for (int a = 0; a < 4; a++) {
						if (!allowEquipment((EntityLivingBase) getControllingPassenger())) {
							getControllingPassenger().dismountRidingEntity();
							break;
						}
					}
				}
			}

			// DAMAGE
			if (this.motionX * this.motionX + this.motionZ * this.motionZ > 2.500000277905201E-7D && this.rand.nextInt(5) == 0) {
				int i = MathHelper.floor(this.posX);
				int j = MathHelper.floor(this.posY - 0.20000000298023224D - this.getMountedYOffset());
				int k = MathHelper.floor(this.posZ);
				BlockPos pos = new BlockPos(i, j, k);
				IBlockState state = this.world.getBlockState(pos);

				if (state.getMaterial() == Material.GROUND) {
					//this.world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, this.posX + (this.rand.nextFloat() - 0.5D) * this.width, this.getEntityBoundingBox().minY + 0.1D, this.posZ + (this.rand.nextFloat() - 0.5D) * this.width, 4.0D * (this.rand.nextFloat() - 0.5D), 0.5D, (this.rand.nextFloat() - 0.5D) * 4.0D);
				}
				if (!world.isRemote && ConfigArmour.cogworkGrief) {
					damageBlock(state.getBlock(), pos, world.getBlockState(pos));
					state = this.world.getBlockState(pos.add(0, 1, 0));
					damageSurface(state.getBlock(), pos.add(0, 1, 0), world.getBlockState(pos));
				}
			}
		} else {
			motionX = motionZ = 0;
			this.setMoveForward(0F);
			this.setMoveStrafe(0F);
			stepHeight = 0;
			this.limbSwing = this.limbSwingAmount = this.prevLimbSwingAmount = 0;
			this.rotationPitch = 20F;
			this.rotationYawHead = this.rotationYaw;
			this.prevRotationYawHead = this.prevRotationYaw;
			this.swingProgress = this.swingProgressInt = 0;
		}
		if (jumpTimer > 0) {
			--jumpTimer;
		}
		onPortalTick();
	}

	/**
	 * Modifier for actions and fuel decay (weight of the suit)
	 */
	private float getFuelCost() {
		float mass = this.getWeight();
		return mass / 200F;
	}

	/**
	 * Rate of constant fuel droppage
	 *
	 * @return fuelDecay
	 */
	private float getFuelDecay() {
		return getFuelCost() * (isSprinting() ? 3.0F : 1.0F);
	}

	private void damageBlock(Block block, BlockPos pos, IBlockState state) {
		if (block == Blocks.GRASS || block == Blocks.FARMLAND) {
			world.setBlockState(pos, (Blocks.DIRT).getDefaultState());
		}
		if (state.getMaterial() == Material.GLASS) {
			world.setBlockToAir(pos);
			this.world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.9F + (rand.nextFloat() * 0.2F), true);
		}
		if (block == Blocks.ICE) {
			world.setBlockState(pos, (Blocks.WATER).getDefaultState());
			this.world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.9F + (rand.nextFloat() * 0.2F), true);
		}
		if (state.getMaterial() == Material.LEAVES) {
			world.setBlockState(pos, (Blocks.WATER).getDefaultState());
			this.world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.9F + (rand.nextFloat() * 0.2F), true);
		}
	}

	private void damageSurface(Block block, BlockPos pos, IBlockState state) {
		if (block.getBlockHardness(state, world, pos) == 0 && (state.getMaterial() == Material.VINE || state.getMaterial() == Material.PLANTS)) {
			world.setBlockToAir(pos);
			this.world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.9F + (rand.nextFloat() * 0.2F), true);
		}
		if (block == Blocks.SNOW_LAYER) {
			world.setBlockToAir(pos);
			this.world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.BLOCKS, 1.0F, 0.9F + (rand.nextFloat() * 0.2F), true);
		}
	}

	@Override
	public void knockBack(Entity hitter, float f, double d, double d1) {
	}

	public CustomMaterial getPlating() {
		return CustomMaterial.getMaterial(getCustomMaterial());
	}

	public void updateRider() {
		if (!isPowered())
			return;

		if (world.isRemote && getControllingPassenger() != null && getControllingPassenger() instanceof EntityPlayer) {
			float forward = ((EntityPlayer) getControllingPassenger()).moveForward;
			float strafe = ((EntityPlayer) getControllingPassenger()).moveStrafing;

			if (getControllingPassenger() instanceof EntityPlayerMP) {
				boolean jump = ClientProxy.isUserJumpCommand(getControllingPassenger());
				if (jump != jumpControl || forward != forwardControl || strafe != strafeControl) {
					this.forwardControl = forward;
					this.strafeControl = strafe;
					this.jumpControl = jump;
					NetworkHandler.sendToPlayer((EntityPlayerMP) getControllingPassenger(), new CogworkControlPacket(this, getControllingPassenger()));
				}
			}
		}
		if (ticksExisted % 100 == 0) {
			if (rand.nextInt(20) == 0) {
				this.playSound(MineFantasySounds.COGWORK_TOOT, 0.5F, 1.0F);
			}
			this.playSound(MineFantasySounds.COGWORK_IDLE, 0.5F, 0.75F + rand.nextFloat() * 0.5F);
		}

		if (!world.isRemote) {
			if (this.jumpControl && jumpTimer == 0) {
				this.jumpTimer = 10;
			}
			if (!this.isInWater() && !this.isInLava()) {
				if (this.onGround && jumpTimer == 8) {
					this.jump();
				}
			}
		}
	}

	protected void jump() {
		spendFuel(5F);
		world.playSound(posX, posY, posZ, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.AMBIENT, 2.0F, 1.0F, true);
		this.motionY = 0.41999998688697815D;
		if (this.isSprinting()) {
			float f = this.rotationYaw * 0.017453292F;
			this.motionX -= MathHelper.sin(f) * 0.2F;
			this.motionZ += MathHelper.cos(f) * 0.2F;
		}
		this.isAirBorne = true;
		ForgeHooks.onLivingJump(this);
	}

	private void spendFuel(float cost) {
		this.setFuel(Math.max(0F, getFuel() - cost * getFuelCost()));
	}

	@Override
	public boolean isSprinting() {
		return isPowered() && forwardControl > 0 && getControllingPassenger() != null && getControllingPassenger().isSprinting();
	}

	public float getMoveForward() {
		return forwardControl;
	}

	public void setMoveForward(float f) {
		forwardControl = f;
	}

	public float getMoveStrafe() {
		return strafeControl;
	}

	public void setMoveStrafe(float f) {
		strafeControl = f;
	}

	public boolean getJumpControl() {
		return jumpControl;
	}

	public void setJumpControl(boolean b) {
		this.jumpControl = b;
	}

	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25F);
	}

	@Override
	public boolean shouldRiderFaceForward(EntityPlayer player) {
		return true;
	}

	@Override
	public EnumHandSide getPrimaryHand() {
		return EnumHandSide.RIGHT;
	}

	@Override
	public boolean shouldRiderSit() {
		return false;
	}

	@Override
	public double getMountedYOffset() {
		return 0.8625D;
	}

	@Override
	public boolean canBeCollidedWith() {
		return getControllingPassenger() == null && super.canBeCollidedWith();
	}

	@Override
	public boolean canBePushed() {
		return false;
	}

	@Override
	public boolean processInitialInteract(EntityPlayer user, EnumHand hand) {
		if (user.isSwingInProgress) {
			return false;
		}

		ItemStack item = user.getHeldItemMainhand();
		if (!item.isEmpty()) {
			float fuel_item = PowerArmour.getFuelValue(item);
			if (fuel_item > 0) {
				float fuel = getFuel();
				float max = getMaxFuel();
				if (fuel < max) {
					fuel += Math.max(0F, fuel_item * ConfigArmour.cogworkFuelUnits);
					if (fuel > max) {
						fuel = max;
					}
					setFuel(fuel);

					if (!user.capabilities.isCreativeMode) {
						item.shrink(1);

						ItemStack container = item.getItem().getContainerItem(item);
						if (!container.isEmpty()) {
							if (item.getCount() >= 1) {
								if (!user.inventory.addItemStackToInventory(container)) {
									user.entityDropItem(container, 0F);
								}
							}
						}
						if (item.getCount() <= 0) {
							user.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, container);
						}
					}
				}
				return true;
			}
			if (this.getControllingPassenger() != null) {
				return false;
			}
			if (this.isUnderRepairFrame()) {
				if (getPlating() == null && item.getItem() == MineFantasyItems.COGWORK_ARMOUR) {
					CustomMaterial material = CustomToolHelper.getCustomPrimaryMaterial(item);
					if (material != null) {
						this.playSound(SoundEvents.ENTITY_HORSE_ARMOR, 1.0F, 1.0F);
						int boltCount = this.getBolts();
						if (boltCount < maxBolts) {
							if (!user.isSwingInProgress && user.capabilities.isCreativeMode || user.inventory.hasItemStack(new ItemStack(MineFantasyItems.BOLT))) {
								int slot = user.inventory.getSlotFor(new ItemStack(MineFantasyItems.BOLT));
								user.inventory.decrStackSize(slot, 1);
								++boltCount;
								setBolts(boltCount);
							}
							user.swingArm(hand);
							return true;
						}
						this.setCustomMaterial(material.name);
						float damagePercent = 1F - ((float) item.getItemDamage() / (float) item.getMaxDamage());
						this.setHealth(getMaxHealth() * damagePercent);
						if (!user.capabilities.isCreativeMode) {
							item.shrink(1);
							if (item.getCount() <= 0) {
								user.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
							}
						}
						user.swingArm(hand);
						return true;
					}
				}
				if (this.getPlating() != null && ToolHelper.getToolTypeFromStack(item) == Tool.SPANNER) {
					this.playSound(SoundEvents.ENTITY_HORSE_ARMOR, 1.2F, 1.0F);
					user.swingArm(hand);
					int boltCount = this.getBolts();
					if (boltCount > 0) {
						if (!world.isRemote) {
							ItemStack bolt = new ItemStack(MineFantasyItems.BOLT, boltCount);
							if (!user.capabilities.isCreativeMode && !user.inventory.addItemStackToInventory(bolt)) {
								this.entityDropItem(bolt, 0.0F);
							}
						}
						setBolts(0);
					}
					float damagePercent = 1F - (getHealth() / getMaxHealth());
					if (!world.isRemote) {
						ItemStack armour = MineFantasyItems.COGWORK_ARMOUR.createComm(getPlating().name, 1, damagePercent);
						if (!user.capabilities.isCreativeMode && !user.inventory.addItemStackToInventory(armour)) {
							this.entityDropItem(armour, 0.0F);
						}
					}
					this.playSound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 1.0F, 1.0F);
					this.setCustomMaterial("");
					return true;
				}
			}
		}
		if (user.getControllingPassenger() == null) {
			if (this.allowEquipment(user)) {
				this.noMoveTime = 20;
				user.moveForward = user.moveStrafing = 0F;
				user.startRiding(this);
				this.playSound(SoundEvents.BLOCK_PISTON_CONTRACT, 1.0F, 0.6F);
			}
			return true;
		}
		return false;
	}

	private float getMaxFuel() {
		return base_fuel_minutes * 1200F;
	}

	private boolean allowEquipment(EntityLivingBase user) {
		if (allowedBulk >= 2) {
			return true;// Any Armour
		}

		float bulk = ArmourCalculator.getEquipmentBulk(user);
		if (allowedBulk >= 0) {
			if (bulk > allowedBulk) {
				if (user instanceof EntityPlayer && world.isRemote) {
					user.sendMessage(new TextComponentString("vehicle.tooBigArmour"));
				}
				return false;
			}
			return true;
		}
		Iterable<ItemStack> armour = user.getArmorInventoryList();
		for (ItemStack stack : armour) {
			if (user instanceof EntityPlayer && world.isRemote) {
				user.sendMessage(new TextComponentString("vehicle.noArmour"));
			}
			return false;
		}
		return true;
	}

	@Override
	protected boolean isMovementBlocked() {
		return true;
	}

	@Override
	public boolean isPowered() {
		return noMoveTime == 0 && getFuel() > 0 && getControllingPassenger() != null;
	}

	@Override
	public void travel(float strafe, float vertical, float forward) {
		if (isPowered() && this.getControllingPassenger() != null && this.getControllingPassenger() instanceof EntityLivingBase) {
			EntityLivingBase user = (EntityLivingBase) getControllingPassenger();

			this.prevRotationYaw = this.rotationYaw = this.getControllingPassenger().rotationYaw;
			this.rotationPitch = this.getControllingPassenger().rotationPitch * 0.5F;
			this.setRotation(this.rotationYaw, this.rotationPitch);
			this.rotationYawHead = this.renderYawOffset = this.rotationYaw;
			strafe = MathHelper.clamp(this.getMoveStrafe(), -1F, 1F) * 0.5F;
			forward = MathHelper.clamp(this.getMoveForward(), -1F, 1F) * 0.5F;

			if (forward <= 0.0F) {
				forward *= 0.5F;// Backstep slower
			}

			if (!this.world.isRemote) {
				this.setAIMoveSpeed((float) this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());
				moveCogwork(strafe, forward);
			}

			user.prevLimbSwingAmount = user.limbSwingAmount;
			double d1 = this.posX - this.prevPosX;
			double d0 = this.posZ - this.prevPosZ;
			float f4 = MathHelper.sqrt(d1 * d1 + d0 * d0) * 4.0F;

			if (f4 > 1.0F) {
				f4 = 1.0F;
			}

			user.limbSwingAmount += (f4 - user.limbSwingAmount) * 0.4F;
			user.limbSwing += user.limbSwingAmount;
		} else {
			super.moveRelative(strafe, forward, 1.0F, 1.0F);
		}
	}

	private float getSpeedModifier() {
		return isSprinting() ? 2.0F : 1.0F;
	}

	public void moveCogwork(float strafe, float forward) {
		double d0;

		if (this.isInWater() || this.isInLava()) {
			d0 = this.posY;
			this.motionY -= 0.03D;

			if (this.collidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + 0.6000000238418579D - this.posY + d0, this.motionZ)) {
				this.motionY = 0.30000001192092896D;
			}
		}
		{
			float f2 = 0.91F;

			if (this.onGround) {
				f2 = this.world.getBlockState(new BlockPos(MathHelper.floor(this.posX), MathHelper.floor(this.getEntityBoundingBox().minY) - 1, MathHelper.floor(this.posZ))).getBlock().slipperiness * 0.91F;
			}

			float f3 = 0.16277136F / (f2 * f2 * f2);
			float up;

			if (this.onGround) {
				up = this.getAIMoveSpeed() * f3;
			} else {
				up = this.jumpMovementFactor;
			}
			if (getControllingPassenger() == null) {
				up = 0F;
			}
			if (isSprinting()) {
				up *= 2.0F;
			}

			this.move(MoverType.SELF, up, forward, 0.02F);
			f2 = 0.91F;

			if (this.onGround) {
				f2 = this.world.getBlockState(new BlockPos(MathHelper.floor(this.posX), MathHelper.floor(this.getEntityBoundingBox().minY) - 1, MathHelper.floor(this.posZ))).getBlock().slipperiness * 0.91F;
			}

			if (this.isOnLadder()) {
				float f5 = 0.15F;

				if (this.motionX < (-f5)) {
					this.motionX = (-f5);
				}

				if (this.motionX > f5) {
					this.motionX = f5;
				}

				if (this.motionZ < (-f5)) {
					this.motionZ = (-f5);
				}

				if (this.motionZ > f5) {
					this.motionZ = f5;
				}

				this.fallDistance = 0.0F;

				if (this.motionY < -0.15D) {
					this.motionY = -0.15D;
				}
			}

			this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);

			if (this.collidedHorizontally && this.isOnLadder()) {
				this.motionY = 0.2D;
			}

			BlockPos pos = new BlockPos((int) this.posX, 0, (int) this.posZ);
			if (this.world.isRemote && (!this.world.isBlockLoaded(pos)
					|| !this.world.getChunkFromBlockCoords(pos).isLoaded())) {
				if (this.posY > 0.0D) {
					this.motionY = -0.1D;
				} else {
					this.motionY = 0.0D;
				}
			} else {
				this.motionY -= 0.08D;
			}

			this.motionY *= 0.9800000190734863D;
			this.motionX *= f2;
			this.motionZ *= f2;
		}

		this.prevLimbSwingAmount = this.limbSwingAmount;
		d0 = this.posX - this.prevPosX;
		double d1 = this.posZ - this.prevPosZ;
		float f6 = MathHelper.sqrt(d0 * d0 + d1 * d1) * 4.0F;

		if (f6 > 1.0F) {
			f6 = 1.0F;
		}

		this.limbSwingAmount += (f6 - this.limbSwingAmount) * 0.4F;
		this.limbSwing += this.limbSwingAmount;
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		nbt.setString("Plating", this.getCustomMaterial());
		nbt.setFloat("Fuel", getFuel());
		nbt.setInteger("Bolts", getBolts());
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		if (nbt.hasKey("Plating", 8) && nbt.getString("Plating").length() > 0) {
			this.setCustomMaterial(nbt.getString("Plating"));
		}
		this.setFuel(nbt.getFloat("Fuel"));
		setBolts(nbt.getInteger("Bolts"));
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	/**
	 * Returns the sound this mob makes on death.
	 */
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_IRONGOLEM_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_IRONGOLEM_HURT;
	}

	@Override
	protected void playStepSound(BlockPos pos, Block block) {
		String s = alternateStep ? "in" : "out";
		alternateStep = !alternateStep;
		this.playSound(SoundEvents.BLOCK_PISTON_EXTEND, 0.25F, 1.0F);
		this.playSound(SoundEvents.ENTITY_IRONGOLEM_STEP, 1.0F, 1.0F);
	}

	@Override
	protected void updatePotionEffects() {
	}

	@Override
	public void addPotionEffect(PotionEffect effect) {
	}

	@Override
	public boolean isPotionApplicable(PotionEffect effect) {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void performHurtAnimation() {
	}

	@Override
	public float getAIMoveSpeed() {
		return getControllingPassenger() == null ? 1F : super.getAIMoveSpeed();
	}

	@Override
	public boolean isFullyArmoured() {
		return getPlating() != null;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float damage) {
		return attackEntityFrom(source, damage, true);
	}

	/**
	 * Modified attackEntityFrom
	 *
	 * @param shouldPass is used to determine if the wearer should take the hit
	 */
	public boolean attackEntityFrom(DamageSource source, float damage, boolean shouldPass) {
		if (shouldPass && this.getControllingPassenger() != null) {
			return this.getControllingPassenger().attackEntityFrom(source, damage);
		}

		if (source.isExplosion()) {
			this.noMoveTime = 20;
			damage *= 1.5F;
		}
		return super.attackEntityFrom(source, modifyDamage(this, damage, source));
	}

	public boolean shouldBlockPoisonOrMagic() {
		return getPlating() != null;
	}

	@Override
	public float modifyDamage(EntityLivingBase user, float damage, DamageSource src) {
		if (src.getDamageType().equalsIgnoreCase("humanstomp")) {
			return 0F;
		}
		if (src == DamageSource.WITHER || src.isMagicDamage()) {
			return shouldBlockPoisonOrMagic() ? 0F : damage;
		}

		if (user != this) {
			this.attackEntityFrom(src, damage, false);
		}

		float AC = 2.0F;
		float fResist = 0.0F;
		CustomMaterial plating = getPlating();
		if (plating != null) {
			AC = plating.hardness * ArmourDesign.COGWORK.getRating() * rating_modifier;
			fResist = plating.getFireResistance() / 100F;
		}

		if (src.isFireDamage()) {
			if (user == this) {
				return damage * MathHelper.clamp(1F - fResist, 0F, 1F);
			} else if (isFullyArmoured()) {
				return 0F;// All or nothing depends on if armour is full
			}
		}
		if (user == this) {
			return damage;
		}

		if (src.isUnblockable() && !isFullyArmoured()) {
			return damage;
		}
		return damage * 1F / AC;
	}

	@Override
	public void setFire(int i) {
	}

	@Override
	protected void damageEntity(DamageSource source, float dam) {
		CustomMaterial plating = this.getPlating();
		boolean canDestroy = false;// Only spanner or fire can destroy frames
		boolean isFrame = plating == null;

		if (source.getImmediateSource() != null && source.getImmediateSource() instanceof EntityLivingBase) {
			canDestroy = ToolHelper.getToolTypeFromStack(((EntityLivingBase) source.getImmediateSource()).getHeldItemMainhand()) == Tool.SPANNER;
		}
		if (source.isFireDamage() || source.canHarmInCreative()) {
			canDestroy = true;
		}

		if (isFrame) {
			if (canDestroy && getControllingPassenger() == null) {
				setHealth(0);
			} else {
				setHealth(getMaxHealth());
			}
			return;
		} else if (dam != 0.0F) {
			if (plating != null) {
				float HP = plating.durability * ArmourDesign.COGWORK.getDurability() * 20F * health_modifier;
				dam *= (this.getMaxHealth() / HP);
			}
			dam = this.applyPotionDamageCalculations(source, dam);

			float hp = this.getHealth() - dam;
			if (hp <= 1.0F) {
				hp = getMaxHealth();
				destroyArmour();
			}
			this.setHealth(hp);

			if (!source.isFireDamage()) {
				this.playSound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 1.0F,
						(this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F);
			}
		}
	}

	private void destroyArmour() {
		this.setCustomMaterial("");
	}

	@Override
	public void heal(float amount) {
	}

	@SideOnly(Side.CLIENT)
	public int getMetreScaled(int i) {
		return (int) (i / getMaxFuel() * getFuel());
	}

	@Override
	public void fall(float distance, float damageMultiplier) {
		if (distance > 2) {
			float power = Math.min(distance / 2F, 3F);
			initImpactLanding(posX, posY, posZ, power / 2F, ConfigArmour.cogworkGrief);
		}
	}

	public Shockwave initImpactLanding(double x, double y, double z, float power, boolean grief) {
		noMoveTime = 10;
		return newShockwave(x, y, z, power, false, grief);
	}

	@Override
	protected void collideWithEntity(Entity hit) {
		super.collideWithEntity(hit);
		if (!isSprinting() || !isPowered() || hit == getControllingPassenger()) {
			return;
		}
		this.playSound(this.getHurtSound(this.getLastDamageSource()), this.getSoundVolume(), this.getSoundPitch());
		float modifier = width * width * height / hit.width * hit.width * hit.height;// compare volume
		float force = (float) Math.hypot(motionX, motionZ) * modifier;
		TacticalManager.knockbackEntity(hit, this, force, force / 4F);
		hit.attackEntityFrom(
				DamageSource.causeMobDamage((getControllingPassenger() != null && getControllingPassenger() instanceof EntityLivingBase)
						? (EntityLivingBase) getControllingPassenger()
						: this),
				force);
	}

	/**
	 * returns a new explosion. Does initiation (at time of writing Explosion is not
	 * finished)
	 */
	public Shockwave newShockwave(double x, double y, double z, float power, boolean fire, boolean grief) {
		Shockwave explosion = new Shockwave("humanstomp", world, this.getControllingPassenger() != null ? this.getControllingPassenger() : this, x, y, z, power);
		explosion.isFlaming = fire;
		explosion.isGriefing = grief;
		explosion.isSmoking = grief;
		explosion.initiate();
		explosion.decorateWave(true);
		return explosion;
	}

	@Override
	protected void dropFewItems(boolean pkill, int looting) {
		this.dropItem(Item.getItemFromBlock(MineFantasyBlocks.BLOCKCOGWORK_HELM), 1);
		this.dropItem(Item.getItemFromBlock(MineFantasyBlocks.BLOCKCOGWORK_CHESTPLATE), 1);
		this.dropItem(Item.getItemFromBlock(MineFantasyBlocks.BLOCKCOGWORK_LEGS), 1);

	}

	@SideOnly(Side.CLIENT)
	public int getArmourRating() {
		return getArmourRating(getPlating());
	}

	public float getWeight() {
		float weight = base_frame_weight;// Weight of frame
		CustomMaterial plating = getPlating();
		if (plating != null) {
			weight += plating.density * base_armour_units;
		}
		return weight;
	}

	public boolean isUnderRepairFrame() {
		return PowerArmour.isStationBlock(world, new BlockPos(MathHelper.floor(this.posX), MathHelper.floor(this.posY + 3), MathHelper.floor(this.posZ)));
	}

	private void onPortalTick() {
		if (!this.world.isRemote && this.world instanceof WorldServer) {
			this.world.profiler.startSection("portal");
			MinecraftServer minecraftserver = ((WorldServer) this.world).getMinecraftServer();
			int i = this.getMaxInPortalTime();

			if (this.inPortal) {
				if (minecraftserver.getAllowNether()) {
					if (this.getControllingPassenger() == null && this.portalCounter++ >= i) {
						this.portalCounter = i;
						this.timeUntilPortal = this.getPortalCooldown();
						byte dimID;

						if (this.world.provider.getDimension() == -1) {
							dimID = 0;
						} else {
							dimID = -1;
						}
						this.changeDimension(dimID);
					}

					this.inPortal = false;
				}
			} else {
				if (this.portalCounter > 0) {
					this.portalCounter -= 4;
				}

				if (this.portalCounter < 0) {
					this.portalCounter = 0;
				}
			}

			if (this.timeUntilPortal > 0) {
				--this.timeUntilPortal;
			}

			this.world.profiler.endSection();
		}
	}

	@Override
	public Entity changeDimension(int id) {
		if (isBeingRidden()) {
			getControllingPassenger().changeDimension(id);
		}
		super.changeDimension(id);
		return getControllingPassenger();
	}

	@Override
	public boolean isArmoured(String piece) {
		return isFullyArmoured();
	}
}