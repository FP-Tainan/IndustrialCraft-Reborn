package net.ic2reborn.menu.layout;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.menu.MachineMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Descrição de uma GUI de máquina no estilo do IC2 Experimental.
 *
 * Existem dois tipos, iguais aos do mod original:
 * <ul>
 *   <li><b>dinâmica</b> (arquivos assets/ic2/guidef/*.xml): fundo montado com pedaços de
 *   common.png, e cada slot/gauge desenhado por cima;</li>
 *   <li><b>texturizada</b> (classes Gui* escritas à mão): uma textura inteira de fundo,
 *   com os slots já desenhados nela.</li>
 * </ul>
 * Coordenadas de slot são as do fundo do slot (como no guidef); o slot de item
 * fica centralizado dentro dele.
 */
public final class MachineLayout {
    public static final Identifier COMMON_TEXTURE = gui("common.png");
    public static final int TEXT_COLOR = 0x404040;

    public enum SlotStyle {
        NORMAL(103, 7, 18, 18, true),
        LARGE(99, 35, 26, 26, true),
        PLAIN(0, 0, 16, 16, false);

        public final int u;
        public final int v;
        public final int width;
        public final int height;
        public final boolean hasBackground;

        SlotStyle(int u, int v, int width, int height, boolean hasBackground) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
            this.hasBackground = hasBackground;
        }
    }

    public enum GaugeSource { ENERGY, PROGRESS, HEAT, NONE }

    public enum TankStyle { NORMAL, PLAIN }

    public record SlotDef(int x, int y, SlotStyle style, boolean output) {
        public int slotX() {
            return this.x + (this.style.width - 16) / 2;
        }

        public int slotY() {
            return this.y + (this.style.height - 16) / 2;
        }
    }

    public record GaugeDef(int x, int y, GaugeStyle style, GaugeSource source) {}

    /** {@code index}: 0 = tanque principal (entrada), 1 = tanque de saída. */
    public record TankDef(int x, int y, int width, int height, TankStyle style, int index) {}

    public record ImageDef(Identifier texture, int x, int y, int u, int v, int width, int height,
                           int textureWidth, int textureHeight, Predicate<MachineMenu> visible) {}

    /** Texto; se width/height > 0 ele é centralizado dentro dessa caixa. */
    public record TextDef(Function<MachineMenu, Component> text, int x, int y, int width, int height, int color) {}

    private final int width;
    private final int height;
    private final @Nullable Identifier background;
    private final int inventoryX;
    private final int inventoryY;
    private final boolean showInventoryTitle;
    private final List<SlotDef> slots;
    private final List<GaugeDef> gauges;
    private final List<TankDef> tanks;
    private final List<ImageDef> images;
    private final List<TextDef> texts;

    private MachineLayout(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.background = builder.background;
        this.inventoryX = builder.inventoryX;
        this.inventoryY = builder.inventoryY;
        this.showInventoryTitle = builder.showInventoryTitle;
        this.slots = List.copyOf(builder.slots);
        this.gauges = List.copyOf(builder.gauges);
        this.tanks = List.copyOf(builder.tanks);
        this.images = List.copyOf(builder.images);
        this.texts = List.copyOf(builder.texts);
    }

    public static Identifier gui(String path) {
        return Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/gui/" + path);
    }

    /** GUI montada a partir de common.png, como os guidef XML do IC2. */
    public static Builder dynamic(int width, int height) {
        return new Builder(width, height, null);
    }

    /** GUI com textura inteira de fundo, como as classes Gui* do IC2. */
    public static Builder textured(String texture, int height) {
        return new Builder(176, height, gui(texture));
    }

    public int width() { return this.width; }
    public int height() { return this.height; }
    public @Nullable Identifier background() { return this.background; }
    public boolean isDynamic() { return this.background == null; }
    public int inventoryX() { return this.inventoryX; }
    public int inventoryY() { return this.inventoryY; }
    public boolean showInventoryTitle() { return this.showInventoryTitle; }
    public List<SlotDef> slots() { return this.slots; }
    public List<GaugeDef> gauges() { return this.gauges; }
    public List<TankDef> tanks() { return this.tanks; }
    public List<ImageDef> images() { return this.images; }
    public List<TextDef> texts() { return this.texts; }

    public int slotCount() {
        return this.slots.size();
    }

    public boolean isOutputSlot(int index) {
        return index >= 0 && index < this.slots.size() && this.slots.get(index).output();
    }

    public static final class Builder {
        private final int width;
        private final int height;
        private final @Nullable Identifier background;
        private int inventoryX;
        private int inventoryY;
        private boolean showInventoryTitle;
        private final List<SlotDef> slots = new ArrayList<>();
        private final List<GaugeDef> gauges = new ArrayList<>();
        private final List<TankDef> tanks = new ArrayList<>();
        private final List<ImageDef> images = new ArrayList<>();
        private final List<TextDef> texts = new ArrayList<>();

        private Builder(int width, int height, @Nullable Identifier background) {
            this.width = width;
            this.height = height;
            this.background = background;
            // <playerInventory x="7" y="83"/> no guidef; ContainerFullInv usa height - 82 para o slot
            this.inventoryX = 7;
            this.inventoryY = height - 83;
            this.showInventoryTitle = background == null;
        }

        public Builder inventory(int x, int y) {
            this.inventoryX = x;
            this.inventoryY = y;
            return this;
        }

        public Builder noInventoryTitle() {
            this.showInventoryTitle = false;
            return this;
        }

        // ── slots em coordenadas de fundo (guidef) ───────────────────────
        public Builder slot(int x, int y) {
            return add(x, y, SlotStyle.NORMAL, false);
        }

        public Builder output(int x, int y) {
            return add(x, y, SlotStyle.NORMAL, true);
        }

        public Builder largeOutput(int x, int y) {
            return add(x, y, SlotStyle.LARGE, true);
        }

        public Builder plain(int x, int y) {
            return add(x, y, SlotStyle.PLAIN, false);
        }

        public Builder plainOutput(int x, int y) {
            return add(x, y, SlotStyle.PLAIN, true);
        }

        public Builder grid(int x, int y, int cols, int rows, boolean output) {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    add(x + col * 18, y + row * 18, SlotStyle.NORMAL, output);
                }
            }
            return this;
        }

        /** <slotgrid name="upgrade" cols="1"/> com os 4 slots de upgrade padrão. */
        public Builder upgrades(int x, int y) {
            return upgrades(x, y, 4);
        }

        /** O IC2 desenha tantos slots quanto o InvSlotUpgrade da máquina tem. */
        public Builder upgrades(int x, int y, int count) {
            return grid(x, y, 1, count, false);
        }

        // ── slots em coordenadas de Slot (Container* do IC2) ─────────────
        public Builder slotAt(int slotX, int slotY) {
            return slot(slotX - 1, slotY - 1);
        }

        public Builder outputAt(int slotX, int slotY) {
            return output(slotX - 1, slotY - 1);
        }

        public Builder gridAt(int slotX, int slotY, int cols, int rows, boolean output) {
            return grid(slotX - 1, slotY - 1, cols, rows, output);
        }

        private Builder add(int x, int y, SlotStyle style, boolean output) {
            this.slots.add(new SlotDef(x, y, style, output));
            return this;
        }

        // ── gauges / tanques / imagens / textos ──────────────────────────
        public Builder energy(int x, int y) {
            return gauge(x, y, GaugeStyle.BOLT, GaugeSource.ENERGY);
        }

        public Builder energyBar(int x, int y) {
            return gauge(x, y, GaugeStyle.BAR, GaugeSource.ENERGY);
        }

        public Builder progress(int x, int y, GaugeStyle style) {
            return gauge(x, y, style, GaugeSource.PROGRESS);
        }

        public Builder gauge(int x, int y, GaugeStyle style, GaugeSource source) {
            this.gauges.add(new GaugeDef(x, y, style, source));
            return this;
        }

        public Builder tank(int x, int y) {
            this.tanks.add(new TankDef(x, y, 20, 55, TankStyle.NORMAL, this.tanks.size()));
            return this;
        }

        public Builder plainTank(int x, int y, int width, int height) {
            this.tanks.add(new TankDef(x, y, width, height, TankStyle.PLAIN, this.tanks.size()));
            return this;
        }

        public Builder image(String texture, int x, int y, int u, int v, int width, int height,
                             int textureWidth, int textureHeight) {
            this.images.add(new ImageDef(gui(texture), x, y, u, v, width, height, textureWidth, textureHeight, menu -> true));
            return this;
        }

        /** Imagem desenhada só quando {@code visible} vale (o <only if> do guidef). */
        public Builder imageIf(Predicate<MachineMenu> visible, String texture, int x, int y, int u, int v,
                               int width, int height, int textureWidth, int textureHeight) {
            this.images.add(new ImageDef(gui(texture), x, y, u, v, width, height, textureWidth, textureHeight, visible));
            return this;
        }

        public Builder text(Component text, int x, int y) {
            return text(menu -> text, x, y, 0, 0, TEXT_COLOR);
        }

        public Builder text(Function<MachineMenu, Component> text, int x, int y) {
            return text(text, x, y, 0, 0, TEXT_COLOR);
        }

        public Builder text(Function<MachineMenu, Component> text, int x, int y, int width, int height, int color) {
            this.texts.add(new TextDef(text, x, y, width, height, color));
            return this;
        }

        public MachineLayout build() {
            return new MachineLayout(this);
        }
    }
}
