package com.splicemachine.derby.jdbc;

import com.splicemachine.SQLConfiguration;
import com.splicemachine.db.iapi.error.PublicAPI;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.Attribute;
import com.splicemachine.db.iapi.reference.Property;
import com.splicemachine.db.iapi.services.context.ContextManager;
import com.splicemachine.db.iapi.services.context.ContextService;
import com.splicemachine.db.iapi.services.monitor.Monitor;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.util.IdUtil;
import com.splicemachine.db.jdbc.InternalDriver;
import com.splicemachine.derby.impl.db.SpliceDatabase;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManager;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.Properties;

public final class SpliceTransactionResourceImpl implements AutoCloseable{
    private static final Logger LOG=Logger.getLogger(SpliceTransactionResourceImpl.class);
    protected ContextManager cm;
    protected ContextService csf;
    protected String username;
    private String dbname;
    private String drdaID;
    protected SpliceDatabase database;
    protected LanguageConnectionContext lcc;
    private boolean generateLcc = true;

    public SpliceTransactionResourceImpl() throws SQLException{
        this("jdbc:splice:"+SQLConfiguration.SPLICE_DB+";create=true",new Properties());
    }

    public SpliceTransactionResourceImpl(String url,Properties info) throws SQLException{
        SpliceLogUtils.debug(LOG,"instance with url %s and properties %s",url,info);
        csf=ContextService.getFactory(); // Singleton - Not Needed
        dbname=InternalDriver.getDatabaseName(url,info); // Singleton - Not Needed
        username=IdUtil.getUserNameFromURLProps(info); // Static
        drdaID=info.getProperty(Attribute.DRDAID_ATTR,null); // Static

        database=(SpliceDatabase)Monitor.findService(Property.DATABASE_MODULE,dbname);
        if(database==null){
            SpliceLogUtils.debug(LOG,"database has not yet been created, creating now");
            try{
                if(!Monitor.startPersistentService(dbname,info)){
                    throw new IllegalArgumentException("Unable to start database!");
                }
                database=(SpliceDatabase)Monitor.findService(Property.DATABASE_MODULE,dbname);
            }catch(StandardException e){
                SpliceLogUtils.error(LOG,e);
                throw PublicAPI.wrapStandardException(e);
            }
        }
    }

    public boolean marshallTransaction(TxnView txn) throws StandardException, SQLException{
        if(LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG,"marshallTransaction with transactionID %s",txn);

        ContextManager ctxM = csf.getCurrentContextManager();
        if(ctxM!=null){
            LanguageConnectionContext possibleLcc = (LanguageConnectionContext) ctxM.getContext(LanguageConnectionContext.CONTEXT_ID);
            if(((SpliceTransactionManager)possibleLcc.getTransactionExecute()).getActiveStateTxn().equals(txn)){
                cm=ctxM;
                generateLcc=false;
                lcc=possibleLcc;
                return false;
            }
        }
        cm=csf.newContextManager(); // Needed
        cm.setActiveThread();
        csf.setCurrentContextManager(cm);
        lcc = database.generateLanguageConnectionContext(txn,cm,username,drdaID,dbname);
        return true;
    }


    public void close(){
        if(generateLcc){
            while(!cm.isEmpty()){
                cm.popContext();
            }
            csf.resetCurrentContextManager(cm);
            csf.forceRemoveContext(cm);
        }
    }

    public LanguageConnectionContext getLcc(){
        return lcc;
    }

    public void resetContextManager(){
        if(generateLcc)
            csf.forceRemoveContext(cm);
    }

    public void prepareContextManager(){
        if(!generateLcc) return;

        cm.setActiveThread();
        csf.setCurrentContextManager(cm);
    }

    public void popContextManager(){
        if(generateLcc)
            csf.resetCurrentContextManager(cm);
    }
}

