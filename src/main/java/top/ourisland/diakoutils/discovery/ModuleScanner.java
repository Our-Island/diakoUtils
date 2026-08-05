package top.ourisland.diakoutils.discovery;

import net.fabricmc.loader.api.FabricLoader;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.annotation.DiakoModule;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

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
        modContainer.getRootPaths().forEach(rootPath -> collectClassNames(
                rootPath,
                basePackage,
                classNames
        ));

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

        candidates.sort(Comparator
                .comparingInt((Candidate candidate) -> candidate.metadata().order())
                .thenComparing(candidate -> candidate.metadata().id())
                .thenComparing(candidate -> candidate.type().getName())
        );

        return candidates.stream()
                .map(ModuleScanner::instantiate)
                .toList();
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

        try (var paths = Files.walk(packagePath)) {
            paths.filter(Files::isRegularFile)
                    .map(rootPath::relativize)
                    .map(Path::toString)
                    .filter(path -> path.endsWith(".class"))
                    .map(ModuleScanner::toClassName)
                    .forEach(classNames::add);
        } catch (IOException e) {
            throw new ModuleDiscoveryException(
                    "Failed to scan module package " + basePackage + " in " + rootPath,
                    e
            );
        }
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
