package com.safepath.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.safepath.ai.models.SosEvent;
import java.util.List;

@Dao
public interface SosEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SosEvent event);

    @Update
    void update(SosEvent event);

    @Query("SELECT * FROM sos_events WHERE userId = :uid ORDER BY triggeredAt DESC")
    LiveData<List<SosEvent>> getAllForUser(String uid);

    @Query("SELECT * FROM sos_events WHERE userId = :uid ORDER BY triggeredAt DESC LIMIT 1")
    LiveData<SosEvent> getLatestEvent(String uid);

    @Query("SELECT * FROM sos_events WHERE id = :id LIMIT 1")
    SosEvent getById(long id);

    @Query("SELECT * FROM sos_events WHERE idempotencyKey = :key LIMIT 1")
    SosEvent getByIdempotencyKey(String key);

    @Query("SELECT * FROM sos_events WHERE status IN ('LOCAL_QUEUED', 'PENDING', 'FAILED') ORDER BY triggeredAt ASC")
    List<SosEvent> getPendingRetries();

    @Query("DELETE FROM sos_events WHERE id = :id")
    void deleteById(long id);
}
