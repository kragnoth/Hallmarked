package com.kragnoth.hallmarked.registry;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.block.AssayAnvilBlock;
import com.kragnoth.hallmarked.block.AssayAnvilBlockEntity;
import com.kragnoth.hallmarked.menu.AssayAnvilMenu;
import com.mojang.serialization.Codec;
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
