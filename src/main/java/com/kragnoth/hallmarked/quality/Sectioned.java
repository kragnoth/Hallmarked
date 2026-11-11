package com.kragnoth.hallmarked.quality;

/** Anything defined per equipment {@link Category} (qualities and affixes). */
public interface Sectioned {
    boolean has(Category c);
}
