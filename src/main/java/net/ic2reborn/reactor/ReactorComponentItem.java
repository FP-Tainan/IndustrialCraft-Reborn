package net.ic2reborn.reactor;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Componentes do reator nuclear do IC2 ({@code ItemReactor*}): barras de combustível de urânio e MOX,
 * células de resfriamento, ventoinhas, trocadores de calor, refletores de nêutrons, placas e
 * condensadores. O desgaste (ou o calor guardado) fica no componente {@code reactor_damage}, para as
 * peças novas continuarem empilhando.
 */
public class ReactorComponentItem extends Item {
    public enum Kind { FUEL, MOX, HEAT_STORAGE, VENT, SPREAD_VENT, EXCHANGER, REFLECTOR, IRIDIUM_REFLECTOR, PLATING, CONDENSATOR }

    private final Kind kind;
    /** Durabilidade (combustível, refletor) ou calor máximo (células, ventoinhas, trocadores, condensadores). */
    private final int maxDamage;
    private final int cells;
    /** Ventoinha: próprio / do reator. Trocador: vizinhos / reator. Ventoinha de componentes: vizinhos. */
    private final int sideValue;
    private final int reactorValue;
    private final float effectModifier;
    private final String depleted;

    private ReactorComponentItem(Properties properties, Kind kind, int maxDamage, int cells, int sideValue, int reactorValue,
                                 float effectModifier, String depleted) {
        super(properties);
        this.kind = kind;
        this.maxDamage = maxDamage;
        this.cells = cells;
        this.sideValue = sideValue;
        this.reactorValue = reactorValue;
        this.effectModifier = effectModifier;
        this.depleted = depleted;
    }

    /** Barra de urânio (20.000 ciclos) ou MOX (10.000), com 1, 2 ou 4 células. */
    public static ReactorComponentItem fuel(Properties properties, int cells, boolean mox, String depleted) {
        return new ReactorComponentItem(properties, mox ? Kind.MOX : Kind.FUEL, mox ? 10_000 : 20_000, cells, 0, 0, 1.0F, depleted);
    }

    public static ReactorComponentItem heatStorage(Properties properties, int heat) {
        return new ReactorComponentItem(properties.stacksTo(64), Kind.HEAT_STORAGE, heat, 0, 0, 0, 1.0F, "");
    }

    public static ReactorComponentItem vent(Properties properties, int heat, int selfVent, int reactorVent) {
        return new ReactorComponentItem(properties, Kind.VENT, heat, 0, selfVent, reactorVent, 1.0F, "");
    }

    public static ReactorComponentItem spreadVent(Properties properties, int sideVent) {
        return new ReactorComponentItem(properties, Kind.SPREAD_VENT, 0, 0, sideVent, 0, 1.0F, "");
    }

    public static ReactorComponentItem exchanger(Properties properties, int heat, int side, int reactor) {
        return new ReactorComponentItem(properties, Kind.EXCHANGER, heat, 0, side, reactor, 1.0F, "");
    }

    public static ReactorComponentItem reflector(Properties properties, int durability) {
        return new ReactorComponentItem(properties, durability > 0 ? Kind.REFLECTOR : Kind.IRIDIUM_REFLECTOR, durability, 0, 0, 0, 1.0F, "");
    }

    public static ReactorComponentItem plating(Properties properties, int maxHeatAdd, float effectModifier) {
        return new ReactorComponentItem(properties, Kind.PLATING, 0, 0, maxHeatAdd, 0, effectModifier, "");
    }

    public static ReactorComponentItem condensator(Properties properties, int heat) {
        return new ReactorComponentItem(properties, Kind.CONDENSATOR, heat, 0, 0, 0, 1.0F, "");
    }

    public Kind kind() {
        return this.kind;
    }

    public int maxDamage() {
        return this.maxDamage;
    }

    public static int damage(ItemStack stack) {
        return stack.getOrDefault(IC2Components.REACTOR_DAMAGE.get(), 0);
    }

