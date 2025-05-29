package com.ebalchemy.crucible;

import java.util.List;

import javax.annotation.Nullable;

import com.ebalchemy.EbAlchemy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class BrewCharmItem extends Item implements ICurioItem {

    public BrewCharmItem(Properties props) {
        super(props.stacksTo(1));
    }

    public void emptyIntoCrucible(ItemStack stack, CrucibleTile crucible) {
        crucible.setFromCharm(stack);
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove("brew_effects");
        tag.remove("max_remaining_secs");
        tag.remove("potion_color");
        tag.remove("splash");
        tag.remove("lingering");
        stack.setTag(tag);
    }

    public void fillFromCrucible(ItemStack stack, CrucibleTile crucible) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag effectsList = new ListTag();
        int totalSecs = 0;

        for (MobEffectInstance inst : crucible.getCurrentEffects().values()) {
            CompoundTag effTag = new CompoundTag();
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
            effTag.putString("effect", key == null ? "" : key.toString());
            effTag.putInt("amplifier", inst.getAmplifier());
            int origSeconds = (int) Math.ceil(inst.getDuration() / 20.0);
            int remaining = Math.max(1, origSeconds * 3);
            effTag.putInt("remaining_secs", remaining);
            effectsList.add(effTag);
            totalSecs += remaining;
        }

        tag.put("brew_effects", effectsList);
        tag.putInt("max_remaining_secs", totalSecs);
        tag.putBoolean("splash", crucible.isSplash());
        tag.putBoolean("lingering", crucible.isLingering());
        tag.putInt("potion_color", (int) crucible.getPotionColor());
        stack.setTag(tag);
    }


    @Override
    public void curioTick(SlotContext context, ItemStack stack) {
        if (!(context.entity() instanceof Player player)) return;
        Level world = player.getCommandSenderWorld();
        if (world.isClientSide) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("brew_effects", Tag.TAG_LIST)) return;
        if (player.tickCount % 20 != 0) return;

        boolean infinite = tag.getBoolean("infinite");
        ListTag list = tag.getList("brew_effects", Tag.TAG_COMPOUND);
        ListTag newList = new ListTag();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag effTag = list.getCompound(i);
            int secs = effTag.getInt("remaining_secs");
            if (secs <= 0 && !infinite) continue;

            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(
                new ResourceLocation(effTag.getString("effect"))
            );
            if (effect == null) continue;

            int amp = effTag.getInt("amplifier");
            int durTicks = infinite ? Integer.MAX_VALUE : secs * 20;

            if (player.getEffect(effect) == null) {
                player.addEffect(new MobEffectInstance(effect, durTicks, amp, false, true, true));
            }

            if (!infinite) {
                effTag.putInt("remaining_secs", secs - 1);
            }
            newList.add(effTag);
        }

        if (newList.isEmpty() && !tag.getBoolean("infinite")) {
            tag.remove("brew_effects");
            tag.remove("max_remaining_secs");
            tag.remove("potion_color");
            tag.remove("splash");
            tag.remove("lingering");
        } else {
            tag.put("brew_effects", newList);
        }
        stack.setTag(tag);
    }



    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level world,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
        tooltip.add(Component.translatable("item.ebalchemy.brew_charm.tooltip"));
        CompoundTag tag = stack.getTag();
        boolean hasEffects = tag != null && tag.contains("brew_effects", Tag.TAG_LIST);
        if (!hasEffects) {
            tooltip.add(Component.translatable("item.ebalchemy.brew_charm.empty"));
            return;
        }
        tooltip.add(Component.translatable("item.ebalchemy.brew_charm.filled"));
        if (tag.contains("max_remaining_secs", Tag.TAG_INT)) {
            int maxSecs = tag.getInt("max_remaining_secs");
            int remaining = 0;
            ListTag list = tag.getList("brew_effects", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                remaining += list.getCompound(i).getInt("remaining_secs");
            }
            int percent = (int) Math.ceil(remaining * 100.0 / maxSecs);
            tooltip.add(Component.literal(percent + "% full"));
        }
    }

    public boolean hasBrew(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("brew_effects", Tag.TAG_LIST);
    }
}
