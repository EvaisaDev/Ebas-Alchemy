package com.ebalchemy.crucible;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import com.ebalchemy.EbAlchemy;
import com.ebalchemy.config.BrewingConfig;
import com.ebalchemy.init.BlockInit;
import com.ebalchemy.init.RecipeInit;
import com.ebalchemy.init.TileEntityInit;
import com.ebalchemy.recipe.PotionIngredientRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;

/**
 * Tile entity that holds brew data, heat logic, effects, etc. 
 */
public class CrucibleTile extends BlockEntity {

    // == Constants ==
    public static final float MIN_TEMP = 0f;
    public static final float MAX_TEMP = 200f;
    public static final float BOIL_POINT = 100f;
    private static final int UPDATE_RATE = 10;
    private static final int MAX_EFFECTS = 6;
    private static final float ITEM_HEAT_LOSS = 25f;
    private static final int MAX_MAGNITUDE = 3;
    private static final int MAX_DURATION = 9600; // 8 minutes
    private static final long WATER_COLOR = 0x3F76E4L;

    // Simple map of known heaters
    private static HashMap<Block, Float> heaters;
    // We keep track of "valid" ingredients to avoid letting random items ruin the brew
    public static final ArrayList<Item> validIngredients = new ArrayList<>();

    static {
        heaters = new HashMap<>();
        heaters.put(Blocks.CAMPFIRE,       12.0f);
        heaters.put(Blocks.FIRE,           18.0f);
        heaters.put(Blocks.LAVA,           30.0f);
        heaters.put(Blocks.SOUL_CAMPFIRE,  30.0f);
        heaters.put(Blocks.ICE,           - 2.0f);

        // Examples of registering "valid" items via tags or direct IDs
        registerIngredientsByTag(new ResourceLocation("c:animal_foods"));
        registerIngredientsByTag(new ResourceLocation("c:foods"));
        registerIngredientsByTag(new ResourceLocation("c:crops"));
        // ... etc. Add or remove as you wish ...

        // Direct item registrations
        registerIngredientsByItemID(new ResourceLocation("minecraft:amethyst_shard"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:sweet_berries"));
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

    // Standard Ticker
    public static void Tick(Level level, BlockPos pos, BlockState state, CrucibleTile blockEntity) {
        blockEntity.tick();
    }

    // == Fields ==
    private float heat = MIN_TEMP;
    private float stir = 0f;
    private boolean is_splash = false;
    private boolean is_lingering = false;
    private int duration = 600;  // default: 30 seconds (600 ticks)

    // Effects: effect -> float-level
    private HashMap<MobEffect, Float> effectStrengths = new HashMap<>();
    private RecipeManager recipeManager;
    private Biome myBiome;

    // Color interpolation
    private long targetColor = 12345L;
    private long startColor = 12345L;
    private double infusePct = 1.0f;

    // Ingredient / brew modifier system
    private LinkedHashMap<Item, Integer> effectIngredientCounts = new LinkedHashMap<>();
    private HashMap<Item, ModifierType[]> ingredientModifiersCache = new HashMap<>();
    private boolean effectsGenerated = false;

    // Track once-per-entity "unique brew modifier"
    private LinkedHashSet<UUID> entitiesWithModifierApplied = new LinkedHashSet<>();
    private HashMap<UUID, Long> lastCollisionTickMap = new HashMap<>();

    // Types of random modifiers an ingredient can give
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

    private static final int WEIGHT_ADD_EFFECT       = 3;
    private static final int WEIGHT_REMOVE_EFFECT    = 1;
    private static final int WEIGHT_ADD_DURATION     = 1;
    private static final int WEIGHT_REMOVE_DURATION  = 1;
    private static final int WEIGHT_ADD_LEVEL        = 1;
    private static final int WEIGHT_REMOVE_LEVEL     = 1;
    private static final int WEIGHT_TRANSMUTE        = 1;
    private static final int WEIGHT_MERGE            = 1;
    private static final int WEIGHT_CLEAR            = 1;

    private static final LinkedHashMap<ModifierType, Integer> MODIFIER_WEIGHTS;
    static {
        MODIFIER_WEIGHTS = new LinkedHashMap<>();
        MODIFIER_WEIGHTS.put(ModifierType.ADD_EFFECT,       WEIGHT_ADD_EFFECT);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_EFFECT,    WEIGHT_REMOVE_EFFECT);
        MODIFIER_WEIGHTS.put(ModifierType.ADD_DURATION,     WEIGHT_ADD_DURATION);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_DURATION,  WEIGHT_REMOVE_DURATION);
        MODIFIER_WEIGHTS.put(ModifierType.ADD_LEVEL,        WEIGHT_ADD_LEVEL);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_LEVEL,     WEIGHT_REMOVE_LEVEL);
        MODIFIER_WEIGHTS.put(ModifierType.TRANSMUTE,        WEIGHT_TRANSMUTE);
        MODIFIER_WEIGHTS.put(ModifierType.MERGE,            WEIGHT_MERGE);
        MODIFIER_WEIGHTS.put(ModifierType.CLEAR,            WEIGHT_CLEAR);
    }

