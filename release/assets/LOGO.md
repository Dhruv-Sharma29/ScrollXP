# ScrollXP logo

The developer supplied the palm-tree floating-island design on 5 October 2026. `scrollxp-logo-source.png` preserves the original screenshot. `scrollxp-logo-mark.png` is the retained transparent production image prepared with the built-in imagegen tool. The background extraction is a derived asset; the original screenshot is retained for reference.

Final retained edit prompt:

> Use case: background-extraction. Edit target: supplied ScrollXP logo. Remove only the cream background and deliver the entire palm tree, floating mint island with dark teal underside, small foliage and coral four-point sparkle as a clean transparent PNG. Preserve the supplied shapes, exact colors, proportions, shading and composition faithfully; do not redesign, add text or add objects. Center complete mark on square transparent canvas with a small even margin; no clipping, shadows beyond existing art, border, checkerboard or background.

`scripts/prepare_release_assets.py` packages the mark for Android, Play Store and the login page, preserves its alpha, and generates the themed layer from the retained mark's silhouette. The cream background is added only to the launcher and Play icon. Adaptive artwork fits inside the safe area. `scrollxp-icon.svg` embeds the final Play PNG and is not a vector master. `app/src/main/res/raw/scrollxp_brand_keep.xml` retains the Compose logo bitmap in optimized release builds.

Generated store assets: `play-icon-512.png` and `feature-graphic-1024x500.png`. Game-world artwork is independent of the brand mark.
