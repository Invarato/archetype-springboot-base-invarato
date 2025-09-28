package ${groupId}.controllers.integration;

import ${groupId}.common.UtilsTest;
import ${groupId}.controllers.integration.common.BaseControllerIntegrationTest;
import ${groupId}.dtos.requests.MyTableRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class ExampleControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Test
    @WithMockUser(authorities = {"ROLE_USER"})
    void testSayHello() throws Exception {
        mockMvc.perform(get("/api/v1/examples/hello"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_USER"})
    void testSayHelloDto() throws Exception {
        mockMvc.perform(get("/api/v1/examples/helloDto"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello World DTO"));
    }


// TODO the following testCreateNew is the same as:
//   @Autowired
//   private MyTableRepository MyTableRepository;
//    @Test
//    @WithMockUser(authorities = {"ROLE_USER"})
//    void testCreateNew() throws Exception {
//        MyTableRequest mockDto = new MyTableRequest(
//                "Name",
//                "Surname", 
//                "Description"
//        );
//        mockMvc.perform(post("/api/v1/examples")
//                .with(SecurityMockMvcRequestPostProcessors.csrf())
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(UtilsTest.toJson(mockDto))) // UtilsTest converts objects to JSON
//                .andDo(print())
//                .andExpect(status().isCreated()) // Check HTTP 201 Created status
//                .andExpect(header().exists("Location"));
//        MyTable persistedEntity = MyTableRepository.findByName("Name").orElse(null);
//        assertNotNull(persistedEntity);
//        assertEquals("Description", persistedEntity.getDescription());
//    }

    private Long createNewExample(String name, String surname, String description) throws Exception {
        MyTableRequest mockDto = new MyTableRequest(name, surname, description, null);
        String location = this.postCreate("/api/v1/examples", mockDto);
        return this.getIdFromLocationHeader(location);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_USER"})
    void testCreateNewAndGetExample() throws Exception {
        // Arrange: Insert an example in the database
        Long generatedId = createNewExample(
                "Name",
                "Surname",
                "Description"
        );

        // Act & Assert: Call GET and verify response
        mockMvc.perform(get("/api/v1/examples/{id}", generatedId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Name"))
                .andExpect(jsonPath("$.surname").value("Surname"))
                .andExpect(jsonPath("$.description").value("Description"));
    }


    @Test
    @WithMockUser(authorities = {"ROLE_USER"})
    void testListAllExamples() throws Exception {
        // Arrange: Insert examples in the database
        createNewExample(
                "Name",
                "Surname",
                "Description"
        );
        createNewExample(
                "Name2",
                "Surname2",
                "Description2"
        );

        // Act & Assert: Call GET and verify response
        mockMvc.perform(get("/api/v1/examples"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)) // Validate there are 2 records
                .andExpect(jsonPath("$[0].name").value("Name")) // Verify first record content
                .andExpect(jsonPath("$[1].name").value("Name2"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_USER"})
    void testUpdateExample() throws Exception {
        // Arrange: Insert an example in the database
        Long generatedId = createNewExample(
                "Name",
                "Surname",
                "Description"
        );

        // Create DTO with updated data
        MyTableRequest updatedDto = new MyTableRequest("NewName", "NewSurname", "NewDescription", null);

        // Act: Call PUT endpoint and verify no content returned
        mockMvc.perform(put("/api/v1/examples/" + generatedId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(updatedDto)))
                .andDo(print())
                .andExpect(status().isNoContent()); // Verify status is 204 (no content)

        // Assert: Validate data was actually updated in database
        mockMvc.perform(get("/api/v1/examples/{id}", generatedId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"))
                .andExpect(jsonPath("$.surname").value("NewSurname"))
                .andExpect(jsonPath("$.description").value("NewDescription"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_USER", "ROLE_ADMIN"})
    void testDeleteExample() throws Exception {
        // Arrange: Insert an example in the database
        Long generatedId = createNewExample(
                "Name",
                "Surname",
                "Description"
        );

        // Act: Call DELETE endpoint to remove entity
        mockMvc.perform(delete("/api/v1/examples/" + generatedId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isNoContent()); // Verify status is 204

        // Assert: Validate entity was deleted
        mockMvc.perform(get("/api/v1/examples/{id}", generatedId))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

}