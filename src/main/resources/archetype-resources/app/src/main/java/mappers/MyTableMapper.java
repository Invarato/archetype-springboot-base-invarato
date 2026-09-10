package ${groupId}.mappers;


import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.entities.MyTable;
import ${groupId}.mappers.common.BaseMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface MyTableMapper {

    // DTO → Entity
    @Mapping(source = "myTableParentId", target = "myTableParent")
    MyTable toEntity(MyTableRequest request);

    // Update (PATCH/PUT)
    @InheritConfiguration(name = "toEntity")
    void updateEntity(MyTableRequest request, @MappingTarget MyTable entity);

    // Entity → DTO
    @Mapping(source = "myTableParent.id", target = "myTableParentId")
    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    MyTableResponse toResponse(MyTable entity);

    // List mappings (MapStruct los genera automáticamente)
    List<MyTable> toEntities(List<MyTableRequest> requests);

    List<MyTableResponse> toResponses(List<MyTable> entities);
}
