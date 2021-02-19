package module.cpg.graphs.cpg;

import ghaffarian.graphs.Edge;
import ghaffarian.nanologger.Logger;
import module.cpg.graphs.BaseAbstractGraph;
import module.cpg.utils.StringUtils;

import java.io.*;
import java.util.*;

public class CodePropertyGraph extends BaseAbstractGraph<CPGNode, CPGEdge> {

    public final String filePath;
    public final String fileName;
    private String pkgName;
    public final CPGNode root;
    private final List<CPGNode> methodEntries;
    private final List cdgEdges;
    private final List CFGEdges;
    private cpgControlFlowGraph cfg;

    public CodePropertyGraph(String path){
        super();
        cfg = null;
        filePath = path;
        fileName = new File(path).getName();
        root = new CPGNode(CPGNode.NodeTyp.AST_ROOT);
        cdgEdges = new ArrayList();
        cdgEdges.add("CDG_TRUE");
        cdgEdges.add("CDG_FALSE");
        cdgEdges.add("CDG_EPSILON");
        cdgEdges.add("CDG_THROWS");
        cdgEdges.add("CDG_NOT_THROWS");
        CFGEdges = new ArrayList();
        CFGEdges.add("FLOWS_TO");
        CFGEdges.add("FLOWS_TO_TRUE");
        CFGEdges.add("FLOWS_TO_FALSE");
        CFGEdges.add("THROWS");
        methodEntries = new ArrayList<>();
        properties.put("label", "CPG of" + fileName);
        properties.put("type", "Code Property Graph (CPG)");
        addVertex(root);
    }

    public void attachCFG(cpgControlFlowGraph cfg){
        this.cfg = cfg;
    }

    public cpgControlFlowGraph getCFG(){
        return cfg;
    }

    public void setPackage(String pkg){
        pkgName = pkg;
    }

    public String getPackage(){
        return pkgName;
    }

    public void addMethodEntry(CPGNode entry){
        methodEntries.add(entry);
    }

    public CPGNode[] getAllMethodEntries(){
        return methodEntries.toArray(new CPGNode[methodEntries.size()]);
    }

    public void printAllNodesUseDefs(Logger.Level level){
        for (CPGNode node: allVertices){
            Logger.log(node, level);
            Logger.log(" +USEs: " + Arrays.toString(node.getAllUSEs()), level);
            Logger.log(" +DEFs: " + Arrays.toString(node.getAllDEFs()) + "\n", level);

        }
    }


    @Override
    public void exportDOT(String outDir) throws IOException{
        exportDOT(outDir, true);
    }

