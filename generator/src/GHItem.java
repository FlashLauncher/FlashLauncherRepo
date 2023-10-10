import Utils.IniGroup;
import Utils.Version;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.web.WebClient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GHItem {
    public static final WebClient c = new WebClient() {{ allowRedirect = true; }};
    public static final List<String>
            info = Arrays.asList(
                    "fl-plugin.ini",
                    "res/fl-plugin.ini",
                    "assets/fl-plugin.ini",
                    "src/fl-plugin.ini"
            ), icon = Arrays.asList(
                    "icon.png",
                    "res/icon.png",
                    "src/icon.png"
            );

    public final Main m;

    public final String
            repo,
            tag,
            commit,
            url
    ;

    public final ArrayList<GHFile> files = new ArrayList<>();

    public GHItem(final Main main, final String repo, final String tag, final String commit, final String url) throws Exception {
        this.m = main;
        this.repo = repo;
        this.tag = tag;
        this.commit = commit;
        this.url = url;

        System.out.println(" -  -  - Commit: " + commit);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        c.open("GET", new URI("https://api.github.com/repos/" + repo + "/git/trees/" + commit + "?recursive=1"), os, true).auto();

        final JsonDict d = Json.parse(new String(os.toByteArray(), StandardCharsets.UTF_8)).getAsDict();
        if (d.getAsBool("truncated")) {
            System.out.println("Check " + repo + " : " + commit);
            throw new Exception("I can't check");
        }
        for (final JsonDict fd : d.getAsList("tree").toArray(new JsonDict[0]))
            files.add(new GHFile(this, fd));
        for (final GHFile f : files)
            if (info.contains(f.path)) {
                System.out.println(" -  -  - Info: " + f.path);
                os = new ByteArrayOutputStream();
                //c.open("GET", new URI("https://api.github.com/repos/" + repo + "/contents/" + f.path + "?ref=" + commit), os, true).auto();
                c.open("GET", new URI("https://raw.githubusercontent.com/" + repo + "/" + commit + "/" + f.path), os, true).auto();

                final IniGroup r = new IniGroup(new String(os.toByteArray(), StandardCharsets.UTF_8), false);

                final String
                        id = r.getAsString("id"),
                        name = r.getAsString("name"),
                        author = r.getAsString("author"),
                        version = r.getAsString("version"),
                        dependencies = r.getAsString("dependencies"),

                        description = r.getAsString("description"),
                        shortDescription = r.getAsString("shortDescription")
                ;

                if (
                        id == null ||
                        version == null
                )
                    throw new Exception("Property is null");

                String ver = "*";

                if (dependencies != null)
                    for (final String dep : dependencies.split(";")) {
                        final int i = dep.indexOf(':');
                        if (i == -1)
                            continue;
                        if (dep.substring(0, i).equals("flash-launcher")) {
                            ver = dep.substring(i + 1);
                            break;
                        }
                    }

                String i = null;
                for (final GHFile f1 : files)
                    if (icon.contains(f1.path)) {
                        i = f1.path;
                        break;
                    }
                final Item item = new Item(
                        repo,
                        id, name, author, version, dependencies,
                        description, shortDescription,
                        url,
                        commit, i
                );

                for (final Version v : m.versions)
                    if (v.isCompatibility(ver))
                        (
                                main.official.contains(repo) ?
                                        main.officials.get(v) :
                                        main.communities.get(v)
                        ).add(item);
                return;
            }
    }
}