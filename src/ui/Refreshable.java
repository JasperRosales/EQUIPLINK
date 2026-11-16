package src.ui;

/**
 * A page that can reload its data in place (keeping search and filters) when the
 * live-sync poller detects that requests or notifications changed elsewhere.
 */
public interface Refreshable {

    void reloadData();
}
