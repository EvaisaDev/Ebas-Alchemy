package com.ebalchemy.events;

import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "ebalchemy", value = Dist.CLIENT)
public class TooltipEventHandler {
    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        CompoundTag tag = stack.getTag();
        // only strip desc lines from your custom-marked bottles
        if (tag == null || !tag.getBoolean("ebalchemy:hide_desc")) {
            return;
        }

        // getTooltipElements() returns List<Either<FormattedText,TooltipComponent>>
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        boolean firstTextSeen = false;
        Iterator<Either<FormattedText, TooltipComponent>> it = elements.iterator();
        while (it.hasNext()) {
            Either<FormattedText, TooltipComponent> either = it.next();
            if (either.left().isPresent()) {
                if (!firstTextSeen) {
                    // keep the very first text line (the item’s name)
                    firstTextSeen = true;
                } else {
                    // remove all subsequent text lines (the unwanted “.desc” lines)
                    it.remove();
                }
            }
        }
    }
}
