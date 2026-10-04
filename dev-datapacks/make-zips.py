#!/usr/bin/env python3
"""Zips each pack folder next to it, with pack.mcmeta at the zip root as Minecraft expects."""
import pathlib
import zipfile

root = pathlib.Path(__file__).parent
for pack in sorted(p for p in root.iterdir() if p.is_dir()):
    with zipfile.ZipFile(root / f"{pack.name}.zip", "w", zipfile.ZIP_DEFLATED) as out:
        for file in sorted(pack.rglob("*")):
            if file.is_file():
                out.write(file, file.relative_to(pack).as_posix())
