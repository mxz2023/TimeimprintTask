package cn.net.mxz.timeimprint.task.boot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * T01 架构基线：kernel 不得依赖 Spring/MyBatis/Web。
 */
class MxzArchitectureRulesTest {

    @Test
    void kernelMustRemainPureJava() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cn.net.mxz.timeimprint.task.service.kernel");
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
}
