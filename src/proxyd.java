
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class proxyd
{
    private static ServerSocket serverSocket = null;
    private static int port;
    private static boolean serverOn = true;
    private static HashMap<String, InetAddress> DNS = new HashMap<>(100);

    public static void main(String[] args)
    {
        System.out.println("in proxyd main");
        port = Integer.parseInt(args[0]); // should be an arg
        proxyd myProxy = new proxyd();
    }

    public proxyd()
    {
        try
        {
            //InetAddress myHost = InetAddress.getByName("eecslinab1.engineering.cwru.edu");
            //System.out.println(myHost);
            serverSocket = new ServerSocket(port);
        }
        catch (Exception exception)
        {
            System.out.println("Couldn't make server socket on that port. Exception: " + exception);
            System.exit(-1);
        }

        while (serverOn)
        {
            try
            {
                // Accept connection, creating socket between client and proxy
                Socket clientSocket = serverSocket.accept();

                // create a client thread
                sendToThread cliThread = new sendToThread(clientSocket);

                // start the thread
                cliThread.start();

                // start second thread
                sendToThread cliThread2 = new sendToThread(clientSocket);
                cliThread2.start();

            }catch (IOException ioException)
            {
                System.out.println("Exception: " + ioException);
            }
        }

        try
        {
            serverSocket.close();
            System.out.println("Server Stopped.");
        }catch (Exception e)
        {
            System.out.println("Exception closing ServerSocket: " + e);
        }

    }

    // This class is utilized for the server to proxy to client aspects (writing back)
    class sendBackThread extends Thread
    {
        Socket clientSocket;
        Socket serverConnection;

        public sendBackThread(Socket s, Socket sc)
        {
            clientSocket = s;
            serverConnection = sc;
        }

        @Override
        public void run()
        {
            try
            {
                // output stream from proxy to client
                final OutputStream toClient = clientSocket.getOutputStream();
                // input stream from server to proxy
                final InputStream fromServer = serverConnection.getInputStream();
                // byte buffer that will be used when sending data from server to proxy to client
                byte reply [] = new byte [4096];

                // The following try/catch loop will read from the server to the proxy,
                // then immediately write to the client
                int readBytes;
                try
                {
                    while ((readBytes = fromServer.read(reply)) != -1)
                    {
                        toClient.write(reply, 0, readBytes);
                        toClient.flush();
                    }
                }catch (IOException ioe)
                {
                    System.out.println("Exception: " + ioe);
                }

                // close sockets
                serverConnection.close();
                clientSocket.close();

            }catch (Exception e)
            {
                System.out.println("Exception: " + e);
            }
        }
    }

    // this class is utilized for the client to proxy to server requests
    class sendToThread extends Thread
    {
        Socket clientSocket;
        Socket serverConnection;

        public sendToThread(Socket s)
        {
            clientSocket = s;
        }

        @Override
        public void run()
        {
            try
            {
                // Buffered reader to accept request from Client into proxy
                BufferedReader inRequest = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                // ArrayList of Strings that will store the ASCII header request from the client
                ArrayList<String> myList = new ArrayList<>();

                // The following code (loop) will find the host the client is trying to get to
                // It also adds the header fields to the arraylist, "myList"
                String line;
                String host = null;
                while (!(line = inRequest.readLine()).equals(""))
                {
                    if (line.length() > 4 && line.substring(0,4).equals("Host"))
                    {
                        host = line.substring(6); // getting url to find IP address\
                    }
                    myList.add(line);
                }

                // Check for IP in DNS cache
                InetAddress IP;
                if (DNS.containsKey(host))
                {
                    IP = DNS.get(host);
                    System.out.println("FOUND IN DNS CACHE!");
                }
                // if not in cache, find the IP address
                else
                {
                    System.out.println("NOT FOUND IN CACHE, ADDING...");
                    // Stores the IP address from the host name
                    IP = InetAddress.getByName(host);
                    // Add to cache the IP/host combo
                    DNS.put(host, IP);
                }

                // create socket between proxy and server
                serverConnection = new Socket(IP, 80);

                // toServer is a buffered writer from the proxy to the server
                BufferedWriter toServer = new BufferedWriter(new OutputStreamWriter(serverConnection.getOutputStream()));

                // This loop converts absolute URLs to relative URLs
                for (int i = 0; i < myList.size(); i++)
                {
                    // if GET line
                    if (myList.get(i).substring(0, 11).equals("GET http://"))
                    {
                        String temp = myList.get(i).substring(11);
                        int slashIndex = 0;
                        SLASH_ENCOUNTERED:
                        // This loop goes through the String containing the URL and chops off a portion
                        for (int j = 0; j < temp.length(); j++)
                        {
                            if (temp.charAt(j) == '/')
                            {
                                slashIndex = j;break SLASH_ENCOUNTERED;
                            }
                        }
                        // set myList line to GET and relative URL
                        myList.set(i, "GET " + temp.substring(slashIndex));
                    }
                }

                // this loop writes to the server from the proxy, sending the request.
                for (String s : myList)
                {
                    toServer.write(s + "\r\n");
                    toServer.flush();
                    // this if statement prints the request line if it is a GET request
                    if (s.substring(0,3).equals("GET"))
                    {
                        System.out.println(s);
                    }
                }
                toServer.write("\r\n");
                toServer.flush();

                // create and start a thread for receiving the request
                sendBackThread sbt = new sendBackThread(clientSocket, serverConnection);
                sbt.start();

            }catch (Exception e)
            {
                System.out.println("Exception: " + e);
            }
        }
    }

}
