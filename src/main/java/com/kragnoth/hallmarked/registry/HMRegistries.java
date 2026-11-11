package com.kragnoth.hallmarked.registry;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.block.AssayAnvilBlock;
import com.kragnoth.hallmarked.block.AssayAnvilBlockEntity;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.loot.GradeLootModifier;
import com.kragnoth.hallmarked.recipe.GradedIngredient;
import com.kragnoth.hallmarked.recipe.ReforgeMaterialRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.IngredientType;
import com.kragnoth.hallmarked.menu.AssayAnvilMenu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HMRegistries {
    private HMRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hallmarked.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hallmarked.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hallmarked.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hallmarked.MODID);
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Hallmarked.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hallmarked.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Hallmarked.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Hallmarked.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Hallmarked.MODID);
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Hallmarked.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hallmarked.MODID);

    // ---- Data components -------------------------------------------------------------------
    /** The quality name (translation key) stamped on an item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> QUALITY =
            COMPONENTS.registerComponentType("quality", b -> b
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Set once the item has had its one automatic roll. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ROLLED =
            COMPONENTS.registerComponentType("rolled", b -> b
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Grade of work plus rolled affixes. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GradeData>> GRADE =
            COMPONENTS.registerComponentType("grade", b -> b
                    .persistent(GradeData.CODEC)
                    .networkSynchronized(GradeData.STREAM_CODEC));

    /**
     * The name an item had before Hallmarked named it (e.g. from a loot table's set_name), so generated names build
     * on it instead of wiping it, and it comes back if our name is dropped.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.network.chat.Component>> BASE_NAME =
            COMPONENTS.registerComponentType("base_name", b -> b
                    .persistent(net.minecraft.network.chat.ComponentSerialization.CODEC)
                    .networkSynchronized(net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC));

    /** Inlay names applied at the smithing table, in order. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<String>>> INLAYS =
            COMPONENTS.registerComponentType("inlays", b -> b
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));

    /** The one inlay applied with a Gearwright's Seal: outside the slot count, ignores group limits, permanent. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SEALED_INLAY =
            COMPONENTS.registerComponentType("sealed_inlay", b -> b
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<GradeLootModifier>> GRADE_LOOT =
            LOOT_MODIFIERS.register("grade_loot", () -> GradeLootModifier.CODEC);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ReforgeMaterialRecipe>> REFORGE_MATERIAL_TYPE =
            RECIPE_TYPES.register("reforge_material", () -> RecipeType.simple(Hallmarked.id("reforge_material")));
    public static final DeferredHolder<RecipeSerializer<?>, ReforgeMaterialRecipe.Serializer> REFORGE_MATERIAL_SERIALIZER =
            RECIPE_SERIALIZERS.register("reforge_material", ReforgeMaterialRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<com.kragnoth.hallmarked.inlay.InlayRecipe>> INLAY_SERIALIZER =
            RECIPE_SERIALIZERS.register("inlay", com.kragnoth.hallmarked.inlay.InlayRecipe.Serializer::new);
    public static final DeferredHolder<IngredientType<?>, IngredientType<GradedIngredient>> GRADED_INGREDIENT =
            INGREDIENT_TYPES.register("graded", () -> new IngredientType<>(GradedIngredient.CODEC, GradedIngredient.STREAM_CODEC));

    // ---- Attributes ------------------------------------------------------------------------
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE = ATTRIBUTES.register("projectile_damage",
            () -> new RangedAttribute("attribute.hallmarked.projectile_damage", 1.0, -100.0, 100.0).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_RESISTANCE = ATTRIBUTES.register("damage_resistance",
            () -> new RangedAttribute("attribute.hallmarked.damage_resistance", 1.0, 0.0, 100.0).setSyncable(true));

    // ---- Blocks / items --------------------------------------------------------------------
    public static final DeferredBlock<AssayAnvilBlock> ASSAY_ANVIL = BLOCKS.register("assay_anvil", AssayAnvilBlock::new);
    public static final DeferredItem<BlockItem> ASSAY_ANVIL_ITEM = ITEMS.registerSimpleBlockItem(ASSAY_ANVIL);
    public static final DeferredItem<Item> EMERALD_RING = ITEMS.registerSimpleItem("emerald_ring", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> EMERALD_AMULET = ITEMS.registerSimpleItem("emerald_amulet", new Item.Properties().stacksTo(1));
    /** Universal inlay template, consumed at the smithing table. */
    public static final DeferredItem<Item> SMITHS_STAMP = ITEMS.registerSimpleItem("smiths_stamp", new Item.Properties());
    /** Rare template: applies one extra, permanent inlay regardless of free slots. */
    public static final DeferredItem<Item> GEARWRIGHTS_SEAL = ITEMS.registerSimpleItem("gearwrights_seal",
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)
                    .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssayAnvilBlockEntity>> ASSAY_ANVIL_BE =
            BLOCK_ENTITIES.register("assay_anvil",
                    () -> BlockEntityType.Builder.of(AssayAnvilBlockEntity::new, ASSAY_ANVIL.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<AssayAnvilMenu>> ASSAY_ANVIL_MENU =
            MENUS.register("assay_anvil", () -> IMenuTypeExtension.create(AssayAnvilMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hallmarked"))
            .icon(() -> new ItemStack(ASSAY_ANVIL_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(ASSAY_ANVIL_ITEM.get());
                out.accept(EMERALD_RING.get());
                out.accept(EMERALD_AMULET.get());
                out.accept(SMITHS_STAMP.get());
                out.accept(GEARWRIGHTS_SEAL.get());
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ATTRIBUTES.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        INGREDIENT_TYPES.register(modBus);
        modBus.addListener(HMRegistries::addPlayerAttributes);
        modBus.addListener(HMRegistries::registerCapabilities);
    }

    private static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, PROJECTILE_DAMAGE);
        event.add(EntityType.PLAYER, DAMAGE_RESISTANCE);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ASSAY_ANVIL_BE.get(),
                (be, side) -> be.automationHandler());
    }
}
