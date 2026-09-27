# Bons and Furious — notices

Copyright 2026 BonsUnleashed and contributors. Our code and modifications are
provided under GNU GPL version 3. The full text is in LICENSE.

This project patches other authors' installed Minecraft mods. Their names and
interfaces identify compatibility targets, and do not imply endorsement. Their
original code, assets and licenses remain their authors' property. Consult
upstream-credits.json for the exact dependency builds used during development.
License documents are retained under resources/META-INF/licenses with their
upstream project names. Those documents describe their respective upstream
works; they do not relicense other targets or their assets.

The runtime adapters and helper implementations contain modifications developed
through September 28, 2026. The modified code is supplied in src/ and the
editable Forge coremod scripts in resources/coremods/. GPL/LGPL-derived adapters
are distributed with their source, build instructions and applicable notices.

For targets with restricted or unclear redistribution permissions, unchanged
instruction bodies are read from the installed class at runtime, with exact
fingerprints and fail-open version guards. Trackwork model geometry and the
two Scorched functions are read from the installed target resources and changed
in memory. Their original resources are not included in this release.

Minecraft and Forge are required external dependencies. No Minecraft or upstream
mod JAR is bundled. No upstream textures, sounds or promotional artwork are
included. Optional target mods must be obtained from their authors.

AI tools assisted development, testing and documentation; the creator reports
substantial implementation work outside those sessions.
