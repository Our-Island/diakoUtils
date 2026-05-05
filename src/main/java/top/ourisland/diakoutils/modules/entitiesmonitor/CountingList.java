package top.ourisland.diakoutils.modules.entitiesmonitor;

import java.util.AbstractList;

public final class CountingList<T> extends AbstractList<T> {

    private int count = 0;

    @Override
    public boolean add(T value) {
        count++;
        return true;
    }

    @Override
    public T get(int index) {
        throw new UnsupportedOperationException("CountingList does not store elements");
    }

    @Override
    public int size() {
        return count;
    }

    public int getCount() {
        return count;
    }

}
