# OpenVoxel ModLoader — client

[English](#english) · [Español](#español)

Part of [github.com/OpenVoxel-ModLoader](https://github.com/OpenVoxel-ModLoader):
**ModLoader-Client** (this repo) · [ModLoader-Server](https://github.com/OpenVoxel-ModLoader/ModLoader-Server) ·
[docs](https://github.com/OpenVoxel-ModLoader/openvoxel-modloader.github.io) · [ModLoader-ModsExamples](https://github.com/OpenVoxel-ModLoader/ModLoader-ModsExamples) · [ModLoader-Server-Example](https://github.com/OpenVoxel-ModLoader/ModLoader-Server-Example)

## English

A mod loader for the OpenVoxel client that never modifies the game: it runs as a Java agent
injected into the official launcher and wraps engine methods in memory (ASM). Release jar:
`ModLoader-v1.1.0-pre2-vgame-0.0.8-client.jar` (loader 1.1.0-pre2 for game 0.0.8 / `0.0.8-Debug`).

### Install & play

1. Download `ModLoader-v1.1.0-pre2-vgame-0.0.8-client.jar` from Releases into `dist/` (or build it).
2. Put this folder next to `OpenVoxelLauncher.exe` (or set `OPENVOXEL_HOME` to the launcher folder).
3. Run `launch.bat` (Windows), `launch.ps1` (PowerShell) or `./launch.sh` (Linux).
4. **In the launcher select the version `0.0.8-Debug` and press PLAY.**
5. Mods go into `instance/mods`, settings/skin/cape into `instance/config`, resource packs into
   `instance/resourcepacks`, sounds/music into `instance/sounds` and `instance/music`.

### Build

```
./gradlew jar          # Gradle 9.1 wrapper, any JDK 11+, output in dist/
build.bat / build.sh   # no Gradle: javac + jar
```

The game JAR is needed at compile time (`OPENVOXEL_GAME_JAR`, or it is found in the launcher folder).

### What it adds

Minecraft-style menus and HUD, Creative / Survival (hunger, armor, tools, mining times, drops,
crafting 2x2 and crafting table, chests, bed), Steve/Alex two-layer skins + capes/elytra, F3,
chat colors/history/Tab completion, rebindable controls, resource packs, languages (en/es +
mod `assets/<id>/lang`), sound packs and music folders, fog/tone shader, client commands
(`/chelp`). Example mods in [ModLoader-ModsExamples](https://github.com/OpenVoxel-ModLoader/ModLoader-ModsExamples); API in the [docs](https://openvoxel-modloader.github.io/).

## Español

Un cargador de mods para el cliente de OpenVoxel que nunca modifica el juego: se ejecuta como
agente Java inyectado en el launcher oficial y envuelve métodos del motor en memoria (ASM).
Jar de release: `ModLoader-v1.1.0-pre2-vgame-0.0.8-client.jar` (loader 1.1.0-pre2 para el juego 0.0.8 / `0.0.8-Debug`).

### Instalar y jugar

1. Descarga `ModLoader-v1.1.0-pre2-vgame-0.0.8-client.jar` de Releases a `dist/` (o compílalo).
2. Pon esta carpeta junto a `OpenVoxelLauncher.exe` (o define `OPENVOXEL_HOME` con la carpeta del launcher).
3. Ejecuta `launch.bat` (Windows), `launch.ps1` (PowerShell) o `./launch.sh` (Linux).
4. **En el launcher selecciona la versión `0.0.8-Debug` y pulsa PLAY.**
5. Los mods van en `instance/mods`; ajustes/skin/capa en `instance/config`; paquetes de texturas en
   `instance/resourcepacks`; sonidos y música en `instance/sounds` y `instance/music`.

### Compilar

```
./gradlew jar          # wrapper de Gradle 9.1, cualquier JDK 11+, salida en dist/
build.bat / build.sh   # sin Gradle: javac + jar
```

### Qué añade

Menús y HUD estilo Minecraft, Creativo / Supervivencia (hambre, armadura, herramientas, tiempos de
minado, drops, crafteo 2x2 y mesa de crafteo, cofres, cama), skins Steve/Alex de dos capas + capas y
élitros, F3, colores/historial/Tab en el chat, controles remapeables, paquetes de texturas, idiomas
(en/es + `assets/<id>/lang` de los mods), carpetas de sonidos y música, shader de niebla, comandos
de cliente (`/chelp`). Mods de ejemplo en [ModLoader-ModsExamples](https://github.com/OpenVoxel-ModLoader/ModLoader-ModsExamples); API en la [documentación](https://openvoxel-modloader.github.io/).
