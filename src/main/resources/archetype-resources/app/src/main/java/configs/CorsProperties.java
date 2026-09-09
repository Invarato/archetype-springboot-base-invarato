package ${groupId}.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origenes permitidos para CORS, configurables por entorno en {@code app.cors.allowed-origins}.
 *
 * <p>Un record con {@link ConfigurationProperties} en vez de {@code @Value} sueltos: la configuracion
 * queda tipada, validada al arrancar y visible en un solo sitio.</p>
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        // Sin origenes configurados, la lista vacia deja CORS cerrado. Es el defecto correcto: que haya
        // que declarar quien puede llamar, en vez de abrirlo y confiar en acordarse de cerrarlo.
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
