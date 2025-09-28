package ${groupId}.controllers.unit;

import ${groupId}.common.UtilsTest;
import ${groupId}.controllers.ExampleController;
import ${groupId}.controllers.unit.common.BaseControllerUnitTest;
import ${groupId}.dtos.responses.SimpleApiResponse;
import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.entities.MyTable;
import ${groupId}.services.ExampleService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExampleController.class)
class ExampleControllerUnitTest extends BaseControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExampleService ExampleService;

    @Test
    void testSayHello() throws Exception {
        mockMvc.perform(get("/api/v1/examples/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    @Test
    void testSayHelloDto() throws Exception {
        when(ExampleService.getHelloDto()).thenReturn(new SimpleApiResponse("Hello World DTO"));

        mockMvc.perform(get("/api/v1/examples/helloDto"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello World DTO"));
    }

    @Test
    void testCreateNew() throws Exception {
        MyTableRequest mockDto = new MyTableRequest(
                "Name",
                "Surname",
                "Description",
                null
        );

        long generatedId = 1L;

        when(ExampleService.saveSimple(Mockito.any(MyTableRequest.class))).thenReturn(generatedId);

        mockMvc.perform(post("/api/v1/examples")
                        .header("Host", "localhost")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(mockDto)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", "http://localhost/api/v1/examples/" + generatedId));
    }

    @Test
    void testListAllEjemplos() throws Exception {
        MyTable mockEntity = new MyTable();
        mockEntity.setName("Name");
        mockEntity.setSurname("Surname");
        mockEntity.setDescription("Description");

        MyTable mockEntity2 = new MyTable();
        mockEntity2.setName("Name2");
        mockEntity2.setSurname("Surname2");
        mockEntity2.setDescription("Description2");

        List<MyTable> examples = Arrays.asList(
                mockEntity,
                mockEntity2
        );

        when(ExampleService.getAllEjemplos()).thenReturn(examples);

        mockMvc.perform(get("/api/v1/examples"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Name"))
                .andExpect(jsonPath("$[1].name").value("Name2"));
    }

    @Test
    void testGetEjemplo() throws Exception {
        MyTable mockEntity = new MyTable();
        mockEntity.setName("Name");
        mockEntity.setSurname("Surname");
        mockEntity.setDescription("Description");

        when(ExampleService.getEjemploById(1L)).thenReturn(mockEntity);

        mockMvc.perform(get("/api/v1/examples/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Name"))
                .andExpect(jsonPath("$.surname").value("Surname"))
                .andExpect(jsonPath("$.description").value("Description"));
    }

    @Test
    void testUpdateEjemplo() throws Exception {
        MyTableRequest mockDto = new MyTableRequest(
                "Name",
                "Surname",
                "Description",
                null
        );

        Mockito.doNothing().when(ExampleService).updateEjemploById(1L, mockDto);

        mockMvc.perform(put("/api/v1/examples/1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(mockDto)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteEjemplo() throws Exception {
        Mockito.doNothing().when(ExampleService).deleteEjemploById(1L);

        mockMvc.perform(delete("/api/v1/examples/1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

}