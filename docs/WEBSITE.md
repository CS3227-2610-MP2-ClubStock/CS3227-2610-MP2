# Maintaining the project website

The site uses GitHub Pages' built-in Jekyll build and the Minima theme.
`index.md` becomes the home page, `_config.yml` defines the theme and navigation,
and the existing guides become HTML pages. The relative-links plugin converts
links between Markdown documents to their published URLs.

## Publish

For local previews, use Ruby 3.3 (the GitHub Pages dependencies exclude Ruby 4).
From `docs/`, run `bundle install`, then `bundle exec jekyll serve`, and open
<http://localhost:4000/CS3227-2610-MP2/>. Commit `Gemfile.lock` when dependencies
change; generated site files and local caches are ignored.

1. Commit and push the website changes to the branch selected in the repository's
   **Settings → Pages**.
2. Under **Build and deployment**, select **Deploy from a branch**, that branch,
   and **/docs**, then save.
3. In **Actions**, wait for the Pages build and deployment to succeed. If it
   fails, open the failed job's log for the actual error.
4. Open <https://cs3227-2610-mp2-clubstock.github.io/CS3227-2610-MP2/>.
   The repository name is part of the URL; `/docs` is not.

Do not add `.nojekyll`: this site needs Jekyll to turn Markdown into HTML.
If the repository is renamed or a custom domain is added, update `url` and
`baseurl` in `_config.yml` to match.

## Add a screenshot

Save a screenshot of the running application as `docs/assets/images/clubstock.png`
(create the directories if needed). Uncomment the `screenshot` setting in
`docs/_config.yml`. The home page displays the image only when this setting exists.
Use demonstration data and update the image's alternative text in `index.md`
to describe the screen shown.

## Edit content

Edit `index.md` for the introduction and the existing `UserGuide.md` and
`DeveloperGuide.md` for the guides. Keep their YAML front matter at the top.
`header_pages` controls the top navigation. Other Markdown documents are rendered
using the same theme through the optional-front-matter plugin and layout defaults.

Mermaid blocks in the Developer Guide remain source code under the standard theme;
GitHub's repository Markdown viewer renders those diagrams.

This maintenance file is excluded from the published site.
