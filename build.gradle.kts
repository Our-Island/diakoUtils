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
    mavenCentral()
}

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(libs.night.config.toml)
    compileOnly(libs.luckperms)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "loaderVersion" to libs.versions.loader.get(),
        "minecraftVersion" to libs.versions.minecraft.get(),
    )

    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(props)
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
    from("LICENSE.txt") {
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
