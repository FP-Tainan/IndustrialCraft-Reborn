package net.craftenergy.registry;

import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/** Referência para uma entrada criada por {@link DeferredRegister}. */
public final class RegistryObject<T> implements Supplier<T> {
    private final Identifier id;
    private Supplier<? extends T> factory;
    private T value;

    RegistryObject(Identifier id, Supplier<? extends T> factory) {
        this.id = id;
        this.factory = factory;
    }

    T create() {
        return this.factory.get();
    }

    @SuppressWarnings("unchecked")
    void bind(Object value) {
        this.value = (T) value;
        this.factory = null;
    }

    public Identifier getId() {
        return this.id;
    }

    @Override
    public T get() {
        if (this.value == null) {
            throw new IllegalStateException("Registry object " + this.id + " is not registered yet");
        }
        return this.value;
    }
}
