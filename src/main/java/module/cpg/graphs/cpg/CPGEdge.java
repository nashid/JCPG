package module.cpg.graphs.cpg;

public class CPGEdge {
    public final Type type;

    public final String var;

    public CPGEdge(Type type, String var) {
        this.type = type;
        this.var = var;
    }

    public String toString() {
        return this.var;
    }

    public enum Type {
        FLOW("Flows"),
        THROWS("Throws"),
        CALLS("Call"),
        RETURN("Return"),
        IS_FUNCTION_OF_AST("IS_FUNCTION_OF_AST"),
        IS_FUNCTION_OF_CFG("IS_FUNCTION_OF_CFG"),
        IS_AST_OF_AST_ROOT("IS_AST_OF_AST_ROOT"),
        IS_AST_PARENT("IS_AST_PARENT"),
        IS_CFG_OF_CFG_ROOT("IS_CFG_OF_CFG_ROOT"),
        FLOWS_TO("FLOWS_TO"),
        FLOWS_TO_TRUE("FLOWS_TO_TRUE"),
        FLOWS_TO_FALSE("FLOWS_TO_FALSE"),
        IS_CLASS_OF("IS_CLASS_OF"),
        DDG_D ("DDG_D"),
        DDG_U ("DDG_U"),
        SELF_FLOW("SELF_FLOW");

        public final String label;

        Type(String lbl) {
            this.label = lbl;
        }

        public String toString() {
            return this.label;
        }
    }
}
