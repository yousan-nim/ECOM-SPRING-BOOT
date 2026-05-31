package com.ecom.catalog.repository;

import com.ecom.catalog.domain.Role;
import com.ecom.catalog.domain.UserRole;
import com.ecom.catalog.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    default Set<Role> findRolesByUserId(Long userId) {
        return findByUserId(userId).stream()
                .map(UserRole::getRole)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
