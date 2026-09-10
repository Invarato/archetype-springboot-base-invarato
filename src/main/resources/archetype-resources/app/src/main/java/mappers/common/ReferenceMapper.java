package ${groupId}.mappers.common;

import ${groupId}.entities.common.BaseEntity;
import ${groupId}.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mapstruct.Mapper;
import org.mapstruct.TargetType;

import java.util.Optional;

/**
 * Convierte un identificador en su entidad y viceversa, para los mapeos de relaciones.
 *
 * <p>Es lo que permite que un DTO lleve {@code myTableParentId} y la entidad tenga
 * {@code myTableParent}: MapStruct usa este mapper —declarado en {@code BaseMapperConfig}— para hacer
 * la traducción en los dos sentidos sin que haya que escribirla en cada mapper.</p>
 */
@Mapper(componentModel = "spring")
public abstract class ReferenceMapper {

    // @PersistenceContext y no inyeccion por constructor: el EntityManager NO es un bean normal, es un
    // proxy ligado a la transaccion en curso, y esta anotacion es la forma estandar de obtenerlo.
    // (Por eso ArchitectureTest prohibe @Autowired en campos y no esta: son cosas distintas.)
    @PersistenceContext
    private EntityManager entityManager;

    public <T extends BaseEntity> T fromId(Long id, @TargetType Class<T> entityClass) {
        // Un id nulo significa «sin relacion», no un error: es lo que llega cuando el DTO no trae padre.
        return Optional.ofNullable(id)
                .map(idValido -> buscarOFallar(idValido, entityClass))
                .orElse(null);
    }

    public <T extends BaseEntity> Long toId(T entity) {
        return entity != null ? entity.getId() : null;
    }

    private <T extends BaseEntity> T buscarOFallar(Long id, Class<T> entityClass) {
        // `find` y no `getReference`: se quiere saber AHORA si el id existe. Con una referencia
        // perezosa, un id inexistente no falla aqui — falla mucho despues, al tocar el objeto, con una
        // excepcion que ya no dice quien lo pidio.
        T entidad = entityManager.find(entityClass, id);
        if (entidad == null) {
            // La excepcion del proyecto, que el manejador global traduce a un 404. Antes habia aqui una
            // clase anidada propia llamada EntityNotFoundException, que ademas sombreaba a la de JPA:
            // dos tipos con el mismo nombre y significados distintos en el mismo codigo.
            throw ResourceNotFoundException.of(entityClass.getSimpleName(), id);
        }
        return entidad;
    }
}
