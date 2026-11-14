package com.kragnoth.hallmarked.mixin;

import com.kragnoth.hallmarked.loot.LootGrading;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every top-level loot roll (containers, block and entity drops, archaeology, vaults, fishing) ends in this method,
 * after the global loot modifiers and LootJS have run. Nested table entries roll "raw" and don't come through here,
 * so a roll is graded once.
 */
@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Inject(method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN"))
    private void hallmarked$gradeLoot(LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        LootGrading.finish(cir.getReturnValue(), context, ((LootTable) (Object) this).getLootTableId());
    }
}
