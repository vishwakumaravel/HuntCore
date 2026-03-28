package com.huntcore.world;

import com.huntcore.config.PluginConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

@SuppressWarnings("deprecation")
public final class StructureHintService {

    private static final StructureType[] HINT_STRUCTURES = {
        StructureType.VILLAGE,
        StructureType.RUINED_PORTAL,
        StructureType.PILLAGER_OUTPOST
    };

    private final PluginConfig pluginConfig;

    public StructureHintService(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public Optional<StructureHint> findNearestHint(Location origin) {
        World world = origin.getWorld();
        List<StructureHint> hints = new ArrayList<>();

        for (StructureType structureType : HINT_STRUCTURES) {
            Location found = world.locateNearestStructure(origin, structureType, pluginConfig.getStructureSearchRadiusChunks(), false);
            if (found == null) {
                continue;
            }

            hints.add(new StructureHint(structureType, found, toDirection(origin, found)));
        }

        return hints.stream()
            .min(Comparator.comparingDouble(hint -> hint.location().distanceSquared(origin)));
    }

    public ItemStack createHintBook(StructureHint structureHint) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        bookMeta.setTitle("Scout Report");
        bookMeta.setAuthor("HuntCore");
        bookMeta.addPage(
            "A nearby lead has been found.\n\n"
                + "Head roughly "
                + structureHint.roughDirection()
                + ".\n\n"
                + "Expected landmark:\n"
                + structureHint.displayName()
                + "."
        );
        book.setItemMeta(bookMeta);
        return book;
    }

    private String toDirection(Location origin, Location target) {
        double deltaX = target.getX() - origin.getX();
        double deltaZ = target.getZ() - origin.getZ();
        double angle = Math.toDegrees(Math.atan2(deltaX, -deltaZ));
        if (angle < 0.0) {
            angle += 360.0;
        }

        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(angle / 45.0) % directions.length;
        return directions[index];
    }
}
