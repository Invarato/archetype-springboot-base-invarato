package ${groupId}.controllers.integration;

import ${groupId}.common.UtilsTest;
import ${groupId}.controllers.integration.common.BaseControllerIT;
import ${groupId}.dtos.requests.MyTableRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integracion del controlador de ejemplo.
 *
 * <p>⚠️ Cada peticion lleva {@code .with(jwt())}, y no es ruido: la API es <b>stateless</b> y se
 * autentica con un token, asi que los tests se autentican igual que lo hara un cliente de verdad.</p>
 *
 * <p>Ojo, porque despista: {@code @WithMockUser} <b>no funciona</b> aqui. Con
 * {@code SessionCreationPolicy.STATELESS} no hay repositorio de contexto de seguridad donde dejar la
 * autenticacion que esa anotacion prepara, asi que la peticion llega sin credenciales y responde 401.
 * Sigue valiendo en los tests de slice ({@code @WebMvcTest}), que usan la seguridad por defecto.</p>
 */
class ExampleControllerIT extends BaseControllerIT {

    @Test
    void testSayHello() throws Exception {
        mockMvc.perform(get("/api/v1/examples/hello").with(jwt()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    @Test
    void testSayHelloDto() throws Exception {
        mockMvc.perform(get("/api/v1/examples/helloDto").with(jwt()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello World DTO"));
    }

    private Long createNewExample(String name, String surname, String description) throws Exception {
        MyTableRequest mockDto = new MyTableRequest(name, surname, description, null);
        String location = this.postCreate("/api/v1/examples", mockDto);
        return this.getIdFromLocationHeader(location);
    }

    @Test
    void testCreateNewAndGetExample() throws Exception {
        // Arrange: se inserta un ejemplo
        Long generatedId = createNewExample("Name", "Surname", "Description");

        // Act & Assert: se pide y se comprueba la respuesta
        mockMvc.perform(get("/api/v1/examples/{id}", generatedId).with(jwt()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Name"))
                .andExpect(jsonPath("$.surname").value("Surname"))
                .andExpect(jsonPath("$.description").value("Description"));
    }

    @Test
    void testListAllExamples() throws Exception {
        createNewExample("Name", "Surname", "Description");
        createNewExample("Name2", "Surname2", "Description2");

        mockMvc.perform(get("/api/v1/examples").with(jwt()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Name"))
                .andExpect(jsonPath("$[1].name").value("Name2"));
    }

    @Test
    void testUpdateExample() throws Exception {
        Long generatedId = createNewExample("Name", "Surname", "Description");

        MyTableRequest updatedDto = new MyTableRequest("NewName", "NewSurname", "NewDescription", null);

        mockMvc.perform(put("/api/v1/examples/" + generatedId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(updatedDto)))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Se comprueba que el cambio llego de verdad a la base de datos, no solo que respondio 204
        mockMvc.perform(get("/api/v1/examples/{id}", generatedId).with(jwt()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"))
                .andExpect(jsonPath("$.surname").value("NewSurname"))
                .andExpect(jsonPath("$.description").value("NewDescription"));
    }

    @Test
    void testDeleteExample() throws Exception {
        Long generatedId = createNewExample("Name", "Surname", "Description");

        mockMvc.perform(delete("/api/v1/examples/" + generatedId).with(jwt()))
                .andDo(print())
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/examples/{id}", generatedId).with(jwt()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

}
