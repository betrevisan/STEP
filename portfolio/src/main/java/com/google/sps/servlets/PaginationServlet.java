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
import com.google.sps.data.AllComments;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;

@WebServlet("/pagination")
public final class PaginationServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Entity allCommentsEntity = getAllCommentsEntity();

        // Initialize AllComments object to be passed as a json.
        List<AllComments> allComments = new ArrayList<>();
        long total = (long) allCommentsEntity.getProperty("total");
        long max = (long) allCommentsEntity.getProperty("max");
        long page = (long) allCommentsEntity.getProperty("page");
        String filter = (String) allCommentsEntity.getProperty("filter");
        allComments.add(new AllComments(total, max, page, filter));
        
        // Convert to json.
        String json = convertToJsonUsingGson(allComments);
        response.setContentType("application/json;");
        response.getWriter().println(json);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Entity allCommentsEntity = getAllCommentsEntity();

        // Update the page property of AllComments.
        long newPage = Long.parseLong(request.getParameter("i")) + 1;
        allCommentsEntity.setProperty("page", newPage);
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        datastore.put(allCommentsEntity);

        response.sendRedirect("/contact.html");
        return;
    }

    //Converts the comments array  into a JSON string using the Gson library.
    private String convertToJsonUsingGson(List<AllComments> allComments) {
        Gson gson = new Gson();
        String json = gson.toJson(allComments);
        return json;
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
