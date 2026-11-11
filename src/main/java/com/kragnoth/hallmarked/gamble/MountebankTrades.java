package com.kragnoth.hallmarked.gamble;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/** Mountebank trades. Offers are picked from the parcel data when a villager levels up, so datapacks control them. */
@EventBusSubscriber(modid = Hallmarked.MODID)
public final class MountebankTrades {
    private MountebankTrades() {}

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != HMRegistries.MOUNTEBANK.get()) return;
        var trades = event.getTrades();
        for (int level = 1; level <= 5; level++) {
            int lv = level;
            trades.get(level).add((trader, random) -> parcelOffer(lv, random));
            trades.get(level).add((trader, random) -> parcelOffer(lv, random));
        }
        // A fence buys scrap: the only thing he'll take off your hands.
        trades.get(1).add((trader, random) -> new MerchantOffer(new ItemCost(HMRegistries.SMITHS_SCRAP.get(), 6),
                new ItemStack(Items.EMERALD), 12, 1, 0.05f));
    }

    private static MerchantOffer parcelOffer(int level, RandomSource random) {
        List<Parcel> options = new ArrayList<>();
        int total = 0;
        for (Parcel p : Parcel.STORE.all()) {
            if (p.level() == level && p.weight() > 0) {
                options.add(p);
                total += p.weight();
            }
        }
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        Parcel chosen = options.get(options.size() - 1);
        for (Parcel p : options) {
            pick -= p.weight();
            if (pick < 0) { chosen = p; break; }
        }
        int price = chosen.priceMin() + random.nextInt(chosen.priceMax() - chosen.priceMin() + 1);
        Optional<ItemCost> second = chosen.secondItem() == null ? Optional.empty()
                : Optional.of(new ItemCost(chosen.secondItem(), chosen.secondCount()));
        return new MerchantOffer(new ItemCost(Items.EMERALD, price), second, parcelStack(chosen), chosen.maxUses(), chosen.xp(), 0.05f);
    }

    public static ItemStack parcelStack(Parcel parcel) {
        ItemStack stack = new ItemStack(HMRegistries.PARCEL_ITEM.get());
        stack.set(HMRegistries.PARCEL.get(), parcel.name());
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable(parcel.name()).withStyle(parcel.color()));
        return stack;
    }
}
