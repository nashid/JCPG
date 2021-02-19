package module.cpg.graphs.cpg;

import java.io.IOException;
import module.cpg.java.JavamethodCPGBuilder;

public class methodCPGBuilder {
    public static methodCodePropertyGraph[] build(String lang, String[] srcFilePaths) throws IOException {
        switch (lang) {
            case "C":
                return null;
            case "Java":
                return JavamethodCPGBuilder.buildForAll(srcFilePaths);
            case "Python":
                return null;
        }
        return null;
    }
}