package bm.minecraft.the.end.plus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;

import static bm.minecraft.the.end.plus.BmMinecraftTheEndPlusGatewayLinks.*;

public final class GatewayLinksRegression {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("end-gateway-regression-");
        try {
            var positions = new HashSet<Point>();
            for (int i = 0; i < 20; i++) {
                check(positions.add(central(i)) && slot(central(i)) == i, "Ring slots must be distinct");
            }
            check(slot(new Point(0, 75, 0)) == -1, "Ignore non-ring gateways");
            Path file = root.resolve("links.properties");
            UUID world = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            Link original = new Link(new Point(1200, 83, -850), false, true);
            var links = new BmMinecraftTheEndPlusGatewayLinks(file);
            links.load();
            check(links.remember(world, 3, original), "Record first vanilla pairing");
            check(!links.remember(world, 3, new Link(new Point(1220, 83, -850), false, false)),
                    "A reset must not replace an existing outer destination");
            check(!links.remember(world, 4, original), "Two central slots must not share an outer gate");
            check(!links.remember(world, 4, new Link(new Point(20, 75, 20), false, false)),
                    "Do not record a destination inside deleted regions");
            check(links.remember(other, 3, original), "Worlds must be isolated");
            links.save();
            for (int reset = 0; reset < 3; reset++) {
                links = new BmMinecraftTheEndPlusGatewayLinks(file);
                links.load();
                check(original.equals(links.get(world, 3)), "Link survives restart and repeated resets");
                check(links.get(world, 4) == null, "Unused vanilla slots stay unused");
                links.save();
            }
            Link next = new Link(new Point(-1100, 91, 900), false, false);
            check(links.remember(world, 4, next), "Crystal respawn can add a new distinct pair");
            links.save();
            Files.writeString(file, "broken=data\n");
            var finalLinks = links;
            expectFailure(finalLinks::load);
            check(original.equals(links.get(world, 3)), "Invalid file must not erase in-memory links");
            check(Files.readString(file).equals("broken=data\n"), "Invalid file must not be overwritten by load");
            Path failureFile = root.resolve("failure.properties");
            var failure = new BmMinecraftTheEndPlusGatewayLinks(failureFile);
            failure.remember(world, 3, original);
            Files.createDirectory(root.resolve("failure.properties.tmp"));
            expectFailure(failure::save);
            check(!Files.exists(failureFile), "Failed save must not publish partial records");
            System.out.println("PASS: gateway ring, stable pairs, duplicate prevention, world isolation, restart, repeated resets, crystal pair, corrupt file, save failure");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
        }
    }

    static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private static void expectFailure(IoAction action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected IOException");
    }
    private interface IoAction { void run() throws Exception; }
}
