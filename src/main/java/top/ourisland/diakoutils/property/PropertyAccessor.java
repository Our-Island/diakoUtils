package top.ourisland.diakoutils.property;

import top.ourisland.diakoutils.IModule;

public interface PropertyAccessor<T> {

    T get(IModule module);

    void set(IModule module, T value);

}
