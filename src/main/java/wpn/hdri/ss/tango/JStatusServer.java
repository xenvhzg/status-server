package wpn.hdri.ss.tango;

import com.google.common.collect.Maps;
import org.tango.DeviceState;
import org.tango.server.ServerManager;
import org.tango.server.annotation.*;
import wpn.hdri.ss.data.Timestamp;
import wpn.hdri.ss.engine.Engine;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StatusServer Tango implementation based on the new JTangoServer library
 *
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 17.06.13
 */
@Device
public class JStatusServer {
    private static interface Status {
        String IDLE = "IDLE";
        String LIGHT_POLLING = "LIGHT_POLLING";
        String LIGHT_POLLING_AT_FIXED_RATE = "LIGHT_POLLING_AT_FIXED_RATE";
        String HEAVY_DUTY = "HEAVY_DUTY";
    }

    private final AtomicLong clientId = new AtomicLong(0);
    /**
     * This field tracks timestamps of the clients and is used in getXXXUpdates methods
     */
    private final ConcurrentMap<Long,Timestamp> timestamps = Maps.newConcurrentMap();


    private Engine engine;

    public JStatusServer() {
        System.out.println("Create instance");
    }

    @State
    private DeviceState state = DeviceState.OFF;

    public DeviceState getState() {
        return state;
    }

    public void setState(DeviceState state) {
        this.state = state;
    }

    @org.tango.server.annotation.Status
    private String status = Status.IDLE;

    public void setStatus(String v){
        this.status = v;
    }

    public String getStatus(){
        return this.status;
    }

    @Init
    @StateMachine(endState = DeviceState.ON)
    public void init() throws Exception{
        //TODO init engine

    }

    //TODO attributes
    //TODO make useAliases client specific
    @Attribute
    private boolean useAliases = false;

    public void setUseAliases(boolean v){
        this.useAliases = v;
    }

    public boolean isUseAliases(){
        return this.useAliases;
    }

    @Attribute
    private String crtActivity;

    public String getCrtActivity(){
        return engine.getCurrentActivity();
    }

    @Attribute
    private long crtTimestamp;

    public long getCrtTimestamp(){
        return System.currentTimeMillis();
    }

    @Attribute
    private String dataEncoded;

    public String getDataEncoded(){
        //TODO
        return "";
    }

    //TODO commands
    @Command
    public void eraseData(){
        engine.clear();
    }

    @Command
    @StateMachine(endState = DeviceState.RUNNING)
    public void startLightPolling(){
        engine.startLightPolling();
        setStatus(Status.LIGHT_POLLING);
    }

    @Command(inTypeDesc = "light polling rate in millis")
    @StateMachine(endState = DeviceState.RUNNING)
    public void startLightPollingAtFixedRate(long rate){
        engine.startLightPollingAtFixedRate(rate);
        setStatus(Status.LIGHT_POLLING_AT_FIXED_RATE);
    }

    @Command
    @StateMachine(endState = DeviceState.RUNNING)
    public void startCollectData(){
        engine.start();
        setStatus(Status.HEAVY_DUTY);
    }

    @Command
    @StateMachine(endState = DeviceState.ON)
    public void stopCollectData(){
        engine.stop();
        setStatus(Status.IDLE);
    }

    @Command(outTypeDesc = "client id is used in getXXXUpdates methods")
    public long registerClient(){
        return clientId.incrementAndGet();
    }

    @Command
    public String[] getDataUpdates(long clientId){
        return new String[0];
    }

    //TODO transform to Attribute
    @Command
    public String[] getData(){
        return new String[0];
    }

    @Command
    public String getDataUpdatesEncoded(){
        return "";
    }

    @Command
    public String[] getLatestSnapshot(){
        return new String[0];
    }

    @Command
    public String[] getLatestSnapshotByGroup(){
        return new String[0];
    }

    @Command
    public String[] getSnapshot(long timestamp){
        return new String[0];
    }

    @Command
    public String[] getSnapshotByGroup(long timestamp){
        return new String[0];
    }

    @Command
    public void createAttributesGroup(String[] args){

    }

    @Delete
    public void delete(){
        engine.shutdown();
    }

    public static void main(String... args) throws Exception{
        ServerManager.getInstance().start(args, JStatusServer.class);
    }
}