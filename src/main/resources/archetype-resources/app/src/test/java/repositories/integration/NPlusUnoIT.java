package ${groupId}.repositories.integration;

import ${groupId}.entities.MyTable;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.repositories.integration.common.BaseRepositoryIT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vigila el problema N+1 <b>contando consultas</b>.
 *
 * <p>El N+1 es el fallo de rendimiento mas comun con JPA y tiene una propiedad desagradable: <b>no
 * falla</b>. Todo responde bien y todo es correcto; simplemente, donde debia haber una consulta hay
 * cien. Con diez filas en la base de datos de desarrollo nadie lo nota, y con cien mil en produccion no
 * se habla de otra cosa.</p>
 *
 * <p>Por eso este test no mira el resultado: mira <b>cuantas sentencias</b> se ejecutaron, que es la
 * unica forma de que un N+1 se ponga rojo antes de desplegarse.</p>
 *
 * <p>Medido aqui con 5 registros: sin {@code default_batch_fetch_size} salian <b>6</b> consultas (una
 * por fila, mas la inicial); con el, <b>2</b>.</p>
 */
class NPlusUnoIT extends BaseRepositoryIT {

    /** 5 hijos: suficiente para que un N+1 se separe con claridad del caso bueno. */
    private static final int FILAS = 5;

    @Autowired
    private MyTableRepository myTableRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void resolverRelacionesNoLanzaUnaConsultaPorFila() {
        for (int i = 0; i < FILAS; i++) {
            MyTable padre = new MyTable();
            padre.setName("padre" + i);
            padre = myTableRepository.save(padre);

            MyTable hijo = new MyTable();
            hijo.setName("hijo" + i);
            hijo.setMyTableParent(padre);
            myTableRepository.save(hijo);
        }
        // Vaciar el contexto es imprescindible: si las entidades siguen en memoria, leerlas no lanza
        // ninguna consulta y el test daria verde pase lo que pase.
        em.flush();
        em.clear();

        Statistics estadisticas = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        // Se piden solo los hijos, para que los padres NO vengan ya cargados de la misma consulta.
        List<MyTable> hijos = em.createQuery(
                "select t from MyTable t where t.myTableParent is not null", MyTable.class).getResultList();
        assertEquals(FILAS, hijos.size());

        long trasLaConsulta = estadisticas.getPrepareStatementCount();

        // Al tocar la relacion es cuando aparece —o no— el N+1.
        hijos.forEach(hijo -> hijo.getMyTableParent().getName());

        long total = estadisticas.getPrepareStatementCount();
        long porResolverLaRelacion = total - trasLaConsulta;

        assertTrue(porResolverLaRelacion <= 1,
                "N+1: resolver la relacion de " + FILAS + " filas costo " + porResolverLaRelacion
                        + " consultas y deberia costar 1 como mucho. Revisa que sigue puesto "
                        + "hibernate.default_batch_fetch_size, o usa @EntityGraph / JOIN FETCH.");
    }
}
