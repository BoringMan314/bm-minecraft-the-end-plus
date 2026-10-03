package bm.minecraft.the.end.plus;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Durable gateway destinations, independent of resettable dragon fight and chunk data. */
final class BmMinecraftTheEndPlusGatewayLinks {
    record Point(int x, int y, int z) {
        boolean outsideReset() { return x < -512 || x >= 512 || z < -512 || z >= 512; }
    }
    record Link(Point outer, boolean centralExact, boolean outerExact) { }

    private final Path file;
    private final Map<UUID, Map<Integer, Link>> worlds = new HashMap<>();
    private boolean dirty;

    BmMinecraftTheEndPlusGatewayLinks(Path file) { this.file = file; }

    static Point central(int slot) {
        if (slot < 0 || slot >= 20) { throw new IllegalArgumentException("Invalid gateway slot"); }
        double angle = 2.0 * (-Math.PI + (Math.PI / 20) * slot);
        return new Point((int) Math.floor(96.0 * Math.cos(angle)), 75,
                (int) Math.floor(96.0 * Math.sin(angle)));
    }

    static int slot(Point point) {
        for (int i = 0; i < 20; i++) {
            if (central(i).equals(point)) { return i; }
        }
        return -1;
    }

    Map<Integer, Link> links(UUID world) { return Map.copyOf(worlds.getOrDefault(world, Map.of())); }

    Link get(UUID world, int slot) { return worlds.getOrDefault(world, Map.of()).get(slot); }

    boolean remember(UUID world, int slot, Link link) {
        central(slot);
        if (!link.outer().outsideReset()) { return false; }
        Map<Integer, Link> links = worlds.computeIfAbsent(world, ignored -> new HashMap<>());
        // Keep a known pair stable across resets; never redirect it to a newly generated outer gate.
        if (links.containsKey(slot)) { return false; }
        if (links.values().stream().anyMatch(existing -> existing.outer().equals(link.outer()))) { return false; }
        links.put(slot, link);
        dirty = true;
        return true;
    }

    void load() throws IOException {
        if (!Files.exists(file)) { return; }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        Map<UUID, Map<Integer, Link>> loaded = new HashMap<>();
        try {
            for (String key : properties.stringPropertyNames()) {
                String[] identity = key.split("/", -1);
                if (identity.length != 2) { throw new IllegalArgumentException("Invalid world/slot"); }
                UUID world = UUID.fromString(identity[0]);
                int slot = Integer.parseInt(identity[1]);
                central(slot);
                String[] value = properties.getProperty(key).split(",", -1);
                if (value.length != 5) { throw new IllegalArgumentException("Invalid destination"); }
                Point outer = new Point(Integer.parseInt(value[0]), Integer.parseInt(value[1]), Integer.parseInt(value[2]));
                if (!outer.outsideReset() || Math.abs((long) outer.x()) > 30_000_000
                        || Math.abs((long) outer.z()) > 30_000_000
                        || !(value[3].equals("true") || value[3].equals("false"))
                        || !(value[4].equals("true") || value[4].equals("false"))) {
                    throw new IllegalArgumentException("Invalid gateway coordinates or flags");
                }
                Map<Integer, Link> links = loaded.computeIfAbsent(world, ignored -> new HashMap<>());
                if (links.values().stream().anyMatch(link -> link.outer().equals(outer))) {
                    throw new IllegalArgumentException("Duplicate outer gateway");
                }
                links.put(slot, new Link(outer, Boolean.parseBoolean(value[3]), Boolean.parseBoolean(value[4])));
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid gateway link file; refusing to discard saved links", exception);
        }
        worlds.clear();
        worlds.putAll(loaded);
        dirty = false;
    }

    void save() throws IOException {
        if (!dirty) { return; }
        Properties properties = new Properties();
        worlds.forEach((world, links) -> links.forEach((slot, link) -> properties.setProperty(world + "/" + slot,
                link.outer().x() + "," + link.outer().y() + "," + link.outer().z()
                        + "," + link.centralExact() + "," + link.outerExact())));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "Original End gateway pairs; retained across End resets");
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        dirty = false;
    }
}
