package ${groupId}.entities;


import ${groupId}.entities.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;


@RequiredArgsConstructor
@Entity
@Table(name = "MyTable")
@Getter
@Setter
@Audited
public class MyTable extends BaseEntity {

    // `length` va aqui y no solo en el DTO: si la entidad no lo dice, la columna sale varchar(255) y la
    // base de datos acepta lo que la validacion rechaza. Que coincidan con los @Size del Request es parte
    // del ejemplo.
    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "surname", length = 200)
    private String surname;

    @Column(name = "description", length = 200)
    private String description;

    // El nombre del campo arranca en minuscula a proposito: MapStruct casa por PROPIEDAD del bean, y con
    // `MyTableParent` la propiedad no era `myTableParent`, asi que el mapper fallaba en compilacion con
    // "Unmapped target property" (BaseMapperConfig usa unmappedTargetPolicy = ERROR).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "my_table_parent_id", foreignKey = @ForeignKey(name = "fk_my_table_parent"))
    private MyTable myTableParent;

}
