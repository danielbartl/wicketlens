#!/usr/bin/env python3
"""Assemble the landing page with existing documentation images. Standard library only."""

import argparse
from html import escape
from pathlib import Path
import shutil
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", help="Published HTTPS URL, including any repository path")
    args = parser.parse_args()
    base_url = None
    if args.base_url:
        parsed = urlsplit(args.base_url)
        if (parsed.scheme != "https" or not parsed.hostname or parsed.username
                or parsed.password or parsed.query or parsed.fragment):
            parser.error("--base-url must be an HTTPS URL without credentials, query, or fragment")
        base_url = args.base_url.rstrip("/") + "/"

    source = Path(__file__).resolve().parent
    repository = source.parent
    output = repository / "build" / "site"
    if output.exists():
        shutil.rmtree(output)
    (output / "assets" / "screenshots").mkdir(parents=True)

    html = (source / "index.html").read_text(encoding="utf-8")
    metadata = ""
    if base_url:
        url = escape(base_url, quote=True)
        image = escape(base_url + "assets/screenshots/component-hierarchy.png", quote=True)
        metadata = (f'<link rel="canonical" href="{url}">\n'
                    f'  <meta property="og:url" content="{url}">\n'
                    f'  <meta property="og:image" content="{image}">\n'
                    '  <meta property="og:image:alt" content="WicketLens component hierarchy beside Java source in IntelliJ IDEA">')
    (output / "index.html").write_text(
        html.replace("<!-- deployment-metadata -->", metadata), encoding="utf-8")
    for name in ("styles.css", "main.js", "assets/wicketlens.svg"):
        shutil.copyfile(source / name, output / name)
    for image in ("component-hierarchy.png", "unpaired-ids.png",
                  "queue-and-repeaters.png", "spring-bean-completion.png"):
        shutil.copyfile(repository / "docs" / "images" / image,
                        output / "assets" / "screenshots" / image)
    (output / ".nojekyll").touch()
    print(f"Landing page assembled in {output}")


if __name__ == "__main__":
    main()
