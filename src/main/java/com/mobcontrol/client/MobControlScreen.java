package com.mobcontrol.client;

import com.mobcontrol.MobRule;
import com.mobcontrol.net.Network;
import com.mobcontrol.net.SaveRulePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Panel de /mobcontrol.
 * Izquierda: todos los mobs (vanilla y de mods). Centro: pestañas Spawn / Dimensiones / Drops del mob elegido.
 * Derecha (solo pestaña Drops): objetos del juego o de tu inventario para añadirlos como loot.
 */
public class MobControlScreen extends Screen {
    private enum Tab { SPAWN, DIMS, DROPS }

    private record MobEntry(ResourceLocation id, String name, String key, ItemStack icon) {}

    private record ItemEntry(ItemStack stack, String key) {}

    private static final int LX = 8, LW = 150, LY = 42;
    private static final int DX = 166, DW = 250;
    private static final int PX = 424;
    private static final int MOB_ROW = 18, DIM_ROW = 18, DROP_ROW = 22;
    private static final int DIM_Y = 52, DIM_H = 108;
    private static final int DROP_Y = 84, DROP_H = 88;
    private static final int STATUS_Y = 258;

    private static List<MobEntry> MOBS;
    private static List<ItemEntry> ALL_ITEMS;

    private final List<ResourceLocation> dims = new ArrayList<>();
    private final Map<ResourceLocation, MobRule> rules = new HashMap<>();

    private ResourceLocation selected = null;
    private Tab tab = Tab.SPAWN;
    private List<MobEntry> mobsFiltered = new ArrayList<>();
    private String mobSearchText = "";
    private int mobScroll = 0, dimScroll = 0, dropScroll = 0, pickScroll = 0;

    private int selDim = -1;
    private boolean dimAllowed = true;
    private String minYText = "", maxYText = "";

    private ItemStack dropItem = ItemStack.EMPTY;
    private String dMin = "1", dMax = "1", dChance = "100";
    private boolean invTab = false;
    private int invTimer = 0;
    private List<ItemEntry> invEntries = new ArrayList<>();
    private List<ItemEntry> itemsFiltered = new ArrayList<>();
    private String itemSearchText = "";

    private String status = "";
    private int statusColor = 0xFFFFFFFF;

    private EditBox mobSearch, itemSearch, minYBox, maxYBox, dMinBox, dMaxBox, dChanceBox;
    private Button toggleSpawnBtn, resetBtn, dimAllowBtn, dimApplyBtn, dimClearBtn, dropsModeBtn, addDropBtn, tabAllBtn, tabInvBtn;
    private final List<Button> tabBtns = new ArrayList<>();

    private int pw, cols, rows, gridY;

    public MobControlScreen(List<ResourceLocation> dimList, List<MobRule> ruleList) {
        super(Component.literal("Control de Mobs"));
        dims.addAll(dimList);
        dims.sort(Comparator.comparing((ResourceLocation d) -> !d.getNamespace().equals("minecraft"))
                .thenComparing(ResourceLocation::toString));
        for (MobRule r : ruleList) rules.put(r.entity, r.copy());
        ensureData();
    }

    // ----------------------------------------------------------------- datos

