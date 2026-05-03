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

    @Column(name="name")
    private String name;

    @Column(name="surname")
    private String surname;

    @Column(name="description")
    private String description;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MyTableparent")
    private MyTable MyTableParent;

}
