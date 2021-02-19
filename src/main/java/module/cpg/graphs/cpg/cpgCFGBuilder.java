package module.cpg.graphs.cpg;

import module.cpg.java.JavacpgCFGBuilder;

import java.io.IOException;


public class cpgCFGBuilder {

    public static cpgControlFlowGraph build(String lang, String srcFilePaths) throws IOException{
        switch (lang){
            case "C":
                return null;

            case "Java":
                return JavacpgCFGBuilder.build(srcFilePaths);

            case "Python":
                return null;

            default:
                return null;
        }
    }
}
