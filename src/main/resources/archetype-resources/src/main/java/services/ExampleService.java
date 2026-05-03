package ${groupId}.services;

import ${groupId}.dtos.responses.SimpleApiResponse;
import ${groupId}.dtos.responses.MyTableResponse;
import ${groupId}.dtos.requests.MyTableRequest;
import ${groupId}.entities.MyTable;
import ${groupId}.repositories.MyTableRepository;
import ${groupId}.mappers.MyTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.NoSuchElementException;


@Service
@RequiredArgsConstructor
public class ExampleService {

    private final MyTableRepository myTableRepository;
    private final MyTableMapper myTableMapper;

    @Transactional
    public MyTable createNewFileInBd(String valorDeMiColumna) {
        MyTable nuevoRegistro = new MyTable();
        nuevoRegistro.setName(valorDeMiColumna);

        return myTableRepository.save(nuevoRegistro);
    }

    @Transactional(readOnly = true)
    public SimpleApiResponse getHelloDto() {
        return new SimpleApiResponse("Hello World DTO");
    }
    

    @Transactional
    public Long saveSimple(MyTableRequest request) {
        MyTable miEntidad = new MyTable();

        miEntidad = myTableMapper.toEntity(request);

        MyTable newRegistro = myTableRepository.save(miEntidad);

        return newRegistro.getId();
    }

    @Transactional(readOnly = true)
    public List<MyTable> getAllEjemplos() {
        return myTableRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MyTable getEjemploById(Long id) {
        return myTableRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No se puede encontrar el registro con id: " + id));
    }

    @Transactional
    public void updateEjemploById(Long id, MyTableRequest request) {
        MyTable miEntidad = myTableRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No se puede actualizar el registro con id: " + id));

// TODO By hand:
//        miEntidad.setName(request.name());
//        miEntidad.setSurname(request.surname());
//        miEntidad.setDescription(request.description());

// TODO Auto with copyProperties
//        BeanUtils.copyProperties(request, miEntidad);

// TODO Auto with mapper
        myTableMapper.updateEntity(request, miEntidad);

        myTableRepository.save(miEntidad);
    }

    @Transactional
    public void deleteEjemploById(Long id) {
        MyTable miEntidad = myTableRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No se puede eliminar el registro con id: " + id));

        myTableRepository.delete(miEntidad);
    }

    public List<MyTableResponse> getAllEjemploResponses() {
        return myTableMapper.toResponses(myTableRepository.findAll());
    }

    // ======= Pagination / Pageable =======

    @Transactional(readOnly = true)
    public Page<MyTable> getAllExamplesPaginated(Pageable pageable) {
        return myTableRepository.findAll(pageable);
    }

}