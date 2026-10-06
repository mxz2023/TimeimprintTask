package cn.net.mxz.timeimprint.task.boot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Q07 架构门禁：kernel 纯 Java；禁止 Mxz 类名前缀；禁止包名使用 impl/util/misc。
 *
 * <p>P05 F10 模块边界：飞书 SDK 与出站 HTTP 只在 adapter；kernel/scenario 等无 adapter 依赖；
 * adapter 不反向依赖业务模块。
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

    private static final String ADAPTER = "cn.net.mxz.timeimprint.task.adapter..";

    @Test
    void kernelAndScenarioMustNotDependOnAdapter() {
        JavaClasses classes = productionClasses("cn.net.mxz.timeimprint.task");
        assertFalse(
                classes.that(DescribedPredicate.describe(
                                "reside in scenario",
                                c -> c.getPackageName().startsWith("cn.net.mxz.timeimprint.task.service.scenario")))
                        .isEmpty(),
                "scenario classes must be imported, otherwise the rule is vacuous");
        noClasses()
                .that()
                .resideInAnyPackage(
                        "cn.net.mxz.timeimprint.task.service.kernel..",
                        "cn.net.mxz.timeimprint.task.service.scenario..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ADAPTER)
                .check(classes);
    }

    @Test
    void onlyNotificationGatewayWebAndBootMayDependOnAdapter() {
        // 允许的消费者见 02 模块表：notification、gateway、web（回调验签）、boot-loader（装配）。
        noClasses()
                .that()
                .resideInAnyPackage(
                        "cn.net.mxz.timeimprint.task.common..",
                        "cn.net.mxz.timeimprint.task.domain..",
                        "cn.net.mxz.timeimprint.task.service.kernel..",
                        "cn.net.mxz.timeimprint.task.service.extension..",
                        "cn.net.mxz.timeimprint.task.service.application..",
                        "cn.net.mxz.timeimprint.task.service.runtime..",
                        "cn.net.mxz.timeimprint.task.service.storage..",
                        "cn.net.mxz.timeimprint.task.service.capability.calendar..",
                        "cn.net.mxz.timeimprint.task.service.scenario..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ADAPTER)
                .check(productionClasses("cn.net.mxz.timeimprint.task"));
    }

    @Test
    void adapterMustNotDependOnBusinessModules() {
        JavaClasses classes = productionClasses("cn.net.mxz.timeimprint.task");
        assertFalse(
                classes.that(DescribedPredicate.describe(
                                "reside in adapter", c -> c.getPackageName().startsWith("cn.net.mxz.timeimprint.task.adapter")))
                        .isEmpty(),
                "adapter classes must be imported, otherwise the rule is vacuous");
        noClasses()
                .that()
                .resideInAPackage(ADAPTER)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cn.net.mxz.timeimprint.task.service..",
                        "cn.net.mxz.timeimprint.task.domain..",
                        "cn.net.mxz.timeimprint.task.gateway..",
                        "cn.net.mxz.timeimprint.task.web..",
                        "cn.net.mxz.timeimprint.task.boot..")
                .check(classes);
    }

    @Test
    void feishuSdkAndOutboundHttpStayInsideAdapter() {
        // 飞书官方 SDK 坐标当前不存在；一旦引入也只能出现在 adapter。JDK HttpClient 同样只属于 adapter 的出站/取令牌实现。
        noClasses()
                .that()
                .resideOutsideOfPackage(ADAPTER)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.lark..",
                        "com.larksuite..",
                        "java.net.http..",
                        "okhttp3..",
                        "org.apache.hc..",
                        "org.apache.http..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..")
                .check(productionClasses("cn.net.mxz.timeimprint.task"));
    }

    private static JavaClasses productionClasses(String pkg) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(pkg);
    }
}