    @SuppressWarnings("unchecked")
    private static void ensureData() {
        if (MOBS == null) {
            List<MobEntry> list = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) continue;
                // solo entidades "vivas" (mobs): son las que tienen atributos registrados
                if (!DefaultAttributes.hasSupplier((EntityType<? extends LivingEntity>) type)) continue;
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                String name = type.getDescription().getString();
                SpawnEggItem egg = SpawnEggItem.byId(type);
                ItemStack icon = egg == null ? ItemStack.EMPTY : new ItemStack(egg);
                list.add(new MobEntry(id, name, (id + " " + name).toLowerCase(Locale.ROOT), icon));
            }
            list.sort(Comparator.comparing((MobEntry m) -> !m.id().getNamespace().equals("minecraft"))
                    .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
            MOBS = list;
        }
        if (ALL_ITEMS == null) {
            List<ItemEntry> list = new ArrayList<>();
            for (Item it : BuiltInRegistries.ITEM) {
                if (it == Items.AIR) continue;
                ItemStack st = new ItemStack(it);
                list.add(new ItemEntry(st, itemKey(st)));
            }
            ALL_ITEMS = list;
        }
    }

    private static String itemKey(ItemStack st) {
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return (id + " " + st.getHoverName().getString()).toLowerCase(Locale.ROOT);
    }

    private void rebuildInv() {
        List<ItemEntry> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                list.add(new ItemEntry(s.copy(), itemKey(s)));
            }
        }
        invEntries = list;
    }

    private MobEntry mobEntry(ResourceLocation id) {
        for (MobEntry m : MOBS) if (m.id().equals(id)) return m;
        return null;
    }

    private static String dimName(ResourceLocation d) {
        if (d.getNamespace().equals("minecraft")) {
            switch (d.getPath()) {
                case "overworld": return "Overworld";
                case "the_nether": return "Nether";
                case "the_end": return "End";
                default: break;
            }
        }
        return d.toString();
    }

    // ------------------------------------------------------------------ init

    @Override
    protected void init() {
        pw = Math.max(72, this.width - PX - 8);
        cols = Math.max(1, pw / 18);
        gridY = 60;
        rows = Math.max(1, (this.height - gridY - 8) / 18);

        mobSearch = new EditBox(this.font, LX, 22, LW, 16, Component.literal("Buscar mob"));
        mobSearch.setHint(Component.literal("Buscar mob..."));
        mobSearch.setMaxLength(50);
        mobSearch.setValue(mobSearchText);
        mobSearch.setResponder(s -> {
            mobSearchText = s;
            mobScroll = 0;
            refilterMobs();
        });
        this.addRenderableWidget(mobSearch);

        tabBtns.clear();
        String[] labels = {"Spawn", "Dimensiones", "Drops"};
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            Button b = Button.builder(Component.literal(labels[i]), btn -> setTab(t))
                    .bounds(DX + i * 84, 24, 82, 18).build();
            tabBtns.add(b);
            this.addRenderableWidget(b);
        }

        // --- pestaña Spawn
        toggleSpawnBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> toggleSpawn())
                .bounds(DX, 52, DW, 22).build());
        resetBtn = this.addRenderableWidget(Button.builder(Component.literal("Restablecer este mob"), b -> resetMob())
                .bounds(DX, 84, DW, 20).build());

        // --- pestaña Dimensiones
        dimAllowBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
            dimAllowed = !dimAllowed;
            refreshLabels();
        }).bounds(DX, 176, 160, 18).build());
        minYBox = new EditBox(this.font, DX + 34, 198, 56, 16, Component.literal("Y min"));
        minYBox.setMaxLength(6);
        minYBox.setFilter(s -> s.matches("-?\\d{0,5}"));
        minYBox.setValue(minYText);
        minYBox.setResponder(s -> minYText = s);
        this.addRenderableWidget(minYBox);
        maxYBox = new EditBox(this.font, DX + 134, 198, 56, 16, Component.literal("Y max"));
        maxYBox.setMaxLength(6);
        maxYBox.setFilter(s -> s.matches("-?\\d{0,5}"));
        maxYBox.setValue(maxYText);
        maxYBox.setResponder(s -> maxYText = s);
        this.addRenderableWidget(maxYBox);
        dimApplyBtn = this.addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> applyDim())
                .bounds(DX, 220, 120, 20).build());
        dimClearBtn = this.addRenderableWidget(Button.builder(Component.literal("Quitar regla"), b -> clearDim())
                .bounds(DX + 124, 220, 126, 20).build());

        // --- pestaña Drops
        dropsModeBtn = this.addRenderableWidget(Button.builder(Component.literal(""), b -> toggleDropsMode())
                .bounds(DX, 50, DW, 20).build());
        dMinBox = digitBox(DX + 6, 212, "Min", dMin, s -> dMin = s);
        dMaxBox = digitBox(DX + 56, 212, "Max", dMax, s -> dMax = s);
        dChanceBox = digitBox(DX + 106, 212, "Prob", dChance, s -> dChance = s);
        addDropBtn = this.addRenderableWidget(Button.builder(Component.literal("Agregar drop"), b -> addDrop())
                .bounds(DX, 234, 150, 20).build());

        int half = pw / 2;
        tabAllBtn = this.addRenderableWidget(Button.builder(Component.literal("Todos"), b -> setInvTab(false))
                .bounds(PX, 20, half, 16).build());
        tabInvBtn = this.addRenderableWidget(Button.builder(Component.literal("Mi inventario"), b -> setInvTab(true))
                .bounds(PX + half, 20, pw - half, 16).build());
        itemSearch = new EditBox(this.font, PX, 38, pw, 16, Component.literal("Buscar objeto"));
        itemSearch.setHint(Component.literal("Buscar objeto..."));
        itemSearch.setMaxLength(50);
        itemSearch.setValue(itemSearchText);
        itemSearch.setResponder(s -> {
            itemSearchText = s;
            pickScroll = 0;
            refilterItems();
        });
        this.addRenderableWidget(itemSearch);

        if (invTab) rebuildInv();
        refilterMobs();
        refilterItems();
        refreshLabels();
        updateWidgets();
    }

    private EditBox digitBox(int x, int y, String name, String value, java.util.function.Consumer<String> responder) {
        EditBox b = new EditBox(this.font, x, y, 40, 16, Component.literal(name));
        b.setMaxLength(3);
        b.setFilter(s -> s.matches("\\d*"));
        b.setValue(value);
        b.setResponder(responder);
        this.addRenderableWidget(b);
        return b;
    }

    private void refilterMobs() {
        String q = mobSearchText.toLowerCase(Locale.ROOT).trim();
        List<MobEntry> out = new ArrayList<>();
        for (MobEntry m : MOBS) {
            if (q.isEmpty() || m.key().contains(q)) out.add(m);
        }
        mobsFiltered = out;
        mobScroll = Mth.clamp(mobScroll, 0, Math.max(0, mobsFiltered.size() - mobRowsVisible()));
    }

    private void refilterItems() {
        String q = itemSearchText.toLowerCase(Locale.ROOT).trim();
        List<ItemEntry> out = new ArrayList<>();
        for (ItemEntry e : (invTab ? invEntries : ALL_ITEMS)) {
            if (q.isEmpty() || e.key().contains(q)) out.add(e);
        }
        itemsFiltered = out;
        int totalRows = (itemsFiltered.size() + cols - 1) / cols;
        pickScroll = Mth.clamp(pickScroll, 0, Math.max(0, totalRows - rows));
    }

    private int mobListH() {
        return this.height - LY - 8;
    }

    private int mobRowsVisible() {
        return Math.max(1, mobListH() / MOB_ROW);
    }

    private void setInvTab(boolean inv) {
        invTab = inv;
        pickScroll = 0;
        if (inv) rebuildInv();
        refilterItems();
    }

    @Override
    public void tick() {
        super.tick();
        if (invTab && tab == Tab.DROPS && ++invTimer >= 10) {
            invTimer = 0;
            rebuildInv();
            refilterItems();
        }
    }

    private void setTab(Tab t) {
        tab = t;
        setStatus("", 0xFFFFFFFF);
        updateWidgets();
    }

    private void updateWidgets() {
        boolean has = selected != null;
        for (Button b : tabBtns) b.visible = has;
        boolean spawn = has && tab == Tab.SPAWN;
        toggleSpawnBtn.visible = spawn;
        resetBtn.visible = spawn;
        boolean dim = has && tab == Tab.DIMS && selDim >= 0;
        dimAllowBtn.visible = dim;
        dimApplyBtn.visible = dim;
        dimClearBtn.visible = dim;
        minYBox.visible = dim;
        maxYBox.visible = dim;
        boolean drops = has && tab == Tab.DROPS;
        dropsModeBtn.visible = drops;
        addDropBtn.visible = drops;
        dMinBox.visible = drops;
        dMaxBox.visible = drops;
        dChanceBox.visible = drops;
        tabAllBtn.visible = drops;
        tabInvBtn.visible = drops;
        itemSearch.visible = drops;
    }

    private void refreshLabels() {
        MobRule r = selected == null ? null : rules.get(selected);
        boolean off = r != null && r.spawnDisabled;
        toggleSpawnBtn.setMessage(off
                ? Component.literal("Spawn: DESACTIVADO").withStyle(ChatFormatting.RED)
                : Component.literal("Spawn: ACTIVADO").withStyle(ChatFormatting.GREEN));
        boolean rm = r != null && r.removeDefaultDrops;
        dropsModeBtn.setMessage(rm
                ? Component.literal("Drops originales: QUITADOS").withStyle(ChatFormatting.RED)
                : Component.literal("Drops originales: SE MANTIENEN").withStyle(ChatFormatting.GREEN));
        dimAllowBtn.setMessage(dimAllowed
                ? Component.literal("Permitido en esta dimension").withStyle(ChatFormatting.GREEN)
                : Component.literal("Bloqueado en esta dimension").withStyle(ChatFormatting.RED));
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    // ------------------------------------------------------- edicion de reglas

    private MobRule edit() {
        return rules.computeIfAbsent(selected, MobRule::new);
    }

    /** Guarda en el servidor la regla del mob elegido (una regla vacia la borra). */
    private void commit() {
        MobRule r = rules.get(selected);
        if (r != null && r.isDefault()) {
            rules.remove(selected);
            r = null;
        }
        MobRule send = r != null ? r.copy() : new MobRule(selected);
        Network.CHANNEL.sendToServer(new SaveRulePacket(send));
        refreshLabels();
    }

    private void select(ResourceLocation id) {
        selected = id;
        selDim = -1;
        dimScroll = 0;
        dropScroll = 0;
        tab = Tab.SPAWN;
        dropItem = ItemStack.EMPTY;
        setStatus("", 0xFFFFFFFF);
        refreshLabels();
        updateWidgets();
    }

    private void toggleSpawn() {
        MobRule r = edit();
        r.spawnDisabled = !r.spawnDisabled;
        commit();
    }

    private void resetMob() {
        rules.remove(selected);
        commit();
        selDim = -1;
        updateWidgets();
        setStatus("Mob restablecido.", 0xFF55FF55);
    }

    private void selectDim(int idx) {
        selDim = idx;
        ResourceLocation dim = dims.get(idx);
        MobRule r = rules.get(selected);
        MobRule.DimRule d = r == null ? null : r.dims.get(dim);
        dimAllowed = d == null || d.allowed;
        minYText = d == null || d.minY == MobRule.NO_MIN ? "" : String.valueOf(d.minY);
        maxYText = d == null || d.maxY == MobRule.NO_MAX ? "" : String.valueOf(d.maxY);
        minYBox.setValue(minYText);
        maxYBox.setValue(maxYText);
        refreshLabels();
        updateWidgets();
    }

    private static int parseY(String s, int def) {
        if (s == null || s.isEmpty() || s.equals("-")) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseNum(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void applyDim() {
        if (selected == null || selDim < 0 || selDim >= dims.size()) return;
        int mn = parseY(minYText, MobRule.NO_MIN);
        int mx = parseY(maxYText, MobRule.NO_MAX);
        if (mn > mx) {
            setStatus("El minimo no puede ser mayor que el maximo.", 0xFFFF5555);
            return;
        }
        ResourceLocation dim = dims.get(selDim);
        MobRule.DimRule d = new MobRule.DimRule(dimAllowed, mn, mx);
        MobRule r = edit();
        if (d.isDefault()) r.dims.remove(dim);
        else r.dims.put(dim, d);
        commit();
        setStatus("Guardado.", 0xFF55FF55);
    }

    private void clearDim() {
        if (selected == null || selDim < 0 || selDim >= dims.size()) return;
        MobRule r = edit();
        r.dims.remove(dims.get(selDim));
        commit();
        selectDim(selDim);
        setStatus("Regla de la dimension quitada.", 0xFF55FF55);
    }

    private void toggleDropsMode() {
        MobRule r = edit();
        r.removeDefaultDrops = !r.removeDefaultDrops;
        commit();
    }

    private void addDrop() {
        if (dropItem.isEmpty()) {
            setStatus("Elige un item: clic en el espacio y luego a la derecha.", 0xFFFF5555);
            return;
        }
        int mn = parseNum(dMin, 1);
        int mx = parseNum(dMax, mn);
        int ch = parseNum(dChance, 100);
        if (mx < mn) {
            setStatus("El maximo no puede ser menor que el minimo.", 0xFFFF5555);
            return;
        }
        if (ch < 1 || ch > 100) {
            setStatus("La probabilidad va de 1 a 100.", 0xFFFF5555);
            return;
        }
        edit().drops.add(new MobRule.DropEntry(dropItem, mn, mx, ch));
        commit();
        setStatus("Drop agregado.", 0xFF55FF55);
    }

    // ---------------------------------------------------------------- input

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int pickerIndexAt(double mx, double my) {
        if (!inside(mx, my, PX, gridY, cols * 18, rows * 18)) return -1;
        int c = (int) ((mx - PX) / 18);
        int r = (int) ((my - gridY) / 18);
        int idx = (pickScroll + r) * cols + c;
        return idx >= 0 && idx < itemsFiltered.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        this.setFocused((GuiEventListener) null);

        // lista de mobs
        if (inside(mx, my, LX, LY, LW, mobListH())) {
            int idx = mobScroll + (int) ((my - LY) / MOB_ROW);
            if (idx >= 0 && idx < mobsFiltered.size()) {
                select(mobsFiltered.get(idx).id());
                return true;
            }
        }
        if (selected == null) return false;

        if (tab == Tab.DIMS && inside(mx, my, DX, DIM_Y, DW, DIM_H)) {
            int idx = dimScroll + (int) ((my - DIM_Y) / DIM_ROW);
            if (idx >= 0 && idx < dims.size()) {
                selectDim(idx);
                return true;
            }
        }
        if (tab == Tab.DROPS) {
            if (inside(mx, my, DX + 6, 180, 18, 18)) {
                if (button == 1) dropItem = ItemStack.EMPTY;
                return true;
            }
            MobRule r = rules.get(selected);
            if (r != null && inside(mx, my, DX, DROP_Y, DW, DROP_H)) {
                int idx = dropScroll + (int) ((my - DROP_Y) / DROP_ROW);
                if (idx >= 0 && idx < r.drops.size() && mx >= DX + DW - 14) {
                    r.drops.remove(idx);
                    commit();
                    return true;
                }
            }
            int pi = pickerIndexAt(mx, my);
            if (pi >= 0) {
                ItemStack c = itemsFiltered.get(pi).stack().copy();
                c.setCount(1);
                dropItem = c;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int dir = delta > 0 ? -1 : 1;
        if (mx < LX + LW + 4) {
            mobScroll = Mth.clamp(mobScroll + dir, 0, Math.max(0, mobsFiltered.size() - mobRowsVisible()));
            return true;
        }
        if (selected != null && tab == Tab.DIMS && inside(mx, my, DX, DIM_Y, DW, DIM_H)) {
            dimScroll = Mth.clamp(dimScroll + dir, 0, Math.max(0, dims.size() - DIM_H / DIM_ROW));
            return true;
        }
        if (selected != null && tab == Tab.DROPS) {
            if (mx >= PX) {
                int totalRows = (itemsFiltered.size() + cols - 1) / cols;
                pickScroll = Mth.clamp(pickScroll + dir, 0, Math.max(0, totalRows - rows));
                return true;
            }
            MobRule r = rules.get(selected);
            if (r != null && inside(mx, my, DX, DROP_Y, DW, DROP_H)) {
                dropScroll = Mth.clamp(dropScroll + dir, 0, Math.max(0, r.drops.size() - DROP_H / DROP_ROW));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --------------------------------------------------------------- render

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void customMarker(GuiGraphics g, ItemStack st, int x, int y) {
        if (st.isEmpty() || !st.hasTag()) return;
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.fill(x + 12, y + 1, x + 16, y + 5, 0xFF55FFFF);
        g.pose().popPose();
    }

    private String dimStatus(MobRule r, ResourceLocation d) {
        MobRule.DimRule dr = r == null ? null : r.dims.get(d);
        if (dr == null) return "Permitido";
        if (!dr.allowed) return "Bloqueado";
        return "Y " + (dr.minY == MobRule.NO_MIN ? "*" : String.valueOf(dr.minY))
                + " a " + (dr.maxY == MobRule.NO_MAX ? "*" : String.valueOf(dr.maxY));
    }

    private int dimStatusColor(MobRule r, ResourceLocation d) {
        MobRule.DimRule dr = r == null ? null : r.dims.get(d);
        if (dr == null) return 0xFFAAAAAA;
        return dr.allowed ? 0xFFFFFF55 : 0xFFFF5555;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);

        // ---- izquierda: mobs
        g.drawString(this.font, Component.literal("Mobs (" + mobsFiltered.size() + ")").withStyle(ChatFormatting.GOLD), LX, 10, 0xFFFFFFFF);
        g.fill(LX - 2, LY - 2, LX + LW + 2, LY + mobListH() + 2, 0x90000000);
        g.enableScissor(LX, LY, LX + LW, LY + mobListH());
        int vis = mobRowsVisible() + 1;
        for (int i = 0; i < vis; i++) {
            int idx = mobScroll + i;
            if (idx >= mobsFiltered.size()) break;
            MobEntry m = mobsFiltered.get(idx);
            int y = LY + i * MOB_ROW;
            boolean sel = m.id().equals(selected);
            boolean hover = inside(mouseX, mouseY, LX, y, LW, MOB_ROW);
            if (sel) g.fill(LX, y, LX + LW, y + MOB_ROW - 1, 0x80FFFF55);
            else if (hover) g.fill(LX, y, LX + LW, y + MOB_ROW - 1, 0x50FFFFFF);
            if (!m.icon().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(LX + 1f, y + 1f, 0f);
                g.pose().scale(0.9f, 0.9f, 1f);
                g.renderItem(m.icon(), 0, 0);
                g.pose().popPose();
            } else {
                g.fill(LX + 3, y + 3, LX + 14, y + 14, 0xFF555555);
            }
            MobRule r = rules.get(m.id());
            int color = 0xFFFFFFFF;
            if (r != null) color = r.spawnDisabled ? 0xFFFF5555 : 0xFFFFFF55;
            g.drawString(this.font, this.font.plainSubstrByWidth(m.name(), LW - 24), LX + 20, y + 5, color);
        }
        g.disableScissor();

        // ---- centro: detalle
        if (selected == null) {
            g.drawString(this.font, Component.literal("Control de Mobs").withStyle(ChatFormatting.GOLD), DX, 10, 0xFFFFFFFF);
            g.drawString(this.font, "Elige un mob de la lista de la izquierda.", DX, 30, 0xFFAAAAAA);
            g.drawString(this.font, "Rojo = spawn desactivado, amarillo = tiene reglas.", DX, 44, 0xFF888888);
            g.drawString(this.font, "Cada pestaña (Spawn / Dimensiones / Drops)", DX, 64, 0xFF888888);
            g.drawString(this.font, "se guarda al instante en el mundo.", DX, 74, 0xFF888888);
        } else {
            MobEntry me = mobEntry(selected);
            String title = (me == null ? selected.toString() : me.name()) + "  (" + selected + ")";
            g.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(title, DW)).withStyle(ChatFormatting.GOLD), DX, 10, 0xFFFFFFFF);
            renderTab(g, mouseX, mouseY);
        }

        // ---- derecha: objetos (solo drops)
        if (selected != null && tab == Tab.DROPS) {
            g.drawString(this.font, Component.literal("Objetos (rueda: desplazar)").withStyle(ChatFormatting.GOLD), PX, 10, 0xFFFFFFFF);
            g.fill(PX - 2, gridY - 2, PX + cols * 18 + 2, gridY + rows * 18 + 2, 0x90000000);
            if (invTab && itemsFiltered.isEmpty()) {
                g.drawString(this.font, "Inventario vacio.", PX + 4, gridY + 6, 0xFFAAAAAA);
            }
            g.enableScissor(PX, gridY, PX + cols * 18, gridY + rows * 18);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = (pickScroll + r) * cols + c;
                    if (idx >= itemsFiltered.size()) break;
                    int x = PX + c * 18;
                    int y = gridY + r * 18;
                    ItemStack st = itemsFiltered.get(idx).stack();
                    g.renderItem(st, x + 1, y + 1);
                    customMarker(g, st, x + 1, y + 1);
                    if (inside(mouseX, mouseY, x, y, 18, 18)) g.fill(x, y, x + 18, y + 18, 0x80FFFFFF);
                }
            }
            g.disableScissor();
        }

        super.render(g, mouseX, mouseY, partial);

        // resaltar pestañas activas
        if (selected != null) {
            Button tb = tabBtns.get(tab.ordinal());
            outline(g, tb.getX() - 1, tb.getY() - 1, tb.getWidth() + 2, tb.getHeight() + 2, 0xFFFFFF55);
            if (tab == Tab.DROPS) {
                Button ib = invTab ? tabInvBtn : tabAllBtn;
                outline(g, ib.getX() - 1, ib.getY() - 1, ib.getWidth() + 2, ib.getHeight() + 2, 0xFFFFFF55);
            }
        }

        // tooltips de items
        ItemStack tip = ItemStack.EMPTY;
        if (selected != null && tab == Tab.DROPS) {
            if (inside(mouseX, mouseY, DX + 6, 180, 18, 18)) tip = dropItem;
            if (tip.isEmpty()) {
                int pi = pickerIndexAt(mouseX, mouseY);
                if (pi >= 0) tip = itemsFiltered.get(pi).stack();
            }
            if (tip.isEmpty()) {
                MobRule r = rules.get(selected);
                if (r != null && inside(mouseX, mouseY, DX, DROP_Y, DW, DROP_H)) {
                    int idx = dropScroll + (mouseY - DROP_Y) / DROP_ROW;
                    if (idx >= 0 && idx < r.drops.size()) tip = r.drops.get(idx).stack;
                }
            }
        }
        if (!tip.isEmpty()) g.renderTooltip(this.font, tip, mouseX, mouseY);
    }

    private void renderTab(GuiGraphics g, int mouseX, int mouseY) {
        MobRule r = rules.get(selected);
        switch (tab) {
            case SPAWN -> {
                g.drawString(this.font, "Desactivado: este mob no aparece solo en ninguna dimension.", DX, 112, 0xFFAAAAAA);
                g.drawString(this.font, "Cuenta: spawn natural, spawners, estructuras, patrullas.", DX, 124, 0xFF888888);
                g.drawString(this.font, "No bloquea: huevos, comandos, dispensadores, crianza.", DX, 134, 0xFF888888);
                int nd = r == null ? 0 : r.dims.size();
                int np = r == null ? 0 : r.drops.size();
                boolean rm = r != null && r.removeDefaultDrops;
                g.drawString(this.font, "Dimensiones con regla: " + nd, DX, 156, 0xFFFFFFFF);
                g.drawString(this.font, "Drops extra: " + np + "   Drops originales: " + (rm ? "quitados" : "se mantienen"), DX, 168, 0xFFFFFFFF);
            }
            case DIMS -> {
                g.fill(DX - 2, DIM_Y - 2, DX + DW + 2, DIM_Y + DIM_H + 2, 0x90000000);
                g.enableScissor(DX, DIM_Y, DX + DW, DIM_Y + DIM_H);
                int rowsVis = DIM_H / DIM_ROW + 1;
                for (int i = 0; i < rowsVis; i++) {
                    int idx = dimScroll + i;
                    if (idx >= dims.size()) break;
                    ResourceLocation d = dims.get(idx);
                    int y = DIM_Y + i * DIM_ROW;
                    boolean sel = idx == selDim;
                    boolean hover = inside(mouseX, mouseY, DX, y, DW, DIM_ROW);
                    g.fill(DX, y, DX + DW, y + DIM_ROW - 1, sel ? 0x80FFFF55 : (hover ? 0x50FFFFFF : 0x30FFFFFF));
                    String st = dimStatus(r, d);
                    int stw = this.font.width(st);
                    g.drawString(this.font, this.font.plainSubstrByWidth(dimName(d), DW - stw - 14), DX + 4, y + 5, 0xFFFFFFFF);
                    g.drawString(this.font, st, DX + DW - stw - 4, y + 5, dimStatusColor(r, d));
                }
                g.disableScissor();
                if (selDim >= 0 && selDim < dims.size()) {
                    g.drawString(this.font, this.font.plainSubstrByWidth("Dimension: " + dimName(dims.get(selDim)), DW), DX, 164, 0xFFFFFF55);
                    g.drawString(this.font, "Y min", DX, 202, 0xFFFFFFFF);
                    g.drawString(this.font, "Y max", DX + 100, 202, 0xFFFFFFFF);
                    g.drawString(this.font, "Vacio = sin limite. La altura es la del punto de aparicion.", DX, 244, 0xFF888888);
                } else {
                    g.drawString(this.font, "Clic en una dimension para editarla.", DX, 164, 0xFFAAAAAA);
                    g.drawString(this.font, "Sin regla = el mob puede aparecer ahi.", DX, 176, 0xFF888888);
                }
            }
            case DROPS -> {
                g.drawString(this.font, "Drops extra (se suman a los originales)", DX, 74, 0xFFFFFFFF);
                g.fill(DX - 2, DROP_Y - 2, DX + DW + 2, DROP_Y + DROP_H + 2, 0x90000000);
                if (r == null || r.drops.isEmpty()) {
                    g.drawString(this.font, "Sin drops extra.", DX + 4, DROP_Y + 6, 0xFFAAAAAA);
                }
                g.enableScissor(DX, DROP_Y, DX + DW, DROP_Y + DROP_H);
                if (r != null) {
                    int rowsVis = DROP_H / DROP_ROW + 1;
                    for (int i = 0; i < rowsVis; i++) {
                        int idx = dropScroll + i;
                        if (idx >= r.drops.size()) break;
                        MobRule.DropEntry d = r.drops.get(idx);
                        int y = DROP_Y + i * DROP_ROW;
                        boolean hover = inside(mouseX, mouseY, DX, y, DW, DROP_ROW);
                        g.fill(DX, y, DX + DW, y + DROP_ROW - 1, hover ? 0x60FFFFFF : 0x30FFFFFF);
                        g.renderItem(d.stack, DX + 3, y + 2);
                        customMarker(g, d.stack, DX + 3, y + 2);
                        String line = d.stack.getHoverName().getString();
                        String amount = "x" + (d.min == d.max ? String.valueOf(d.min) : d.min + "-" + d.max) + "  " + d.chance + "%";
                        g.drawString(this.font, this.font.plainSubstrByWidth(line, DW - 40), DX + 24, y + 2, 0xFFFFFFFF);
                        g.drawString(this.font, amount, DX + 24, y + 12, 0xFFAAAAAA);
                        g.fill(DX + DW - 14, y + 5, DX + DW - 2, y + 17, 0xFFAA2222);
                        g.drawString(this.font, "x", DX + DW - 10, y + 7, 0xFFFFFFFF);
                    }
                }
                g.disableScissor();

                g.drawString(this.font, "Nuevo drop:", DX, 168, 0xFFFFFF55);
                // espacio del item
                g.fill(DX + 6, 180, DX + 24, 198, 0xFF373737);
                g.fill(DX + 7, 181, DX + 23, 197, 0xFF8B8B8B);
                if (!dropItem.isEmpty()) {
                    g.renderItem(dropItem, DX + 7, 181);
                    customMarker(g, dropItem, DX + 7, 181);
                }
                g.drawString(this.font, "<- item (elige a la derecha, clic der. vacia)", DX + 30, 185, 0xFFAAAAAA);
                g.drawString(this.font, "Min", DX + 6, 202, 0xFFFFFFFF);
                g.drawString(this.font, "Max", DX + 56, 202, 0xFFFFFFFF);
                g.drawString(this.font, "Prob %", DX + 106, 202, 0xFFFFFFFF);
            }
            default -> { }
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(status, DW), DX, STATUS_Y, statusColor);
    }
}
