// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import com.google.gson.Gson;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.sps.data.Comment;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

@WebServlet("/data")
public final class DataServlet extends HttpServlet {

    @Override
    public void init() {
        // Only creates a new AllComments entity if one has not yet been created.
        Entity allCommentsEntity = getAllCommentsEntity();
        if (allCommentsEntity == null) {
            createAllComments();
        }
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Entity allCommentsEntity = getAllCommentsEntity();

        Query queryComments = null;
        String selectedFilter = (String) allCommentsEntity.getProperty("filter");

        // Assign the correct query to queryComments according to the filter settings in place. 
        switch (selectedFilter) {
            case "recent":
                queryComments = new Query("Comment").addSort("time", SortDirection.DESCENDING);
                break;
            case "oldest":
                queryComments = new Query("Comment").addSort("time", SortDirection.ASCENDING);
                break;
            case "top":
                queryComments = new Query("Comment").addSort("popularity", SortDirection.DESCENDING);
                break;
            case "bottom":
                queryComments = new Query("Comment").addSort("popularity", SortDirection.ASCENDING);
                break;
            case "alphabetical":
                queryComments = new Query("Comment").addSort("name", SortDirection.ASCENDING);
                break;
            default:
                Filter searchFilter = new FilterPredicate("name", FilterOperator.EQUAL, selectedFilter);
                queryComments = new Query("Comment").setFilter(searchFilter);
                break;
        }

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery resultsComments = datastore.prepare(queryComments);
        List<Comment> comments = iterateQuery(resultsComments);
        String json = convertToJsonUsingGson(comments);
        response.setContentType("application/json;");
        response.getWriter().println(json);
    }

    // Converts the comments array  into a JSON string using the Gson library.
    private String convertToJsonUsingGson(List<Comment> comments) {
        Gson gson = new Gson();
        String json = gson.toJson(comments);
        return json;
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = getParameter(request, "user-name", null);
        // If the name field was left blank, change it to Anonymous.
        if (name.equals("")) {
            name = "Anonymous";
        }
        
        String comment = getParameter(request, "user-comment", null);

        // Add comment to the datastore.
        createComment(comment, name);
        // Increase total of AllComments by 1.
        changeAllCommentsTotal(1);

        response.sendRedirect("/contact.html");
        return;
    }

    // Returns the desired parameter entered by the user, or null if the user input was invalid.
    private String getParameter(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getParameter(name);

        if (value == null) {
            return defaultValue;
        }

        return value;
    }

    // Iterates over a comments query and returns an array of comments.
    private List<Comment> iterateQuery(PreparedQuery results) {
        Entity allCommentsEntity = getAllCommentsEntity();
        long totalComments = (long) allCommentsEntity.getProperty("total");
        long page = (long) allCommentsEntity.getProperty("page");
        long maxComments = (long) allCommentsEntity.getProperty("max");
        List<Comment> comments = new ArrayList<>();
        Iterator<Entity> iter = results.asIterator();

        // Iterates over the results until the results are less than the limit on comments or until the end of all results.
        for (int count = 0; count < (maxComments * page) && count < totalComments; count++) {
            Entity entity = iter.next();

            // Only add comment when it is part of the page the user is currently in.
            if (count >= (maxComments * (page - 1))) {
                long id = entity.getKey().getId();
                String content = (String) entity.getProperty("content");
                long time = (long) entity.getProperty("time");
                long thumbsup = (long) entity.getProperty("thumbsup");
                long thumbsdown = (long) entity.getProperty("thumbsdown");
                String name = (String) entity.getProperty("name");

                Comment comment = new Comment(id, content, time, thumbsup, thumbsdown, name);
                comments.add(comment);
            }
        }

        return comments;
    }

    // Creates a Comment entity and stores it in the datastore.
    private void createComment(String comment, String name) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity commentEntity = new Entity("Comment");
        commentEntity.setProperty("content", comment);
        long timestamp = System.currentTimeMillis();
        commentEntity.setProperty("time", timestamp);
        commentEntity.setProperty("thumbsup", 0);
        commentEntity.setProperty("thumbsdown", 0);
        commentEntity.setProperty("popularity", 0);
        commentEntity.setProperty("name", name);
        datastore.put(commentEntity);
    }

    // Creates an AllComments entity and stores it in the datastore.
    private void createAllComments() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity allComments = new Entity("AllComments");
        allComments.setProperty("total", 0);
        // Comments are by default limited to 10 per page.
        allComments.setProperty("max", 10);
        allComments.setProperty("page", 1);
        // Comments are by default filtered by most recent first.
        allComments.setProperty("filter", "recent");
        datastore.put(allComments);
    }

    // Changes the value of the total property in AllComments and updates the datastore.
    private void changeAllCommentsTotal(int value) {
        Entity allCommentsEntity = getAllCommentsEntity();
        long prevTotal = (long) allCommentsEntity.getProperty("total");
        long newTotal = prevTotal + value;
        allCommentsEntity.setProperty("total", newTotal);
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        datastore.put(allCommentsEntity);
    }

    // Accesses the datastore to get the AllComments entity. Returns the entity or null if one does not exist.
    private Entity getAllCommentsEntity() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query queryAllComments = new Query("AllComments");
        PreparedQuery resultsAllComments = datastore.prepare(queryAllComments);

        // Return null if there are no AllComments entity.
        if (resultsAllComments.countEntities() == 0) {
            return null;
        }

        Iterator<Entity> iterAllComments = resultsAllComments.asIterator();
        Entity allCommentsEntity = iterAllComments.next(); 

        return allCommentsEntity;
    }
}
