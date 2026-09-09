package ${groupId}.mappers.common;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Configuracion comun de todos los mappers.
 *
 * <p>Las dos politicas en {@code ERROR} son deliberadas: si anades un campo a un DTO o a una entidad y
 * te olvidas de mapearlo, quieres que <b>falle la compilacion</b>, no que llegue un {@code null} a
 * produccion. Es de las pocas veces que un mapeador puede avisarte a tiempo.</p>
 */
@MapperConfig(
        componentModel = "spring",
        // ⚠️ Sin esto, MapStruct genera los *Impl con @Autowired SOBRE EL CAMPO. Es codigo generado, asi
        // que no se ve en una revision, pero es codigo del proyecto igualmente: aparece en el jar, no
        // permite campos final y obliga a levantar Spring para probar el mapper.
        // Lo detecto ArchitectureTest, que es justo para lo que esta.
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedSourcePolicy = ReportingPolicy.ERROR,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = {ReferenceMapper.class}
)
public interface BaseMapperConfig {
}
