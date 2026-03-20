package com.mustafabulu.smartpantry.common.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "com.mustafabulu.smartpantry.common",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class CommonArchitectureTest {

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
    static final ArchRule repositories_should_not_depend_on_services_or_controllers =
            noClasses()
                    .that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule common_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.mustafabulu.smartpantry.common.(*)..")
                    .should().beFreeOfCycles();
}
