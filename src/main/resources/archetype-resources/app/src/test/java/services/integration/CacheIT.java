package ${groupId}.services.integration;

import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.entities.MyTable;
import ${groupId}.exceptions.ResourceNotFoundException;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.services.ExampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprueba que la caché <b>existe de verdad</b>: que guarda y que se invalida.
 *
 * <p>Suena a test de una biblioteca ajena, y no lo es. Lo que se prueba aqui es el cableado de ESTE
 * proyecto, que tiene tres piezas y falla en silencio si falta cualquiera de ellas: el starter de caché
 * en el pom, el {@code @EnableCaching} de {@code CacheConfig} y el serializador JSON. Si se cae una,
 * la aplicacion <b>arranca igual y responde igual</b>, solo que sin cachear. Solo un test que mire el
 * efecto —no la anotacion— nota la diferencia.</p>
 *
 * <p><b>Por que no hereda de {@code BaseIntegrationTest}:</b> esa clase envuelve cada prueba en una
 * transaccion con rollback, y aqui hacen falta escrituras confirmadas de verdad para poder distinguir
 * «lo leyo de Redis» de «lo leyo de la base». A cambio hay que limpiar a mano, que es lo que hace el
 * {@code @BeforeEach}.</p>
 */
@SpringBootTest(properties = "spring.cache.type=redis")
@ActiveProfiles("test")
@Testcontainers
class CacheIT {

    // ⚠️ La MISMA version que compose-app.yml. Estuvieron descuadradas (los tests con Redis 7, el
    // entorno real con Redis 8), que es la peor forma de descuadre: los tests dan verde sobre un motor
    // que no es el que se despliega. Lo vigila la comprobacion C9 de scripts/check-generated.sh.
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.1"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void propiedadesDeRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private ExampleService exampleService;

    @Autowired
    private MyTableRepository myTableRepository;

    @Autowired
    private CacheManager cacheManager;

    // ⚠️ Antes Y despues, y el «despues» no es por simetria. Esta clase escribe de verdad (no hay
    // rollback), asi que las filas que deja sobreviven a la prueba y contaminan a las demas: los tests
    // del controlador empezaron a ver tres registros donde esperaban dos. Quien renuncia a la
    // transaccion se encarga de recoger.
    @BeforeEach
    @AfterEach
    void limpiar() {
        myTableRepository.deleteAll();
        Cache cache = cacheManager.getCache(ExampleService.CACHE_EJEMPLOS);
        assertNotNull(cache, "No existe la region de cache '" + ExampleService.CACHE_EJEMPLOS
                + "': falta @EnableCaching o el starter de cache en el pom");
        cache.clear();
    }

    /**
     * Demuestra que la segunda lectura NO toca la base de datos.
     *
     * <p>El truco esta en cambiar la fila <b>por debajo</b> del servicio, con el repositorio, sin pasar
     * por {@code update()} — asi nadie invalida la entrada. Si la caché funciona, el servicio sigue
     * devolviendo el valor viejo; si no funcionara, devolveria el nuevo y el test fallaria.</p>
     */
    @Test
    void laSegundaLecturaSaleDeLaCache() {
        Long id = exampleService.create(new MyTableRequest("Original", "A", "D", null));
        exampleService.findById(id);

        MyTable fila = myTableRepository.findById(id).orElseThrow();
        fila.setName("CambiadoPorDetras");
        myTableRepository.save(fila);

        assertEquals("Original", exampleService.findById(id).name(),
                "Devolvio el valor nuevo: la lectura fue a la base de datos, no hay cache");
    }

    /** La otra mitad del trato: quien escribe, invalida. */
    @Test
    void actualizarInvalidaLaEntrada() {
        Long id = exampleService.create(new MyTableRequest("Original", "A", "D", null));
        exampleService.findById(id);

        exampleService.update(id, new MyTableRequest("Actualizado", "B", "E", null));

        assertEquals("Actualizado", exampleService.findById(id).name(),
                "Sigue el valor viejo: el @CacheEvict de update() no esta invalidando");
    }

    /**
     * Borrar tambien invalida, o la caché resucitaria un registro que ya no existe.
     *
     * <p>Fijate en que se comprueba el <b>comportamiento</b> (pedirlo da 404) y no el contenido de la
     * caché. La primera version de este test miraba dentro, con
     * {@code cacheManager.getCache(...).get(id)}, y era peor por dos motivos: se ataba a como esta
     * implementado, y sobre todo <b>demostraba menos</b>. Si la entrada siguiera cacheada,
     * {@code findById} la devolveria en vez de lanzar — asi que esta asercion cubre la de antes y
     * ademas prueba lo que de verdad le importa a quien llama.</p>
     */
    @Test
    void borrarInvalidaLaEntrada() {
        Long id = exampleService.create(new MyTableRequest("Original", "A", "D", null));
        exampleService.findById(id);

        exampleService.delete(id);

        assertThrows(ResourceNotFoundException.class, () -> exampleService.findById(id),
                "Devolvio un registro ya borrado: sale de la cache, que no se invalido");
    }
}
