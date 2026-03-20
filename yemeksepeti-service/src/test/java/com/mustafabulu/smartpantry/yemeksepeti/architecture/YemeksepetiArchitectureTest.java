package com.mustafabulu.smartpantry.yemeksepeti.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "com.mustafabulu.smartpantry.yemeksepeti",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class YemeksepetiArchitectureTest {

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
    static final ArchRule yemeksepeti_module_should_not_depend_on_other_service_modules =
            noClasses()
                    .that().resideInAPackage("com.mustafabulu.smartpantry.yemeksepeti..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.mustafabulu.smartpantry.migros..",
                            "com.mustafabulu.smartpantry.discovery.."
                    );

    @ArchTest
    static final ArchRule yemeksepeti_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.mustafabulu.smartpantry.yemeksepeti.(*)..")
                    .should().beFreeOfCycles();
}
