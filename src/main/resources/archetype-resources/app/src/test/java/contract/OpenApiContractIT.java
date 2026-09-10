package ${groupId}.contract;

import ${groupId}.common.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Genera el contrato OpenAPI desde el codigo y comprueba que no ha cambiado sin querer.
 *
 * <p>El enfoque es <b>code-first</b>: la fuente de verdad son los controladores y sus anotaciones, y
 * springdoc deriva el OpenAPI. Este test lo escribe en el modulo {@code contract}, que es quien lo
 * versiona y lo empaqueta para que otros lo consuman.</p>
 *
 * <p><b>Y hace de guardian:</b> si el contrato generado difiere del que hay versionado, el build se
 * pone rojo. No para impedir cambiar la API —eso hay que poder hacerlo— sino para que **ningun cambio
 * de contrato pase inadvertido**: renombrar un campo de un DTO es un cambio para todos los clientes, y
 * sin este test se cuela en un commit que hablaba de otra cosa.</p>
 *
 * <p>Para aceptar el cambio a proposito:</p>
 * <pre>./mvnw -pl app verify -Dopenapi.update=true</pre>
 * <p>y se commitea el {@code openapi.json} junto al cambio de codigo, que es lo que hace visible en la
 * revision que la API ha cambiado.</p>
 *
 * <p>Se genera desde un test y no con un plugin de Maven a proposito: el plugin tendria que arrancar la
 * aplicacion en una fase aparte, con su base de datos y su configuracion; aqui se reaprovecha el
 * contexto que los tests ya levantan con Testcontainers.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("El contrato OpenAPI esta al dia")
class OpenApiContractIT extends BaseIntegrationTest {

    /** Relativa al modulo app, que es el directorio de trabajo de los tests. */
    private static final Path CONTRATO = Path.of("..", "contract", "src", "main", "resources", "openapi", "openapi.json");

    private static final String PROPIEDAD_ACTUALIZAR = "openapi.update";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("coincide con el fichero versionado en el modulo contract")
    void elContratoCoincideConElVersionado() throws Exception {
        String generado = generarContrato();

        if (Boolean.parseBoolean(System.getProperty(PROPIEDAD_ACTUALIZAR))) {
            Files.createDirectories(CONTRATO.getParent());
            Files.writeString(CONTRATO, generado, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(CONTRATO))
                .as("Falta %s. Generalo con: ./mvnw -pl app verify -D%s=true", CONTRATO, PROPIEDAD_ACTUALIZAR)
                .isTrue();

        String versionado = Files.readString(CONTRATO, StandardCharsets.UTF_8);

        assertThat(generado.strip())
                .as("""
                        El contrato OpenAPI ha CAMBIADO respecto al versionado en el modulo contract.

                        Eso no es necesariamente un error: si has cambiado la API a proposito, acepta el
                        cambio y commitea el fichero junto al cambio de codigo, para que se vea en la
                        revision que la API se ha movido:

                          ./mvnw -pl app verify -D%s=true

                        Si NO esperabas cambiar la API, mira que has tocado: renombrar un campo de un DTO
                        o cambiar un tipo rompe a todos los clientes.
                        """.formatted(PROPIEDAD_ACTUALIZAR))
                .isEqualTo(versionado.strip());
    }

    /**
     * Pide el contrato a springdoc y lo normaliza.
     *
     * <p>La normalizacion no es cosmetica: sin claves ordenadas, el JSON sale en un orden que puede
     * variar entre ejecuciones y el test fallaria sin que nada haya cambiado de verdad — que es la forma
     * mas rapida de que alguien lo desactive.</p>
     */
    private String generarContrato() throws Exception {
        String crudo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        // ⚠️ Se lee como Object (mapas y listas), NO como JsonNode.
        // ORDER_MAP_ENTRIES_BY_KEYS solo ordena Map: sobre un JsonNode no hace nada, y springdoc NO
        // garantiza el orden de las claves entre ejecuciones — dos corridas seguidas intercambiaban
        // "first" y "last". Un test de contrato que parpadea sin que nadie cambie nada acaba
        // desactivado, y entonces deja de proteger.
        Object arbol = mapper.readValue(crudo, Object.class);
        return mapper.writeValueAsString(arbol);
    }
}
