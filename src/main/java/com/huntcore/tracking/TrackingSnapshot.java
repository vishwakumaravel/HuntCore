package com.huntcore.tracking;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;

public record TrackingSnapshot(TrackingMode mode, Location targetLocation, Component statusText) {
}
