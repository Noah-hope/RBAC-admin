package com.adgn.grantfixture;

import com.adgn.backend.grant.GrantReader;
import com.adgn.backend.people.OrganizationReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

class GrantReaderBoundaryTest {
    @Test void refusesReadsOutsideTheCallerTransaction(){
        var source=new DriverManagerDataSource();var reader=new GrantReader(source,new OrganizationReader(source));
        assertThatThrownBy(()->reader.source(new GrantReader.SourceKey("e","c","d","s"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void anUnrelatedTransactionFlagDoesNotAuthorizeReadingAnotherDatasource(){
        var source=new DriverManagerDataSource();var reader=new GrantReader(source,new OrganizationReader(source));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThatThrownBy(()->reader.source(new GrantReader.SourceKey("e","c","d","s"))).isInstanceOf(IllegalStateException.class);}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
}
