package com.ebalchemy.events;

import com.ebalchemy.EbAlchemy;
import com.ebalchemy.init.ItemInit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client‐only events for dynamic item tints.
 */
@Mod.EventBusSubscriber(modid = EbAlchemy.MODID,
                        bus    = Mod.EventBusSubscriber.Bus.MOD,
                        value  = Dist.CLIENT)
public class ClientEventSubscriber {

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("potion_color")) {
                    // apply the saved RGB, force alpha to 0xFF
                    return 0xFF000000 | (tag.getInt("potion_color") & 0xFFFFFF);
                } else {
                    // empty → no tint (white × 0 opacity)
                    // but since alpha is ignored, use 0xFFFFFF so the overlay is invisible
                    return 0xFFFFFF;
                }
            }
            // layer0 (base) stays un‐tinted
            return 0xFFFFFF;
        }, ItemInit.BREW_CHARM.get());
    }

}