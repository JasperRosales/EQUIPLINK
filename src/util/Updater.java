package src.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks GitHub Releases for a newer Windows installer and applies it. */
public final class Updater {

    /** A newer release and its MSI download. */
    public record Release(String version, String msiUrl) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Updater() {
    }

    /** Returns the newer release, or null if up to date, offline, or not applicable. */
    public static Release check() {
        if (!AppInfo.isPackaged() || !isWindows()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://api.github.com/repos/" + AppInfo.repository() + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "EquipLink-Updater")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            String body = response.body();
            Matcher tag = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").matcher(body);
            Matcher msi = Pattern.compile(
                    "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.msi)\"").matcher(body);
            if (!tag.find() || !msi.find()) {
                return null;
            }
            String latest = tag.group(1);
            return compare(latest, AppInfo.version()) > 0 ? new Release(latest, msi.group(1)) : null;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            System.err.println("Update check failed: " + exception.getMessage());
            return null;
        }
    }

    /**
     * Downloads the installer, then hands off to a script that waits for this app to exit,
     * installs silently, and relaunches EquipLink. The caller should exit right after.
     */
    public static void downloadAndInstall(Release release) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("equiplink-update");
        Path msi = dir.resolve("EquipLink-" + release.version() + ".msi");
        HttpRequest request = HttpRequest.newBuilder(URI.create(release.msiUrl()))
                .header("User-Agent", "EquipLink-Updater")
                .GET().build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(msi));
        if (response.statusCode() != 200) {
            throw new IOException("Download failed (HTTP " + response.statusCode() + ")");
        }

        String exe = System.getProperty("jpackage.app-path", "");
        Path script = dir.resolve("update.bat");
        Files.writeString(script, String.join("\r\n",
                "@echo off",
                "ping -n 3 127.0.0.1 >nul",
                "msiexec /i \"%~1\" /passive /norestart",
                "if not \"%~2\"==\"\" start \"\" \"%~2\"",
                ""), StandardCharsets.US_ASCII);
        new ProcessBuilder("cmd", "/c", script.toString(), msi.toString(), exe).start();
    }

    /** Compares dotted numeric versions ("1.10.0" > "1.9.3"). */
    static int compare(String a, String b) {
        String[] x = a.split("[.-]");
        String[] y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int diff = Integer.compare(number(x, i), number(y, i));
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[i].replaceAll("\\D", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
