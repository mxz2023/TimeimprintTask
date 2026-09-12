#!/usr/bin/env python3
"""Generate T01 13-module Maven skeleton for TimeImprintTask."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GROUP = "cn.net.mxz"
REVISION = "0.1.0-SNAPSHOT"
BASE_PKG = "cn.net.mxz.timeimprint.task"
SPRING_BOOT = "4.0.8"
SPRING_AI = "2.0.1"
MYBATIS = "4.0.1"
JAVA = "21"

MODULES = [
    ("timeimprint-task-common", "common", []),
    ("timeimprint-task-domain", "domain", ["timeimprint-task-common"]),
    ("timeimprint-task-service-kernel", "service.kernel", ["timeimprint-task-common"]),
    ("timeimprint-task-service-extension-api", "service.extension", ["timeimprint-task-service-kernel", "timeimprint-task-common"]),
    ("timeimprint-task-service-application", "service.application", ["timeimprint-task-service-kernel", "timeimprint-task-service-extension-api", "timeimprint-task-common"]),
    ("timeimprint-task-service-runtime", "service.runtime", ["timeimprint-task-service-kernel", "timeimprint-task-service-extension-api", "timeimprint-task-service-application", "timeimprint-task-common"]),
    ("timeimprint-task-service-storage-mysql", "service.storage.mysql", ["timeimprint-task-service-kernel", "timeimprint-task-service-application", "timeimprint-task-service-runtime", "timeimprint-task-common"]),
    ("timeimprint-task-service-capability-calendar", "service.capability.calendar", ["timeimprint-task-service-extension-api", "timeimprint-task-service-kernel", "timeimprint-task-common"]),
    ("timeimprint-task-service-capability-notification", "service.capability.notification", ["timeimprint-task-service-extension-api", "timeimprint-task-service-kernel", "timeimprint-task-common"]),
    ("timeimprint-task-service-scenario-basic", "service.scenario.basic", ["timeimprint-task-service-extension-api", "timeimprint-task-service-kernel", "timeimprint-task-service-capability-calendar", "timeimprint-task-common"]),
    ("timeimprint-task-gateway", "gateway", ["timeimprint-task-domain", "timeimprint-task-service-application", "timeimprint-task-service-kernel", "timeimprint-task-common"]),
    ("timeimprint-task-web", "web", ["timeimprint-task-domain", "timeimprint-task-gateway", "timeimprint-task-common"]),
    ("timeimprint-task-boot-loader", "boot", None),  # special
]


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"write {path.relative_to(ROOT)}")


def module_pom(artifact: str, deps: list[str], extra: str = "") -> str:
    dep_xml = ""
    for d in deps:
        dep_xml += f"""
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>{d}</artifactId>
            <version>${{revision}}</version>
        </dependency>"""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>{GROUP}</groupId>
        <artifactId>timeimprint-task-parent</artifactId>
        <version>${{revision}}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>{artifact}</artifactId>
    <name>{artifact}</name>
    <dependencies>{dep_xml}
{extra}
    </dependencies>
</project>
"""


