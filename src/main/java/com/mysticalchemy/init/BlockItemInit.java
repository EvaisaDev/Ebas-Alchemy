package com.mysticalchemy.init;

import java.util.ArrayList;

import com.mysticalchemy.MysticAlchemy;
import com.mysticalchemy.crucible.IDontCreateBlockItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = MysticAlchemy.MODID, bus = Bus.MOD)
public class BlockItemInit {

	private static final ArrayList<BlockItem> blockItems = new ArrayList<>();

	@SubscribeEvent
	public static void registerItems(final RegisterEvent event) {

		event.register(ForgeRegistries.Keys.ITEMS, helper -> {
			BlockInit.BLOCKS.getEntries().stream().map(RegistryObject::get).forEach(block -> {
				if (block instanceof IDontCreateBlockItem)
					return;

				final BlockItem blockItem = new BlockItem(block, new Item.Properties());
				blockItems.add(blockItem);
				helper.register(ForgeRegistries.BLOCKS.getKey(block), blockItem);

			});
		});
	}
	
	@SubscribeEvent
	public static void onCreativeMenuInit(CreativeModeTabEvent.BuildContents event) {
		if (event.getTab() == CreativeModeTabs.INGREDIENTS) {
			blockItems.forEach(bi -> event.accept(bi));
		}
	}

}
