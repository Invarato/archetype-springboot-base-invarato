package ${groupId}.dtos.responses;

/**
 * Respuesta simple con un único mensaje.
 *
 * @param message texto del mensaje
 */
public record SimpleApiResponse(
        String message
) {
}
