package ${groupId}.controllers.handlers;

import ${groupId}.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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

    /**
     * Cuerpo que no se puede leer: JSON mal formado, un tipo que no encaja, un campo con basura.
     *
     * <p>Sin esto caia en el manejador genérico y respondia <b>500</b>, diciéndole a quien llama que el
     * fallo es del servidor cuando en realidad mandó algo que no es JSON válido. Lo encontró
     * Schemathesis generando cuerpos a partir del contrato.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleCuerpoIlegible(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        // El mensaje de Jackson dice posición, clase y a veces parte del contenido recibido: al log, no
        // a la respuesta.
        log.warn("Cuerpo de peticion ilegible", ex);
        return construir("Peticion mal formada", ex, HttpStatus.BAD_REQUEST,
                "El cuerpo de la peticion no se puede leer: revisa que sea JSON valido y que los tipos "
                        + "coincidan con el contrato", request, Map.of());
    }

    /**
     * Un parámetro que no se puede convertir al tipo esperado.
     *
     * <p>El caso que lo destapó: {@code GET /api/v1/examples/-9223372036854775808}. Ese número no cabe
     * en un {@code Long}, Spring no puede convertirlo y saltaba un {@code NumberFormatException} hasta
     * el manejador genérico — <b>500</b> por un id que el cliente escribió mal.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTipoInvalido(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        String tipoEsperado = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "otro tipo";
        return construir("Parametro invalido", ex, HttpStatus.BAD_REQUEST,
                "El parametro '%s' no es un %s valido".formatted(ex.getName(), tipoEsperado),
                request, Map.of());
    }

    /**
     * Método HTTP que esa ruta no admite: {@code PUT} sobre una colección, por ejemplo.
     *
     * <p>Es un <b>405</b> de manual, y sin este manejador salía <b>500</b>: la API se acusaba a sí misma
     * de un fallo interno porque alguien usó el verbo equivocado. Además se devuelve la cabecera
     * {@code Allow}, que es la que dice qué métodos sí valen — sin ella, quien llama tiene que
     * adivinar.</p>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMetodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest request) {
        ResponseEntity<ProblemDetail> respuesta = construir("Metodo no permitido", ex,
                HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo %s no esta permitido en esta ruta".formatted(ex.getMethod()),
                request, Map.of());

        Set<HttpMethod> permitidos = ex.getSupportedHttpMethods();
        if (permitidos == null || permitidos.isEmpty()) {
            return respuesta;
        }
        return ResponseEntity.status(respuesta.getStatusCode())
                .headers(cabeceras -> cabeceras.setAllow(permitidos))
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(respuesta.getBody());
    }

    /**
     * Ordenar por un campo que no existe: {@code ?sort=campoInventado,asc}.
     *
     * <p>Spring Data lanza {@code PropertyReferenceException} y, sin este manejador, salía <b>500</b> —
     * un error del servidor por un parámetro que escribió el cliente. Es <b>400</b>, y el mensaje dice
     * qué propiedad no existe para que se pueda corregir sin adivinar.</p>
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handlePropiedadInexistente(PropertyReferenceException ex,
                                                                    HttpServletRequest request) {
        return construir("Parametro invalido", ex, HttpStatus.BAD_REQUEST,
                "No existe la propiedad '%s' por la que se intenta ordenar o filtrar"
                        .formatted(ex.getPropertyName()),
                request, Map.of());
    }

    /**
     * Cadena de consulta que ni siquiera se puede descomponer en parámetros.
     *
     * <p>Por ejemplo {@code ?=845968717378}: un valor sin nombre. Tomcat no lo puede analizar y lanza su
     * propia excepción, que sin este manejador llegaba al genérico y salía <b>500</b> — otra vez la API
     * echándose la culpa de algo que mandó mal quien llama.</p>
     *
     * <p>⚠️ Esta excepción es <b>de Tomcat</b>, no de Spring. Si algún día se cambia a Jetty o Undertow,
     * este manejador sobra y hay que buscar el equivalente del nuevo contenedor.</p>
     */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ProblemDetail> handleParametrosIlegibles(InvalidParameterException ex,
                                                                   HttpServletRequest request) {
        return construir("Peticion mal formada", ex, HttpStatus.BAD_REQUEST,
                "La cadena de consulta no se puede interpretar: revisa el formato de los parametros",
                request, Map.of());
    }

    /**
     * Uso invalido de la API de acceso a datos, casi siempre por un parametro que no tiene sentido.
     *
     * <p>Es <b>400</b> y no 500: el fallo esta en lo que pidio quien llama, no en el servidor.</p>
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ProblemDetail> handleUsoInvalido(InvalidDataAccessApiUsageException ex,
                                                           HttpServletRequest request) {
        log.warn("Uso invalido del acceso a datos", ex);
        return construir("Peticion invalida", ex, HttpStatus.BAD_REQUEST,
                "Alguno de los valores enviados no es valido para esta operacion", request, Map.of());
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