PARENT_POM = f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>{SPRING_BOOT}</version>
        <relativePath/>
    </parent>

    <groupId>{GROUP}</groupId>
    <artifactId>timeimprint-task-parent</artifactId>
    <version>${{revision}}</version>
    <packaging>pom</packaging>
    <name>timeimprint-task-parent</name>
    <description>TimeImprintTask P01 multi-module parent</description>

    <modules>
        <module>timeimprint-task-common</module>
        <module>timeimprint-task-domain</module>
        <module>timeimprint-task-service-kernel</module>
        <module>timeimprint-task-service-extension-api</module>
        <module>timeimprint-task-service-application</module>
        <module>timeimprint-task-service-runtime</module>
        <module>timeimprint-task-service-storage-mysql</module>
        <module>timeimprint-task-service-capability-calendar</module>
        <module>timeimprint-task-service-capability-notification</module>
        <module>timeimprint-task-service-scenario-basic</module>
        <module>timeimprint-task-gateway</module>
        <module>timeimprint-task-web</module>
        <module>timeimprint-task-boot-loader</module>
    </modules>

    <properties>
        <revision>{REVISION}</revision>
        <java.version>{JAVA}</java.version>
        <maven.compiler.release>{JAVA}</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-ai.version>{SPRING_AI}</spring-ai.version>
        <mybatis-spring-boot.version>{MYBATIS}</mybatis-spring-boot.version>
        <archunit.version>1.4.1</archunit.version>
        <mysql.it.image>mysql:9.7.2</mysql.it.image>
        <mysql.it.image.digest>PENDING_T01_LOCK</mysql.it.image.digest>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${{spring-ai.version}}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.mybatis.spring.boot</groupId>
                <artifactId>mybatis-spring-boot-starter</artifactId>
                <version>${{mybatis-spring-boot.version}}</version>
            </dependency>
            <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>${{archunit.version}}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-common</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-domain</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-kernel</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-extension-api</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-application</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-runtime</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-storage-mysql</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-capability-calendar</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-capability-notification</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-service-scenario-basic</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-gateway</artifactId>
                <version>${{revision}}</version>
            </dependency>
            <dependency>
                <groupId>{GROUP}</groupId>
                <artifactId>timeimprint-task-web</artifactId>
                <version>${{revision}}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-enforcer-plugin</artifactId>
                    <version>3.5.0</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.5.3</version>
                </plugin>
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>flatten-maven-plugin</artifactId>
                    <version>1.7.0</version>
                </plugin>
            </plugins>
        </pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>flatten-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>flatten</id>
                        <phase>process-resources</phase>
                        <goals><goal>flatten</goal></goals>
                    </execution>
                    <execution>
                        <id>flatten-clean</id>
                        <phase>clean</phase>
                        <goals><goal>clean</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <updatePomFile>true</updatePomFile>
                    <flattenMode>resolveCiFriendliesOnly</flattenMode>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-rules</id>
                        <goals><goal>enforce</goal></goals>
                        <configuration>
                            <rules>
                                <requireJavaVersion>
                                    <version>[21,22)</version>
                                </requireJavaVersion>
                                <requireMavenVersion>
                                    <version>[3.9.0,4.0.0)</version>
                                </requireMavenVersion>
                                <dependencyConvergence/>
                                <banCircularDependencies/>
                                <bannedDependencies>
                                    <searchTransitive>true</searchTransitive>
                                    <excludes>
                                        <!-- kernel purity enforced additionally by ArchUnit -->
                                        <exclude>com.h2database:h2</exclude>
                                    </excludes>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <excludedGroups>mysql-it,dual-process-it</excludedGroups>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>mysql-it</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-failsafe-plugin</artifactId>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>integration-test</goal>
                                    <goal>verify</goal>
                                </goals>
                                <configuration>
                                    <groups>mysql-it</groups>
                                    <excludedGroups>dual-process-it</excludedGroups>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
        <profile>
            <id>dual-process-it</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-failsafe-plugin</artifactId>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>integration-test</goal>
                                    <goal>verify</goal>
                                </goals>
                                <configuration>
                                    <groups>dual-process-it</groups>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
"""


def pkg_info(pkg: str, desc: str) -> str:
    return f"""/**
 * {desc}
 */
package {pkg};
"""


def main() -> None:
    write(ROOT / "pom.xml", PARENT_POM)

    for artifact, pkg_suffix, deps in MODULES:
        mod = ROOT / artifact
        pkg = f"{BASE_PKG}.{pkg_suffix}"
        src = mod / "src/main/java" / pkg.replace(".", "/")
        test = mod / "src/test/java" / pkg.replace(".", "/")

        if artifact == "timeimprint-task-boot-loader":
            extra = f"""
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-web</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-service-storage-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-service-runtime</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-service-application</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-service-capability-calendar</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-service-capability-notification</artifactId>
        </dependency>
        <dependency>
            <groupId>{GROUP}</groupId>
            <artifactId>timeimprint-task-service-scenario-basic</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <scope>test</scope>
        </dependency>
"""
            write(mod / "pom.xml", module_pom(artifact, [], extra))
            write(
                src / "MxzTimeImprintTaskApplication.java",
                f"""package {pkg};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TimeImprintTask 启动入口；唯一组合根。
 */
@SpringBootApplication(scanBasePackages = "{BASE_PKG}")
public class MxzTimeImprintTaskApplication {{

    public static void main(String[] args) {{
        SpringApplication.run(MxzTimeImprintTaskApplication.class, args);
    }}
}}
""",
            )
            write(
                mod / "src/main/resources/application.yml",
                """spring:
  application:
    name: timeimprint-task
  profiles:
    default: local
  lifecycle:
    timeout-per-shutdown-phase: ${SHUTDOWN_GRACE_SECONDS:40}s

