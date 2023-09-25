import Utils.json.JsonDict;

public class GHFile {
    public final GHItem repo;
    public final String path, sha;
    public final int size;
    public final GHFileType type;

    public GHFile(final GHItem repo, final JsonDict d) {
        this.repo = repo;

        path = d.getAsString("path");
        sha = d.getAsString("sha");
        switch (d.getAsString("type")) {
            case "blob":
                type = GHFileType.BLOB;
                size = d.getAsInt("size");
                break;
            case "tree":
                type = GHFileType.TREE;
                size = -1;
                break;
            default:
                type = GHFileType.UNKNOWN;
                size = -1;
                System.out.println(" -  -  - Unknown file type: " + d.getAsString("type"));
                break;
        }
    }
}