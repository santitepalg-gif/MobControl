package com.mobcontrol;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reglas guardadas por mundo. */
public class RulesData extends SavedData {
    private static final String NAME = "mobcontrol_reglas";

    public final Map<ResourceLocation, MobRule> rules = new LinkedHashMap<>();

    public static RulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RulesData::load, RulesData::new, NAME);
    }

    public static RulesData load(CompoundTag tag) {
        RulesData d = new RulesData();
        ListTag l = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            try {
                MobRule r = MobRule.fromTag(l.getCompound(i));
                if (!r.isDefault()) d.rules.put(r.entity, r);
            } catch (RuntimeException ignored) {
                // regla dañada: se omite
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (MobRule r : rules.values()) l.add(r.toTag());
        tag.put("rules", l);
        return tag;
    }
}
