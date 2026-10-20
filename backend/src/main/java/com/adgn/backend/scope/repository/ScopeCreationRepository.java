package com.adgn.backend.scope.repository;

import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.web.scope.dto.ScopeResponses;
import javax.sql.DataSource;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Capability-checked JPA entry point; flush before the same-transaction success audit. */
public final class ScopeCreationRepository {
    private final DataSource source;
    private final ScopeCreationJpaRepository scopes;
    private final AppointmentCreationJpaRepository appointments;
    public ScopeCreationRepository(DataSource source,ScopeCreationJpaRepository scopes,AppointmentCreationJpaRepository appointments){this.source=source;this.scopes=scopes;this.appointments=appointments;}
    public ScopeResponses.Scope create(WriteContext<?> context,String id,String company,String department,List<String> systems,Map<String,String> appointmentIds){
        context.requireActive(source);
        if(scopes.existsById(id))throw new IdentityFailure(409,"STATE_CONFLICT");
        var now=context.now().truncatedTo(ChronoUnit.MICROS);
        scopes.saveAndFlush(new ScopeCreationEntity(id,company,department,systems,now));
        appointments.saveAllAndFlush(appointmentIds.entrySet().stream().map(e->new AppointmentCreationEntity(e.getValue(),id,e.getKey(),now)).toList());
        return new ScopeResponses.Scope(id,company,department,List.copyOf(systems),1,now);
    }
}
