package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.SessionEntity;

@Dao
public interface SessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSession(SessionEntity session);
    
    @Update
    void updateSession(SessionEntity session);
    
    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    SessionEntity getSessionById(String id);
    
    @Query("SELECT * FROM sessions WHERE id = 'current_session' LIMIT 1")
    SessionEntity getCurrentSession();
    
    @Query("UPDATE sessions SET isLoggedIn = :isLoggedIn, lastActivityTime = :activityTime WHERE id = :id")
    void updateLoginStatus(String id, boolean isLoggedIn, long activityTime);
    
    @Query("UPDATE sessions SET lastActivityTime = :activityTime WHERE id = :id")
    void updateActivity(String id, long activityTime);
    
    @Query("UPDATE sessions SET lastOnlineSync = :syncTime WHERE id = :id")
    void updateLastOnlineSync(String id, long syncTime);
    
    @Query("DELETE FROM sessions WHERE id = :id")
    void deleteSession(String id);
    
    @Query("DELETE FROM sessions")
    void deleteAllSessions();
}



