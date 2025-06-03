package com.ebalchemy.crucible;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;

/**
 *  CrucibleTile: streamlined in-place brew modifier logic.
 */
public class CrucibleTile extends BlockEntity {

    // === Constants ===
    public static final float MIN_TEMP = 0f;
    public static final float MAX_TEMP = 200f;
    public static final float BOIL_POINT = 100f;
    private static final int UPDATE_RATE = 10;
    private static final float ITEM_HEAT_LOSS = 25f;
    private static final long WATER_COLOR = 0x3F76E4L;
    private static final int MAX_EFFECTS = 6;
    private static final int MAX_MAGNITUDE = 4;
    private static final int MAX_DURATION = 960000; // 8 minutes

    // === Static heaters & valid ingredients ===
    private static final Map<Block, Float> heaters = new HashMap<>();
    public static final List<Item> validIngredients = new ArrayList<>();

    static {
        heaters.put(Blocks.CAMPFIRE,       12.0f);
        heaters.put(Blocks.FIRE,           18.0f);
        heaters.put(Blocks.LAVA,           30.0f);
        heaters.put(Blocks.SOUL_CAMPFIRE,  30.0f);
        heaters.put(Blocks.ICE,           -2.0f);

        // Tag registrations
        /*registerIngredientsByTag(new ResourceLocation("c:animal_foods"));
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
        registerIngredientsByTag(new ResourceLocation("c:compostable"));
        registerIngredientsByTag(new ResourceLocation("c:slimeballs"));
        registerIngredientsByTag(new ResourceLocation("c:quartz"));

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
        registerIngredientsByTag(new ResourceLocation("forge:compostable"));
        registerIngredientsByTag(new ResourceLocation("forge:slimeballs"));
        registerIngredientsByTag(new ResourceLocation("forge:quartz"));
        registerIngredientsByTag(new ResourceLocation("forge:books"));

        registerIngredientsByTag(new ResourceLocation("minecraft:fishes"));
        registerIngredientsByTag(new ResourceLocation("minecraft:flowers"));
        registerIngredientsByTag(new ResourceLocation("minecraft:fox_food"));
        registerIngredientsByTag(new ResourceLocation("minecraft:leaves"));
        registerIngredientsByTag(new ResourceLocation("minecraft:enchanting_fuels"));

        registerIngredientsByTag(new ResourceLocation("mushroomquest:mushrooms"));
        registerIngredientsByTag(new ResourceLocation("supplementaries:throwable_bricks"));
        registerIngredientsByTag(new ResourceLocation("incendium:brewing_ingredients"));
        registerIngredientsByTag(new ResourceLocation("quark:shards"));
        registerIngredientsByTag(new ResourceLocation("biomancy:sugars"));
        

        // Specific items
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
        registerIngredientsByItemID(new ResourceLocation("minecraft:heart_of_the_sea"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:echo_shard"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:popped_chorus_fruit"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:glow_ink_sac"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:ink_sac"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:nether_wart"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:blaze_rod"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:phantom_membrane"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:shulker_shell"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:nautilus_shell"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:nether_star"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:rabbit_foot"));
        registerIngredientsByItemID(new ResourceLocation("minecraft:pufferfish"));

        // Mod extras
        registerIngredientsByItemID(new ResourceLocation("supplementaries:soap"));
        registerIngredientsByItemID(new ResourceLocation("supplementaries:ash"));
        registerIngredientsByItemID(new ResourceLocation("quark:moss_paste"));
        registerIngredientsByItemID(new ResourceLocation("quark:glowstone_dust"));
        registerIngredientsByItemID(new ResourceLocation("quark:crab_shell"));

        // Partials
        registerIngredientsByNamePartial("mushroom");
        registerIngredientsByNamePartial("shroom");
        
        registerIngredientsByTag(new ResourceLocation("iceandfire:dragon_hearts"));
        registerIngredientsByTag(new ResourceLocation("iceandfire:mob_skulls"));
        registerIngredientsByTag(new ResourceLocation("forge:scales"));
        registerIngredientsByTag(new ResourceLocation("iceandfire:scales/dragon"));
        registerIngredientsByTag(new ResourceLocation("createaddition:plants"));
        registerIngredientsByTag(new ResourceLocation("minecraft:logs_that_burn"));
        registerIngredientsByTag(new ResourceLocation("minecraft:planks"));*/

		registerAllItems();
    }

