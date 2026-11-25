package com.rs118.datavault;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Backup engine. Pure Java (no Android classes) so it can be unit tested on a desktop JVM.
 *
 * Layout inside the target app's private data directory:
 *
 * <pre>
 * vault_backups/
 *   settings.properties
 *   pending.properties        (operation to run at the next app start)
 *   20261124-193000/
 *     meta.properties
 *     data/   copy of /data/user/0/pkg  (minus vault_backups, lib, optionally cache)
 *     de/     copy of /data/user_de/0/pkg   (optional)
 *     ext/    copy of /sdcard/Android/data/pkg (optional)
 * </pre>
 */
public final class Vault {

    public static final String DIR_NAME = "vault_backups";
    public static final String PART_DATA = "data";
    public static final String PART_DE = "de";
    public static final String PART_EXT = "ext";

    static final String META = "meta.properties";
    static final String SETTINGS = "settings.properties";
    static final String PENDING = "pending.properties";
    static final String RUNNING = "pending.running";
    static final String LOCK = ".lock";

    static final String OP_BACKUP = "backup";
    static final String OP_RESTORE = "restore";

    /** How many automatic "before load" safety backups are kept. */
    static final int MAX_AUTO = 3;

    /** Files that are never copied (stale single-instance locks of Chromium based data dirs). */
    private static final Set<String> NEVER_COPY = new HashSet<>(
            Arrays.asList("SingletonLock", "SingletonSocket", "SingletonCookie"));

    public interface Progress {
        void onProgress(String message);
    }

    public static final class Settings {
        public boolean includeCache = false;
        public boolean includeDe = true;
        public boolean includeExt = false;
        public boolean backupBeforeLoad = true;
    }

    public static final class Backup {
        public final File dir;
        public final String id;
        public String name;
        public long created;
        public long bytes;
        public int files;
        public int skipped;
        public boolean auto;
        public boolean cache;
        public Set<String> parts = new LinkedHashSet<>();

        Backup(File dir) {
            this.dir = dir;
            this.id = dir.getName();
        }
    }

    public static final class Result {
        public final Backup backup;
        public final int skipped;

        Result(Backup backup, int skipped) {
            this.backup = backup;
            this.skipped = skipped;
        }
    }

    private static final class Counter {
        long bytes;
        int files;
        int skipped;
    }

    /** Aborts a whole operation (disk full). Individual unreadable files only increment "skipped". */
    private static final class Fatal extends RuntimeException {
        Fatal(String m, Throwable cause) {
            super(m, cause);
        }
    }

    private final File dataRoot;
    private final File deRoot;
    private final File extRoot;
    private final File vaultDir;

    public Vault(File dataRoot, File deRoot, File extRoot) {
        this.dataRoot = dataRoot;
        this.deRoot = deRoot;
        this.extRoot = extRoot;
        this.vaultDir = new File(dataRoot, DIR_NAME);
    }

    public File dataRoot() {
        return dataRoot;
    }

    public File vaultDir() {
        return vaultDir;
    }

    // ------------------------------------------------------------------ settings

    public Settings loadSettings() {
        Settings s = new Settings();
        Properties p = readProps(new File(vaultDir, SETTINGS));
        s.includeCache = bool(p, "includeCache", s.includeCache);
        s.includeDe = bool(p, "includeDe", s.includeDe);
        s.includeExt = bool(p, "includeExt", s.includeExt);
        s.backupBeforeLoad = bool(p, "backupBeforeLoad", s.backupBeforeLoad);
        return s;
    }

    public void saveSettings(Settings s) throws IOException {
        Properties p = new Properties();
        p.setProperty("includeCache", String.valueOf(s.includeCache));
        p.setProperty("includeDe", String.valueOf(s.includeDe));
        p.setProperty("includeExt", String.valueOf(s.includeExt));
        p.setProperty("backupBeforeLoad", String.valueOf(s.backupBeforeLoad));
        writePropsAtomic(new File(vaultDir, SETTINGS), p);
    }

    // ------------------------------------------------------------------ listing

    public List<Backup> list() {
        List<Backup> out = new ArrayList<>();
        File[] kids = vaultDir.listFiles();
        if (kids == null) return out;
        for (File k : kids) {
            if (!k.isDirectory() || k.getName().endsWith(".tmp")) continue;
            Backup b = readMeta(k);
            if (b != null) out.add(b);
        }
        Collections.sort(out, new Comparator<Backup>() {
            @Override
            public int compare(Backup a, Backup b) {
                return Long.compare(b.created, a.created);
            }
        });
        return out;
    }

