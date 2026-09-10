package ${groupId}.repositories.integration.common;

import ${groupId}.configs.JpaAuditingConfig;
import ${groupId}.common.BaseIntegrationTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;


@DataJpaTest
// ⚠️ @DataJpaTest solo carga los beans de JPA: NO recoge las @Configuration del proyecto. Sin este
// import, la auditoria no se aplica y los tests fallan con "null value in column created_at" — un error
// que apunta a la base de datos cuando el problema es que falta una configuracion.
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseRepositoryIT extends BaseIntegrationTest {

}
