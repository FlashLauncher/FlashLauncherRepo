public class Item {
    public final String repo, id, name, author, version, dependencies, description, shortDescription, asset, commit, icon;

    public Item(
            final String repo,
            final String id,
            final String name,
            final String author,
            final String version,
            final String dependencies,
            final String description, final String shortDescription,

            final String asset,

            final String commit,
            final String icon
    ) {
        this.repo = repo;
        this.id = id;
        this.name = name;
        this.author = author;
        this.version = version;
        this.dependencies = dependencies;
        this.description = description;
        this.shortDescription = shortDescription;

        this.asset = asset;

        this.commit = commit;
        this.icon = icon;
    }


}