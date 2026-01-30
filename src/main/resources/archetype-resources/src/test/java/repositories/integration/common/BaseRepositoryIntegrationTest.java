package ${groupId}.repositories.integration.common;

import ${groupId}.common.ConfigTestContainersTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;


@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseRepositoryIntegrationTest extends ConfigTestContainersTest {

}
