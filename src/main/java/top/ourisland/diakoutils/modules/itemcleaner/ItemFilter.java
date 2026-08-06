package top.ourisland.diakoutils.modules.itemcleaner;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

public final class ItemFilter {

    private final Set<Item> exactItems;
    private final List<TagKey<Item>> itemTags;

    ItemFilter(
            Set<Item> exactItems,
            List<TagKey<Item>> itemTags
    ) {
        this.exactItems = Set.copyOf(exactItems);
        this.itemTags = List.copyOf(itemTags);
    }

    public boolean matches(
            ItemCleanerMode mode,
            ItemStack stack
    ) {
        if (stack.isEmpty()) {
            return false;
        }

        var listed = contains(stack);
        return switch (mode) {
            case WHITELIST -> listed;
            case BLACKLIST -> !listed;
            case ALL -> true;
        };
    }

    public boolean contains(ItemStack stack) {
        if (exactItems.contains(stack.getItem())) {
            return true;
        }

        return itemTags.stream().anyMatch(stack::is);
    }

}
