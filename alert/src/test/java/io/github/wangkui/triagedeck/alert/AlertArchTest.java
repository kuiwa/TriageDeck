package io.github.wangkui.triagedeck.alert;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "io.github.wangkui.triagedeck.alert",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AlertArchTest {

    @ArchTest
    static final ArchRule alert_must_not_depend_on_other_business_modules =
            noClasses().should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.github.wangkui.triagedeck.audit..",
                            "io.github.wangkui.triagedeck.ai..",
                            "io.github.wangkui.triagedeck.notify..");

    @ArchTest
    static void alert_classes_obey_module_boundary(JavaClasses classes) {
        alert_must_not_depend_on_other_business_modules.check(classes);
    }
}
