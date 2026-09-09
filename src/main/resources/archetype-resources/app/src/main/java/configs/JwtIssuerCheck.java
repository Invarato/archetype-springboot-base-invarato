package ${groupId}.configs;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Comprueba al arrancar que hay un emisor de tokens configurado.
 *
 * <p>Sin esto, olvidar {@code OAUTH2_ISSUER_URI} en un despliegue produce este mensaje:</p>
 *
 * <pre>
 *   Parameter 0 of method setFilterChains ... required a bean of type
 *   'org.springframework.security.oauth2.jwt.JwtDecoder' that could not be found.
 *   Action: Consider defining a bean of type 'JwtDecoder' in your configuration.
 * </pre>
 *
 * <p>Que es cierto y no sirve de nada: manda a definir un bean cuando lo que falta es una variable de
 * entorno. Alguien desplegando pierde la tarde buscando en el sitio equivocado.</p>
 *
 * <p>No se activa en {@code dev} ni {@code test}, donde el emisor es local ({@code DevJwtConfig}).</p>
 */
@Configuration
@Profile("!dev & !test")
public class JwtIssuerCheck implements InitializingBean {

    private final String issuerUri;

    // ⚠️ El #[[...]]# es para el arquetipo, no para Spring: los .java tambien pasan por Velocity al
    // generarse, y una referencia con valor por defecto (los dos puntos finales) rompe la generacion.
    // Al proyecto generado llega la anotacion limpia.
    public JwtIssuerCheck(@Value("#[[${spring.security.oauth2.resourceserver.jwt.issuer-uri:}]]#") String issuerUri) {
        this.issuerUri = issuerUri;
    }

    @Override
    public void afterPropertiesSet() {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("""
                    Falta el emisor de tokens: la variable de entorno OAUTH2_ISSUER_URI esta vacia.

                    Esta aplicacion valida JWT contra un emisor OAuth2/OIDC, y fuera de los perfiles dev y
                    test no hay ninguno por defecto — a proposito: arrancar sin saber quien firma los
                    tokens seria peor que no arrancar.

                    Que hacer:
                      - En un despliegue: define OAUTH2_ISSUER_URI con la URL de tu emisor
                        (por ejemplo https://login.example.com/realms/mi-realm).
                      - Para trabajar en local: arranca con el perfil dev (`make run`) y consigue un
                        token con `make token`.
                    """);
        }
    }
}
