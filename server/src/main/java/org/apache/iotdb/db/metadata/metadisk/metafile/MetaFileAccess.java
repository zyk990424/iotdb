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
package org.apache.iotdb.db.metadata.metadisk.metafile;

import org.apache.iotdb.db.metadata.mnode.MNode;

import java.io.IOException;
import java.util.Collection;

/** this interface provides mnode IO operation on a file/disk */
public interface MetaFileAccess {

  MNode readRoot() throws IOException;

  MNode read(PersistenceInfo persistenceInfo) throws IOException;

  void write(MNode mNode) throws IOException;

  void write(Collection<MNode> mNodes) throws IOException;

  void remove(PersistenceInfo persistenceInfo) throws IOException;

  void close() throws IOException;

  void sync() throws IOException;
}