    public Backup find(String id) {
        if (id == null) return null;
        File d = new File(vaultDir, id);
        return d.isDirectory() ? readMeta(d) : null;
    }

    private Backup readMeta(File dir) {
        File m = new File(dir, META);
        if (!m.isFile()) return null;
        Properties p = readProps(m);
        Backup b = new Backup(dir);
        b.name = p.getProperty("name", dir.getName());
        b.created = lng(p, "created", dir.lastModified());
        b.bytes = lng(p, "bytes", 0);
        b.files = (int) lng(p, "files", 0);
        b.skipped = (int) lng(p, "skipped", 0);
        b.auto = bool(p, "auto", false);
        b.cache = bool(p, "cache", false);
        for (String part : p.getProperty("parts", PART_DATA).split(",")) {
            if (!part.trim().isEmpty()) b.parts.add(part.trim());
        }
        return b;
    }

    // ------------------------------------------------------------------ create

    public synchronized Result create(String name, Settings s, boolean auto, Progress p) throws IOException {
        if (!vaultDir.isDirectory() && !vaultDir.mkdirs()) {
            throw new IOException("Cannot create " + vaultDir);
        }
        deleteStaleTmp();

        String id = newId();
        File tmp = new File(vaultDir, id + ".tmp");
        File fin = new File(vaultDir, id);
        Counter c = new Counter();
        Set<String> parts = new LinkedHashSet<>();

        try {
            Set<String> skipData = new HashSet<>(Arrays.asList(DIR_NAME, "lib"));
            if (!s.includeCache) {
                skipData.add("cache");
                skipData.add("code_cache");
            }
            say(p, "Copying app data…");
            copyChildren(dataRoot, new File(tmp, PART_DATA), skipData, c, p);
            parts.add(PART_DATA);

            if (s.includeDe && deRoot != null && deRoot.isDirectory() && !sameDir(deRoot, dataRoot)) {
                Set<String> skip = new HashSet<>();
                if (!s.includeCache) {
                    skip.add("cache");
                    skip.add("code_cache");
                }
                say(p, "Copying device-protected data…");
                copyChildren(deRoot, new File(tmp, PART_DE), skip, c, p);
                parts.add(PART_DE);
            }

            if (s.includeExt && extRoot != null && extRoot.isDirectory()) {
                Set<String> skip = new HashSet<>();
                if (!s.includeCache) skip.add("cache");
                say(p, "Copying external data…");
                copyChildren(extRoot, new File(tmp, PART_EXT), skip, c, p);
                parts.add(PART_EXT);
            }

            Properties meta = new Properties();
            meta.setProperty("name", cleanName(name));
            meta.setProperty("created", String.valueOf(System.currentTimeMillis()));
            meta.setProperty("bytes", String.valueOf(c.bytes));
            meta.setProperty("files", String.valueOf(c.files));
            meta.setProperty("skipped", String.valueOf(c.skipped));
            meta.setProperty("auto", String.valueOf(auto));
            meta.setProperty("cache", String.valueOf(s.includeCache));
            meta.setProperty("parts", join(parts));
            writePropsAtomic(new File(tmp, META), meta);

            if (!tmp.renameTo(fin)) throw new IOException("Cannot finalize backup folder");
        } catch (Fatal f) {
            deleteRecursive(tmp);
            throw new IOException(f.getMessage(), f);
        } catch (IOException | RuntimeException e) {
            deleteRecursive(tmp);
            throw e;
        }

        if (auto) pruneAuto();
        return new Result(find(id), c.skipped);
    }

    // ------------------------------------------------------------------ restore

