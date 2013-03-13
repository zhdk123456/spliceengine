package com.splicemachine.tools;

import junit.framework.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Scott Fines
 *         Created on: 3/11/13
 */
public class ThreadSafeResourcePoolTest {
    @Test
    public void testGet() throws Exception {
        ThreadSafeResourcePool<String> test = new ThreadSafeResourcePool<String>(new ResourcePool.Generator<String>() {
            @Override
            public String makeNew(ResourcePool.Key refKey) {
                return "test "+ ((TestKey)refKey).getCount();
            }

            @Override
            public void close(String entity) {
                //no-op
            }
        });

        String one = test.get(new TestKey(1));
        String two = test.get(new TestKey(2));
        Assert.assertTrue("Same reference was returned!",!one.equals(two));
        String nextOne = test.get(new TestKey(1));
        Assert.assertTrue("Difference refernece was returned!",one==nextOne);
    }

    @Test
    public void testRelease() throws Exception {

    }

    private static class TestKey implements ResourcePool.Key{
        private final int count;

        private TestKey(int count) {
            this.count = count;
        }

        public int getCount() { return count;}
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestKey)) return false;

            TestKey testKey = (TestKey) o;

            return count == testKey.count;
        }

        @Override
        public int hashCode() {
            return count;
        }
    }
}
