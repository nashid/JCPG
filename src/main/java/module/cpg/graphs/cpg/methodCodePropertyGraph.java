package module.cpg.graphs.cpg;
import ghaffarian.graphs.Edge;
import ghaffarian.nanologger.Logger;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import module.cpg.graphs.BaseAbstractGraph;
import module.cpg.utils.StringUtils;

public class methodCodePropertyGraph extends BaseAbstractGraph<CPGNode, CPGEdge> {
    public final String filePath;

    public final String fileName;

    private String pkgName;

    public final CPGNode root;

    private final List<CPGNode> methodEntries;

    private final List cdgEdges;

    private final List CFGEdges;

    private cpgControlFlowGraph[] cfg;

    public methodCodePropertyGraph(String path) {
        this.cfg = null;
        this.filePath = path;
        this.fileName = (new File(path)).getName();
        this.root = new CPGNode(CPGNode.NodeTyp.AST_ROOT);
        this.cdgEdges = new ArrayList();
        this.cdgEdges.add("CDG_TRUE");
        this.cdgEdges.add("CDG_FALSE");
        this.cdgEdges.add("CDG_EPSILON");
        this.cdgEdges.add("CDG_THROWS");
        this.cdgEdges.add("CDG_NOT_THROWS");
        this.CFGEdges = new ArrayList();
        this.CFGEdges.add("FLOWS_TO");
        this.CFGEdges.add("FLOWS_TO_TRUE");
        this.CFGEdges.add("FLOWS_TO_FALSE");
        this.CFGEdges.add("THROWS");
        this.methodEntries = new ArrayList<>();
        this.properties.put("label", "methodCPG of" + this.fileName);
        this.properties.put("type", "CodeProperty Graph (CPG)");
        addVertex(this.root);
    }

    public void attachCFG(cpgControlFlowGraph[] cfg) {
        this.cfg = cfg;
    }

    public cpgControlFlowGraph[] getCFG() {
        return this.cfg;
    }

    public void setPackage(String pckage) {
        this.pkgName = pckage;
    }

    public String getPackage() {
        return this.pkgName;
    }

    public void addMethodEntry(CPGNode entry) {
        this.methodEntries.add(entry);
    }

    public CPGNode[] getAllMethodEntries() {
        return this.methodEntries.<CPGNode>toArray(new CPGNode[this.methodEntries.size()]);
    }

    public void printAllNodesUseDefs(Logger.Level level) {
        for (CPGNode node : this.allVertices) {
            Logger.log(node, level);
            Logger.log(" +USEs: " + Arrays.toString((Object[])node.getAllUSEs()), level);
            Logger.log(" +DEFs: " + Arrays.toString((Object[])node.getAllDEFs()) + "\n", level);
        }
    }

    public void exportDOT(String outDir) throws IOException {
        exportDOT(outDir, true);
    }

