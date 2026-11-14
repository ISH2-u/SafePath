package com.safepath.ai.database.dao;
import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.safepath.ai.models.User;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insert(User user);
    @Update void update(User user);
    @Delete void delete(User user);
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1") LiveData<User> getById(String userId);
    @Query("DELETE FROM users") void deleteAll();
}

