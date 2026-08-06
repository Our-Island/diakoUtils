package top.ourisland.diakoutils.modules.itemcleaner;

import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

public record ItemScanResult(
        long itemCount,
        int stackCount,
        List<ItemEntity> entities
) {

    public ItemScanResult {
        entities = List.copyOf(entities);
    }

}
