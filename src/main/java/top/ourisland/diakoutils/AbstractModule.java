package top.ourisland.diakoutils;

public abstract class AbstractModule implements IModule {

    private boolean enabled;

    @Override
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public final boolean enabled() {
        return enabled;
    }

}
