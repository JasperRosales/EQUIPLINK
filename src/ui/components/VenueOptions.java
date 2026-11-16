package src.ui.components;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Room choices for the venue picker. They are read from room.md at the project
 * root so non-technical users can add rooms without touching code. A built-in
 * copy keeps the picker usable when the app is launched outside the repository.
 */
public final class VenueOptions {

    private static final String[] FALLBACK = {
            "Room 201", "Room 202", "Room 203", "Room 204", "Room 205",
            "Room 206", "Room 207", "Room 208", "Room 209", "Room 210",
            "Physics Lab", "Chemistry Lab", "Computer Laboratory",
            "Internet Laboratory", "AVR", "4th Floor", "EEB 02", "EEB 04",
            "RGR 04", "RGR 05", "RGR 06", "Nurse Office", "OGC Office",
            "OJT Office", "Testing Admission Office", "Cashiering Office",
            "OSD Office", "Registrar Office"
    };

    private VenueOptions() {
    }

    /** Returns the room names in file order, without duplicates. */
    public static List<String> load() {
        List<String> fromFile = loadFromDisk();
        return fromFile.isEmpty() ? List.of(FALLBACK) : fromFile;
    }

    private static List<String> loadFromDisk() {
        for (Path candidate : new Path[] {
                Path.of("room.md"),
                Path.of("..", "room.md") }) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                List<String> rooms = parse(Files.readAllLines(candidate, StandardCharsets.UTF_8));
                if (!rooms.isEmpty()) {
                    return rooms;
                }
            } catch (IOException ignored) {
                // Fall through to the next location or the built-in list.
            }
        }

        try (InputStream resource = VenueOptions.class.getResourceAsStream("/room.md")) {
            if (resource != null) {
                List<String> rooms = parse(new String(
                        resource.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
                if (!rooms.isEmpty()) {
                    return rooms;
                }
            }
        } catch (IOException ignored) {
            // Fall back to the built-in list.
        }
        return List.of();
    }

    private static List<String> parse(List<String> lines) {
        Set<String> rooms = new LinkedHashSet<>();
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (!value.startsWith("- ") && !value.startsWith("* ")) {
                continue;
            }
            value = value.substring(2).trim();
            if (!value.isEmpty()) {
                rooms.add(value);
            }
        }
        return new ArrayList<>(rooms);
    }
}
