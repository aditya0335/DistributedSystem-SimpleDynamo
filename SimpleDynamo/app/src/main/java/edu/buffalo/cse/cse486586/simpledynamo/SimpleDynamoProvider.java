package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.Gson;

import static android.content.Context.MODE_PRIVATE;

public class SimpleDynamoProvider extends ContentProvider {

	static final String TAG = SimpleDynamoProvider.class.getSimpleName();

	//    Global Variables
	Boolean recover=false;
	Boolean recovering=false;
	String myPort = null;
	int myPortnumber;
	String myPortId;
	String predecessor=null;
	String nextPredecessor=null;
	String successor=null;
	String nextSuccessor=null;

//	Boolean isJoined=false;
//	String successor=null;
//	String successorID;
//	String predecessor=null;
//	String predecessorId;

	String PORT0="11108";
	String PORT1="11112";
	String PORT2="11116";
	String PORT3="11120";
	String PORT4="11124";
	ArrayList<String> chord=new ArrayList<String>();
	ArrayList<message> servermessages=new ArrayList();


	static final int SERVER_PORT = 10000;

	Hashtable<String, localmessage> querymessages
			= new Hashtable<String, localmessage>();
	Hashtable<String, String> remotemessage
			= new Hashtable<String, String>();

	private static Map<String,localmessage> local = new ConcurrentHashMap<String, localmessage>();
	TreeMap<String,String> nodejoin=new TreeMap<String, String>();
	TreeMap<String,String> reversenodejoin=new TreeMap<String, String>();

