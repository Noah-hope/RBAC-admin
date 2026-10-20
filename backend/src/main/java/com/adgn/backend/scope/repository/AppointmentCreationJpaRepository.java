package com.adgn.backend.scope.repository;

import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.*;
import java.util.List;

@Transactional(propagation=Propagation.MANDATORY)
public interface AppointmentCreationJpaRepository extends Repository<AppointmentCreationEntity,String> {
    List<AppointmentCreationEntity> saveAllAndFlush(Iterable<AppointmentCreationEntity> appointments);
}
