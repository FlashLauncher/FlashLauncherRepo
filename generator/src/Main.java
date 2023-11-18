import Utils.Core;
import Utils.IniGroup;
import Utils.ListMap;
import Utils.Version;
import Utils.fixed.FixedSet;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonElement;
import Utils.json.JsonList;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(final String[] args) throws Exception { new Main(); }

    public final FixedSet<Version> versions;
    public final FixedSet<String> official, blocked;

    public final ListMap<Version, ArrayList<Item>>
                officials = new ListMap<>(),
                communities = new ListMap<>()
    ;

    public Main() throws Exception {
        try (final Scanner s = new Scanner(new File("versions.txt"))) {
            final ArrayList<Version> bl = new ArrayList<>();
            while (s.hasNextLine()) {
                final String l = s.nextLine();
                if (l.isEmpty() || l.startsWith("#"))
                    continue;
                bl.add(new Version(l) {{
                    officials.put(this, new ArrayList<>());
                    communities.put(this, new ArrayList<>());
                }});
            }
            versions = new FixedSet<>(bl.toArray(new Version[0]));
        }

        try (final Scanner s = new Scanner(new File("official.txt"))) {
            final ArrayList<String> bl = new ArrayList<>();
            while (s.hasNextLine()) {
                final String l = s.nextLine();
                if (l.isEmpty() || l.startsWith("#"))
                    continue;
                bl.add(l);
            }
            official = new FixedSet<>(bl.toArray(new String[0]));
        }

        try (final Scanner s = new Scanner(new File("blocklist.txt"))) {
            final ArrayList<String> bl = new ArrayList<>();
            while (s.hasNextLine()) {
                final String l = s.nextLine();
                if (l.isEmpty() || l.startsWith("#"))
                    continue;
                bl.add(l);
            }
            blocked = new FixedSet<>(bl.toArray(new String[0]));
        }

        final WebClient c = new WebClient() {{ allowRedirect = true; }};

        System.out.println("Searching:");
        int page = 1;
        while (true) {
            System.gc();
            System.out.println(" - page " + page + ":");
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            c.open("GET", new URI("https://api.github.com/search/repositories?q=topic:mcflashlauncher-plugin&per_page=100&page=" + (page++)), os, true).auto();

            final JsonDict d = Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsDict();
            final JsonList l = d.getAsList("items");

            rl:
            for (final JsonDict e : l.toArray(new JsonDict[0])) {
                final String fn = e.getAsString("full_name");
                if (blocked.contains(fn)) {
                    System.out.println(" -  - Skipped:"  + fn);
                    continue;
                }
                System.out.println(" -  - " + fn);
                os = new ByteArrayOutputStream();
                final JsonList li;
                while (true) {
                    final WebResponse r = c.open("GET", new URI("https://api.github.com/repos/" + fn + "/releases"), os, true);
                    r.auto();
                    if (r.getResponseCode() == 200) {
                        li = Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsList();
                        break;
                    }
                    final long s = Long.parseLong(r.headers.get("X-RateLimit-Reset")) - System.currentTimeMillis() / 1000;
                    System.out.println("Wait " + s + "s");
                    if (s > 0)
                        Thread.sleep(s * 1000);
                }
                if (li.isEmpty())
                    continue;
                for (final JsonDict di : li.toArray(new JsonDict[0]))
                    for (final JsonDict a : di.getAsList("assets").toArray(new JsonDict[0])) {
                        final String fileName = a.getAsString("name"), tagName = di.getAsString("tag_name");
                        if (fileName.endsWith(".jar")) {
                            os = new ByteArrayOutputStream();
                            c.open("GET", new URI("https://api.github.com/repos/" + fn + "/tags"), os, true).auto();
                            for (final JsonDict tag : Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsList().toArray(new JsonDict[0]))
                                if (tag.getAsString("name").equals(tagName)) {
                                    new GHItem(this, fn, tagName, tag.getAsDict("commit").getAsString("sha"), a.getAsString("browser_download_url"));
                                    continue rl;
                                }
                        }
                    }
            }

            if (l.size() < 100)
                break;
        }

        System.gc();

        System.out.println("Saving ...");
        save("main", officials);
        save("user", communities);
    }

    public void save(final String name, final ListMap<Version, ArrayList<Item>> d) throws Exception {
        final File f = new File(name + "/" + name + ".ini");
        if (!f.getParentFile().exists())
            f.getParentFile().mkdirs();
        else
            for (final File file : f.getParentFile().listFiles())
                if (file.getName().endsWith(".json"))
                    file.delete();
        try (final FileOutputStream osm = new FileOutputStream(f)) {
            System.out.println(" - " + name);
            final IniGroup m = new IniGroup();
            for (final Map.Entry<Version, ArrayList<Item>> e : d.entrySet()) {
                System.out.println(" -  - " + e.getKey());
                final IniGroup vg = m.newGroup(e.getKey().toString());
                int p = 0, i = 0;
                final JsonList l = new JsonList();
                for (final Item item : e.getValue()) {
                    l.add(new JsonDict() {{
                        put("id", item.id);
                        put("name", item.name);
                        put("version", item.version);
                        put("author", item.author);
                        if (item.icon != null)
                            put("icon", "https://raw.githubusercontent.com/" + item.repo + "/" + item.commit + "/" + item.icon);
                        if (item.dependencies != null)
                            put("dependencies", item.dependencies);
                        if (item.description != null)
                            put("description", item.description);
                        if (item.shortDescription != null)
                            put("shortDescription", item.shortDescription);
                        put("asset", item.asset);
                    }});
                    if (++i == 50) {
                        i = 1;
                        final byte[] r = l.toString().getBytes(StandardCharsets.UTF_8);
                        vg.put(Core.hashToHex("sha-256", r), name + "/" + name + "-" + p + ".json");
                        try (final FileOutputStream os = new FileOutputStream(name + "/" + name + "-" + p++ + ".json")) {
                            os.write(r);
                        }
                    }
                }
                if (!l.isEmpty()) {
                    final byte[] r = l.toString().getBytes(StandardCharsets.UTF_8);
                    vg.put(Core.hashToHex("sha-256", r), name + "/" + name + "-" + p + ".json");
                    try (final FileOutputStream os = new FileOutputStream(name + "/" + name + "-" + p + ".json")) {
                        os.write(r);
                    }
                }
            }

            osm.write(m.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}