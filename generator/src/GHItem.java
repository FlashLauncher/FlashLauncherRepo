import Utils.*;
import Utils.json.Json;
import Utils.json.JsonDict;
import Utils.web.WebClient;
import Utils.web.WebResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GHItem {
    public static final String token = System.getenv("token");

    public static final WebClient c = new WebClient() {
        private WebResponse superOpen(final String method, final URL urlAddr, final OutputStream outputStream, final boolean autoCloseStream) throws IOException {
            return super.open(method, urlAddr, outputStream, autoCloseStream);
        }

        @Override
        public WebResponse open(final String method, final URL urlAddr, final OutputStream outputStream, final boolean autoCloseStream) throws IOException {
            return new WebResponse(outputStream) {
                @Override public void connect(final Map<String, String> headers, final byte[] data) throws IOException, InterruptedException {}
                @Override public void readAll() throws IOException, InterruptedException {}
                @Override public void close() throws IOException { outputStream.flush(); if (autoCloseStream) outputStream.close(); }

                @Override
                public void auto() throws IOException, InterruptedException {
                    while (true) {
                        final ByteArrayOutputStream sos = new ByteArrayOutputStream();
                        final WebResponse r = superOpen(method, urlAddr, sos, true);
                        final String h;
                        String h1 = null;
                        try {
                            h1 = Core.hashStringToHex("SHA-256", urlAddr.toString());
                        } catch (final NoSuchAlgorithmException ex) {
                            ex.printStackTrace();
                        }
                        h = h1;


                        File f = h == null ? null : new File(".cache/" + h), f2 = h == null ? null : new File(".cache/" + h + ".tag");

                        r.auto(new ListMap<String, String>() {{
                            if (h != null && f2.exists())
                                put("If-None-Match", new String(IO.readFully(f2), StandardCharsets.UTF_8));
                            if (token != null) {
                                put("Content-Type", "application/vnd.github.v3+json");
                                put("Authorization", "Bearer " + token);
                            }
                        }}, NO_DATA);

                        if (r.getResponseCode() == 200) {
                            os.write(sos.toByteArray());
                            if (h != null && r.headers.containsKey("ETag")) {
                                Files.write(f2.toPath(), r.headers.get("ETag").getBytes(StandardCharsets.UTF_8));
                                Files.write(f.toPath(), sos.toByteArray());
                            }
                            break;
                        }

                        if (r.getResponseCode() == 304) {
                            os.write(IO.readFully(f));
                            break;
                        }

                        if (r.getResponseCode() == 403) {
                            System.out.println(r.headers);
                        }

                        final long s = Long.parseLong(r.headers.get("X-RateLimit-Reset")) * 1000 - System.currentTimeMillis();
                        System.out.println("Code: " + r.getResponseCode() + ". Wait " + (s / 1000) + "s");
                        if (s > 0)
                            Thread.sleep(s);
                    }
                    close();
                }
            };
        }


        { allowRedirect = true; }
    };
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

    public GHItem(final Main main, final String repo, final String tag, final String commit, final String url, final ArrayList<Version> vl) throws Exception {
        this.m = main;
        this.repo = repo;
        this.tag = tag;
        this.commit = commit;
        this.url = url;

        final boolean l = repo.equals("FlashLauncher/FlashLauncher");

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
            if (l ? f.path.equals("res/fl-info.ini") : info.contains(f.path)) {
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

                for (int i1 = vl.size() - 1; i1 >= 0; i1--) {
                    final Version v = vl.get(i1);
                    if (v.isCompatibility(ver)) {
                        System.out.println(" -  -  -  - " + v);
                        vl.remove(i1);
                        (
                                main.official.contains(repo) ?
                                        main.officials.get(v) :
                                        main.communities.get(v)
                        ).add(item);
                    }
                }
                return;
            }
    }
}