    public void exportDOT(String outDir, boolean ctrlEdgeLabels) throws FileNotFoundException{
        if(!outDir.endsWith(File.separator)){
            outDir += File.separator;
        }
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.indexOf('.'));
        String filepath = outDir + filename + "-CPG.dot";
        try(PrintWriter dot = new PrintWriter(filepath, "UTF-8")){
            dot.println("digraph " + filename + "_CPG {");
            dot.println(" // graph-vertices");
            Map<cpgCFGNode, String> ctrlNodes = new LinkedHashMap<>();
            Map<CPGNode, String> dataNodes = new LinkedHashMap<>();
            int nodeCounter = 1;
            Iterator<cpgCFGNode> cfgNode = cfg.allVerticesIterator();
            while(cfgNode.hasNext()){
                cpgCFGNode node = cfgNode.next();
                String name = "v" + nodeCounter++;
                ctrlNodes.put(node, name);
                CPGNode cpgNode = (CPGNode) node.getProperty("pdNode");
                if(cpgNode != null){
                    dataNodes.put(cpgNode, name);
                }
                StringBuilder label = new StringBuilder(" [label=\"");
                if(node.getLineOfCode() > 0){
                    label.append(node.getLineOfCode()).append(":");
                }
                label.append(StringUtils.escape(node.getType())).append(":");
                label.append(StringUtils.escape(node.getCode())).append("\";]");
                dot.println(" " + name + label.toString());
            }
            dot.println(" // graph-edges");
            Iterator<Edge<cpgCFGNode,cpgCFGEdge>> cfEdges = cfg.allEdgesIterator();
            while(cfEdges.hasNext()){
                Edge<cpgCFGNode, cpgCFGEdge> ctrlEdge = cfEdges.next();
                String src = ctrlNodes.get(ctrlEdge.source);
                String trgt = ctrlNodes.get(ctrlEdge.target);
                if (cdgEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                    dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=purple, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                }
                else if ((ctrlEdge.label.type.toString() == "AST") ) {
                    dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=red, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                }
                else if (CFGEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                    dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=green, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                }
                else if ((ctrlEdgeLabels && ((cpgCFGEdge)ctrlEdge.label).type.toString() == "Call") || (ctrlEdgeLabels && ((cpgCFGEdge)ctrlEdge.label).type.toString() == "Return")) {
                    dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=blue, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                }
                else {
                    dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=green, style=dashed];");
                }

            }
            for(Edge<CPGNode, CPGEdge> dataEdge: allEdges){
                String src, trgt;
                if(dataNodes.get(dataEdge.source) != null && dataNodes.get(dataEdge.target) != null){
                    if (dataEdge.label.type.toString() == "DDG_U"){
                        src = dataNodes.get(dataEdge.source);
                        trgt = dataNodes.get(dataEdge.target);
                        dot.println(" " + src + " -> " + trgt + " [style=bold, label=\" (" + ((CPGEdge)dataEdge.label).var + ")\"];");
                    }
                    else if(dataEdge.label.type.toString() == "DDG_D"){
                        src = dataNodes.get(dataEdge.source);
                        trgt = dataNodes.get(dataEdge.target);
                        dot.println(" " + src + " -> " + trgt + " [style=bold, color=brown, label=\" (" + ((CPGEdge)dataEdge.label).var + ")\"];");
                    }
                }

            }
            dot.println(" // end-of-graph\n}");
        }catch (UnsupportedEncodingException ex){
            Logger.error(ex);
        }
        Logger.info("CPG exported to: " + filepath);

    }


    @Override
    public void exportGML(String outDir) throws IOException{
        if(!outDir.endsWith(File.separator)){
            outDir += File.separator;
        }
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.indexOf('.'));
        String filepath = outDir + filename + "-CPG.gml";
        try(PrintWriter gml =  new PrintWriter(filepath, "UTF-8")){
            gml.println("graph [");
            gml.println("  directed 1");
            gml.println("  multigraph 1");
            for(Map.Entry<String, String> property: properties.entrySet()){
                switch (property.getKey()){
                    case "directed":
                        continue;
                    default:
                        gml.println("  " + property.getKey() + " \"" + property.getValue() + "\"");

                }
            }
            gml.println("  file \"" + this.fileName + "\"\n");
            //
            Map<cpgCFGNode, Integer> ctrlNodes = new LinkedHashMap<>();
            Map<CPGNode, Integer> dataNodes = new LinkedHashMap<>();
            Iterator<cpgCFGNode> cfgNodes = cfg.allVerticesIterator();
            int nodeCounter = 0;
            while(cfgNodes.hasNext()){
                cpgCFGNode node = cfgNodes.next();
                gml.println("  node [");
                gml.println("    id " + nodeCounter);
                gml.println("    line " + node.getLineOfCode());
                gml.println("    label \"" + StringUtils.escape(node.getCode()) + "\"");
                gml.println("    type \"" + StringUtils.escape(node.getType()) + "\"");
                CPGNode pdNode = (CPGNode) node.getProperty("pdnode");
                if (pdNode != null) {
                    dataNodes.put(pdNode, nodeCounter);
                    String check = StringUtils.toGmlArray(pdNode.getAllDEFs(), "var");
                    gml.println("    defs " + StringUtils.toGmlArray(pdNode.getAllDEFs(), "var"));
                    gml.println("    uses " + StringUtils.toGmlArray(pdNode.getAllUSEs(), "var"));
                }
                gml.println("  ]");
                ctrlNodes.put(node, nodeCounter);
                ++nodeCounter;
            }
            gml.println();
            //
            int edgeCounter = 0;
            Iterator<Edge<cpgCFGNode, cpgCFGEdge>> cfgEdges = cfg.allEdgesIterator();
            while (cfgEdges.hasNext()) {
                Edge<cpgCFGNode, cpgCFGEdge> ctrlEdge = cfgEdges.next();

                if (this.cdgEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                    gml.println("  edge [");
                    gml.println("    id " + edgeCounter);
                    gml.println("    source " + ctrlNodes.get(ctrlEdge.source));
                    gml.println("    target " + ctrlNodes.get(ctrlEdge.target));
                    gml.println("    type \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    gml.println("    label \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    gml.println("  ]");
                    edgeCounter++;
                    continue;
                }
                else if ((ctrlEdge.label.type.toString() == "AST")) {
                    gml.println("  edge [");
                    gml.println("    id " + edgeCounter);
                    gml.println("    source " + ctrlNodes.get(ctrlEdge.source));
                    gml.println("    target " + ctrlNodes.get(ctrlEdge.target));
                    gml.println("    type \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    gml.println("    label \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    gml.println("  ]");
                    edgeCounter++;
                    continue;
                }
                else{
                    gml.println("  edge [");
                    gml.println("    id " + edgeCounter);
                    gml.println("    source " + ctrlNodes.get(ctrlEdge.source));
                    gml.println("    target " + ctrlNodes.get(ctrlEdge.target));
                    gml.println("    type \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    gml.println("    label \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    gml.println("  ]");
                    edgeCounter++;
                }

        }
            for(Edge<CPGNode, CPGEdge> dataEdge: allEdges){
                if(dataNodes.get(dataEdge.source) != null && dataNodes.get(dataEdge.target) != null){
                    gml.println("  edge [");
                    gml.println("    id " + edgeCounter);
                    gml.println("    source " + dataNodes.get(dataEdge.source));
                    gml.println("    target " + dataNodes.get(dataEdge.target));
                    gml.println("    type \"" + dataEdge.label.type + "\"");
                    gml.println("    label \"" + dataEdge.label.var + "\"");
                    gml.println("  ]");
                    ++edgeCounter;

                }

                } gml.println("]");
        }catch(UnsupportedEncodingException ex){
            Logger.error(ex);
        }
        Logger.info("CPG of the file exported to:" + filepath);
    }

    @Override
    public void exportJSON(String outDir) throws FileNotFoundException{
        if(!outDir.endsWith(File.separator)){
            outDir += File.separator;
        }
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.indexOf('.'));
        String filepath = outDir + filename + "-CPG.json";
        try(PrintWriter json = new PrintWriter(filepath, "UTF-8")){
            json.println("{\n \"directed\": true,");
            json.println(" \"multigraph\": true,");
            for(Map.Entry<String, String> property: properties.entrySet()){
                switch (property.getKey()){
                    case "directed":
                        continue;
                    default:
                        json.println(" \"" + property.getKey() + "\": \"" + property.getValue() + "\",");

                }
            }
            json.println("  \"file\": \"" + fileName + "\",\n");
            //
            json.println("  \"nodes\": [");
            Map<cpgCFGNode, Integer> ctrlNodes = new LinkedHashMap<>();
            Map<CPGNode, Integer> dataNodes = new LinkedHashMap<>();
            Iterator<cpgCFGNode> cfgNode = cfg.allVerticesIterator();
            int nodeCounter = 0;
            while(cfgNode.hasNext()){
                cpgCFGNode node = cfgNode.next();
                json.println("    {");
                json.println("      \"id\": " + nodeCounter + ",");
                json.println("      \"line\": " + node.getLineOfCode() + ",");
                CPGNode cpgNode = (CPGNode) node.getProperty("pdNode");
                if(cpgNode != null){
                    json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\",");
                    json.println("      \"type\": \"" + StringUtils.escape(node.getType()) + "\",");
                    dataNodes.put(cpgNode, nodeCounter);
                    json.println("      \"defs\": " + StringUtils.toJsonArray(cpgNode.getAllDEFs()) + ",");
                    json.println("      \"uses\": " + StringUtils.toJsonArray(cpgNode.getAllUSEs()));
                }else {
                    json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\"");
                }
                ctrlNodes.put(node, nodeCounter);
                ++nodeCounter;
                if(nodeCounter == cfg.vertexCount()){
                    json.println("    }");
                }else {
                    json.println("    ");
                }
            }
            json.println(" ],\n\n  \"edges\": [");
            int edgeCounter = 0;
            Iterator<Edge<cpgCFGNode, cpgCFGEdge>> cfgEdges = cfg.allEdgesIterator();
            while(cfgEdges.hasNext()){
                Edge<cpgCFGNode, cpgCFGEdge> ctrlEdge = cfgEdges.next();
                if ((ctrlEdge.label.type.toString() == "AST") ) {
                    json.println("    {");
                    json.println("      \"id\": " + edgeCounter + ",");
                    json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
                    json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
                    json.println("      \"type\": \"" + ((cpgCFGEdge)ctrlEdge.label).type) ;
                    json.println("      \"label\": \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    json.println("    },");
                    edgeCounter++;
                }
                if (cdgEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                    json.println("    {");
                    json.println("      \"id\": " + edgeCounter + ",");
                    json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
                    json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
                    json.println("      \"type\": \"" + ((cpgCFGEdge)ctrlEdge.label).type);
                    json.println("      \"label\": \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    json.println("    },");
                    edgeCounter++;
                }
                else{
                    json.println("    {");
                    json.println("      \"id\": " + edgeCounter + ",");
                    json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
                    json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
                    json.println("      \"type\":" + ((cpgCFGEdge)ctrlEdge.label).type);
                    json.println("      \"label\": \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                    json.println("    },");
                    edgeCounter++;
                }

            }
            for(Edge<CPGNode, CPGEdge> dataEdge: allEdges){
                if(dataNodes.get(dataEdge.source) != null && dataNodes.get(dataEdge.target) != null){
                    json.println("    {");
                    json.println("      \"id\": " + edgeCounter + ",");
                    json.println("      \"source\": " + dataNodes.get(dataEdge.source) + ",");
                    json.println("      \"target\": " + dataNodes.get(dataEdge.target) + ",");
                    json.println("      \"type\": \"Data\"" + dataEdge.label.type + "\",");
                    json.println("      \"label\": \"" + dataEdge.label.var + "\"");

                    ++edgeCounter;

                    if(edgeCounter == cfg.edgeCount() + allEdges.size()){
                        json.println("    }");
                    }else {
                        json.println("    },");
                    }
                }
            }
            json.println("  ]\n}");
        }catch (UnsupportedEncodingException ex){
            Logger.error(ex);
        }
        Logger.info("CPG of the file exported to" + filepath);
    }


}
