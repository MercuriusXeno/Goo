package com.mercuriusxeno.goo;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit convention enforcement. Only rules that catch recurring mistakes.
 */
class ConventionTest {

    private static JavaClasses mainClasses;
    private static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        mainClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mercuriusxeno.goo");
        testClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("com.mercuriusxeno.goo");
    }

    /** HARD RULE: no @OnlyIn anywhere - classes, methods, or fields. */
    @Test
    void noOnlyIn() {
        String annotation = "net.neoforged.api.distmarker.OnlyIn";
        noClasses()
            .should().beAnnotatedWith(annotation)
            .orShould(haveAnnotatedMembers(annotation))
            .because("HARD RULE: use dist executors or proxies, never @OnlyIn")
            .check(mainClasses);
    }

    /** Test classes should be package-private. */
    @Test
    void testClassesArePackagePrivate() {
        classes()
            .that().haveSimpleNameEndingWith("Test")
            .should().notBePublic()
            .because("JUnit 5 doesn't need public test classes (TEST-ETHOS)")
            .check(testClasses);
    }

    /** Condition: class has methods or fields annotated with the given annotation. */
    private static ArchCondition<JavaClass> haveAnnotatedMembers(String annotationName) {
        return new ArchCondition<>("have members annotated with @" + extractSimpleName(annotationName)) {
            @Override
            public void check(JavaClass cls, ConditionEvents events) {
                cls.getMembers().stream()
                    .filter(m -> m.isAnnotatedWith(annotationName))
                    .forEach(m -> events.add(SimpleConditionEvent.violated(
                        m, m.getFullName() + " uses @" + extractSimpleName(annotationName))));
            }
        };
    }

    private static String extractSimpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    // ── Decoupling boundary enforcement (decoupling-arch §5) ────────────

    private static final String[] DOMAIN_PACKAGES = {
        "com.mercuriusxeno.goo.block..", "com.mercuriusxeno.goo.item.."
    };

    /**
     * Adapter-boundary classes that legitimately import GooItems, GooBlocks, or GooFluids.
     * Pattern B construction sites, fluid handlers, and registration code.
     */
    private static DescribedPredicate<JavaClass> registryAllowed() {
        return simpleName("CrucibleBlock")
            .or(simpleName("CanisterBlockEntity"))
            .or(simpleName("VatBlock"))
            .or(simpleName("GooCauldronInteractions"))
            .or(simpleName("BlobStacks"))
            .or(simpleName("GooOmniblobItem"))
            .or(simpleName("DepletedBlazeRodItem"))
            .or(simpleName("PartiallyMeltedItem"))
            .or(simpleName("BucketOfGooItem"))
            .or(simpleName("GooFluidHandler"))
            .or(simpleName("HubFluidHandler"))
            .or(simpleName("GooFluidTransfer"))
            .or(simpleName("PlayerInventorySlotHandler"))
            .or(simpleName("TapBlockEntity"))
            .or(simpleName("CanisterFluidHandler"))
            .or(simpleName("BucketGooFluidHandler"))
            .or(simpleName("GasketInstallation"))
            .or(simpleName("TapBlock"))
            .or(simpleName("PlexerBlock"))
            .or(simpleName("HubBlock"))
            .or(simpleName("CanisterSlotLifecycle"))
            .or(simpleName("CrucibleInteraction"))
            .or(simpleName("CrucibleDrops"))
            .or(simpleName("TapInteractionHandler"))
            .or(simpleName("VatGasketOps"));
    }

    /** No new domain-layer class may import GooItems, GooBlocks, or GooFluids. */
    @Test
    void domainLayerDoesNotImportBannedRegistryClasses() {
        noClasses()
            .that().resideInAnyPackage(DOMAIN_PACKAGES)
            .and(DescribedPredicate.not(registryAllowed()))
            .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooItems")
            .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooBlocks")
            .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooFluids")
            .because("domain layer must not import registry singletons (decoupling-arch §5.1)")
            .check(mainClasses);
    }

    /** Goo.GOO_VALUES must only appear in adapter wrappers, not domain logic. */
    @Test
    void domainLayerDoesNotAccessGooValues() {
        noClasses()
            .that().resideInAnyPackage(DOMAIN_PACKAGES)
            .and(DescribedPredicate.not(
                simpleName("CrucibleBlockEntity").or(simpleName("PlexerBlockEntity"))
                    .or(simpleName("CrucibleInsertion"))))
            .should().accessField(Goo.class, "GOO_VALUES")
            .because("domain layer must use IGooValueLookup seams, not Goo.GOO_VALUES (decoupling-arch §5.2)")
            .check(mainClasses);
    }

    /** BuiltInRegistries must only appear in adapter wrappers, not domain logic. */
    @Test
    void domainLayerDoesNotUseBuiltInRegistries() {
        noClasses()
            .that().resideInAnyPackage(DOMAIN_PACKAGES)
            .and(DescribedPredicate.not(
                simpleName("ContainerEvaluator")
                    .or(simpleName("CrucibleBlockEntity"))
                    .or(simpleName("CrucibleInsertion"))
                    .or(simpleName("PlexerBlockEntity"))))
            .should().dependOnClassesThat()
                .haveFullyQualifiedName("net.minecraft.core.registries.BuiltInRegistries")
            .because("domain layer must resolve Identifiers in adapter wrappers (decoupling-arch §5.3)")
            .check(mainClasses);
    }
}
