package org.apache.iotdb.db.metadata.metadisk;

import org.apache.iotdb.db.metadata.mnode.InternalMNode;
import org.apache.iotdb.db.metadata.mnode.MNode;
import org.apache.iotdb.db.metadata.mnode.MeasurementMNode;
import org.apache.iotdb.db.metadata.mnode.StorageGroupMNode;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class MetadataDiskManagerTest {

  private static final int CACHE_SIZE = 10;
  private static final String BASE_PATH = MetadataDiskManagerTest.class.getResource("").getPath();
  private static final String METAFILE_FILEPATH =
      BASE_PATH + "MetadataDiskManagerTest_metafile.bin";
  private String SNAPSHOT_PATH = METAFILE_FILEPATH + ".snapshot.bin";
  private String SNAPSHOT_TEMP_PATH = SNAPSHOT_PATH + ".tmp";

  @Before
  public void setUp() {
    EnvironmentUtils.envSetUp();
    File file = new File(METAFILE_FILEPATH);
    if (file.exists()) {
      file.delete();
    }
    file = new File(SNAPSHOT_PATH);
    if (file.exists()) {
      file.delete();
    }
    file = new File(SNAPSHOT_TEMP_PATH);
    if (file.exists()) {
      file.delete();
    }
  }

  @After
  public void tearDown() throws Exception {
    File file = new File(METAFILE_FILEPATH);
    if (file.exists()) {
      file.delete();
    }
    file = new File(SNAPSHOT_PATH);
    if (file.exists()) {
      file.delete();
    }
    file = new File(SNAPSHOT_TEMP_PATH);
    if (file.exists()) {
      file.delete();
    }
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testRecover() throws Exception {
    MetadataDiskManager manager = new MetadataDiskManager(0, METAFILE_FILEPATH);
    MNode root = manager.getRoot();
    MNode sg = new StorageGroupMNode(null, "sg", 0);
    manager.addChild(root, "sg", sg);
    MNode device = new InternalMNode(null, "device");
    manager.addChild(sg, "device", device);
    MNode measurement = new MeasurementMNode(null, "t1", new MeasurementSchema(), null);
    manager.addChild(device, "t1", measurement);
    manager.createSnapshot();
    manager.clear();

    MetadataDiskManager recoverManager = new MetadataDiskManager(4, METAFILE_FILEPATH);
    root = recoverManager.getRoot();
    sg = recoverManager.getChild(root, "sg");
    device = recoverManager.getChild(sg, "device");
    measurement = recoverManager.getChild(device, "t1");
    Assert.assertEquals("root", root.getName());
    Assert.assertEquals("sg", sg.getName());
    Assert.assertTrue(sg.isStorageGroup());
    Assert.assertEquals("device", device.getName());
    Assert.assertEquals("t1", measurement.getName());
    Assert.assertTrue(measurement.isMeasurement());
    recoverManager.clear();
  }
}
