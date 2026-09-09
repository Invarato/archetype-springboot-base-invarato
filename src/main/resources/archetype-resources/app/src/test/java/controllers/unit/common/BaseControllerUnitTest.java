package ${groupId}.controllers.unit.common;


import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.test.context.support.WithMockUser;


@WithMockUser
public abstract class BaseControllerUnitTest {

    @BeforeEach
    public void setup() {
        // Configuración común para pruebas de integración
    }

}
