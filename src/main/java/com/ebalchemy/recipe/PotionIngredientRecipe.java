package com.ebalchemy.recipe;

import com.google.gson.JsonObject;
import com.ebalchemy.EbAlchemy;
import com.ebalchemy.init.RecipeInit;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class PotionIngredientRecipe extends CustomRecipe {

    private ArrayList<Item> matchItems;

    private HashMap<MobEffect, Float> generatedEffects = null;
    
    private boolean makesSplash = false;
    private boolean makesLingering = false;
    private int durationAdded = 0;
    private long worldSeed = 0;
    
    // Tag for matching items (if used).
    private ResourceLocation tagResource = null;
    
    public PotionIngredientRecipe(ResourceLocation idIn) {
        super(idIn, CraftingBookCategory.MISC);
        matchItems = new ArrayList<>();
    }
    
    @Override
    public boolean matches(CraftingContainer inv, Level worldIn) {
        if (worldIn instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            worldSeed = serverLevel.getSeed();
        } else {
            worldSeed = 0;
        }
        if (inv.getContainerSize() == 1) {
            boolean match = getMatchItems().contains(inv.getItem(0).getItem());
            return match;
        }
        return false;
    }
    
    @Override
    public ItemStack assemble(CraftingContainer pContainer, RegistryAccess pRegistryAccess) {
        return new ItemStack(Items.POTION);
    }
    
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width == 1 && height == 1;
    }
    
    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeInit.POTION_INGREDIENT_SERIALIZER.get();
    }
    
    @Override
    public RecipeType<?> getType() {
        return RecipeInit.POTION_RECIPE_TYPE.get();
    }
    

    public HashMap<MobEffect, Float> getEffects(int level, ItemStack stack) {
        /*if (generatedEffects == null && worldSeed != 0) {
        	
            long combinedSeed = worldSeed ^ stack.getDescriptionId().hashCode() ^ (level * 123423);
            Random random = new Random(combinedSeed);
            generatedEffects = new HashMap<>();

            ArrayList<MobEffect> availableEffects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
            if (!availableEffects.isEmpty()) {
                int count = random.nextInt(3) + 1; // Choose between 1 and 3 effects.
                if(level == 2) {
                	count = 1;
                }
                EbAlchemy.LOGGER.info("PotionIngredientRecipe.getEffects: Generating " + count + " effects.");
                for (int i = 0; i < count && !availableEffects.isEmpty(); i++) {
                    int index = random.nextInt(availableEffects.size());
                    MobEffect effect = availableEffects.remove(index);
                    float strength = random.nextFloat(0.3f, 1.5f); // Default strength; adjust if needed.
                    if(level == 2) {
                    	strength = strength * 2f;
                    }
                    generatedEffects.put(effect, strength);
                    EbAlchemy.LOGGER.info("PotionIngredientRecipe.getEffects: Selected effect " +
                            ForgeRegistries.MOB_EFFECTS.getKey(effect) + " with strength " + strength);
                }
            } else {
                EbAlchemy.LOGGER.warn("PotionIngredientRecipe.getEffects: No available effects in registry!");
            }
        }
		*/
		if (generatedEffects == null) {
			generatedEffects = new HashMap<MobEffect, Float>();
		}

        return generatedEffects;
    }


    
    public ArrayList<Item> getMatchItems(){
        if (tagResource != null) {
            ITag<Item> tag = ForgeRegistries.ITEMS.tags().getTag(ForgeRegistries.ITEMS.tags().createTagKey(tagResource));
            if (tag != null) {
                tag.forEach(matchItems::add);
            }
            tagResource = null;			
        }
        return matchItems;
    }
    
    public boolean getMakesSplash() {
        return makesSplash;
    }
    
    public boolean getMakesLingering() {
        return makesLingering;
    }
    
    public int getDurationAdded() {
        return durationAdded;
    }
    
    public static class Serializer implements RecipeSerializer<PotionIngredientRecipe> {
        @Override
        public PotionIngredientRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            PotionIngredientRecipe recipe = new PotionIngredientRecipe(recipeId);
            
            if (json.has("item")) {
                recipe.matchItems.add(ForgeRegistries.ITEMS.getValue(new ResourceLocation(json.get("item").getAsString())));
            } else if (json.has("tag")) {
                recipe.tagResource = new ResourceLocation(json.get("tag").getAsString());
            } else {
                EbAlchemy.LOGGER.error("Potion Ingredient Recipe is missing item or tag field");
            }
            
            
            if (json.has("duration_added"))
                recipe.durationAdded = json.get("duration_added").getAsInt();
            
            if (json.has("splash_catalyst"))
                recipe.makesSplash = json.get("splash_catalyst").getAsBoolean();
            
            if (json.has("lingering_catalyst"))
                recipe.makesLingering = json.get("lingering_catalyst").getAsBoolean();
            
            return recipe;
        }
        
        @Override
        public PotionIngredientRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            PotionIngredientRecipe recipe = new PotionIngredientRecipe(recipeId);
            
            recipe.makesLingering = buffer.readBoolean();
            recipe.makesSplash = buffer.readBoolean();
            recipe.durationAdded = buffer.readInt();
            
            boolean hasTagResource = buffer.readBoolean();
            if (hasTagResource)
                recipe.tagResource = buffer.readResourceLocation();

            int numItems = buffer.readInt();
            for (int i = 0; i < numItems; i++) {
                recipe.matchItems.add(ForgeRegistries.ITEMS.getValue(buffer.readResourceLocation()));
            }
            
            // Effects are generated from the tag and world seed, so they are not transmitted.
            return recipe;
        }
        
        @Override
        public void toNetwork(FriendlyByteBuf buffer, PotionIngredientRecipe recipe) {
            buffer.writeBoolean(recipe.makesLingering);
            buffer.writeBoolean(recipe.makesSplash);
            buffer.writeInt(recipe.durationAdded);
            
            buffer.writeBoolean(recipe.tagResource != null);
            if (recipe.tagResource != null)
                buffer.writeResourceLocation(recipe.tagResource);
            
            buffer.writeInt(recipe.matchItems.size());
            recipe.matchItems.forEach(i -> buffer.writeResourceLocation(ForgeRegistries.ITEMS.getKey(i)));
            
            // Do not write effects – they are generated on the client based on the tag and world seed.
        }
    }
}
