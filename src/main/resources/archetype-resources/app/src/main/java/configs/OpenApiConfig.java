package ${groupId}.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadatos del contrato OpenAPI.
 *
 * <p>Lo que se declara aqui acaba en {@code contract/src/main/resources/openapi/openapi.json} y, desde
 * ahi, en los clientes generados. No es documentacion decorativa: es lo que los consumidores compilan.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI openApi(@Value("${spring.application.name}") String nombreAplicacion) {
        return new OpenAPI()
                .info(new Info()
                        .title(nombreAplicacion + " API")
                        .version("v1"))

                // ⚠️ Servidor RELATIVO, no una URL absoluta.
                // Springdoc, si no se le dice nada, escribe la URL desde la que se genero el contrato —
                // que al generarse en un test es `http://localhost`. Un cliente generado a partir de ese
                // contrato apuntaria a localhost por defecto, y el fallo aparece en el consumidor, lejos
                // de aqui. Con "/" cada cliente pone la suya.
                .servers(List.of(new Server().url("/")))

                // Que el contrato diga COMO se autentica. Sin esto, un cliente generado no sabe que hay
                // que mandar un bearer token y el primer 401 parece un fallo del servidor.
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
