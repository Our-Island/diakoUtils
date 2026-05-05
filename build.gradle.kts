plugins {
    alias(libs.plugins.fabric.loom)
    `maven-publish`
}

version = libs.versions.modVersion.get()
group = libs.versions.mavenGroup.get()

base {
    archivesName.set(libs.versions.archivesName.get())
}

val targetJavaVersion = libs.versions.targetJava.get().toInt()

repositories {
    // Loom adds the essential maven repositories for Minecraft and Fabric automatically.
}

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
}

tasks.processResources {
    inputs.properties(mapOf(
        "version" to project.version,
        "loaderVersion" to libs.versions.loader,
        "minecraftVersion" to libs.versions.minecraft,
    ))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(targetJavaVersion)
    targetCompatibility = JavaVersion.toVersion(targetJavaVersion)
}

tasks.jar {
    inputs.property("projectName", project.name)
    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
