package com.disky.api.controller;

import com.disky.api.Exceptions.GetUserException;
import com.disky.api.Exceptions.PostControllerException;
import com.disky.api.filter.PostFilter;
import com.disky.api.model.Post;
import com.disky.api.model.ScoreCard;
import com.disky.api.model.User;
import com.disky.api.util.DatabaseConnection;
import com.disky.api.util.Parse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PostController {
    public static List<Post> getPost(PostFilter filter) throws PostControllerException {
        int psId = 1;
        Logger log = Logger.getLogger(String.valueOf(PostController.class));
        List<Post> postResults = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        Connection conn = DatabaseConnection.getConnection();

        try {
            String where = " WHERE 1=1 ";

            if (filter.getUser() != null && filter.getUser().getUserId() != 0L && !filter.isGetFromConnections()) {
                where += " AND posts.USER_ID = ? ";
            }

            if (filter.getType() != null && filter.getType() != 0) {
                where += " AND posts.POST_TYPE = ? ";
            }

            if (filter.getScoreCardId() != null && filter.getScoreCardId().getCardId() != 0l) {
                where += " AND posts.SCORE_CARD_LINK = ? ";
            }

            if(filter.isGetFromConnections() && filter.getUser() != null && filter.getUser().getUserId() != 0L){
                userIds = getUserRelations(filter.getUser());
                where += " AND posts.USER_ID in(" + Parse.listAsQuestionMarks(userIds)+ ") ";
            }

            String sql = " SELECT " + Post.getColumns() + " FROM posts " + where + " ORDER BY POSTED_TS; ";
            PreparedStatement stmt = conn.prepareStatement(sql);

            if (filter.getUser() != null && filter.getUser().getUserId() != 0L && !filter.isGetFromConnections()) {
                stmt.setLong(psId++, filter.getUser().getUserId());
            }

            if (filter.getType() != null && filter.getType() != 0) {
                stmt.setInt(psId++, filter.getType());
            }

            if (filter.getScoreCardId() != null && filter.getScoreCardId().getCardId() != 0l) {
                stmt.setLong(psId++, filter.getScoreCardId().getCardId());
            }

            if(filter.isGetFromConnections() && filter.getUser() != null && filter.getUser().getUserId() != 0L){
                for(Long userId : userIds){
                    stmt.setLong(psId++, userId);
                }
            }
            log.info(stmt.toString());
            ResultSet res = stmt.executeQuery();

            while (res.next()) {
                User user = UserController.getOne(new User(res.getLong("USER_ID")));
                Post post = new Post(
                        res.getLong("POST_ID"),
                        user,
                        res.getString("TEXT_MESSAGE"),
                        res.getInt("POST_TYPE"),
                        res.getLong("SCORE_CARD_LINK") == 0 ? null : new ScoreCard(res.getLong("SCORE_CARD_LINK")),
                        res.getTimestamp("POSTED_TS"),
                        res.getTimestamp("UPDATED_TS")
                );

                postResults.add(post);
            }
            log.info("Successfully retireved: " + postResults.size() + " posts.");
            return postResults;
        } catch (SQLException  | GetUserException e) {
            throw new PostControllerException(e.getMessage());
        }
    }

    public static void delete(Post post) throws PostControllerException {
        if(post.getPostId() == null) throw new PostControllerException("postId is required!");
        Logger log = Logger.getLogger(String.valueOf(PostController.class));
        Connection conn = DatabaseConnection.getConnection();
        try {
            String sql = "DELETE FROM posts WHERE POST_ID = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);

            stmt.setLong(1, post.getPostId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new PostControllerException(e.getMessage());
        }
    };

    public static void create(Post post) throws PostControllerException {
        validateObject(post);
        int psId = 1;
        Logger log = Logger.getLogger(String.valueOf(PostController.class));
        Connection conn = DatabaseConnection.getConnection();
        try {
            if(post.getPostId() != null){
                update(post);
                return;
            }
            post.setPostedTs(new Timestamp(System.currentTimeMillis()));
            post.setUpdatedTs(new Timestamp(System.currentTimeMillis()));

            String sql = "INSERT INTO posts (USER_ID,TEXT_MESSAGE, POST_TYPE, SCORE_CARD_LINK, POSTED_TS, UPDATED_TS) values (?,?,?,?,?,?)";

            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            stmt.setLong(psId++, post.getUser().getUserId());
            stmt.setString(psId++, post.getMessage());
            stmt.setInt(psId++, post.getType());
            if(post.getScoreCard() == null || post.getScoreCard().getCardId() == null){
                stmt.setNull(psId++, Types.BIGINT);
            }
            else{
                stmt.setLong(psId++, post.getScoreCard().getCardId());
            }
            stmt.setTimestamp(psId++, post.getPostedTs());
            stmt.setTimestamp(psId++, post.getUpdatedTs());
            log.info(stmt.toString());
            log.info("Rows affected: " + stmt.executeUpdate());

            ResultSet keys = stmt.getGeneratedKeys();
            if(keys.next()){
                post.setPostId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new PostControllerException(e.getMessage());
        }
    }

    private static void validateObject(Post post) throws PostControllerException {
        if(post.getUser() == null || post.getUser().getUserId() == 0l)
            throw new PostControllerException("User is required!");
        if(post.getMessage() == null || post.getMessage() == "")
            throw new PostControllerException("Message is required!");
        if(post.getType() == 0)
            throw new PostControllerException("postType is required!");
    }

    //TODO: Do this
    private static int update(Post post) throws PostControllerException {
        if(post.getPostId() == null || post.getMessage() == null) throw new PostControllerException("PostId and post message is required!");
        Logger log = Logger.getLogger(String.valueOf(PostController.class));
        Connection conn = DatabaseConnection.getConnection();
        post.setUpdatedTs(new Timestamp(System.currentTimeMillis()));

        try {
            int psId = 1;

            String sql = "UPDATE posts SET TEXT_MESSAGE = ?, UPDATED_TS = ? WHERE POST_ID = ?";

            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(psId++, post.getMessage());
            stmt.setTimestamp(psId++, post.getUpdatedTs());
            stmt.setLong(psId++, post.getPostId());

            int rowsAffected = stmt.executeUpdate();
            log.info("Rows affected: " + rowsAffected);
            return rowsAffected;
        } catch (SQLException e) {
            throw new PostControllerException(e.getMessage());
        }
    }

    private static List<Long> getUserRelations(User user) throws PostControllerException {
        List<Long> userIds = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();

        String sqlOne = "SELECT user_links.USER_ID_LINK1 AS userId FROM user_links WHERE user_links.USER_ID_LINK2 = ?";
        String sqlTwo = "SELECT user_links.USER_ID_LINK2 AS userId FROM user_links WHERE user_links.USER_ID_LINK1 = ?";

        try {
            PreparedStatement stmt1 = conn.prepareStatement(sqlOne);
            stmt1.setLong(1, user.getUserId());
            ResultSet res1 = stmt1.executeQuery();

            while (res1.next()) {
                userIds.add(res1.getLong("userId"));
            }

            PreparedStatement stmt2 = conn.prepareStatement(sqlTwo);
            stmt2.setLong(1, user.getUserId());
            ResultSet res2 = stmt2.executeQuery();

            while (res2.next()) {
                userIds.add(res2.getLong("userId"));
            }
            userIds.add(user.getUserId());

        } catch (SQLException e) {
            throw new PostControllerException(e.getMessage());
        }
        return userIds;
    }
}