package ${groupId}.mappers;


import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.entities.MyTable;
import ${groupId}.mappers.common.BaseMapperConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface MyTableMapper {
    @Mapping(source = "myTableParentId", target = "myTableParent")
    @BeanMapping(
        unmappedSourcePolicy = ReportingPolicy.ERROR, 
        unmappedTargetPolicy = ReportingPolicy.IGNORE
    )
    MyTable toEntity(MyTableRequest request);

    List<MyTable> toEntities(List<MyTableRequest> requests);

    @Mapping(source = "myTableParentId", target = "myTableParent")
    @BeanMapping(
        unmappedSourcePolicy = ReportingPolicy.ERROR, 
        unmappedTargetPolicy = ReportingPolicy.IGNORE
    )
    void updateEntity(MyTableRequest request, @MappingTarget MyTable entity);

    @Mapping(source = "myTableParent.id", target = "myTableParentId")
    @BeanMapping(
        unmappedSourcePolicy = ReportingPolicy.IGNORE, 
        unmappedTargetPolicy = ReportingPolicy.ERROR
    )
    MyTableResponse toResponse(MyTable entity);

    List<MyTableResponse> toResponses(List<MyTable> entities);
}
