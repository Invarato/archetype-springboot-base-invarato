package ${groupId}.repositories.integration;

import ${groupId}.entities.MyTable;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.repositories.integration.common.BaseRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


class ExampleRepositoryIntegrationTest extends BaseRepositoryIntegrationTest {

    @Autowired
    private MyTableRepository MyTableRepository;


    @Test
    void testFindByName() {
        MyTable mockEntity = new MyTable();
        mockEntity.setName("Name");
        mockEntity.setSurname("Surname");
        mockEntity.setDescription("Description");
        MyTableRepository.save(mockEntity);

        Optional<MyTable> persistedEntityOpt = MyTableRepository.findByName("Name");
        assertTrue(persistedEntityOpt.isPresent());

        MyTable persistedEntity = persistedEntityOpt.get();
        assertNotNull(persistedEntity);
        assertEquals("Description", persistedEntity.getDescription());
    }

    @Test
    void testFindAll() {
        MyTable mockEntity = new MyTable();
        mockEntity.setName("Name");
        mockEntity.setSurname("Surname");
        mockEntity.setDescription("Description");
        MyTableRepository.save(mockEntity);

        MyTable mockEntity2 = new MyTable();
        mockEntity2.setName("Name2");
        mockEntity2.setSurname("Surname2");
        mockEntity2.setDescription("Description2");
        MyTableRepository.save(mockEntity2);

        List<MyTable> misTablas = MyTableRepository.findAll();
        assertNotNull(misTablas);
        assertThat(misTablas).hasSize(2);
    }

}