import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;

class Server {
    //todo wall collision, probably on Game Grid though
    private final int allowedConnections;
    private ServerSocket serverSocket;
    private DatagramSocket socket;
    private ArrayList<Connection> connections;
    private Grid grid;

    Server(int port, int allowedConnections, Grid grid) throws IOException {//todo create method that starts all threads and remove them from constructor, name all threads and setDaemon where appropriate
        this.allowedConnections = allowedConnections;
        serverSocket = new ServerSocket(port);
        System.out.printf("Created server. Port: %d Maximum Connections: %d%n",port,allowedConnections);
        socket = new DatagramSocket(port);
        connections = new ArrayList<>(allowedConnections);
        this.grid = grid;
        new Thread(new GridUpdater()).start();
        while (connections.size()<allowedConnections){
            listen();
        }
    }

    private void listen(){
        if(connections.size()<allowedConnections){
            try{
                Connection connection = new Connection(serverSocket);
                connections.add(connection);
                new Thread(connection).start();

                ArrayList<Object> objects = new ArrayList<>(Arrays.asList(grid.getObjects()));
                objects.add(connection.object);
                grid.setObjects(objects.toArray(new Object[0]));
                sendAll(new ObjectAddEvent(connection.object));
                connection.send(new GridUpdateEvent(grid));

                System.out.println(String.format("%s connected",connection.getRemoteSocketAddress()));
                sendAll(String.format("<Server>: %s connected",connection.getRemoteSocketAddress()));
                sendAll(String.valueOf(connections.size()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendAll(Event event) throws IOException {
        ByteOutputStream bytes = new ByteOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(event);
        byte[] data = bytes.getBytes();
        for (Connection connection : connections) {
            DatagramPacket packet = new DatagramPacket(
                    data,
                    0,
                    data.length,
                    connection.getRemoteAddress(),
                    connection.getPort()
            );
            socket.send(packet);
        }
    }

    private void sendAll(String message){
        for(Connection connection : connections){
            try {
                connection.send(message);
            } catch (IOException e) {
                connection.disconnect();
            }
        }
    }

    class Connection implements Runnable{
        private final Socket socket;
        private final DataOutputStream out;
        private final DataInputStream in;
        private boolean connected;
        final CollisionObject object;

        Connection(ServerSocket serverSocket) throws IOException {
            socket = serverSocket.accept();
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            connected = true;
            Point point = new Point((int) (Math.random() * grid.getWidth()), (int) (Math.random() * grid.getHeight()));
            Color color = new Color((int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255));
            object = new CollisionObject(point, Pixel.rectangle(new Point(-5, -5), new Point(5, 5), color));
        }

        void disconnect(){
            connected = false;
            connections.remove(this);

            ArrayList<Object> objects = new ArrayList<>(Arrays.asList(grid.getObjects()));
            int index = objects.indexOf(this.object);
            objects.remove(this.object);
            grid.setObjects(objects.toArray(new Object[0]));

            sendAll(String.format("<Server>: %s disconnected.",getRemoteSocketAddress()));
            try {
                sendAll(new ObjectRemoveEvent(index));
                sendAll(String.valueOf(connections.size())); //todo
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        InetAddress getRemoteAddress(){
            return socket.getInetAddress();
        }

        SocketAddress getRemoteSocketAddress(){
            return socket.getRemoteSocketAddress();
        }

        int getPort(){
            return socket.getPort();
        }

        void send(String message) throws IOException {
            out.writeUTF(message);
        }

        void send(Event event) throws IOException {
            ByteOutputStream bytes = new ByteOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bytes);
            out.writeObject(event);
            byte[] data = bytes.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data,
                    0,
                    data.length,
                    socket.getInetAddress(),
                    socket.getPort()
            );
            Server.this.socket.send(packet);
        }

        String receive() throws IOException {
            return in.readUTF();
        }

        @Override
        public void run() {
            while(connected){
                try {
                    String message = receive();
                    if (message.toCharArray()[0] != '/') {
                        for (Connection connection : connections) {
                            connection.send(String.format("<%s>: %s", getRemoteSocketAddress(), message));
                        }
                    } else {
                        String[] split = message.split(" ");

                        String command = split[0];
                        String[] args = new String[split.length-1];
                        System.arraycopy(split, 1, args, 0, split.length - 1);
                        switch (command){
                            case "/requestgrid":
                                send(new GridUpdateEvent(grid));
                                break;
                            case "/color":
                                Pixel[] pixels = object.getPixels();
                                Color color = new Color(Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]));
                                for (Pixel pixel : pixels){
                                    pixel.setColor(color);
                                }
                                object.setPixels(pixels);
                                ArrayList<Object> objects = new ArrayList<>(Arrays.asList(grid.getObjects()));
                                int index = objects.indexOf(this.object);
                                sendAll(new PixelChangeEvent(index,pixels));
                                break;
                                default:
                                    send("<Server>: Command unknown");
                                    break;
                        }
                    }
                }catch (SocketException ignored){
                    disconnect();
                    System.out.println(String.format("%s disconnected",getRemoteSocketAddress()));
                } catch (IOException e) {
                    disconnect();
                    e.printStackTrace();
                }
            }
        }
    }

    class GridUpdater implements Runnable{
        void receive() throws IOException, ClassNotFoundException {
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf,buf.length);
            socket.receive(packet);

            ByteArrayInputStream bytes = new ByteArrayInputStream(packet.getData());
            ObjectInputStream in = new ObjectInputStream(bytes);
            Movement movement = (Movement) in.readObject();

            for (Connection connection : connections) {
                if (connection.getRemoteAddress().equals(packet.getAddress()) && connection.getPort() == packet.getPort()){
                    try {
                        connection.object.translate(movement,1,grid.getObjects());
                    } catch (ObjectCollisionException ignored) {}
                    sendAll(new PositionChangeEvent(connections.indexOf(connection),connection.object.getAnchor()));
                    break;
                }
            }
        }
        @Override
        public void run() {
            while (socket != null){
                try {
                    receive();
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
