package ${groupId}.common;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo comun a todos los tests de integracion: perfil {@code test} y una transaccion por prueba.
 *
 * <p><b>La base de datos no se declara aqui.</b> Sale de la URL {@code jdbc:tc:postgresql:...} de
 * {@code application-test.yaml}: el propio driver de Testcontainers levanta el contenedor cuando alguien
 * pide la primera conexion, y lo reutiliza para toda la ejecucion. Por eso esta clase no necesita
 * {@code @Testcontainers} ni ningun {@code @Container}.</p>
 *
 * <p>El {@code @Transactional} hace que cada test termine con un rollback, asi que las pruebas no se
 * contaminan entre si y no hay que ir limpiando tablas a mano. Quien necesite commits de verdad —la
 * caché, por ejemplo— no debe heredar de aqui: ver {@code CacheIT}.</p>
 *
 * <p>⚠️ Esta clase se llamaba {@code ConfigTestContainersTest} y declaraba un contenedor de Redis que
 * arrancaba <b>cinco veces por suite</b> sin que ninguna prueba lo usara: la caché esta apagada
 * ({@code spring.cache.type: none}) en el perfil de test a proposito, para que los tests no lean valores
 * cacheados de otra prueba. Quien de verdad necesita Redis se lo levanta el, y solo el.</p>
 */
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

}
