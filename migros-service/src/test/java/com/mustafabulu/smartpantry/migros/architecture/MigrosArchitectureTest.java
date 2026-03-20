package com.mustafabulu.smartpantry.migros.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "com.mustafabulu.smartpantry.migros",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class MigrosArchitectureTest {

    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..repository..");

    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..controller..");

    @ArchTest
    static final ArchRule migros_module_should_not_depend_on_other_service_modules =
            noClasses()
                    .that().resideInAPackage("com.mustafabulu.smartpantry.migros..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.mustafabulu.smartpantry.yemeksepeti..",
                            "com.mustafabulu.smartpantry.discovery.."
                    );

    @ArchTest
    static final ArchRule migros_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.mustafabulu.smartpantry.migros.(*)..")
                    .should().beFreeOfCycles();
}