    public CrucibleTile(BlockPos pos, BlockState state) {
        super(TileEntityInit.CRUCIBLE_TILE_TYPE.get(), pos, state);
    }

    private void tick() {
        int waterLevel = getBlockState().getValue(BlockCrucible.LEVEL);

        // Client side: bubble particles
        if (level.isClientSide && waterLevel > 0) {
            spawnParticles(waterLevel);
        }
        // Gradually "infuse" color
        infusePct = Mth.clamp(infusePct + 0.01f, 0f, 1f);

        // Perform certain logic every UPDATE_RATE ticks
        if (level.getGameTime() % UPDATE_RATE != 0) {
            return;
        }

        // If empty
        if (waterLevel == 0) {
            resetPotion();
            return;
        }
        if (myBiome == null) {
            myBiome = level.getBiome(worldPosition).get();
        }

        // Server side: heat logic
        if (!level.isClientSide) {
            tickHeatAndStir(waterLevel);
        }
    }

    private void tickHeatAndStir(int waterLevel) {
        Block below = level.getBlockState(getBlockPos().below()).getBlock();
        float oldHeat = heat;

        if (heaters.containsKey(below)) {
            heat = Mth.clamp(heat + heaters.get(below), MIN_TEMP, MAX_TEMP);
        } else {
            // Slight cooling
            heat = Mth.clamp(heat - (1 - myBiome.getBaseTemperature()) * 10, MIN_TEMP, MAX_TEMP);
        }

        // Stir decays over time
        if (stir > 0.25f) {
            stir = Mth.clamp(stir - 0.02f, 0, 1);
        } else {
            stir = Mth.clamp(stir - 0.005f, 0, 1);
        }

        // If boiling and not stirred => chance to boil away
        if (heat >= BOIL_POINT && stir == 0 && Math.random() < 0.04f) {
            if (waterLevel <= 1) {
                level.setBlockAndUpdate(getBlockPos(), BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState());
            } else {
                level.setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockCrucible.LEVEL, waterLevel - 1));
            }
        }

        // If heat changed, notify client
        if (heat != oldHeat) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void spawnParticles(int waterLevel) {
        if (heat > BOIL_POINT) {
            int bubbleCount = (int) Math.ceil(5f * ((heat - BOIL_POINT) / (MAX_TEMP - BOIL_POINT)));
            for (int i = 0; i < bubbleCount; i++) {
                level.addParticle(ParticleTypes.BUBBLE_POP,
                        getBlockPos().getX() + 0.5 - 0.3 + Math.random() * 0.6,
                        getBlockPos().getY() + 0.2 + (0.25f * waterLevel),
                        getBlockPos().getZ() + 0.5 - 0.3 + Math.random() * 0.6,
                        0, is_splash ? 0.125f : 0, 0);
            }
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
        startColor = 12345L;

        entitiesWithModifierApplied.clear();
        lastCollisionTickMap.clear();
    }

    public void handleLivingEntityCollision(LivingEntity entity) {
        long currTick = level.getGameTime();
        UUID uuid = entity.getUUID();
        long lastTick = lastCollisionTickMap.getOrDefault(uuid, 0L);

        // Only apply every 40 ticks (~2 seconds)
        if (currTick - lastTick >= 40) {
            // 1) Always apply brew’s effects
            for (Map.Entry<MobEffect, Float> e : effectStrengths.entrySet()) {
                MobEffect eff = e.getKey();
                float fLvl = e.getValue();
                int amplifier = Math.max(0, (int) fLvl - 1);
                MobEffectInstance inst = new MobEffectInstance(eff, duration, amplifier, false, true);
                entity.addEffect(inst);
            }

            // 2) If boiling => do damage + unique brew mod
            if (heat >= BOIL_POINT) {
                entity.hurt(level.damageSources().magic(), 2.0F);

                if (!entitiesWithModifierApplied.contains(uuid)) {
                    // Attempt one "unique" random mod
                    ModifierType mod = weightedRandomModifier(new Random(uuid.hashCode()));
                    Item dummy = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:cauldron"));
                    if (dummy != null) {
                        applyModifier(mod, 1, dummy, 0);
                        entitiesWithModifierApplied.add(uuid);
                    }
                }
            }
            lastCollisionTickMap.put(uuid, currTick);
        }
    }

    private ModifierType weightedRandomModifier(Random random) {
        int total = 0;
        for (int w : MODIFIER_WEIGHTS.values()) {
            total += w;
        }
        int r = random.nextInt(total);
        for (ModifierType mod : MODIFIER_WEIGHTS.keySet()) {
            int w = MODIFIER_WEIGHTS.get(mod);
            if (r < w) {
                return mod;
            }
            r -= w;
        }
        return ModifierType.ADD_EFFECT; // fallback
    }

    private ModifierType[] getModifiersForIngredient(Item ingredient) {
        // If we have a cached set, use it
        if (ingredientModifiersCache.containsKey(ingredient)) {
            return ingredientModifiersCache.get(ingredient);
        }

        long baseSeed = getWorldSeed();
        ResourceLocation ingrKey = ForgeRegistries.ITEMS.getKey(ingredient);
        if (ingrKey != null) {
            baseSeed ^= ingrKey.toString().hashCode();
        }
        Random baseRandom = new Random(baseSeed);

        // We'll create 1-3 random modifiers
        int num = baseRandom.nextInt(3) + 1;
        ModifierType[] rawMods = new ModifierType[num];
        for (int i = 0; i < num; i++) {
            long seed = baseSeed ^ (i + 1);
            Random r2 = new Random(seed);
            rawMods[i] = weightedRandomModifier(r2);
        }

        // Some conflict resolution
        ArrayList<ModifierType> modList = new ArrayList<>();
        for (ModifierType m : rawMods) {
            modList.add(m);
        }
        // CLEAR overrides everything else
        if (modList.contains(ModifierType.CLEAR)) {
            modList.clear();
            modList.add(ModifierType.CLEAR);
        }
        // Remove direct conflicts
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

    private void applyModifier(ModifierType mod, int count, Item ingredient, int modIndex) {
        long seed = getWorldSeed();
        ResourceLocation ingrKey = ForgeRegistries.ITEMS.getKey(ingredient);
        if (ingrKey != null) {
            seed ^= ingrKey.toString().hashCode();
        }
        seed ^= mod.ordinal();
        seed ^= (modIndex + 1);

        Random random = new Random(seed);

        switch(mod) {
            case ADD_EFFECT -> {
                var allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                if (!allEff.isEmpty()) {
                    MobEffect chosen = allEff.get(random.nextInt(allEff.size()));
                    // Only add if we have room or if it's not already there
                    if (!effectStrengths.containsKey(chosen) && effectStrengths.size() >= MAX_EFFECTS) {
                        // skip if at max
                        break;
                    }
                    effectStrengths.put(chosen, 1f);
                }
            }
            case REMOVE_EFFECT -> {
                if (!effectStrengths.isEmpty()) {
                    var currList = new ArrayList<>(effectStrengths.keySet());
                    MobEffect rem = currList.get(random.nextInt(currList.size()));
                    float oldLvl = effectStrengths.get(rem);
                    float newLvl = oldLvl - count;
                    if (newLvl < 1) {
                        effectStrengths.remove(rem);
                    } else {
                        effectStrengths.put(rem, newLvl);
                    }
                }
            }
            case ADD_DURATION -> {
                int plus = 200 + random.nextInt(1001); // random 200..1200
                plus *= count;
                duration = Math.min(duration + plus, MAX_DURATION);
            }
            case REMOVE_DURATION -> {
                int minus = 200 + random.nextInt(1001);
                minus *= count;
                duration = Math.max(0, duration - minus);
            }
            case ADD_LEVEL -> {
                var effs = new ArrayList<>(effectStrengths.keySet());
                for (MobEffect me : effs) {
                    float oldLvl = effectStrengths.get(me);
                    float newLvl = oldLvl + count;
                    effectStrengths.put(me, Math.min(newLvl, MAX_MAGNITUDE));
                }
            }
            case REMOVE_LEVEL -> {
                var effs = new ArrayList<>(effectStrengths.keySet());
                for (MobEffect me : effs) {
                    float oldLvl = effectStrengths.get(me);
                    float newLvl = oldLvl - count;
                    if (newLvl < 1) {
                        effectStrengths.remove(me);
                    } else {
                        effectStrengths.put(me, newLvl);
                    }
                }
            }
            case TRANSMUTE -> {
                // Change each effect to a random different effect
                HashMap<MobEffect, Float> newMap = new HashMap<>();
                var allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                for (var oldE : effectStrengths.keySet()) {
                    if (allEff.isEmpty()) break;
                    MobEffect newE = allEff.get(random.nextInt(allEff.size()));
                    float lvl = effectStrengths.get(oldE);
                    newMap.put(newE, lvl);
                }
                effectStrengths.clear();
                effectStrengths.putAll(newMap);
            }
            case MERGE -> {
                // Merge all effects into 1 random effect
                int n = effectStrengths.size();
                if (n == 0) break;
                float totalLvl = 0f;
                for (float f : effectStrengths.values()) {
                    totalLvl += f;
                }
                effectStrengths.clear();

                var allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                if (!allEff.isEmpty()) {
                    MobEffect merged = allEff.get(random.nextInt(allEff.size()));
                    effectStrengths.put(merged, totalLvl);
                    // Multiply duration by number of effects (capped)
                    duration = Math.min(duration * n, MAX_DURATION);
                }
            }
            case CLEAR -> {
                effectStrengths.clear();
                duration = 600;
            }
        }
    }

    private long getWorldSeed() {
        if (level == null) return 0;
        long seedVal = 0;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            seedVal = serverLevel.getSeed();
        }
        // If the block below is a soul campfire, alter the seed
        Block below = level.getBlockState(getBlockPos().below()).getBlock();
        if (below == Blocks.SOUL_CAMPFIRE) {
            seedVal ^= 0xFACEB00CL;
        }
        return seedVal;
    }

    /**
     * Attempt to add ingredient items.
     * Must be fully color-infused and at/above boiling temp.
     */
    public boolean tryAddIngredient(ItemStack stack) {
        EbAlchemy.LOGGER.info("tryAddIngredient: item={} count={}", stack.getItem(), stack.getCount());

        // Must be fully color-infused & boiling
        if (infusePct != 1.0f || heat < BOIL_POINT) {
            EbAlchemy.LOGGER.info("  => Not ready (infusePct={}, heat={})", infusePct, heat);
            return false;
        }
        if (recipeManager == null) {
            recipeManager = level.getRecipeManager();
        }

        boolean valid = false;
        // Check custom recipes
        Optional<PotionIngredientRecipe> recipeOpt = recipeManager
            .getRecipesFor(RecipeInit.POTION_RECIPE_TYPE.get(), createDummyCraftingInventory(stack), level)
            .stream().findFirst();
        if (recipeOpt.isPresent()) {
            PotionIngredientRecipe rec = recipeOpt.get();
            if (rec.getMakesLingering()) {
            	is_lingering = true;
            	EbAlchemy.LOGGER.info("Added lingering!");
            }
            if (rec.getMakesSplash()) {
            	is_splash = true;
            	EbAlchemy.LOGGER.info("Added splash!");
            }
            if (rec.getDurationAdded() > 0) {
                duration += rec.getDurationAdded() * stack.getCount();
                if (duration > MAX_DURATION) {
                    duration = MAX_DURATION;
                }
                EbAlchemy.LOGGER.info("Added duration!");
            }
            valid = true;
        }

        
        // If it's not in our valid ingredients => "bad brew"
        if (!valid && !validIngredients.contains(stack.getItem())) {
            applyInvalidIngredientOutcome();
            return true;
        }

        // Otherwise, add to our ingredient count
        int newCount = effectIngredientCounts.getOrDefault(stack.getItem(), 0) + stack.getCount();
        effectIngredientCounts.put(stack.getItem(), newCount);

        // If total >= 3 => recalc brew
        int totalIngr = effectIngredientCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalIngr >= 3) {
            recalculateBrew();
            effectsGenerated = true;
        }

        // Lower heat for each item
        heat = Mth.clamp(heat - ITEM_HEAT_LOSS * stack.getCount(), MIN_TEMP, MAX_TEMP);

        recalculatePotionColor();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        return true;
    }

    private CraftingContainer createDummyCraftingInventory(ItemStack stack) {
        TransientCraftingContainer c = new TransientCraftingContainer(
            new AbstractContainerMenu(null, -1) {
                @Override
                public boolean stillValid(Player playerIn) { return false; }
                @Override
                public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
                    return ItemStack.EMPTY;
                }
            },
            1, 1
        );
        c.setItem(0, stack);
        return c;
    }

    /**
     * Rebuilds the brew from scratch based on how many times each ingredient was added.
     */
    private void recalculateBrew() {
        EbAlchemy.LOGGER.info("Recalculating brew...");

        // 1) Hard reset
        effectStrengths.clear();
        duration = 600;
        is_splash = false;
        is_lingering = false;

        // 2) For each ingredient in the pot...
        for (var entry : effectIngredientCounts.entrySet()) {
            Item ingr = entry.getKey();
            int count = entry.getValue();

            // (a) Check custom recipe
            Optional<PotionIngredientRecipe> recipeOpt = recipeManager
                .getRecipesFor(
                    RecipeInit.POTION_RECIPE_TYPE.get(),
                    createDummyCraftingInventory(new ItemStack(ingr, count)),
                    level
                )
                .stream().findFirst();
            if (recipeOpt.isPresent()) {
                PotionIngredientRecipe rec = recipeOpt.get();
                if (rec.getMakesLingering()) {
                    is_lingering = true;
                }
                if (rec.getMakesSplash()) {
                    is_splash = true;
                }
                if (rec.getDurationAdded() > 0) {
                    duration += rec.getDurationAdded() * count;
                    if (duration > MAX_DURATION) {
                        duration = MAX_DURATION;
                    }
                }
            }

            // (b) Apply random modifiers
            ModifierType[] mods = getModifiersForIngredient(ingr);
            for (int i = 0; i < mods.length; i++) {
                // do normal logic
                if (mods[i] != ModifierType.MERGE) {
                    applyModifier(mods[i], count, ingr, i);
                }
            }
            // Merges last
            for (int i = 0; i < mods.length; i++) {
                if (mods[i] == ModifierType.MERGE) {
                    applyModifier(mods[i], count, ingr, i);
                }
            }
        }

        // 3) Done
        EbAlchemy.LOGGER.info("Brew after recalc: {}", effectsToString());
        recalculatePotionColor();
    }


    private String effectsToString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : effectStrengths.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            var effKey = ForgeRegistries.MOB_EFFECTS.getKey(e.getKey());
            sb.append(effKey != null ? effKey.toString() : e.getKey().toString());
            sb.append("=");
            sb.append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Called when we add an invalid item. Forces the brew to become "poisoned" or otherwise messed up.
     */
    private void applyInvalidIngredientOutcome() {
        EbAlchemy.LOGGER.info("applyInvalidIngredientOutcome: brew fails => poison, etc.");
        effectStrengths.clear();

        // Example: turn everything into a poison effect
        MobEffect poison = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft:poison"));
        if (poison != null) {
            effectStrengths.put(poison, (float)MAX_MAGNITUDE);
        }
        targetColor = 0;
        infusePct = 1.0f;
        effectIngredientCounts.clear();
        ingredientModifiersCache.clear();

        recalculatePotionColor();
        if (!level.isClientSide) {
            level.playSound(null, getBlockPos(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (level instanceof net.minecraft.server.level.ServerLevel slevel) {
                for (int i = 0; i < 20; i++) {
                    double ox = getBlockPos().getX() + 0.5 + (slevel.random.nextDouble() - 0.5) * 0.5;
                    double oy = getBlockPos().getY() + 1.2;
                    double oz = getBlockPos().getZ() + 0.5 + (slevel.random.nextDouble() - 0.5) * 0.5;
                    slevel.sendParticles(ParticleTypes.WITCH, ox, oy, oz, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    /**
     * Dilute an existing brew by halving duration and lowering levels.
     */
    public void diluteBrew() {
        duration /= 2;
        if (duration < 1) {
            duration = 1;
        }
        HashMap<MobEffect, Float> newMap = new HashMap<>();
        for (var e : effectStrengths.entrySet()) {
            float newVal = e.getValue() - 1.0F;
            if (newVal >= 1.0F) {
                newMap.put(e.getKey(), newVal);
            }
        }
        effectStrengths.clear();
        effectStrengths.putAll(newMap);

        if (effectStrengths.isEmpty()) {
            resetPotion();
        } else {
            recalculatePotionColor();
        }
    }

    /**
     * Return all "prominent" (level >= 1.0) effects, ignoring ones that mod config says to disable.
     */
    public HashMap<MobEffect, Float> getProminentEffects() {
        HashMap<MobEffect, Float> map = new HashMap<>();
        for (var e : effectStrengths.entrySet()) {
            if (e.getValue() >= 1f) {
                ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(e.getKey());
                if (!BrewingConfig.isEffectDisabled(rl)) {
                    map.put(e.getKey(), e.getValue());
                }
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    //   SAVE / LOAD
    // ------------------------------------------------------------------
    @Override
    protected void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.putFloat("heat", heat);
        compound.putFloat("stir", stir);
        compound.putBoolean("splash", is_splash);
        compound.putBoolean("lingering", is_lingering);
        compound.putInt("duration", duration);

        compound.putInt("numEffects", effectStrengths.size());
        int idx = 0;
        for (var entry : effectStrengths.entrySet()) {
            var effRL = ForgeRegistries.MOB_EFFECTS.getKey(entry.getKey());
            if (effRL != null) {
                compound.putString("effect" + idx, effRL.toString());
                compound.putFloat("effectstr" + idx, entry.getValue());
                idx++;
            }
        }
    }

    @Override
    public void load(CompoundTag data) {
        super.load(data);
        if (data.contains("heat")) {
            heat = data.getFloat("heat");
        }
        if (data.contains("stir")) {
            stir = data.getFloat("stir");
        }
        if (data.contains("splash")) {
            is_splash = data.getBoolean("splash");
        }
        if (data.contains("lingering")) {
            is_lingering = data.getBoolean("lingering");
        }
        if (data.contains("duration")) {
            duration = data.getInt("duration");
        }
        if (data.contains("numEffects")) {
            int n = data.getInt("numEffects");
            effectStrengths.clear();
            for (int i = 0; i < n; i++) {
                if (data.contains("effect" + i) && data.contains("effectstr" + i)) {
                    ResourceLocation rl = new ResourceLocation(data.getString("effect" + i));
                    MobEffect eff = ForgeRegistries.MOB_EFFECTS.getValue(rl);
                    float lvl = data.getFloat("effectstr" + i);
                    if (eff != null) {
                        effectStrengths.put(eff, lvl);
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

    // ------------------------------------------------------------------
    //   Accessors
    // ------------------------------------------------------------------
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

    public void setSplash(boolean s) {
        is_splash = s;
    }

    public boolean isLingering() {
        return is_lingering;
    }

    public void setLingering(boolean l) {
        is_lingering = l;
    }

    public int getDuration() {
        return duration;
    }

    public void addDuration(int d) {
        duration += d;
    }

    /**
     * We consider it "potion" if there's at least one effect with level >= 1.0.
     */
    public boolean isPotion() {
        for (float lvl : effectStrengths.values()) {
            if (lvl >= 1.0f) return true;
        }
        return false;
    }

    /**
     * Returns the current color (possibly interpolating from startColor to targetColor).
     */
    public long getPotionColor() {
        if (infusePct >= 1.0f) {
            return targetColor;
        }
        int[] sc = {
            (int)(startColor >> 16 & 0xFF),
            (int)(startColor >> 8 & 0xFF),
            (int)(startColor & 0xFF)
        };
        int[] tc = {
            (int)(targetColor >> 16 & 0xFF),
            (int)(targetColor >> 8 & 0xFF),
            (int)(targetColor & 0xFF)
        };
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = sc[i] + (int)((tc[i] - sc[i]) * infusePct);
        }
        return ((long)out[0] << 16) + ((long)out[1] << 8) + out[2];
    }

    // ------------------------------------------------------------------
    //  The MISSING method: recalculatePotionColor()
    // ------------------------------------------------------------------
    /**
     * Called after changes to the brew (new effects, etc.) to update color interpolation.
     */
    private void recalculatePotionColor() {
        var prominents = getProminentEffects();
        if (prominents.isEmpty()) {
            // If no real effects, fallback to water
            targetColor = WATER_COLOR;
            infusePct = 1.0f;
            return;
        }
        // Average color of all effect colors
        long sum = 0;
        for (MobEffect e : prominents.keySet()) {
            sum += e.getColor();
        }
        sum /= prominents.size();

        // If the new color differs from what we had, we start interpolating from the old color
        if (targetColor != sum) {
            startColor = getPotionColor();
            targetColor = sum;
            infusePct = 0.0f; // reset interpolation
        }
    }


    public void setFromItemStack(ItemStack potionStack) {
        effectStrengths.clear();

        if (potionStack.is(Items.SPLASH_POTION)) {
            this.is_splash = true;
            this.is_lingering = false;
        } else if (potionStack.is(Items.LINGERING_POTION)) {
            this.is_lingering = true;
            this.is_splash = false;
        } else {
            this.is_splash = false;
            this.is_lingering = false;
        }

        // Extract all vanilla + custom MobEffectInstances
        ArrayList<MobEffectInstance> itemEffects = new ArrayList<>(PotionUtils.getMobEffects(potionStack));
        itemEffects.addAll(PotionUtils.getCustomEffects(potionStack));

        // Pick the largest duration among them
        int maxDur = 0;
        for (MobEffectInstance inst : itemEffects) {
            if (inst.getDuration() > maxDur) {
                maxDur = inst.getDuration();
            }
        }
        if (maxDur <= 0) {
            maxDur = 3600; // fallback
        }
        this.duration = Math.min(maxDur, MAX_DURATION);

        // Convert amplifier => float-level
        for (MobEffectInstance inst : itemEffects) {
            int amplifier = inst.getAmplifier();  // e.g. 0 => level 1
            float newLvl = Math.min(amplifier + 1, MAX_MAGNITUDE);
            effectStrengths.put(inst.getEffect(), newLvl);
        }

        recalculatePotionColor();
    }

    /**
     * If the crucible already has brew, and we right-click with a new potion: average them.
     *
     * @param potionStack The new potion to combine
     * @param oldLevel    The existing fill level (1 or 2)
     */
    public void combineFromItemStack(ItemStack potionStack, int oldLevel) {
        if (potionStack.is(Items.SPLASH_POTION)) {
            this.is_splash = true;
        }
        if (potionStack.is(Items.LINGERING_POTION)) {
            this.is_lingering = true;
        }
		
		EbAlchemy.LOGGER.info("combineFromItemStack: item={} count={}", potionStack.getItem(), potionStack.getCount());
		EbAlchemy.LOGGER.info("  => oldLevel={}", oldLevel);

        ArrayList<MobEffectInstance> itemEffects = new ArrayList<>(PotionUtils.getMobEffects(potionStack));
        itemEffects.addAll(PotionUtils.getCustomEffects(potionStack));

        int maxDur = 0;
        for (MobEffectInstance inst : itemEffects) {
            if (inst.getDuration() > maxDur) {
                maxDur = inst.getDuration();
            }
        }
        if (maxDur <= 0) {
            maxDur = 3600;
        }
        // Weighted average of duration
        float combinedVolume = oldLevel + 1.0f;
        this.duration = (int) ((this.duration * oldLevel + maxDur) / combinedVolume);
        if (this.duration > MAX_DURATION) {
            this.duration = MAX_DURATION;
        }

        // Build new potion effect map
        HashMap<MobEffect, Float> newPotionMap = new HashMap<>();
        for (MobEffectInstance inst : itemEffects) {
            float potLvl = Math.min(inst.getAmplifier() + 1, MAX_MAGNITUDE);
            newPotionMap.put(inst.getEffect(), potLvl);
        }

        // Combine with existing effectStrengths
        HashMap<MobEffect, Float> combinedMap = new HashMap<>();
        var unionEffects = new LinkedHashSet<>(effectStrengths.keySet());
        unionEffects.addAll(newPotionMap.keySet());

        for (MobEffect eff : unionEffects) {
            float oldVal = effectStrengths.getOrDefault(eff, 0f);
            float newVal = newPotionMap.getOrDefault(eff, 0f);
            float averaged = (oldVal * oldLevel + newVal) / combinedVolume;
            averaged = Math.min(averaged, MAX_MAGNITUDE);

            if (averaged > 1e-3f) {
                combinedMap.put(eff, averaged);
            }
        }

        effectStrengths.clear();
        effectStrengths.putAll(combinedMap);

        recalculatePotionColor();
    }
}
