package ${groupId}.entities.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base de todas las entidades: identificador, auditoría y control de concurrencia.
 *
 * <p>Va aquí y no en cada entidad porque son las tres cosas que <b>se necesitan siempre y son muy caras
 * de añadir después</b>: cuando la tabla ya tiene millones de filas, incorporar columnas de auditoría es
 * una migración con relleno masivo, y la historia que no se registró no se recupera.</p>
 */
@MappedSuperclass
@Getter
// Es quien rellena las cuatro columnas de auditoria al persistir y al actualizar. Sin este listener las
// anotaciones @CreatedDate y compania no hacen nada: no fallan, simplemente dejan los campos a null.
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Auditoría: quién y cuándo ────────────────────────────────────────────────────────────
    // Responde a «¿quién tocó esto y cuándo?», que es la primera pregunta cuando algo aparece mal en
    // producción. `Instant` y no `LocalDateTime`: un instante no depende de la zona horaria del
    // servidor, así que no cambia de significado al desplegar en otra región ni al cambiar la hora.

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    // ── Concurrencia: bloqueo optimista ──────────────────────────────────────────────────────
    /**
     * Versión del registro, para detectar modificaciones concurrentes.
     *
     * <p>Resuelve la <i>actualización perdida</i>: dos procesos leen la misma fila, los dos la
     * modifican, y el segundo pisa el cambio del primero sin que nadie se entere. Con esto, el segundo
     * falla y puede recargar y reintentar.</p>
     *
     * <p>Hibernate la incrementa sola. El error resultante lo traduce el manejador global a un
     * <b>409</b>, que es lo correcto: no ha fallado nada, es concurrencia.</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ⚠️ Lo que NO se pone aquí, y es una decisión: un `activo`/`borrado` para borrado lógico.
    //
    // Puesto en la clase base, obliga a que TODAS las consultas del sistema recuerden filtrarlo.
    // Olvidarlo una vez devuelve datos borrados; y una restricción de unicidad sobre filas «borradas»
    // impide volver a crear algo con la misma clave, con un error que no menciona el borrado por
    // ninguna parte. El borrado lógico es una decisión por entidad y con sus consultas pensadas, no
    // algo que se hereda sin querer.
}
