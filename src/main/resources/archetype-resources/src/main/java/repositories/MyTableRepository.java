package ${groupId}.repositories;


import ${groupId}.entities.MyTable;
import ${groupId}.repositories.common.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;


public interface MyTableRepository extends BaseRepository<MyTable> {

    @Query("SELECT t FROM MyTable t WHERE t.id = :id")
    MyTable findByIdPersonalizado(@Param("id") Long id);

    Optional<MyTable> findByName(String name);

}