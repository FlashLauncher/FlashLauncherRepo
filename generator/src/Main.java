import Utils.Core;
import Utils.IniGroup;
import Utils.Version;
import Utils.fixed.FixedSet;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonList;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static WebClient c = new WebClient() {{ allowRedirect = true; }};

    public static final FixedSet<String> official = new FixedSet<>(new String[] { "FlashLauncher/ExampleJavaPlugin", "FlashLauncher/FlashLauncherRepo" });
    public static final FixedSet<String> ignored = new FixedSet<>(new String[] {  });
    public static final FixedSet<Version> versions;

    public static final IniGroup main = new IniGroup();
    public static final IniGroup user = new IniGroup();

    public static final HashMap<Version, JsonList> mainRepo = new HashMap<>();
    public static final HashMap<Version, JsonList> userRepo = new HashMap<>();

    static {
        ArrayList<Version> launcherVersions = new ArrayList<>();
        for (final String ver : new String[]{ "0.1.7" }) {
            launcherVersions.add(new Version(ver));
            main.newGroup(ver);
            user.newGroup(ver);
        }
        versions = new FixedSet<>(launcherVersions.toArray(new Version[0]));
    }

    public static void main(final String[] args) {
        try {
            for (final File f : new File("main").listFiles())
                if ((f.getName().startsWith("main-") || f.getName().startsWith("user-")) && f.getName().endsWith(".json"))
                    f.delete();
            for (final File f : new File("user").listFiles())
                if ((f.getName().startsWith("main-") || f.getName().startsWith("user-")) && f.getName().endsWith(".json"))
                    f.delete();

            int page = 1;
            while (true) {
                System.gc();
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                System.out.println("Page " + page);
                final WebResponse r = c.open("GET", new URL("https://api.github.com/search/repositories?q=topic:mcflashlauncher-plugin&per_page=100&page=" + (page++)), os, false);
                r.connect();
                r.readAll();
                r.close();

                try {
                    System.out.println("Parsing ...");
                    final JsonDict d = Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsDict();
                    final JsonList l = d.getAsList("items");

                    for (final JsonDict e : l.toArray(new JsonDict[0])) {
                        final String fn = e.getAsString("full_name");
                        if (ignored.contains(fn)) continue;
                        new GHInfo(fn);
                    }

                    if (l.size() != 100) break;
                } catch (final Throwable ex) {
                    throw ex;
                }
            }
            System.out.println("Exporting ...");
            for (final Map.Entry<Version, JsonList> e : mainRepo.entrySet()) {
                final String hash = save("main/main-", e.getValue());
                main.getAsGroup(e.getKey().toString()).put(hash, "main-" + hash + ".json");
            }
            for (final Map.Entry<Version, JsonList> e : userRepo.entrySet()) {
                final String hash = save("user/user-", e.getValue());
                user.getAsGroup(e.getKey().toString()).put(hash, "user-" + hash + ".json");
            }
            new FileOutputStream("main/main.ini") {{
                write(main.toString().getBytes(StandardCharsets.UTF_8));
                close();
            }};
            new FileOutputStream("user/user.ini") {{
                write(user.toString().getBytes(StandardCharsets.UTF_8));
                close();
            }};
            System.out.println("Finished!");
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    public static String save(final String prefix, final JsonList l) throws NoSuchAlgorithmException, IOException {
        final byte[] d = l.toString().getBytes(StandardCharsets.UTF_8);
        final String h = Core.hashToHex("SHA-256", d);
        final File f = new File(prefix + h + ".json");
        if (f.exists()) f.delete();
        new FileOutputStream(f) {{
            write(d);
            close();
        }};
        return h;
    }
}