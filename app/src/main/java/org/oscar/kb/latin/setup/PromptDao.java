package org.oscar.kb.latin.setup;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
@Dao
public interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Prompt prompt);

    @Query("SELECT * FROM prompt_table")
    LiveData<List<Prompt>> getAllPrompts();
}
