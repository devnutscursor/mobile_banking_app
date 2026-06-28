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
import com.example.myapplication.database.dao.TransactionDao;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.LicenseEntity;
import com.example.myapplication.database.entities.SessionEntity;
import com.example.myapplication.database.entities.CredentialEntity;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.BalanceAdjustmentEntity;
import com.example.myapplication.database.entities.CommissionRateEntity;
import com.example.myapplication.database.entities.CommissionEntity;
import com.example.myapplication.database.entities.OperatorBalanceEntity;
import com.example.myapplication.database.dao.OperatorDao;
import com.example.myapplication.database.dao.OperatorActionDao;
import com.example.myapplication.database.dao.OperatorBalanceDao;
import com.example.myapplication.database.dao.BalanceAdjustmentDao;
import com.example.myapplication.database.dao.CommissionRateDao;
import com.example.myapplication.database.dao.CommissionDao;

@Database(
    entities = {UserEntity.class, LicenseEntity.class, SessionEntity.class, CredentialEntity.class, CustomerEntity.class,
            OperatorEntity.class, OperatorActionEntity.class, TransactionEntity.class, BalanceAdjustmentEntity.class,
            CommissionRateEntity.class, CommissionEntity.class, OperatorBalanceEntity.class},
    version = 20,
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
    public abstract OperatorBalanceDao operatorBalanceDao();
    public abstract TransactionDao transactionDao();
    public abstract BalanceAdjustmentDao balanceAdjustmentDao();
    public abstract CommissionRateDao commissionRateDao();
    public abstract CommissionDao commissionDao();
    
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
        if (INSTANCE != null) {
            INSTANCE.close();
        INSTANCE = null;
        }
    }
    
    /**
     * Clear all data from the database and recreate it
     * WARNING: This will delete all local data!
     */
    public static void clearDatabase(Context context) {
        destroyInstance();
        context.deleteDatabase(DATABASE_NAME);
        // Also delete any journal files
        String[] dbFiles = context.databaseList();
        for (String dbFile : dbFiles) {
            if (dbFile.startsWith(DATABASE_NAME)) {
                context.deleteDatabase(dbFile.replace(".db", "").replace("-wal", "").replace("-shm", ""));
            }
        }
        // Clear the instance so it will be recreated
        INSTANCE = null;
        android.util.Log.d("AppDatabase", "Database cleared and will be recreated on next access");
    }
}



