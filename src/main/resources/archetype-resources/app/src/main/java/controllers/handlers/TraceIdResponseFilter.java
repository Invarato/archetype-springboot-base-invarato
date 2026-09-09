package ${groupId}.controllers.handlers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Devuelve el identificador de traza en la cabecera {@code X-Trace-Id}.
 *
 * <p><b>Por que importa mas de lo que parece.</b> Cuando alguien reporta «me ha dado un error», sin esto
 * empieza la arqueologia: buscar por hora aproximada entre los logs de todas las replicas. Con la
 * cabecera, quien reporta el fallo trae consigo la clave exacta para encontrar sus lineas de log —
 * incluso a traves de varios servicios, porque el mismo traceId viaja con la peticion.</p>
 *
 * <p>Encaja con el patron de log de {@code logback-spring.xml}, que ya imprime {@code traceId} y
 * {@code spanId}.</p>
 *
 * <p>El valor sale del MDC, que rellena la instrumentacion de trazas. Si no hay tracing activo el MDC
 * viene vacio y el filtro no añade nada: no estorba y no hay que recordar quitarlo.</p>
 */
@Component
public class TraceIdResponseFilter extends OncePerRequestFilter {

    private static final String CLAVE_MDC = "traceId";
    private static final String CABECERA = "X-Trace-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = MDC.get(CLAVE_MDC);
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader(CABECERA, traceId);
        }
        filterChain.doFilter(request, response);
    }
}
