package com.ebalchemy.crucible;

import java.util.HashMap;

import com.ebalchemy.init.BlockInit;
import com.ebalchemy.init.TileEntityInit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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

/**
 * A custom crucible block that can contain potions, combine effects, etc.
 */
public class BlockCrucible extends LayeredCauldronBlock implements EntityBlock, IDontCreateBlockItem {

    // Vanilla cauldron uses "3" as the max fill level; we do similarly
    private static final int UPDATE_ALL = 3;

    public BlockCrucible() {
        // Copy standard cauldron properties, but add noOcclusion() and strength(3)
        super(BlockBehaviour.Properties.copy(Blocks.CAULDRON).noOcclusion().strength(3.0f),
              LayeredCauldronBlock.RAIN, CauldronInteraction.WATER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CrucibleTile(pPos, pState);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return type == TileEntityInit.CRUCIBLE_TILE_TYPE.get()
            ? (world1, pos, state1, te) -> CrucibleTile.Tick(world1, pos, state1, (CrucibleTile) te)
            : null;
    }

    @Override
    public void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
        int fillLevel = state.getValue(LEVEL);
        float insideYPos = pos.getY() + (6.0F + 3 * fillLevel) / 16.0F;

        // Only act on the server side, if there's some fluid (fillLevel>0) and entity is low enough
        if (!worldIn.isClientSide && fillLevel > 0 && entityIn.getY() <= insideYPos) {
            // If it's an item entity, try to add ingredients
            if (entityIn instanceof ItemEntity itemEntity) {
                BlockEntity blockentity = worldIn.getBlockEntity(pos);
                if (blockentity instanceof CrucibleTile crucible) {
                    ItemStack stack = itemEntity.getItem();
                    if (crucible.tryAddIngredient(stack)) {
                        worldIn.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0f,
                                (float) (0.8f + Math.random() * 0.4f));
                        itemEntity.remove(RemovalReason.KILLED);
                    } else {
                        // Ingredient not accepted => fling item out
                        itemEntity.push(-0.2 + Math.random() * 0.4, 1, -0.2 + Math.random() * 0.4);
                    }
                }
            }
            // If it's a living entity, apply brew logic
            else if (entityIn instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
                BlockEntity blockentity = worldIn.getBlockEntity(pos);
                if (blockentity instanceof CrucibleTile crucible) {
                    crucible.handleLivingEntityCollision(livingEntity);
                }
            }
        }
    }

    @Override
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        // Client-side: play boiling sound if there's fluid and it’s hot enough
        if (pState.getValue(LEVEL) > 0 && pLevel.isClientSide) {
            BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof CrucibleTile crucible && crucible.getHeat() >= CrucibleTile.BOIL_POINT) {
                Minecraft mc = Minecraft.getInstance();
                pLevel.playSound(mc.player, pPos, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                        1.0f, (float) (0.8f + Math.random() * 0.4f));
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos,
                                 Player player, InteractionHand handIn, BlockHitResult hit) {
        ItemStack itemstack = player.getItemInHand(handIn);
        BlockEntity be = worldIn.getBlockEntity(pos);
        if (!(be instanceof CrucibleTile crucible)) {
            return InteractionResult.FAIL;
        }

        int fillLevel = state.getValue(LEVEL);

        if (itemstack.is(Items.WATER_BUCKET)) {
        	crucible.printIngredientPotentialEffects();
            if (crucible.isPotion()) {
                crucible.diluteBrew();
                worldIn.setBlock(pos, state.setValue(LEVEL, 3), UPDATE_ALL);
                worldIn.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(worldIn.isClientSide);
            } else {
                // Just fill with water
                worldIn.setBlock(pos, state.setValue(LEVEL, 3), UPDATE_ALL);
                return InteractionResult.sidedSuccess(worldIn.isClientSide);
            }
        }

        if (crucible.isPotion() && fillLevel > 0) {
            HashMap<MobEffect, Float> prominents = crucible.getProminentEffects();
            if (!prominents.isEmpty() && itemstack.is(Items.GLASS_BOTTLE)) {
                extractPotion(worldIn, crucible, player, handIn, state, pos);
                return InteractionResult.SUCCESS;
            }
        }
        
        if (crucible.isPotion() && fillLevel > 0 && itemstack.getItem().isEdible()) {
            if (!worldIn.isClientSide) {
                var prominentEffects = crucible.getProminentEffects();
                if (!prominentEffects.isEmpty()) {
                    // Create a copy of the original food item 
                    // so we can store custom potion data in its NBT
                    ItemStack infusedFood = itemstack.copy();
                    infusedFood.setCount(1); // We'll only infuse 1 at a time

                    // Build the effect list
                    java.util.List<MobEffectInstance> customEffects = new java.util.ArrayList<>();
                    for (var entry : prominentEffects.entrySet()) {
                        MobEffect effect = entry.getKey();
                        float levelF = entry.getValue();
                        int amplifier = Math.max(0, (int)levelF - 1);

                        // Example: 10 seconds = 200 ticks
                        MobEffectInstance inst = new MobEffectInstance(effect, 200, amplifier);
                        customEffects.add(inst);
                    }

                    // Store them as "custom potion effects" (like potions do) 
                    // in the NBT of the infusedFood stack
                    PotionUtils.setPotion(infusedFood, Potions.WATER);
                    PotionUtils.setCustomEffects(infusedFood, customEffects);

                    // OPTIONAL: rename if you like
                    // infusedFood.setHoverName(Component.translatable("item.ebalchemy.infused_food"));

                    // Remove 1 from the player's original stack
                    itemstack.shrink(1);

                    // Give them the new "infused" item
                    if (!player.addItem(infusedFood)) {
                        player.drop(infusedFood, false);
                    }

                    // Decrement the crucible fill level
                    if (fillLevel == 1) {
                        worldIn.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), UPDATE_ALL);
                    } else {
                        worldIn.setBlock(pos, state.setValue(LEVEL, fillLevel - 1), UPDATE_ALL);
                    }

                    // Optional: play a sound
                    worldIn.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            return InteractionResult.sidedSuccess(worldIn.isClientSide);
        }

        if (itemstack.is(Items.BUCKET)) {
            worldIn.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), UPDATE_ALL);
			
            return InteractionResult.sidedSuccess(worldIn.isClientSide);
        }

		if (itemstack.is(Items.POTION) || itemstack.is(Items.SPLASH_POTION) || itemstack.is(Items.LINGERING_POTION)) {


			if (fillLevel < 3) {
				worldIn.setBlock(pos, state.setValue(LEVEL, fillLevel + 1), UPDATE_ALL);
			} else {
				return InteractionResult.FAIL;
			}

		
			crucible.combineFromItemStack(itemstack, fillLevel);
		

			if (itemstack.getCount() == 1) {
				player.setItemInHand(handIn, new ItemStack(Items.GLASS_BOTTLE));
			} else {
				itemstack.shrink(1);
				ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
				if (!player.addItem(empty)) {
					player.drop(empty, false);
				}
			}

			worldIn.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,
							net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
		
            return InteractionResult.sidedSuccess(worldIn.isClientSide);
        }

        // Otherwise, nothing else to do
        return InteractionResult.FAIL;
    }

    private void extractPotion(Level worldIn, CrucibleTile crucible, Player player,
                               InteractionHand handIn, BlockState state, BlockPos pos) {
        if (!worldIn.isClientSide) {
            // Build a new potion stack: normal, splash, or lingering
            ItemStack potionstack = createBasePotionStack(crucible);

            // Copy the tile's prominent effects to the ItemStack
            var prominentEffects = crucible.getProminentEffects();
            var customEffects = new java.util.ArrayList<net.minecraft.world.effect.MobEffectInstance>();
            for (var entry : prominentEffects.entrySet()) {
                MobEffect effect = entry.getKey();
                float levelF = entry.getValue();
                int amplifier = Math.max(0, (int) levelF - 1);

                net.minecraft.world.effect.MobEffectInstance inst =
                    new net.minecraft.world.effect.MobEffectInstance(effect, crucible.getDuration(), amplifier);
                customEffects.add(inst);
            }
            // Force the base to be "water" but with custom effects
            PotionUtils.setPotion(potionstack, Potions.WATER);
            PotionUtils.setCustomEffects(potionstack, customEffects);

            // Rename it
            potionstack.setHoverName(Component.translatable("item.ebalchemy.concoction"));

			// hide potion effects in tooltip by adding hide_additional_tooltip={} to the nbt
			CompoundTag tag = potionstack.getOrCreateTag();
            tag.put("hide_additional_tooltip", new CompoundTag());
            tag.putInt("HideFlags", 127);

            // Remove 1 glass bottle
            player.getItemInHand(handIn).shrink(1);
            // Give them the new potion
            if (!player.addItem(potionstack)) {
                player.drop(potionstack, false);
            }

            // Decrement the cauldron fill level
            int existingLevel = state.getValue(LEVEL);
            if (existingLevel == 1) {
                // If that was the last layer, switch to the empty crucible block
                worldIn.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), UPDATE_ALL);
            } else {
                // Otherwise, just go down by one
                worldIn.setBlock(pos, state.setValue(LEVEL, existingLevel - 1), UPDATE_ALL);
            }
        }
    }


    private ItemStack createBasePotionStack(CrucibleTile crucible) {
        if (crucible.isLingering()) {
            return new ItemStack(Items.LINGERING_POTION);
        } else if (crucible.isSplash()) {
            return new ItemStack(Items.SPLASH_POTION);
        } else {
            return new ItemStack(Items.POTION);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }
}
