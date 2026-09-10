package ${groupId}.services;

import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.dtos.responses.SimpleApiResponse;
import ${groupId}.entities.MyTable;
import ${groupId}.exceptions.ResourceNotFoundException;
import ${groupId}.mappers.MyTableMapper;
import ${groupId}.repositories.MyTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ejemplo de servicio.
 *
 * <p>⚠️ Fijate en que <b>no devuelve entidades</b>, sino DTOs de respuesta. La entidad se queda dentro
 * de esta capa. Devolverla hacia fuera parece inofensivo y sale caro por tres sitios a la vez:</p>
 *
 * <ol>
 *   <li>Un cambio en la base de datos se convierte en un cambio de la API publica, sin querer.</li>
 *   <li>Serializar una entidad fuera de la transaccion revienta con las relaciones perezosas, y ademas
 *       expone campos que no tocaba publicar.</li>
 *   <li>La entidad acaba en el contrato OpenAPI y, desde ahi, <b>en todos los clientes generados</b>.
 *       Ya paso: el cliente Java traia una clase {@code MyTable} con la forma de la tabla.</li>
 * </ol>
 *
 * <p>La regla la vigila {@code ArchitectureTest}, para que no vuelva a colarse.</p>
 */
@Service
@RequiredArgsConstructor
public class ExampleService {

    /**
     * Nombre de la región de caché. Es una constante y no un literal suelto porque el nombre tiene que
     * coincidir <b>exactamente</b> entre el {@code @Cacheable} y sus {@code @CacheEvict}: una errata deja
     * la entrada sin invalidar y el fallo no aparece hasta que alguien lee un dato viejo.
     */
    public static final String CACHE_EJEMPLOS = "examples";

    private final MyTableRepository myTableRepository;
    private final MyTableMapper myTableMapper;

    @Transactional(readOnly = true)
    public SimpleApiResponse getHelloDto() {
        return new SimpleApiResponse("Hello World DTO");
    }

    @Transactional
    public Long create(MyTableRequest request) {
        MyTable nueva = myTableMapper.toEntity(request);
        return myTableRepository.save(nueva).getId();
    }

    @Transactional(readOnly = true)
    public List<MyTableResponse> findAll() {
        return myTableMapper.toResponses(myTableRepository.findAll());
    }

    /**
     * Busca un registro por su identificador.
     *
     * @param id identificador del registro
     * @return el registro encontrado
     */
    // La lectura por id es el caso tipico de cache: se pide muchas veces y cambia poco. Lo unico que
    // hay que tener presente es que quien cachea contrae una deuda — TODA escritura sobre este id
    // tiene que invalidar la entrada, o la aplicacion servira datos viejos indefinidamente. De ahi los
    // @CacheEvict de update() y delete(): no son opcionales, son la otra mitad de esta anotacion.
    @Cacheable(cacheNames = CACHE_EJEMPLOS, key = "#id")
    @Transactional(readOnly = true)
    public MyTableResponse findById(Long id) {
        return myTableMapper.toResponse(buscarOFallar(id, "No se puede encontrar el registro con id: "));
    }

    @CacheEvict(cacheNames = CACHE_EJEMPLOS, key = "#id")
    @Transactional
    public void update(Long id, MyTableRequest request) {
        MyTable existente = buscarOFallar(id, "No se puede actualizar el registro con id: ");
        // El mapper aplica los cambios sobre la entidad ya cargada, en vez de construir una nueva: asi
        // no se pierden los campos que el request no trae (id, version, auditoria...).
        myTableMapper.updateEntity(request, existente);
        myTableRepository.save(existente);
    }

    @CacheEvict(cacheNames = CACHE_EJEMPLOS, key = "#id")
    @Transactional
    public void delete(Long id) {
        myTableRepository.delete(buscarOFallar(id, "No se puede eliminar el registro con id: "));
    }

    // ======= Paginacion =======

    @Transactional(readOnly = true)
    public Page<MyTableResponse> findAllPaginated(Pageable pageable) {
        // `map` sobre el Page conserva los metadatos de paginacion (total, pagina, tamaño) y convierte
        // solo el contenido.
        return myTableRepository.findAll(pageable).map(myTableMapper::toResponse);
    }

    private MyTable buscarOFallar(Long id, String mensaje) {
        return myTableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(mensaje + id));
    }
}
