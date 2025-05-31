package orgs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private ServerSocket serverSocket;
    public static List<ClientHandler> clientHandlers = new ArrayList<>();


    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void startServer() {
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected!");

                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandlers.add(clientHandler);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeServerSocket() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void forwardVideo(Socket inSocket, Socket outSocket) {
        try {
            DataInputStream input = new DataInputStream(inSocket.getInputStream());
            DataOutputStream output = new DataOutputStream(outSocket.getOutputStream());

            while (true) {
                int length = input.readInt();
                byte[] data = new byte[length];
                input.readFully(data);
                output.writeInt(length);
                output.write(data);
                output.flush();
            }
        } catch (IOException e) {
            System.out.println("⛔ اتصال الفيديو انتهى: " + e.getMessage());
        }
    }

    public static void forwardAudio(Socket inSocket, Socket outSocket) {
        try {
            InputStream input = inSocket.getInputStream();
            OutputStream output = outSocket.getOutputStream();

            byte[] buffer = new byte[4096];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                output.flush();
            }

        } catch (IOException e) {
            System.out.println("⛔ انقطع اتصال الصوت");
        }
    }

}
