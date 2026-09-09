package ${groupId};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// Registra los @ConfigurationProperties (por ejemplo configs.CorsProperties) sin tener que enumerarlos
// uno a uno en un @EnableConfigurationProperties que siempre se acaba olvidando actualizar.
@ConfigurationPropertiesScan
// TODO solo si usas Pageable/Page en las respuestas:
// TODO @EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

}