    public static void setDamage(ItemStack stack, int damage) {
        if (damage <= 0) {
            stack.remove(IC2Components.REACTOR_DAMAGE.get());
        } else {
            stack.set(IC2Components.REACTOR_DAMAGE.get(), damage);
        }
    }

    private boolean storesHeat() {
        return this.kind == Kind.HEAT_STORAGE || this.kind == Kind.VENT || this.kind == Kind.EXCHANGER;
    }

    // ── comportamento no reator ───────────────────────────────────────────
    public void processChamber(ItemStack stack, Reactor reactor, int x, int y, boolean heatRun) {
        switch (this.kind) {
            case FUEL, MOX -> processFuel(stack, reactor, x, y, heatRun);
            case VENT -> {
                if (!heatRun) return;
                if (this.reactorValue > 0) {
                    int reactorHeat = reactor.getHeat();
                    int drain = Math.min(reactorHeat, this.reactorValue);
                    if (alterHeat(stack, reactor, x, y, drain) > 0) return;
                    reactor.setHeat(reactorHeat - drain);
                }
                int self = alterHeat(stack, reactor, x, y, -this.sideValue);
                if (self <= 0) reactor.addEmitHeat(self + this.sideValue);
            }
            case SPREAD_VENT -> {
                if (!heatRun) return;
                coolNeighbor(reactor, x - 1, y);
                coolNeighbor(reactor, x + 1, y);
                coolNeighbor(reactor, x, y - 1);
                coolNeighbor(reactor, x, y + 1);
            }
            case EXCHANGER -> {
                if (heatRun) processExchanger(stack, reactor, x, y);
            }
            case PLATING -> {
                if (!heatRun) return;
                reactor.setMaxHeat(reactor.getMaxHeat() + this.sideValue);
                reactor.setHeatEffectModifier(reactor.getHeatEffectModifier() * this.effectModifier);
            }
            default -> {
            }
        }
    }

    /**
     * Barra de combustível do IC2: cada célula dá 1 + células/2 pulsos em si mesma e um pulso em cada
     * vizinho que aceita (outras barras, refletores). No ciclo de calor, gera 4 × T(pulsos) HU, dividido
     * entre os vizinhos que guardam calor; o que sobra aquece o reator.
     */
    private void processFuel(ItemStack stack, Reactor reactor, int x, int y, boolean heatRun) {
        if (!reactor.produceEnergy()) return;
        int basePulses = 1 + this.cells / 2;
        for (int iteration = 0; iteration < this.cells; iteration++) {
            if (!heatRun) {
                for (int i = 0; i < basePulses; i++) {
                    acceptUraniumPulse(stack, reactor, stack, x, y, x, y, false);
                }
                pulse(reactor, x - 1, y, stack, x, y, false);
                pulse(reactor, x + 1, y, stack, x, y, false);
                pulse(reactor, x, y - 1, stack, x, y, false);
                pulse(reactor, x, y + 1, stack, x, y, false);
            } else {
                int pulses = basePulses + pulse(reactor, x - 1, y, stack, x, y, true) + pulse(reactor, x + 1, y, stack, x, y, true)
                        + pulse(reactor, x, y - 1, stack, x, y, true) + pulse(reactor, x, y + 1, stack, x, y, true);
                int heat = (pulses * pulses + pulses) / 2 * 4;
                if (this.kind == Kind.MOX && reactor.isFluidCooled() && (float) reactor.getHeat() / reactor.getMaxHeat() > 0.5F) heat *= 2;

                Queue<Target> acceptors = new ArrayDeque<>();
                addHeatAcceptor(reactor, x - 1, y, acceptors);
                addHeatAcceptor(reactor, x + 1, y, acceptors);
                addHeatAcceptor(reactor, x, y - 1, acceptors);
                addHeatAcceptor(reactor, x, y + 1, acceptors);
                while (!acceptors.isEmpty() && heat > 0) {
                    int share = heat / acceptors.size();
                    heat -= share;
                    Target target = acceptors.remove();
                    heat += target.item().alterHeat(target.stack(), reactor, target.x(), target.y(), share);
                }
                if (heat > 0) reactor.addHeat(heat);
            }
        }
        if (heatRun) return;
        if (damage(stack) >= this.maxDamage - 1) {
            reactor.setItemAt(x, y, depletedStack());
        } else {
            setDamage(stack, damage(stack) + 1);
        }
    }

