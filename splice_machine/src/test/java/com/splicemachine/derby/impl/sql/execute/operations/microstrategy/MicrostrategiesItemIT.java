/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.impl.sql.execute.operations.microstrategy;

import org.spark_project.guava.collect.Lists;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.derby.test.framework.tables.SpliceItemTable;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * @author Scott Fines
 *         Created on: 2/24/13
 */
public class MicrostrategiesItemIT extends SpliceUnitTest { 
    protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher();
	public static final String CLASS_NAME = MicrostrategiesItemIT.class.getSimpleName().toUpperCase();
	public static final String TABLE_NAME = "A";
	protected static SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(CLASS_NAME);	
	protected static SpliceItemTable spliceTableWatcher = new SpliceItemTable(TABLE_NAME,CLASS_NAME); 	
	@ClassRule 
	public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
		.around(spliceSchemaWatcher)
		.around(spliceTableWatcher);
	
	@Rule public SpliceWatcher methodWatcher = new SpliceWatcher();

    /**
     * Regression test for Bug #241. Confirms that ORDER BY does not throw an exception and
     * correctly sorts data
     */
    @Test
    public void testOrderBySorts() throws Exception{
        PreparedStatement ps = methodWatcher.prepareStatement("call SYSCS_UTIL.IMPORT_DATA (?, ?, null,?,',',null,null,null,null,1,null,true,null)");
        ps.setString(1,CLASS_NAME);
        ps.setString(2,TABLE_NAME);
        ps.setString(3,getResourceDirectory()+"item.csv");
        ps.executeQuery();
        ResultSet rs = methodWatcher.executeQuery(format("select itm_subcat_id from %s order by itm_subcat_id",this.getTableReference(TABLE_NAME)));
        List<Integer> results = Lists.newArrayList();
        int count=0;
        while(rs.next()){
            if(rs.getObject(1)==null){
                Assert.assertTrue("Sort incorrect! Null entries are not in the front of the list",count==0);
            }
            results.add(rs.getInt(1));
            count++;
        }

        //check that sort order is maintained
        for(int i=0;i<results.size()-1;i++){
            Integer first = results.get(i);
            Integer second = results.get(i+1);
            Assert.assertTrue("Sort order incorrect!",first.compareTo(second)<=0);
        }
    }
}
