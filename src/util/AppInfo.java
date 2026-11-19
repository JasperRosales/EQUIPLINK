package src.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build metadata baked in by the release workflow (equiplink.properties). */
public final class AppInfo {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppInfo.class.getResourceAsStream("/equiplink.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException ignored) {
            // Development build: no metadata.
        }
    }

    private AppInfo() {
    }

    /** Installed version, e.g. "1.2.0", or "dev" when run from source. */
    public static String version() {
        return PROPS.getProperty("version", "dev");
    }

    /** GitHub "owner/name" that publishes releases, or null in development. */
    public static String repository() {
        return PROPS.getProperty("repo");
    }

    public static boolean isPackaged() {
        return !"dev".equals(version()) && repository() != null;
    }
}
