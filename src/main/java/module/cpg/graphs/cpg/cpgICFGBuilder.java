package module.cpg.graphs.cpg;

import module.cpg.java.JavacpgICFGBuilder;

import java.io.IOException;

public class cpgICFGBuilder {

    /**
     * Build and return ICFG of given source code files with specified language.
     * @return
     */
    public static cpgControlFlowGraph buildForAll(String lang, String[] javaFilePaths) throws IOException {
        switch (lang) {
            case "C":
                return null;
            //
            case "Java":
                return JavacpgICFGBuilder.buildForAll(javaFilePaths);
            //
            case "Python":
                return null;
            //
            default:
                return null;
        }
    }

}
