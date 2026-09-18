package ${groupId}.dtos.responses;

import io.swagger.v3.oas.annotations.media.Schema;

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
        // ⚠️ Puede venir a null y el contrato TIENE que decirlo. Sin esto, OpenAPI 3.1 lo declara
        // `type: integer` a secas, un cliente estricto rechaza la respuesta y el fallo aparece en el
        // consumidor. Lo destapo Schemathesis: «null is not of type integer».
        @Schema(nullable = true)
        Long myTableParentId
) {
}
