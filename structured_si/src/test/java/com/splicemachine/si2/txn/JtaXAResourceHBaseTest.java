package com.splicemachine.si2.txn;

import com.splicemachine.si2.HStoreSetup;
import com.splicemachine.si2.TransactorSetup;
import com.splicemachine.si2.data.hbase.TransactorFactory;
import com.splicemachine.si2.impl.TransactorFactoryImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class JtaXAResourceHBaseTest extends JtaXAResourceTest {
    @BeforeClass
    public static void setUp() {
        storeSetup = new HStoreSetup();
        transactorSetup = new TransactorSetup(storeSetup);
        TransactorFactory.setDefaultTransactor(transactor);
        TransactorFactoryImpl.setTransactor(transactor);
        baseSetUp();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        storeSetup.getTestCluster().shutdownMiniCluster();
    }

}
