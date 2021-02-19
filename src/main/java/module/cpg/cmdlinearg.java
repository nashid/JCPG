package module.cpg;

import java.io.File;
import java.io.IOException;
import ghaffarian.nanologger.Logger;


public class cmdlinearg {

    private final Execution exec;

    public cmdlinearg(){
        exec = new Execution();
    }

    public Execution parse(String[] args){
        for (int i = 0; i < args.length; ++i){
            if (args[i].startsWith("-")) {
                if (args[i].length() > 3) {
                    String optn = args[i].substring(1).toLowerCase();
                    switch (optn) {
                        case "ast":
                            exec.addAnalysisOption(Execution.Analysis.AST);
                            break;

                        case "cfg":
                            exec.addAnalysisOption(Execution.Analysis.CFG);
                            break;

                        case "pdg":
                            exec.addAnalysisOption(Execution.Analysis.PDG);
                            break;

                        case "cpg":
                            exec.addAnalysisOption(Execution.Analysis.CPG);
                            break;

                        case "cpm":
                            exec.addAnalysisOption(Execution.Analysis.CPM);
                            break;

                        case "outdir":
                            if (i < args.length - 1) {
                                ++i;
                                if (!exec.setOutputDirectory(args[i])) {
                                    printHelp("Output directory is invalid");
                                    System.exit(1);
                                }
                            } else {
                                printHelp("Output directory not specified");
                                System.exit(1);
                            }
                            break;
                        case "format":
                            if (i < args.length - 1) {
                                ++i;
                                switch (args[i].toLowerCase()) {
                                    case "dot":
                                        exec.setOutputFormat(Execution.Formats.DOT);
                                        break;
                                    case "gml":
                                        exec.setOutputFormat(Execution.Formats.GML);
                                        break;
                                    case "json":
                                        exec.setOutputFormat(Execution.Formats.JSON);
                                        break;
                                    default:
                                        printHelp("Unknown output format: " + args[i]);
                                        System.exit(1);
                                }

                            } else {
                                printHelp("Please specify a Format");
                                System.exit(1);
                            }
                            break;
                        case "lang":
                            if (i < args.length - 1) {
                                ++i;
                                if (args[i].toLowerCase().contentEquals("java")) {
                                    exec.setLanguage(Execution.Languages.JAVA);
                                } else {
                                    printHelp("Please specify the language");
                                    System.exit(1);
                                }
                                break;
                            }
                            break;
                        case "debug":
                            exec.setDebugMode(true);
                            try {
                                Logger.setActiveLevel(Logger.Level.DEBUG);
                                Logger.redirectStandardError("cpg.err");
                            } catch (IOException ex) {
                                Logger.error(ex);
                            }
                            break;
                        case "timetags":
                            Logger.setTimeTagEnabled(true);
                            break;
                        //
                        default:
                            printHelp("Unknown Option: " + args[i]);
                            System.exit(1);
                    }
                } else {
                    printHelp("Option is invalid:" + args[i]);
                    System.exit(1);
                }
            }
            else {
                File input = new File(args[i]);
                if(input.exists()){
                    exec.addInputPath(args[i]);
                }
                else {
                    Logger.warn("Path is incorrect" + args[i]);
                }
            }
        }


        return exec;
    }

    public static void printHelp(String errMsg) {
        if (errMsg != null && !errMsg.isEmpty())
            Logger.error("ERROR -- " + errMsg + '\n');

        String[] help = {"Use the instructions correctly"};

        for (String line: help)
            Logger.info(line);
    }
}
