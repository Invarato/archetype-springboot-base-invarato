package ${groupId}.entities.common;

import ${groupId}.entities.common.IdLongBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;


/**
 * Clase base abstracta para todas las entidades en el modelo de datos.
 *
 * <p>Proporciona un identificador único {@code id} común para todas las entidades que
 * extiendan esta clase. Está anotada como {@link MappedSuperclass}, lo que significa que
 * su mapeo no será persistente por sí mismo, pero sus campos se heredarán en las entidades
 * concretas que la extiendan.</p>
 *
 * <p>La anotación de Lombok {@link Getter} genera automáticamente los métodos getters
 * para el atributo {@code id}.</p>
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity implements IdLongBaseEntity {

    /**
     * Identificador único de la entidad.
     *
     * <p>Marcado como clave primaria mediante {@link Id} y configurado para ser generado
     * automáticamente en la base de datos usando {@link GenerationType#IDENTITY}.</p>
     */
    @Id
    @Column(name="id", unique=true, nullable=false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

}