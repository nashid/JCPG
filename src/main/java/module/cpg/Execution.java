package module.cpg;


import module.cpg.graphs.cpg.*;
import module.cpg.utils.FileUtils;
import module.cpg.utils.SystemUtils;
import module.cpg.java.JavaClass;
import module.cpg.java.JavaClassExtractor;
import ghaffarian.nanologger.Logger;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class Execution {

    private final ArrayList<Analysis> analysisTypes;
    private final ArrayList<String> inputPaths;
    private boolean debugMode;
    private String outputDir;
    private Languages lang;
    private Formats format;

    public Execution() {
        debugMode = false;
        analysisTypes = new ArrayList<>();
        inputPaths = new ArrayList<>();
        lang = Languages.JAVA;
        format = Formats.DOT;
        outputDir = System.getProperty("user.dir");
        if (!outputDir.endsWith(File.separator))
            outputDir += File.separator;
    }

    public enum Languages {
        JAVA	    ("Java", ".java");

        private Languages(String str, String suffix) {
            name = str;
            this.suffix = suffix;
        }
        @Override
        public String toString() {
            return name;
        }
        public final String name;
        public final String suffix;
    }

    public enum Analysis {
        // analysis types
        CFG			("CFG"),
        PDG			("PDG"),
        AST			("AST"),
        ICFG		("ICFG"),
        CPG         ("CPG"),
        CPM        ("CPM");

        private Analysis(String str) {
            type = str;
        }
        @Override
        public String toString() {
            return type;
        }
        public final String type;
    }

    public enum Formats {
        DOT, GML, JSON
    }

    public void addAnalysisOption(Analysis opt) {
        analysisTypes.add(opt);
    }

    public void addInputPath(String path) {
        inputPaths.add(path);
    }

    public void setLanguage(Languages lang) {
        this.lang = lang;
    }

    public void setDebugMode(boolean isDebug) {
        debugMode = isDebug;
    }

    public void setOutputFormat(Formats fmt) {
        format = fmt;
    }

    public boolean setOutputDirectory(String outPath) {
        if (!outPath.endsWith(File.separator))
            outPath += File.separator;
        File outDir = new File(outPath);
        outDir.mkdirs();
        if (outDir.exists()) {
            if (outDir.canWrite() && outDir.isDirectory()) {
                outputDir = outPath;
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Config for CPG:");
        str.append("\n  Language = ").append(lang);
        str.append("\n  Output format = ").append(format);
        str.append("\n  Output directory = ").append(outputDir);
        str.append("\n  Analysis types = ").append(Arrays.toString(analysisTypes.toArray()));
        str.append("\n  Input paths = \n");
        for (String path: inputPaths)
            str.append("        ").append(path).append('\n');
        return str.toString();
    }


    public void execute() {
        if (inputPaths.isEmpty()) {
            Logger.info("No input path provided!\nAbort.");
            System.exit(0);
        }
        if (analysisTypes.isEmpty()) {
            Logger.info("No analysis type provided!\nAbort.");
            System.exit(0);
        }

        Logger.info(toString());

        // 1. Extract source files from input-paths, based on selected language
        String[] paths = inputPaths.toArray(new String[inputPaths.size()]);
        String[] filePaths = new String[0];
        if (paths.length > 0)
            filePaths = FileUtils.listOfFileWS(paths, lang.suffix);
        Logger.info("\n# " + lang.name + " source files = " + filePaths.length + "\n");

        // Check language
        if (!lang.equals(Languages.JAVA)) {
            Logger.info("Analysis of " + lang.name + " programs is not yet supported!");
            Logger.info("Abort.");
            System.exit(0);
        }

        // 2. For each analysis type, do the analysis and output results
        for (Analysis analysis: analysisTypes) {

            Logger.debug("\nMemory Status");
            Logger.debug("=============");
            Logger.debug(SystemUtils.getMemoryStats());

            switch (analysis.type) {
                case "CPG":
                    Logger.info("\nStarting CPG execution");
                    Logger.info("========================");
                    Logger.debug("START: " + Logger.time() + '\n');
                    try{
                        for(CodePropertyGraph cpg: Objects.requireNonNull(CPGBuilder.build(lang.name, filePaths))){
                            System.out.println("Testing CPG creation");
                            cpg.export(format.toString(), outputDir);
                        }


                    }catch (IOException ex){
                        Logger.error(ex);
                    }

                    break;
                //
                case "CPM":
                    Logger.info("\nStarting CPGM execution");
                    Logger.info("=========================");
                    Logger.debug("START: " + Logger.time() + '\n');
                    try{
                        for(methodCodePropertyGraph cpgm: methodCPGBuilder.build(lang.name, filePaths)){
                            System.out.println("Testing CPGM creation");
                            cpgm.export(format.toString(), outputDir);
                        }
                    }catch (IOException ex){
                        Logger.error(ex);
                    }
                    break;

                default:
                    Logger.info("\n\'" + analysis.type + "\' analysis is not supported!\n");
            }
            Logger.debug("\nFINISH: " + Logger.time());
        }
        //
        Logger.debug("\nMemory Status");
        Logger.debug("=============");
        Logger.debug(SystemUtils.getMemoryStats());
    }

    private void analyzeInfo(String lang, String srcFilePath) {
        if(lang.toLowerCase().contentEquals("java")){
            try{
                Logger.info("\n========================================\n");
                Logger.info("FILE: " + srcFilePath);
                // first extract class info
                List<JavaClass> classInfoList = JavaClassExtractor.extractInfo(srcFilePath);
                for (JavaClass classInfo : classInfoList)
                    Logger.info("\n" + classInfo);
                // then extract imports info
                if (classInfoList.size() > 0) {
                    Logger.info("\n- - - - - - - - - - - - - - - - - - - - -");
                    String[] imports = classInfoList.get(0).IMPORTS;
                    for (JavaClass importInfo : JavaClassExtractor.extractImportsInfo(imports))
                        Logger.info("\n" + importInfo);
            }
            }catch (IOException ex){
                Logger.error(ex);
            }
        }else {
            cmdlinearg.printHelp("language is invalid");
        }


    }


}
