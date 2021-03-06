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

import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.metadata.mnode.MNode;

import java.io.IOException;
import java.util.*;

public class MetaFile implements MetaFileAccess {

  private final MTreeFile mTreeFile;

  public MetaFile(String mTreeFilePath) throws IOException {
    mTreeFile = new MTreeFile(mTreeFilePath);
  }

  @Override
  public MNode readRoot() throws IOException {
    return mTreeFile.read(PersistenceInfo.createPersistenceInfo(mTreeFile.getRootPosition()));
  }

  @Override
  public MNode read(PersistenceInfo persistenceInfo) throws IOException {
    return mTreeFile.read(persistenceInfo);
  }

  @Override
  public void write(MNode mNode) throws IOException {
    mTreeFile.write(mNode);
  }

  @Override
  public void write(Collection<MNode> mNodes) throws IOException {
    allocateFreePos(mNodes);
    for (MNode mNode : mNodes) {
      write(mNode);
    }
  }

  @Override
  public void remove(PersistenceInfo persistenceInfo) throws IOException {
    mTreeFile.remove(persistenceInfo);
  }

  @Override
  public void close() throws IOException {
    mTreeFile.close();
  }

  @Override
  public void sync() throws IOException {
    mTreeFile.sync();
  }

  public MNode read(String path) throws IOException {
    return mTreeFile.read(path);
  }

  public MNode readRecursively(PersistenceInfo persistenceInfo) throws IOException {
    return mTreeFile.readRecursively(persistenceInfo);
  }

  public void writeRecursively(MNode mNode) throws IOException {
    List<MNode> mNodeList = new LinkedList<>();
    flatten(mNode, mNodeList);
    write(mNodeList);
  }

  public void remove(PartialPath path) throws IOException {}

  private void flatten(MNode mNode, Collection<MNode> mNodes) {
    mNodes.add(mNode);
    for (MNode child : mNode.getChildren().values()) {
      flatten(child, mNodes);
    }
  }

  private void allocateFreePos(Collection<MNode> mNodes) throws IOException {
    for (MNode mNode : mNodes) {
      if (mNode.getPersistenceInfo() != null) {
        continue;
      }
      if (mNode.getName().equals("root")) {
        mNode.setPersistenceInfo(
            PersistenceInfo.createPersistenceInfo(mTreeFile.getRootPosition()));
      } else {
        mNode.setPersistenceInfo(PersistenceInfo.createPersistenceInfo(mTreeFile.getFreePos()));
      }
    }
  }
}
