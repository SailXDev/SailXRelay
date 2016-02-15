/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sailxrelay;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;

import java.io.IOException;
import java.net.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;
import java.util.regex.Pattern;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.framing.FrameBuilder;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 *
 * @author amandoestela
 */


public class SailXRelay extends WebSocketServer {
	private static int counter = 0;
        Hashtable<InetSocketAddress, UDPReceiver> ras = new Hashtable<InetSocketAddress, UDPReceiver>();
        Hashtable<String, UDPReceiver> gas = new Hashtable<String, UDPReceiver>();
	
        WebSocket lastConn;

        static String location=""; 
        static int myPort=3000;
        static String myIPAddress="";
        
	public static void main( String[] args ) throws  UnknownHostException {
            WebSocketImpl.DEBUG = false;
            int port;

            Properties prop = new Properties();
            InputStream input = null;

            try {
                    input = new FileInputStream("config.properties");
                    // load a properties file
                    prop.load(input);
                    // get the property value and print it out
                    location=prop.getProperty("location");
                    myPort=Integer.parseInt(prop.getProperty("port"));
                    myIPAddress=prop.getProperty("ipaddress");
                        
            } catch (IOException ex) {
                    ex.printStackTrace();
            } finally {
                    if (input != null) {
                            try {
                                    input.close();
                            } catch (IOException e) {
                                    e.printStackTrace();
                            }
                    }
            }            
            
            
            try {
                myPort = new Integer( args[ 0 ] );
                    
            } catch ( Exception e ) {
                    //System.out.println( "No port specified. Defaulting to 3000" );
                    myPort = 3000;
            }
                
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(myPort+1), 0);
                server.createContext("/", new MyHandler());
                server.setExecutor(null); // creates a default executor
                server.start();
                System.out.println( "Starting HTTP server port: " + (myPort+1));
            } catch(Exception ee) {}    
            
            System.out.println( "Starting Relay at location: " + location + " IpAddress: " + myIPAddress + "  port: " + myPort);
            new SailXRelay( myPort, new Draft_17() ).start();
                
    
	}
        
        static class MyHandler implements HttpHandler {
            public void handle(HttpExchange t) throws IOException {
              String response = "Welcome Real's HowTo test page";
              t.sendResponseHeaders(200, response.length());
              OutputStream os = t.getResponseBody();
              os.write(response.getBytes());
              os.close();
            }
          }
        
        
	public SailXRelay( int port , Draft d ) throws UnknownHostException {
		super( new InetSocketAddress( port ), Collections.singletonList( d ) );
                
                Timer timer;
                timer = new Timer();

                TimerTask task = new TimerTask() {
                    @Override
                    public void run()
                    {
                        try {
                            URL url = new URL("http://www.sailx.com/modphp/request.php?action=relayhb"
                                    + "&myip="+myIPAddress+"&myport="+ myPort 
                                    + "+&location="+location);
                            InputStream is = url.openStream();
                            try {
                                /* Now read the retrieved document from the stream. */
                            } finally {
                                is.close();
                            }
                        } catch(Exception ee) {}
                        //output log
                        String linea="";
                        Set<String> keys = gas.keySet();
                        for(String key: keys){
                            linea=linea + gas.get(key).status()+ "  ";
                            }
                        System.out.println(new Date().toString() + " " + linea);
                    }
                    };
            
                // Empezamos dentro de 10ms y luego lanzamos la tarea cada 1000ms
                timer.schedule(task, 10, 10000);                
                
	}
	
	public SailXRelay( InetSocketAddress address, Draft d ) {
		super( address, Collections.singletonList( d ) );
	}

	@Override
	public void onOpen( WebSocket conn, ClientHandshake handshake ) {
		counter++;
                
		System.out.println( "///////////Opened connection number" + counter + " from " + conn.getRemoteSocketAddress().getAddress() + ":" + conn.getRemoteSocketAddress().getPort());
	}

	@Override
	public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
		System.out.println( "closed, deletting from the table" );
                InetSocketAddress ra=conn.getRemoteSocketAddress();
                ras.get(ra).removeWsConn(conn);
                ras.remove(ra);
                
	}

	@Override
	public void onError( WebSocket conn, Exception ex ) {
		System.out.println( "Error:" );
		ex.printStackTrace();
	}

	@Override
	public void onMessage( WebSocket conn, String message ) {
		//conn.send( message );
            //System.out.println( "WS String received: " + message );
            trataWsMsg(conn,message);
	}

	@Override
	public void onMessage( WebSocket conn, ByteBuffer blob ) {
		//conn.send( blob );
            try {
                String message=new String(blob.array(), "UTF8");

                //System.out.println( "WS blob received( " + blob.toString() + "): " + message );
                trataWsMsg(conn,message);
            } catch ( Exception e ) {
                    System.out.println( "Exception receiving WS blob received( " + blob.toString());
            }
            
	}

	//@Override
	public void onWebsocketMessageFragment( WebSocket conn, Framedata frame ) {
		FrameBuilder builder = (FrameBuilder) frame;
		builder.setTransferemasked( false );
		conn.sendFrame( frame );
	}

        
        
        void trataWsMsg(WebSocket conn, String msg){
            //System.out.println("Dealing with msg: "+ msg);
            String[] tokens = msg.split(Pattern.quote("/")); // Split on period.

            String id=tokens[0];
            String seq=tokens[1];
            String q=tokens[2];
            String tipo=tokens[3];
            
            //System.out.println("Msg parsed");
            
            if (tipo.equals("INIT")){
		String gsserverip=tokens[4];
		String gsserverpuerto=tokens[5];
		System.out.println("INIT for: " + gsserverip + ":" + gsserverpuerto);
		
		//check if server is created
                UDPReceiver udpr;
                if (gas.containsKey(gsserverip + ":" + gsserverpuerto)){
                    udpr=gas.get(gsserverip + ":" + gsserverpuerto);
                    System.out.println("UDP connection found");
                } else {
                    System.out.println("UDP connection NOT found, starting connection");
                    //create udp server
                    udpr = new UDPReceiver(gsserverip,Integer.parseInt(gsserverpuerto));
                    (new Thread(udpr)).start();
                    gas.put(gsserverip + ":" + gsserverpuerto, udpr);        
                    //udpr = new UDPReceiver(gsserverip,Integer.parseInt(gsserverpuerto));
                    System.out.println("UDP connection created, trying to start!");
                    //udpr.startUDPServer();
                }

                InetSocketAddress ra=conn.getRemoteSocketAddress();
                ras.put(ra, udpr);
                lastConn=conn;
                //System.out.println("INIT finished process");
                        
		return;
	}

        //System.out.println("Sending to GS");
        ras.get(conn.getRemoteSocketAddress()).sendPacket(msg.getBytes());


        }        
        