    private ItemStack depletedStack() {
        if (this.depleted.isEmpty()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(IC2Reborn.MODID, this.depleted));
        return new ItemStack(item);
    }

    private static int pulse(Reactor reactor, int x, int y, ItemStack source, int sourceX, int sourceY, boolean heatRun) {
        ItemStack other = reactor.getItemAt(x, y);
        return other.getItem() instanceof ReactorComponentItem component
                && component.acceptUraniumPulse(other, reactor, source, x, y, sourceX, sourceY, heatRun) ? 1 : 0;
    }

    private record Target(ReactorComponentItem item, ItemStack stack, int x, int y) {}

    private static void addHeatAcceptor(Reactor reactor, int x, int y, java.util.Collection<Target> acceptors) {
        ItemStack stack = reactor.getItemAt(x, y);
        if (stack.getItem() instanceof ReactorComponentItem component && component.canStoreHeat(stack)) {
            acceptors.add(new Target(component, stack, x, y));
        }
    }

    public boolean acceptUraniumPulse(ItemStack stack, Reactor reactor, ItemStack pulsing, int youX, int youY, int pulseX, int pulseY, boolean heatRun) {
        switch (this.kind) {
            case FUEL -> {
                if (!heatRun) reactor.addOutput(1.0F);
                return true;
            }
            case MOX -> {
                if (!heatRun) reactor.addOutput(4.0F * reactor.getHeat() / reactor.getMaxHeat() + 1.0F);
                return true;
            }
            case REFLECTOR, IRIDIUM_REFLECTOR -> {
                if (!heatRun) {
                    if (pulsing.getItem() instanceof ReactorComponentItem source) {
                        source.acceptUraniumPulse(pulsing, reactor, stack, pulseX, pulseY, youX, youY, false);
                    }
                } else if (this.kind == Kind.REFLECTOR) {
                    if (damage(stack) + 1 >= this.maxDamage) {
                        reactor.setItemAt(youX, youY, ItemStack.EMPTY);
                    } else {
                        setDamage(stack, damage(stack) + 1);
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean canStoreHeat(ItemStack stack) {
        if (this.kind == Kind.CONDENSATOR) return damage(stack) < this.maxDamage;
        return storesHeat();
    }

    public int getMaxHeat(ItemStack stack) {
        return storesHeat() || this.kind == Kind.CONDENSATOR ? this.maxDamage : 0;
    }

    public int getCurrentHeat(ItemStack stack) {
        return storesHeat() || this.kind == Kind.CONDENSATOR ? damage(stack) : 0;
    }

    /** Muda o calor guardado; devolve o que não coube (ou o que faltou, se negativo). Passar do limite derrete a peça. */
    public int alterHeat(ItemStack stack, Reactor reactor, int x, int y, int heat) {
        if (this.kind == Kind.CONDENSATOR) {
            if (heat < 0) return heat;
            int current = damage(stack);
            int amount = Math.min(heat, this.maxDamage - current);
            setDamage(stack, current + amount);
            return heat - amount;
        }
        if (!storesHeat()) return heat;
        int myHeat = damage(stack) + heat;
        if (myHeat > this.maxDamage) {
            reactor.setItemAt(x, y, ItemStack.EMPTY);
            return this.maxDamage - myHeat + 1;
        }
        if (myHeat < 0) {
            setDamage(stack, 0);
            return myHeat;
        }
        setDamage(stack, myHeat);
        return 0;
    }

    private void coolNeighbor(Reactor reactor, int x, int y) {
        ItemStack stack = reactor.getItemAt(x, y);
        if (stack.getItem() instanceof ReactorComponentItem component && component.canStoreHeat(stack)) {
            int self = component.alterHeat(stack, reactor, x, y, -this.sideValue);
            if (self <= 0) reactor.addEmitHeat(self + this.sideValue);
        }
    }

    /** Trocador de calor do IC2: equilibra a porcentagem de calor com os vizinhos e com o reator. */
    private void processExchanger(ItemStack stack, Reactor reactor, int x, int y) {
        int myHeat = 0;
        if (this.sideValue > 0) {
            List<Target> acceptors = new ArrayList<>();
            addHeatAcceptor(reactor, x - 1, y, acceptors);
            addHeatAcceptor(reactor, x + 1, y, acceptors);
            addHeatAcceptor(reactor, x, y - 1, acceptors);
            addHeatAcceptor(reactor, x, y + 1, acceptors);
            for (Target target : acceptors) {
                double mine = getCurrentHeat(stack) * 100.0 / getMaxHeat(stack);
                double theirs = target.item().getCurrentHeat(target.stack()) * 100.0 / target.item().getMaxHeat(target.stack());
                int add = (int) (target.item().getMaxHeat(target.stack()) / 100.0 * (theirs + mine / 2.0));
                add = Math.min(add, this.sideValue);
                add = scaleSmall(add, theirs + mine / 2.0);
                if (Math.round(theirs * 10.0) / 10.0 > Math.round(mine * 10.0) / 10.0) {
                    add -= 2 * add;
                } else if (Math.round(theirs * 10.0) / 10.0 == Math.round(mine * 10.0) / 10.0) {
                    add = 0;
                }
                myHeat -= add;
                myHeat += target.item().alterHeat(target.stack(), reactor, target.x(), target.y(), add);
            }
        }
        if (this.reactorValue > 0) {
            double mine = getCurrentHeat(stack) * 100.0 / getMaxHeat(stack);
            double reactorPercent = reactor.getHeat() * 100.0 / reactor.getMaxHeat();
            int add = (int) Math.round(reactor.getMaxHeat() / 100.0 * (reactorPercent + mine / 2.0));
            add = Math.min(add, this.reactorValue);
            add = scaleSmall(add, reactorPercent + mine / 2.0);
            if (Math.round(reactorPercent * 10.0) / 10.0 > Math.round(mine * 10.0) / 10.0) {
                add -= 2 * add;
            } else if (Math.round(reactorPercent * 10.0) / 10.0 == Math.round(mine * 10.0) / 10.0) {
                add = 0;
            }
            myHeat -= add;
            reactor.setHeat(reactor.getHeat() + add);
        }
        alterHeat(stack, reactor, x, y, myHeat);
    }

    /** Com pouco calor o IC2 move quantidades pequenas fixas (usando a troca com os vizinhos como base). */
    private int scaleSmall(int add, double level) {
        if (level < 0.25) return 1;
        if (level < 0.5) return this.sideValue / 8;
        if (level < 0.75) return this.sideValue / 4;
        if (level < 1.0) return this.sideValue / 2;
        return add;
    }

    public float influenceExplosion(ItemStack stack, Reactor reactor) {
        return switch (this.kind) {
            case FUEL, MOX -> 2.0F * this.cells;
            case REFLECTOR, IRIDIUM_REFLECTOR -> -1.0F;
            case PLATING -> this.effectModifier >= 1.0F ? 0.0F : this.effectModifier;
            default -> 0.0F;
        };
    }

    // ── item ──────────────────────────────────────────────────────────────
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return this.maxDamage > 0 && damage(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int value = storesHeat() || this.kind == Kind.CONDENSATOR ? damage(stack) : this.maxDamage - damage(stack);
        return Math.round(13.0F * Math.max(0, Math.min(this.maxDamage, value)) / Math.max(1, this.maxDamage));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return storesHeat() || this.kind == Kind.CONDENSATOR ? 0xE04020 : super.getBarColor(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (this.maxDamage <= 0) return;
        if (storesHeat() || this.kind == Kind.CONDENSATOR) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.reactor.heat", "Heat: %s / %s",
                    damage(stack), this.maxDamage).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.reactor.durability", "Durability: %s / %s",
                    this.maxDamage - damage(stack), this.maxDamage).withStyle(ChatFormatting.GRAY));
        }
    }
}
