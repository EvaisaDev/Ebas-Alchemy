package com.mysticalchemy.crucible;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mysticalchemy.init.BlockInit;
import com.mysticalchemy.init.TileEntityInit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BlockCrucible extends LayeredCauldronBlock implements EntityBlock, IDontCreateBlockItem {
    public BlockCrucible() {
        super(BlockBehaviour.Properties.copy(Blocks.CAULDRON).noOcclusion().strength(3.0f), LayeredCauldronBlock.RAIN, CauldronInteraction.WATER);
    }
    
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CrucibleTile(pPos, pState);
    }
    
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return type == TileEntityInit.CRUCIBLE_TILE_TYPE.get() ? (world1, pos, state1, te) -> CrucibleTile.Tick(world1, pos, state1, (CrucibleTile) te) : null;
    }
    
    @Override
    public void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
        int fillLevel = state.getValue(LEVEL);
        float insideYPos = pos.getY() + (6.0F + 3 * fillLevel) / 16.0F;
        if (!worldIn.isClientSide && fillLevel > 0 && entityIn.getY() <= insideYPos) {
            if (entityIn instanceof ItemEntity) {
                CrucibleTile crucible = (CrucibleTile) worldIn.getBlockEntity(pos);
                if (crucible != null) {
                    ItemStack stack = ((ItemEntity) entityIn).getItem();
                    if (crucible.tryAddIngredient(stack)) {
                        worldIn.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0f,
                                (float) (0.8f + Math.random() * 0.4f));
                        entityIn.remove(RemovalReason.KILLED);
                    } else {
                        entityIn.push(-0.2 + Math.random() * 0.4, 1, -0.2 + Math.random() * 0.4); 
                    }
                }
            } else if (entityIn instanceof LivingEntity) {
                CrucibleTile crucible = (CrucibleTile) worldIn.getBlockEntity(pos);
                if (crucible != null && crucible.getHeat() > crucible.getMaxHeat() / 2) {
                    entityIn.hurt(worldIn.damageSources().inFire(), 1);
                }
            }
        }
    }
    
    @Override
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        if (pLevel.isClientSide && pState.getValue(LEVEL) > 0) {
            CrucibleTile crucible = (CrucibleTile) pLevel.getBlockEntity(pPos);
            if (crucible != null && crucible.getHeat() >= CrucibleTile.BOIL_POINT) {
                Minecraft mc = Minecraft.getInstance();
                pLevel.playSound(mc.player, pPos, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                        1.0f, (float) (0.8f + Math.random() * 0.4f));
            }
        }
    }
    
	@Override
	public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player player, InteractionHand handIn, BlockHitResult hit) {
		// The item currently in the player's hand
		ItemStack itemstack = player.getItemInHand(handIn);
		// Get our crucible tile entity
		CrucibleTile crucible = (CrucibleTile) worldIn.getBlockEntity(pos);
		if (crucible == null) {
			return InteractionResult.FAIL;
		}

		// ------------------------------------------------
		// 1) USING A WATER BUCKET ON THE CRUCIBLE
		// ------------------------------------------------
		if (itemstack.is(Items.WATER_BUCKET)) {
			// If the crucible has an active potion brew...
			if (crucible.isPotion()) {
				// Dilute the brew
				crucible.diluteBrew();
				// Fill the cauldron visually to level 3
				worldIn.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, 3), UPDATE_ALL);
				// Optional: play a small splash sound
				worldIn.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
				// Return success so default CauldronInteraction logic can finish
				return InteractionResult.sidedSuccess(worldIn.isClientSide);
			} else {
				// No potion in the crucible, so just fill with water
				worldIn.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, 3), UPDATE_ALL);
				return InteractionResult.sidedSuccess(worldIn.isClientSide);
			}
		}

		// ------------------------------------------------
		// 2) EXTRACT A POTION WITH A GLASS BOTTLE
		// ------------------------------------------------
		if (crucible.isPotion() && state.getValue(LEVEL) > 0) {
			HashMap<MobEffect, Float> prominents = crucible.getProminentEffects();
			if (!prominents.isEmpty()) {
				// If the player is holding a glass bottle
				if (itemstack.is(Items.GLASS_BOTTLE)) {
					extractPotion(worldIn, prominents, crucible, player, handIn, state, pos);
					return InteractionResult.SUCCESS;
				}
				// If a potion brew exists, but it's not a bottle in hand,
				// we simply block other interactions so items aren't lost.
				return InteractionResult.SUCCESS;
			}
		}

		// ------------------------------------------------
		// 3) EMPTY THE CRUCIBLE WITH AN EMPTY BUCKET
		// ------------------------------------------------
		if (itemstack.is(Items.BUCKET)) {
			// Remove all water/potion from the crucible
			worldIn.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), UPDATE_ALL);
			// Let vanilla handle converting the Bucket -> Water Bucket, if applicable
			return InteractionResult.sidedSuccess(worldIn.isClientSide);
		}

		// Nothing else handled
		return InteractionResult.FAIL;
	}

    
    private void extractPotion(Level worldIn, HashMap<MobEffect, Float> prominents, CrucibleTile crucible, Player player, InteractionHand handIn, BlockState state, BlockPos pos) {
        if (!worldIn.isClientSide) {
            List<MobEffectInstance> prominentEffects = new ArrayList<>();

            ItemStack potionstack = createBasePotionStack(crucible);

            prominents.forEach((e, f) -> {
                prominentEffects.add(new MobEffectInstance(e, e.isInstantenous() ? 1 : crucible.getDuration(), (int) Math.floor(f - 1)));
            });
            PotionUtils.setPotion(potionstack, Potions.WATER);
            PotionUtils.setCustomEffects(potionstack, prominentEffects);
            
            /*CompoundTag tag = potionstack.getOrCreateTag();
            tag.put("hide_additional_tooltip", new CompoundTag());
            tag.putInt("HideFlags", 127);*/

            potionstack.setHoverName(Component.translatable("item.mysticalchemy.concoction"));

            player.getItemInHand(handIn).shrink(1);
            if (!player.addItem(potionstack)) {
                player.drop(potionstack, false);
            }

            int existingLevel = state.getValue(LEVEL);
            if (existingLevel == 1) {
                worldIn.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), 3);
            } else {
                worldIn.setBlock(pos, state.setValue(LEVEL, existingLevel - 1), 3);
            }
        }
    }
    
    private ItemStack createBasePotionStack(CrucibleTile crucible) {
        Item outputPotionItem;
        
        if (crucible.isLingering()) {
            outputPotionItem = Items.LINGERING_POTION;
        } else if (crucible.isSplash()) {
            outputPotionItem = Items.SPLASH_POTION;
        } else {
            outputPotionItem = Items.POTION;
        }
        
        return new ItemStack(outputPotionItem);
    }
    
    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }
}
