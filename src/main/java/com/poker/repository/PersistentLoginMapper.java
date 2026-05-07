package com.poker.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

/**
 * Remember-Me Mapper
 */
@Mapper
public interface PersistentLoginMapper extends BaseMapper<PersistentLogin> {
    
    @Insert("INSERT INTO poker_persistent_logins (username, series, token, last_used) " +
            "VALUES (#{username}, #{series}, #{token}, #{lastUsed})")
    void insertPersistentLogin(PersistentLogin login);
    
    @Select("SELECT username, series, token, last_used FROM poker_persistent_logins WHERE series = #{series}")
    @Results({
        @Result(property = "username", column = "username"),
        @Result(property = "series", column = "series"),
        @Result(property = "token", column = "token"),
        @Result(property = "lastUsed", column = "last_used")
    })
    PersistentLogin selectBySeries(@Param("series") String series);
    
    @Select("SELECT username, series, token, last_used FROM poker_persistent_logins WHERE username = #{username}")
    @Results({
        @Result(property = "username", column = "username"),
        @Result(property = "series", column = "series"),
        @Result(property = "token", column = "token"),
        @Result(property = "lastUsed", column = "last_used")
    })
    java.util.List<PersistentLogin> selectByUsername(@Param("username") String username);
    
    @Update("UPDATE poker_persistent_logins SET token = #{token}, last_used = #{lastUsed} WHERE series = #{series}")
    void updateTokenBySeries(@Param("series") String series, 
                            @Param("token") String token, 
                            @Param("lastUsed") LocalDateTime lastUsed);
    
    @Delete("DELETE FROM poker_persistent_logins WHERE series = #{series}")
    void deleteBySeries(@Param("series") String series);
    
    @Delete("DELETE FROM poker_persistent_logins WHERE username = #{username}")
    void deleteByUsername(@Param("username") String username);
}
