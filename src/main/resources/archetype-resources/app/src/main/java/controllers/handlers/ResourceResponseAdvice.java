package ${groupId}.controllers.handlers;

import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Pone la cabecera {@code Location} en toda respuesta {@code 201 Created}, sin que el controlador tenga
 * que acordarse.
 *
 * <p><b>El problema que resuelve.</b> Un POST que crea un recurso debe devolver {@code Location} con su
 * URL: es parte del contrato HTTP y es como un cliente sabe donde quedo lo que acaba de crear. Pero
 * construirla a mano en cada endpoint significa repetir el mismo bloque, y basta con que <b>uno</b> se
 * olvide para que el cliente se quede sin saberlo — y nada falla, simplemente falta la cabecera.</p>
 *
 * <p><b>Como funciona.</b> Se activa solo en metodos anotados con {@code @ResponseStatus(CREATED)} y
 * mira lo que devuelve el controlador para sacar el identificador. El controlador queda limpio:</p>
 *
 * <pre>
 * &#64;PostMapping
 * &#64;ResponseStatus(HttpStatus.CREATED)
 * public Long crear(&#64;Valid &#64;RequestBody MiRequest request) {
 *     return servicio.crear(request);   // Location: /api/v1/recursos/42
 * }
 * </pre>
 *
 * <p>Si el identificador no es lo que se devuelve directamente, basta con que el DTO implemente
 * {@link Identifiable}.</p>
 */
@ControllerAdvice
public class ResourceResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * Para cuerpos de respuesta que no son el identificador pero lo contienen.
     *
     * <p>Un record lo implementa en una linea: {@code public Long id() { return id; }} ya lo cumple si
     * el componente se llama {@code id}.</p>
     */
    public interface Identifiable {
        Object id();
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        ResponseStatus status = returnType.getMethodAnnotation(ResponseStatus.class);
        return status != null && status.value() == HttpStatus.CREATED;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        // switch con patrones (Java 21+): se lee de un vistazo que formas se aceptan como identificador.
        Object id = switch (body) {
            case Number n -> n;
            case String s -> s;
            case Identifiable identificable -> identificable.id();
            case null, default -> null;
        };

        if (id != null) {
            response.getHeaders().add(HttpHeaders.LOCATION, construirLocation(returnType, request, id));
        }

        // El cuerpo se devuelve intacto: este advice añade una cabecera, no transforma la respuesta.
        return body;
    }

    private String construirLocation(MethodParameter returnType, ServerHttpRequest request, Object id) {
        ResourceLocation override = returnType.getMethodAnnotation(ResourceLocation.class);
        String rutaBase = override != null
                ? (override.value().startsWith("/") ? override.value() : "/" + override.value())
                // Por defecto, la propia URL del POST: `POST /api/v1/x` -> `/api/v1/x/{id}`, que es la
                // convencion REST habitual.
                : request.getURI().getPath();

        return UriComponentsBuilder.newInstance()
                .scheme(request.getURI().getScheme())
                .host(request.getURI().getHost())
                .port(request.getURI().getPort())
                .path(rutaBase)
                .path("/{id}")
                .buildAndExpand(id)
                .toUriString();
    }
}
