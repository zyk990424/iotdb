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

package org.apache.iotdb.cluster.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.rpc.thrift.RaftService;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.db.utils.CommonUtils;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.THsHaServer.Args;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RaftServer works as a broker (network and protocol layer) that sends the requests to the proper
 * RaftMembers to process.
 */
public abstract class RaftServer implements RaftService.AsyncIface, RaftService.Iface {

  private static final Logger logger = LoggerFactory.getLogger(RaftServer.class);
  private static int connectionTimeoutInMS =
      ClusterDescriptor.getInstance().getConfig().getConnectionTimeoutInMS();
  private static int queryTimeoutInSec =
      ClusterDescriptor.getInstance().getConfig().getQueryTimeoutInSec();
  private static int syncLeaderMaxWaitMs = 20 * 1000;
  private static long heartBeatIntervalMs = 1000L;

  ClusterConfig config = ClusterDescriptor.getInstance().getConfig();
  // the socket poolServer will listen to
  private TServerTransport socket;
  // RPC processing server
  private TServer poolServer;
  Node thisNode;

  TProtocolFactory protocolFactory = config.isRpcThriftCompressionEnabled() ?
      new TCompactProtocol.Factory() : new TBinaryProtocol.Factory();

  // this thread pool is to run the thrift server (poolServer above)
  private ExecutorService clientService;

  RaftServer() {
    thisNode = new Node();
    thisNode.setIp(config.getLocalIP());
    thisNode.setMetaPort(config.getLocalMetaPort());
    thisNode.setDataPort(config.getLocalDataPort());
  }

  RaftServer(Node thisNode) {
    this.thisNode = thisNode;
  }

  public static int getConnectionTimeoutInMS() {
    return connectionTimeoutInMS;
  }

  public static void setConnectionTimeoutInMS(int connectionTimeoutInMS) {
    RaftServer.connectionTimeoutInMS = connectionTimeoutInMS;
  }

  public static int getQueryTimeoutInSec() {
    return queryTimeoutInSec;
  }

  public static int getSyncLeaderMaxWaitMs() {
    return syncLeaderMaxWaitMs;
  }

  public static void setSyncLeaderMaxWaitMs(int syncLeaderMaxWaitMs) {
    RaftServer.syncLeaderMaxWaitMs = syncLeaderMaxWaitMs;
  }

  public static long getHeartBeatIntervalMs() {
    return heartBeatIntervalMs;
  }

  public static void setHeartBeatIntervalMs(long heartBeatIntervalMs) {
    RaftServer.heartBeatIntervalMs = heartBeatIntervalMs;
  }

  /**
   * Establish a thrift server with the configurations in ClusterConfig to listen to and respond to
   * thrift RPCs. Calling the method twice does not induce side effects.
   *
   * @throws TTransportException
   */
  @SuppressWarnings("java:S1130") // thrown in override method
  public void start() throws TTransportException, StartupException {
    if (poolServer != null) {
      return;
    }

    establishServer();
  }

  /**
   * Stop the thrift server, close the socket and interrupt all in progress RPCs. Calling the method
   * twice does not induce side effects.
   */
  public void stop() {
    if (poolServer == null) {
      return;
    }

    poolServer.stop();
    socket.close();
    clientService.shutdownNow();
    socket = null;
    poolServer = null;
  }

  /**
   * @return An AsyncProcessor that contains the extended interfaces of a non-abstract subclass of
   * RaftService (DataService or MetaService).
   */
  abstract TProcessor getProcessor();

  /**
   * @return A socket that will be used to establish a thrift server to listen to RPC requests.
   * DataServer and MetaServer use different port, so this is to be determined.
   * @throws TTransportException
   */
  abstract TServerTransport getServerSocket() throws TTransportException;

  /**
   * Each thrift RPC request will be processed in a separate thread and this will return the name
   * prefix of such threads. This is used to fast distinguish DataServer and MetaServer in the logs
   * for the sake of debug.
   *
   * @return name prefix of RPC processing threads.
   */
  abstract String getClientThreadPrefix();

  /**
   * The thrift server will be run in a separate thread, and this will be its name. It help you
   * locate the desired logs quickly when debugging.
   *
   * @return The name of the thread running the thrift server.
   */
  abstract String getServerClientName();

  private TServer getAsyncServer() throws TTransportException {
    socket = getServerSocket();
    Args poolArgs =
        new THsHaServer.Args((TNonblockingServerTransport) socket)
            .maxWorkerThreads(config.getMaxConcurrentClientNum())
            .minWorkerThreads(CommonUtils.getCpuCores());

    poolArgs.executorService(new ThreadPoolExecutor(poolArgs.minWorkerThreads,
        poolArgs.maxWorkerThreads, poolArgs.getStopTimeoutVal(), poolArgs.getStopTimeoutUnit(),
        new SynchronousQueue<>(), new ThreadFactory() {
      private AtomicLong threadIndex = new AtomicLong(0);

      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, getClientThreadPrefix() + threadIndex.incrementAndGet());
      }
    }));
    poolArgs.processor(getProcessor());
    poolArgs.protocolFactory(protocolFactory);
    // async service requires FramedTransport
    poolArgs.transportFactory(new TFastFramedTransport.Factory(
        IoTDBDescriptor.getInstance().getConfig().getThriftInitBufferSize(),
        IoTDBDescriptor.getInstance().getConfig().getThriftMaxFrameSize()));

    // run the thrift server in a separate thread so that the main thread is not blocked
    return new THsHaServer(poolArgs);
  }

  private TServer getSyncServer() throws TTransportException {
    socket = getServerSocket();
    TThreadPoolServer.Args poolArgs =
        new TThreadPoolServer.Args(socket).maxWorkerThreads(config.getMaxConcurrentClientNum())
            .minWorkerThreads(CommonUtils.getCpuCores());

    poolArgs.executorService(new ThreadPoolExecutor(poolArgs.minWorkerThreads,
        poolArgs.maxWorkerThreads, poolArgs.stopTimeoutVal, poolArgs.stopTimeoutUnit,
        new SynchronousQueue<>(), new ThreadFactory() {
      private AtomicLong threadIndex = new AtomicLong(0);

      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, getClientThreadPrefix() + threadIndex.incrementAndGet());
      }
    }));
    poolArgs.processor(getProcessor());
    poolArgs.protocolFactory(protocolFactory);
    // async service requires FramedTransport
    poolArgs.transportFactory(new TFastFramedTransport.Factory(
        IoTDBDescriptor.getInstance().getConfig().getThriftInitBufferSize(),
        IoTDBDescriptor.getInstance().getConfig().getThriftMaxFrameSize()));

    // run the thrift server in a separate thread so that the main thread is not blocked
    return new TThreadPoolServer(poolArgs);
  }

  private void establishServer() throws TTransportException {
    logger.info("Cluster node {} begins to set up", thisNode);

    if (ClusterDescriptor.getInstance().getConfig().isUseAsyncServer()) {
      poolServer = getAsyncServer();
    } else {
      poolServer = getSyncServer();
    }

    clientService = Executors.newSingleThreadExecutor(r -> new Thread(r, getServerClientName()));
    clientService.submit(() -> poolServer.serve());

    logger.info("Cluster node {} is up", thisNode);
  }
}