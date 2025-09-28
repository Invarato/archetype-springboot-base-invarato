package ${groupId}.controllers;

import ${groupId}.dtos.responses.SimpleApiResponse;
import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.entities.MyTable;
import ${groupId}.services.ExampleService;
import ${groupId}.controllers.common.ResponseUtils;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.util.List;


@RestController
@RequestMapping("api/v1/examples")
@RequiredArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;

    @GetMapping("/hello")
    public ResponseEntity<String> sayHello() {
        String body = "Hello World";
        return ResponseEntity.ok(body);
    }
    
    @GetMapping("/helloDto")
    public ResponseEntity<SimpleApiResponse> sayHelloDto() {
        SimpleApiResponse body = exampleService.getHelloDto();
        return ResponseEntity.ok(body);
    }

    // Ejemplo de CRUD completo
    @PostMapping
    public ResponseEntity<Long> createNew(@Valid @RequestBody MyTableRequest request) {
        Long id = exampleService.saveSimple(request);
        return ResponseUtils.responseEntityCreated(id);
    }

    @GetMapping
    public ResponseEntity<List<MyTable>> listAllEjemplos() {
        List<MyTable> MyTables = exampleService.getAllEjemplos();
        return ResponseEntity.ok(MyTables);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MyTable> getEjemplo(@PathVariable Long id) {
        MyTable MyTable = exampleService.getEjemploById(id);
        return ResponseEntity.ok(MyTable);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody MyTableRequest request) {
        exampleService.updateEjemploById(id, request);
        return ResponseUtils.responseNoContent();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        exampleService.deleteEjemploById(id);
        return ResponseUtils.responseNoContent();
    }


    /**
     * Ejemplo de controlador con paginación.
     *
     * Requiere añadir en MainApplication: @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
     *
     * Ejemplos de paginacion:
     * - /api/v1/ejemplos/paginated?page=0&size=10
     * - /api/v1/ejemplos/paginated?page=0&size=10&sort=id,asc
     * - /api/v1/ejemplos/paginated?page=1&size=20&sort=name,desc&sort=id,asc
     * @param pageable
     * @return
     */
    @GetMapping("/paginated")
    @Parameters({
            @Parameter(name = "page",
                    description = "Número de Página",
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer",  defaultValue = "0")
            ),
            @Parameter(name = "size",
                    description = "Tamaño de página",
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer",  defaultValue = "10")
            ),
            @Parameter(name = "name",
                    description = "Orden (field,dir). Puede repetirse",
                    in = ParameterIn.QUERY,
                    schema = @Schema(type = "string",  defaultValue = "id,asc")
            )
    })
    public ResponseEntity<Page<MyTable>> listPaginatedFromService(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) @Parameter(hidden = true) Pageable pageable
    ) {
        Page<MyTable> page = exampleService.getAllExamplesPaginated(pageable);
        return ResponseEntity.ok(page);
    }

}
