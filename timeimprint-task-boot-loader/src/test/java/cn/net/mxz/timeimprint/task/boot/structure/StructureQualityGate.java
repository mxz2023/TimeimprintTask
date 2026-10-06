package cn.net.mxz.timeimprint.task.boot.structure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * P02 T05 结构门禁扫描器：对仓库根目录执行 Q01—Q06 检查，返回可断言的违规列表。
 *
 * <p>故意违规样例通过 {@link #evaluateSynthetic} 注入虚拟树，不污染生产源码。
 */
public final class StructureQualityGate {

    public enum Check {
        Q01_PACKAGE_SHAPE,
        Q02_PATH_PACKAGE_XML,
        Q03_TEST_MIRROR,
        Q04_OWNER_MAPPING,
        Q05_OWNER_ASSERTION,
        Q06_SPLIT_FEATURE_TESTS
    }

    public record Violation(Check check, String detail) {}

    public record Report(List<Violation> violations) {
        public boolean passed() {
            return violations.isEmpty();
        }

        public List<Violation> of(Check check) {
            return violations.stream().filter(v -> v.check() == check).toList();
        }
    }

    private static final Pattern PACKAGE_DECL =
            Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE_KIND =
            Pattern.compile("\\b(public\\s+)?(final\\s+)?(abstract\\s+)?(class|interface|record|enum)\\s+(\\w+)\\b");
    private static final Pattern ASSERT_SIGNAL = Pattern.compile(
            "\\b(assert[A-Z]\\w*|assertThat|fail\\s*\\(|\\.check\\s*\\(|ArchRule|Assertions\\.)");
    private static final Pattern XML_NAMESPACE =
            Pattern.compile("namespace\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern XML_TYPE =
            Pattern.compile("(?:type|resultType|parameterType)\\s*=\\s*\"([^\"]+)\"");

    static final List<String> MODULES = List.of(
            "timeimprint-task-common",
            "timeimprint-task-domain",
            "timeimprint-task-service-kernel",
            "timeimprint-task-service-extension-api",
            "timeimprint-task-service-application",
            "timeimprint-task-service-runtime",
            "timeimprint-task-service-storage-mysql",
            "timeimprint-task-service-capability-calendar",
            "timeimprint-task-adapter",
            "timeimprint-task-service-capability-notification",
            "timeimprint-task-service-scenario-basic",
            "timeimprint-task-gateway",
            "timeimprint-task-web",
            "timeimprint-task-boot-loader");

    private static final Map<String, ModuleRule> RULES = buildRules();

    private static final List<String> FEATURE_TESTS = List.of(
            "timeimprint-task-gateway/src/test/java/cn/net/mxz/timeimprint/task/gateway/shared/gateway/TaskGatewayTest.java",
            "timeimprint-task-service-application/src/test/java/cn/net/mxz/timeimprint/task/service/application/definition/service/DefinitionCommandServiceTest.java",
            "timeimprint-task-service-storage-mysql/src/test/java/cn/net/mxz/timeimprint/task/service/storage/mysql/transition/committer/TransitionPlanCommitterImplTest.java",
            "timeimprint-task-service-runtime/src/test/java/cn/net/mxz/timeimprint/task/service/runtime/action/worker/ActionWorkerTest.java",
            "timeimprint-task-service-runtime/src/test/java/cn/net/mxz/timeimprint/task/service/runtime/shared/configuration/JsonGoldenContractTest.java");

    private StructureQualityGate() {}

    public static Report evaluate(Path repoRoot) throws IOException {
        List<Violation> out = new ArrayList<>();
        Map<String, Set<String>> prodPackagesByModule = new HashMap<>();
        Map<String, ProdType> prodTypes = new LinkedHashMap<>();

        for (String module : MODULES) {
            Path mainJava = repoRoot.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(mainJava)) {
                out.add(new Violation(Check.Q01_PACKAGE_SHAPE, "missing main/java for " + module));
                continue;
            }
            ModuleRule rule = RULES.get(module);
            Set<String> pkgs = prodPackagesByModule.computeIfAbsent(module, k -> new HashSet<>());
            try (Stream<Path> walk = Files.walk(mainJava)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .sorted()
                        .toList();
                for (Path file : files) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String pkg = packageOf(text);
                    String rel = repoRoot.relativize(file).toString().replace('\\', '/');
                    if (pkg == null) {
                        out.add(new Violation(Check.Q02_PATH_PACKAGE_XML, "missing package: " + rel));
                        continue;
                    }
                    Path expected = mainJava.resolve(Path.of("", pkg.split("\\.")));
                    Path actualDir = file.getParent();
                    if (!actualDir.equals(expected)) {
                        out.add(new Violation(
                                Check.Q02_PATH_PACKAGE_XML,
                                "path/package mismatch: " + rel + " declares " + pkg));
                    }
                    if (file.getFileName().toString().equals("package-info.java")) {
                        pkgs.add(pkg);
                        continue;
                    }
                    String simple = file.getFileName().toString().replace(".java", "");
                    if (!pkg.startsWith(rule.rootPackage() + ".") && !pkg.equals(rule.rootPackage())) {
                        out.add(new Violation(
                                Check.Q01_PACKAGE_SHAPE,
                                "outside module root: " + rel + " pkg=" + pkg));
                    }
                    for (String forbidden : List.of(".impl.", ".util.", ".misc.", ".impl", ".util", ".misc")) {
                        if (pkg.contains(forbidden) || pkg.endsWith(".impl") || pkg.endsWith(".util") || pkg.endsWith(".misc")) {
                            out.add(new Violation(
                                    Check.Q01_PACKAGE_SHAPE, "forbidden package segment in " + rel + " (" + pkg + ")"));
                        }
                    }
                    String shapeError = rule.validateShape(pkg, simple);
                    if (shapeError != null) {
                        out.add(new Violation(Check.Q01_PACKAGE_SHAPE, shapeError + " @ " + rel));
                    }
                    pkgs.add(pkg);
                    OwnerKind kind = classify(simple, text);
                    prodTypes.put(module + "|" + pkg + "|" + simple, new ProdType(module, pkg, simple, kind, rel));
                }
            }
            // empty directories under main/java
            try (Stream<Path> walk = Files.walk(mainJava)) {
                walk.filter(Files::isDirectory)
                        .filter(d -> !d.equals(mainJava))
                        .sorted(Comparator.reverseOrder())
                        .forEach(d -> {
                            try (Stream<Path> children = Files.list(d)) {
                                if (children.findAny().isEmpty()) {
                                    out.add(new Violation(
                                            Check.Q02_PATH_PACKAGE_XML,
                                            "empty directory: " + repoRoot.relativize(d)));
                                }
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
            }
        }

        // MyBatis XML namespace/type sync
        for (String module : MODULES) {
            Path resources = repoRoot.resolve(module).resolve("src/main/resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(resources)) {
                for (Path xml : walk.filter(p -> p.getFileName().toString().endsWith("Mapper.xml")).toList()) {
                    String body = Files.readString(xml, StandardCharsets.UTF_8);
                    Matcher ns = XML_NAMESPACE.matcher(body);
                    if (ns.find()) {
                        String fqcn = ns.group(1);
                        if (!classFileExists(repoRoot, fqcn)) {
                            out.add(new Violation(
                                    Check.Q02_PATH_PACKAGE_XML,
                                    "XML namespace missing class " + fqcn + " @ " + repoRoot.relativize(xml)));
                        }
                    }
                    Matcher types = XML_TYPE.matcher(body);
                    while (types.find()) {
                        String fqcn = types.group(1);
                        if (fqcn.contains(".") && fqcn.startsWith("cn.net.mxz.") && !classFileExists(repoRoot, fqcn)) {
                            out.add(new Violation(
                                    Check.Q02_PATH_PACKAGE_XML,
                                    "XML type missing class " + fqcn + " @ " + repoRoot.relativize(xml)));
                        }
                    }
                }
            }
        }

        // Owner mapping + assertion + test mirror
        Map<String, List<Path>> testsByKey = new HashMap<>();
        for (String module : MODULES) {
            Path testJava = repoRoot.resolve(module).resolve("src/test/java");
            if (!Files.isDirectory(testJava)) {
                continue;
            }
            Set<String> prodPkgs = prodPackagesByModule.getOrDefault(module, Set.of());
            try (Stream<Path> walk = Files.walk(testJava)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String pkg = packageOf(text);
                    String rel = repoRoot.relativize(file).toString().replace('\\', '/');
                    String simple = file.getFileName().toString().replace(".java", "");
                    if (pkg == null) {
                        out.add(new Violation(Check.Q03_TEST_MIRROR, "test missing package: " + rel));
                        continue;
                    }
                    Path expected = testJava.resolve(Path.of("", pkg.split("\\.")));
                    if (!file.getParent().equals(expected)) {
                        out.add(new Violation(Check.Q03_TEST_MIRROR, "test path/package mismatch: " + rel));
                    }
                    if (!isBootAuxiliary(module, pkg) && !prodPkgs.contains(pkg)) {
                        out.add(new Violation(
                                Check.Q03_TEST_MIRROR,
                                "test package has no production peer: " + rel + " (" + pkg + ")"));
                    }
                    if (!allowedTestName(simple)) {
                        out.add(new Violation(Check.Q03_TEST_MIRROR, "unsupported test suffix: " + rel));
                    }
                    for (ProdType prod : prodTypes.values()) {
                        if (!prod.module().equals(module)) {
                            continue;
                        }
                        String expectedOwner = ownerName(prod.simple(), prod.kind());
                        if (simple.equals(expectedOwner) && pkg.equals(prod.pkg())) {
                            testsByKey.computeIfAbsent(prod.key(), k -> new ArrayList<>()).add(file);
                        }
                    }
                }
            }
        }

        for (ProdType prod : prodTypes.values()) {
            List<Path> owners = testsByKey.getOrDefault(prod.key(), List.of());
            if (owners.isEmpty()) {
                out.add(new Violation(
                        Check.Q04_OWNER_MAPPING,
                        "missing owner " + ownerName(prod.simple(), prod.kind()) + " for " + prod.rel()));
            } else if (owners.size() > 1) {
                out.add(new Violation(
                        Check.Q04_OWNER_MAPPING,
                        "duplicate owners for " + prod.rel() + ": " + owners));
            } else {
                String body = Files.readString(owners.getFirst(), StandardCharsets.UTF_8);
                String stripped = stripComments(body);
                if (!ASSERT_SIGNAL.matcher(stripped).find()) {
                    out.add(new Violation(
                            Check.Q05_OWNER_ASSERTION,
                            "owner lacks observable assertion: " + repoRoot.relativize(owners.getFirst())));
                }
            }
        }

        for (String feature : FEATURE_TESTS) {
            if (!Files.isRegularFile(repoRoot.resolve(feature))) {
                out.add(new Violation(Check.Q06_SPLIT_FEATURE_TESTS, "missing feature test: " + feature));
            }
        }

        return new Report(List.copyOf(out));
    }

    /**
     * 负例入口：在临时目录写入给定文件内容后执行同一套规则，用于证明门禁会对违规失败。
     */
    public static Report evaluateSynthetic(Path tempRepoRoot, Map<String, String> relativePathToSource)
            throws IOException {
        for (Map.Entry<String, String> e : relativePathToSource.entrySet()) {
            Path target = tempRepoRoot.resolve(e.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
        }
        // seed minimal valid modules so MODULES loop does not explode on missing dirs
        for (String module : MODULES) {
            Path main = tempRepoRoot.resolve(module).resolve("src/main/java");
            Files.createDirectories(main);
        }
        return evaluate(tempRepoRoot);
    }

    private static boolean isBootAuxiliary(String module, String pkg) {
        if (!module.equals("timeimprint-task-boot-loader")) {
            return false;
        }
        return pkg.equals("cn.net.mxz.timeimprint.task.boot")
                || pkg.startsWith("cn.net.mxz.timeimprint.task.boot.it")
                || pkg.startsWith("cn.net.mxz.timeimprint.task.boot.fixture")
                || pkg.startsWith("cn.net.mxz.timeimprint.task.boot.structure")
                || pkg.startsWith("cn.net.mxz.timeimprint.task.boot.bootstrap")
                || pkg.startsWith("cn.net.mxz.timeimprint.task.boot.health");
    }

    private static boolean allowedTestName(String simple) {
        // 02 规定的所有者/架构后缀；boot-loader 内 fixture、双进程 Main、结构扫描器为辅助类型。
        return simple.endsWith("Test")
                || simple.endsWith("MysqlIT")
                || simple.endsWith("IT")
                || simple.endsWith("Fixture")
                || simple.endsWith("Main")
                || simple.endsWith("Materializer")
                || simple.endsWith("Gate");
    }

    private static String ownerName(String simple, OwnerKind kind) {
        return switch (kind) {
            case INTERFACE -> simple + "ContractTest";
            case MAPPER -> simple + "MysqlIT";
            case SPRING_CONFIG -> simple + "ContextTest";
            default -> simple + "Test";
        };
    }

    private static OwnerKind classify(String simple, String text) {
        Matcher m = TYPE_KIND.matcher(text);
        boolean isInterface = false;
        while (m.find()) {
            if (m.group(5).equals(simple)) {
                isInterface = "interface".equals(m.group(4));
                break;
            }
        }
        // 仅 MyBatis Mapper 接口走 XxxMysqlIT；同名工具类（如 RowMapper）仍用 XxxTest。
        if (simple.endsWith("Mapper") && isInterface) {
            return OwnerKind.MAPPER;
        }
        if (text.contains("@Configuration") || text.contains("@MapperScan")) {
            return OwnerKind.SPRING_CONFIG;
        }
        if (isInterface) {
            return OwnerKind.INTERFACE;
        }
        return OwnerKind.TYPE;
    }

    private static boolean classFileExists(Path repoRoot, String fqcn) {
        String rel = fqcn.replace('.', '/') + ".java";
        for (String module : MODULES) {
            if (Files.isRegularFile(repoRoot.resolve(module).resolve("src/main/java").resolve(rel))) {
                return true;
            }
        }
        return false;
    }

    private static String packageOf(String text) {
        Matcher m = PACKAGE_DECL.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String stripComments(String text) {
        String noBlock = text.replaceAll("/\\*.*?\\*/", " ");
        return noBlock.replaceAll("//.*?$", " ");
    }

    private enum OwnerKind {
        TYPE,
        INTERFACE,
        MAPPER,
        SPRING_CONFIG
    }

    private record ProdType(String module, String pkg, String simple, OwnerKind kind, String rel) {
        String key() {
            return module + "|" + pkg + "|" + simple;
        }
    }

    private record ModuleRule(String rootPackage, Set<String> biz, Set<String> tech, boolean techOnly) {
        String validateShape(String pkg, String simple) {
            if (pkg.equals(rootPackage)) {
                return "type not allowed at module root package: " + simple;
            }
            if (!pkg.startsWith(rootPackage + ".")) {
                return "package not under " + rootPackage;
            }
            String rest = pkg.substring(rootPackage.length() + 1);
            String[] parts = rest.split("\\.");
            if (techOnly) {
                if (parts.length < 1 || !tech.contains(parts[0])) {
                    return "tech-only module requires first segment in " + tech + " but was " + rest;
                }
                return null;
            }
            if (parts.length < 2) {
                return "require root.biz.tech, got " + pkg;
            }
            if (!biz.contains(parts[0])) {
                return "unknown biz '" + parts[0] + "', allowed " + biz;
            }
            if (!tech.contains(parts[1])) {
                return "unknown tech '" + parts[1] + "' under biz " + parts[0] + ", allowed " + tech;
            }
            return null;
        }
    }

    private static Map<String, ModuleRule> buildRules() {
        Map<String, ModuleRule> m = new LinkedHashMap<>();
        m.put(
                "timeimprint-task-common",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.common",
                        Set.of(),
                        Set.of("time", "hashing"),
                        true));
        m.put(
                "timeimprint-task-boot-loader",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.boot",
                        Set.of(),
                        Set.of("bootstrap", "configuration", "health", "lifecycle"),
                        true));
        m.put(
                "timeimprint-task-domain",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.domain",
                        Set.of("definition", "instance", "signal", "inbox", "diagnostic", "shared"),
                        Set.of("request", "view", "response"),
                        false));
        m.put(
                "timeimprint-task-service-kernel",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.kernel",
                        Set.of("definition", "instance", "participant", "transition", "shared"),
                        Set.of("model", "identity", "state", "revision"),
                        false));
        m.put(
                "timeimprint-task-service-extension-api",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.extension",
                        Set.of("scenario", "trigger", "command", "action", "policy", "materialization", "shared"),
                        Set.of("spi", "context", "registry", "result"),
                        false));
        m.put(
                "timeimprint-task-service-application",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.application",
                        Set.of(
                                "definition",
                                "instance",
                                "signal",
                                "action",
                                "inbox",
                                "transition",
                                "extension",
                                "access",
                                "shared"),
                        Set.of(
                                "service",
                                "port",
                                "model",
                                "validation",
                                "registry",
                                "limit",
                                "paging",
                                "transaction"),
                        false));
        m.put(
                "timeimprint-task-service-runtime",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.runtime",
                        Set.of("trigger", "signal", "action", "shared"),
                        Set.of("worker", "recovery", "configuration", "lifecycle"),
                        false));
        m.put(
                "timeimprint-task-service-storage-mysql",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.storage.mysql",
                        Set.of(
                                "definition",
                                "instance",
                                "participant",
                                "trigger",
                                "signal",
                                "transition",
                                "action",
                                "inbox",
                                "command",
                                "audit",
                                "shared"),
                        Set.of(
                                "adapter",
                                "mapper",
                                "row",
                                "committer",
                                "configuration",
                                "mapping",
                                "time",
                                "transaction"),
                        false));
        m.put(
                "timeimprint-task-service-capability-calendar",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.capability.calendar",
                        Set.of("schedule"),
                        Set.of("configuration", "calculation", "provider"),
                        false));
        m.put(
                "timeimprint-task-service-capability-notification",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.capability.notification",
                        Set.of("notification", "inapp", "feishu"),
                        Set.of("handler", "adapter", "mapper", "row", "port", "configuration"),
                        false));
        m.put(
                "timeimprint-task-adapter",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.adapter",
                        Set.of("feishu"),
                        Set.of("client", "auth", "callback", "configuration"),
                        false));
        m.put(
                "timeimprint-task-service-scenario-basic",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.service.scenario.basic",
                        Set.of("reminder", "recurringtodo"),
                        Set.of("extension", "command", "projection"),
                        false));
        m.put(
                "timeimprint-task-gateway",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.gateway",
                        Set.of("definition", "instance", "signal", "inbox", "diagnostic", "callback", "shared"),
                        Set.of("gateway", "mapper"),
                        false));
        m.put(
                "timeimprint-task-web",
                new ModuleRule(
                        "cn.net.mxz.timeimprint.task.web",
                        Set.of("definition", "instance", "signal", "inbox", "diagnostic", "callback", "shared"),
                        Set.of("controller", "filter", "error", "configuration"),
                        false));
        return Map.copyOf(m);
    }

    public static Path findRepoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null) {
            if (Files.exists(p.resolve("timeimprint-task-common")) && Files.exists(p.resolve("pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("cannot locate repo root");
    }

    public static String summarize(Report report) {
        return report.violations().stream()
                .collect(Collectors.groupingBy(Violation::check, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
