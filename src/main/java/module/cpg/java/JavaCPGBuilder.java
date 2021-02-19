package module.cpg.java;

import module.cpg.graphs.cpg.CodePropertyGraph;

import java.io.File;
import java.io.IOException;

public class JavaCPGBuilder{

    public static CodePropertyGraph[] buildForAll(String[] javaFilePaths) throws IOException{
        File[] javaFiles = new File[javaFilePaths.length];
        for(int i = 0; i < javaFilePaths.length; ++i){
            javaFiles[i] = new File(javaFilePaths[i]);
        }
        return buildForAll(javaFiles);
    }

    public static CodePropertyGraph[] buildForAll(File[] javaFiles) throws IOException{

        CodePropertyGraph[] codePropertyGraphs;

        codePropertyGraphs = JavacpgDDGBuilder.buildForAll(javaFiles);

        CodePropertyGraph[] listCPG = new CodePropertyGraph[javaFiles.length];
        for (int i = 0; i < javaFiles.length; i++){
            listCPG[i] = codePropertyGraphs[i];
        }



        return listCPG;

    }


}