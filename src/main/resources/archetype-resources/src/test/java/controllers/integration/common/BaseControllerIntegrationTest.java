package ${groupId}.controllers.integration.common;


import lombok.extern.slf4j.Slf4j;
import ${groupId}.common.ConfigTestContainersTest;
import ${groupId}.common.UtilsTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
public abstract class BaseControllerIntegrationTest extends ConfigTestContainersTest {

    @Autowired
    protected MockMvc mockMvc;

    /**
     * Sends a POST request to the specified URL with the provided body,
     * validates the response status (201 Created) and ensures the existence
     * of the "Location" header in the response. Returns the value of the "Location" header.
     *
     * @param urlTemplatePost the URL to which the POST request will be sent.
     * @param bodyDtoPost     the object that will be serialized and sent as the POST request body.
     * @return the value of the "Location" header from the response.
     * @throws Exception if an error occurs during request execution or validation.
     */
    protected String postCreate(String urlTemplatePost, Object bodyDtoPost) throws Exception {
        MvcResult result = mockMvc.perform(post(urlTemplatePost)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()) // Ensure CSRF is enabled in integration
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UtilsTest.toJson(bodyDtoPost))) // UtilsTest converts objects to JSON
                .andExpect(status().isCreated()) // Check that it was created correctly
                .andExpect(header().exists("Location")) // Validate that it contains Location header
                .andReturn();

        // Extract URL from "Location" header
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location); // Verify that URL is not null

        return location;
    }

    /**
     * Extracts the ID from a URL (post create location) from the provided location header.
     *
     * @param location the location header string from which the ID will be extracted
     * @return the extracted ID as a Long
     */
    protected Long getIdFromLocationHeader(String location) {
        String id = location.substring(location.lastIndexOf("/") + 1);
        assertNotNull(id);
        return Long.parseLong(id);
    }

}
