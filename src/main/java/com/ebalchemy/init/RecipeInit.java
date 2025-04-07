package com.ebalchemy.init;

import com.ebalchemy.EbAlchemy;
import com.ebalchemy.recipe.PotionIngredientRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid=EbAlchemy.MODID, bus=Bus.MOD)
public class RecipeInit {
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, EbAlchemy.MODID);
	public static final RegistryObject<RecipeSerializer<PotionIngredientRecipe>> POTION_INGREDIENT_SERIALIZER = SERIALIZERS.register("potion_ingredient", PotionIngredientRecipe.Serializer::new);

	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, EbAlchemy.MODID);
	public static RegistryObject<RecipeType<PotionIngredientRecipe>> POTION_RECIPE_TYPE = RECIPE_TYPES.register("potion-ingredient-type", () -> new RecipeType<PotionIngredientRecipe>(){
		ResourceLocation potion_type = new ResourceLocation(EbAlchemy.MODID, "potion-ingredient-type");

		@Override
		public String toString() {
			return potion_type.toString();
		}
	});
}