	Boolean isDeleting=false;
	Boolean isQuyering=true;
	private final Object lock = new Object();

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		synchronized (this)
		{
			synchronized (lock){
				while (recover){
					try {
						lock.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			String keyid=null;
			try {
				keyid=genHash(selection);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			if(local.containsKey(selection)){local.remove(selection);}

			Iterator<String> chordIter=chord.iterator();
			while(chordIter.hasNext()){
				String nodeport=chordIter.next();
				String nodesuccessor=chord.get((chord.indexOf(nodeport)+1)%chord.size());
				String nodeNextSuccessor=chord.get((chord.indexOf(nodeport)+2)%chord.size());
				String nodeportid=reversenodejoin.get(nodeport);
				String nodepredecessorId=reversenodejoin.get(chord.get((chord.indexOf(nodeport)+4)%chord.size()));

				if((nodepredecessorId.compareTo(keyid)<0 &&nodeportid.compareTo(keyid)>=0)||
						(nodepredecessorId.compareTo(nodeportid)>0&&keyid.compareTo(nodeportid)>0&&keyid.compareTo(nodepredecessorId)>0)||
						(nodepredecessorId.compareTo(nodeportid)>0&&keyid.compareTo(nodeportid)<0&&keyid.compareTo(nodepredecessorId)<0)){
					if(nodeport.equals(myPort)){
						local.remove(selection);
						message msg=new message(message.type.DELETE,myPort,successor,nextSuccessor,myPort,selection,"");
						lookupinsert(msg);
						msg.setCoordinator(nextSuccessor);
						lookupinsert(msg);
					}
					else{   //(type value,String myport,String coordinator,String coordinator_substitute,String key,String val,int insertnumber)
						message msg=new message(message.type.DELETE,myPort,nodeport,nodesuccessor,nodeport,selection,"");
						lookupinsert(msg);
						msg.setCoordinator(nodesuccessor);
						msg.setCoordinator_substitute(nodeNextSuccessor);
						lookupinsert(msg);
						msg.setCoordinator(nodeNextSuccessor);
						lookupinsert(msg);
					}
					break;
				}
			}




			return 0;
		}
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		synchronized (this){
			synchronized (lock){
				while (recover){
					try {
						lock.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			String keyid=null;
			String key=(String) values.get("key");
			try {
				keyid=genHash(key);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			String value=(String) values.get("value");

			Log.d(TAG,"in insert of port- "+myPort+"key- "+key+"value- "+value);
			Iterator<String> chordIter=chord.iterator();
			while(chordIter.hasNext()){
				String port=chordIter.next();
				String successorPort=chord.get((chord.indexOf(port)+1)%chord.size());

				String portid=reversenodejoin.get(port);
				String predecessorPortId=reversenodejoin.get(chord.get((chord.indexOf(port)+4)%chord.size()));

				if((predecessorPortId.compareTo(keyid)<0 &&portid.compareTo(keyid)>=0)||
						(predecessorPortId.compareTo(portid)>0&&keyid.compareTo(portid)>0&&keyid.compareTo(predecessorPortId)>0)||
						(predecessorPortId.compareTo(portid)>0&&keyid.compareTo(portid)<0&&keyid.compareTo(predecessorPortId)<0)){

							message msg=new message(message.type.INSERT,myPort,port,successorPort,key,value,0,port);
							Log.d(TAG,"in insert of port- "+myPort+"after local.containsKey "+"key- "+msg.getKey()+"value- "+msg.getVal());
							lookupinsert(msg);
						break;
				}
			}
			return uri;
		}
	}

	private void lookupinsert(message msg){
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);
	}

	@Override
	public boolean onCreate() {

		SharedPreferences pref = getContext().getSharedPreferences("MyPref",MODE_PRIVATE);
		if(pref.contains("failure")){recover=pref.getBoolean("failure",false);}
		else{
			SharedPreferences.Editor editor = pref.edit();
			editor.putBoolean("failure",true);
			editor.commit();
		}


		// TODO Auto-generated method stub
		//      To find port no.
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));
		myPortnumber=Integer.parseInt(myPort)/2;
		try {
			myPortId=genHash(Integer.toString(myPortnumber));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
			Log.d(TAG,"called server"+myPort);
		} catch (IOException e) {
			Log.d(TAG, "AVD- " + myPort + "OnCreate Process ErrorLogNo-1 " + "can't create ServerSocket");
			e.printStackTrace();
		}

		String port0id=null;
		String port1id=null;
		String port2id=null;
		String port3id=null;
		String port4id=null;

		try {
			port0id=genHash(Integer.toString(Integer.parseInt(PORT0)/2));
			port1id=genHash(Integer.toString(Integer.parseInt(PORT1)/2));
			port2id=genHash(Integer.toString(Integer.parseInt(PORT2)/2));
			port3id=genHash(Integer.toString(Integer.parseInt(PORT3)/2));
			port4id=genHash(Integer.toString(Integer.parseInt(PORT4)/2));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}



		nodejoin.put(port0id,PORT0);
		nodejoin.put(port1id,PORT1);
		nodejoin.put(port2id,PORT2);
		nodejoin.put(port3id,PORT3);
		nodejoin.put(port4id,PORT4);
		reversenodejoin.put(PORT0,port0id);
		reversenodejoin.put(PORT1,port1id);
		reversenodejoin.put(PORT2,port2id);
		reversenodejoin.put(PORT3,port3id);
		reversenodejoin.put(PORT4,port4id);


		for (Map.Entry<String,String> entry : nodejoin.entrySet())
		{ Log.d(TAG,"Gen Hash - "+entry.getKey()+"For Port- "+entry.getValue());
		chord.add(entry.getValue());}

		Log.d(TAG,"Printing chord- "+chord+"chord size- "+chord.size());
		predecessor=chord.get((chord.indexOf(myPort)+4)%chord.size());
		nextPredecessor=chord.get((chord.indexOf(myPort)+3)%chord.size());
		successor=chord.get((chord.indexOf(myPort)+1)%chord.size());
		nextSuccessor=chord.get((chord.indexOf(myPort)+2)%chord.size());
		Log.d(TAG,"First Node- "+chord.get((chord.indexOf(myPort)+1)%chord.size()));

		if(recover){
			//(type value,String myport,String destination)
			Log.d(TAG, "onCreate: In Recovery");
			message msg=new message(message.type.RECOVERY,myPort,successor,predecessor,predecessor);  //(type value,String myport,String coordinator,String coordinator_substitute,String recovermsgPort)
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);
			recovering=true;
			while (recovering){}
			recovering=true;
			msg.setCoordinator(predecessor);
			msg.setCoordinator_substitute(nextPredecessor);
			msg.setRecovermsgPort(nextPredecessor);
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);
			while (recovering){}
			recovering=true;
			msg.setCoordinator(nextSuccessor);
			msg.setCoordinator_substitute(successor);
			msg.setRecovermsgPort(myPort);
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);
			while (recovering){}
			recovering=false;
		}
		synchronized (lock) {
			recover = false;
			lock.notifyAll();
		}
		while (!servermessages.isEmpty()){
			synchronized (this){
				serverFunction(servermessages.get(0));
				servermessages.remove(0);
			}
		}


		// TODO Auto-generated method stub
		return true;
	}



	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		synchronized (this){
			synchronized (lock){
			while (recover){
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			}
			MatrixCursor matrixCursor=new MatrixCursor(new String[]{"key","value"});

			if(selection.equals("@")){
				for (Map.Entry<String,localmessage> entry : local.entrySet())
				{matrixCursor.newRow().add("key",entry.getKey()).add("value",entry.getValue().getValue()); }
			}

			else if(selection.equals("*")){
				Hashtable<String, localmessage> queryall
						= new Hashtable<String, localmessage>();
				for (Map.Entry<String,localmessage> entry : local.entrySet()){
					if(entry.getValue().getCoordinatorPort().equals(nextPredecessor)){
						querymessages.put(entry.getKey(),entry.getValue());
					}

				}

				if(successor!=null){    //(type value,String myport,String coordinator,String coordinator_substitute,Hashtable<String,localmessage> messages)
					message msg=new message(message.type.ALLQUERY,myPort,successor,nextSuccessor,queryall);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);
					while(isQuyering){}
					isQuyering=true;

				}

				for (Map.Entry<String,localmessage> entry : querymessages.entrySet())
				{matrixCursor.newRow().add("key",entry.getKey()).add("value",entry.getValue().getValue()); }
				querymessages.clear();
			}
			else{
				Iterator<String> chordIter=chord.iterator();
				String keyid= null;
				try {
					keyid = genHash(selection);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
				while(chordIter.hasNext()){
					String nodeport=chordIter.next();
					String nodesuccessor=chord.get((chord.indexOf(nodeport)+1)%chord.size());
					String nodeNextSuccessor=chord.get((chord.indexOf(nodeport)+2)%chord.size());
					String nodeportid=reversenodejoin.get(nodeport);
					String nodepredecessorId=reversenodejoin.get(chord.get((chord.indexOf(nodeport)+4)%chord.size()));

					if((nodepredecessorId.compareTo(keyid)<0 &&nodeportid.compareTo(keyid)>=0)||
							(nodepredecessorId.compareTo(nodeportid)>0&&keyid.compareTo(nodeportid)>0&&keyid.compareTo(nodepredecessorId)>0)||
							(nodepredecessorId.compareTo(nodeportid)>0&&keyid.compareTo(nodeportid)<0&&keyid.compareTo(nodepredecessorId)<0)){
						if(nodeNextSuccessor.equals(myPort)){
							if(!local.containsKey(selection)){
								while (!local.containsKey(selection)){}
							}
							else{
								matrixCursor.newRow().add("key",selection).add("value",local.get(selection).getValue());
							}
						}
						else{   //(type value,String myport,String coordinator,String coordinator_substitute,String key,String val,int insertnumber)
							message msg=new message(message.type.QUERY,myPort,nodeNextSuccessor,nodesuccessor,nodeport,selection,"");
							new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);
							while(isQuyering){}
							isQuyering=true;
							matrixCursor.newRow().add("key",selection).add("value",querymessages.get(selection).getValue());
							querymessages.clear();
						}
						break;
					}
				}
			}

			Log.v("query", selection);
			return matrixCursor;
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {         //Server Class


		@Override
		protected Void doInBackground(ServerSocket... serverSockets) {

			ServerSocket serverSocket = serverSockets[0];

			while (true){

				Socket clientSocket = null;

				try{
					Log.d(TAG,"in server"+myPort);
					clientSocket = serverSocket.accept();
					Log.d(TAG,"socket accepted"+myPort);
					InputStream is = clientSocket.getInputStream();  //Opening Input Stream
					ObjectInputStream ois_server = new ObjectInputStream(is);
					OutputStream os=clientSocket.getOutputStream();
					ObjectOutputStream oos_server=new ObjectOutputStream(os);
					message servermsgnew = (message) ois_server.readObject();
					Log.d(TAG,"Port: " +servermsgnew.myport + " MsgType: " +servermsgnew.getValue());
//
					message ack=new message(message.type.ACKNOWLEDGE);

					oos_server.writeObject(ack);
					oos_server.flush();

					if(recover){
						if(servermsgnew.getValue()==message.type.RECOVERED){serverFunction(servermsgnew);}
						else{servermessages.add(servermsgnew);}
					}
					else{
						while (!servermessages.isEmpty()){}
						Log.d(TAG, "Server doInBackground: "+myPort+servermsgnew.getValue());
						serverFunction(servermsgnew);
					}

				}
				catch (OptionalDataException e){e.printStackTrace();}
				catch (StreamCorruptedException e){e.printStackTrace();}
				catch (SocketTimeoutException e){e.printStackTrace();}
				catch (EOFException e){e.printStackTrace();}
				catch (UnknownHostException e){e.printStackTrace();}
				catch (IOException e){e.printStackTrace();}
				catch (Exception e){e.printStackTrace();}


			}

		}

		protected void onProgressUpdate(String... strings) {


		}



	}
	public void serverFunction(message servermsgnew){
		Log.d(TAG, "serverFunction"+servermsgnew.getValue());

//ok
		if(servermsgnew.getValue()==message.type.INSERT){
			Log.d(TAG,"in server insert message insertnumber- "+servermsgnew.getInsertnumber()+"operating port- "+servermsgnew.getPortoperated());
			if(local.containsKey(servermsgnew.getKey())){local.get(servermsgnew.getKey()).setValue(servermsgnew.getVal());}
			else{localmessage msg=new localmessage(servermsgnew.getKey(),servermsgnew.getVal(),servermsgnew.getPortoperated());
				local.put(servermsgnew.getKey(),msg);}
			if(servermsgnew.getInsertnumber()==0){
				servermsgnew.setInsertnumber(1);
				servermsgnew.setCoordinator(successor);
				servermsgnew.setCoordinator_substitute(nextSuccessor);
				lookupinsert(servermsgnew);}
			else if(servermsgnew.getInsertnumber()==1){

				servermsgnew.setInsertnumber(2);
				servermsgnew.setCoordinator(successor);
				servermsgnew.setCoordinator_substitute(nextSuccessor);
				lookupinsert(servermsgnew);
			}


		}
//ok
		if(servermsgnew.getValue()==message.type.RECOVERY){
			Hashtable<String,localmessage> temp=new Hashtable<String, localmessage>();
			for (Map.Entry<String,localmessage> entry : local.entrySet())
			{if(entry.getValue().getCoordinatorPort().equals(servermsgnew.getRecovermsgPort())){temp.put(entry.getKey(),entry.getValue());} }
			message msg=new message(message.type.RECOVERED,myPort,servermsgnew.getMyport(),null,temp); //(type value,String myport,String coordinator,String coordinator_substitute,Hashtable<String,String> messages)
			lookupinsert(msg);
		}
//ok
		if(servermsgnew.getValue()==message.type.RECOVERED){
			local.putAll(servermsgnew.getMessages());
			recovering=false;
		}
//ok
		if(servermsgnew.getValue()==message.type.ALLQUERY){
			if(!servermsgnew.getMyport().equals(myPort)){
				Hashtable<String,localmessage> temp=new Hashtable<String, localmessage>();
				for (Map.Entry<String,localmessage> entry : local.entrySet())
				{if(entry.getValue().getCoordinatorPort().equals(nextPredecessor)){

					temp.put(entry.getKey(),entry.getValue());} }
				servermsgnew.setMessages(temp);
				servermsgnew.setCoordinator(successor);
				servermsgnew.setCoordinator_substitute(nextSuccessor);
				lookupinsert(servermsgnew);

			}
			else {
				querymessages.putAll(servermsgnew.getMessages());
				isQuyering=false;
			}
		}
//ok
		if(servermsgnew.getValue()==message.type.QUERY){
			if (local.containsKey(servermsgnew.getKey())) {    //(type value,String myport,String coordinator,String coordinator_substitute,String portoperated,String key,String val)
				message msg=new message(message.type.FOUNDQUERY,myPort,
						servermsgnew.getMyport(),null,null,servermsgnew.getKey(),
						local.get(servermsgnew.getKey()).getValue());
				Log.d(TAG,"in server"+myPort+"servermsgnew.getMyport()"+servermsgnew.getMyport()+"servermsgnew.getKey()"+servermsgnew.getKey()+"local.get(servermsgnew.getKey()).getValue()"+local.get(servermsgnew.getKey()).getValue());
				lookupinsert(msg);
			}
			else{

					servermsgnew.setCoordinator(servermsgnew.getCoordinator_substitute());
					servermsgnew.setCoordinator_substitute(servermsgnew.getPortoperated());
					lookupinsert(servermsgnew);

			}
		}
//ok
		if(servermsgnew.getValue()==message.type.FOUNDQUERY){
			Log.d(TAG, "serverFunction: "+myPort+"FOUNDQUERY");
			localmessage msg=new localmessage(servermsgnew.getKey(),servermsgnew.getVal(),servermsgnew.getCoordinator());
			querymessages.put(servermsgnew.getKey(),msg);
			isQuyering=false;
		}

		if(servermsgnew.getValue()==message.type.DELETE){
			local.remove(servermsgnew.getKey());
		}

	}


	private class ClientTask extends AsyncTask<message, Void, Void> {                //Client Class

		@Override
		protected Void doInBackground(message... messages) {
			if(messages[0].getValue()==message.type.INSERT||
					messages[0].getValue()==message.type.RECOVERY||
					messages[0].getValue()==message.type.RECOVERED||
					messages[0].getValue()==message.type.ALLQUERY||
					messages[0].getValue()==message.type.QUERY||
					messages[0].getValue()==message.type.FOUNDQUERY||
					messages[0].getValue()==message.type.DELETE) {
				try {

					Socket socket = new Socket();

					socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(messages[0].getCoordinator())), 10000);

					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					ObjectInputStream ois=new ObjectInputStream(socket.getInputStream());
					oos.writeObject(messages[0]);
					socket.setSoTimeout(10000);
					oos.flush();

					message recieved=(message)ois.readObject();
					oos.close();
					ois.close();
					socket.close();
				} catch (SocketTimeoutException e) {
					Log.d(TAG,"I was in error");
					if(messages[0].getValue()==message.type.INSERT)
					{
						if(messages[0].getInsertnumber()==0){
							messages[0].setInsertnumber(1);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							lookupinsert(messages[0]);  //Doubt
						}

						else if(messages[0].getInsertnumber()==1){
							messages[0].setInsertnumber(2);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							lookupinsert(messages[0]);
						}
					}
					if(messages[0].getValue()==message.type.RECOVERY)
					{messages[0].setCoordinator(messages[0].getCoordinator_substitute());
						lookupinsert(messages[0]);}
					if(messages[0].getValue()==message.type.ALLQUERY){
						if(!messages[0].getCoordinator().equals(messages[0].getMyport())){
							Hashtable<String,localmessage> temp=new Hashtable<String, localmessage>();
							for (Map.Entry<String,localmessage> entry : local.entrySet()){
								if(entry.getValue().getCoordinatorPort().equals(predecessor)){
									temp.put(entry.getKey(),entry.getValue());
								}

							}
							messages[0].setMessages(temp);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							messages[0].setCoordinator_substitute(null);
							lookupinsert(messages[0]);
						}
					}
					if(messages[0].getValue()==message.type.QUERY){
						messages[0].setCoordinator(messages[0].getCoordinator_substitute());
						messages[0].setCoordinator_substitute(null);
						lookupinsert(messages[0]);
					}

					e.printStackTrace();
				} catch (IOException e) {
					Log.d(TAG,"I was in error");
					if(messages[0].getValue()==message.type.INSERT)
					{
						if(messages[0].getInsertnumber()==0){
							messages[0].setInsertnumber(1);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							lookupinsert(messages[0]);  //Doubt
						}

						else if(messages[0].getInsertnumber()==1){
							messages[0].setInsertnumber(2);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							lookupinsert(messages[0]);
						}
					}
					if(messages[0].getValue()==message.type.RECOVERY)
					{messages[0].setCoordinator(messages[0].getCoordinator_substitute());
						lookupinsert(messages[0]);}
					if(messages[0].getValue()==message.type.ALLQUERY){
						if(!messages[0].getCoordinator().equals(messages[0].getMyport())){
							Hashtable<String,localmessage> temp=new Hashtable<String, localmessage>();
							for (Map.Entry<String,localmessage> entry : local.entrySet()){
								if(entry.getValue().getCoordinatorPort().equals(predecessor)){
									temp.put(entry.getKey(),entry.getValue());
								}

							}
							messages[0].setMessages(temp);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							messages[0].setCoordinator_substitute(null);
							lookupinsert(messages[0]);
						}
					}
					if(messages[0].getValue()==message.type.QUERY){
						messages[0].setCoordinator(messages[0].getCoordinator_substitute());
						messages[0].setCoordinator_substitute(null);
						lookupinsert(messages[0]);
					}
					e.printStackTrace();
				}
				catch (Exception e){
					Log.d(TAG,"I was in error");
					if(messages[0].getValue()==message.type.INSERT)
					{
						if(messages[0].getInsertnumber()==0){
							messages[0].setInsertnumber(1);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							lookupinsert(messages[0]);  //Doubt
						}

						else if(messages[0].getInsertnumber()==1){
							messages[0].setInsertnumber(2);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							lookupinsert(messages[0]);
						}
					}
					if(messages[0].getValue()==message.type.RECOVERY)
					{messages[0].setCoordinator(messages[0].getCoordinator_substitute());
						lookupinsert(messages[0]);}
					if(messages[0].getValue()==message.type.ALLQUERY){
						if(!messages[0].getCoordinator().equals(messages[0].getMyport())){
							Hashtable<String,localmessage> temp=new Hashtable<String, localmessage>();
							for (Map.Entry<String,localmessage> entry : local.entrySet()){
								if(entry.getValue().getCoordinatorPort().equals(predecessor)){
									temp.put(entry.getKey(),entry.getValue());
								}

							}
							messages[0].setMessages(temp);
							messages[0].setCoordinator(messages[0].getCoordinator_substitute());
							messages[0].setCoordinator_substitute(null);
							lookupinsert(messages[0]);
						}
					}
					if(messages[0].getValue()==message.type.QUERY){
						messages[0].setCoordinator(messages[0].getCoordinator_substitute());
						messages[0].setCoordinator_substitute(null);
						lookupinsert(messages[0]);
					}
					e.printStackTrace();
				}
			}
//
//			if(messages[0].getValue()==message.type.RECOVERY){
//
//			}


			return null;
		}




	}


}
