package orgs;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static orgs.Server.forwardVideo;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        new Thread(() -> {
            try {
                ServerSocket videoSocket = new ServerSocket(6000);
                System.out.println("🎥 Video Call Server Started on port 6000");

                while (true) {
                    Socket senderSocket = videoSocket.accept();
                    Socket receiverSocket = videoSocket.accept();

                    // ربط المرسل بالمستقبل
                    new Thread(() -> forwardVideo(senderSocket, receiverSocket)).start();
                    new Thread(() -> forwardVideo(receiverSocket, senderSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                ServerSocket audioSocket = new ServerSocket(6001);
                System.out.println("🔊 Audio Call Server Started on port 6001");

                while (true) {
                    Socket senderSocket = audioSocket.accept();
                    Socket receiverSocket = audioSocket.accept();

                    new Thread(() -> Server.forwardAudio(senderSocket, receiverSocket)).start();
                    new Thread(() -> Server.forwardAudio(receiverSocket, senderSocket)).start();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }
    ServerSocket  serverSocket;

    {
        try {
            serverSocket = new ServerSocket(6700);
            Server server = new Server(serverSocket);
            server.startServer();


            // Server side
        } catch (IOException e) {
            throw new RuntimeException(e + "cccccccccc");
        }



    }
    }
}