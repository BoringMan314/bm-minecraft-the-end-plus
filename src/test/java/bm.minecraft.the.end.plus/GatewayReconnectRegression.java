package bm.minecraft.the.end.plus;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.EndGateway;
import org.bukkit.boss.DragonBattle;
import io.papermc.paper.math.Position;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static bm.minecraft.the.end.plus.BmMinecraftTheEndPlusGatewayLinks.*;
import static bm.minecraft.the.end.plus.GatewayLinksRegression.check;

/** In-memory Bukkit boundary tests; no production world files or running server. */
public final class GatewayReconnectRegression {
    public static void main(String[] args) {
        var fake = new FakeWorld();
        Point center = central(3);
        Point outer = new Point(1200, 83, -850);
        Link link = new Link(outer, false, true);
        // First kill / first vanilla traversal already created both gates.
        fake.types.put(center, Material.END_GATEWAY);
        fake.types.put(outer, Material.END_GATEWAY);
        BmMinecraftTheEndPlusGateways.reconnect(fake.world, 3, link);
        check(fake.spawns == 0, "Existing pair must not generate another gateway");
        check(outer.equals(fake.exits.get(center)) && center.equals(fake.exits.get(outer)), "Pair must be reciprocal");
        check(!fake.exact.get(center) && fake.exact.get(outer), "Retain exact teleport flags");
        check(fake.types.values().stream().filter(type -> type == Material.BEDROCK).count() == 24, "Repair both frames");
        // A command reset removes the central terrain, but retains the outer gate.
        fake.types.keySet().removeIf(point -> !point.outsideReset());
        fake.exits.remove(center);
        BmMinecraftTheEndPlusGateways.reconnect(fake.world, 3, link);
        check(fake.spawns == 1 && fake.lastSpawn.equals(center), "Repair central at the same point only");
        // A broken outer gate is repaired at its saved location, not a new island.
        fake.types.remove(outer);
        fake.exits.remove(outer);
        BmMinecraftTheEndPlusGateways.reconnect(fake.world, 3, link);
        check(fake.spawns == 2 && fake.lastSpawn.equals(outer), "Repair outer at the original point");
        for (int i = 0; i < 5; i++) { BmMinecraftTheEndPlusGateways.reconnect(fake.world, 3, link); }
        check(fake.spawns == 2, "Repeated traversals must not duplicate gateway generation");
        // Vanilla recreates a gate with no exit after another reset/kill.
        fake.exits.remove(center);
        BmMinecraftTheEndPlusGateways.reconnect(fake.world, 3, link);
        check(fake.spawns == 2 && outer.equals(fake.exits.get(center)), "Reconnect regenerated gate before vanilla exit search");
        check(fake.automaticSpawns == 0, "Never consume vanilla gateway progression during repair");
        System.out.println("PASS: reciprocal reconnect, exact flags, frame repair, command reset, destroyed outer gate, repeated traversal, regenerated gate, vanilla progression");
    }

    private static final class FakeWorld {
        final Map<Point, Material> types = new HashMap<>();
        final Map<Point, Point> exits = new HashMap<>();
        final Map<Point, Boolean> exact = new HashMap<>();
        int spawns;
        int automaticSpawns;
        Point lastSpawn;
        final DragonBattle battle = proxy(DragonBattle.class, (object, method, args) -> {
            if (method.getName().equals("spawnNewGateway")) {
                if (args == null || args.length == 0) { automaticSpawns++; return true; }
                Position pos = (Position) args[0];
                lastSpawn = new Point(pos.blockX(), pos.blockY(), pos.blockZ());
                types.put(lastSpawn, Material.END_GATEWAY);
                spawns++;
                return null;
            }
            throw new AssertionError(method);
        });
        final World world = proxy(World.class, (object, method, args) -> switch (method.getName()) {
            case "getMinHeight" -> 0;
            case "getMaxHeight" -> 256;
            case "getEnderDragonBattle" -> battle;
            case "getBlockAt" -> block(new Point((int) args[0], (int) args[1], (int) args[2]));
            case "equals" -> object == args[0];
            case "hashCode" -> System.identityHashCode(object);
            default -> throw new AssertionError(method);
        });

        Block block(Point point) {
            return proxy(Block.class, (object, method, args) -> switch (method.getName()) {
                case "getType" -> types.getOrDefault(point, Material.AIR);
                case "setType" -> { types.put(point, (Material) args[0]); yield null; }
                case "getState" -> types.get(point) == Material.END_GATEWAY ? gateway(point)
                        : proxy(BlockState.class, (unused, called, values) -> { throw new AssertionError(called); });
                default -> throw new AssertionError(method);
            });
        }

        EndGateway gateway(Point point) {
            return proxy(EndGateway.class, (object, method, args) -> switch (method.getName()) {
                case "getExitLocation" -> {
                    Point exit = exits.get(point);
                    yield exit == null ? null : new Location(world, exit.x(), exit.y(), exit.z());
                }
                case "setExitLocation" -> {
                    Location exit = (Location) args[0];
                    exits.put(point, new Point(exit.getBlockX(), exit.getBlockY(), exit.getBlockZ()));
                    yield null;
                }
                case "isExactTeleport" -> exact.getOrDefault(point, false);
                case "setExactTeleport" -> { exact.put(point, (boolean) args[0]); yield null; }
                case "update" -> true;
                default -> throw new AssertionError(method);
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
