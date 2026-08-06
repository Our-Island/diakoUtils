package top.ourisland.diakoutils.discovery;

import net.fabricmc.loader.api.FabricLoader;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.annotation.DiakoModule;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

public final class ModuleScanner {

    private static final Pattern MODULE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    private ModuleScanner() {
    }

    public static List<IModule> discover(String modId, String basePackage) {
        var modContainer = FabricLoader.getInstance()
                .getModContainer(modId)
                .orElseThrow(() -> new ModuleDiscoveryException(
                        "Unable to locate Fabric mod container: " + modId
                ));

        var classNames = new TreeSet<String>();

        // Packaged mods expose their JAR filesystem roots here. In a Loom
        // development run, however, the mod root may contain only resources,
        // while compiled classes live in a separate output directory.
        modContainer.getRootPaths().forEach(rootPath -> collectFromPathEntry(
                rootPath,
                basePackage,
                classNames
        ));

        collectFromClassLoader(basePackage, classNames);
        collectFromCodeSource(basePackage, classNames);
        collectFromRuntimeClasspath(basePackage, classNames);

        var candidates = new ArrayList<Candidate>();
        var moduleIds = new HashMap<String, Class<?>>();
        classNames.stream()
                .filter(ModuleScanner::isCandidateClassName)
                .map(ModuleScanner::loadClass)
                .forEach(type -> {
                    var metadata = type.getAnnotation(DiakoModule.class);
                    if (metadata == null) {
                        return;
                    }

                    var candidate = validateCandidate(type, metadata);
                    var duplicate = moduleIds.putIfAbsent(metadata.id(), type);
                    if (duplicate != null) {
                        throw new ModuleDiscoveryException(
                                "Duplicate module id '" + metadata.id() + "' on "
                                        + duplicate.getName() + " and " + type.getName()
                        );
                    }

                    candidates.add(candidate);
                });

        if (candidates.isEmpty()) {
            throw new ModuleDiscoveryException(
                    "No @DiakoModule implementations were found under package "
                            + basePackage + ". Checked the mod roots, class loader, "
                            + "code source, and runtime classpath."
            );
        }

        candidates.sort(Comparator
                .comparingInt((Candidate candidate) -> candidate.metadata().order())
                .thenComparing(candidate -> candidate.metadata().id())
                .thenComparing(candidate -> candidate.type().getName())
        );

        return candidates.stream()
                .map(ModuleScanner::instantiate)
                .toList();
    }

    private static void collectFromClassLoader(
            String basePackage,
            SortedSet<String> classNames
    ) {
        var packageResource = basePackage.replace('.', '/');
        var classLoader = ModuleScanner.class.getClassLoader();

        try {
            var resources = classLoader.getResources(packageResource);
            while (resources.hasMoreElements()) {
                collectFromResource(
                        resources.nextElement(),
                        packageResource,
                        basePackage,
                        classNames
                );
            }
        } catch (IOException e) {
            throw new ModuleDiscoveryException(
                    "Failed to scan class-loader resources for " + basePackage,
                    e
            );
        }
    }

