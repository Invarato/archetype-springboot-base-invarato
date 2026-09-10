package ${groupId};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
// Registra los @ConfigurationProperties (por ejemplo configs.CorsProperties) sin tener que enumerarlos
// uno a uno en un @EnableConfigurationProperties que siempre se acaba olvidando actualizar.
@ConfigurationPropertiesScan
// Red de seguridad para la paginacion. Serializado tal cual, un `Page` publica su estructura INTERNA
// (`pageable`, `sort`, `first`, `last`, `numberOfElements`...), que Spring puede cambiar entre
// versiones: el dia que cambie, los clientes se rompen sin que nadie haya tocado esta API.
//
// Con VIA_DTO el JSON pasa a ser una forma estable y minima: `content` + un `page` con
// `size`/`number`/`totalElements`/`totalPages`.
//
// Esto cubre CUALQUIER `Page` que se devuelva sin pensar en ello. Aun asi, los controladores de aqui
// declaran `PagedModel` como tipo de retorno: la anotacion arregla lo que se serializa, pero no lo que
// se documenta — el contrato OpenAPI sale del tipo declarado. Ver ExampleController#listPaginated.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

}
