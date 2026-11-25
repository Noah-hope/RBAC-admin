"""Package the review page and the existing design atlas for GitHub Pages."""

import argparse
import re
import shutil
from pathlib import Path

SOURCE = Path(__file__).resolve().parent
ATLAS = SOURCE.parent / "docs" / "design"
REPOSITORY = "https://github.com/Noah-hope/RBAC-admin"


def build(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    for filename in ("index.html", "style.css", "app.js"):
        shutil.copyfile(SOURCE / filename, output / filename)

    atlas_output = output / "atlas"
    assets_output = atlas_output / "assets"
    assets_output.mkdir(parents=True, exist_ok=True)
    for asset in (ATLAS / "assets").iterdir():
        if asset.suffix in {".css", ".js", ".svg"} and asset.is_file():
            shutil.copyfile(asset, assets_output / asset.name)

    html = (ATLAS / "index.html").read_text(encoding="utf-8")
    # The atlas uses repository-relative source links when opened inside a checkout.
    html = re.sub(
        r'href="\.\./\.\./(rbac1-(?:structured|ddd)/[^"]+)"',
        rf'href="{REPOSITORY}/blob/main/\1"',
        html,
    )
    (atlas_output / "index.html").write_text(html, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    build(parser.parse_args().output)
