package module.cpg.graphs.cpg;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class cpgCFGNode {

    public enum NodeTyp{
        AST_ROOT        ("ROOT"),
        IMPORTS     ("IMPORTS"),
        IMPORT      ("IMPORT"),
        PACKAGE     ("PACKAGE"),
        NAME        ("NAME"),
        MODIFIER    ("MODIFIER"),
        CLASS       ("CLASS"),
        EXTENDS     ("EXTENDS"),
        IMPLEMENTS  ("IMPLEMENTS"),
        INTERFACE   ("INTERFACE"),
        STATIC_BLOCK("STATIC-BLOCK"),
        CONSTRUCTOR ("CONSTRUCTOR"),
        FIELD       ("FIELD"),
        TYPE        ("TYPE"),
        METHOD      ("METHOD"),
        RETURN      ("RETURN"),
        PARAMS      ("PARAMS"),
        BLOCK       ("BLOCK"),
        IF          ("IF"),
        CONDITION   ("COND"),
        THEN        ("THEN"),
        ELSE        ("ELSE"),
        VARIABLE    ("VAR"),
        INIT_VALUE  ("INIT"),
        STATEMENT   (""),
        FOR         ("FOR"),
        FOR_INIT    ("INIT"),
        FOR_UPDATE  ("UPDATE"),
        FOR_EACH    ("FOR-EACH"),
        IN          ("IN"),
        WHILE       ("WHILE"),
        DO_WHILE    ("DO-WHILE"),
        TRY         ("TRY"),
        RESOURCES   ("RESOURCES"),
        CATCH       ("CATCH"),
        FINALLY     ("FINALLY"),
        SWITCH      ("SWITCH"),
        CASE        ("CASE"),
        DEFAULT     ("DEFAULT"),
        LABELED     ("LABELED"),
        SYNC        ("SYNCHRONIZED"),
        CFG_NODE ("CFG_NODE"),
        THROWS      ("THROWS"),
        ADDITIONAL_NODE ("ADDITIONAL_NODE");

        public final String node_label;

        private NodeTyp(String node_lbl) { node_label = node_lbl;}

        @Override
        public String toString(){ return node_label;}
    }


    private Map<String, Object> properties;

    private Set<String> DEFs, USEs, selfFlows;

    public cpgCFGNode(NodeTyp ntype){
        DEFs = new HashSet<>();
        USEs = new HashSet<>();
        selfFlows = new HashSet<>();
        properties = new LinkedHashMap<>();
        setLineOfCode(0);
        setNodeType(ntype);
    }

    public final void setNodeType(cpgCFGNode.NodeTyp ntype){
        properties.put("ntype", ntype);
    }

    public final cpgCFGNode.NodeTyp getNodeType(){
        return (NodeTyp) properties.get("ntype");
    }

    public final void setLineOfCode(int line){
        properties.put("line", line);
    }

    public final int getLineOfCode(){
        return (Integer) properties.get("line");
    }

    public final void setCode(String code){
        properties.put("code", code);
    }

    public final String getCode(){
        return (String) properties.get("code");
    }

    public final void setType(String type){
        properties.put("type", type);
    }

    public final String getType(){
        return (String) properties.get("type");
    }

    // For AST nodes
    public final void setNormalizedCode(String normal){
        if (normal != null){
            properties.put("normalized", normal);
        }
    }

    public final String getNormalizedCode(){
        String normalized = (String) properties.get("normalized");
        if (normalized != null && !normalized.isEmpty()){
            return normalized;
        }
        return (String) properties.get("code");
    }

    public CPGNode getCPGNode(){
        return (CPGNode) getProperty("cpgnode");
    }

    public void setProperty(String key, Object value){
        properties.put(key.toLowerCase(), value);
    }

    public Object getProperty(String key){
        return properties.get(key.toLowerCase());
    }

    public Set<String> getAllProperties(){
        return properties.keySet();
    }

    @Override
    public String toString(){

        return getType() + ": " + (Integer) properties.get("line") + ": " + (String) properties.get("code") ;
    }


}
