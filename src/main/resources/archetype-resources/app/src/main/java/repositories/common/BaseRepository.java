package ${groupId}.repositories.common;

import ${groupId}.entities.common.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Extiende de:
 *  * PagingAndSortingRepository
 *  * CrudRepository:
 *      * save(…): Save a specific entity.
 *      * findAll(): Retrieve all entities.
 *      * findById(ID id): Search for an entity by its ID.
 *      * count(): Get the number of stored entities.
 *      * delete(…): Delete a specific entity.
 *      * deleteAll(): Delete all entities.
 *
 *  Additional:
 *   * flush(): Immediately save all pending entities for persistence in storage.
 *   * deleteInBatch(…): Delete a batch of entities in a single query.
 *   * deleteAllInBatch(): Delete all entities at once.
 *   * getOne(ID id): Get an entity reference (can return a proxy reference without hitting the database).
 *   * findAll(Example example): Retrieve entities whose state matches the Example.
 *
 */
@Repository
public interface BaseRepository<T extends BaseEntity> extends JpaRepository<T, Long> {

}