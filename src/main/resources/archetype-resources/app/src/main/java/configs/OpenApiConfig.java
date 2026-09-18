package ${groupId}.configs;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Documenta las respuestas de ERROR en todas las operaciones, de una vez.
     *
     * <p>El contrato declaraba solo el {@code 200} de cada operación, y eso deja fuera la mitad de lo
     * que un cliente necesita: los 400, 404 y 409 que {@code GlobalExceptionHandlerController} devuelve
     * de verdad. El consumidor se encuentra con un cuerpo que su cliente generado no sabe interpretar,
     * justo en el momento en que algo ha ido mal.</p>
     *
     * <p><b>Por qué aquí y no con {@code @ApiResponse} en cada método:</b> serían cinco anotaciones
     * repetidas en cada operación de cada controlador, y el día que se añada un manejador nuevo habría
     * que acordarse de todas. Aquí se escribe una vez y se aplica a lo que haya.</p>
     *
     * <p>Lo destapó Schemathesis: 13 avisos de «Undocumented HTTP status code» contra la API real.</p>
     */
    @Bean
    public OpenApiCustomizer respuestasDeError() {
        return openApi -> {
            // El esquema de ProblemDetail (RFC 9457) se registra una vez en components y las respuestas
            // lo referencian, en vez de repetirlo en cada operación.
            ResolvedSchema resuelto = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(ProblemDetail.class));
            resuelto.referencedSchemas.forEach((nombre, esquema) ->
                    openApi.getComponents().addSchemas(nombre, esquema));

            // ⚠️ GlobalExceptionHandlerController añade campos PROPIOS al ProblemDetail estandar
            // (`timestamp`, `path` y, en los errores de validacion, `errors`). Si no se declaran aqui,
            // el contrato describe una respuesta distinta de la que sale por el cable — y eso es
            // exactamente lo que rompe a un cliente generado que valide lo que recibe.
            Schema<?> problema = openApi.getComponents().getSchemas().get("ProblemDetail");
            if (problema != null) {
                problema.addProperty("timestamp", new Schema<>().type("string").format("date-time")
                        .description("Momento en que se genero el error"));
                problema.addProperty("path", new Schema<>().type("string")
                        .description("Ruta de la peticion que fallo"));
                problema.addProperty("errors", new Schema<>().type("object")
                        .description("Solo en errores de validacion: mensaje por cada campo invalido")
                        .additionalProperties(new Schema<>().type("string")));
            }

            Content cuerpoDeProblema = new Content().addMediaType(
                    "application/problem+json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")));

            Map<String, String> errores = new LinkedHashMap<>();
            errores.put("400", "Peticion invalida: validacion, cuerpo ilegible o parametro de tipo incorrecto");
            errores.put("404", "El recurso no existe");
            errores.put("405", "Metodo HTTP no permitido en esta ruta");
            errores.put("409", "Conflicto: integridad de datos o modificacion concurrente");
            errores.put("500", "Error interno no controlado");

            openApi.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(operacion -> {
                ApiResponses respuestas = operacion.getResponses();
                errores.forEach((codigo, descripcion) -> {
                    // Si una operación ya documenta ese código a mano, se respeta.
                    if (respuestas.get(codigo) == null) {
                        respuestas.addApiResponse(codigo, new ApiResponse()
                                .description(descripcion)
                                .content(cuerpoDeProblema));
                    }
                });

                // ⚠️ El 401 va SIN cuerpo, y no es un descuido: lo emite Spring Security desde la cadena
                // de filtros, antes de que exista un controlador, asi que no pasa por el manejador de
                // excepciones y responde vacio. Documentarlo con cuerpo seria describir algo que no
                // ocurre.
                if (respuestas.get("401") == null) {
                    respuestas.addApiResponse("401", new ApiResponse()
                            .description("Falta el token o no es valido (respuesta sin cuerpo)"));
                }
            }));
        };
    }
}
