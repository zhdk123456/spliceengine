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

package com.splicemachine.derby.impl.sql.execute.operations;

import com.carrotsearch.hppc.BitSet;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.sql.execute.ExecIndexRow;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.store.access.Qualifier;
import com.splicemachine.db.iapi.store.access.ScanController;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.storage.DataScan;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * @author Scott Fines
 *         Created on: 10/1/13
 */
public class MultiProbeDerbyScanInformation extends DerbyScanInformation{
    private DataValueDescriptor[] probeValues;
    private DataValueDescriptor probeValue;

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2",justification = "Intentional")
    public MultiProbeDerbyScanInformation(String resultRowAllocatorMethodName,
                                          String startKeyGetterMethodName,
                                          String stopKeyGetterMethodName,
                                          String scanQualifiersField,
                                          long conglomId,
                                          int colRefItem,
                                          boolean sameStartStopPosition,
                                          int startSearchOperator,
                                          int stopSearchOperator,
                                          DataValueDescriptor[] probeValues, String tableVersion) {
        super(resultRowAllocatorMethodName, startKeyGetterMethodName, stopKeyGetterMethodName,
                scanQualifiersField, conglomId, colRefItem, -1, sameStartStopPosition, startSearchOperator, stopSearchOperator, false,tableVersion);
        this.probeValues = probeValues;
    }

    @Deprecated
    public MultiProbeDerbyScanInformation() { }

		@Override
		protected ExecIndexRow getStopPosition() throws StandardException {
				ExecIndexRow stopPosition = sameStartStopPosition?super.getStartPosition():super.getStopPosition();
				if (stopPosition != null) {
						stopPosition.getRowArray()[0] = probeValue;
				}
				return stopPosition;
		}

	@Override
	public ExecIndexRow getStartPosition() throws StandardException {
		ExecIndexRow startPosition = super.getStartPosition();
        if(sameStartStopPosition)
            startSearchOperator = ScanController.NA;
		if(startPosition!=null)
            startPosition.getRowArray()[0] = probeValue; 
		return startPosition;
	}

	@Override
    public List<DataScan> getScans(TxnView txn, ExecRow startKeyOverride, Activation activation, int[] keyDecodingMap) throws StandardException {
        /*
         * We must build the proper scan here in pieces
         */
        BitSet colsToReturn = new BitSet();
        FormatableBitSet accessedCols = getAccessedColumns();
        if (accessedCols != null) {
            for (int i = accessedCols.anySetBit(); i >= 0; i = accessedCols.anySetBit(i)) {
                colsToReturn.set(i);
            }
        }
        List<DataScan> scans = new ArrayList<DataScan>(probeValues.length);
        for (int i = 0; i < probeValues.length; i++) {
            probeValue = probeValues[i];
            DataScan scan = getScan(txn, null, keyDecodingMap, null, null);
            scans.add(scan);
        }
        return scans;
    }

	@Override
    protected Qualifier[][] populateQualifiers() throws StandardException {
		Qualifier[][] qualifiers = super.populateQualifiers();
		if(qualifiers!=null){
			/*
			 * The first qualifier is the qualifier for the start and stop keys, so
			 * set it on that field.
			 */
			Qualifier[] ands  = qualifiers[0];
			if(ands!=null){
					Qualifier first = ands[0];
					if(first!=null){
							first.clearOrderableCache();
							first.getOrderable().setValue(probeValue);
					}
			}
		}
		return qualifiers;
	}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
				super.writeExternal(out);
				out.writeInt(probeValues.length);
				for(DataValueDescriptor dvd:probeValues){
						out.writeObject(dvd);
				}
                out.writeObject(probeValue);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
				super.readExternal(in);
				probeValues = new DataValueDescriptor[in.readInt()];
				for(int i=0;i<probeValues.length;i++){
						probeValues[i] = (DataValueDescriptor)in.readObject();
				}
                probeValue = (DataValueDescriptor)in.readObject();
		}
}
