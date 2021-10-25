package com.disky.api.filter;

import com.disky.api.model.Arena;
import com.disky.api.model.User;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class ScoreCardFilter {
    private User user; //Required
    private Arena arena;
    private User member;
    private boolean getMembers = true;
}
