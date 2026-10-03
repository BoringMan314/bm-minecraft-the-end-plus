package bm.minecraft.the.end.plus;

import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent;
import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import io.papermc.paper.math.Position;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.block.EndGateway;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;

import java.io.IOException;
import java.util.logging.Level;

import static bm.minecraft.the.end.plus.BmMinecraftTheEndPlusGatewayLinks.*;

/** Reconnects regenerated vanilla gateways before Minecraft searches for a new outer destination. */
final class BmMinecraftTheEndPlusGateways implements Listener {
    private final BmMinecraftTheEndPlusPlugin plugin;
    private final BmMinecraftTheEndPlusGatewayLinks links;

    BmMinecraftTheEndPlusGateways(BmMinecraftTheEndPlusPlugin plugin) throws IOException {
        this.plugin = plugin;
        this.links = new BmMinecraftTheEndPlusGatewayLinks(plugin.getDataFolder().toPath().resolve("gateway-links.properties"));
        links.load();
    }

    void save() throws IOException { links.save(); }

    void captureBeforeReset(World world) throws IOException {
        // Include unloaded central gates, without generating unused ring chunks.
        for (int slot = 0; slot < 20; slot++) {
            Point point = central(slot);
            if (world.isChunkGenerated(point.x() >> 4, point.z() >> 4)) {
                EndGateway gateway = gateway(world, point);
                if (gateway != null) { remember(gateway); }
            }
        }
        // An intact outer gate can recover a broken central gate's original pairing.
        for (var chunk : world.getLoadedChunks()) {
            for (var state : chunk.getTileEntities()) {
                if (state instanceof EndGateway gateway) { remember(gateway); }
            }
        }
        // A failed write must abort preparation before any world data is removed.
        links.save();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEnter(EntityPortalEnterEvent event) {
        World world = event.getLocation().getWorld();
        if (event.getPortalType() != PortalType.END_GATEWAY || world == null
                || world.getEnvironment() != World.Environment.THE_END) { return; }
        if (plugin.isResetRunning(world)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getLocation().getBlock().getState() instanceof EndGateway source)) { return; }
        try {
            remember(source);
            Point sourcePoint = point(source.getLocation());
            int slot = slot(sourcePoint);
            if (slot < 0) {
                for (var entry : links.links(world.getUID()).entrySet()) {
                    if (entry.getValue().outer().equals(sourcePoint)) { slot = entry.getKey(); break; }
                }
            }
            Link link = links.get(world.getUID(), slot);
            if (link != null) {
                links.save();
                reconnect(world, slot, link);
            }
        } catch (IOException | RuntimeException exception) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.SEVERE, "Could not reconnect End gateway in " + world.getName(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEndGatewayEvent event) { recordAfterVanilla(event.getGateway()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityTeleport(EntityTeleportEndGatewayEvent event) { recordAfterVanilla(event.getGateway()); }

    private void recordAfterVanilla(EndGateway source) {
        if (plugin.isResetRunning(source.getWorld())) { return; }
        try {
            // The first vanilla traversal has now chosen the outer destination, even if teleport is cancelled.
            remember(source);
            links.save();
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save End gateway link in " + source.getWorld().getName(), exception);
        }
    }

    private void remember(EndGateway source) {
        World world = source.getWorld();
        if (world.getEnvironment() != World.Environment.THE_END) { return; }
        Location exit = source.getExitLocation();
        if (exit == null) { return; }
        Point position = point(source.getLocation());
        Point destination = point(exit);
        int slot = slot(position);
        if (slot >= 0 && destination.outsideReset()) {
            if (links.get(world.getUID(), slot) != null) { return; }
            EndGateway outer = gateway(world, destination);
            // Do not adopt custom one-way teleporters as vanilla pairs.
            if (outer != null && !position.equals(pointOrNull(outer.getExitLocation()))) { return; }
            links.remember(world.getUID(), slot,
                    new Link(destination, source.isExactTeleport(), outer != null && outer.isExactTeleport()));
        } else if (position.outsideReset() && (slot = slot(destination)) >= 0) {
            EndGateway central = gateway(world, destination);
            if (central != null && central.getExitLocation() != null
                    && !position.equals(point(central.getExitLocation()))) { return; }
            links.remember(world.getUID(), slot,
                    new Link(position, central != null && central.isExactTeleport(), source.isExactTeleport()));
        }
    }

    static void reconnect(World world, int slot, Link link) {
        Point center = central(slot);
        // Repair only the saved pair at its original coordinates; never search for a new outer island.
        EndGateway main = repair(world, center);
        EndGateway outer = repair(world, link.outer());
        connect(main, world, link.outer(), link.centralExact());
        connect(outer, world, center, link.outerExact());
    }

    private static EndGateway repair(World world, Point position) {
        if (position.y() - 2 < world.getMinHeight() || position.y() + 2 >= world.getMaxHeight()) {
            throw new IllegalStateException("Saved gateway is outside the world height");
        }
        EndGateway gateway = gateway(world, position);
        if (gateway == null) {
            if (world.getEnderDragonBattle() == null) { throw new IllegalStateException("Missing dragon battle"); }
            // The explicit-position overload does not consume the next vanilla gateway slot.
            world.getEnderDragonBattle().spawnNewGateway(Position.block(position.x(), position.y(), position.z()));
            gateway = gateway(world, position);
            if (gateway == null) { throw new IllegalStateException("Could not rebuild gateway at " + position); }
        }
        // Repair the twelve bedrock frame blocks without clearing nearby player constructions.
        for (int dy : new int[] {-2, -1, 1, 2}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((Math.abs(dy) == 2 && (dx != 0 || dz != 0)) || (dx != 0 && dz != 0)) { continue; }
                    var block = world.getBlockAt(position.x() + dx, position.y() + dy, position.z() + dz);
                    if (block.getType() != Material.BEDROCK) { block.setType(Material.BEDROCK, false); }
                }
            }
        }
        return gateway;
    }

    private static void connect(EndGateway gateway, World world, Point destination, boolean exact) {
        if (destination.equals(pointOrNull(gateway.getExitLocation())) && gateway.isExactTeleport() == exact) { return; }
        gateway.setExitLocation(new Location(world, destination.x(), destination.y(), destination.z()));
        gateway.setExactTeleport(exact);
        if (!gateway.update(false, false)) { throw new IllegalStateException("Gateway changed during reconnect"); }
    }

    private static EndGateway gateway(World world, Point point) {
        return world.getBlockAt(point.x(), point.y(), point.z()).getState() instanceof EndGateway gateway ? gateway : null;
    }

    private static Point point(Location location) { return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private static Point pointOrNull(Location location) { return location == null ? null : point(location); }
}
