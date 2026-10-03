package bm.minecraft.the.end.plus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Filesystem regression tests with real disposable files; run with test.ps1. */
public final class ResetFilesRegression {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("end-reset-regression-");
        try {
            boundaries();
            resetAndRollback(root.resolve("normal"));
            missingFightIsRejected(root.resolve("missing-fight"));
            backupFailureDoesNotDelete(root.resolve("backup-failure"));
            partialDeleteRollsBack(root.resolve("partial-delete"));
            unsafeTargetIsRejected(root.resolve("unsafe"));
            System.out.println("PASS: boundaries, outer-island preservation, rollback, missing fight, backup failure, partial deletion, unsafe target");
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (Files.getFileStore(file).supportsFileAttributeView("dos")) {
                        Files.setAttribute(file, "dos:readonly", false);
                    }
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static void boundaries() {
        for (String name : List.of("r.-1.-1.mca", "r.-1.0.mca", "r.0.-1.mca", "r.0.0.mca",
                "c.-32.-32.mcc", "c.31.31.mcc", "c.0.-1.mcc")) {
            check(BmMinecraftTheEndPlusResetFiles.isCentralFile(name), "Must reset " + name);
        }
        for (String name : List.of("r.1.0.mca", "r.-2.-1.mca", "c.-33.0.mcc", "c.32.0.mcc",
                "c.0.-33.mcc", "c.0.32.mcc", "c.999999999999.0.mcc", "r.0.0.mca.bak", "level.dat")) {
            check(!BmMinecraftTheEndPlusResetFiles.isCentralFile(name), "Must preserve " + name);
        }
    }

    private static Path fixture(Path root) throws IOException {
        Path dimension = root.resolve("dimensions/minecraft/the_end");
        for (String type : List.of("region", "entities", "poi")) {
            for (String file : List.of("r.-1.-1.mca", "r.-1.0.mca", "r.0.-1.mca", "r.0.0.mca",
                    "r.1.0.mca", "r.-2.0.mca", "c.-32.-32.mcc", "c.31.31.mcc", "c.32.0.mcc")) {
                write(dimension.resolve(type).resolve(file), type + ":" + file);
            }
        }
        write(dimension.resolve(BmMinecraftTheEndPlusResetFiles.DRAGON_FIGHT), "old dragon fight");
        write(dimension.resolve("data/minecraft/ender_dragon_fight.dat_old"), "old fight backup");
        write(dimension.resolve("data/minecraft/game_rules.dat"), "keep game rules");
        write(dimension.resolve("data/minecraft/world_gen_settings.dat"), "keep seed");
        write(dimension.resolve("level.dat"), "keep metadata");
        return dimension;
    }

    private static void resetAndRollback(Path root) throws Exception {
        Path dimension = fixture(root);
        Map<Path, byte[]> before = contents(dimension);
        List<Path> selected = BmMinecraftTheEndPlusResetFiles.selectFiles(dimension);
        check(selected.size() == 20, "Exactly 12 MCA + 6 MCC + 2 fight files");
        Path backup = root.resolve("backup");
        var reset = new BmMinecraftTheEndPlusResetFiles(dimension, backup);
        reset.backupAndDelete();
        for (var entry : before.entrySet()) {
            if (selected.contains(entry.getKey())) {
                check(!Files.exists(dimension.resolve(entry.getKey())), "Reset target still exists");
                check(Arrays.equals(entry.getValue(), Files.readAllBytes(backup.resolve(entry.getKey()))),
                        "Backup is not identical");
            } else {
                check(Arrays.equals(entry.getValue(), Files.readAllBytes(dimension.resolve(entry.getKey()))),
                        "Outer island or metadata changed");
            }
        }
        // Emulate a partially generated replacement world, then a failed load.
        write(dimension.resolve("region/r.0.0.mca"), "new partial world");
        write(dimension.resolve("entities/c.3.3.mcc"), "new partial entity chunk");
        write(dimension.resolve(BmMinecraftTheEndPlusResetFiles.DRAGON_FIGHT), "new fight");
        reset.rollback();
        identical(before, contents(dimension));
    }

    private static void missingFightIsRejected(Path root) throws Exception {
        Path dimension = fixture(root);
        Files.delete(dimension.resolve(BmMinecraftTheEndPlusResetFiles.DRAGON_FIGHT));
        Map<Path, byte[]> before = contents(dimension);
        expectFailure(() -> new BmMinecraftTheEndPlusResetFiles(dimension, root.resolve("backup")));
        identical(before, contents(dimension));
    }

    private static void backupFailureDoesNotDelete(Path root) throws Exception {
        Path dimension = fixture(root);
        Map<Path, byte[]> before = contents(dimension);
        Path backup = root.resolve("backup");
        write(backup.resolve("entities/r.0.0.mca"), "conflicting backup");
        var reset = new BmMinecraftTheEndPlusResetFiles(dimension, backup);
        expectFailure(reset::backupAndDelete);
        reset.rollback();
        identical(before, contents(dimension));
    }

    private static void partialDeleteRollsBack(Path root) throws Exception {
        Path dimension = fixture(root);
        if (!Files.getFileStore(dimension).supportsFileAttributeView("dos")) {
            System.out.println("SKIP: Windows read-only deletion failure (DOS attributes unavailable)");
            return;
        }
        Map<Path, byte[]> before = contents(dimension);
        Path locked = dimension.resolve(BmMinecraftTheEndPlusResetFiles.DRAGON_FIGHT);
        var reset = new BmMinecraftTheEndPlusResetFiles(dimension, root.resolve("backup"));
        Files.setAttribute(locked, "dos:readonly", true);
        try {
            expectFailure(reset::backupAndDelete);
            check(!Files.exists(dimension.resolve("region/r.0.0.mca")), "Test must reach partial deletion");
        } finally {
            if (Files.exists(locked)) {
                Files.setAttribute(locked, "dos:readonly", false);
            }
        }
        reset.rollback();
        identical(before, contents(dimension));
    }

    private static void unsafeTargetIsRejected(Path root) throws Exception {
        Path dimension = fixture(root);
        expectFailure(() -> new BmMinecraftTheEndPlusResetFiles(dimension, dimension.resolve("backup")));
        Files.delete(dimension.resolve("region/r.0.0.mca"));
        Files.createDirectory(dimension.resolve("region/r.0.0.mca"));
        expectFailure(() -> new BmMinecraftTheEndPlusResetFiles(dimension, root.resolve("backup")));
        check(Files.exists(dimension.resolve("region/r.-1.-1.mca")), "Other targets must remain intact");
    }

    private static void write(Path path, String value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }

    private static Map<Path, byte[]> contents(Path root) throws IOException {
        Map<Path, byte[]> contents = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                contents.put(root.relativize(path), Files.readAllBytes(path));
            }
        }
        return contents;
    }

    private static void identical(Map<Path, byte[]> before, Map<Path, byte[]> after) {
        check(before.keySet().equals(after.keySet()), "File set changed");
        before.forEach((path, bytes) -> check(Arrays.equals(bytes, after.get(path)), "File changed: " + path));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(IoAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected IOException");
    }

    private interface IoAction {
        void run() throws Exception;
    }
}
