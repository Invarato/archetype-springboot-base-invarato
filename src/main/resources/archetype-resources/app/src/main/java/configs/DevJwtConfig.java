package ${groupId}.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Emisor de tokens para desarrollo y tests.
 *
 * <p><b>Esto es lo unico que cambia entre local y produccion.</b> Las reglas de seguridad
 * ({@code SecurityConfig}) son EXACTAMENTE las mismas en los dos sitios: misma cadena, mismos
 * requisitos, misma respuesta ante un token invalido. Lo que varia es de donde sale el token:</p>
 *
 * <ul>
 *   <li><b>dev / test:</b> tokens firmados con un secreto local (HS256). No hace falta ningun servidor
 *       de identidad levantado para trabajar. {@code make token} acuña uno.</li>
 *   <li><b>Resto de entornos:</b> este bean no existe, y Spring configura el decodificador a partir de
 *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} — el emisor de verdad, con sus
 *       claves publicas y su rotacion.</li>
 * </ul>
 *
 * <p>Por que asi y no con un perfil que apague la seguridad: si en local no hay seguridad, los fallos de
 * autorizacion se descubren en el primer despliegue, que es el peor momento y el mas caro.</p>
 *
 * <p>⚠️ El secreto de dev es de mentira y esta en el repositorio A PROPOSITO: no protege nada, solo
 * permite firmar en local. Si alguna vez aparece un {@code app.dev.jwt-secret} en la configuracion de un
 * entorno real, es un error grave.</p>
 */
@Configuration
@Profile({"dev", "test"})
public class DevJwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.dev.jwt-secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
