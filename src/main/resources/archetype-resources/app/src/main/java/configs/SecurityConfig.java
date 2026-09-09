package ${groupId}.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Seguridad de la aplicacion. UNA sola cadena, y activa en TODOS los entornos.
 *
 * <p><b>La regla que ordena todo esto:</b> lo que cambia entre entornos no son las reglas, es de donde
 * salen los tokens. Si las reglas cambiaran por perfil, en local estarias probando una aplicacion
 * distinta de la que despliegas, y los fallos de autorizacion apareceerian en produccion por primera vez.
 * Por eso aqui NO hay ningun perfil que desactive la seguridad, ni en desarrollo ni en los tests.</p>
 *
 * <p>Para trabajar comodo en local, ver {@code DevJwtConfig}: cambia el emisor de tokens, no las reglas.</p>
 */
@Configuration
@EnableWebSecurity
// Habilita @PreAuthorize / @PostAuthorize. Las reglas por URL de aqui abajo son la primera linea, gruesa;
// lo fino (¿es este usuario el dueño del recurso?) se expresa mejor junto al metodo que lo aplica.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        // Sondas de Kubernetes: tienen que responder sin credenciales o el pod nunca
                        // se marca como vivo. Solo health e info, NO todo /actuator: el resto expone
                        // configuracion, metricas y variables de entorno.
                        //
                        // ⚠️ /actuator/prometheus queda AUTENTICADO. Si tu Prometheus no sabe
                        // autenticarse, la solucion no es abrirlo aqui: es publicar actuator en un
                        // puerto de gestion aparte (`management.server.port`) que no salga del cluster.
                        // Abrirlo publicamente regala el mapa de trafico y errores del servicio.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()

                        // Documentacion de la API. Las rutas se abren aqui, pero springdoc solo esta
                        // ENCENDIDO en dev (ver application.yaml): en produccion no hay nada que servir.
                        // Son las dos mitades de la misma decision; si tocas una, mira la otra.
                        .requestMatchers("/docs", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // CORS manda una peticion OPTIONS previa SIN credenciales. Si no se permite,
                        // el navegador recibe un 401 en el preflight y el error que ves es de CORS,
                        // no de autenticacion — y te pasas la tarde mirando donde no es.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Todo lo demas exige autenticacion. Este `anyRequest` va el ULTIMO a proposito:
                        // las reglas se evaluan en orden y la primera que casa gana.
                        .anyRequest().authenticated()
                )

                // Sin sesion: cada peticion trae su credencial. No se crea ni se consulta HttpSession.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ⚠️ CSRF desactivado, y el motivo importa: la credencial viaja en la cabecera
                // `Authorization`, que el navegador NO adjunta solo. Sin envio automatico no hay vector
                // CSRF que proteger.
                //
                // ⚠️ Este razonamiento NO vale si algun dia se anade autenticacion por COOKIE de sesion,
                // httpBasic o mTLS: ahi el navegador si las manda solo y CSRF debe volver a activarse.
                // El snippet `httpBasic() + csrf.disable()` que circula por todas partes es inseguro.
                //
                // ⚠️ Y ojo con el cambio de Spring Boot 4: CSRF pasó a aplicarse tambien a los endpoints
                // de API por defecto. Sin esta linea, TODO POST/PUT/DELETE responderia 403.
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Validacion de JWT estandar (firma, emisor, audiencia y expiracion), en vez de un filtro
                // propio: un filtro JWT escrito a mano es codigo de seguridad sin revisar.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * CORS.
     *
     * <p>Casi todo el dolor de «no me deja llamar a la API desde el front en local» es CORS y no
     * autenticacion: el navegador manda un preflight OPTIONS, le responden que no, y el error que
     * aparece en consola no menciona la palabra CORS por ningun lado.</p>
     *
     * <p>Los origenes se configuran por entorno en {@code app.cors.allowed-origins}. No se usa `*`:
     * con credenciales el navegador lo rechaza, y sin restriccion cualquier web podria llamar a la API
     * desde el navegador de un usuario autenticado.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
