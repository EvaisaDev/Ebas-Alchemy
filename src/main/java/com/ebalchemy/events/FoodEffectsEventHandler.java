package com.ebalchemy.events;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.util.List;

import com.ebalchemy.EbAlchemy;

@Mod.EventBusSubscriber(modid = EbAlchemy.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FoodEffectsEventHandler {

    @SubscribeEvent
    public static void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        ItemStack stack = event.getItem();
        LivingEntity eater = event.getEntity();

        // If the item is edible and has CustomPotionEffects in its NBT, apply them
        if (stack.getItem().isEdible()) {
            List<MobEffectInstance> customEffects = PotionUtils.getCustomEffects(stack);
            if (!customEffects.isEmpty()) {
                for (MobEffectInstance inst : customEffects) {
                    eater.addEffect(new MobEffectInstance(inst));
                }
            }
        }
    }
}