package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Hashtable;

//query and delete will only be used if we have to get global operation

public class message implements Serializable {

    public enum type{
        INSERT,
        NEXTINSERT,
        QUERY,
        RECOVERY,
        RECOVERED,
        ALLQUERY,
        ALLQUERYWITHREPLICA,
        DELETE,
        ALLDELETE,
        ACKNOWLEDGE,
        FORCEINSERT,
        FOUNDQUERY

    }


    public String getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(String coordinator) {
        this.coordinator = coordinator;
    }

    String coordinator;

    type value;

    public type getValue() {
        return value;
    }

    public void setValue(type value) {
        this.value = value;
    }


    public String getCoordinator_substitute() {
        return coordinator_substitute;
    }

    public void setCoordinator_substitute(String coordinator_substitute) {
        this.coordinator_substitute = coordinator_substitute;
    }

    String coordinator_substitute;

    String myport;


    public String getMyport() {
        return myport;
    }

    public void setMyport(String myport) {
        this.myport = myport;
    }


    Hashtable<String, localmessage> messages
            = new Hashtable<String, localmessage>();


    public Hashtable<String, localmessage> getMessages() {
        return messages;
    }

    public void setMessages(Hashtable<String, localmessage> messages) {
        this.messages.putAll(messages);
    }


    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    String key;

    public String getVal() {
        return val;
    }

    public void setVal(String val) {
        this.val = val;
    }

    String val;

    public int getInsertnumber() {
        return insertnumber;
    }

    public void setInsertnumber(int insertnumber) {
        this.insertnumber = insertnumber;
    }

    int insertnumber;

    public String getRecovermsgPort() {
        return recovermsgPort;
    }

    public void setRecovermsgPort(String recovermsgPort) {
        this.recovermsgPort = recovermsgPort;
    }

    String recovermsgPort;

    public String getPortoperated() {
        return portoperated;
    }

    public void setPortoperated(String portoperated) {
        this.portoperated = portoperated;
    }

    String portoperated;




// Constructors in use
//    DELETE,QUERY
    message(type value,String myport,String coordinator,String coordinator_substitute,String portoperated,String key,String val){
        this.value=value;
        this.myport=myport;
        this.coordinator=coordinator;
        this.coordinator_substitute=coordinator_substitute;
        this.portoperated=portoperated;
        this.key=key;
        this.val=val;
    }

//    INSERT
    message(type value,String myport,String coordinator,String coordinator_substitute,String key,String val,int insertnumber,String portoperated){
        this.value=value;
        this.myport=myport;
        this.coordinator=coordinator;
        this.coordinator_substitute=coordinator_substitute;
        this.key=key;
        this.val=val;
        this.insertnumber=insertnumber;
        this.portoperated=portoperated;
    }
//  insert,query,delete-coordinator is node we find from id||allquery,alldelete-coordinator is node to which we are sending
    message(type value,String myport,String coordinator,String coordinator_substitute,Hashtable<String,localmessage> messages){
        this.value=value;
        this.myport=myport;
        this.coordinator=coordinator;
        this.coordinator_substitute=coordinator_substitute;
        this.messages.putAll(messages);
    }



    message(type value,String myport,String coordinator,String coordinator_substitute,String recovermsgPort){
        this.value=value;
        this.myport=myport;
        this.coordinator=coordinator;
        this.coordinator_substitute=coordinator_substitute;
        this.recovermsgPort=recovermsgPort;

    }
//    for ack only
    message(type value){
        this.value=value;
    }






}

