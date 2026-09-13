package net.ic2reborn.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registro adiado simples, no estilo do DeferredRegister do Forge.
 *
 * As entradas são declaradas como campos estáticos e só são criadas/registradas
 * quando {@link #register()} é chamado durante o onInitialize do Fabric.
 */
public final class DeferredRegister<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String modId;
    private final List<RegistryObject<T>> entries = new ArrayList<>();
    private boolean registered;

    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        this.registryKey = registryKey;
        this.modId = modId;
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        return new DeferredRegister<>(registryKey, modId);
    }

    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> factory) {
        if (this.registered) {
            throw new IllegalStateException("Cannot add " + name + " after " + this.registryKey + " was registered");
        }
        RegistryObject<I> entry = new RegistryObject<>(Identifier.fromNamespaceAndPath(this.modId, name), factory);
        @SuppressWarnings("unchecked")
        RegistryObject<T> widened = (RegistryObject<T>) entry;
        this.entries.add(widened);
        return entry;
    }

    public ResourceKey<T> key(String name) {
        return ResourceKey.create(this.registryKey, Identifier.fromNamespaceAndPath(this.modId, name));
    }

    public List<RegistryObject<T>> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    @SuppressWarnings("unchecked")
    public void register() {
        if (this.registered) return;
        this.registered = true;

        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) this.registryKey);
        for (RegistryObject<T> entry : this.entries) {
            entry.bind(Registry.register(registry, entry.getId(), entry.create()));
        }
    }
}
