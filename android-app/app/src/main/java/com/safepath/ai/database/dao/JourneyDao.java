package com.safepath.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.safepath.ai.models.Journey;
import java.util.List;

@Dao
public interface JourneyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Journey journey);

    @Update
    void update(Journey journey);

    @Query("SELECT * FROM journeys WHERE userId = :uid ORDER BY startedAt DESC")
    LiveData<List<Journey>> getAllForUser(String uid);

    @Query("SELECT * FROM journeys WHERE userId = :uid AND status = 'ACTIVE' LIMIT 1")
    LiveData<Journey> getActiveJourney(String uid);

    @Query("SELECT * FROM journeys WHERE status = 'ACTIVE' LIMIT 1")
    Journey getActiveJourneySync();

    @Query("SELECT * FROM journeys WHERE id = :id LIMIT 1")
    Journey getJourneyById(long id);

    @Query("UPDATE journeys SET endedAt = :endTime, endLat = :lat, endLng = :lng, status = 'COMPLETED' WHERE id = :id")
    void completeJourney(long id, long endTime, double lat, double lng);

    @Query("UPDATE journeys SET waypointsJson = :waypointsJson, distanceMeters = :distance WHERE id = :id")
    void updateWaypoints(long id, String waypointsJson, float distance);

    @Query("DELETE FROM journeys WHERE id = :id")
    void deleteById(long id);
}
