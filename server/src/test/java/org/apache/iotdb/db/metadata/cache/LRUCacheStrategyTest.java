package org.apache.iotdb.db.metadata.cache;

import org.apache.iotdb.db.metadata.mnode.MNode;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;

public class LRUCacheStrategyTest {

  @Test
  public void testLRUEviction() {
    MNode root = getSimpleTree();
    LRUCacheStrategy lruEviction = new LRUCacheStrategy();
    lruEviction.applyChange(root);
    lruEviction.applyChange(root.getChild("s1"));
    lruEviction.applyChange(root.getChild("s2"));
    lruEviction.applyChange(root.getChild("s1").getChild("t2"));
    StringBuilder stringBuilder = new StringBuilder();
    CacheEntry entry = root.getCacheEntry();
    while (entry != null) {
      stringBuilder.append(entry.getValue().getFullPath()).append("\r\n");
      entry = entry.getPre();
    }
    Assert.assertEquals(
        "root\r\n" + "root.s1\r\n" + "root.s2\r\n" + "root.s1.t2\r\n", stringBuilder.toString());

    lruEviction.remove(root.getChild("s1"));
    stringBuilder = new StringBuilder();
    entry = root.getCacheEntry();
    while (entry != null) {
      stringBuilder.append(entry.getValue().getFullPath()).append("\r\n");
      entry = entry.getPre();
    }
    Assert.assertEquals("root\r\n" + "root.s2\r\n", stringBuilder.toString());

    Collection<MNode> collection = lruEviction.evict();
    Assert.assertTrue(collection.contains(root));
    Assert.assertTrue(collection.contains(root.getChild("s2")));
    Assert.assertFalse(collection.contains(root.getChild("s1")));
  }

  private MNode getSimpleTree() {
    MNode root = new MNode(null, "root");
    root.addChild("s1", new MNode(root, "s1"));
    root.addChild("s2", new MNode(root, "s2"));
    root.getChild("s1").addChild("t1", new MNode(root.getChild("s1"), "t1"));
    root.getChild("s1").addChild("t2", new MNode(root.getChild("s1"), "t2"));
    root.getChild("s1")
        .getChild("t2")
        .addChild("z1", new MNode(root.getChild("s1").getChild("t2"), "z1"));
    root.getChild("s2").addChild("t1", new MNode(root.getChild("s2"), "t1"));
    root.getChild("s2").addChild("t2", new MNode(root.getChild("s2"), "t2"));
    return root;
  }
}