    private static void collectFromResource(
            URL resource,
            String packageResource,
            String basePackage,
            SortedSet<String> classNames
    ) {
        try {
            var connection = resource.openConnection();
            if (connection instanceof JarURLConnection jarConnection) {
                jarConnection.setUseCaches(false);
                try (var jarFile = jarConnection.getJarFile()) {
                    collectFromJar(jarFile, packageResource, classNames);
                }

                return;
            }

            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                collectFromPackageDirectory(
                        Path.of(resource.toURI()),
                        basePackage,
                        classNames
                );
            }
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new ModuleDiscoveryException(
                    "Failed to scan module resource " + resource,
                    e
            );
        }
    }

    private static void collectFromCodeSource(
            String basePackage,
            SortedSet<String> classNames
    ) {
        var codeSource = ModuleScanner.class
                .getProtectionDomain()
                .getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return;
        }

        try {
            collectFromPathEntry(
                    Path.of(codeSource.getLocation().toURI()),
                    basePackage,
                    classNames
            );
        } catch (URISyntaxException | IllegalArgumentException _) {
            // The runtime classpath fallback below still covers development runs
            // whose class loader does not expose a regular code-source URI.
        }
    }

    private static void collectFromRuntimeClasspath(
            String basePackage,
            SortedSet<String> classNames
    ) {
        var classPath = System.getProperty("java.class.path", "");
        if (classPath.isBlank()) {
            return;
        }

        Arrays.stream(classPath.split(Pattern.quote(File.pathSeparator)))
                .filter(entry -> !entry.isBlank())
                .forEachOrdered(entry -> {
                    try {
                        var path = Path.of(entry);
                        if (Files.isDirectory(path)) {
                            collectClassNames(path, basePackage, classNames);
                        }
                    } catch (InvalidPathException _) {
                        // Ignore launcher-specific classpath entries that are not paths.
                    }
                });
    }

    private static void collectFromPathEntry(
            Path entry,
            String basePackage,
            SortedSet<String> classNames
    ) {
        if (Files.isDirectory(entry)) {
            collectClassNames(entry, basePackage, classNames);
            return;
        }

        if (!Files.isRegularFile(entry)
                || !entry.getFileName().toString().endsWith(".jar")
        ) {
            return;
        }

        try (var jarFile = new JarFile(entry.toFile())) {
            collectFromJar(
                    jarFile,
                    basePackage.replace('.', '/'),
                    classNames
            );
        } catch (IOException e) {
            throw new ModuleDiscoveryException(
                    "Failed to scan module classes in " + entry,
                    e
            );
        }
    }

    private static void collectClassNames(
            Path rootPath,
            String basePackage,
            SortedSet<String> classNames
    ) {
        var packagePath = rootPath.resolve(basePackage.replace('.', '/'));
        if (!Files.isDirectory(packagePath)) {
            return;
        }

        collectFromPackageDirectory(packagePath, basePackage, classNames);
    }

    private static void collectFromPackageDirectory(
            Path packagePath,
            String basePackage,
            SortedSet<String> classNames
    ) {
        try (var paths = Files.walk(packagePath)) {
            paths.filter(Files::isRegularFile)
                    .map(packagePath::relativize)
                    .map(Path::toString)
                    .filter(path -> path.endsWith(".class"))
                    .map(path -> toClassName(basePackage, path))
                    .forEach(classNames::add);
        } catch (IOException e) {
            throw new ModuleDiscoveryException(
                    "Failed to scan module package " + basePackage
                            + " in " + packagePath,
                    e
            );
        }
    }

    private static void collectFromJar(
            JarFile jarFile,
            String packageResource,
            SortedSet<String> classNames
    ) {
        var prefix = packageResource.endsWith("/")
                ? packageResource
                : packageResource + "/";

        jarFile.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.startsWith(prefix))
                .filter(name -> name.endsWith(".class"))
                .map(ModuleScanner::toClassName)
                .forEach(classNames::add);
    }

    private static boolean isCandidateClassName(String className) {
        return !className.contains(".mixin.")
                && !className.contains("$")
                && !className.endsWith("package-info")
                && !className.endsWith("module-info");
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(
                    className,
                    false,
                    ModuleScanner.class.getClassLoader()
            );
        } catch (ClassNotFoundException | LinkageError e) {
            throw new ModuleDiscoveryException(
                    "Failed to load module candidate class: " + className,
                    e
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static Candidate validateCandidate(
            Class<?> type,
            DiakoModule metadata
    ) {
        if (!IModule.class.isAssignableFrom(type)) {
            throw new ModuleDiscoveryException(
                    "@DiakoModule type must implement IModule: " + type.getName()
            );
        }

        var modifiers = type.getModifiers();
        if (type.isInterface() || Modifier.isAbstract(modifiers)) {
            throw new ModuleDiscoveryException(
                    "@DiakoModule type must be concrete: " + type.getName()
            );
        }

        if (!Modifier.isPublic(modifiers)) {
            throw new ModuleDiscoveryException(
                    "@DiakoModule type must be public: " + type.getName()
            );
        }

        if (!MODULE_ID.matcher(metadata.id()).matches()) {
            throw new ModuleDiscoveryException(
                    "Invalid module id '%s' on %s; expected %s".formatted(
                            metadata.id(),
                            type.getName(),
                            MODULE_ID.pattern()
                    )
            );
        }

        if (metadata.displayName().isBlank()) {
            throw new ModuleDiscoveryException(
                    "Module displayName must not be blank: " + type.getName()
            );
        }

        try {
            var constructor = type.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new ModuleDiscoveryException(
                        "@DiakoModule type must have a public no-arg constructor: "
                                + type.getName()
                );
            }
        } catch (NoSuchMethodException e) {
            throw new ModuleDiscoveryException(
                    "@DiakoModule type must have a public no-arg constructor: "
                            + type.getName(),
                    e
            );
        }

        return new Candidate((Class<? extends IModule>) type, metadata);
    }

    private static IModule instantiate(Candidate candidate) {
        try {
            return candidate.type().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ModuleDiscoveryException(
                    "Failed to instantiate module: " + candidate.type().getName(),
                    e
            );
        }
    }

    private static String toClassName(
            String basePackage,
            String relativeClassFilePath
    ) {
        var relativeName = relativeClassFilePath
                .substring(0, relativeClassFilePath.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');

        return relativeName.isEmpty()
                ? basePackage
                : basePackage + "." + relativeName;
    }

    private static String toClassName(String classFilePath) {
        return classFilePath
                .substring(0, classFilePath.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
    }

    private record Candidate(
            Class<? extends IModule> type,
            DiakoModule metadata
    ) {

    }

}
