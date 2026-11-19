package src.ui.components;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    /** Adds a room for the ICT System Admin and persists it to room.md immediately. */
    public static synchronized boolean addRoom(String roomName, String actorRole)
            throws IOException {

        if (!"ICT_STAFF".equalsIgnoreCase(actorRole == null ? "" : actorRole.trim())) {
            throw new SecurityException("Only the ICT System Admin can add rooms.");
        }
        String room = roomName == null ? "" : roomName.trim();
        if (room.isEmpty()) {
            throw new IllegalArgumentException("Enter a room name.");
        }
        List<String> rooms = new ArrayList<>(load());
        if (rooms.stream().anyMatch(r -> r.equalsIgnoreCase(room))) {
            return false;
        }

        Path target = roomFile();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        List<String> lines = new ArrayList<>();
        if (Files.isRegularFile(target)) {
            try {
                lines.addAll(Files.readAllLines(target, StandardCharsets.UTF_8));
            } catch (IOException e) {
                lines.clear();
            }
        }
        if (lines.isEmpty()) {
            lines.add("#Rooms");
            for (String r : rooms) lines.add("- " + r);
        } else if (!lines.stream().anyMatch(l -> l != null && l.trim().equalsIgnoreCase("#Rooms"))) {
            // keep as-is
        }
        lines.add("- " + room);

        Path dir = target.getParent();
        Path temporary = dir == null
                ? Files.createTempFile("room-options-", ".md")
                : Files.createTempFile(dir, "room-options-", ".md");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }

    public static synchronized boolean updateRoom(String oldName, String newName, String actorRole)
            throws IOException {
        if (!"ICT_STAFF".equalsIgnoreCase(actorRole == null ? "" : actorRole.trim())) {
            throw new SecurityException("Only the ICT System Admin can edit rooms.");
        }
        String oldVal = oldName == null ? "" : oldName.trim();
        String newVal = newName == null ? "" : newName.trim().toUpperCase(Locale.ROOT);
        if (oldVal.isEmpty() || newVal.isEmpty()) {
            throw new IllegalArgumentException("Room name cannot be empty.");
        }
        List<String> rooms = new ArrayList<>(load());
        int idx = -1;
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).equalsIgnoreCase(oldVal)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("Room not found.");
        for (int i = 0; i < rooms.size(); i++) {
            if (i != idx && rooms.get(i).equalsIgnoreCase(newVal)) {
                throw new IllegalArgumentException("A room with that name already exists.");
            }
        }
        rooms.set(idx, newVal);
        writeRooms(rooms);
        return true;
    }

    public static synchronized boolean deleteRoom(String roomName, String actorRole)
            throws IOException {
        if (!"ICT_STAFF".equalsIgnoreCase(actorRole == null ? "" : actorRole.trim())) {
            throw new SecurityException("Only the ICT System Admin can delete rooms.");
        }
        String val = roomName == null ? "" : roomName.trim();
        if (val.isEmpty()) throw new IllegalArgumentException("Room name required.");
        List<String> rooms = new ArrayList<>(load());
        boolean removed = rooms.removeIf(r -> r.equalsIgnoreCase(val));
        if (!removed) return false;
        writeRooms(rooms);
        return true;
    }

    private static void writeRooms(List<String> rooms) throws IOException {
        Path target = roomFile();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        List<String> lines = new ArrayList<>();
        lines.add("#Rooms");
        for (String r : rooms) lines.add("- " + r);
        Path dir = target.getParent();
        Path temporary = dir == null
                ? Files.createTempFile("room-options-", ".md")
                : Files.createTempFile(dir, "room-options-", ".md");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path roomFile() {
        for (Path candidate : new Path[] {
                Path.of("room.md"),
                Path.of("..", "room.md") }) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return Path.of("room.md");
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