int numreceivers=0;

class UDPReceiver implements Runnable {
    InetAddress IPAddress;
    int gsPort;
    String gsAddress;
    DatagramSocket serverSocket;
    int udpport=0;
    String lastbroadcastedasciimessage="";
    
    
    Hashtable <Integer,WebSocket> gsws = new Hashtable<Integer, WebSocket>();
    
    public UDPReceiver(String gsaddress, int gsPort )  {
            try{
                IPAddress = InetAddress.getByName(gsaddress);
                this.gsPort=gsPort;
                gsAddress=gsaddress;
                udpport=3000+numreceivers;
		System.out.println("Creating UDP connection for " + gsaddress +":"+ gsPort);
                numreceivers++;
            } catch (UnknownHostException e) {
              System.out.println(e);
            }    
	}
    
    public String status(){
        return (gsAddress + ":" + gsPort + ">" + gsws.size());
    }
    
    public void sendPacket(byte[] sendData){
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                                    IPAddress, gsPort);
        try{
            serverSocket.send(sendPacket);
              } catch (IOException e) {
              System.out.println(e);
      }
    

    
    }
    
    
    
    public void run() {    
      try {
        serverSocket = new DatagramSocket(udpport);
        byte[] receiveData = new byte[2048];

        System.out.printf("Listening on udp:%s:%d%n",
                InetAddress.getLocalHost().getHostAddress(), udpport);     
        DatagramPacket receivePacket = new DatagramPacket(receiveData,
                           receiveData.length);

        while(true)
        {
              serverSocket.receive(receivePacket);
              String sentence = new String( receivePacket.getData(), 0,
                                 receivePacket.getLength() );
              //System.out.println("UDP RECEIVED: " + sentence);
              
              trataUdpMsg(receivePacket);
        }
      } catch (IOException e) {
              System.out.println("Exception starting UDPServer: " + e);
      }
      // should close serverSocket in finally block
    }
    
    void trataUdpMsg(DatagramPacket dp){

	//console.log("Processing UDP from : " + server.address().port);
	if (dp.getData()[0]!=0){
                String msg = new String( dp.getData(), 0, dp.getLength() );
                //System.out.println("Processing ASCII message: "+ msg);
                String[] tokens = msg.split(Pattern.quote("/")); // Split on period.
                int cid=Integer.parseInt(tokens[0]);
		String tipom=tokens[1];
		if (tipom.equals("I")) {
                    //System.out.println("I received from GS for connection #"+cid);
                    gsws.put(new Integer(cid),lastConn);
                    lastConn.send(msg.getBytes());
                    //System.out.println("I sent back to client, address " + lastConn.getRemoteSocketAddress().getAddress() + " added to table");
				
                } else {
                    //not an I message
                    if (cid==0 || cid==-1) {
                            if (!msg.equals(lastbroadcastedasciimessage)){
                                    //System.out.println("Broadcasting ascii to clients  msg: " + msg );
                                    wsbroadcast(msg.getBytes());
                                    lastbroadcastedasciimessage=msg;
                            } else {
                                    //console.log("Dropping repeated ascii message");
                            }
                                
                    } else {
                        //System.out.println("Forwarding ascii to client "  + cid + " msg: " + msg);
                        gsws.get(cid).send(msg.getBytes());
                    }
            }
	} else {
		//System.out.println("Forwarding to clients bin length: " + dp.getLength());
		wsbroadcastbin(dp);
                
	}
        
        
    }
    
    void wsbroadcast(byte[] data){
        
        Set<Integer> keys = gsws.keySet();
        for(Integer key: keys){
            //System.out.println("Broadcast to :" + gsws.get(key).getRemoteSocketAddress().getAddress() + " bytes:" + data.length);
            gsws.get(key).send(data);
            }

    }
    void wsbroadcastbin(DatagramPacket dp){
        byte[] data;
        data = new byte[dp.getLength()];
        System.arraycopy(dp.getData(), dp.getOffset(), data, 0, dp.getLength());

        Set<Integer> keys = gsws.keySet();
        for(Integer key: keys){
            //System.out.println("Broadcast to :" + gsws.get(key).getRemoteSocketAddress().getAddress() + " bytes:" + dp.getLength());
            gsws.get(key).send(data);
            }

    }
    
    void removeWsConn(WebSocket wsc){
        Set<Integer> keys = gsws.keySet();
        for(Integer key: keys){
            //System.out.println("Broadcast to :" + gsws.get(key).getRemoteSocketAddress().getAddress() + " bytes:" + dp.getLength());
            if (gsws.get(key)==wsc) gsws.remove(key);
            }
        //
    }
}        
        
        

}
