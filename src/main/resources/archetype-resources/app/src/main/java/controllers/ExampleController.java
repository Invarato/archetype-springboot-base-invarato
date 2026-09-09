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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ejemplo de controlador REST.
 *
 * <p>⚠️ Todo lo que sale de aqui son DTOs, nunca entidades. Lo que devuelva un controlador acaba en el
 * contrato OpenAPI y, desde ahi, en todos los clientes generados: publicar la entidad ata la API a la
 * forma de la tabla. {@code ArchitectureTest} lo vigila.</p>
 */
@RestController
@RequestMapping("api/v1/examples")
@RequiredArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;

    @GetMapping("/hello")
    public ResponseEntity<String> sayHello() {
        return ResponseEntity.ok("Hello World");
    }

    @GetMapping("/helloDto")
    public ResponseEntity<SimpleApiResponse> sayHelloDto() {
        return ResponseEntity.ok(exampleService.getHelloDto());
    }

    // ── CRUD de ejemplo ──────────────────────────────────────────────────────────────────────

    // La cabecera Location la pone ResourceResponseAdvice a partir de lo que se devuelve: el
    // controlador no construye URLs. Basta con declarar el 201.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long createNew(@Valid @RequestBody MyTableRequest request) {
        return exampleService.saveSimple(request);
    }

    @GetMapping
    public ResponseEntity<List<MyTableResponse>> listAllEjemplos() {
        return ResponseEntity.ok(exampleService.getAllEjemplos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MyTableResponse> getEjemplo(@PathVariable Long id) {
        return ResponseEntity.ok(exampleService.getEjemploById(id));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable Long id, @Valid @RequestBody MyTableRequest request) {
        exampleService.updateEjemploById(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        exampleService.deleteEjemploById(id);
    }

    /**
     * Ejemplo con paginacion.
     *
     * <p>Requiere anadir en MainApplication:
     * {@code @EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)},
     * o el JSON de la pagina expondra la estructura interna de {@code Page} y cambiara con Spring.</p>
     *
     * <p>Ejemplos:
     * {@code ?page=0&size=10} · {@code ?page=0&size=10&sort=id,asc} ·
     * {@code ?page=1&size=20&sort=name,desc&sort=id,asc}</p>
     */
    @GetMapping("/paginated")
    @Parameters({
            @Parameter(name = "page", description = "Numero de pagina", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Tamano de pagina", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "Orden (campo,direccion). Puede repetirse", in = ParameterIn.QUERY,
                    schema = @Schema(type = "string", defaultValue = "id,asc"))
    })
    public ResponseEntity<Page<MyTableResponse>> listPaginatedFromService(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC)
            @Parameter(hidden = true) Pageable pageable
    ) {
        return ResponseEntity.ok(exampleService.getAllExamplesPaginated(pageable));
    }

}
