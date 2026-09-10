package ${groupId};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// Registra los @ConfigurationProperties (por ejemplo configs.CorsProperties) sin tener que enumerarlos
// uno a uno en un @EnableConfigurationProperties que siempre se acaba olvidando actualizar.
@ConfigurationPropertiesScan
// Si devuelves `Page` en alguna respuesta, anade tambien:
//   @EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
// Sin eso, el JSON de la pagina expone la estructura interna de Page, que cambia entre versiones de
// Spring y romperia a los clientes sin previo aviso.
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

}