    public void exportDOT(String outDir, boolean ctrlEdgeLabels) throws FileNotFoundException {
        for (cpgControlFlowGraph cpgControlFlowGraph1 : this.cfg) {
            int methodCount = 0;
            if (!outDir.endsWith(File.separator))
                outDir = outDir + File.separator;
            File outDirFile = new File(outDir);
            outDirFile.mkdirs();
            String filename = this.fileName.substring(0, this.fileName.indexOf('.'));
            String filepath = outDir + cpgControlFlowGraph1.fileName + "-CPG.dot";
            try {
                PrintWriter dot = new PrintWriter(filepath, "UTF-8");
                try {
                    dot.println("digraph " + filename + "_CPG {");
                    dot.println(" // graph-vertices");
                    Map<cpgCFGNode, String> ctrlNodes = new LinkedHashMap<>();
                    Map<CPGNode, String> dataNodes = new LinkedHashMap<>();
                    int nodeCounter = 1;
                    Iterator<cpgCFGNode> cfgNode = cpgControlFlowGraph1.allVerticesIterator();
                    while (cfgNode.hasNext()) {
                        cpgCFGNode node = cfgNode.next();
                        String name = "v" + nodeCounter++;
                        ctrlNodes.put(node, name);
                        CPGNode cpgNode = (CPGNode)node.getProperty("pdNode");
                        if (cpgNode != null)
                            dataNodes.put(cpgNode, name);
                        StringBuilder label = new StringBuilder(" [label=\"");
                        if (node.getLineOfCode() > 0)
                            label.append(node.getLineOfCode()).append(":");
                        label.append(StringUtils.escape(node.getType())).append(":");
                        label.append(StringUtils.escape(node.getCode())).append("\";]");
                        dot.println(" " + name + label.toString());
                    }
                    dot.println(" // graph-edges");
                    Iterator<Edge<cpgCFGNode, cpgCFGEdge>> cfEdges = cpgControlFlowGraph1.allEdgesIterator();
                    while (cfEdges.hasNext()) {
                        Edge<cpgCFGNode, cpgCFGEdge> ctrlEdge = cfEdges.next();
                        String src = ctrlNodes.get(ctrlEdge.source);
                        String trgt = ctrlNodes.get(ctrlEdge.target);
                        if (this.cdgEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                            dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=purple, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                        }
                        else if (ctrlEdge.label.type.toString() == "AST") {
                            dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=red, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                        }
                        else if (this.CFGEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                            dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=green, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");
                        }
                        else if ((ctrlEdgeLabels && ((cpgCFGEdge)ctrlEdge.label).type.toString() == "Call") || (ctrlEdgeLabels && ((cpgCFGEdge)ctrlEdge.label).type.toString() == "Return")) {
                            dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=blue, style=dashed, label=\"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"];");

                        }
                        else {
                            dot.println(" " + src + " -> " + trgt + " [arrowhead=empty, color=green, style=dashed];");
                        }

                    }
                    for (Edge<CPGNode, CPGEdge> dataEdge : (Iterable<Edge<CPGNode, CPGEdge>>)this.allEdges) {
                        if (dataNodes.get(dataEdge.source) != null && dataNodes.get(dataEdge.target) != null) {
                            if (dataEdge.label.type.toString() == "DDG_U"){
                                String src = dataNodes.get(dataEdge.source);
                                String trgt = dataNodes.get(dataEdge.target);
                                dot.println(" " + src + " -> " + trgt + " [style=bold, label=\" (" + ((CPGEdge)dataEdge.label).var + ")\"];");
                            }
                            else if(dataEdge.label.type.toString() == "DDG_D"){
                                String src = dataNodes.get(dataEdge.source);
                                String trgt = dataNodes.get(dataEdge.target);
                                dot.println(" " + src + " -> " + trgt + " [style=bold, color=brown, label=\" (" + ((CPGEdge)dataEdge.label).var + ")\"];");
                            }

                        }
                    }
                    dot.println(" // end-of-graph\n}");
                    dot.close();
                } catch (Throwable throwable) {
                    try {
                        dot.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                    throw throwable;
                }
            } catch (UnsupportedEncodingException ex) {
                Logger.error(ex);
            }
            Logger.info("CPG exported to: " + filepath);
            methodCount++;
        }
    }

