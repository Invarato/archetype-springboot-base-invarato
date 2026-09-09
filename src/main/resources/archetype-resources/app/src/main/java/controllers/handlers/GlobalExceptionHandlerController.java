package ${groupId}.controllers.handlers;

import ${groupId}.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones a respuestas HTTP, en formato {@code application/problem+json}
 * ({@link ProblemDetail}, RFC 9457).
 *
 * <p><b>Por que un formato estandar y no un JSON propio.</b> Un error con forma conocida lo entienden
 * los clientes generados, las pasarelas y las herramientas de monitorizacion sin que nadie les explique
 * nada. Un `{"error": "..."}` inventado obliga a cada consumidor a escribir su propio parseo.</p>
 *
 * <p><b>Lo que NO se hace aqui, a proposito:</b> devolver el mensaje crudo de cualquier excepcion. Un
 * fallo de base de datos puede llevar dentro nombres de tablas, SQL o hasta datos; eso se registra en el
 * log y al cliente se le da algo generico. Por eso el manejador de {@code Exception} no propaga
 * {@code ex.getMessage()}.</p>
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandlerController {

    // ── Errores del cliente ──────────────────────────────────────────────────────────────────

    /**
     * Validacion de {@code @Valid}: se devuelve **campo a campo**.
     *
     * <p>Un 400 que solo dice «peticion invalida» obliga a adivinar. Con el mapa de campos, quien llama
     * puede señalar el formulario exacto que esta mal.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidacion(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, String> errores = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> Objects.requireNonNullElse(f.getDefaultMessage(), "Valor invalido"),
                        // Un campo puede violar varias restricciones; se queda la primera.
                        (primero, segundo) -> primero));

        return construir("Error de validacion", ex, HttpStatus.BAD_REQUEST,
                "Hay %d campo(s) invalido(s)".formatted(errores.size()),
                request, Map.of("errors", errores));
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ProblemDetail> handleNoEncontrado(RuntimeException ex, HttpServletRequest request) {
        return construir("Recurso no encontrado", ex, HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegridad(DataIntegrityViolationException ex,
                                                          HttpServletRequest request) {
        // ⚠️ El mensaje de la causa raiz suele traer el SQL y el nombre de la restriccion. Va al log,
        // no a la respuesta.
        log.warn("Violacion de integridad", ex);
        return construir("Conflicto de datos", ex, HttpStatus.CONFLICT,
                "La operacion entra en conflicto con datos que ya existen", request, Map.of());
    }

    /**
     * Bloqueo optimista: otro proceso modifico el registro entre la lectura y la escritura.
     *
     * <p>Es un {@code 409}, no un {@code 500}: no ha fallado nada, es concurrencia. Y el cliente puede
     * hacer algo util — recargar y reintentar.</p>
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrencia(ObjectOptimisticLockingFailureException ex,
                                                            HttpServletRequest request) {
        return construir("Conflicto de concurrencia", ex, HttpStatus.CONFLICT,
                "Otro proceso ha modificado este recurso. Vuelve a cargarlo e intentalo de nuevo",
                request, Map.of());
    }

    // ── Todo lo demas ────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenerica(Exception ex, HttpServletRequest request) {
        // El detalle va al log (con traza) y al cliente le llega algo generico: el mensaje de una
        // excepcion inesperada puede contener cualquier cosa.
        log.error("Error no controlado", ex);
        return construir("Error interno", ex, HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error inesperado", request, Map.of());
    }

    // ── Construccion de la respuesta ─────────────────────────────────────────────────────────

    private ResponseEntity<ProblemDetail> construir(String titulo,
                                                    Exception ex,
                                                    HttpStatusCode estado,
                                                    String detalle,
                                                    HttpServletRequest request,
                                                    Map<String, Object> propiedades) {
        ErrorResponse.Builder builder = ErrorResponse.builder(ex, estado, detalle)
                .title(titulo)
                .property("timestamp", Instant.now());

        propiedades.forEach(builder::property);

        if (request != null) {
            builder.instance(URI.create(request.getRequestURI()));
            builder.property("path", request.getRequestURI());
        }

        return ResponseEntity.status(estado).body(builder.build().getBody());
    }
}
