package ${groupId};

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Tests de ARQUITECTURA: comprueban la forma del codigo, no su comportamiento.
 *
 * <p>Existen porque una estructura que solo esta documentada se erosiona. El primer atajo —un
 * repositorio inyectado en un controlador para salir del paso— no rompe ningun test y nadie lo nota
 * hasta que ya hay veinte. Aqui rompe el build, que es cuando cuesta barato arreglarlo.</p>
 *
 * <p><b>Si una regla te estorba constantemente, la sospechosa es la regla, no el codigo.</b> Una regla
 * que salta en casos legitimos acaba desactivada, y una desactivada no protege de nada: mejor
 * cambiarla o borrarla a conciencia que convivir con ella en rojo.</p>
 */
@AnalyzeClasses(
        packages = "${groupId}",
        // Las clases de test no siguen estas reglas (ni deben): un test puede inyectar lo que necesite.
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureTest {

    /**
     * Las capas van en un solo sentido. Es la regla que sostiene a las demas.
     */
    @ArchTest
    static final ArchRule las_capas_se_respetan = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controllers").definedBy("..controllers..")
            .layer("Services").definedBy("..services..")
            .layer("Repositories").definedBy("..repositories..")
            .layer("Configs").definedBy("..configs..")
            // Nadie llama a un controlador: es la puerta de entrada, no una utilidad.
            .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Configs")
            // Que un controlador hable directamente con el repositorio es el atajo mas comun y el que
            // mas cuesta deshacer: la logica acaba repartida entre la capa web y la de datos.
            .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Configs")
            .as("Las dependencias entre capas van en un solo sentido");

    @ArchTest
    static final ArchRule las_configuraciones_viven_en_configs = classes()
            .that().areAnnotatedWith(Configuration.class)
            .should().resideInAPackage("..configs..")
            .as("Las clases @Configuration van en el paquete configs, para que la configuracion de la "
                    + "aplicacion se pueda leer entera de un vistazo");

    /**
     * Inyeccion por constructor, no por campo.
     *
     * <p>No es estetica: un campo {@code @Autowired} no puede ser {@code final}, permite construir el
     * objeto a medio inicializar, y obliga a levantar un contexto de Spring para poder probar la clase.
     * Con el constructor, las dependencias son explicitas y el test es un {@code new}.</p>
     */
    @ArchTest
    static final ArchRule sin_inyeccion_por_campo = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .as("Inyeccion por constructor (@RequiredArgsConstructor), nunca @Autowired en un campo");

    @ArchTest
    static final ArchRule los_controladores_se_llaman_Controller = classes()
            .that().resideInAPackage("..controllers..")
            .and().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller")
            .as("Los controladores REST terminan en *Controller");

    @ArchTest
    static final ArchRule los_servicios_se_llaman_Service = classes()
            .that().resideInAPackage("..services..")
            .and().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Service")
            .as("Los servicios terminan en *Service");

    /**
     * Los controladores no tocan entidades.
     *
     * <p>Es la regla que mas se salta y la que mas caro sale, porque el coste no se ve donde se comete.
     * Lo que devuelve un controlador acaba en el contrato OpenAPI y, desde ahi, <b>en todos los clientes
     * generados</b>: publicar la entidad convierte cualquier cambio de la tabla en un cambio de la API
     * publica. Ademas, serializarla fuera de la transaccion revienta con las relaciones perezosas.</p>
     *
     * <p>Aqui ya paso: el cliente Java generado traia una clase con la forma exacta de la tabla.</p>
     */
    @ArchTest
    static final ArchRule los_controladores_no_exponen_entidades = noClasses()
            .that().resideInAPackage("..controllers..")
            .should().dependOnClassesThat().resideInAPackage("..entities..")
            .as("Los controladores devuelven DTOs, nunca entidades");

    /**
     * Los DTOs son la frontera con el exterior: no conocen a nadie hacia dentro.
     */
    @ArchTest
    static final ArchRule los_dtos_no_dependen_de_nada = noClasses()
            .that().resideInAPackage("..dtos..")
            .should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..services..", "..repositories..")
            .as("Los DTOs no dependen de la logica de negocio ni de la capa web");

    /**
     * Nada de {@code System.out}.
     *
     * <p>En un contenedor eso escribe fuera del sistema de logs: no lleva nivel, ni marca de tiempo, ni
     * traceId, y no se puede filtrar ni silenciar. En produccion es ruido que no se puede apagar.</p>
     */
    @ArchTest
    static final ArchRule sin_System_out = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .as("Usa un Logger, no System.out ni System.err");

    /**
     * Nada de excepciones genericas.
     *
     * <p>Un {@code throw new RuntimeException(...)} obliga a quien llama a capturar todo o nada, y
     * borra la unica informacion util: que ha fallado exactamente.</p>
     */
    @ArchTest
    static final ArchRule sin_excepciones_genericas = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
            .as("Lanza excepciones propias, no RuntimeException/Exception/Throwable");

    @ArchTest
    static final ArchRule sin_java_util_logging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
            .as("El log va por SLF4J, no por la API nativa de Java");
}
