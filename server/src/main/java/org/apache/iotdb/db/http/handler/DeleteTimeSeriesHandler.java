/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.http.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.iotdb.db.auth.AuthException;
import org.apache.iotdb.db.auth.AuthorityChecker;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.metadata.PathNotExistException;
import org.apache.iotdb.db.exception.metadata.StorageGroupNotSetException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.http.constant.HttpConstant;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.qp.physical.sys.DeleteTimeSeriesPlan;
import org.apache.iotdb.db.service.IoTDB;

import java.util.ArrayList;
import java.util.List;

public class DeleteTimeSeriesHandler extends Handler {
  public JsonElement handle(JsonArray json)
      throws QueryProcessException, StorageEngineException, StorageGroupNotSetException,
      AuthException, IllegalPathException, PathNotExistException {
    List<PartialPath> timeSeries = new ArrayList<>();
    for(JsonElement object : json) {
      PartialPath partialPath = new PartialPath(object.getAsString());
      if(!IoTDB.metaManager.isPathExist(partialPath)) {
        throw new PathNotExistException(partialPath.toString());
      }
      timeSeries.add(partialPath);
    }
    DeleteTimeSeriesPlan plan = new DeleteTimeSeriesPlan(timeSeries);
    if(!AuthorityChecker.check(username, plan.getPaths(), plan.getOperatorType(), null)) {
      throw new AuthException(String.format("%s can't be delete by %s", timeSeries, username));
    }
    if(!executor.processNonQuery(plan)) {
      throw new QueryProcessException(String.format("%s can't be created successfully", timeSeries));
    }
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(HttpConstant.RESULT, HttpConstant.SUCCESSFUL_OPERATION);
    return jsonObject;
  }
}