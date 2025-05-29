package com.ebalchemy.init;

import com.ebalchemy.EbAlchemy;
import com.ebalchemy.api.CreativeTabs;
import com.ebalchemy.crucible.BrewCharmItem;
import com.ebalchemy.crucible.ItemCrucibleSpoon;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;


@Mod.EventBusSubscriber(modid = EbAlchemy.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ItemInit {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EbAlchemy.MODID);
	
	public static RegistryObject<ItemCrucibleSpoon> SPOON = ITEMS.register("crucible_spoon", () -> new ItemCrucibleSpoon());
    public static final RegistryObject<BrewCharmItem> BREW_CHARM =
            ITEMS.register("brew_charm",
                () -> new BrewCharmItem(new Item.Properties()
                    .stacksTo(1)
                )
            );
    
	@SubscribeEvent
	public static void FillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTab() == CreativeTabs.MYSTIC_ALCHEMY) {
			ITEMS.getEntries().stream().map(RegistryObject::get).forEach(event::accept);
		}
	}
}
