package ${groupId}.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Activa la auditoría de JPA y dice <b>quién</b> está haciendo cada cambio.
 *
 * <p>Sin esto, las anotaciones {@code @CreatedDate} y {@code @CreatedBy} de {@code BaseEntity} no hacen
 * nada. Y no fallan: dejan las columnas a {@code null}, que es peor, porque parece que funciona hasta
 * que alguien mira los datos.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
public class JpaAuditingConfig {

    /** Valor que se registra cuando el cambio no lo hace una persona (arranque, tareas programadas). */
    private static final String SISTEMA = "SYSTEM";

    /**
     * De dónde sale el nombre que se guarda en {@code created_by} / {@code updated_by}.
     *
     * <p>Se toma del contexto de seguridad, así que es exactamente el sujeto del token que hizo la
     * petición. Encaja con la seguridad de este proyecto, donde <b>siempre</b> hay autenticación en las
     * rutas de negocio.</p>
     */
    @Bean
    public AuditorAware<String> auditorActual() {
        return () -> {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

            // Sin autenticación no significa «error»: hay escrituras legítimas fuera de una peticion —
            // migraciones de datos, tareas programadas, el arranque. Se marcan como SYSTEM en vez de
            // dejar la columna vacia, para que la auditoria no tenga huecos sin explicacion.
            boolean hayUsuario = autenticacion != null
                    && autenticacion.isAuthenticated()
                    && !"anonymousUser".equals(autenticacion.getPrincipal());

            return Optional.of(hayUsuario ? autenticacion.getName() : SISTEMA);
        };
    }
}
