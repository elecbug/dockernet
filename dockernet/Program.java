package dockernet;

import dockernet.*;

public class Program
{
    private static NetworkDevice device;

    public static void main(String args[])
    {
        if (args.length != 1) {
            System.out.println("Need one args [host|router|switch]");
        }

        switch(args[0].toLowerCase()) {
            case "host": 
                Program.device = new Host();
                break;
            case "router": 
                Program.device = new Router();
                break;
        }
    }
}
