import Utils.IniGroup;
import Utils.Pair;
import Utils.Version;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.json.JsonList;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GHInfo {
    public final List<String> dirs = Arrays.asList("res", "resources", "assets", "src", "sources");

    public String fullName, id, name, author, desc, shortDesc;
    public Version version;
    public final JsonDict dependencies = new JsonDict();

    public GHInfo(final String fullName) throws Throwable {
        System.out.println(" - " + fullName);
        this.fullName = fullName;

        final JsonList l;
        {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            Main.c.open("GET", new URL("https://api.github.com/repos/" + fullName + "/contents"), os, true).auto();
            l = Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsList();
        }

        final boolean[] dirsB = new boolean[dirs.size()];
        Arrays.fill(dirsB, false);
        for (final JsonDict d : l.toArray(new JsonDict[0]))
            if (d.getAsString("type").equals("dir")) {
                final int index = dirs.indexOf(d.getAsString("name"));
                if (index == -1) continue;
                dirsB[index] = true;
            } else if (d.getAsString("name").equals("fl-plugin.ini")) {
                parse(d.getAsString("download_url"));
                return;
            }
        for (int i = 0; i < dirsB.length; i++)
            if (dirsB[i]) {
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                Main.c.open("GET", new URL("https://api.github.com/repos/" + fullName + "/contents/" + dirs.get(i)), os, true).auto();
                for (final JsonDict d : Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsList().toArray(new JsonDict[0]))
                    if (d.getAsString("name").equals("fl-plugin.ini") && d.getAsString("type").equals("file")) {
                        parse(d.getAsString("download_url"));
                        return;
                    }
            }
    }

    private void parse(final String url) throws Throwable {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        Main.c.open("GET", new URL(url), os, true).auto();
        final IniGroup ini = new IniGroup(new String(os.toByteArray(), StandardCharsets.UTF_8));
        id = ini.getAsString("id");
        if (id == null || id.length() == 0) return;
        name = ini.getAsString("name");
        if (name == null || name.length() == 0) name = id;
        version = new Version(ini.getAsString("version"));
        author = ini.getAsString("author");
        desc = ini.getAsString("description");
        shortDesc = ini.getAsString("shortDescription");
        if (ini.has("dependencies"))
            for (final String dep : ini.getAsString("dependencies").split(",")) {
                final int i = dep.indexOf(':');
                final String name, version;
                if (i == 0) return;
                if (i == -1) {
                    name = dep;
                    version = "*";
                } else {
                    name = dep.substring(0, i);
                    version = dep.length() > i + 1 ? dep.substring(i + 1) : "*";
                }
                dependencies.put(name, version);
            }
        final String launcherVersions = dependencies.getAsStringOrDefault("flash-launcher", "*");
        final HashMap<Version, JsonList> repo = Main.official.contains(fullName) ? Main.mainRepo : Main.userRepo;
        boolean added = false;
        for (final Version ver : Main.versions) {
            if (!ver.isCompatibility(launcherVersions)) continue;
            if (!added) added = true;
            JsonList l = repo.get(ver);
            if (l == null) repo.put(ver, l = new JsonList());
            l.add(new JsonDict() {{
                put("id", id);
                put("name", name);
                put("version", version.toString());
                put("dependencies", dependencies);
                put("author", author);
                put("description", desc);
                put("shortDescription", shortDesc);
            }});
        }
    }
}