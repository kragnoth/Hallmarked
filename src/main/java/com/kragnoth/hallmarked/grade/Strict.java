package com.kragnoth.hallmarked.grade;

import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.quality.Category;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Load-time validation for pack data. Typos fail loudly instead of silently changing behaviour: an unknown
 * operation or colour rejects the entry (with the file named in the log); unknown keys, such as a misspelled
 * section like "tols", are warned about.
 */
public final class Strict {
    private Strict() {}

    public static final Set<String> MODIFIER_KEYS = Set.of("name", "attribute", "operation", "value", "min", "max");

    /** Section keys (tool, bow, ... bauble) plus the given extras. */
    public static Set<String> withSections(String... extra) {
        Set<String> s = new HashSet<>(Set.of(extra));
        for (Category c : Category.values()) s.add(c.key());
        s.add("enabled");
        return s;
    }

    /** Warns about keys not in {@code allowed}. Skipped for client-side re-parses of synced data. */
    public static void warnUnknownKeys(ResourceLocation id, JsonObject json, Set<String> allowed, String what) {
        if (id.getPath().equals("synced")) return;
        for (String key : json.keySet()) {
            if (key.equals("requires_mods") || key.equals("enabled")) continue; // handled by DataStore for every type
            if (!allowed.contains(key)) Hallmarked.LOG.warn("{} {}: unknown key '{}' is ignored (typo?)", what, id, key);
        }
    }

    public static AttributeModifier.Operation operation(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "addition", "add", "add_value" -> AttributeModifier.Operation.ADD_VALUE;
            case "multiply_base", "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "multiply_total", "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> throw new IllegalArgumentException("unknown operation '" + s + "' (use addition, multiply_base or multiply_total)");
        };
    }

    /** A colour name ("blue", "dark_gray") or legacy code ("§9" / "&9"). */
    public static ChatFormatting color(String s) {
        ChatFormatting f = null;
        if (s.length() == 2 && (s.charAt(0) == '§' || s.charAt(0) == '&')) f = ChatFormatting.getByCode(s.charAt(1));
        else f = ChatFormatting.getByName(s);
        if (f == null || !f.isColor()) throw new IllegalArgumentException("unknown colour '" + s + "'");
        return f;
    }
}
