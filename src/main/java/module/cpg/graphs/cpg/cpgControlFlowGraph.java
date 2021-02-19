package module.cpg.graphs.cpg;

import module.cpg.graphs.BaseAbstractGraph;
import module.cpg.utils.StringUtils;


import ghaffarian.graphs.Edge;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ghaffarian.nanologger.Logger;
import java.io.IOException;
import java.util.Map.Entry;


public class cpgControlFlowGraph extends BaseAbstractGraph<cpgCFGNode, cpgCFGEdge> {
    public final String filePath;
    public final String fileName;
    private String pkgName;
    public final cpgCFGNode root;
    private final List<cpgCFGNode> methodEntries;

    public cpgControlFlowGraph(String path){
        super();
        this.filePath = path;
        this.fileName = new File(path).getName();
        this.root = new cpgCFGNode(cpgCFGNode.NodeTyp.AST_ROOT);
        methodEntries = new ArrayList<>();
        properties.put("label", "CPG of " + fileName);
        properties.put("type", "Code Property Graph (CPG)");
        addVertex(root);

    }

    public void setPackage(String pkg){
        pkgName = pkg;
    }

    public String getPackage(){
        return pkgName;
        }

    public void addMethodEntry(cpgCFGNode entry){
        methodEntries.add(entry);
    }

    public cpgCFGNode[] getAllMethodEntries(){
        return methodEntries.toArray(new cpgCFGNode[methodEntries.size()]);
    }


    @Override
    public void exportDOT(String outDir) throws IOException{
        if(!outDir.endsWith(File.separator)){
            outDir += File.separator;
        }
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.indexOf('.'));
        String filepath = outDir + filename + "-CFG.dot";
        try(PrintWriter dot = new PrintWriter(filepath, "UTF-8")){
            dot.println("digraph" + filename + "_CFG {");
            dot.println("  // graph-vertices");
            Map<cpgCFGNode, String> nodeNames = new LinkedHashMap<>();
            int nodeCounter = 1;
            for(cpgCFGNode node: allVertices){
                String name = "v" + nodeCounter++;
                nodeNames.put(node, name);
                StringBuilder label = new StringBuilder("  [label=\"");
                if(node.getLineOfCode() > 0){
                    label.append(node.getLineOfCode()).append(":  ");
                }
                label.append(StringUtils.escape(node.getCode())).append("\"];");
                dot.println("  " + name + label.toString());
            }
            dot.println("  // graph-edges");
            for(Edge<cpgCFGNode, cpgCFGEdge> edge: allEdges){
                String src = nodeNames.get(edge.source);
                String trg = nodeNames.get(edge.target);
                if(edge.label.type.equals(cpgCFGEdge.Type.FLOWS_TO)){
                    dot.println("  " + src + " -> " + trg + "  [label=\"" + edge.label.type + "\"];");
                }else{
                    dot.println("  " + src + " -> " + trg + "  [label=\"" + edge.label.type + "\"];");
                }
            }
            dot.println("  // end-of-graph\n}");
        }catch (UnsupportedEncodingException ex){
            Logger.error(ex);
        }
        Logger.info("CFG exported to: " + filepath);

    }

    @Override
    public void exportGML(String outDir) throws IOException{
        if(!outDir.endsWith(File.separator)){
            outDir += File.separator;
        }
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.indexOf('.'));
        String filepath = outDir + filename + "-CFG.gml";
        try(PrintWriter gml = new PrintWriter(filepath, "UTF-8")){
            gml.println("graph [");
            gml.println("  directed 1");
            gml.println("  multigraph 1");
            for(Entry<String, String> property: properties.entrySet()){
                switch (property.getKey()){
                    case "directed":
                        continue;
                    default:
                        gml.println("  " + property.getKey() + " \"" + property.getValue() + "\"");
                }
            }
            gml.println("  file \"" + this.fileName + "\"");
            gml.println("  package \"" + this.pkgName + "\"\n");
            //
            Map<cpgCFGNode, Integer> nodeIDs = new LinkedHashMap<>();
            int nodeCounter = 0;
            for (cpgCFGNode node: allVertices) {
                gml.println("  node [");
                gml.println("    id " + nodeCounter);
                gml.println("    line " + node.getLineOfCode());
                gml.println("    label \"" + StringUtils.escape(node.getCode()) + "\"");
                gml.println("  ]");
                nodeIDs.put(node, nodeCounter);
                ++nodeCounter;
            }
            gml.println("]");
        }catch(UnsupportedEncodingException ex){
            Logger.error(ex);
        }
        Logger.info("CFG exported to: " + filepath);
        //
        }

    @Override
    public void exportJSON(String outDir) throws FileNotFoundException{
        if(!outDir.endsWith(File.separator)){
            outDir += File.separator;
        }
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.indexOf('.'));
        String filepath = outDir + filename + "-CFG.json";
        try(PrintWriter json = new PrintWriter(filepath, "UTF-8")){
            json.println("{\n  \"directed\": true,");
            json.println("  \"multigraph\": true,");
            for(Entry<String, String> property: properties.entrySet()){
                switch (property.getKey()){
                    case "directed":
                        continue;
                    default:
                        json.println("  \"" + property.getKey() + "\": \"" + property.getValue() + "\",");
                }
            }
            json.println("  \"file\": \"" + fileName + "\",");
            json.println("  \"package\": \"" + this.pkgName + "\",\n");
            json.println("  \"nodes\": [");
            Map<cpgCFGNode, Integer> nodeIDs = new LinkedHashMap<>();
            int nodeCounter = 0;
            for(cpgCFGNode node: allVertices){
                json.println("    {");
                json.println("      \"id\": " + nodeCounter + ",");
                json.println("      \"line\": " + node.getLineOfCode() + ",");
                json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\"");
                nodeIDs.put(node, nodeCounter);
                ++nodeCounter;
                if (nodeCounter == allVertices.size())
                    json.println("    }");
                else
                    json.println("    },");
            }
            json.println("  ],\n\n  \"edges\": [");
            int edgeCounter = 0;
            for (Edge<cpgCFGNode, cpgCFGEdge> edge: allEdges) {
                json.println("    {");
                json.println("      \"id\": " + edgeCounter + ",");
                json.println("      \"source\": " + nodeIDs.get(edge.source) + ",");
                json.println("      \"target\": " + nodeIDs.get(edge.target) + ",");
                json.println("      \"label\": \"" + edge.label.type + "\"");
                ++edgeCounter;
                if (edgeCounter == allEdges.size())
                    json.println("    }");
                else
                    json.println("    },");
            }
            json.println("  ]\n}");
        }catch (UnsupportedEncodingException ex){
            Logger.error(ex);
        }
        Logger.info("CFG exported to: " + filepath);
    }
}

