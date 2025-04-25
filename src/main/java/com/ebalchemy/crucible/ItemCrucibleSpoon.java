package com.ebalchemy.crucible;

import com.ebalchemy.init.BlockInit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;    // kept unchanged
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;

public class ItemCrucibleSpoon extends Item {
    public ItemCrucibleSpoon() {
        super(new Item.Properties().stacksTo(1).durability(200));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        BlockState state = world.getBlockState(context.getClickedPos());

        if (state.getBlock() == BlockInit.CRUCIBLE.get()) {
            CrucibleTile crucible = (CrucibleTile) world.getBlockEntity(context.getClickedPos());
            if (crucible != null) {
                if (!world.isClientSide && !player.isCreative()) {
                    // Properly damage & break the spoon when it runs out
                    stack.hurtAndBreak(1, player, p ->
                        // this callback fires the break animation/sound on the correct hand
                        p.broadcastBreakEvent(context.getHand())
                    );
                }
                crucible.stir();
                world.playSound(player,
                    context.getClickedPos(),
                    SoundEvents.PLAYER_SPLASH,
                    SoundSource.BLOCKS,
                    1.0f,
                    (float)(0.8f + Math.random() * 0.4f)
                );
                return InteractionResult.SUCCESS;
            }
        }

        return super.onItemUseFirst(stack, context);
    }
}
