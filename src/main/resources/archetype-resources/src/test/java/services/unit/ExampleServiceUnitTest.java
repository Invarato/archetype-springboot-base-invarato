package ${groupId}.services.unit;


import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.entities.MyTable;
import ${groupId}.mappers.MyTableMapper;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.services.ExampleService;
import ${groupId}.services.unit.common.BaseServiceUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class ExampleServiceUnitTest extends BaseServiceUnitTest {

    @InjectMocks
    private ExampleService ExampleService;

    @Mock
    private MyTableRepository MyTableRepository;

    @Mock
    private MyTableMapper MyTableMapper;

    @Test
    void testGetAllEjemplos() {
        // Simulate the repository response
        MyTable mockEntity1 = new MyTable();
        mockEntity1.setName("Name");
        mockEntity1.setSurname("Surname");
        mockEntity1.setDescription("Description");

        MyTable mockEntity2 = new MyTable();
        mockEntity2.setName("Name2");
        mockEntity2.setSurname("Surname2");
        mockEntity2.setDescription("Description2");

        when(MyTableRepository.findAll()).thenReturn(List.of(mockEntity1, mockEntity2));

        // Execute the function
        List<MyTable> examples = ExampleService.getAllEjemplos();

        // Validate the response
        assertNotNull(examples);
        assertEquals(2, examples.size());
        verify(MyTableRepository, times(1)).findAll();
    }

    @Test
    void testsaveSimple() {
        // Simulated data
        MyTableRequest request = new MyTableRequest(
                "New Example",
                "Surname",
                "Description",
                null
        );

        MyTable mockEntity = spy(new MyTable()); // Spy the instance to customize methods
        doReturn(1L).when(mockEntity).getId();   // Simulate that ID is "1"

        // Set properties of the previously simulated entity
        mockEntity.setName(request.name());
        mockEntity.setSurname(request.surname());
        mockEntity.setDescription(request.description());

        when(MyTableRepository.save(any(MyTable.class))).thenReturn(mockEntity);
        when(MyTableMapper.toEntity(request)).thenReturn(mockEntity);

        // Execute and verify
        Long newId = ExampleService.saveSimple(request);
        assertEquals(1L, newId);

        // Capture the actual arguments passed to security service
        ArgumentCaptor<MyTable> tableCaptor = ArgumentCaptor.forClass(MyTable.class);

        // Verify that repository received a MyTable object
        verify(MyTableRepository).save(tableCaptor.capture());

        // Validate that captured object is correct
        MyTable capturedEntity = tableCaptor.getValue();
        assertEquals("New Example", capturedEntity.getName());
        assertEquals("Surname", capturedEntity.getSurname());
        assertEquals("Description", capturedEntity.getDescription());
    }

    @Test
    void testDeleteEjemploById() {
        // Configure mock
        MyTable mockEntity = new MyTable();
        mockEntity.setName("Name");
        mockEntity.setSurname("Surname");
        mockEntity.setDescription("Description");

        Long id = 1L;

        when(MyTableRepository.findById(id)).thenReturn(Optional.of(mockEntity));

        // Execute function
        ExampleService.deleteEjemploById(id);

        // Verify interaction
        verify(MyTableRepository, times(1)).findById(id);
        verify(MyTableRepository, times(1)).delete(any(MyTable.class));
    }

    @Test
    void testGetEjemploById_Success() {
        // Create MyTable mock with assigned ID
        MyTable MyTable = mock(MyTable.class);
        when(MyTable.getId()).thenReturn(1L); // Configure ID with getter

        // Simulate MyTableRepository.save() behavior
        when(MyTableRepository.save(any(MyTable.class))).thenReturn(MyTable); // Mock repository returns this object

        // Save object and get generated ID
        Long generatedId = MyTableRepository.save(new MyTable()).getId();

        // Now configure findById() behavior
        when(MyTableRepository.findById(generatedId)).thenReturn(Optional.of(MyTable));

        // Invoke service
        MyTable result = ExampleService.getEjemploById(generatedId);

        // Verify results
        assertNotNull(result);
        assertEquals(generatedId, result.getId());
        verify(MyTableRepository, times(1)).findById(generatedId);
    }

    @Test
    void testGetEjemploById_NotFound() {
        // Simulate record does not exist
        Long id = 1L;
        when(MyTableRepository.findById(id)).thenReturn(Optional.empty());

        // Invoke function and expect exception
        assertThrows(NoSuchElementException.class, () -> ExampleService.getEjemploById(id));
        verify(MyTableRepository, times(1)).findById(id);
    }

}