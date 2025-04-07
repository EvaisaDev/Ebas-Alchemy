package com.ebalchemy.api;

import com.ebalchemy.EbAlchemy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = EbAlchemy.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTabs {
    public static CreativeModeTab MYSTIC_ALCHEMY = CreativeModeTab.builder().icon(() -> {
        return new ItemStack((ItemLike) ForgeRegistries.ITEMS
                .getValue(new ResourceLocation(EbAlchemy.MODID, "crucible_empty")));
    }).title(Component.translatable("itemGroup.ebalchemy")).build();

    @SubscribeEvent
    public static void onRegisterCreativeTabs(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, (helper) -> {
            helper.register(new ResourceLocation(EbAlchemy.MODID, "mystic_alchemy"), MYSTIC_ALCHEMY);
        });
    }
}
