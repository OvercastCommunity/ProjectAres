package tc.oc.commons.bukkit.tablist;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import tc.oc.commons.core.util.DefaultProvider;

import javax.annotation.Nullable;

/**
 * A single player's tab list. When this view is enabled, it creates a scoreboard team for
 * each slot and creates an entry for each team. The team names are used to order the list.
 * The view is always full of entries. When an entry is removed, it is replaced by a blank
 * one. The player's list is not disabled, but because it is always full of fake entries,
 * the real entries are pushed off the bottom and cannot be seen. The fake team names all
 * start with a '\u0001' character, so they will always come before any real teams.
 */
public class TabView {

    public static class Factory implements DefaultProvider<Player, TabView> {
        @Override
        public TabView get(Player key) {
            return new TabView(key);
        }
    }

    private static final int WIDTH = 4, HEIGHT = 20;

    private final int size, headerSlot, footerSlot;

    // The single player seeing this view
    private final Player viewer;

    protected @Nullable TabManager manager;

    // True when any slots/header/footer have been changed but not rendered
    private boolean dirtyLayout, dirtyContent, dirtyHeaderFooter;

    private final TabEntry[] slots, rendered;

    private @Nullable BukkitTask fakeEntityTask;

    public TabView(Player viewer) {
        this.viewer = viewer;
        this.size = WIDTH * HEIGHT;
        this.headerSlot = this.size;
        this.footerSlot = this.headerSlot + 1;

        // Two extra slots for header/footer
        this.slots = new TabEntry[this.size + 2];
        this.rendered = new TabEntry[this.size + 2];
    }

    private void assertEnabled() {
        if(manager == null) throw new IllegalStateException(getClass().getSimpleName()  + " is not enabled");
    }

    public Player getViewer() {
        return viewer;
    }

    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public int getSize() {
        return this.size;
    }

    /**
     * Take control of the viewer's player list
     */
    public void enable(TabManager manager) {
        if(this.manager != null) disable();
        this.manager = manager;
        this.setup();

        this.invalidateLayout();
        this.invalidateContent();
        this.invalidateHeaderFooter();
    }

    /**
     * Tear down the display and return control the the viewer's player list to bukkit
     */
    public void disable() {
        if(this.manager != null) {
            if(fakeEntityTask != null) {
                fakeEntityTask.cancel();
                fakeEntityTask = null;
            }

            this.manager.removeView(this);
            this.tearDown();

            for(int i = 0; i < slots.length; i++) {
                if(slots[i] != null) {
                    slots[i].removeFromView(this);
                    slots[i] = null;
                }
            }

            this.manager = null;
        }
    }

    private void invalidateManager() {
        if(this.manager != null) this.manager.invalidate();
    }

    protected void invalidateLayout() {
        if(!this.dirtyLayout) {
            this.dirtyLayout = true;
            this.invalidateManager();
        }
    }

    protected void invalidateContent() {
        if(!this.dirtyContent) {
            this.dirtyContent = true;
            this.invalidateManager();
        }
    }

    protected void invalidateLayoutAndContent() {
        if(!dirtyLayout || !dirtyContent) {
            dirtyLayout = dirtyContent = true;
            invalidateManager();
        }
    }

    protected void invalidateContent(TabEntry entry) {
        int slot = getSlot(entry);
        if(slot >= this.size) {
            this.invalidateHeaderFooter();
        } else if(slot >= 0) {
            this.invalidateContent();
        }
    }

    protected void invalidateHeaderFooter() {
        if(!this.dirtyHeaderFooter) {
            this.dirtyHeaderFooter = true;
            this.invalidateManager();
        }
    }

    protected boolean isLayoutDirty() {
        return this.dirtyLayout;
    }

    protected int getSlot(TabEntry entry) {
        for(int i = 0; i < slots.length; i++) {
            if(entry == slots[i]) return i;
        }
        return -1;
    }

    private void setSlot(int slot, @Nullable TabEntry entry) {
        assertEnabled();

        if(entry == null) {
            entry = this.manager.getBlankEntry(slot);
        }

        TabEntry oldEntry = this.slots[slot];
        if(oldEntry != entry) {
            oldEntry.removeFromView(this);

            int oldIndex = getSlot(entry);

            if(oldIndex != -1) {
                TabEntry blankEntry = this.manager.getBlankEntry(oldIndex);
                this.slots[oldIndex] = blankEntry;
                blankEntry.addToView(this);
            } else {
                entry.addToView(this);
            }

            this.slots[slot] = entry;

            if(slot < this.size) {
                this.invalidateLayoutAndContent();
            } else {
                this.invalidateHeaderFooter();
            }
        }
    }

    public void setSlot(int x, int y, @Nullable TabEntry entry) {
        this.setSlot(this.slotIndex(x, y), entry);
    }

    public void setHeader(@Nullable TabEntry entry) {
        this.setSlot(this.headerSlot, entry);
    }

    public void setFooter(@Nullable TabEntry entry) {
        this.setSlot(this.footerSlot, entry);
    }

    private int slotIndex(int x, int y) {
        return x * HEIGHT + y;
    }

