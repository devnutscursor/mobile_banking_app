package com.example.myapplication.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import com.example.myapplication.database.dao.UserDao;
import com.example.myapplication.database.dao.LicenseDao;
import com.example.myapplication.database.dao.SessionDao;
import com.example.myapplication.database.dao.CredentialDao;
import com.example.myapplication.database.dao.CustomerDao;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.LicenseEntity;
import com.example.myapplication.database.entities.SessionEntity;
import com.example.myapplication.database.entities.CredentialEntity;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.dao.OperatorDao;
import com.example.myapplication.database.dao.OperatorActionDao;

@Database(
    entities = {UserEntity.class, LicenseEntity.class, SessionEntity.class, CredentialEntity.class, CustomerEntity.class,
            OperatorEntity.class, OperatorActionEntity.class},
    version = 6,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "mobile_banking_db";
    
    public abstract UserDao userDao();
    public abstract LicenseDao licenseDao();
    public abstract SessionDao sessionDao();
    public abstract CredentialDao credentialDao();
    public abstract CustomerDao customerDao();
    public abstract OperatorDao operatorDao();
    public abstract OperatorActionDao operatorActionDao();
    
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
                    .fallbackToDestructiveMigration() // Handle version changes by recreating database
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