    public static void registerIngredientsByTag(ResourceLocation tag) {
        ITag<Item> t = ForgeRegistries.ITEMS.tags()
            .getTag(ForgeRegistries.ITEMS.tags().createTagKey(tag));
        if (t != null) t.forEach(item -> {
            if (!validIngredients.contains(item)) validIngredients.add(item);
        });
    }

    public static void registerIngredientsByItemID(ResourceLocation id) {
        Item itm = ForgeRegistries.ITEMS.getValue(id);
        if (itm != null && !validIngredients.contains(itm)) validIngredients.add(itm);
    }

    public static void registerIngredientsByNamePartial(String name) {
        ForgeRegistries.ITEMS.getKeys().forEach(res -> {
            if (res.getPath().contains(name)) {
                Item itm = ForgeRegistries.ITEMS.getValue(res);
                if (itm != null && !validIngredients.contains(itm)) validIngredients.add(itm);
            }
        });
    }

	public static void registerAllItems() {
		// registers all items in minecraft for debugging
		ForgeRegistries.ITEMS.getKeys().forEach(res -> {
			Item itm = ForgeRegistries.ITEMS.getValue(res);
			if (itm != null && !validIngredients.contains(itm)) validIngredients.add(itm);
		});
	}
    
    

    // === Fields ===
    private float heat = MIN_TEMP, stir = 0f;
    private boolean isSplash = false, isLingering = false;
    private final LinkedHashMap<MobEffect, MobEffectInstance> currentEffects = new LinkedHashMap<>();
    private RecipeManager recipeManager;
    private Biome biome;
    private long targetColor = 12345L, startColor = 12345L;
    private double infusePct = 1.0;

    private final List<ModifierType> entityModifiers = new ArrayList<>();
    private final LinkedHashSet<UUID> entitiesWithModifierApplied = new LinkedHashSet<>();
    private final HashMap<UUID, Long> lastCollisionTickMap = new HashMap<>();

    // Cache for modifiers
    private final HashMap<Item, ModifierType[]> ingredientModifiersCache = new HashMap<>();

    public enum ModifierType {
        ADD_EFFECT, REMOVE_FIRST, REMOVE_LAST,
        ADD_DURATION, REMOVE_DURATION,
        ADD_LEVEL, REMOVE_LEVEL,
        TRANSMUTE, MERGE, CLEAR
    }

