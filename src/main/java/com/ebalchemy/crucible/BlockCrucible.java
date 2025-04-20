package com.ebalchemy.crucible;

import java.util.HashMap;

import com.ebalchemy.init.BlockInit;
import com.ebalchemy.init.TileEntityInit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

/**
 *  Custom crucible block.  All client‑only classes are confined to the
 *  ClientHooks inner class and executed through DistExecutor so this file
 *  can be loaded on a dedicated server without blowing up.
 */
public class BlockCrucible extends LayeredCauldronBlock
                           implements EntityBlock, IDontCreateBlockItem {

    /** Same “update everything” bitmask vanilla uses. */
    private static final int UPDATE_ALL = 3;

    public BlockCrucible() {
        super(BlockBehaviour.Properties.copy(Blocks.CAULDRON)
                                        .noOcclusion()
                                        .strength(3.0f),
              LayeredCauldronBlock.RAIN,
              CauldronInteraction.WATER);
    }

    /* ------------------------------------------------------------------ */
    /*  Tile entity wiring                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world,
                                                                  BlockState state,
                                                                  BlockEntityType<T> type) {
        return type == TileEntityInit.CRUCIBLE_TILE_TYPE.get()
               ? (lvl, p, st, te) -> CrucibleTile.Tick(lvl, p, st, (CrucibleTile) te)
               : null;
    }

    /* ------------------------------------------------------------------ */
    /*  Entity collision                                                  */
    /* ------------------------------------------------------------------ */

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        int fillLevel = state.getValue(LEVEL);
        float insideY = pos.getY() + (6.0F + 3 * fillLevel) / 16.0F;

        if (!world.isClientSide && fillLevel > 0 && entity.getY() <= insideY) {

            /* ---- Items dropped into the crucible ---- */
            if (entity instanceof ItemEntity itemEntity) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof CrucibleTile crucible) {
                    ItemStack stack = itemEntity.getItem();
                    if (crucible.tryAddIngredient(stack)) {
                        world.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS,
                                        1.0f, (float) (0.8f + Math.random() * 0.4f));
                        itemEntity.remove(RemovalReason.KILLED);
                    } else {
                        itemEntity.push(-0.2 + Math.random() * 0.4,
                                         1,
                                         -0.2 + Math.random() * 0.4);
                    }
                }
            }

            /* ---- Living things swimming in the brew ---- */
            else if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof CrucibleTile crucible) {
                    crucible.handleLivingEntityCollision(living);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Client‑only particle / sound tick                                 */
    /* ------------------------------------------------------------------ */

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (state.getValue(LEVEL) > 0) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.animateTickClient(state, level, pos, rand));
        }
    }

    /**
     *  Everything in here is stripped out of the dedicated‑server jar.
     */
    @OnlyIn(Dist.CLIENT)
    private static final class ClientHooks {
        private static void animateTickClient(BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              RandomSource rand) {

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CrucibleTile crucible &&
                crucible.getHeat() >= CrucibleTile.BOIL_POINT) {

                net.minecraft.client.Minecraft mc =
                        net.minecraft.client.Minecraft.getInstance();

                level.playSound(mc.player, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                                1.0f, (float) (0.8f + rand.nextDouble() * 0.4f));
            }
        }

        private ClientHooks() {}
    }

    /* ------------------------------------------------------------------ */
    /*  Right‑click interaction                                           */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {

        ItemStack itemstack = player.getItemInHand(hand);
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof CrucibleTile crucible))
            return InteractionResult.FAIL;

        int fillLevel = state.getValue(LEVEL);

        /* -- Water bucket: fill or dilute -------------------------------- */
        if (itemstack.is(Items.WATER_BUCKET)) {

            crucible.printIngredientPotentialEffects();

            if (crucible.isPotion()) {
                crucible.diluteBrew();
                world.setBlock(pos, state.setValue(LEVEL, 3), UPDATE_ALL);
                world.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS,
                                1.0F, 1.0F);
                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                world.setBlock(pos, state.setValue(LEVEL, 3), UPDATE_ALL);
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }

        /* -- Glass bottle: pull potion out -------------------------------- */
        if (crucible.isPotion() && fillLevel > 0 && itemstack.is(Items.GLASS_BOTTLE)) {
            HashMap<net.minecraft.world.effect.MobEffect, Float> prominents =
                    crucible.getProminentEffects();
            if (!prominents.isEmpty()) {
                extractPotion(world, crucible, player, hand, state, pos);
                return InteractionResult.SUCCESS;
            }
        }

        /* -- Edible item: infuse ------------------------------------------ */
        if (crucible.isPotion() && fillLevel > 0 && itemstack.getItem().isEdible()) {

            if (!world.isClientSide) {
                var prominents = crucible.getProminentEffects();
                if (!prominents.isEmpty()) {

                    ItemStack infused = itemstack.copy();
                    infused.setCount(1);

                    java.util.List<net.minecraft.world.effect.MobEffectInstance> custom =
                            new java.util.ArrayList<>();

                    for (var e : prominents.entrySet()) {
                        net.minecraft.world.effect.MobEffect eff = e.getKey();
                        float lvlF = e.getValue();
                        int amplifier = Math.max(0, (int) lvlF - 1);
                        custom.add(new net.minecraft.world.effect.MobEffectInstance(eff,
                                                                                    200,
                                                                                    amplifier));
                    }

                    net.minecraft.world.item.alchemy.PotionUtils.setPotion(infused,
                            net.minecraft.world.item.alchemy.Potions.WATER);
                    net.minecraft.world.item.alchemy.PotionUtils.setCustomEffects(infused, custom);

                    itemstack.shrink(1);
                    if (!player.addItem(infused)) player.drop(infused, false);

                    if (fillLevel == 1) {
                        world.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(),
                                       UPDATE_ALL);
                    } else {
                        world.setBlock(pos, state.setValue(LEVEL, fillLevel - 1),
                                       UPDATE_ALL);
                    }

                    world.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS,
                                    1.0F, 1.0F);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        /* -- Empty bucket: remove everything ------------------------------ */
        if (itemstack.is(Items.BUCKET)) {
            world.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), UPDATE_ALL);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        /* -- Potion, splash, lingering: add ------------------------------------------------ */
        if (itemstack.is(Items.POTION) ||
            itemstack.is(Items.SPLASH_POTION) ||
            itemstack.is(Items.LINGERING_POTION)) {

            if (fillLevel < 3) {
                world.setBlock(pos, state.setValue(LEVEL, fillLevel + 1), UPDATE_ALL);
            } else {
                return InteractionResult.FAIL;
            }

            crucible.combineFromItemStack(itemstack, fillLevel);

            if (itemstack.getCount() == 1) {
                player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
            } else {
                itemstack.shrink(1);
                ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
                if (!player.addItem(empty)) player.drop(empty, false);
            }

            world.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS,
                            1.0F, 1.0F);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        return InteractionResult.FAIL;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private void extractPotion(Level world, CrucibleTile crucible, Player player,
                               InteractionHand hand, BlockState state, BlockPos pos) {

        if (!world.isClientSide) {

            ItemStack potionStack = createBasePotionStack(crucible);

            var prominents = crucible.getProminentEffects();
            var custom     = new java.util.ArrayList<net.minecraft.world.effect.MobEffectInstance>();

            for (var e : prominents.entrySet()) {
                net.minecraft.world.effect.MobEffect eff = e.getKey();
                float lvlF = e.getValue();
                int amplifier = Math.max(0, (int) lvlF - 1);

                custom.add(new net.minecraft.world.effect.MobEffectInstance(eff,
                                                                             crucible.getDuration(),
                                                                             amplifier));
            }

            net.minecraft.world.item.alchemy.PotionUtils.setPotion(potionStack,
                    net.minecraft.world.item.alchemy.Potions.WATER);
            net.minecraft.world.item.alchemy.PotionUtils.setCustomEffects(potionStack, custom);

            potionStack.setHoverName(Component.translatable("item.ebalchemy.concoction"));

            CompoundTag tag = potionStack.getOrCreateTag();
            tag.put("hide_additional_tooltip", new CompoundTag());
            tag.putInt("HideFlags", 127);

            player.getItemInHand(hand).shrink(1);
            if (!player.addItem(potionStack)) player.drop(potionStack, false);

            int existingLevel = state.getValue(LEVEL);
            if (existingLevel == 1) {
                world.setBlock(pos, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState(), UPDATE_ALL);
            } else {
                world.setBlock(pos, state.setValue(LEVEL, existingLevel - 1), UPDATE_ALL);
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

    /* ------------------------------------------------------------------ */

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
