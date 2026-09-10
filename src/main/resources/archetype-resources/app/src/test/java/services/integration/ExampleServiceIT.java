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

/**
 * Tests de integracion del servicio: contra una base de datos real (efimera, via Testcontainers).
 *
 * <p>El servicio devuelve DTOs, no entidades. El repositorio se usa aqui solo para <b>comprobar</b> lo
 * que quedo guardado, que es justo lo que un test de integracion debe verificar: no que el metodo
 * respondio, sino que el dato esta.</p>
 */
class ExampleServiceIT extends BaseServiceIT {

    @Autowired
    private ExampleService exampleService;

    @Autowired
    private MyTableRepository myTableRepository;

    @Test
    void devuelveTodos() {
        exampleService.create(new MyTableRequest("New Example", "Surname", "Description", null));

        List<MyTableResponse> ejemplos = exampleService.findAll();

        assertNotNull(ejemplos);
        assertEquals(1, ejemplos.size());
        assertEquals("New Example", ejemplos.getFirst().name());
    }

    @Test
    void creaYGuarda() {
        Long nuevoId = exampleService.create(
                new MyTableRequest("Saved Example", "Surname", "Description", null));

        // Se comprueba contra la base de datos, no contra lo que devolvio el servicio.
        MyTable guardada = myTableRepository.findById(nuevoId).orElse(null);
        assertNotNull(guardada);
        assertEquals("Saved Example", guardada.getName());
    }

    @Test
    void buscaPorId() {
        Long id = exampleService.create(new MyTableRequest("Uno", "Dos", "Tres", null));

        MyTableResponse encontrado = exampleService.findById(id);

        assertEquals("Uno", encontrado.name());
        assertEquals("Dos", encontrado.surname());
        assertEquals("Tres", encontrado.description());
    }

    @Test
    void actualiza() {
        Long id = exampleService.create(new MyTableRequest("Antes", "A", "D", null));

        exampleService.update(id, new MyTableRequest("Despues", "B", "E", null));

        assertEquals("Despues", exampleService.findById(id).name());
    }

    @Test
    void borra() {
        Long id = exampleService.create(new MyTableRequest("Para borrar", null, null, null));

        exampleService.delete(id);

        Optional<MyTable> borrada = myTableRepository.findById(id);
        assertTrue(borrada.isEmpty());
    }

}
