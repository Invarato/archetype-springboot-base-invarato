package ${groupId}.mappers.common;

import ${groupId}.entities.common.BaseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mapstruct.Mapper;
import org.mapstruct.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * Generic Mapper that converts an ID to its entity and vice versa.
 */
@Mapper(componentModel = "spring")
public abstract class ReferenceMapper {
    private static final Logger log = LoggerFactory.getLogger(ReferenceMapper.class);

    @PersistenceContext
    private EntityManager entityManager;

    public <T extends BaseEntity> T fromId(Long id, @TargetType Class<T> entityClass) {
        return Optional.ofNullable(id)
                .map(validId -> findEntityById(validId, entityClass))
                .orElse(null);
    }

    private <T extends BaseEntity> T findEntityById(Long id, Class<T> entityClass) {
        T entity = entityManager.find(entityClass, id);
        return Optional.ofNullable(entity)
                .orElseThrow(() -> {
                    log.error("Entity not found: {} with ID {}", entityClass.getSimpleName(), id);
                    return new EntityNotFoundException(String.format(
                            "Entity not found: %s with ID %s",
                            entityClass.getSimpleName(), id
                    ));
                });
    }

    public <T extends BaseEntity> Long toId(T entity) {
        return entity != null ? entity.getId() : null;
    }

    // Custom exception for more specific error handling
    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }
}