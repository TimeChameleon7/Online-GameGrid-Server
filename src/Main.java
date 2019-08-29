import java.awt.*;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Grid grid = new Grid(400,400,new Object[]{}, Color.WHITE);
        while(true) {
            Scanner in = new Scanner(System.in);
            System.out.print("Port to use for server?: ");//todo grab from a config file
            String port = in.nextLine();
            System.out.print("Maximum connections allowed?: ");
            String maxCon = in.nextLine();
            try{
                Server server = new Server(Integer.valueOf(port),Integer.valueOf(maxCon),grid);
                break;
            }catch (IOException e){
                e.printStackTrace();//todo deal with exceptions in a user-friendly way
            }
        }
    }
}
