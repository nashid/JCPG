package module.cpg;

import ghaffarian.nanologger.Logger;
import org.stringtemplate.v4.ST;

import java.io.File;
import java.io.IOException;

public class Main {

    public static String VERSION = "1.0.0";

    public static void main(String[] args){
        try {
            Logger.init("cpg.log");
            Logger.setEchoToStdOut(true);
            Logger.setTimeTagEnabled(false);
        }
        catch (java.io.IOException ex){
            System.err.println("[ERR] Logger Init failed: "+ ex);
        }
        Logger.printf(Logger.Level.INFO, "Code Property Graph  [ v%s ]", VERSION);

        if(args.length == 0){
            System.out.println("No arguments passed");
        }
        else {
            new cmdlinearg().parse(args).execute();
        }
    }
}
