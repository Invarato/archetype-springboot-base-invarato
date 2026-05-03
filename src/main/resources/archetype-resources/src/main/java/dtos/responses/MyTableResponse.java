package ${groupId}.dtos.responses;

public record MyTableResponse(
        String name,
        String surname,
        String description,
        Long myTableParentId
) {
}
