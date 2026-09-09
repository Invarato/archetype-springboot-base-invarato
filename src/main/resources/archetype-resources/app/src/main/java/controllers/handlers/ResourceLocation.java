package ${groupId}.controllers.handlers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fuerza la ruta base que usara {@link ResourceResponseAdvice} para construir la cabecera
 * {@code Location}.
 *
 * <p>Solo hace falta cuando el recurso creado <b>no</b> cuelga de la URL a la que se hizo el POST. El
 * caso tipico: {@code POST /api/v1/pedidos/{id}/lineas} crea una linea, pero si quieres que el
 * {@code Location} apunte a {@code /api/v1/lineas/{id}} lo dices aqui.</p>
 *
 * <pre>
 * &#64;PostMapping("/{id}/lineas")
 * &#64;ResponseStatus(HttpStatus.CREATED)
 * &#64;ResourceLocation("/api/v1/lineas")
 * public Long crearLinea(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceLocation {

    /** Ruta base del recurso creado. Se le añade {@code /{id}}. */
    String value();
}
