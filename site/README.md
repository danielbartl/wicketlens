# WicketLens landing page

A standalone static page for GitHub Pages, using the warm backgrounds, Wicket
orange, restrained Spring green, system fonts, and light/dark themes of
[fullstack4j.dev](https://fullstack4j.dev/). WicketLens keeps its own identity and
links to the related fullstack4j concept without implying an existing combined
offering.

Published at [danielbartl.github.io/wicketlens](https://danielbartl.github.io/wicketlens/).

The page presents working preview features: component exploration, Java/HTML
navigation, editor findings and supported ID edits, optional Spring tools, and
read-only MCP integration, including dependency/profile/FactoryBean context and
native Spring inspection findings. Installation uses a prebuilt preview ZIP from
GitHub Releases, with an optional source-build path. Compatibility and
static-analysis limits are explicit.

## Preview locally

From the repository root, with Python 3:

```bash
python3 site/build.py
python3 -m http.server 8000 --bind 127.0.0.1 --directory build/site
```

Open <http://127.0.0.1:8000/>. Stop the server with Ctrl+C.

There is no package installation or Java/plugin build. The assembler copies only
the public HTML, CSS, JavaScript, logo, and four existing documentation screenshots
into the ignored `build/site/` directory. Original screenshots stay in `docs/images/`
and are not duplicated in source control. Rebuilding replaces `build/site/`.

The example component selector, screenshot tabs (including arrow-key navigation),
theme toggle, and copy button are small browser-only enhancements. Core content,
installation commands, links, and FAQ remain available without JavaScript. There
are no analytics, third-party scripts, external fonts, or backend services.

## Publish an update

The repository already uses GitHub Actions for Pages. Commit and push the update,
then run **Actions → Publish landing page → Run workflow**. Use the deployment's
reported URL. For a new repository, the initial setup is:

1. Commit and push the page and `.github/workflows/pages.yml` to the default branch.
2. In the GitHub repository, choose **Settings → Pages → Source → GitHub Actions**.
3. Under **Actions → Publish landing page**, select **Run workflow**.
4. Use the URL reported by the deployment. With the current repository name and
   no custom domain, the URL is `https://danielbartl.github.io/wicketlens/`.

The workflow runs manually; pushing ordinary plugin changes does not publish the
site. It assembles a static artifact without building or distributing the plugin.
No GitHub Pages setting, custom domain, or DNS change is made by this source change.

Deployment supplies the actual Pages base URL to add canonical and social-preview
metadata. For a local build without a published address, that metadata is omitted.
To assemble for a known address explicitly:

```bash
python3 site/build.py --base-url https://danielbartl.github.io/wicketlens/
```

All asset paths are relative, so the artifact also works under a future
fullstack4j subdirectory. A future domain/path move should update the deployment
base URL and link from fullstack4j; do not add a `CNAME` until a domain is chosen.

## Maintain the content

- Edit `index.html`, `styles.css`, and `main.js` here, then rebuild.
- Keep feature claims aligned with the root README and linked scope guides.
- Update the verified compatibility baseline when plugin verification changes.
- Screenshot descriptions and dimensions are in `index.html` and `main.js`; the
  four copied filenames are listed in `build.py`.
- Check narrow and wide screens, light/dark themes, gallery keyboard navigation,
  the installation copy button, and a server path below the host root before publishing.
