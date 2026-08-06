package top.ourisland.diakoutils.modules.itemcleaner;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ItemFilterCompiler {

    private ItemFilterCompiler() {
    }

    public static ItemFilter compile(
            ItemCleanerMode mode,
            List<String> selectors
    ) {
        if (mode == ItemCleanerMode.ALL) {
            return new ItemFilter(Set.of(), List.of());
        }

        var exactItems = new LinkedHashSet<Item>();
        var tags = new LinkedHashSet<TagKey<Item>>();

        selectors.stream()
                .map(String::trim)
                .distinct()
                .forEachOrdered(selector -> {
                    if (selector.startsWith("##")) {
                        throw new IllegalArgumentException(
                                "Invalid item selector: " + selector
                        );
                    }

                    if (selector.startsWith("#")) {
                        var id = parseId(selector.substring(1), selector);
                        var tag = TagKey.create(Registries.ITEM, id);

                        if (BuiltInRegistries.ITEM.get(tag).isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Unknown item tag: " + selector
                            );
                        }

                        tags.add(tag);
                    } else {
                        var id = parseId(selector, selector);
                        var item = BuiltInRegistries.ITEM.getOptional(id)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Unknown item: " + selector
                                ));

                        exactItems.add(item);
                    }
                });

        return new ItemFilter(
                exactItems,
                List.copyOf(tags)
        );
    }

    private static Identifier parseId(
            String value,
            String selector
    ) {
        var id = Identifier.tryParse(value);
        if (id == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid item selector: " + selector);
        }

        return id;
    }

}
