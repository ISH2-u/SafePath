package com.safepath.ai.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.safepath.ai.models.User;
import com.safepath.ai.models.EmergencyContact;
import com.safepath.ai.models.Journey;
import com.safepath.ai.models.SosEvent;
import com.safepath.ai.database.dao.UserDao;
import com.safepath.ai.database.dao.EmergencyContactDao;
import com.safepath.ai.database.dao.JourneyDao;
import com.safepath.ai.database.dao.SosEventDao;

/**
 * SafePathDatabase — Room database, single-instance (singleton).
 * Add new entities to the @Database annotation and increment version on schema change.
 */
@Database(
    entities = {User.class, EmergencyContact.class, Journey.class, SosEvent.class},
    version = 1,
    exportSchema = false
)
public abstract class SafePathDatabase extends RoomDatabase {

    private static volatile SafePathDatabase INSTANCE;
    private static final String DB_NAME = "safepath_db";

    public abstract UserDao userDao();
    public abstract EmergencyContactDao emergencyContactDao();
    public abstract JourneyDao journeyDao();
    public abstract SosEventDao sosEventDao();

    public static SafePathDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SafePathDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SafePathDatabase.class,
                            DB_NAME
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}

