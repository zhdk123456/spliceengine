package com.splicemachine.derby.impl.sql.catalog;

import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.services.sanity.SanityManager;
import org.apache.derby.iapi.services.uuid.UUIDFactory;
import org.apache.derby.iapi.sql.dictionary.*;
import org.apache.derby.iapi.sql.execute.ExecIndexRow;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.ScanQualifier;
import org.apache.derby.iapi.store.access.ScanController;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.Orderable;
import org.apache.derby.impl.sql.catalog.*;

import java.util.List;
import java.util.Properties;

/**
 * @author Scott Fines
 *         Created on: 2/28/13
 */
public class SpliceDataDictionary extends DataDictionaryImpl {
    public static final int SYSPRIMARYKEYS_CATALOG_NUM = 23;
    private volatile TabInfoImpl pkTable = null;

    @Override
    protected SystemProcedureGenerator getSystemProcedures() {
        return new SpliceSystemProcedures(this);
    }

    @Override
    public SubKeyConstraintDescriptor getSubKeyConstraint(UUID constraintId,
                                                          int type) throws StandardException {
        if(type == DataDictionary.PRIMARYKEY_CONSTRAINT){
            DataValueDescriptor constraintIDOrderable = getIDValueAsCHAR(constraintId);

            TabInfoImpl ti = getPkTable();
            faultInTabInfo(ti);

            SYSPRIMARYKEYSRowFactory rf = (SYSPRIMARYKEYSRowFactory) ti.getCatalogRowFactory();
            ScanQualifier[][] scanQualifiers = exFactory.getScanQualifier(1);
            scanQualifiers[0][0].setQualifier(
               SYSPRIMARYKEYSRowFactory.SYSPRIMARYKEYS_CONSTRAINTID-1,
                    constraintIDOrderable,
                    Orderable.ORDER_OP_EQUALS,
                    false, false, false);
            return (SubKeyConstraintDescriptor)getDescriptorViaHeap(
                    null,scanQualifiers,ti,null,null);
        }
        /*If it's a foreign key or unique constraint, then just do the derby default*/
        return super.getSubKeyConstraint(constraintId,type);
    }

    @Override
    protected void addSubKeyConstraint(KeyConstraintDescriptor descriptor,
                                       TransactionController tc)
            throws StandardException {
        ExecRow row;
        TabInfoImpl	ti;

		/*
		** Foreign keys get a row in SYSFOREIGNKEYS, and
		** all others get a row in SYSKEYS.
		*/
        if (descriptor.getConstraintType()
                == DataDictionary.FOREIGNKEY_CONSTRAINT) {
            if (SanityManager.DEBUG) {
                if (!(descriptor instanceof ForeignKeyConstraintDescriptor)) {
                    SanityManager.THROWASSERT("descriptor not an fk descriptor, is "+
                            descriptor.getClass().getName());
                }
            }
            ForeignKeyConstraintDescriptor fkDescriptor =
                    (ForeignKeyConstraintDescriptor)descriptor;

            ti = getNonCoreTI(SYSFOREIGNKEYS_CATALOG_NUM);
            SYSFOREIGNKEYSRowFactory fkkeysRF = (SYSFOREIGNKEYSRowFactory)ti.getCatalogRowFactory();

            row = fkkeysRF.makeRow(fkDescriptor, null);

			/*
			** Now we need to bump the reference count of the
			** contraint that this FK references
			*/
            ReferencedKeyConstraintDescriptor refDescriptor =
                    fkDescriptor.getReferencedConstraint();

            refDescriptor.incrementReferenceCount();

            int[] colsToSet = new int[1];
            colsToSet[0] = SYSCONSTRAINTSRowFactory.SYSCONSTRAINTS_REFERENCECOUNT;

            updateConstraintDescriptor(refDescriptor,
                    refDescriptor.getUUID(),
                    colsToSet,
                    tc);
        }else if (descriptor.getConstraintType()
                ==DataDictionary.PRIMARYKEY_CONSTRAINT){
            ti = getPkTable();
            faultInTabInfo(ti);
            SYSPRIMARYKEYSRowFactory pkRF = (SYSPRIMARYKEYSRowFactory)ti.getCatalogRowFactory();

            row = pkRF.makeRow(descriptor,null);
        } else {
            ti = getNonCoreTI(SYSKEYS_CATALOG_NUM);
            SYSKEYSRowFactory keysRF = (SYSKEYSRowFactory) ti.getCatalogRowFactory();

            // build the row to be stuffed into SYSKEYS
            row = keysRF.makeRow(descriptor, null);
        }

        // insert row into catalog and all its indices
        ti.insertRow(row, tc);
    }

    private TabInfoImpl getPkTable() throws StandardException {
        if(pkTable ==null){
            pkTable = new TabInfoImpl(new SYSPRIMARYKEYSRowFactory(uuidFactory,exFactory,dvf));
        }
        initSystemIndexVariables(pkTable);
        return pkTable;
    }

    @Override
    protected void createDictionaryTables(Properties params,
                                          TransactionController tc,
                                          DataDescriptorGenerator ddg)
            throws StandardException {
        super.createDictionaryTables(params, tc, ddg);

        //create SYSPRIMARYKEYS


        makeCatalog(getPkTable(), getSystemSchemaDescriptor(), tc);
    }

}
