package ${groupId}.controllers.integration;

import ${groupId}.common.ConfigTestContainersTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que la seguridad esta PUESTA.
 *
 * <p>Los demas tests se autentican para poder probar lo suyo, asi que ninguno se daria cuenta si alguien
 * dejara la API abierta: todos seguirian en verde. Este test existe justo para eso, y por eso NO extiende
 * de {@code BaseControllerIT} — esa clase lleva un {@code @WithMockUser} que autenticaria tambien aqui y
 * dejaria el test sin sentido.</p>
 *
 * <p>⚠️ Tampoco lleva {@code @ActiveProfiles}, y es deliberado: si alguien anadiera un perfil que apaga la
 * seguridad, este test tiene que caer. Un test que se puede desactivar cambiando de perfil no protege
 * nada.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("La seguridad esta activa y no se puede apagar sin que salte")
class SeguridadActivaIT extends ConfigTestContainersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sin credenciales, un endpoint de negocio responde 401")
    void sinCredencialesDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/examples"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con un JWT valido, el mismo endpoint responde 200")
    void conJwtDevuelve200() throws Exception {
        // Asi se autentica un cliente de verdad contra esta API: con un token, no con una sesion.
        // `jwt()` construye la autenticacion ya resuelta, sin necesidad de firmar nada.
        mockMvc.perform(get("/api/v1/examples").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("las sondas de Kubernetes responden sin credenciales")
    void lasSondasSonPublicas() throws Exception {
        // Si estas dejaran de ser publicas, el pod nunca se marcaria como vivo y el despliegue se
        // quedaria colgado sin decir por que.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("el resto de actuator NO es publico")
    void elRestoDeActuatorNoEsPublico() throws Exception {
        // /actuator/env expone la configuracion entera, variables de entorno incluidas. Que health sea
        // publico no puede arrastrar consigo al resto.
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