    public void render() {
        if(this.manager == null) return;

        TabRender render = new TabRender(this);
        this.renderLayout(render);
        this.renderContent(render);
        this.markSlotsClean();
        this.renderHeaderFooter(render, false);
        render.finish();
    }

    public void renderLayout(TabRender render) {
        if(this.manager == null) return;

        if(this.dirtyLayout) {
            this.dirtyLayout = false;

            // First search for entries that have been added, removed, or moved
            Map<TabEntry, Integer> removals = new HashMap<>();
            Map<TabEntry, Integer> additions = new HashMap<>();

            for(int index = 0; index < this.size; index++) {
                TabEntry oldEntry = this.rendered[index];
                TabEntry newEntry = this.rendered[index] = this.slots[index];

                if(oldEntry != newEntry) {
                    // There is a different entry in this slot

                    Integer oldIndex = removals.remove(newEntry);
                    if(oldIndex == null) {
                        // We have not seen the new entry yet, so assume it's being added
                        additions.put(newEntry, index);
                    } else {
                        // We already saw the new entry removed from another slot, so it's actually being moved
                        render.changeSlot(newEntry, oldIndex, index);
                    }

                    Integer newIndex = additions.remove(oldEntry);
                    if(newIndex == null) {
                        // We have not seen the old entry yet, so assume it's being removed
                        removals.put(oldEntry, index);
                    } else {
                        // We already saw the old entry added to another slot, so it's actually being moved
                        render.changeSlot(oldEntry, index, newIndex);
                    }
                }

            }

            // Build the removal packet
            for(Map.Entry<TabEntry, Integer> removal : removals.entrySet()) {
                render.removeEntry(removal.getKey(), removal.getValue());
            }

            // Build the addition packet (this also adds to the update packet)
            for(Map.Entry<TabEntry, Integer> addition : additions.entrySet()) {
                render.addEntry(addition.getKey(), addition.getValue());
            }
        }
    }

    public void renderContent(TabRender render) {
        if(this.manager == null) return;

        if(this.dirtyContent) {
            this.dirtyContent = false;

            // Build the update packet from entries with new content that are not being added or removed
            for(int i = 0; i < this.size; i++) {
                if(this.slots[i].isDirty(this)) {
                    render.updateEntry(this.slots[i], i);
                }
            }
        }
    }

    public void markSlotsClean() {
        for(TabEntry entry : slots) {
            entry.markClean(this);
        }
    }

    public void renderHeaderFooter(TabRender render, boolean force) {
        if(this.manager == null) return;

        if(force || this.dirtyHeaderFooter) {
            this.dirtyHeaderFooter = false;
            render.setHeaderFooter(this.rendered[this.headerSlot] = this.slots[this.headerSlot],
                                   this.rendered[this.footerSlot] = this.slots[this.footerSlot]);
            this.slots[this.headerSlot].markClean(this);
            this.slots[this.footerSlot].markClean(this);
        }
    }

    private void setup() {
        assertEnabled();

        for(int slot = 0; slot < this.slots.length; slot++) {
            this.slots[slot] = this.manager.getBlankEntry(slot);
            this.slots[slot].addToView(this);
        }

        TabRender render = new TabRender(this);

        for(int index = 0; index < this.size; index++) {
            render.createSlot(this.rendered[index] = this.slots[index], index);
        }

        this.renderHeaderFooter(render, true);

        render.finish();
    }

    private void tearDown() {
        if(this.manager == null) return;

        TabRender render = new TabRender(this);

        render.setHeaderFooter(this.manager.getBlankEntry(this.headerSlot),
                               this.manager.getBlankEntry(this.footerSlot));

        for(int index = 0; index < this.size; index++) {
            render.destroySlot(this.rendered[index], index);
            this.rendered[index] = null;
        }

        render.finish();
    }

    protected void refreshEntry(TabEntry entry) {
        if(this.manager == null) return;

        TabRender render = new TabRender(this);
        int slot = getSlot(entry);
        if(slot < this.size) {
            render.refreshEntry(entry, slot);
        } else {
            this.renderHeaderFooter(render, true);
        }
        render.finish();
    }

    protected void updateFakeEntity(TabEntry entry) {
        if(this.manager == null) return;

        TabRender render = new TabRender(this);
        render.updateFakeEntity(entry, false);
        render.finish();
    }

    private void respawnFakeEntities() {
        if(this.manager == null || fakeEntityTask != null) return;

        fakeEntityTask = this.viewer.getServer().getScheduler().runTask(this.manager.getPlugin(), () -> {
            fakeEntityTask = null;
            TabRender render = new TabRender(TabView.this);
            for(TabEntry entry : TabView.this.rendered) {
                render.updateFakeEntity(entry, true);
            }
            render.finish();
        });
    }

    protected void onRespawn(PlayerRespawnEvent event) {
        if(viewer.equals(event.getPlayer())) this.respawnFakeEntities();
    }

    protected void onWorldChange(PlayerChangedWorldEvent event) {
        if(viewer.equals(event.getPlayer())) this.respawnFakeEntities();
    }
}
