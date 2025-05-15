package com.vn.demo.dao;

import com.vn.demo.entity.CommandList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CommandListRepository extends JpaRepository<CommandList, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Command c WHERE c.commandId = :commandId")
    void deleteCommandById(@Param("commandId") int commandId);
    
    // Kiểm tra CommandList có đang được sử dụng bởi Profile nào không
    @Query("SELECT COUNT(p) FROM Profile p WHERE p.commandList.commandListId = :commandListId")
    int countProfilesUsingCommandList(@Param("commandListId") int commandListId);
    
    // Xóa tất cả Command trong CommandList
    @Modifying
    @Transactional
    @Query("DELETE FROM Command c WHERE c.commandList.commandListId = :commandListId")
    void deleteAllCommandsInList(@Param("commandListId") int commandListId);
}
