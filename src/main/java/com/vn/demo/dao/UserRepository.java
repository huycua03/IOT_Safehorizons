package com.vn.demo.dao;

import com.vn.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    @Query("SELECT u FROM User u WHERE u.user_name = :username")
    Optional<User> findByUser_name(@Param("username") String username);
    
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.user_name = :username")
    boolean existsByUser_name(@Param("username") String username);
    
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email")
    boolean existsByEmail(@Param("email") String email);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.roleName = 'ROLE_OPERATOR'")
    List<User> findOperators();
    
    @Query("SELECT DISTINCT u FROM User u JOIN Session s ON s.operator.user_id = u.user_id " +
           "WHERE s.status = 'ACTIVE'")
    List<User> findOperatorsWithActiveSessions();
}
