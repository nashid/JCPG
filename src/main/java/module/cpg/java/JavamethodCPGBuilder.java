package module.cpg.java;

import java.io.File;
import java.io.IOException;
import module.cpg.graphs.cpg.methodCodePropertyGraph;

public class JavamethodCPGBuilder {
    public static methodCodePropertyGraph[] buildForAll(String[] javaFilePaths) throws IOException {
        File[] javaFiles = new File[javaFilePaths.length];
        for (int i = 0; i < javaFilePaths.length; i++)
            javaFiles[i] = new File(javaFilePaths[i]);
        return buildForAll(javaFiles);
    }

    public static methodCodePropertyGraph[] buildForAll(File[] javaFiles) throws IOException {
        methodCodePropertyGraph[] methodcpgs = JavacpgMethodLevel.buildForAll(javaFiles);
        methodCodePropertyGraph[] listCPG = new methodCodePropertyGraph[javaFiles.length];
        for (int i = 0; i < javaFiles.length; i++)
            listCPG[i] = methodcpgs[i];
        return listCPG;
    }
}