    private static final LinkedHashMap<ModifierType, Integer> MODIFIER_WEIGHTS = new LinkedHashMap<>();
    static {
        MODIFIER_WEIGHTS.put(ModifierType.ADD_EFFECT,     15);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_FIRST,    3);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_LAST,     3);
        MODIFIER_WEIGHTS.put(ModifierType.ADD_DURATION,    6);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_DURATION, 4);
        MODIFIER_WEIGHTS.put(ModifierType.ADD_LEVEL,       6);
        MODIFIER_WEIGHTS.put(ModifierType.REMOVE_LEVEL,    4);
        MODIFIER_WEIGHTS.put(ModifierType.TRANSMUTE,       8);
        MODIFIER_WEIGHTS.put(ModifierType.MERGE,           5);
        MODIFIER_WEIGHTS.put(ModifierType.CLEAR,           1);
    }


    public CrucibleTile(BlockPos pos, BlockState state) {
        super(TileEntityInit.CRUCIBLE_TILE_TYPE.get(), pos, state);
    }

    public static <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level lvl, BlockState st, BlockEntityType<T> type) {
        return type == TileEntityInit.CRUCIBLE_TILE_TYPE.get()
            ? (level, pos, state, te) -> ((CrucibleTile) te).tick()
            : null;
    }

    /** Called by BlockCrucible.getTicker(...) */
    public static void Tick(Level level, BlockPos pos, BlockState state, CrucibleTile te) {
        te.tick();
    }

    /** Debug helper invoked on water-bucket click */
    public void printIngredientPotentialEffects() {
        // Copy your old implementation, or even a simple dump:
        EbAlchemy.LOGGER.info("Current brew effects: {}", currentEffects);
    }

    /** True if there's at least one effect present */
    public boolean isPotion() {
        return !currentEffects.isEmpty();
    }

    /** Halve durations and remove one level; reset if empty */
    public void diluteBrew() {
        // Halve each effect’s duration
        currentEffects.replaceAll((eff, inst) -> {
            int newDur = Math.max(1, inst.getDuration() / 2);
            int newAmp = inst.getAmplifier() - 1;
            return new MobEffectInstance(eff, newDur, newAmp, inst.isAmbient(), inst.isVisible());
        });
        // Remove any now-empty effects
        currentEffects.entrySet().removeIf(e -> e.getValue().getAmplifier() < 0);
        // If none remain, clear everything
        if (currentEffects.isEmpty()) resetAll();
    }

    /**
     *  Returns a map of “prominent” (level ≥ 1) effects
     *  for bottle extraction and edible infusing.
     */
    public HashMap<MobEffect, Float> getProminentEffects() {
        HashMap<MobEffect, Float> map = new HashMap<>();
        for (MobEffectInstance inst : currentEffects.values()) {
            float lvl = inst.getAmplifier() + 1f;
            ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
            if (lvl >= 1f && !BrewingConfig.isEffectDisabled(rl)) {
                map.put(inst.getEffect(), lvl);
            }
        }
        return map;
    }

    public void combineFromItemStack(ItemStack potionStack, int oldLevel) {
        if (potionStack.is(Items.SPLASH_POTION))    isSplash    = true;
        if (potionStack.is(Items.LINGERING_POTION)) isLingering = true;

        List<MobEffectInstance> itemEffects = new ArrayList<>(PotionUtils.getMobEffects(potionStack));
        itemEffects.addAll(PotionUtils.getCustomEffects(potionStack));

        int maxDur = 0;
        for (var inst : itemEffects) maxDur = Math.max(maxDur, inst.getDuration());
        if (maxDur <= 0) maxDur = 3600;
        float combinedVolume = oldLevel + 1f;
        int newDur = (int)((getMaxDurationSum() * oldLevel + maxDur) / combinedVolume);

        HashMap<MobEffect, Float> bottleMap = new HashMap<>();
        for (var inst : itemEffects) {
            float lvlF = Math.min(inst.getAmplifier() + 1f, MAX_EFFECTS);
            bottleMap.put(inst.getEffect(), lvlF);
        }

        HashMap<MobEffect, MobEffectInstance> merged = new LinkedHashMap<>();
        var union = new LinkedHashSet<MobEffect>(currentEffects.keySet());
        union.addAll(bottleMap.keySet());

        for (MobEffect eff : union) {
            float oldVal = currentEffects.containsKey(eff)
                         ? currentEffects.get(eff).getAmplifier()+1f
                         : 0f;
            float newVal = bottleMap.getOrDefault(eff, 0f);
            float avg   = Math.min((oldVal * oldLevel + newVal) / combinedVolume, MAX_EFFECTS);
            int amp     = Math.max(0, (int)avg - 1);
            merged.put(eff, new MobEffectInstance(eff, newDur, amp, false, true));
        }

        currentEffects.clear();
        currentEffects.putAll(merged);
    }
    
    public void setFromCharm(ItemStack charmStack) {
        CompoundTag tag = charmStack.getTag();
        // If there's no brew data, just reset to empty
        if (tag == null || !tag.contains("brew_effects", Tag.TAG_LIST)) {
            resetAll();
            return;
        }

        // 1) Clear existing brew
        resetAll();

        // 2) Rebuild effects from the charm NBT
        ListTag list = tag.getList("brew_effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag effTag = list.getCompound(i);
            ResourceLocation rl = new ResourceLocation(effTag.getString("effect"));
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
            if (effect == null) continue;
            int amp  = effTag.getInt("amplifier");
            int secs = effTag.getInt("remaining_secs");
            // We stored secs = originalSeconds*3, so invert that:
            int durTicks = Math.max(1, (secs / 3) * 20);
            currentEffects.put(effect,
                new MobEffectInstance(effect, durTicks, amp, false, true));
        }

        // 3) Restore splash/lingering flags
        if (tag.contains("splash")) {
            this.isSplash = tag.getBoolean("splash");
        }
        if (tag.contains("lingering")) {
            this.isLingering = tag.getBoolean("lingering");
        }

        // 4) Recompute color
        recalculatePotionColor();
    }

    // Helper to compute sum of durations in currentEffects
    private int getMaxDurationSum() {
        int sum = 0;
        for (MobEffectInstance inst : currentEffects.values()) {
            sum = Math.max(sum, inst.getDuration());
        }
        return sum;
    }

    private void tick() {
        int lvl = getBlockState().getValue(LayeredCauldronBlock.LEVEL);
        if (level.isClientSide && lvl > 0) spawnParticles(lvl);
        infusePct = Mth.clamp(infusePct + 0.05f, 0, 1);
        if (level.getGameTime() % UPDATE_RATE != 0) return;
        if (lvl == 0) { resetAll(); return; }
        if (biome == null) biome = level.getBiome(worldPosition).get();
        if (!level.isClientSide) heatAndStir(lvl);
    }

    private void heatAndStir(int lvl) {
        Block below = level.getBlockState(worldPosition.below()).getBlock();
        float old = heat;
        heat = Mth.clamp(
            heat + heaters.getOrDefault(below, -(1f - biome.getBaseTemperature()) * 10f),
            MIN_TEMP, MAX_TEMP
        );
        stir = Mth.clamp(stir - (stir > 0.25f ? 0.02f : 0.005f), 0, 1);
        if (heat >= BOIL_POINT && stir == 0 && Math.random() < 0.04f) {
            int nl = lvl - 1;
            if (nl <= 0) level.setBlockAndUpdate(
                worldPosition, BlockInit.EMPTY_CRUCIBLE.get().defaultBlockState()
            );
            else level.setBlockAndUpdate(
                worldPosition,
                getBlockState().setValue(LayeredCauldronBlock.LEVEL, nl)
            );
        }
        if (heat != old) level.sendBlockUpdated(
            worldPosition, getBlockState(), getBlockState(), 3
        );
    }

    private void spawnParticles(int lvl) {
        if (heat > BOIL_POINT) {
            int count = (int)Math.ceil(5f * ((heat - BOIL_POINT) / (MAX_TEMP - BOIL_POINT)));
            for (int i = 0; i < count; i++) {
                level.addParticle(ParticleTypes.BUBBLE_POP,
                    worldPosition.getX()+0.5-0.3+Math.random()*0.6,
                    worldPosition.getY()+0.2+0.25f*lvl,
                    worldPosition.getZ()+0.5-0.3+Math.random()*0.6,
                    0, isSplash?0.125f:0, 0
                );
            }
        }
    }

    private void resetAll() {
        heat = MIN_TEMP;
        stir = 1f;
        isSplash = isLingering = false;
        currentEffects.clear();
        entityModifiers.clear();
        entitiesWithModifierApplied.clear();
        lastCollisionTickMap.clear();
        infusePct = 1;
        targetColor = startColor = 12345L;
    }

    public boolean tryAddIngredient(ItemStack stack) {
        EbAlchemy.LOGGER.info("=== tryAddIngredient START ===");
        EbAlchemy.LOGGER.info(" Adding ingredient → {} x{}", stack.getItem(), stack.getCount());

        if (heat < BOIL_POINT) {
            EbAlchemy.LOGGER.info("  => Not boiling (heat={})", heat);
            return false;
        }

        if (recipeManager == null) recipeManager = level.getRecipeManager();

        Optional<PotionIngredientRecipe> recOpt = recipeManager
            .getRecipesFor(RecipeInit.POTION_RECIPE_TYPE.get(),
                           createDummyCraftingInventory(stack),
                           level)
            .stream().findFirst();

        boolean validRecipe = false;
		boolean wasModifier = false;
        if (recOpt.isPresent()) {
            var rec = recOpt.get();
            EbAlchemy.LOGGER.info("  recipe → splash={}, linger={}, durAdd={}",
                rec.getMakesSplash(), rec.getMakesLingering(), rec.getDurationAdded()
            );
            if (rec.getMakesSplash()) {
				isSplash    = true;
				wasModifier = true;
			}
            if (rec.getMakesLingering()) {isLingering = true;
				wasModifier = true;
			}
            if (rec.getDurationAdded() > 0) {
                int add = rec.getDurationAdded() * stack.getCount();
                currentEffects.replaceAll((eff, inst) ->
                    new MobEffectInstance(eff,
                        inst.getDuration() + add,
                        inst.getAmplifier(),
                        inst.isAmbient(),
                        inst.isVisible()
                    )
                );
				wasModifier = true;
            }
            validRecipe = true;
        }

        if (!validRecipe && !validIngredients.contains(stack.getItem())) {
            applyInvalidIngredientOutcome();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return true;
        }

		if(!wasModifier){
			ModifierType[] mods = getModifiersForIngredient(stack.getItem());
			EbAlchemy.LOGGER.info("  random mods = {}", java.util.Arrays.toString(mods));
			for (int i = 0; i < mods.length; i++) {
				applyModifier(mods[i], stack.getCount(), stack.getItem(), i);
			}
		}

        heat = Mth.clamp(heat - ITEM_HEAT_LOSS * stack.getCount(), MIN_TEMP, MAX_TEMP);
        recalculatePotionColor();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        EbAlchemy.LOGGER.info("=== tryAddIngredient END ===");
        return true;
    }

    private void applyInvalidIngredientOutcome() {
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            level.playSound(null, worldPosition, SoundEvents.DRAGON_FIREBALL_EXPLODE,
                            SoundSource.BLOCKS, 1f, 1f);
            for (int i = 0; i < 20; i++) {
                double ox = worldPosition.getX() + 0.5 + (sl.random.nextDouble() - 0.5) * 0.5;
                double oy = worldPosition.getY() + 1.2;
                double oz = worldPosition.getZ() + 0.5 + (sl.random.nextDouble() - 0.5) * 0.5;
                sl.sendParticles(ParticleTypes.WITCH, ox, oy, oz, 1, 0, 0, 0, 0);
            }
        }
    }

    public void handleLivingEntityCollision(LivingEntity entity) {
        long now = level.getGameTime();
        UUID id = entity.getUUID();
        if (now - lastCollisionTickMap.getOrDefault(id, 0L) < 40) return;

        for (MobEffectInstance inst : currentEffects.values()) {
            entity.addEffect(new MobEffectInstance(
                inst.getEffect(), inst.getDuration(), inst.getAmplifier(), false, true
            ));
        }

        if (heat >= BOIL_POINT && !entitiesWithModifierApplied.contains(id)) {
            entity.hurt(level.damageSources().magic(), 2f);
            ModifierType m = weightedRandomModifier(new Random(id.hashCode()));
            Item dummy = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("minecraft:cauldron")
            );
            if (dummy != null) {
                applyModifier(m, 1, dummy, 0);
                entityModifiers.add(m);
                entitiesWithModifierApplied.add(id);
            }
        }

        lastCollisionTickMap.put(id, now);
    }

    private ModifierType weightedRandomModifier(Random rnd) {
        int total = MODIFIER_WEIGHTS.values().stream().mapToInt(i -> i).sum();
        int r = rnd.nextInt(total);
        for (Map.Entry<ModifierType, Integer> e : MODIFIER_WEIGHTS.entrySet()) {
            if (r < e.getValue()) return e.getKey();
            r -= e.getValue();
        }
        return ModifierType.ADD_EFFECT;
    }

    private void recalculatePotionColor() {
        if (currentEffects.isEmpty()) {
            targetColor = WATER_COLOR;
            infusePct = 1;
            return;
        }
        long sum = 0;
        for (MobEffectInstance inst : currentEffects.values()) {
            sum += inst.getEffect().getColor();
        }
        sum /= currentEffects.size();
        if (targetColor != sum) {
            startColor = getPotionColor();
            targetColor  = sum;
            infusePct    = 0;
            if (!level.isClientSide && level instanceof ServerLevel sl) {
            	level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK,
                        SoundSource.BLOCKS, 1f, 1f);
                float r = ((sum >> 16) & 0xFF) / 255f;
                float g = ((sum >>  8) & 0xFF) / 255f;
                float b = ((sum      ) & 0xFF) / 255f;
                double x = worldPosition.getX() + 0.5;
                double y = worldPosition.getY() + 1.0;
                double z = worldPosition.getZ() + 0.5;
                sl.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1f),
                                 x, y, z, 20, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    public long getPotionColor() {
        if (infusePct >= 1) return targetColor;
        int scR = (int)(startColor>>16 & 0xFF),
            scG = (int)(startColor>> 8 & 0xFF),
            scB = (int)(startColor     & 0xFF);
        int tcR = (int)(targetColor>>16 & 0xFF),
            tcG = (int)(targetColor>> 8 & 0xFF),
            tcB = (int)(targetColor     & 0xFF);
        int rr = scR + (int)((tcR-scR)*infusePct);
        int gg = scG + (int)((tcG-scG)*infusePct);
        int bb = scB + (int)((tcB-scB)*infusePct);
        return ((long)rr<<16)|((long)gg<<8)|bb;
    }

    private TransientCraftingContainer createDummyCraftingInventory(ItemStack stack) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player p) { return false; }
            @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
        };
        TransientCraftingContainer c = new TransientCraftingContainer(menu, 1, 1);
        c.setItem(0, stack);
        return c;
    }

    /**
     * Initializes this CrucibleTile’s state to match a vanilla potion stack.
     * Called when you pour a potion (normal/splash/lingering) into an empty crucible.
     */
    public void setFromItemStack(ItemStack potionStack) {
        // Clear any existing brew
        currentEffects.clear();
        isSplash    = potionStack.is(Items.SPLASH_POTION);
        isLingering = potionStack.is(Items.LINGERING_POTION);

        // Gather all vanilla + custom effects
        List<MobEffectInstance> effects = new ArrayList<>();
        effects.addAll(PotionUtils.getMobEffects(potionStack));
        effects.addAll(PotionUtils.getCustomEffects(potionStack));

        // Populate currentEffects with each instance (preserving duration & amp)
        for (MobEffectInstance inst : effects) {
            // clamp amplifier to our max if needed
            int amp = Math.min(inst.getAmplifier(), MAX_EFFECTS - 1);
            currentEffects.put(
                inst.getEffect(),
                new MobEffectInstance(
                    inst.getEffect(),
                    inst.getDuration(),
                    amp,
                    inst.isAmbient(),
                    inst.isVisible()
                )
            );
        }

        // Re-compute color immediately
        recalculatePotionColor();
    }

    
    private ModifierType[] getModifiersForIngredient(Item ingredient) {
        if (ingredientModifiersCache.containsKey(ingredient)) {
            return ingredientModifiersCache.get(ingredient);
        }
        long seed = getWorldSeed();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(ingredient);
        if (key != null) seed ^= key.toString().hashCode();

        Random base = new Random(seed);
        int n = base.nextInt(3) + 1;
        List<ModifierType> raw = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Random r2 = new Random(seed ^ (i + 1));
            raw.add(weightedRandomModifier(r2));
        }
        if (raw.contains(ModifierType.CLEAR)) {
            raw.clear();
            raw.add(ModifierType.CLEAR);
        }
        if (raw.contains(ModifierType.ADD_EFFECT) && (raw.contains(ModifierType.REMOVE_FIRST) || raw.contains(ModifierType.REMOVE_LAST))) {
            raw.remove(ModifierType.REMOVE_FIRST);
            raw.remove(ModifierType.REMOVE_LAST);
        }
        if (raw.contains(ModifierType.ADD_DURATION) && raw.contains(ModifierType.REMOVE_DURATION)) {
            raw.remove(ModifierType.REMOVE_DURATION);
        }
        if (raw.contains(ModifierType.ADD_LEVEL) && raw.contains(ModifierType.REMOVE_LEVEL)) {
            raw.remove(ModifierType.REMOVE_LEVEL);
        }

        ModifierType[] mods = raw.toArray(new ModifierType[0]);
        ingredientModifiersCache.put(ingredient, mods);
        return mods;
    }

    private void applyModifier(ModifierType mod, int count, Item ingredient, int modIndex) {
        long seed = getWorldSeed();
        ResourceLocation ingrKey = ForgeRegistries.ITEMS.getKey(ingredient);
        if (ingrKey != null) seed ^= ingrKey.toString().hashCode();
        seed ^= mod.ordinal();
        seed ^= (modIndex + 1);

        Random random = new Random(seed);

        switch (mod) {
            case ADD_EFFECT -> {
                List<MobEffect> allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                if (!allEff.isEmpty() && currentEffects.size() < MAX_EFFECTS) {
                    MobEffect chosen = allEff.get(random.nextInt(allEff.size()));
                    if (!currentEffects.containsKey(chosen)) {
                        int durationTicks;
                        if (currentEffects.isEmpty()) {
                            int secs = 5 + random.nextInt(36);
                            durationTicks = secs * 20;
                        } else {
                            int sum = 0, cnt = 0;
                            for (var inst : currentEffects.values()) {
                                sum += inst.getDuration();
                                cnt++;
                            }
                            durationTicks = cnt > 0 ? sum / cnt : 0;
                        }
                        currentEffects.put(chosen,
                            new MobEffectInstance(chosen,
                                                  Math.max(1, durationTicks),
                                                  0,
                                                  false,
                                                  true));
                    }
                }
            }
            case REMOVE_FIRST -> {
                if (!currentEffects.isEmpty()) {
                    MobEffect rem = currentEffects.keySet().iterator().next();
                    currentEffects.remove(rem);
                }
            }
            case REMOVE_LAST -> {
                if (!currentEffects.isEmpty()) {
                    List<MobEffect> keys = new ArrayList<>(currentEffects.keySet());
                    MobEffect rem = keys.get(keys.size() - 1);
                    currentEffects.remove(rem);
                }
            }
            case ADD_DURATION -> {
                final int plusDuration = (200 + random.nextInt(1001)) * count;
                currentEffects.replaceAll((eff, inst) ->
                    new MobEffectInstance(eff,
                                          Math.min(inst.getDuration() + plusDuration, MAX_DURATION),
                                          inst.getAmplifier(),
                                          inst.isAmbient(),
                                          inst.isVisible())
                );
            }
            case REMOVE_DURATION -> {
                final int minusDuration = (200 + random.nextInt(1001)) * count;
                currentEffects.replaceAll((eff, inst) ->
                    new MobEffectInstance(eff,
                                          Math.max(1, inst.getDuration() - minusDuration),
                                          inst.getAmplifier(),
                                          inst.isAmbient(),
                                          inst.isVisible())
                );
            }
            case ADD_LEVEL -> {
                currentEffects.replaceAll((eff, inst) -> {
                    int na = Math.min(inst.getAmplifier() + count, MAX_MAGNITUDE - 1);
                    return new MobEffectInstance(eff,
                                                 inst.getDuration(),
                                                 na,
                                                 inst.isAmbient(),
                                                 inst.isVisible());
                });
            }
            case REMOVE_LEVEL -> {
                currentEffects.replaceAll((eff, inst) -> {
                    int na = Math.max(0, inst.getAmplifier() - count);
                    return new MobEffectInstance(eff,
                                                 inst.getDuration(),
                                                 na,
                                                 inst.isAmbient(),
                                                 inst.isVisible());
                });
            }
            case TRANSMUTE -> {
                Map<MobEffect, MobEffectInstance> newMap = new HashMap<>();
                List<MobEffect> all = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                all.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                for (var oldEntry : currentEffects.entrySet()) {
                    if (all.isEmpty()) break;
                    MobEffect newE = all.get(random.nextInt(all.size()));
                    MobEffectInstance oi = oldEntry.getValue();
                    newMap.put(newE,
                        new MobEffectInstance(newE,
                                               oi.getDuration(),
                                               oi.getAmplifier(),
                                               oi.isAmbient(),
                                               oi.isVisible()));
                }
                currentEffects.clear();
                currentEffects.putAll(newMap);
            }
            case MERGE -> {
                int n = currentEffects.size();
                if (n > 0) {
                    int totalDur = 0, totalAmp = 0;
                    for (var inst : currentEffects.values()) {
                        totalDur += inst.getDuration();
                        totalAmp += inst.getAmplifier();
                    }
                    totalAmp = Math.min(totalAmp, MAX_MAGNITUDE - 1);
                    List<MobEffect> all = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                    all.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                    MobEffect pick = all.get(random.nextInt(all.size()));
                    currentEffects.clear();
                    currentEffects.put(pick,
                        new MobEffectInstance(pick,
                                               Math.min(totalDur, MAX_DURATION),
                                               totalAmp,
                                               false,
                                               true));
                }
            }
            case CLEAR -> currentEffects.clear();
        }
    }

    private long getWorldSeed() {
        long s = 0;
        if (level instanceof ServerLevel sl) s = sl.getSeed();
        Block below = level.getBlockState(worldPosition.below()).getBlock();
        if (below == Blocks.SOUL_CAMPFIRE) s ^= 0xFACEB00CL;
        return s;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("heat", heat);
        tag.putFloat("stir", stir);
        tag.putBoolean("splash", isSplash);
        tag.putBoolean("lingering", isLingering);
        tag.putInt("numEffects", currentEffects.size());
        int i = 0;
        for (MobEffectInstance inst : currentEffects.values()) {
            ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
            if (rl != null) {
                tag.putString("eff" + i, rl.toString());
                tag.putInt("dur" + i, inst.getDuration());
                tag.putInt("amp" + i, inst.getAmplifier());
                i++;
            }
        }
    }

    @Override
    public void load(CompoundTag data) {
        super.load(data);
        if (data.contains("heat"))      heat = data.getFloat("heat");
        if (data.contains("stir"))      stir = data.getFloat("stir");
        if (data.contains("splash"))    isSplash = data.getBoolean("splash");
        if (data.contains("lingering")) isLingering = data.getBoolean("lingering");
        if (data.contains("numEffects")) {
            int n = data.getInt("numEffects");
            currentEffects.clear();
            for (int j = 0; j < n; j++) {
                MobEffect eff = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(data.getString("eff" + j)));
                int d = data.getInt("dur" + j);
                int a = data.getInt("amp" + j);
                if (eff != null) currentEffects.put(eff, new MobEffectInstance(eff, d, a, false, true));
            }
        }
        recalculatePotionColor();
    }

    public void debugPrintAllIngredientModifiers() {
        EbAlchemy.LOGGER.info("=== Ingredient Modifier Dump ===");
        for (Item ingredient : validIngredients) {
            ResourceLocation ingrKey = ForgeRegistries.ITEMS.getKey(ingredient);
            if (ingrKey == null) continue;

            EbAlchemy.LOGGER.info("Ingredient: {}", ingrKey);
            ModifierType[] mods = getModifiersForIngredient(ingredient);

            for (int i = 0; i < mods.length; i++) {
                ModifierType mod = mods[i];
                long seed = getWorldSeed();
                seed ^= ingrKey.toString().hashCode();
                seed ^= mod.ordinal();
                seed ^= (i + 1);
                Random r = new Random(seed);

                String detail;
                switch (mod) {
                    case ADD_EFFECT -> {
                        List<MobEffect> allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                        allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                        MobEffect pick = allEff.get(r.nextInt(allEff.size()));
                        detail = "Adds effect → " + ForgeRegistries.MOB_EFFECTS.getKey(pick);
                    }
                    case REMOVE_FIRST -> detail = "Removes the first added effect";
                    case REMOVE_LAST  -> detail = "Removes the last added effect";
                    case ADD_DURATION -> {
                        int plus = 200 + r.nextInt(1001);
                        detail = "Adds duration +" + plus + " ticks";
                    }
                    case REMOVE_DURATION -> {
                        int minus = 200 + r.nextInt(1001);
                        detail = "Removes duration -" + minus + " ticks";
                    }
                    case ADD_LEVEL -> detail = "Increases level of all effects by 1";
                    case REMOVE_LEVEL -> detail = "Decreases level of all effects by 1";
                    case TRANSMUTE -> {
                        List<MobEffect> allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                        allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                        MobEffect pickT = allEff.get(r.nextInt(allEff.size()));
                        detail = "Transmutes all effects to → " + ForgeRegistries.MOB_EFFECTS.getKey(pickT);
                    }
                    case MERGE -> {
                        List<MobEffect> allEff = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
                        allEff.sort(Comparator.comparing(e -> ForgeRegistries.MOB_EFFECTS.getKey(e).toString()));
                        MobEffect pickM = allEff.get(r.nextInt(allEff.size()));
                        detail = "Merges all effects into → " + ForgeRegistries.MOB_EFFECTS.getKey(pickM);
                    }
                    case CLEAR -> detail = "Clears all effects";
                    default -> detail = "<unknown>";
                }

                EbAlchemy.LOGGER.info("  Modifier[{}]: {} -> {}", i, mod, detail);
            }
        }
        EbAlchemy.LOGGER.info("=== End Ingredient Modifier Dump ===");
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

    // === Accessors ===
    public float getHeat()       { return heat; }
    public float getMaxHeat()    { return MAX_TEMP; }
    public void stir()           { stir = 1f; }
    public float getStir()       { return stir; }
    public boolean isSplash()    { return isSplash; }
    public boolean isLingering() { return isLingering; }
    public Map<MobEffect, MobEffectInstance> getCurrentEffects() { return currentEffects; }
}
