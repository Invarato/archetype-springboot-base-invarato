package ${groupId}.services.unit;

import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.entities.MyTable;
import ${groupId}.exceptions.ResourceNotFoundException;
import ${groupId}.mappers.MyTableMapper;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.services.ExampleService;
import ${groupId}.services.unit.common.BaseServiceUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del servicio: sin Spring, sin base de datos, solo la logica.
 *
 * <p>Se pueden escribir asi porque el servicio recibe sus dependencias por CONSTRUCTOR. Con inyeccion
 * por campo haria falta levantar un contexto para poder probarlo — de ahi que
 * {@code ArchitectureTest} la prohiba.</p>
 */
class ExampleServiceUnitTest extends BaseServiceUnitTest {

    @InjectMocks
    private ExampleService exampleService;

    @Mock
    private MyTableRepository myTableRepository;

    @Mock
    private MyTableMapper myTableMapper;

    @Test
    void devuelveTodos() {
        List<MyTable> entidades = List.of(new MyTable(), new MyTable());
        List<MyTableResponse> esperadas = List.of(
                new MyTableResponse("Name", "Surname", "Description", null),
                new MyTableResponse("Name2", "Surname2", "Description2", null));

        when(myTableRepository.findAll()).thenReturn(entidades);
        when(myTableMapper.toResponses(entidades)).thenReturn(esperadas);

        List<MyTableResponse> ejemplos = exampleService.findAll();

        assertEquals(2, ejemplos.size());
        assertEquals("Name", ejemplos.getFirst().name());
        verify(myTableRepository).findAll();
    }

    @Test
    void creaYGuarda() {
        MyTableRequest peticion = new MyTableRequest("New Example", "Surname", "Description", null);

        MyTable entidad = new MyTable();
        entidad.setName(peticion.name());
        entidad.setSurname(peticion.surname());
        entidad.setDescription(peticion.description());

        MyTable guardada = spy(entidad);
        doReturn(1L).when(guardada).getId();

        when(myTableMapper.toEntity(peticion)).thenReturn(entidad);
        when(myTableRepository.save(any(MyTable.class))).thenReturn(guardada);

        Long nuevoId = exampleService.create(peticion);
        assertEquals(1L, nuevoId);

        // Se comprueba QUE se guarda, no solo que se devolvio un id.
        ArgumentCaptor<MyTable> captor = ArgumentCaptor.forClass(MyTable.class);
        verify(myTableRepository).save(captor.capture());
        assertEquals("New Example", captor.getValue().getName());
        assertEquals("Surname", captor.getValue().getSurname());
        assertEquals("Description", captor.getValue().getDescription());
    }

    @Test
    void buscaPorId() {
        MyTable entidad = new MyTable();
        MyTableResponse esperada = new MyTableResponse("Name", "Surname", "Description", null);

        when(myTableRepository.findById(1L)).thenReturn(Optional.of(entidad));
        when(myTableMapper.toResponse(entidad)).thenReturn(esperada);

        assertEquals("Name", exampleService.findById(1L).name());
        verify(myTableRepository).findById(1L);
    }

    @Test
    void buscarInexistenteFalla() {
        when(myTableRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> exampleService.findById(1L));
        verify(myTableRepository).findById(1L);
    }

    @Test
    void borra() {
        MyTable entidad = new MyTable();
        when(myTableRepository.findById(1L)).thenReturn(Optional.of(entidad));

        exampleService.delete(1L);

        verify(myTableRepository).findById(1L);
        verify(myTableRepository).delete(entidad);
    }

    @Test
    void borrarInexistenteFalla() {
        when(myTableRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> exampleService.delete(1L));
        // Y sobre todo: no se borra nada si no existia.
        verify(myTableRepository, never()).delete(any());
    }

}