server:
  address: ${SERVER_ADDRESS:127.0.0.1}
  port: ${SERVER_PORT:8080}
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db

---
spring:
  config:
    activate:
      on-profile: local,test
server:
  address: 127.0.0.1
""",
            )
            write(
                test / "MxzModuleBaselineTest.java",
                f"""package {pkg};

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T01：确认一期批准的13个平级模块目录存在。
 */
class MxzModuleBaselineTest {{

    private static final List<String> MODULES = List.of(
            "timeimprint-task-common",
            "timeimprint-task-domain",
            "timeimprint-task-service-kernel",
            "timeimprint-task-service-extension-api",
            "timeimprint-task-service-application",
            "timeimprint-task-service-runtime",
            "timeimprint-task-service-storage-mysql",
            "timeimprint-task-service-capability-calendar",
            "timeimprint-task-service-capability-notification",
            "timeimprint-task-service-scenario-basic",
            "timeimprint-task-gateway",
            "timeimprint-task-web",
            "timeimprint-task-boot-loader");

    @Test
    void approvedModulesExist() {{
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("pom.xml"))) {{
            root = root.getParent();
        }}
        // Surefire cwd is module dir; parent is repo root for boot-loader when run from reactor,
        // but when run alone climb to aggregator.
        Path probe = Path.of("").toAbsolutePath();
        if (!Files.exists(probe.resolve("timeimprint-task-common"))) {{
            probe = probe.getParent();
        }}
        for (String module : MODULES) {{
            assertTrue(Files.isDirectory(probe.resolve(module)), () -> "missing module " + module);
        }}
    }}
}}
""",
            )
            write(
                test / "MxzArchitectureRulesTest.java",
                f"""package {pkg};

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * T01 架构基线：kernel 不得依赖 Spring/MyBatis/Web。
 */
class MxzArchitectureRulesTest {{

    @Test
    void kernelMustRemainPureJava() {{
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("{BASE_PKG}.service.kernel");
        noClasses()
                .that()
                .resideInAPackage("{BASE_PKG}.service.kernel..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.mybatis..",
                        "jakarta.servlet..",
                        "org.apache.ibatis..")
                .check(classes);
    }}
}}
""",
            )
            continue

        extra = ""
        if artifact == "timeimprint-task-service-storage-mysql":
            extra = """
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
        </dependency>
"""
        elif artifact in (
            "timeimprint-task-service-application",
            "timeimprint-task-service-runtime",
            "timeimprint-task-gateway",
            "timeimprint-task-web",
            "timeimprint-task-service-capability-calendar",
            "timeimprint-task-service-capability-notification",
            "timeimprint-task-service-scenario-basic",
        ):
            extra = """
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
"""
        if artifact == "timeimprint-task-web":
            extra += """
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
"""
        if artifact == "timeimprint-task-domain":
            extra = """
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
"""

        write(mod / "pom.xml", module_pom(artifact, deps or [], extra))
        write(src / "package-info.java", pkg_info(pkg, f"{artifact} module."))
        # Ensure at least one compilable class for modules that need markers
        simple = "".join(part.capitalize() for part in artifact.replace("timeimprint-task-", "").split("-"))
        class_name = f"Mxz{simple}Marker"
        write(
            src / f"{class_name}.java",
            f"""package {pkg};

/**
 * {artifact} 模块占位类，保证 T01 可编译；后续任务替换为正式实现。
 */
public final class {class_name} {{
    private {class_name}() {{}}
}}
""",
        )

    write(
        ROOT / ".gitignore",
        """target/
.idea/
*.iml
.classpath
.project
.settings/
.DS_Store
*.log
.env
.env.*
!.env.example
.flattened-pom.xml
**/dependency-reduced-pom.xml
""",
    )
    write(
        ROOT / ".env.example",
        """SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
DB_JDBC_URL=jdbc:mysql://127.0.0.1:3306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC
DB_USERNAME=
DB_PASSWORD=
INSTANCE_ID=local-1
LOCAL_TENANT_ID=local-tenant
LOCAL_ACTOR_ID=local-actor
WORKER_ENABLED=true
""",
    )
    print("scaffold complete")


if __name__ == "__main__":
    main()
