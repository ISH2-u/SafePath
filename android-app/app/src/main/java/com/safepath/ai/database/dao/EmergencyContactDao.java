package com.safepath.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.safepath.ai.models.EmergencyContact;
import java.util.List;

@Dao
public interface EmergencyContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EmergencyContact contact);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EmergencyContact> contacts);

    @Update
    void update(EmergencyContact contact);

    @Delete
    void delete(EmergencyContact contact);

    @Query("DELETE FROM emergency_contacts WHERE remoteId = :remoteId")
    void deleteByRemoteId(String remoteId);

    @Query("SELECT * FROM emergency_contacts WHERE userId = :uid ORDER BY addedAt ASC")
    LiveData<List<EmergencyContact>> getForUser(String uid);

    @Query("SELECT * FROM emergency_contacts WHERE userId = :uid ORDER BY addedAt ASC")
    List<EmergencyContact> getForUserSync(String uid);

    @Query("SELECT * FROM emergency_contacts WHERE userId = :uid AND isVerified = 1 AND receiveSosAlerts = 1")
    List<EmergencyContact> getAuthorizedSosRecipients(String uid);

    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE userId = :uid AND isVerified = 1")
    int countVerified(String uid);

    @Query("DELETE FROM emergency_contacts WHERE userId = :uid")
    void deleteAllForUser(String uid);
}
