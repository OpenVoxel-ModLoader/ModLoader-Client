plugins {
    java
}

group = "com.happyuky7.openvoxelmoding"
val loaderVersion = "1.1.0-pre1"
val gameVersion = "0.0.8"
version = loaderVersion

// Compile-only dependency on the game JAR. Set OPENVOXEL_GAME_JAR or place the game next to
// this repo (../../Game/versions/...) or inside the launcher folder (../../../Game/...).
val gameJar: File = (System.getenv("OPENVOXEL_GAME_JAR")?.let { file(it) }
    ?: listOf("../../../Game/versions/0.0.8-Debug/openvoxel-0.0.8-Debug.jar", "../../../Game/versions/0.0.8-SNAPSHOT/openvoxel-0.0.8-SNAPSHOT.jar",
              "../../Game/versions/0.0.8-Debug/openvoxel-0.0.8-Debug.jar", "../Game/versions/0.0.8-Debug/openvoxel-0.0.8-Debug.jar", "game/openvoxel.jar")
        .map { projectDir.resolve(it) }.firstOrNull { it.exists() })
    ?: error("Game JAR not found. Set OPENVOXEL_GAME_JAR=<path to openvoxel-0.0.8-Debug.jar> (download it once with the official launcher).")

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files(gameJar))
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
}

// Any JDK 11+ can build it: bytecode is targeted at Java 11 (the game ships a JRE 11).
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(11)
}

// Fat agent jar: ASM is merged in so the game JVM needs no extra classpath.
tasks.jar {
    archiveFileName.set("ModLoader-v$loaderVersion-vgame-$gameVersion-client.jar")
    destinationDirectory.set(projectDir.resolve("dist"))
    manifest {
        attributes(
            "Premain-Class" to "com.happyuky7.openvoxelmoding.loader.Agent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Title" to "OpenVoxel ModLoader (client)",
            "Implementation-Version" to loaderVersion,
            "Game-Version" to gameVersion
        )
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