    /** Replaces the app's data with the contents of the backup. Must run while the app is not running. */
    public synchronized Result restore(Backup b, Progress p) throws IOException {
        File srcData = new File(b.dir, PART_DATA);
        if (b.parts.contains(PART_DATA) && !srcData.isDirectory()) {
            throw new IOException("Backup is incomplete: data folder missing");
        }
        Counter c = new Counter();
        try {
            if (b.parts.contains(PART_DATA)) {
                say(p, "Restoring app data…");
                wipe(dataRoot, new HashSet<>(Arrays.asList(DIR_NAME, "lib")));
                copyChildren(srcData, dataRoot, null, c, p);
            }
            File srcDe = new File(b.dir, PART_DE);
            if (b.parts.contains(PART_DE) && srcDe.isDirectory() && deRoot != null
                    && !sameDir(deRoot, dataRoot)) {
                say(p, "Restoring device-protected data…");
                wipe(deRoot, new HashSet<String>());
                copyChildren(srcDe, deRoot, null, c, p);
            }
            File srcExt = new File(b.dir, PART_EXT);
            if (b.parts.contains(PART_EXT) && srcExt.isDirectory() && extRoot != null) {
                say(p, "Restoring external data…");
                extRoot.mkdirs();
                wipe(extRoot, new HashSet<String>());
                copyChildren(srcExt, extRoot, null, c, p);
            }
        } catch (Fatal f) {
            throw new IOException(f.getMessage(), f);
        }
        return new Result(b, c.skipped);
    }

    // ------------------------------------------------------------------ rename / delete

    public synchronized void rename(Backup b, String newName) throws IOException {
        File m = new File(b.dir, META);
        Properties p = readProps(m);
        p.setProperty("name", cleanName(newName));
        writePropsAtomic(m, p);
    }

    public synchronized void delete(Backup b) {
        deleteRecursive(b.dir);
    }

    private void pruneAuto() {
        List<Backup> autos = new ArrayList<>();
        for (Backup b : list()) if (b.auto) autos.add(b);
        for (int i = MAX_AUTO; i < autos.size(); i++) deleteRecursive(autos.get(i).dir); // list is newest first
    }

    private void deleteStaleTmp() {
        File[] kids = vaultDir.listFiles();
        if (kids == null) return;
        for (File k : kids) if (k.isDirectory() && k.getName().endsWith(".tmp")) deleteRecursive(k);
    }

    // ------------------------------------------------------------------ pending operations

    /** Queue "load this backup" to run at the next start of the app (before it touches its data). */
    public void queueRestore(Backup b, Settings s) throws IOException {
        Properties p = new Properties();
        p.setProperty("op", OP_RESTORE);
        p.setProperty("id", b.id);
        p.setProperty("backupBeforeLoad", String.valueOf(s.backupBeforeLoad));
        writePending(p, s);
    }

    /** Queue "take a backup" to run at the next start of the app, when nothing has files open. */
    public void queueBackup(String name, Settings s) throws IOException {
        Properties p = new Properties();
        p.setProperty("op", OP_BACKUP);
        p.setProperty("name", cleanName(name));
        writePending(p, s);
    }

    private void writePending(Properties p, Settings s) throws IOException {
        p.setProperty("includeCache", String.valueOf(s.includeCache));
        p.setProperty("includeDe", String.valueOf(s.includeDe));
        p.setProperty("includeExt", String.valueOf(s.includeExt));
        vaultDir.mkdirs();
        writePropsAtomic(new File(vaultDir, PENDING), p);
    }

