package ${groupId}.services.integration;


import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.entities.MyTable;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.services.ExampleService;
import ${groupId}.services.integration.common.BaseServiceIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


class ExampleServiceIT extends BaseServiceIT {

    @Autowired
    private ExampleService ExampleService;

    @Autowired
    private MyTableRepository MyTableRepository;

    @Test
    void testGetAllEjemplos() {
        MyTableRequest request = new MyTableRequest(
                "New Example",
                "Surname",
                "Description",
                null
        );

        ExampleService.saveSimple(request);

        // Execute the function
        List<MyTable> examples = ExampleService.getAllEjemplos();

        // Validate results
        assertNotNull(examples);
        assertEquals(1, examples.size());
        assertEquals("New Example", examples.get(0).getName());
    }

    @Test
    void testsaveSimple() {
        // Prepare the DTO
        MyTableRequest request = new MyTableRequest(
                "Saved Example",
                "Surname",
                "Description",
                null
        );

        // Execute the function 
        Long newId = ExampleService.saveSimple(request);

        // Verify it was saved
        MyTable MyTable = MyTableRepository.findById(newId).orElse(null);
        assertNotNull(MyTable);
        assertEquals("Saved Example", MyTable.getName());
    }

    @Test
    void testDeleteEjemploById() {
        // Add a record to delete
        MyTable MyTable = new MyTable();
        MyTable.setName("Record to delete");
        MyTable savedTable = MyTableRepository.save(MyTable);
        MyTableRepository.flush();

        // Delete the record
        ExampleService.deleteEjemploById(savedTable.getId());

        // Verify it no longer exists
        Optional<MyTable> deletedTable = MyTableRepository.findById(savedTable.getId());
        assertTrue(deletedTable.isEmpty());
    }

    @Test
    void testGetAllEjemploResponses() {
        MyTableRequest request = new MyTableRequest(
                "New Example",
                "Surname",
                "Description",
                null
        );

        ExampleService.saveSimple(request);

        // Execute the function
        List<MyTableResponse> examples = ExampleService.getAllEjemploResponses();

        // Validate results
        assertNotNull(examples);
        assertEquals(1, examples.size());
        assertEquals("New Example", examples.getFirst().name());
    }

}