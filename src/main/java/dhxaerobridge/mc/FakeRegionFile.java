package dhxaerobridge.mc;

import java.io.File;
import java.nio.file.Path;

/**
 * Xaero's World Map only shows a region if a Minecraft region file (r.X.Z.mca) exists for it.
 * Regions that only exist as Distant Horizons LODs have no such file, and we do NOT want to
 * create fake files inside your world folder.
 *
 * So this object pretends the file exists. It never reads or writes anything:
 * the size check Xaero does is answered using a small placeholder file that lives in the
 * mod's own config folder instead.
 */
final class FakeRegionFile extends File {
    private static final long serialVersionUID = 1L;
    private final transient Path placeholder;

    FakeRegionFile(File wouldBeLocation, Path placeholder) {
        super(wouldBeLocation.getPath());
        this.placeholder = placeholder;
    }

    @Override public boolean exists() { return true; }
    @Override public boolean isFile() { return true; }
    @Override public boolean canRead() { return true; }
    @Override public long length() { return 16384L; }
    @Override public long lastModified() { return 1L; }
    @Override public Path toPath() { return placeholder; }
}
