package com.paysi.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PackageDependencyTest {
    @Test
    void coreAndSplitStayIndependentFromFrameworks() {
        var classes = new ClassFileImporter().importPackages("com.paysi");

        noClasses().that().resideInAnyPackage("..core..", "..payment.split..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void ledgerDomainStaysIndependentFromFrameworks() {
        var classes = new ClassFileImporter().importPackages("com.paysi.ledger.domain");
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(classes);
    }
}
