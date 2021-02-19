package module.cpg.graphs.cpg;

public class cpgCFGEdge {
    public final Type type;

    public final String var;

    public cpgCFGEdge(Type type, String var) {
        this.type = type;
        this.var = var;
    }

    public String toString() {
        return this.var;
    }

    public enum Type {
        THROWS("Throws"),
        CALLS("Call"),
        RETURN("Return"),
        AST("AST"),
        CFG("CFG"),
        CDG_TRUE("CDG_TRUE"),
        CDG_FALSE("CDG_FALSE"),
        CDG_EPSILON("CDG_EPSILON"),
        CDG_THROWS("CDG_THROWS"),
        CDG_NOT_THROWS("CDG_NOT_THROWS"),
        IMPORTS("IMPORTS"),
        FLOWS_TO("FLOWS_TO"),
        FLOWS_TO_TRUE("FLOWS_TO_TRUE"),
        FLOWS_TO_FALSE("FLOWS_TO_FALSE");

        public final String label;

        Type(String lbl) {
            this.label = lbl;
        }

        public String toString() {
            return this.label;
        }
    }
}
