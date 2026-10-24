package com.mobcontrol;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Todas las reglas de un tipo de mob: spawn, dimensiones/alturas y drops. */
public final class MobRule {
    public static final int NO_MIN = Integer.MIN_VALUE;
    public static final int NO_MAX = Integer.MAX_VALUE;
    private static final int MAX_DIMS = 300;
    private static final int MAX_DROPS = 100;

    /** Regla de una dimension para este mob. */
    public static final class DimRule {
        public boolean allowed;
        public int minY;
        public int maxY;

        public DimRule(boolean allowed, int minY, int maxY) {
            this.allowed = allowed;
            this.minY = minY;
            this.maxY = maxY;
        }

        public boolean isDefault() {
            return allowed && minY == NO_MIN && maxY == NO_MAX;
        }

        public DimRule copy() {
            return new DimRule(allowed, minY, maxY);
        }
    }

    /** Un drop extra: item (puede ser custom, con NBT), cantidad min-max y probabilidad en %. */
    public static final class DropEntry {
        public ItemStack stack;
        public int min;
        public int max;
        public int chance;

        public DropEntry(ItemStack stack, int min, int max, int chance) {
            ItemStack s = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!s.isEmpty()) s.setCount(1);
            this.stack = s;
            this.min = Math.max(0, Math.min(64, min));
            this.max = Math.max(this.min, Math.min(64, max));
            this.chance = Math.max(1, Math.min(100, chance));
        }

        public DropEntry copy() {
            return new DropEntry(stack, min, max, chance);
        }
    }

    public final ResourceLocation entity;
    public boolean spawnDisabled;
    public boolean removeDefaultDrops;
    public final Map<ResourceLocation, DimRule> dims = new LinkedHashMap<>();
    public final List<DropEntry> drops = new ArrayList<>();

    public MobRule(ResourceLocation entity) {
        this.entity = entity;
    }

    public boolean isDefault() {
        return !spawnDisabled && !removeDefaultDrops && dims.isEmpty() && drops.isEmpty();
    }

    public MobRule copy() {
        MobRule r = new MobRule(entity);
        r.spawnDisabled = spawnDisabled;
        r.removeDefaultDrops = removeDefaultDrops;
        for (Map.Entry<ResourceLocation, DimRule> e : dims.entrySet()) r.dims.put(e.getKey(), e.getValue().copy());
        for (DropEntry d : drops) r.drops.add(d.copy());
        return r;
    }

    /** Limpia valores raros (se usa en el servidor antes de guardar). */
    public void sanitize() {
        Iterator<Map.Entry<ResourceLocation, DimRule>> it = dims.entrySet().iterator();
        int n = 0;
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, DimRule> e = it.next();
            if (e.getValue().isDefault() || ++n > MAX_DIMS) it.remove();
        }
        drops.removeIf(d -> d.stack.isEmpty());
        while (drops.size() > MAX_DROPS) drops.remove(drops.size() - 1);
    }

    // ---- red ----
    public void write(FriendlyByteBuf b) {
        b.writeResourceLocation(entity);
        b.writeBoolean(spawnDisabled);
        b.writeBoolean(removeDefaultDrops);
        b.writeVarInt(dims.size());
        for (Map.Entry<ResourceLocation, DimRule> e : dims.entrySet()) {
            b.writeResourceLocation(e.getKey());
            b.writeBoolean(e.getValue().allowed);
            b.writeInt(e.getValue().minY);
            b.writeInt(e.getValue().maxY);
        }
        b.writeVarInt(drops.size());
        for (DropEntry d : drops) {
            b.writeItem(d.stack);
            b.writeVarInt(d.min);
            b.writeVarInt(d.max);
            b.writeVarInt(d.chance);
        }
    }

    public static MobRule read(FriendlyByteBuf b) {
        MobRule r = new MobRule(b.readResourceLocation());
        r.spawnDisabled = b.readBoolean();
        r.removeDefaultDrops = b.readBoolean();
        int nd = Math.min(b.readVarInt(), MAX_DIMS);
        for (int i = 0; i < nd; i++) {
            ResourceLocation dim = b.readResourceLocation();
            boolean allowed = b.readBoolean();
            int mn = b.readInt();
            int mx = b.readInt();
            r.dims.put(dim, new DimRule(allowed, mn, mx));
        }
        int np = Math.min(b.readVarInt(), MAX_DROPS);
        for (int i = 0; i < np; i++) {
            ItemStack st = b.readItem();
            int mn = b.readVarInt();
            int mx = b.readVarInt();
            int ch = b.readVarInt();
            r.drops.add(new DropEntry(st, mn, mx, ch));
        }
        return r;
    }

    // ---- guardado ----
    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("entity", entity.toString());
        t.putBoolean("off", spawnDisabled);
        t.putBoolean("nodrops", removeDefaultDrops);
        ListTag dl = new ListTag();
        for (Map.Entry<ResourceLocation, DimRule> e : dims.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.getKey().toString());
            c.putBoolean("allowed", e.getValue().allowed);
            c.putInt("min", e.getValue().minY);
            c.putInt("max", e.getValue().maxY);
            dl.add(c);
        }
        t.put("dims", dl);
        ListTag pl = new ListTag();
        for (DropEntry d : drops) {
            CompoundTag c = new CompoundTag();
            c.put("item", d.stack.save(new CompoundTag()));
            c.putInt("min", d.min);
            c.putInt("max", d.max);
            c.putInt("chance", d.chance);
            pl.add(c);
        }
        t.put("drops", pl);
        return t;
    }

    public static MobRule fromTag(CompoundTag t) {
        MobRule r = new MobRule(new ResourceLocation(t.getString("entity")));
        r.spawnDisabled = t.getBoolean("off");
        r.removeDefaultDrops = t.getBoolean("nodrops");
        ListTag dl = t.getList("dims", Tag.TAG_COMPOUND);
        for (int i = 0; i < dl.size(); i++) {
            CompoundTag c = dl.getCompound(i);
            r.dims.put(new ResourceLocation(c.getString("dim")),
                    new DimRule(c.getBoolean("allowed"), c.getInt("min"), c.getInt("max")));
        }
        ListTag pl = t.getList("drops", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            CompoundTag c = pl.getCompound(i);
            r.drops.add(new DropEntry(ItemStack.of(c.getCompound("item")), c.getInt("min"), c.getInt("max"), c.getInt("chance")));
        }
        r.sanitize();
        return r;
    }
}