    public void exportGML(String outDir) throws IOException {
        for (cpgControlFlowGraph cpgControlFlowGraph1 : this.cfg) {
            int methodCount = 0;
            if (!outDir.endsWith(File.separator))
                outDir = outDir + File.separator;
            File outDirFile = new File(outDir);
            outDirFile.mkdirs();
            String[] fName = this.fileName.split("[.]");
            String filepath = outDir + fName[0] + "_" + cpgControlFlowGraph1.fileName + "-CPG.gml";
            try {
                PrintWriter gml = new PrintWriter(filepath, "UTF-8");
                try {
                    gml.println("graph [");
                    gml.println("  directed 1");
                    gml.println("  multigraph 1");
                    for (Map.Entry<String, String> property : (Iterable<Map.Entry<String, String>>)this.properties.entrySet()) {
                        switch ((String)property.getKey()) {
                            case "directed":
                                continue;
                        }
                        gml.println("  " + (String)property.getKey() + " \"" + (String)property.getValue() + "\"");
                    }
                    gml.println("  file \"" + this.fileName + "\"\n");
                    Map<cpgCFGNode, Integer> ctrlNodes = new LinkedHashMap<>();
                    Map<CPGNode, Integer> dataNodes = new LinkedHashMap<>();
                    Iterator<cpgCFGNode> cfgNodes = cpgControlFlowGraph1.allVerticesIterator();
                    int nodeCounter = 0;
                    while (cfgNodes.hasNext()) {
                        cpgCFGNode node = cfgNodes.next();
                        gml.println("  node [");
                        gml.println("    id " + nodeCounter);
                        gml.println("    line " + node.getLineOfCode());
                        gml.println("    label \"" + StringUtils.escape(node.getCode()) + "\"");
                        gml.println("    type \"" + StringUtils.escape(node.getType()) + "\"");
                        CPGNode pdNode = (CPGNode)node.getProperty("pdnode");
                        if (pdNode != null) {
                            dataNodes.put(pdNode, Integer.valueOf(nodeCounter));
                            gml.println("    defs " + StringUtils.toGmlArray(pdNode.getAllDEFs(), "var"));
                            gml.println("    uses " + StringUtils.toGmlArray(pdNode.getAllUSEs(), "var"));
                        }
                        gml.println("  ]");
                        ctrlNodes.put(node, Integer.valueOf(nodeCounter));
                        nodeCounter++;
                    }
                    gml.println();
                    int edgeCounter = 0;
                    Iterator<Edge<cpgCFGNode, cpgCFGEdge>> cfgEdges = cpgControlFlowGraph1.allEdgesIterator();
                    while (cfgEdges.hasNext()) {
                        Edge<cpgCFGNode, cpgCFGEdge> ctrlEdge = cfgEdges.next();
                        if (cdgEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                            gml.println("  edge [");
                            gml.println("    id " + edgeCounter);
                            gml.println("    source " + ctrlNodes.get(ctrlEdge.source));
                            gml.println("    target " + ctrlNodes.get(ctrlEdge.target));
                            gml.println("    type \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            gml.println("    label \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            gml.println("  ]");
                            edgeCounter++;
                        }
                        else if (ctrlEdge.label.type.toString() == "AST")  {
                            gml.println("  edge [");
                            gml.println("    id " + edgeCounter);
                            gml.println("    source " + ctrlNodes.get(ctrlEdge.source));
                            gml.println("    target " + ctrlNodes.get(ctrlEdge.target));
                            gml.println("    type \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            gml.println("    label \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            gml.println("  ]");
                            edgeCounter++;
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
                    for (Edge<CPGNode, CPGEdge> dataEdge : (Iterable<Edge<CPGNode, CPGEdge>>)this.allEdges) {
                        if (dataNodes.get(dataEdge.source) != null && dataNodes.get(dataEdge.target) != null) {
                            gml.println("  edge [");
                            gml.println("    id " + edgeCounter);
                            gml.println("    source " + dataNodes.get(dataEdge.source));
                            gml.println("    target " + dataNodes.get(dataEdge.target));
                            gml.println("    type \"" + ((CPGEdge)dataEdge.label).type + "\"");
                            gml.println("    label \"" + ((CPGEdge)dataEdge.label).var + "\"");
                            gml.println("  ]");
                            edgeCounter++;
                        }
                    }
                    gml.println("]");
                    gml.close();
                } catch (Throwable throwable) {
                    try {
                        gml.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                    throw throwable;
                }
            } catch (UnsupportedEncodingException ex) {
                Logger.error(ex);
            }
            Logger.info("CPG of the file exported to:" + filepath);
            methodCount++;
        }
    }

    public void exportJSON(String outDir) throws FileNotFoundException {
        for (cpgControlFlowGraph cpgControlFlowGraph1 : this.cfg) {
            int methodCount = 0;
            if (!outDir.endsWith(File.separator))
                outDir = outDir + File.separator;
            File outDirFile = new File(outDir);
            outDirFile.mkdirs();
            String filepath = outDir + cpgControlFlowGraph1.fileName + methodCount + "-CPG.json";
            try {
                PrintWriter json = new PrintWriter(filepath, "UTF-8");
                try {
                    json.println("{\n \"directed\": true,");
                    json.println(" \"multigraph\": true,");
                    for (Map.Entry<String, String> property : (Iterable<Map.Entry<String, String>>)this.properties.entrySet()) {
                        switch ((String)property.getKey()) {
                            case "directed":
                                continue;
                        }
                        json.println(" \"" + (String)property.getKey() + "\": \"" + (String)property.getValue() + "\",");
                    }
                    json.println("  \"file\": \"" + this.fileName + "\",\n");
                    json.println("  \"nodes\": [");
                    Map<cpgCFGNode, Integer> ctrlNodes = new LinkedHashMap<>();
                    Map<CPGNode, Integer> dataNodes = new LinkedHashMap<>();
                    Iterator<cpgCFGNode> cfgNode = cpgControlFlowGraph1.allVerticesIterator();
                    int nodeCounter = 0;
                    while (cfgNode.hasNext()) {
                        cpgCFGNode node = cfgNode.next();
                        json.println("    {");
                        json.println("      \"id\": " + nodeCounter + ",");
                        json.println("      \"line\": " + node.getLineOfCode() + ",");
                        CPGNode cpgNode = (CPGNode)node.getProperty("pdNode");
                        if (cpgNode != null) {
                            json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\",");
                            json.println("      \"type\": \"" + StringUtils.escape(node.getType()) + "\",");
                            dataNodes.put(cpgNode, Integer.valueOf(nodeCounter));
                            json.println("      \"defs\": " + StringUtils.toJsonArray(cpgNode.getAllDEFs()) + ",");
                            json.println("      \"uses\": " + StringUtils.toJsonArray(cpgNode.getAllUSEs()));
                        } else {
                            json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\"");
                        }
                        ctrlNodes.put(node, Integer.valueOf(nodeCounter));
                        nodeCounter++;
                        if (nodeCounter == cpgControlFlowGraph1.vertexCount()) {
                            json.println("    }");
                            continue;
                        }
                        json.println("    ");
                    }
                    json.println(" ],\n\n  \"edges\": [");
                    int edgeCounter = 0;
                    Iterator<Edge<cpgCFGNode, cpgCFGEdge>> cfgEdges = cpgControlFlowGraph1.allEdgesIterator();
                    while (cfgEdges.hasNext()) {
                        Edge<cpgCFGNode, cpgCFGEdge> ctrlEdge = cfgEdges.next();
                        if (ctrlEdge.label.type.toString() == "AST")  {
                            json.println("    {");
                            json.println("      \"id\": " + edgeCounter + ",");
                            json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
                            json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
                            json.println("      \"type\": \"AST\",");
                            json.println("      \"label\": \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            json.println("    },");
                            edgeCounter++;

                        }
                        else if (cdgEdges.contains(((cpgCFGEdge)ctrlEdge.label).type.toString())) {
                            json.println("    {");
                            json.println("      \"id\": " + edgeCounter + ",");
                            json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
                            json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
                            json.println("      \"type\": \"CDG\",");
                            json.println("      \"label\": \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            json.println("    },");
                            edgeCounter++;
                        }
                        else{
                            json.println("    {");
                            json.println("      \"id\": " + edgeCounter + ",");
                            json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
                            json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
                            json.println("      \"type\": \"CFG\",");
                            json.println("      \"label\": \"" + ((cpgCFGEdge)ctrlEdge.label).type + "\"");
                            json.println("    },");
                            edgeCounter++;
                        }

                    }
                    for (Edge<CPGNode, CPGEdge> dataEdge : (Iterable<Edge<CPGNode, CPGEdge>>)this.allEdges) {
                        if (dataNodes.get(dataEdge.source) != null && dataNodes.get(dataEdge.target) != null) {
                            json.println("    {");
                            json.println("      \"id\": " + edgeCounter + ",");
                            json.println("      \"source\": " + dataNodes.get(dataEdge.source) + ",");
                            json.println("      \"target\": " + dataNodes.get(dataEdge.target) + ",");
                            json.println("      \"type\": \"Data\"" + ((CPGEdge)dataEdge.label).type + "\",");
                            json.println("      \"label\": \"" + ((CPGEdge)dataEdge.label).var + "\"");
                            edgeCounter++;
                            if (edgeCounter == cpgControlFlowGraph1.edgeCount() + this.allEdges.size()) {
                                json.println("    }");
                                continue;
                            }
                            json.println("    },");
                        }
                    }
                    json.println("  ]\n}");
                    json.close();
                } catch (Throwable throwable) {
                    try {
                        json.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                    throw throwable;
                }
            } catch (UnsupportedEncodingException ex) {
                Logger.error(ex);
            }
            Logger.info("CPG of the file exported to" + filepath);
            methodCount++;
        }
    }
}
