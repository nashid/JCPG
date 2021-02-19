package module.cpg.utils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;

public class FileUtils {
    /**
     * Returns a list of file-paths from the given directory-path
     * where the files exist and the filenames match a given suffix.
     */
    /* list of filepaths */
    public static String[] listOfFilesWS(String pathDirec, String suffix){
        return  listOfFilesWS(new File[] {new File(pathDirec)}, suffix);
    }


    /**
     * Returns a list of file-paths based on the given input file-paths
     * where the files exist and the filenames match a given suffix.
     */
    public  static String[] listOfFileWS(String[] args, String suffix){
        ArrayList<File> files = new ArrayList<>();
        for (String arg: args){
            if(arg.contains("*")){
            File directory = new File(System.getProperty("user.dir"));
            File[] matches = directory.listFiles(new WildcardFilter(arg));
            for(File file : matches){
                if(file.getName().endsWith(suffix))
                    files.add(file);
            }
            }
            else{
                files.add(new File(arg));
            }
        }
        return listOfFilesWS(files.toArray(new File[files.size()]),suffix);
    }


    /**
     * Returns a list of file-paths based on the given file objects
     * where the files exist and the filenames match a given suffix.
     */
    public static String[] listOfFilesWS(File[] argFiles, String suffix) {
        ArrayList<String> list = new ArrayList<>();
        for (File file: argFiles) {
            if (file.isDirectory()) {
                list.addAll(Arrays.asList(listOfFilesWS(file.listFiles(), suffix)));
            } else {
                if (file.exists() && file.getName().endsWith(suffix))
                    list.add(file.getAbsolutePath());
            }
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * Simple wildcard-matcher for filenames, using REGEX.
     */
    static class WildcardFilter implements FileFilter {
        private final String regex;
        public WildcardFilter(String wildcard) {
            regex = ".*" + wildcard.replaceAll("\\*", ".*");
        }
        @Override
        public boolean accept(File pathname) {
            return pathname.getPath().matches(regex);
        }
    }

}
