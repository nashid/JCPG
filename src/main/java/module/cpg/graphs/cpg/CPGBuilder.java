package module.cpg.graphs.cpg;

import module.cpg.java.JavaCPGBuilder;

import java.io.IOException;

public class CPGBuilder {

    public static CodePropertyGraph[] build(String lang, String[] srcFilePaths) throws IOException{
        switch (lang){
            case "C":
                return null;

            case "Java":
                return JavaCPGBuilder.buildForAll(srcFilePaths);

            case "Python":
                return null;

            default:
                return null;
        }
    }
}
