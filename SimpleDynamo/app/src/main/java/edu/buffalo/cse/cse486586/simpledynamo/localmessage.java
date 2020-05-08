package edu.buffalo.cse.cse486586.simpledynamo;

import android.util.Log;

import java.io.Serializable;
import java.util.TreeMap;

import static edu.buffalo.cse.cse486586.simpledynamo.SimpleDynamoProvider.TAG;

public class localmessage implements Serializable {
    String key;

    public String getValue() {
        Log.d(TAG,"value in local message getValue- "+value.get(value.lastKey()));
        return value.get(value.lastKey());
    }

    public void setValue(String value) {
        Log.d(TAG,"value in local message setValue- "+value);
        this.value.put(version,value);
        version++;
    }

    public String getCoordinatorPort() {
        return coordinatorPort;
    }

    public void setCoordinatorPort(String coordinatorPort) {
        this.coordinatorPort = coordinatorPort;
    }

    String coordinatorPort;

    TreeMap<Integer,String> value=new TreeMap<Integer, String>();

    public int getVersion() {
        return version;
    }

    int version=0;

    localmessage(String key,String value,String coordinatorPort){
        this.key=key;
        this.value.put(version,value);
        this.coordinatorPort=coordinatorPort;
    }

}