    /**
     * Called very early in every start of the target app. Cheap when nothing is queued.
     * Returns a message to show the user once the UI is up, or null if nothing ran.
     */
    public String runPendingIfAny() {
        File pend = new File(vaultDir, PENDING);
        File run = new File(vaultDir, RUNNING);
        if (!pend.exists() && !run.exists()) return null;

        // Several processes of the same app can start at once: the first one does the job, the rest wait here.
        try (RandomAccessFile raf = new RandomAccessFile(new File(vaultDir, LOCK), "rw");
             FileChannel ch = raf.getChannel();
             FileLock ignored = ch.lock()) {

            if (pend.exists()) {
                run.delete();
                if (!pend.renameTo(run)) return null;
            }
            if (!run.exists()) return null; // another process finished it while we waited

            Properties pr = readProps(run);
            String op = pr.getProperty("op", "");
            Settings s = new Settings();
            s.includeCache = bool(pr, "includeCache", false);
            s.includeDe = bool(pr, "includeDe", true);
            s.includeExt = bool(pr, "includeExt", false);

            String msg;
            try {
                if (OP_BACKUP.equals(op)) {
                    Result r = create(pr.getProperty("name", "Backup"), s, false, null);
                    msg = "Backup saved: " + r.backup.name + skippedNote(r.skipped);
                } else if (OP_RESTORE.equals(op)) {
                    Backup b = find(pr.getProperty("id"));
                    if (b == null) {
                        msg = "Load failed: backup no longer exists";
                    } else {
                        if (bool(pr, "backupBeforeLoad", true) && !bool(pr, "autoDone", false)) {
                            create("Auto: before loading " + b.name, s, true, null);
                            pr.setProperty("autoDone", "true");
                            writePropsAtomic(run, pr); // so an interrupted load never snapshots a half-restored state
                        }
                        Result r = restore(b, null);
                        msg = "Loaded backup: " + b.name + skippedNote(r.skipped);
                    }
                } else {
                    msg = null;
                }
            } catch (Throwable t) {
                msg = "DataVault failed: " + t.getMessage();
            }
            run.delete();
            return msg;
        } catch (IOException e) {
            return "DataVault failed: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ file copying

    private void copyChildren(File srcDir, File dstDir, Set<String> topSkip, Counter c, Progress p) {
        dstDir.mkdirs();
        File[] kids = srcDir.listFiles();
        if (kids == null) {
            c.skipped++;
            return;
        }
        for (File k : kids) {
            if (topSkip != null && topSkip.contains(k.getName())) continue;
            copyEntry(k, new File(dstDir, k.getName()), c, p);
        }
    }

    private void copyEntry(File s, File d, Counter c, Progress p) {
        String name = s.getName();
        if (NEVER_COPY.contains(name)) return;
        Path sp = s.toPath();
        try {
            if (Files.isSymbolicLink(sp)) {
                Path target = Files.readSymbolicLink(sp);
                Files.deleteIfExists(d.toPath());
                Files.createSymbolicLink(d.toPath(), target);
            } else if (Files.isDirectory(sp, LinkOption.NOFOLLOW_LINKS)) {
                copyChildren(s, d, null, c, p);
                d.setLastModified(s.lastModified());
            } else if (Files.isRegularFile(sp, LinkOption.NOFOLLOW_LINKS)) {
                copyFile(s, d, c, p);
            }
            // sockets, fifos and device nodes are intentionally ignored
        } catch (Fatal f) {
            throw f;
        } catch (IOException | RuntimeException e) {
            c.skipped++;
        }
    }

    private void copyFile(File s, File d, Counter c, Progress p) throws IOException {
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] buf = new byte[1 << 17];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                c.bytes += n;
            }
        } catch (IOException e) {
            d.delete();
            String m = String.valueOf(e.getMessage());
            if (m.contains("ENOSPC") || m.contains("No space left")) {
                throw new Fatal("Not enough free storage space", e);
            }
            throw e;
        }
        d.setLastModified(s.lastModified());
        if (s.canExecute()) d.setExecutable(true, true);
        c.files++;
        if (p != null && c.files % 25 == 0) {
            p.onProgress(c.files + " files · " + formatSize(c.bytes));
        }
    }

    private void wipe(File root, Set<String> keep) {
        File[] kids = root.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (keep.contains(k.getName())) continue;
            deleteRecursive(k);
        }
    }

    static void deleteRecursive(File f) {
        if (f == null) return;
        if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean sameDir(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return a.equals(b);
        }
    }

    private String newId() {
        String base = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String id = base;
        int i = 2;
        while (new File(vaultDir, id).exists() || new File(vaultDir, id + ".tmp").exists()) {
            id = base + "-" + i++;
        }
        return id;
    }

    static String cleanName(String n) {
        if (n == null) n = "";
        n = n.trim().replace('\n', ' ').replace('\r', ' ');
        if (n.isEmpty()) n = "Backup";
        return n.length() > 60 ? n.substring(0, 60) : n;
    }

    private static String skippedNote(int skipped) {
        return skipped > 0 ? " (" + skipped + " unreadable items skipped)" : "";
    }

    private static void say(Progress p, String m) {
        if (p != null) p.onProgress(m);
    }

    private static String join(Set<String> s) {
        StringBuilder sb = new StringBuilder();
        for (String x : s) {
            if (sb.length() > 0) sb.append(',');
            sb.append(x);
        }
        return sb.toString();
    }

    public static String formatSize(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    private static boolean bool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static long lng(Properties p, String k, long def) {
        try {
            return Long.parseLong(p.getProperty(k, ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Properties readProps(File f) {
        Properties p = new Properties();
        if (!f.isFile()) return p;
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException ignored) {
        }
        return p;
    }

    private static void writePropsAtomic(File target, Properties p) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(target.getPath() + ".w");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, null);
            out.getFD().sync();
        }
        target.delete();
        if (!tmp.renameTo(target)) throw new IOException("Cannot write " + target.getName());
    }
}
