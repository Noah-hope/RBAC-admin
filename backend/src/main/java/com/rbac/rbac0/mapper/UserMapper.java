package com.rbac.rbac0.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.rbac.rbac0.entity.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserMapper extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account from UserAccount account
            where account.id in :ids and account.departmentId is not null order by account.id
            """)
    List<UserAccount> findOrganizationUsersForUpdate(List<Long> ids);

    @Query("""
            select account from UserAccount account, Department department
            where department.id = account.departmentId and account.departmentId in :departmentIds
              and (:keyword = '' or locate(:keyword, account.name) > 0 or locate(:keyword, account.username) > 0
                   or (:includeDepartmentName = true and locate(:keyword, department.name) > 0))
            order by account.id
            """)
    Page<UserAccount> search(Collection<Long> departmentIds, String keyword, boolean includeDepartmentName,
                             Pageable pageable);
}
