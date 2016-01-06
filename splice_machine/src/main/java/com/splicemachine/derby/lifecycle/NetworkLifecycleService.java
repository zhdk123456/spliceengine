package com.splicemachine.derby.lifecycle;

import com.splicemachine.SQLConfiguration;
import com.splicemachine.access.api.SConfiguration;
import com.splicemachine.db.drda.NetworkServerControl;
import com.splicemachine.derby.logging.DerbyOutputLoggerWriter;
import com.splicemachine.lifecycle.DatabaseLifecycleService;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.log4j.Logger;

import javax.management.MBeanServer;
import java.net.InetAddress;

/**
 * @author Scott Fines
 *         Date: 1/6/16
 */
public class NetworkLifecycleService implements DatabaseLifecycleService{
    private static final Logger LOG=Logger.getLogger(NetworkLifecycleService.class);
    private final SConfiguration config;
    private NetworkServerControl server;

    public NetworkLifecycleService(SConfiguration config){
        this.config=config;
    }

    @Override
    public void start() throws Exception{
        try {

            String bindAddress = config.getString(SQLConfiguration.NETWORK_BIND_ADDRESS);
            int bindPort = config.getInt(SQLConfiguration.NETWORK_BIND_PORT);
            server = new NetworkServerControl(InetAddress.getByName(bindAddress),bindPort);
            server.setLogConnections(true);
            server.start(new DerbyOutputLoggerWriter());
            SpliceLogUtils.info(LOG, "Ready to accept JDBC connections on %s:%s", bindAddress,bindPort);
        } catch (Exception e) {
            SpliceLogUtils.error(LOG, "Unable to start Client/Server Protocol", e);
            throw e;
        }

    }

    @Override
    public void registerJMX(MBeanServer mbs) throws Exception{
    }

    @Override
    public void shutdown() throws Exception{
        server.shutdown();
    }
}
