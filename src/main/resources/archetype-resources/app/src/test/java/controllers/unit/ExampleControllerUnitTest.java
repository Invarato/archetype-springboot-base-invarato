package ${groupId}.controllers.unit;

import ${groupId}.common.UtilsTest;
import ${groupId}.controllers.ExampleController;
import ${groupId}.controllers.unit.common.BaseControllerUnitTest;
import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.dtos.responses.SimpleApiResponse;
import ${groupId}.services.ExampleService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test de SLICE del controlador: solo la capa web, con el servicio simulado.
 *
 * <p>Al ser un slice usa la seguridad por defecto de Spring Boot, no la cadena real de la aplicacion.
 * Por eso aqui si vale {@code @WithMockUser} (esta en la clase base) y hace falta {@code csrf()}: en los
 * tests de integracion, que si levantan la cadena real —stateless y sin CSRF—, es al reves.</p>
 */
@WebMvcTest(ExampleController.class)
class ExampleControllerUnitTest extends BaseControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExampleService exampleService;

    private static MyTableResponse respuesta(String name, String surname, String description) {
        return new MyTableResponse(name, surname, description, null);
    }

    @Test
    void saluda() throws Exception {
        mockMvc.perform(get("/api/v1/examples/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    @Test
    void saludaConDto() throws Exception {
        when(exampleService.getHelloDto()).thenReturn(new SimpleApiResponse("Hello World DTO"));

        mockMvc.perform(get("/api/v1/examples/helloDto"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello World DTO"));
    }

    @Test
    void crea() throws Exception {
        MyTableRequest peticion = new MyTableRequest("Name", "Surname", "Description", null);
        long idGenerado = 1L;

        when(exampleService.create(Mockito.any(MyTableRequest.class))).thenReturn(idGenerado);

        mockMvc.perform(post("/api/v1/examples")
                        .header("Host", "localhost")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(peticion)))
                .andDo(print())
                .andExpect(status().isCreated())
                // La cabecera Location es parte del contrato de un POST que crea: se comprueba.
                .andExpect(header().string("Location", "http://localhost/api/v1/examples/" + idGenerado));
    }

    @Test
    void listaTodos() throws Exception {
        when(exampleService.findAll()).thenReturn(List.of(
                respuesta("Name", "Surname", "Description"),
                respuesta("Name2", "Surname2", "Description2")));

        mockMvc.perform(get("/api/v1/examples"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Name"))
                .andExpect(jsonPath("$[1].name").value("Name2"));
    }

    @Test
    void recuperaUno() throws Exception {
        when(exampleService.findById(1L)).thenReturn(respuesta("Name", "Surname", "Description"));

        mockMvc.perform(get("/api/v1/examples/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Name"))
                .andExpect(jsonPath("$.surname").value("Surname"))
                .andExpect(jsonPath("$.description").value("Description"));
    }

    @Test
    void actualiza() throws Exception {
        MyTableRequest peticion = new MyTableRequest("Name", "Surname", "Description", null);

        Mockito.doNothing().when(exampleService).update(1L, peticion);

        mockMvc.perform(put("/api/v1/examples/1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(peticion)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    void borra() throws Exception {
        Mockito.doNothing().when(exampleService).delete(1L);

        mockMvc.perform(delete("/api/v1/examples/1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

}
