package ${groupId}.dtos.responses;

/**
 * Datos de un registro.
 *
 * @param name             nombre
 * @param surname          apellidos
 * @param description      descripción libre
 * @param myTableParentId  identificador del registro padre, si lo tiene
 */
// El javadoc de cada componente acaba siendo la descripcion de esa propiedad en el contrato OpenAPI, y
// de ahi pasa a los clientes generados. Se escribe pensando en quien consume la API, no en quien
// mantiene esta clase.
public record MyTableResponse(
        String name,
        String surname,
        String description,
        Long myTableParentId
) {
}
