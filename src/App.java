package dockernet;

public class App {
    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Need one args [host|router]");
        }

        switch(args[0].toLowerCase()) {
            case "host":
                new Host().run();
                break;
            case "router":
                new Router().run();
                break;
        }
    }
}
