package com.ebalchemy.events;

import com.ebalchemy.EbAlchemy;
import com.ebalchemy.crucible.BrewCharmItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

@Mod.EventBusSubscriber(modid = EbAlchemy.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BrewCharmEvents {
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        ItemStack from = event.getFrom();
        ItemStack to   = event.getTo();
        boolean wasCharm = from.getItem() instanceof BrewCharmItem;
        boolean isCharm  = to.getItem()   instanceof BrewCharmItem;
        if (!wasCharm && !isCharm) return;

        if (wasCharm) {
            CompoundTag tag = from.getTag();
            if (tag != null && tag.contains("brew_effects", Tag.TAG_LIST)) {
                ListTag effects = tag.getList("brew_effects", Tag.TAG_COMPOUND);
                for (int i = 0; i < effects.size(); i++) {
                    CompoundTag eff = effects.getCompound(i);
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS
                        .getValue(new ResourceLocation(eff.getString("effect")));
                    if (effect != null) {
                        player.removeEffect(effect);
                    }
                }
            }
        }

        if (isCharm) {
            CompoundTag tag = to.getTag();
            if (tag != null && tag.contains("brew_effects", Tag.TAG_LIST)) {
                ListTag effects = tag.getList("brew_effects", Tag.TAG_COMPOUND);
                boolean infinite = tag.getBoolean("infinite");
                for (int i = 0; i < effects.size(); i++) {
                    CompoundTag eff = effects.getCompound(i);
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS
                        .getValue(new ResourceLocation(eff.getString("effect")));
                    if (effect != null) {
                        int amp = eff.getInt("amplifier");
                        int duration = infinite
                            ? Integer.MAX_VALUE
                            : eff.getInt("remaining_secs") * 20;
                        player.addEffect(new MobEffectInstance(effect, duration, amp, false, true, true));
                    }
                }
            }
        }
    }
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.getItem() instanceof BrewCharmItem && right.getItem() == Items.DIAMOND) {
            ItemStack result = left.copy();
            CompoundTag tag = result.getOrCreateTag();
            tag.putBoolean("infinite", true);
            result.setTag(tag);
            event.setOutput(result);
            event.setCost(1);
            event.setMaterialCost(1);
        }
    }
}
