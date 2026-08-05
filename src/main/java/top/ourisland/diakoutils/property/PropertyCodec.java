package top.ourisland.diakoutils.property;

import com.electronwill.nightconfig.core.Config;

import java.util.Collection;

public interface PropertyCodec<T> {

    T parse(String input) throws PropertyParseException;

    T read(
            Config config,
            String path,
            T fallback
    ) throws PropertyParseException;

    void write(
            Config config,
            String path,
            T value
    );

    String format(T value);

    String typeName();

    default Collection<String> suggestions() {
        return java.util.List.of();
    }

}
