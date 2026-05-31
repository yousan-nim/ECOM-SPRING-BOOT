package com.ecom.user.repository;

import com.ecom.user.domain.Role;
import com.ecom.user.domain.UserRole;
import com.ecom.user.domain.UserRoleId;
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
