package com.ebalchemy;

import com.ebalchemy.config.Config;
import com.ebalchemy.init.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("ebalchemy")
public class EbAlchemy {
	public static final String MODID = "ebalchemy";
	public static final Logger LOGGER = LogManager.getLogger();
	final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

	public EbAlchemy() {
		final boolean HIDE_CONSOLE_NOISE = false; 
		if (HIDE_CONSOLE_NOISE) {
			ForgeLoggerTweaker.setMinimumLevel(Level.WARN);
			ForgeLoggerTweaker.applyLoggerFilter();
		}
		
		//load config
		Config.loadConfig(Config.CONFIG, FMLPaths.CONFIGDIR.get().resolve("mystic-alchemy-brewing-config.toml"));

		ItemInit.ITEMS.register(modEventBus);
		BlockInit.BLOCKS.register(modEventBus);
		TileEntityInit.TILE_ENTITY_TYPES.register(modEventBus);
		RecipeInit.SERIALIZERS.register(modEventBus);
		RecipeInit.RECIPE_TYPES.register(modEventBus);
		
		if (FMLEnvironment.dist.isClient()) {
			modEventBus.register(TileEntityClientInit.class);
		};

	}
}
