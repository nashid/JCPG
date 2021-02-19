package module.cpg.graphs;

import ghaffarian.graphs.Digraph;
import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.util.Properties;

public abstract class BaseAbstractGraph<V, E> extends Digraph<V, E> {

    public BaseAbstractGraph(){
        super();
    }

    public BaseAbstractGraph(BaseAbstractGraph g){
        super(g);
    }

    public void export(String format) throws IOException{
        export(format, System.getProperty("user.dir"));
    }

    public void
    export(String format, String outDir) throws IOException{
        switch (format){
            case "DOT":
                exportDOT(outDir);
                break;

            case "GML":
                exportGML(outDir);
                break;

            case "JSON":
                exportJSON(outDir);
                break;
        }
    }

    public void exportDOT() throws IOException{
        exportDOT(System.getProperty("user.dir"));
    }

    public abstract void exportDOT(String outDir) throws IOException;

    public void exportGML() throws IOException{
        exportGML(System.getProperty("user.dir"));
    }

    public abstract void exportGML(String outDir) throws IOException;

    public void exportJSON() throws IOException{
        exportJSON(System.getProperty("user.dir"));
    }

    public abstract void exportJSON(String outDir) throws IOException;

}
