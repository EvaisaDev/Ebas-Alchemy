package com.ebalchemy.crucible;

import com.ebalchemy.init.BlockInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
// IMPORTANT: import net.minecraft.world.item.Items
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A custom "empty" crucible block that transitions to BlockCrucible when filled
 * by water or potions.
 */
public class BlockEmptyCrucible extends AbstractCauldronBlock {

    private static final float RAIN_FILL_CHANCE = 0.05F;
    /**
     * Typically, modders use "Block.UPDATE_ALL" or "3" to trigger neighbor & visual updates.
     * For clarity, we define it explicitly here.
     */
    private static final int UPDATE_ALL = 3;

    public BlockEmptyCrucible() {
        super(BlockBehaviour.Properties.copy(Blocks.CAULDRON).noOcclusion().strength(3.0f), CauldronInteraction.EMPTY);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos,
                                 Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);

        if (itemstack.is(Items.POTION) || itemstack.is(Items.SPLASH_POTION) || itemstack.is(Items.LINGERING_POTION)) {
			if (!pLevel.isClientSide) {

				pLevel.setBlock(pPos, BlockInit.CRUCIBLE.get().defaultBlockState()
										.setValue(LayeredCauldronBlock.LEVEL, 1),
								UPDATE_ALL);

				var blockEntity = pLevel.getBlockEntity(pPos);
				if (blockEntity instanceof CrucibleTile crucible) {
					crucible.setFromItemStack(itemstack);
				}

				if (itemstack.getCount() == 1) {
					pPlayer.setItemInHand(pHand, new ItemStack(Items.GLASS_BOTTLE));
				} else {
					itemstack.shrink(1);
					ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
					if (!pPlayer.addItem(empty)) {
						pPlayer.drop(empty, false);
					}
				}

				pLevel.playSound(null, pPos, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,
								net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
			}
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        CauldronInteraction cauldroninteraction = CauldronInteraction.WATER.get(itemstack.getItem());
        if (cauldroninteraction != null && (cauldroninteraction == CauldronInteraction.FILL_WATER
                                           || cauldroninteraction == CauldronInteraction.WATER)) {

            InteractionResult res = cauldroninteraction.interact(pState, pLevel, pPos, pPlayer, pHand, itemstack);


            if (pLevel.getBlockState(pPos).getBlock() == Blocks.WATER_CAULDRON) {
                pLevel.setBlock(pPos,
                    BlockInit.CRUCIBLE.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3),
                    UPDATE_ALL
                );
            }
            return res;
        }

        // If none of the above matched, do nothing
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isFull(BlockState pState) {
        return false;
    }

    // --------------------------------------------------------------------
    //  Rain fill logic
    // --------------------------------------------------------------------
    protected static boolean shouldHandlePrecipitation(Level pLevel, Biome.Precipitation pPrecipitation) {
        if (pPrecipitation == Biome.Precipitation.RAIN) {
            return pLevel.getRandom().nextFloat() < RAIN_FILL_CHANCE;
        } else {
            return false;
        }
    }

    @Override
    public void handlePrecipitation(BlockState pState, Level pLevel, BlockPos pPos,
                                    Biome.Precipitation pPrecipitation) {
        if (shouldHandlePrecipitation(pLevel, pPrecipitation)) {
            if (pPrecipitation == Biome.Precipitation.RAIN) {
                // Switch from empty crucible to a single-layer watery crucible
                pLevel.setBlockAndUpdate(pPos,
                    BlockInit.CRUCIBLE.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1)
                );
                pLevel.gameEvent((Entity) null, GameEvent.FLUID_PLACE, pPos);
            }
        }
    }

    // --------------------------------------------------------------------
    //  Dripstone fill logic
    // --------------------------------------------------------------------
    @SuppressWarnings("deprecation")
    @Override
    protected boolean canReceiveStalactiteDrip(Fluid pFluid) {
        return pFluid.is(FluidTags.WATER);
    }

    @Override
    protected void receiveStalactiteDrip(BlockState pState, Level pLevel, BlockPos pPos, Fluid pFluid) {
        if (pFluid == Fluids.WATER) {
            // Switch from empty to single-layer watery crucible
            pLevel.setBlockAndUpdate(pPos,
                BlockInit.CRUCIBLE.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1)
            );
            pLevel.levelEvent(1047, pPos, 0);
            pLevel.gameEvent((Entity) null, GameEvent.FLUID_PLACE, pPos);
        }
    }
}
