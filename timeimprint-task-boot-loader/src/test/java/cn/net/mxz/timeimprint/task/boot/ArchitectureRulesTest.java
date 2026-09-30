package cn.net.mxz.timeimprint.task.boot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Q07 架构门禁：kernel 纯 Java；禁止 Mxz 类名前缀；禁止包名使用 impl/util/misc。
 */
class ArchitectureRulesTest {

    @Test
    void kernelMustRemainPureJava() {
        JavaClasses classes = productionClasses("cn.net.mxz.timeimprint.task.service.kernel");
        noClasses()
                .that()
                .resideInAPackage("cn.net.mxz.timeimprint.task.service.kernel..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.mybatis..",
                        "jakarta.servlet..",
                        "org.apache.ibatis..")
                .check(classes);
    }

    @Test
    void productionTypesMustNotUseMxzPrefix() {
        JavaClasses classes = productionClasses("cn.net.mxz.timeimprint.task");
        Set<String> offenders = classes.stream()
                .map(c -> c.getSimpleName())
                .filter(n -> n.startsWith("Mxz"))
                .collect(Collectors.toSet());
        assertTrue(offenders.isEmpty(), () -> "Mxz-prefixed types: " + offenders);
    }

    @Test
    void productionMustNotResideInImplUtilMiscPackages() {
        JavaClasses classes = productionClasses("cn.net.mxz.timeimprint.task");
        noClasses()
                .that()
                .resideInAPackage("cn.net.mxz.timeimprint.task..")
                .should()
                .resideInAnyPackage("..impl..", "..util..", "..misc..")
                .check(classes);
    }

    @Test
    void extensionApiMustNotDependOnWebOrStorage() {
        JavaClasses classes = productionClasses("cn.net.mxz.timeimprint.task.service.extension");
        noClasses()
                .that()
                .resideInAPackage("cn.net.mxz.timeimprint.task.service.extension..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cn.net.mxz.timeimprint.task.web..",
                        "cn.net.mxz.timeimprint.task.service.storage..",
                        "org.springframework.web..")
                .check(classes);
    }

    private static JavaClasses productionClasses(String pkg) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(pkg);
    }
}
