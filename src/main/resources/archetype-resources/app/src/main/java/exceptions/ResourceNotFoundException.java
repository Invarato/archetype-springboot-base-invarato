package ${groupId}.exceptions;

/**
 * El recurso pedido no existe. Se traduce a un {@code 404}.
 *
 * <p>Existe en vez de lanzar {@code NoSuchElementException} o, peor, {@code RuntimeException}: una
 * excepcion propia dice <b>que</b> ha fallado, y permite que el manejador global le asigne un codigo
 * HTTP sin adivinar. {@code ArchitectureTest} prohibe lanzar excepciones genericas justo por esto.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }

    /** Atajo para el caso mas comun: «este tipo de recurso, con este id, no esta». */
    public static ResourceNotFoundException of(String recurso, Object id) {
        return new ResourceNotFoundException("No se encuentra %s con id %s".formatted(recurso, id));
    }
}
