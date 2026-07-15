#!/usr/bin/env python3
"""
Delegates to the Java baker (same Voronoi as the game).

  gradlew.bat previewStormWall
  gradlew.bat previewStormWall --args="--size 256 --slices 48 --frames 48"

Writes:
  src/main/resources/assets/eyeofthestorm/textures/storm/wall_morph_atlas.png  (runtime asset)
  tools/output/storm_wall_morph_loop.gif
  tools/output/storm_wall_w_strip.png
"""

from __future__ import annotations

import subprocess
import sys


def main() -> None:
    extra = sys.argv[1:]
    cmd = ["gradlew.bat" if sys.platform.startswith("win") else "./gradlew", "previewStormWall"]
    if extra:
        cmd.append("--args=" + " ".join(extra))
    print("Delegating to Java bake:", " ".join(cmd))
    raise SystemExit(subprocess.call(cmd))


if __name__ == "__main__":
    main()
