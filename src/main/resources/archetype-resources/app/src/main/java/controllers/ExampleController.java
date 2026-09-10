package ${groupId}.controllers;

import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.dtos.responses.SimpleApiResponse;
import ${groupId}.services.ExampleService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ⚠️ NOTA INTERNA, y por eso va en un comentario `//` y no en javadoc:
//
// El javadoc de esta clase YA NO ES INTERNO. Con therapi activado, el javadoc de la clase acaba siendo
// la descripcion del tag en el contrato OpenAPI, y el de cada metodo la descripcion de su operacion. O
// sea: lo escriben los de dentro y lo leen los de FUERA, incluidos los clientes generados.
//
// La regla que se sigue aqui: javadoc = lo que un consumidor necesita saber. Comentarios `//` = lo que
// necesita saber quien mantiene el codigo. Mezclarlos publica avisos internos en la API.
//
// Y lo de siempre: de aqui solo salen DTOs, nunca entidades. Lo vigila ArchitectureTest.

/**
 * Operaciones de ejemplo.
 *
 * <p>Todas las rutas exigen un token válido en la cabecera {@code Authorization}, salvo las sondas de
 * salud. Las respuestas de error siguen el formato {@code application/problem+json}.</p>
 */
@RestController
@RequestMapping("api/v1/examples")
@RequiredArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;

    /**
     * Devuelve un saludo en texto plano.
     *
     * @return el texto {@code Hello World}
     */
    @GetMapping("/hello")
    public ResponseEntity<String> sayHello() {
        return ResponseEntity.ok("Hello World");
    }

    /**
     * Devuelve un saludo como objeto JSON.
     *
     * @return un mensaje de saludo
     */
    @GetMapping("/helloDto")
    public ResponseEntity<SimpleApiResponse> sayHelloDto() {
        return ResponseEntity.ok(exampleService.getHelloDto());
    }

    // ── CRUD de ejemplo ──────────────────────────────────────────────────────────────────────

    /**
     * Crea un registro nuevo.
     *
     * <p>La respuesta incluye la cabecera {@code Location} con la URL del recurso creado.</p>
     *
     * @param request datos del registro a crear
     * @return el identificador asignado
     */
    // La cabecera Location la pone ResourceResponseAdvice: el controlador no construye URLs.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long create(@Valid @RequestBody MyTableRequest request) {
        return exampleService.create(request);
    }

    /**
     * Lista todos los registros.
     *
     * @return los registros existentes; lista vacía si no hay ninguno
     */
    @GetMapping
    public ResponseEntity<List<MyTableResponse>> listAll() {
        return ResponseEntity.ok(exampleService.findAll());
    }

    /**
     * Busca un registro por su identificador.
     *
     * @param id identificador del registro
     * @return el registro encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<MyTableResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(exampleService.findById(id));
    }

    /**
     * Actualiza un registro existente.
     *
     * @param id      identificador del registro a actualizar
     * @param request nuevos datos del registro
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable Long id, @Valid @RequestBody MyTableRequest request) {
        exampleService.update(id, request);
    }

    /**
     * Elimina un registro.
     *
     * @param id identificador del registro a eliminar
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        exampleService.delete(id);
    }

    /**
     * Lista los registros de forma paginada.
     *
     * <p>Admite ordenación por cualquier campo con {@code sort=campo,dirección}, y el parámetro puede
     * repetirse para ordenar por varios criterios.</p>
     *
     * @return una página de registros con sus metadatos de paginación
     */
    // NOTA INTERNA — por que `PagedModel` y no `Page` como tipo de retorno:
    //
    // MainApplication activa VIA_DTO, que ya arregla el JSON en tiempo de ejecucion. Pero springdoc
    // documenta el tipo DECLARADO, no lo que acaba saliendo por el cable. Devolviendo `Page` el
    // contrato describia un `PageMyTableResponse` con 11 campos internos (`pageable`, `sort`, `first`,
    // `last`...) que el servidor ya no manda: contrato y realidad decian cosas distintas, y los
    // clientes generados a partir de el llevaban ese modelo fantasma dentro.
    //
    // Declarando `PagedModel` las dos cosas coinciden y no dependen de que nadie recuerde la anotacion.
    @GetMapping("/paginated")
    @Parameters({
            @Parameter(name = "page", description = "Numero de pagina (empezando en 0)", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Numero de elementos por pagina", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "Orden: campo,direccion. Puede repetirse", in = ParameterIn.QUERY,
                    schema = @Schema(type = "string", defaultValue = "id,asc"))
    })
    public ResponseEntity<PagedModel<MyTableResponse>> listPaginated(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC)
            @Parameter(hidden = true) Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(exampleService.findAllPaginated(pageable)));
    }

}
