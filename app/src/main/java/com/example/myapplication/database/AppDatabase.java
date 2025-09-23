package com.example.myapplication.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import com.example.myapplication.database.dao.UserDao;
import com.example.myapplication.database.dao.LicenseDao;
import com.example.myapplication.database.dao.SessionDao;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.LicenseEntity;
import com.example.myapplication.database.entities.SessionEntity;

@Database(
    entities = {UserEntity.class, LicenseEntity.class, SessionEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "mobile_banking_db";
    
    public abstract UserDao userDao();
    public abstract LicenseDao licenseDao();
    public abstract SessionDao sessionDao();
    
    public static AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries() // For simplicity, but should use background threads in production
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    public static void destroyInstance() {
        INSTANCE = null;
    }
}



