package com.mysticalchemy.crucible;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.LinkedHashSet;

import com.mysticalchemy.MysticAlchemy;
import com.mysticalchemy.config.BrewingConfig;
import com.mysticalchemy.init.BlockInit;
import com.mysticalchemy.init.RecipeInit;
import com.mysticalchemy.init.TileEntityInit;
import com.mysticalchemy.recipe.PotionIngredientRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import net.minecraft.client.resources.language.I18n;

public class CrucibleTile extends BlockEntity {

    // --- Constants and Static Data ---
    private static final int UPDATE_RATE = 10;
    public static final float MIN_TEMP = 0f;
    public static final float MAX_TEMP = 200f;
    public static final float BOIL_POINT = 100f;
    public static final float ITEM_HEAT_LOSS = 25f;
    public static final int MAX_MAGNITUDE = 3;
    public static final int MAX_EFFECTS = 6;
    public static final int MAX_DURATION = 9600; // 8 minutes

    // Typical water color when no effects remain.
    private static final long WATER_COLOR = 0x3F76E4L;

    private static HashMap<Block, Float> heaters;
    public static final ArrayList<Item> validIngredients = new ArrayList<>();

    static {
        heaters = new HashMap<>();
        heaters.put(Blocks.CAMPFIRE, 12.0f);
        heaters.put(Blocks.FIRE, 18.0f);
        heaters.put(Blocks.LAVA, 30.0f);
        heaters.put(Blocks.SOUL_CAMPFIRE, 30.0f);
        heaters.put(Blocks.ICE, -2.0f);

        registerIngredientsByTag(new ResourceLocation("c:animal_foods"));
        registerIngredientsByTag(new ResourceLocation("c:foods"));
        registerIngredientsByTag(new ResourceLocation("c:crops"));
        registerIngredientsByTag(new ResourceLocation("c:dusts"));
        registerIngredientsByTag(new ResourceLocation("c:dyes"));
		registerIngredientsByTag(new ResourceLocation("c:leaves"));
        registerIngredientsByTag(new ResourceLocation("c:gems"));
        registerIngredientsByTag(new ResourceLocation("c:mushrooms"));
        registerIngredientsByTag(new ResourceLocation("c:nether_stars"));
        registerIngredientsByTag(new ResourceLocation("c:nuggets"));
        registerIngredientsByTag(new ResourceLocation("c:ender_pearls"));
        registerIngredientsByTag(new ResourceLocation("c:raw_materials"));
        registerIngredientsByTag(new ResourceLocation("c:seeds"));
        registerIngredientsByTag(new ResourceLocation("c:ingots"));
		registerIngredientsByTag(new ResourceLocation("c:rods"));
		registerIngredientsByTag(new ResourceLocation("c:skulls"));
		
		
		registerIngredientsByTag(new ResourceLocation("forge:animal_foods"));
        registerIngredientsByTag(new ResourceLocation("forge:foods"));
        registerIngredientsByTag(new ResourceLocation("forge:crops"));
        registerIngredientsByTag(new ResourceLocation("forge:dusts"));
        registerIngredientsByTag(new ResourceLocation("forge:dyes"));
		registerIngredientsByTag(new ResourceLocation("forge:leaves"));
        registerIngredientsByTag(new ResourceLocation("forge:gems"));
        registerIngredientsByTag(new ResourceLocation("forge:mushrooms"));
        registerIngredientsByTag(new ResourceLocation("forge:nether_stars"));
        registerIngredientsByTag(new ResourceLocation("forge:nuggets"));
        registerIngredientsByTag(new ResourceLocation("forge:ender_pearls"));
        registerIngredientsByTag(new ResourceLocation("forge:raw_materials"));
        registerIngredientsByTag(new ResourceLocation("forge:seeds"));
        registerIngredientsByTag(new ResourceLocation("forge:ingots"));
		registerIngredientsByTag(new ResourceLocation("forge:rods"));
		registerIngredientsByTag(new ResourceLocation("forge:skulls"));

        registerIngredientsByTag(new ResourceLocation("minecraft:fishes"));
        registerIngredientsByTag(new ResourceLocation("minecraft:flowers"));

        registerIngredientsByTag(new ResourceLocation("minecraft:fox_food"));
        registerIngredientsByTag(new ResourceLocation("minecraft:leaves"));

		registerIngredientsByTag(new ResourceLocation("minecraft:enchanting_fuels"));


        registerIngredientsByItemID(new ResourceLocation("minecraft:amethyst_shard"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:sweet_berries"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:grass"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:egg"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:turtle_egg"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:sniffer_egg"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:glowstone_dust"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:sugar"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:sugar_cane"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:blaze_powder"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:bone_meal"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:bone"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:rotten_flesh"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:spider_eye"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:fermented_spider_eye"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:ghast_tear"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:cocoa_beans"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:ender_eye"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:ender_pearl"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:crimson_fungus"));
		registerIngredientsByItemID(new ResourceLocation("minecraft:warped_fungus"));



		// print ingredients list
		MysticAlchemy.LOGGER.info("Valid ingredients: " + validIngredients.size() + " items.");
		for (Item item : validIngredients) {
			ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
			MysticAlchemy.LOGGER.info(key + " => " + I18n.get(item.getDescriptionId()));
		}
    }

    public static void registerIngredientsByTag(ResourceLocation tagResource) {
        ITag<Item> tag = ForgeRegistries.ITEMS.tags().getTag(ForgeRegistries.ITEMS.tags().createTagKey(tagResource));
        if (tag != null) {
            tag.forEach(item -> {
                if (!validIngredients.contains(item)) {
                    validIngredients.add(item);
                }
            });
        }
    }

    public static void registerIngredientsByItemID(ResourceLocation itemID) {
        Item item = ForgeRegistries.ITEMS.getValue(itemID);
        if (item != null && !validIngredients.contains(item)) {
            validIngredients.add(item);
        }
    }

    // --- Static Tick Method for Ticker Compatibility ---
    public static void Tick(Level level, BlockPos pos, BlockState state, CrucibleTile blockEntity) {
        blockEntity.tick();
    }

    // --- Fields for Heat Behavior & Potion State ---
    private float heat = MIN_TEMP;
    private float stir = 0;
    private boolean is_splash = false;
    private boolean is_lingering = false;
    private int duration = 600;

    private HashMap<MobEffect, Float> effectStrengths;
    private RecipeManager recipeManager;
    private Biome myBiome;

    private long targetColor = 12345L;
    private long startColor = 12345L;
    private double infusePct = 1.0f;

    // --- Fields for Brew Modifier System ---
    // Use a LinkedHashMap to preserve the order in which ingredients are added.
    private LinkedHashMap<Item, Integer> effectIngredientCounts = new LinkedHashMap<>();
    private HashMap<Item, ModifierType[]> ingredientModifiersCache = new HashMap<>();
    private boolean effectsGenerated = false;

    // --- Modifier Types ---
    public enum ModifierType {
        ADD_EFFECT,
        REMOVE_EFFECT,
        ADD_DURATION,
        REMOVE_DURATION,
        ADD_LEVEL,
        REMOVE_LEVEL,
        TRANSMUTE,
        MERGE,
        CLEAR
    }

    // --- Weight Definitions (modifiable) ---
    private static final int WEIGHT_ADD_EFFECT = 3;
    private static final int WEIGHT_REMOVE_EFFECT = 1;
    private static final int WEIGHT_ADD_DURATION = 1;
    private static final int WEIGHT_REMOVE_DURATION = 1;
    private static final int WEIGHT_ADD_LEVEL = 1;
    private static final int WEIGHT_REMOVE_LEVEL = 1;
    private static final int WEIGHT_TRANSMUTE = 1;
    private static final int WEIGHT_MERGE = 1;
    private static final int WEIGHT_CLEAR = 1;

    private static final LinkedHashMap<ModifierType, Integer> MODIFIER_WEIGHTS;
    static {
        MODIFIER_WEIGHTS = new LinkedHashMap<>();
        MODIFIER_WEIGHTS.put(ModifierType.ADD_EFFECT, WEIGHT_ADD_EFFECT);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_EFFECT, WEIGHT_REMOVE_EFFECT);
        MODIFIER_WEIGHTS.put(ModifierType.ADD_DURATION, WEIGHT_ADD_DURATION);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_DURATION, WEIGHT_REMOVE_DURATION);
        MODIFIER_WEIGHTS.put(ModifierType.ADD_LEVEL, WEIGHT_ADD_LEVEL);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_LEVEL, WEIGHT_REMOVE_LEVEL);
        MODIFIER_WEIGHTS.put(ModifierType.TRANSMUTE, WEIGHT_TRANSMUTE);
        MODIFIER_WEIGHTS.put(ModifierType.MERGE, WEIGHT_MERGE);
        MODIFIER_WEIGHTS.put(ModifierType.CLEAR, WEIGHT_CLEAR);
    }

    // --- Helper: Weighted Random Modifier Selection ---
    private ModifierType weightedRandomModifier(Random random) {
        int totalWeight = 0;
        for (int weight : MODIFIER_WEIGHTS.values()) {
            totalWeight += weight;
        }
        int r = random.nextInt(totalWeight);
        for (ModifierType mod : MODIFIER_WEIGHTS.keySet()) {
            int weight = MODIFIER_WEIGHTS.get(mod);
            if (r < weight) {
                return mod;
            }
            r -= weight;
        }
        return ModifierType.ADD_EFFECT; // fallback
    }

    // --- Constructor ---
    public CrucibleTile(BlockPos pos, BlockState state) {
        super(TileEntityInit.CRUCIBLE_TILE_TYPE.get(), pos, state);
        effectStrengths = new HashMap<>();
    }

    // --- Updated Print Method ---
    public void printAllIngredientModifiers() {
        MysticAlchemy.LOGGER.info("=== Valid Ingredients Modifier Mapping ===");
        for (Item item : validIngredients) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            ModifierType[] mods = getModifiersForIngredient(item);
            StringBuilder modInfo = new StringBuilder(java.util.Arrays.toString(mods));
            for (int i = 0; i < mods.length; i++) {
                if (mods[i] == ModifierType.ADD_EFFECT) {
                    MobEffect predicted = getPredictedAddEffect(item, i);
                    ResourceLocation effectKey = predicted != null ? ForgeRegistries.MOB_EFFECTS.getKey(predicted) : null;
                    modInfo.append(" (Predicted ADD_EFFECT: " + (effectKey != null ? effectKey.toString() : "None") + ")");
                }
            }
            MysticAlchemy.LOGGER.info(key + " => " + modInfo.toString());
        }
    }

    // --- Helper: getPredictedAddEffect (using sorted list and stable ordinal) ---
    private MobEffect getPredictedAddEffect(Item ingredient, int modIndex) {
        long seed = getWorldSeed();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(ingredient);
        if (key != null) {
            seed ^= key.toString().hashCode();
        }
        seed ^= ModifierType.ADD_EFFECT.ordinal();
        seed ^= (modIndex + 1);
        Random random = new Random(seed);
        ArrayList<MobEffect> effects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
        effects.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
        if (effects.isEmpty()) return null;
        return effects.get(random.nextInt(effects.size()));
    }

    // --- Helper: sendChatMessage ---
    private void sendChatMessage(String message) {
        if (!level.isClientSide) {
            for (Player player : level.players()) {
                player.sendSystemMessage(Component.literal(message));
            }
            MysticAlchemy.LOGGER.info("Chat message sent: " + message);
        }
    }

    // --- Tick and Heat Behavior ---
    private void tick() {
        int waterLevel = this.getBlockState().getValue(BlockCrucible.LEVEL);
        if (level.isClientSide && waterLevel > 0) {
            spawnParticles(waterLevel);
        }
        infusePct = Mth.clamp(infusePct + 0.01f, 0, 1);
        if (level.getGameTime() % UPDATE_RATE != 0) {
            return;
        }
        if (myBiome == null) {
            myBiome = this.level.getBiome(worldPosition).get();
        }
        if (waterLevel == 0) {
            resetPotion();
            return;
        }
        if (!level.isClientSide) {
            tickHeatAndStir(waterLevel);
        }
    }

    private void tickHeatAndStir(int waterLevel) {
        Block below = level.getBlockState(getBlockPos().below()).getBlock();
        float preHeat = heat;
        if (heaters.containsKey(below)) {
            heat = Mth.clamp(heat + heaters.get(below), MIN_TEMP, MAX_TEMP);
        } else {
            heat = Mth.clamp(heat - (1 - myBiome.getBaseTemperature()) * 10, MIN_TEMP, MAX_TEMP);
        }
        if (stir > 0.25f)
            stir = Mth.clamp(stir - 0.02f, 0, 1);
        else
            stir = Mth.clamp(stir - 0.005f, 0, 1);
        if (heat >= BOIL_POINT && stir == 0 && Math.random() < 0.04f) {
            if (waterLevel == 1) {
                level.setBlockAndUpdate(getBlockPos(), BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState());
            } else {
                level.setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockCrucible.LEVEL, waterLevel - 1));
            }
        }
        if (heat != preHeat) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void resetPotion() {
        heat = MIN_TEMP;
        stir = 1.0f;
        is_splash = false;
        is_lingering = false;
        duration = 600;
        effectStrengths.clear();
        effectIngredientCounts.clear();
        ingredientModifiersCache.clear();
        effectsGenerated = false;
        infusePct = 1.0f;
        targetColor = 12345L;
    }

    private void spawnParticles(int waterLevel) {
        if (this.getHeat() > BOIL_POINT) {
            int numBubbles = (int) Math.ceil(5f * ((this.getHeat() - BOIL_POINT) / (this.getMaxHeat() - BOIL_POINT)));
            for (int i = 0; i < numBubbles; ++i) {
                level.addParticle(ParticleTypes.BUBBLE_POP,
                        getBlockPos().getX() + 0.5 - 0.3 + Math.random() * 0.6,
                        getBlockPos().getY() + 0.2 + (0.25f * waterLevel),
                        getBlockPos().getZ() + 0.5 - 0.3 + Math.random() * 0.6,
                        0, is_splash ? 0.125f : 0, 0);
            }
        }
    }

    // --- Brew Modifier System Methods ---
    private void recalculateBrew() {
        MysticAlchemy.LOGGER.info("Recalculating brew...");
        effectStrengths.clear();
        duration = 600;
        // Iterate over the ingredients in the order they were added.
        MysticAlchemy.LOGGER.info("Current ingredient counts: " + effectIngredientCounts);
        
        // First, apply all non-MERGE modifiers in insertion order.
        for (Item ingredient : effectIngredientCounts.keySet()) {
            int count = effectIngredientCounts.get(ingredient);
            ModifierType[] mods = getModifiersForIngredient(ingredient);
            MysticAlchemy.LOGGER.info("Applying non-merge modifiers for " + ForgeRegistries.ITEMS.getKey(ingredient) +
                    " (count " + count + "): " + java.util.Arrays.toString(mods));
            for (int i = 0; i < mods.length; i++) {
                if (mods[i] != ModifierType.MERGE) {
                    applyModifier(mods[i], count, ingredient, i);
                }
            }
        }
        // Then, apply MERGE modifiers in insertion order.
        for (Item ingredient : effectIngredientCounts.keySet()) {
            int count = effectIngredientCounts.get(ingredient);
            ModifierType[] mods = getModifiersForIngredient(ingredient);
            for (int i = 0; i < mods.length; i++) {
                if (mods[i] == ModifierType.MERGE) {
                    MysticAlchemy.LOGGER.info("Applying MERGE modifier from " + ForgeRegistries.ITEMS.getKey(ingredient) +
                            " (count " + count + "): " + mods[i]);
                    applyModifier(mods[i], count, ingredient, i);
                }
            }
        }
        MysticAlchemy.LOGGER.info("Brew state after recalculation: effects=" + effectsToString() + ", duration=" + duration);
    }

    // Helper method to convert the effects map to a readable string.
    private String effectsToString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<MobEffect, Float> entry : effectStrengths.entrySet()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            ResourceLocation effectKey = ForgeRegistries.MOB_EFFECTS.getKey(entry.getKey());
            sb.append(effectKey != null ? effectKey.toString() : entry.getKey().toString());
            sb.append("=");
            sb.append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private void applyModifier(ModifierType mod, int count, Item ingredient, int modIndex) {
        long seed = getWorldSeed();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(ingredient);
        if (key != null) {
            seed ^= key.toString().hashCode();
        }
        seed ^= mod.ordinal();
        seed ^= (modIndex + 1);
        Random random = new Random(seed);
        switch(mod) {
            case ADD_EFFECT: {
                ArrayList<MobEffect> effects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                effects.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                if (effects.isEmpty()) break;
                MobEffect addEff = effects.get(random.nextInt(effects.size()));
                if (!effectStrengths.containsKey(addEff) && effectStrengths.size() >= MAX_EFFECTS) {
                    MysticAlchemy.LOGGER.info("ADD_EFFECT from " + key + " skipped (max effects reached).");
                    break;
                }
                effectStrengths.put(addEff, 1f);
                MysticAlchemy.LOGGER.info("ADD_EFFECT from " + key + " (mod index " + modIndex + ") sets effect " +
                        ForgeRegistries.MOB_EFFECTS.getKey(addEff) + " to level 1");
                break;
            }
            case REMOVE_EFFECT: {
                if (!effectStrengths.isEmpty()) {
                    ArrayList<MobEffect> curr = new ArrayList<>(effectStrengths.keySet());
                    MobEffect remEff = curr.get(random.nextInt(curr.size()));
                    float currLevel = effectStrengths.get(remEff);
                    float newLevel = currLevel - count;
                    if (newLevel < 1) {
                        effectStrengths.remove(remEff);
                        MysticAlchemy.LOGGER.info("REMOVE_EFFECT from " + key + " removed effect " +
                                ForgeRegistries.MOB_EFFECTS.getKey(remEff));
                    } else {
                        effectStrengths.put(remEff, newLevel);
                        MysticAlchemy.LOGGER.info("REMOVE_EFFECT from " + key + " reduced " +
                                ForgeRegistries.MOB_EFFECTS.getKey(remEff) + " to level " + newLevel);
                    }
                }
                break;
            }
            case ADD_DURATION: {
                int addDur = 200 + random.nextInt(1200 - 200 + 1);
                addDur *= count;
                duration += addDur;
                if (duration > MAX_DURATION) {
                    MysticAlchemy.LOGGER.info("ADD_DURATION from " + key + " added " + (MAX_DURATION - (duration - addDur)) +
                            " ticks (max reached)");
                    duration = MAX_DURATION;
                } else {
                    MysticAlchemy.LOGGER.info("ADD_DURATION from " + key + " added " + addDur +
                            " ticks; new duration: " + duration);
                }
                break;
            }
            case REMOVE_DURATION: {
                int remDur = 200 + random.nextInt(1200 - 200 + 1);
                remDur *= count;
                duration = Math.max(0, duration - remDur);
                MysticAlchemy.LOGGER.info("REMOVE_DURATION from " + key + " removed " + remDur +
                        " ticks; new duration: " + duration);
                break;
            }
            case ADD_LEVEL: {
                for (MobEffect e : new ArrayList<>(effectStrengths.keySet())) {
                    float lvl = effectStrengths.get(e) + count;
                    effectStrengths.put(e, Math.min(lvl, MAX_MAGNITUDE));
                    MysticAlchemy.LOGGER.info("ADD_LEVEL from " + key + " increased " +
                            ForgeRegistries.MOB_EFFECTS.getKey(e) + " to level " + lvl);
                }
                break;
            }
            case REMOVE_LEVEL: {
                for (MobEffect e : new ArrayList<>(effectStrengths.keySet())) {
                    float lvl = effectStrengths.get(e) - count;
                    if (lvl < 1) {
                        effectStrengths.remove(e);
                        MysticAlchemy.LOGGER.info("REMOVE_LEVEL from " + key + " removed effect " +
                                ForgeRegistries.MOB_EFFECTS.getKey(e));
                    } else {
                        effectStrengths.put(e, lvl);
                        MysticAlchemy.LOGGER.info("REMOVE_LEVEL from " + key + " decreased " +
                                ForgeRegistries.MOB_EFFECTS.getKey(e) + " to level " + lvl);
                    }
                }
                break;
            }
            case TRANSMUTE: {
                HashMap<MobEffect, Float> newMap = new HashMap<>();
                ArrayList<MobEffect> avail = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                avail.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                for (MobEffect e : effectStrengths.keySet()) {
                    if (avail.isEmpty()) break;
                    MobEffect newEff = avail.get(random.nextInt(avail.size()));
                    float lvl = effectStrengths.get(e);
                    newMap.put(newEff, lvl);
                    MysticAlchemy.LOGGER.info("TRANSMUTE from " + key + " changed " +
                            ForgeRegistries.MOB_EFFECTS.getKey(e) + " to " + ForgeRegistries.MOB_EFFECTS.getKey(newEff));
                }
                effectStrengths.clear();
                effectStrengths.putAll(newMap);
                break;
            }
            case MERGE: {
                int numEffects = effectStrengths.size();
                if (numEffects == 0) break;
                float totalLevel = 0;
                for (Float lvl : effectStrengths.values()) {
                    totalLevel += lvl;
                }
                effectStrengths.clear();
                ArrayList<MobEffect> availMerge = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                availMerge.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                if (!availMerge.isEmpty()) {
                    MobEffect merged = availMerge.get(random.nextInt(availMerge.size()));
                    effectStrengths.put(merged, totalLevel);
                    int combinedDuration = Math.min(duration * numEffects, MAX_DURATION);
                    duration = combinedDuration;
                    MysticAlchemy.LOGGER.info("MERGE from " + key + " merged " + numEffects +
                            " effects into " + ForgeRegistries.MOB_EFFECTS.getKey(merged) +
                            " with combined level " + totalLevel + " and combined duration " + duration);
                }
                break;
            }
            case CLEAR: {
                effectStrengths.clear();
                duration = 600;
                MysticAlchemy.LOGGER.info("CLEAR from " + key + " cleared the brew.");
                break;
            }
        }
    }

    private long getWorldSeed() {
        if (level == null) return 0;
        long seed = 0;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            seed = serverLevel.getSeed();
        }
        Block blockBelow = level.getBlockState(getBlockPos().below()).getBlock();
        if (blockBelow == Blocks.SOUL_CAMPFIRE) {
            seed ^= 0xFACEB00CL;
        }
        return seed;
    }

    // --- Helper: getModifiersForIngredient ---
    private ModifierType[] getModifiersForIngredient(Item ingredient) {
        if (ingredientModifiersCache.containsKey(ingredient)) {
            return ingredientModifiersCache.get(ingredient);
        }
        long baseSeed = getWorldSeed();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(ingredient);
        if (key != null) {
            baseSeed ^= key.toString().hashCode();
        }
        Random baseRandom = new Random(baseSeed);
        int num = baseRandom.nextInt(3) + 1;
        ModifierType[] mods = new ModifierType[num];
        for (int i = 0; i < num; i++) {
            long seed = baseSeed;
            seed ^= (i + 1);
            Random random = new Random(seed);
            mods[i] = weightedRandomModifier(random);
        }
        ArrayList<ModifierType> modList = new ArrayList<>();
        for (ModifierType mod : mods) {
            modList.add(mod);
        }
        // If CLEAR is present, override all.
        if (modList.contains(ModifierType.CLEAR)) {
            modList.clear();
            modList.add(ModifierType.CLEAR);
        }
        // Remove conflicting pairs.
        if (modList.contains(ModifierType.ADD_EFFECT) && modList.contains(ModifierType.REMOVE_EFFECT)) {
            modList.remove(ModifierType.REMOVE_EFFECT);
        }
        if (modList.contains(ModifierType.ADD_DURATION) && modList.contains(ModifierType.REMOVE_DURATION)) {
            modList.remove(ModifierType.REMOVE_DURATION);
        }
        if (modList.contains(ModifierType.ADD_LEVEL) && modList.contains(ModifierType.REMOVE_LEVEL)) {
            modList.remove(ModifierType.REMOVE_LEVEL);
        }
        ModifierType[] finalMods = modList.toArray(new ModifierType[0]);
        ingredientModifiersCache.put(ingredient, finalMods);
        return finalMods;
    }

    // --- Ingredient Addition ---
    public boolean tryAddIngredient(ItemStack stack) {
        MysticAlchemy.LOGGER.info("tryAddIngredient: Attempting to add " + stack.getCount() +
                " of " + ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (infusePct != 1.0f || heat < BOIL_POINT) {
            MysticAlchemy.LOGGER.info("tryAddIngredient: Conditions not met (infusePct=" +
                    infusePct + ", heat=" + heat + ")");
            return false;
        }
        if (recipeManager == null) {
            recipeManager = level.getRecipeManager();
        }
        Optional<PotionIngredientRecipe> recipeOpt = recipeManager
                .getRecipesFor(RecipeInit.POTION_RECIPE_TYPE.get(), createDummyCraftingInventory(stack), level)
                .stream().findFirst();
        if (recipeOpt.isPresent()) {
            PotionIngredientRecipe recipe = recipeOpt.get();
            if (recipe.getMakesLingering())
                is_lingering = true;
            if (recipe.getMakesSplash())
                is_splash = true;
            if (recipe.getDurationAdded() > 0)
                duration += recipe.getDurationAdded() * stack.getCount();
            MysticAlchemy.LOGGER.info("tryAddIngredient: Recipe modified brew: lingering=" +
                    is_lingering + ", splash=" + is_splash + ", duration=" + duration);
        }
        if (!validIngredients.contains(stack.getItem())) {
            MysticAlchemy.LOGGER.info("tryAddIngredient: Invalid ingredient " +
                    ForgeRegistries.ITEMS.getKey(stack.getItem()));
            applyInvalidIngredientOutcome();
            return true;
        }
        Item ingredientItem = stack.getItem();
        int newCount = effectIngredientCounts.getOrDefault(ingredientItem, 0) + stack.getCount();
        effectIngredientCounts.put(ingredientItem, newCount);
        MysticAlchemy.LOGGER.info("tryAddIngredient: Updated ingredient counts: " + effectIngredientCounts);
        int total = effectIngredientCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total >= 3) {
            MysticAlchemy.LOGGER.info("tryAddIngredient: Total ingredients (" + total + ") reached. Recalculating brew.");
            recalculateBrew();
            effectsGenerated = true;
        }
        heat = Mth.clamp(heat - ITEM_HEAT_LOSS * stack.getCount(), MIN_TEMP, MAX_TEMP);
        MysticAlchemy.LOGGER.info("tryAddIngredient: New heat = " + heat);
        recalculatePotionColor();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        MysticAlchemy.LOGGER.info("Final brew state: effects=" + effectsToString() +
                ", duration=" + duration + ", color=" + getPotionColor());
        return true;
    }

    private void recalculatePotionColor() {
        HashMap<MobEffect, Float> prominents = getProminentEffects();
        if (prominents.size() == 0) {
            targetColor = WATER_COLOR;
            infusePct = 1.0f;
            return;
        }
        long color = 0;
        for (MobEffect e : prominents.keySet()) {
            color += e.getColor();
        }
        color /= prominents.size();
        if (targetColor != color) {
            startColor = getPotionColor();
            targetColor = color;
            infusePct = 0.0f;
        }
    }

    private void applyInvalidIngredientOutcome() {
        MysticAlchemy.LOGGER.info("applyInvalidIngredientOutcome: Invalid ingredient or error.");
        effectStrengths.clear();
        MobEffect poison = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft:poison"));
        if (poison != null) {
            effectStrengths.put(poison, (float) MAX_MAGNITUDE);
        }
        targetColor = 0;
        infusePct = 1.0f;
        effectIngredientCounts.clear();
        ingredientModifiersCache.clear();
        sendChatMessage("Invalid ingredient detected! Brew overridden to Poison.");
    }

    private CraftingContainer createDummyCraftingInventory(ItemStack stack) {
        CraftingContainer craftinginventory = new TransientCraftingContainer(new AbstractContainerMenu((net.minecraft.world.inventory.MenuType<?>) null, -1) {
            @Override
            public boolean stillValid(Player playerIn) {
                return false;
            }
            @Override
            public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
                return ItemStack.EMPTY;
            }
        }, 1, 1);
        craftinginventory.setItem(0, stack);
        return craftinginventory;
    }

    public HashMap<MobEffect, Float> getProminentEffects() {
        HashMap<MobEffect, Float> effects = new HashMap<>();
        effectStrengths.forEach((e, f) -> {
            if (f >= 1 && !BrewingConfig.isEffectDisabled(ForgeRegistries.MOB_EFFECTS.getKey(e)))
                effects.put(e, f);
        });
        return effects;
    }

    @SuppressWarnings("unchecked")
    public HashMap<MobEffect, Float> getAllEffects() {
        return (HashMap<MobEffect, Float>) effectStrengths.clone();
    }

    @Override
    protected void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.putFloat("heat", this.heat);
        compound.putFloat("stir", this.stir);
        compound.putBoolean("splash", this.is_splash);
        compound.putBoolean("lingering", this.is_lingering);
        compound.putInt("duration", this.duration);
        compound.putInt("numEffects", this.effectStrengths.size());
        int count = 0;
        for (MobEffect e : this.effectStrengths.keySet()) {
            compound.putString("effect" + count, ForgeRegistries.MOB_EFFECTS.getKey(e).toString());
            compound.putFloat("effectstr" + count, this.effectStrengths.get(e));
            count++;
        }
    }

    @Override
    public void load(CompoundTag data) {
        super.load(data);
        if (data.contains("heat"))
            this.heat = data.getFloat("heat");
        if (data.contains("stir"))
            this.stir = data.getFloat("stir");
        if (data.contains("splash"))
            this.is_splash = data.getBoolean("splash");
        if (data.contains("lingering"))
            this.is_lingering = data.getBoolean("lingering");
        if (data.contains("duration"))
            this.duration = data.getInt("duration");
        if (data.contains("numEffects")) {
            int count = data.getInt("numEffects");
            for (int i = 0; i < count; ++i) {
                if (data.contains("effect" + i) && data.contains("effectstr" + i)) {
                    MobEffect e = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(data.getString("effect" + i)));
                    if (e != null) {
                        effectStrengths.put(e, data.getFloat("effectstr" + i));
                    }
                }
            }
        }
        recalculatePotionColor();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        load(pkt.getTag());
    }

    // --- Getters / Setters ---
    public float getHeat() {
        return heat;
    }

    public float getMaxHeat() {
        return MAX_TEMP;
    }

    public void stir() {
        stir = 1.0f;
    }

    public float getStir() {
        return stir;
    }

    public boolean isSplash() {
        return is_splash;
    }

    public void setSplash(boolean splash) {
        this.is_splash = splash;
    }

    public boolean isLingering() {
        return is_lingering;
    }

    public void setLingering(boolean lingering) {
        this.is_lingering = lingering;
    }

    public int getDuration() {
        return this.duration;
    }

    public void addDuration(int duration) {
        this.duration += duration;
    }

    public boolean isPotion() {
        for (MobEffect e : effectStrengths.keySet()) {
            if (effectStrengths.get(e) >= 1.0f)
                return true;
        }
        return false;
    }

    public long getPotionColor() {
        if (infusePct == 1.0f)
            return targetColor;
        int[] rgb_start = new int[] {
            (int) (startColor >> 16 & 0xff),
            (int) (startColor >> 8 & 0xff),
            (int) (startColor & 0xff)
        };
        int[] rgb_target = new int[] {
            (int) (targetColor >> 16 & 0xff),
            (int) (targetColor >> 8 & 0xff),
            (int) (targetColor & 0xff)
        };
        int[] lerp_color = new int[3];
        for (int i = 0; i < 3; ++i)
            lerp_color[i] = rgb_start[i] + (int)((rgb_target[i] - rgb_start[i]) * infusePct);
        long outputColor = 0;
        outputColor += lerp_color[0] << 16;
        outputColor += lerp_color[1] << 8;
        outputColor += lerp_color[2];
        return outputColor;
    }
}
