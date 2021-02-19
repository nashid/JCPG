package module.cpg.java;
import ghaffarian.collections.IdentityLinkedHashSet;
import org.jgrapht.traverse.*;
import ghaffarian.graphs.BreadthFirstTraversal;
import ghaffarian.graphs.DepthFirstTraversal;
import ghaffarian.graphs.Edge;
import ghaffarian.graphs.GraphTraversal;
import ghaffarian.nanologger.Logger;
import module.cpg.graphs.cpg.*;
import module.cpg.java.parser.JavaBaseVisitor;
import module.cpg.java.parser.JavaLexer;
import module.cpg.java.parser.JavaParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JavacpgMethodLevel {

    //-----------------------FileName-------------------------------------//
    private static String currentFile;

    private static Map<String, JavaClass> allClassInfos;

    private static Map<String, List<MethodDefInfo>> methodDEFs;

    private static ArrayList<JavaClass> javaClasses = new ArrayList<>();

    private static ArrayList<JavaClass> alwaysAvailableClasses = new ArrayList<>();

    private static ArrayList<JavaClass> currentFileClasses = new ArrayList<>();

    public static methodCodePropertyGraph[] buildForAll(File[] files) throws IOException {

        //----------Parse Java Files-------------------------------//
        Logger.info("Parsing all source files ... ");
        ParseTree[] parseTrees = new ParseTree[files.length];
        for (int i = 0; i < files.length; ++i) {
            InputStream inFile = new FileInputStream(files[i]);
            ANTLRInputStream input = new ANTLRInputStream(inFile);
            JavaLexer lexer = new JavaLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            JavaParser parser = new JavaParser(tokens);
            parseTrees[i] = parser.compilationUnit();
        }
        Logger.info("Done.");


        //-----------Class Information extraction--------------------------//
        Logger.info("\nExtracting class-infos ... ");
        allClassInfos = new HashMap<>();
        List<JavaClass[]> filesClasses = new ArrayList<>();
        for (int i = 0; i < files.length; ++i) {
            List<JavaClass> classesList = JavaClassExtractor.extractInfo(files[i].getPath(), parseTrees[i]);
            filesClasses.add(classesList.toArray(new JavaClass[classesList.size()]));
            for (JavaClass cls : classesList)
                allClassInfos.put(cls.NAME, cls);
        }
        Logger.info("Done.");

        //--------------Method-DEF Information initialization-------------------//
        Logger.info("\nInitializing method-DEF infos ... ");
        methodDEFs = new HashMap<>();
        for (JavaClass[] classArray : filesClasses) {
            for (JavaClass cls : classArray) {
                for (JavaMethod mtd : cls.getAllMethods()) {
                    List<MethodDefInfo> list = methodDEFs.get(mtd.NAME);
                    if (list == null) {
                        list = new ArrayList<>();
                        list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
                        methodDEFs.put(mtd.NAME, list);
                    } else {
                        list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
                        // no need to do 'methodDEFs.put(...)' again
                    }
                }
            }
        }
        Logger.info("Done.");

        //---------------------Analyzing Method-DEF info for imported libs-------------------------//
        analyzeImportsDEF(filesClasses);

        //-----------------Extracting USE-DEF Information for program-----------------//

        methodCodePropertyGraph[] cpgs = new methodCodePropertyGraph[files.length];
        for (int i = 0; i < cpgs.length; ++i) {
            cpgs[i] = new methodCodePropertyGraph(files[i].getName());
            cpgs[i].removeVertex(cpgs[i].root);
        }

        //
        Map<ParserRuleContext, Object>[] cpgNodes = new Map[parseTrees.length];
        for (int i = 0; i < parseTrees.length; ++i)
            cpgNodes[i] = new IdentityHashMap<>();
        //
        Logger.info("\nIterative DEF-USE analysis ... ");
        boolean changed;
        int iteration = 0;
        do {
            ++iteration;
            changed = false;
            for (int i = 0; i < files.length; ++i) {
                currentFile = files[i].getName();
                JavacpgMethodLevel.DefUseVisitor defUse = new JavacpgMethodLevel.DefUseVisitor(iteration, filesClasses.get(i), cpgs[i], cpgNodes[i]);
                defUse.visit(parseTrees[i]);
                changed |= defUse.changed;
            }
            Logger.debug("Iteration #" + iteration + ": " + (changed ? "CHANGED" : "NO-CHANGE"));
            Logger.debug("\n========================================\n");
        } while (changed);
        Logger.info("Done.");

        //-----------Building ICFG-----------------------------------------------------//
        //-----------------------EXTRACTING_CLASS_INFORMATION--------------------------//
        for(File javaFile: files){
            try{
                for(JavaClass jc: JavaClassExtractor.extractInfo(javaFile)){
                    javaClasses.add(jc);
                }
            }catch(IOException ex){
                System.err.println(ex);
            }
        }

        //----------------------EXTRACTING JAVA CLASS INFO-------------------------------//
        alwaysAvailableClasses.addAll(JavaClassExtractor.extractJavaLangInfo());

        //-------------------------------------------------------------------------------//
        Map<ParserRuleContext, Object>[] ctxToKey = new Map[parseTrees.length];
        for(int i = 0; i < parseTrees.length; i++){
            ctxToKey[i] = new IdentityHashMap<>();
        }

        for(int i = 0; i < parseTrees.length; i++){
            currentFileClasses.clear();
            currentFileClasses.addAll(JavaClassExtractor.extractInfo(files[i]));
            ICFGVisitor icfgvisit = new ICFGVisitor();
            icfgvisit.visit(parseTrees[i]);
            ctxToKey[i] = icfgvisit.getMap();
        }

        Logger.info("\n Creating CFG with calling properties");
        cpgControlFlowGraph[] callCFG = new cpgControlFlowGraph[files.length];
        for(int i = 0; i < files.length; i++){
            callCFG[i] = JavacpgCFGBuilder.build(files[i].getName(), parseTrees[i], "calls", ctxToKey[i]);
        }
        Logger.info("Done with callCFG");

        // Build control-flow graphs for all Java files including the extracted DEF-USE info ...
        Logger.info("\nExtracting CFGs ... ");
        cpgControlFlowGraph[] cfgs = new cpgControlFlowGraph[files.length];
        for (int i = 0; i < files.length; ++i)
            cfgs[i] = JavacpgCFGBuilder.build(files[i].getName(), parseTrees[i], "pdnode", cpgNodes[i]);
        Logger.info("Done.");


        // Finally, traverse all control-flow paths and draw data-flow dependency edges ...
        Logger.info("\nAdding data-flow edges ... ");
        for (int i = 0; i < files.length; ++i) {
            addDataFlowEdges(cfgs[i], cpgs[i]);
            Iterator itr = cfgs[i].allVerticesIterator();
            Iterator icfgitr = callCFG[i].allVerticesIterator();
            while (itr.hasNext() && icfgitr.hasNext()) {
                cpgCFGNode node = (cpgCFGNode) itr.next();
                cpgCFGNode inode = (cpgCFGNode) icfgitr.next();
                if (inode.getProperty("calls") != null) {
                    node.setProperty("calls", inode.getProperty("calls"));
                }
            }
        }
        Logger.info("Done.\n");

        for (cpgControlFlowGraph cfg : cfgs) {
            for (cpgCFGNode entry : cfg.getAllMethodEntries()) {
                if (cfg.getPackage() != null) {
                    entry.setProperty("packageName", cfg.getPackage());
                } else {
                    entry.setProperty("packageName", "");
                }
                ArrayList<cpgCFGNode> exitpoints = new ArrayList<>();
                GraphTraversal<cpgCFGNode, cpgCFGEdge> iter = new DepthFirstTraversal<>(cfg, entry);
                while(iter.hasNext()){
                    cpgCFGNode node = iter.nextVertex();
                    if(node.getNodeType().toString() == "CFG_NODE" && cfg.getOutDegree(node) == 0){
                        exitpoints.add(node);
                    }
                }
                entry.setProperty("exits", exitpoints);
            }
        }

        for (int filenum = 0; filenum < files.length; ++filenum){
            cpgControlFlowGraph[] methodCFG = new cpgControlFlowGraph[cfgs[filenum].getAllMethodEntries().length];
            Map<MethodKey, cpgControlFlowGraph> keyToEntry = new HashMap<>();
            Map<MethodKey, cpgCFGNode> entryKey = new HashMap<>();
            int num = 0;
            for (cpgCFGNode entry : cfgs[filenum].getAllMethodEntries()) {
                //--------------------------Adding-Nodes--------------------------------------//
                methodCFG[num] = new cpgControlFlowGraph(entry.getProperty("name").toString());
                methodCFG[num].removeVertex(methodCFG[num].root);
                methodCFG[num].addMethodEntry(entry);

                GraphTraversal<cpgCFGNode, cpgCFGEdge> iter = new DepthFirstTraversal<>(cfgs[filenum], entry);
                while (iter.hasNext()) {
                    cpgCFGNode node = iter.nextVertex();
                    methodCFG[num].addVertex(node);
                }
                //-------------------------Adding-Edges---------------------------------------//
                Set<Edge<cpgCFGNode, cpgCFGEdge>> edge = cfgs[filenum].copyEdgeSet();
                Iterator<Edge<cpgCFGNode, cpgCFGEdge>> edgeiter = edge.iterator();
                while (edgeiter.hasNext()){
                    Edge<cpgCFGNode, cpgCFGEdge> cfgedge = edgeiter.next();
                    if (methodCFG[num].containsVertex(cfgedge.source) && methodCFG[num].containsVertex(cfgedge.target)){
                        methodCFG[num].addEdge(cfgedge);
                    }
                }
                MethodKey key = new MethodKey((String) entry.getProperty("packageName"), (String) entry.getProperty("class"), (String) entry.getProperty("name"), entry.getLineOfCode());
                keyToEntry.put(key, methodCFG[num]);
                entryKey.put(key, entry);
                num++;
            }

            for (int i = 0; i < methodCFG.length; ++i) {
                ArrayList<cpgControlFlowGraph> cfglist = new ArrayList<>();
                cfglist = connectmethods(methodCFG[i], keyToEntry, cfglist);
                //-------------------------Adding called methodcpgs-----------------------------------//
                if (cfglist.size() != 0) {
                    for (cpgControlFlowGraph mcfg : cfglist) {
                        methodCFG[i].addGraph(mcfg);
                        methodCFG[i].addMethodEntry(mcfg.getAllMethodEntries()[0]);
                    }
                    Iterator iter = methodCFG[i].allVerticesIterator();
                    while (iter.hasNext()) {
                        cpgCFGNode node = (cpgCFGNode) iter.next();
                        ArrayList<MethodKey> keys = (ArrayList<MethodKey>) node.getProperty("calls");
                        if (keys != null) {
                            for (MethodKey key : keys) {
                                cpgCFGNode entry = entryKey.get(key);
                                if (entry != null) {
                                    if (!methodCFG[i].containsEdge(node, entry)) {
                                        methodCFG[i].addEdge(new Edge<>(node, new cpgCFGEdge(cpgCFGEdge.Type.CALLS, "Calls"), entry));
                                        for (cpgCFGNode exitNode : (ArrayList<cpgCFGNode>) entry.getProperty("exits")) {
                                            if(!methodCFG[i].containsEdge(exitNode, node)){
                                                methodCFG[i].addEdge(new Edge<>(exitNode, new cpgCFGEdge(cpgCFGEdge.Type.RETURN, "Return"), node));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            cpgs[filenum].attachCFG(methodCFG);
        }
        Logger.info("Done.\n");
        return cpgs;
    }

    public static ArrayList<cpgControlFlowGraph> connectmethods(cpgControlFlowGraph methodCFG, Map<MethodKey, cpgControlFlowGraph> keyToEntry, ArrayList<cpgControlFlowGraph> listcfg){
        Iterator itr = methodCFG.allVerticesIterator();
        while (itr.hasNext()) {
            cpgCFGNode node = (cpgCFGNode) itr.next();
            if (node.getProperty("calls") != null) {
                ArrayList<MethodKey> keys = (ArrayList<MethodKey>) node.getProperty("calls");
                if (keys != null) {
                    for (MethodKey key : keys) {
                        cpgControlFlowGraph cfg = keyToEntry.get(key);
                        if (cfg != null) {
                            listcfg.add(cfg);
                            connectmethods(cfg, keyToEntry, listcfg);
                        }
                    }
                }
            }
        }
        return listcfg;
    }


    /**
     * Analyze method DEF information for imported libraries.
     */
    private static void analyzeImportsDEF(List<JavaClass[]> filesClasses) throws IOException {
        // Extract the import strings
        Logger.info("\nExtracting & Parsing imports ... ");
        Set<String> rawImports = new LinkedHashSet<>();
        rawImports.add("java.lang.*");
        for (JavaClass[] classes : filesClasses)
            for (JavaClass cls : classes)
                for (String qualifiedName : cls.IMPORTS)
                    rawImports.add(qualifiedName);
        /**
         * NOTE: Extract specific ZIP-entries for all imports to extract the ParseTree and JavaClass[] info.
         */
        ZipFile zip = new ZipFile("res/jdk7-src.zip");
        Set<String> imports = new LinkedHashSet<>();
        List<ParseTree> importsParseTrees = new ArrayList<>();
        List<JavaClass[]> importsClassInfos = new ArrayList<>();
        for (String qualifiedName : rawImports) {
            if (qualifiedName.endsWith(".*")) {
                for (ZipEntry ent : getPackageEntries(zip, qualifiedName)) {
                    if (imports.add(ent.getName())) {
                        ANTLRInputStream input = new ANTLRInputStream(zip.getInputStream(ent));
                        JavaLexer lexer = new JavaLexer(input);
                        CommonTokenStream tokens = new CommonTokenStream(lexer);
                        JavaParser parser = new JavaParser(tokens);
                        ParseTree tree = parser.compilationUnit();
                        //
                        importsParseTrees.add(tree);
                        List<JavaClass> list = JavaClassExtractor.extractInfo("src.zip/" + ent.getName(), tree);
                        importsClassInfos.add(list.toArray(new JavaClass[list.size()]));
                        for (JavaClass cls : list)
                            allClassInfos.put(cls.NAME, cls);
                    }
                }
            } else {
                String path = qualifiedName.replace('.', '/') + ".java";
                if (imports.add(path)) {
                    ZipEntry entry = zip.getEntry(path);
                    if (entry == null) {
                        imports.remove(path);
                        continue;
                    }
                    //
                    ANTLRInputStream input = new ANTLRInputStream(zip.getInputStream(entry));
                    JavaLexer lexer = new JavaLexer(input);
                    CommonTokenStream tokens = new CommonTokenStream(lexer);
                    JavaParser parser = new JavaParser(tokens);
                    ParseTree tree = parser.compilationUnit();
                    //
                    importsParseTrees.add(tree);
                    List<JavaClass> list = JavaClassExtractor.extractInfo("src.zip/" + path, tree);
                    importsClassInfos.add(list.toArray(new JavaClass[list.size()]));
                }
            }
        }
        Logger.info("Done.");
        //
        for (JavaClass[] classArray : importsClassInfos) {
            for (JavaClass cls : classArray) {
                for (JavaMethod mtd : cls.getAllMethods()) {
                    List<MethodDefInfo> list = methodDEFs.get(mtd.NAME);
                    if (list == null) {
                        list = new ArrayList<>();
                        list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
                        methodDEFs.put(mtd.NAME, list);
                    } else {
                        list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
                        // no need to do 'methodDEFs.put(...)' again
                    }
                }
            }
        }
        //
        Logger.info("\nAnalyzing imports DEF-USE ... ");
        Map<ParserRuleContext, Object> dummyMap = new HashMap<>();
        methodCodePropertyGraph dummyDDG = new methodCodePropertyGraph("Dummy.java");
        boolean changed;
        int iteration = 0;
        do {
            ++iteration;
            changed = false;
            int i = 0;
            for (String imprt : imports) {
                currentFile = "src.zip/" + imprt;
                JavacpgMethodLevel.DefUseVisitor defUse = new JavacpgMethodLevel.DefUseVisitor(iteration, importsClassInfos.get(i), dummyDDG, dummyMap);
                defUse.visit(importsParseTrees.get(i));
                changed |= defUse.changed;
                ++i;
            }
        } while (changed);
        Logger.info("Done.");
        //
        dummyDDG = null;
        dummyMap.clear();
    }

    /**
     * Returns an array of ZipEntry for a given wildcard package import.
     */
    private static ZipEntry[] getPackageEntries(ZipFile zip, String qualifiedName) {
        // qualifiedName ends with ".*"
        String pkg = qualifiedName.replace('.', '/').substring(0, qualifiedName.length() - 1);
        int slashCount = countSlashes(pkg);
        ArrayList<ZipEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> zipEntries = zip.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            if (entry.getName().startsWith(pkg)
                    && !entry.isDirectory()
                    && slashCount == countSlashes(entry.getName())) {
                entries.add(entry);
            }
        }
        return entries.toArray(new ZipEntry[entries.size()]);
    }

    /**
     * Returns the number of forward-slash ('/') characters in a given string.
     */
    private static int countSlashes(String str) {
        int slashCount = 0;
        for (char chr : str.toCharArray())
            if (chr == '/')
                ++slashCount;
        return slashCount;
    }


    /**
     * Traverses each CFG and uses the extracted DEF-USE info
     * to add Flow-dependence edges to the corresponding DDG.
     */
    private static void addDataFlowEdges(cpgControlFlowGraph cfg, methodCodePropertyGraph cpg) {
        Set<cpgCFGNode> visitedDefs = new LinkedHashSet<>();
        Set<Edge<CPGNode, CPGEdge>> addedDef = new LinkedHashSet<>()
;        for (cpgCFGNode entry : cfg.getAllMethodEntries()) {
            visitedDefs.clear();
            cpgCFPathTraversal defTraversal = new cpgCFPathTraversal(cfg, entry);
            while (defTraversal.hasNext()) {
                cpgCFGNode defCFNode = defTraversal.next();
                if (!visitedDefs.add(defCFNode)) {
                    defTraversal.continueNextPath();
                    continue;
                }
                CPGNode defNode = (CPGNode) defCFNode.getProperty("pdnode");
                if (defNode == null) {
                    //Logger.debug("No CPGNode: " + defCFNode);
                    continue;
                }
                if (defNode.getAllDEFs().length == 0)
                    continue;
                // first add any self-flows of this node
                for (String flow : defNode.getAllSelfFlows()) {
                    cpg.addEdge(new Edge<>(defNode, new CPGEdge(CPGEdge.Type.SELF_FLOW, flow), defNode));
                }
                // now traverse the CFG for any USEs till a DEF
                Set<cpgCFGNode> visitedUses = new LinkedHashSet<>();
                for (String def : defNode.getAllDEFs()) {
                    cpgCFPathTraversal useTraversal = new cpgCFPathTraversal(cfg, defCFNode);
                    visitedUses.clear();
                    cpgCFGNode useCFNode = useTraversal.next(); // skip start node
                    visitedUses.add(useCFNode);
                    while (useTraversal.hasNext()) {
                        useCFNode = useTraversal.next();
                        CPGNode useNode = (CPGNode) useCFNode.getProperty("pdnode");
                        if (useNode == null) {
                            //Logger.debug("No CPGNode: " + useCFNode);
                            continue;
                        }

                        if (useNode.hasDEF(def)){
                            if (!visitedUses.add(useCFNode))
                                useTraversal.continueNextPath();
                            else
                                if (useNode != defNode)
                                    cpg.addEdge(new Edge<>(defNode, new CPGEdge(CPGEdge.Type.DDG_D, def), useNode));
                        }
                        if (!visitedUses.add(useCFNode))
                            useTraversal.continueNextPath(); // no need to continue this path
                        else
                            if (useNode.hasUSE(def))
                                cpg.addEdge(new Edge<>(defNode, new CPGEdge(CPGEdge.Type.DDG_U, def), useNode));

                    }
                }
            }
        }
    }

    /**
     * Visitor class which performs iterative DEF-USE analysis for all program statements.
     */
    private static class DefUseVisitor extends JavaBaseVisitor<String> {

        private static final int PARAM = 1;
        private static final int FIELD = 101;
        private static final int LOCAL = 202;
        private static final int OUTER = 303;
        private int iteration;
        private int localVar;
        private boolean changed;
        private boolean analysisVisit;
        private JavaClass[] classInfos;
        private methodCodePropertyGraph cpg;
        private Set<String> defList, useList, selfFlowList;
        private Map<ParserRuleContext, Object> cpgNodes;
        private Deque<JavaClass> activeClasses;
        private MethodDefInfo methodDefInfo;
        private JavaField[] methodParams;
        private List<JavaField> localVars;
        //------variables for AST-------------//
        private String typeModifier;
        private String memberModifier;
        private Deque<CPGNode> rootStack;
        private Deque<CPGNode> preNodes;
        private Map<String, String> vars, fields, methods;
        private int varsCounter, fieldsCounter, methodsCounter;
        private Deque<String> classNames;
        //---------------------------------------------------------//

        public DefUseVisitor(int iter, JavaClass[] classInfos,
                             methodCodePropertyGraph cpg, Map<ParserRuleContext, Object> cpgNodes) {
            Logger.debug("FILE IS: " + currentFile);
            this.cpg = cpg;
            changed = false;
            iteration = iter;
            analysisVisit = false;
            this.cpgNodes = cpgNodes;
            this.classInfos = classInfos;
            defList = new LinkedHashSet<>();
            useList = new LinkedHashSet<>();
            selfFlowList = new LinkedHashSet<>();
            activeClasses = new ArrayDeque<>();
            methodDefInfo = null;
            methodParams = new JavaField[0];
            localVars = new ArrayList<>();
            rootStack = new ArrayDeque<>();
            preNodes = new ArrayDeque<>();
            vars = new LinkedHashMap<>();
            fields = new LinkedHashMap<>();
            methods = new LinkedHashMap<>();
            varsCounter = 0;
            fieldsCounter = 0;
            methodsCounter = 0;
            classNames = new ArrayDeque<>();


        }

        private void analyseDefUse(CPGNode node, ParseTree expression) {
            Logger.debug("--- ANALYSIS ---");
            Logger.debug(node.toString());
            analysisVisit = true;
            String expr = visit(expression);
            Logger.debug(expr);
            //
            StringBuilder locVarsStr = new StringBuilder(256);
            locVarsStr.append("LOCAL VARS = [");
            for (JavaField lv : localVars)
                locVarsStr.append(lv.TYPE).append(' ').append(lv.NAME).append(", ");
            locVarsStr.append("]");
            Logger.debug(locVarsStr.toString());
            //
            if (isUsableExpression(expr)) {
                useList.add(expr);
                Logger.debug("USABLE");
            }
            analysisVisit = false;
            Logger.debug("Changed = " + changed);
            Logger.debug("DEFs = " + Arrays.toString(node.getAllDEFs()));
            Logger.debug("USEs = " + Arrays.toString(node.getAllUSEs()));
            for (String def : defList) {
                int status = isDefined(def);
                if (status > -1) {
                    if (status < 100) {
                        methodDefInfo.setArgDEF(status, true);
                        Logger.debug("Method defines argument #" + status);
                    } else if (status == FIELD) {
                        methodDefInfo.setStateDEF(true);
                        if (def.startsWith("this."))
                            def = def.substring(5);
                        def = "$THIS." + def;
                        Logger.debug("Method defines object state.");
                    }
                    changed |= node.addDEF(def);
                } else
                    Logger.debug(def + " is not defined!");
            }
            Logger.debug("Changed = " + changed);
            Logger.debug("DEFs = " + Arrays.toString(node.getAllDEFs()));
            //
            for (String use : useList) {
                int status = isDefined(use);
                if (status > -1) {
                    if (status == FIELD) {
                        if (use.startsWith("this."))
                            use = use.substring(5);
                        use = "$THIS." + use;
                    }
                    changed |= node.addUSE(use);
                } else
                    Logger.debug(use + " is not defined!");
            }
            Logger.debug("Changed = " + changed);
            Logger.debug("USEs = " + Arrays.toString(node.getAllUSEs()));
            //
            for (String flow : selfFlowList) {
                int status = isDefined(flow);
                if (status > -1) {
                    if (status == FIELD) {
                        if (flow.startsWith("this."))
                            flow = flow.substring(5);
                        flow = "$THIS." + flow;
                    }
                    changed |= node.addSelfFlow(flow);
                } else
                    Logger.debug(flow + " is not defined!");
            }
            Logger.debug("Changed = " + changed);
            Logger.debug("SELF_FLOWS = " + Arrays.toString(node.getAllSelfFlows()));
            defList.clear();
            useList.clear();
            selfFlowList.clear();
            Logger.debug("----------------");
        }

        /**
         * Check if a given symbol is a defined variable.
         * This returns -1 if the symbol is not defined; otherwise,
         * it returns 101 if the symbol is a class field,
         * or returns 202 if the symbol is a local variable,
         * or returns 303 if the symbol is an outer class field,
         * or if the symbol is a method parameter, returns the index of the parameter.
         */
        private int isDefined(String id) {
            for (int i = 0; i < methodParams.length; ++i)
                if (methodParams[i].NAME.equals(id))
                    return i;
            for (JavaField local : localVars)
                if (local.NAME.equals(id))
                    return LOCAL;
            if (id.startsWith("this."))
                id = id.substring(5);
            for (JavaField field : activeClasses.peek().getAllFields())
                if (field.NAME.equals(id))
                    return FIELD;
            for (JavaClass cls : activeClasses)
                for (JavaField field : cls.getAllFields())
                    if (field.NAME.equals(id))
                        return OUTER;
            return -1;
        }

        /**
         * Return type of a given symbol.
         * Returns null if symbol is not found.
         */
        private String getType(String id) {
            if (isUsableExpression(id)) {
                for (JavaField param : methodParams)
                    if (param.NAME.equals(id))
                        return param.TYPE;
                for (JavaField local : localVars)
                    if (local.NAME.equals(id))
                        return local.TYPE;
                if (id.startsWith("this."))
                    id = id.substring(4);
                for (JavaField field : activeClasses.peek().getAllFields())
                    if (field.NAME.equals(id))
                        return field.TYPE;
                for (JavaClass cls : activeClasses)
                    for (JavaField field : cls.getAllFields())
                        if (field.NAME.equals(id))
                            return field.TYPE;
                Logger.debug("getType(" + id + ") : is USABLE but NOT DEFINED");
                return null;
            } else {
                Logger.debug("getType(" + id + ") : is NOT USABLE");
                // might be:
                // 'this'
                // 'super'
                // literal ($INT, $DBL, $CHR, $STR, $BOL)
                // class-name  [ ID ]
                // constructor-call [ $NEW creator ]
                // method-call [ expr(exprList) ]
                // casting [ $CAST(type) expr ]
                // array-indexing  [ expr[expr] ]
                // unary-op [ ++, --, !, ~ ]
                // paren-expr [ (...) ]
                // array-init [ {...} ]
                return null;
            }
        }

        private JavaClass findClass(String type) {
            return null;
        }

        /**
         * Find and return matching method-definition-info.
         * Returns null if not found.
         */
        private MethodDefInfo findDefInfo(String callee, String name, JavaParser.ExpressionListContext ctx) {
            List<MethodDefInfo> list = methodDEFs.get(name);
            Logger.debug("METHOD NAME: " + name);
            Logger.debug("# found = " + (list == null ? 0 : list.size()));
            //
            if (list == null)
                return null;
            //
            if (list.size() == 1) { // only one candidate
                Logger.debug("SINGLE CANDIDATE");
                MethodDefInfo mtd = list.get(0);
                // just check params-count to make sure
                if (ctx != null && mtd.PARAM_TYPES != null &&
                        mtd.PARAM_TYPES.length != ctx.expression().size())
                    return null;
                Logger.debug("WITH MATCHING PARAMS COUNT");
                return mtd;
            }
            //
            if (callee == null) { // no callee; so search for self methods
                Logger.debug("NO CALLEE");
                forEachDefInfo:
                for (MethodDefInfo mtd : list) {
                    // check package-name
                    if (!mtd.PACKAGE.equals(activeClasses.peek().PACKAGE))
                        continue;
                    // check class-name
                    boolean classNameMatch = false;
                    for (JavaClass cls : activeClasses) {
                        if (mtd.CLASS_NAME.equals(cls.NAME)) {
                            classNameMatch = true;
                            break;
                        }
                    }
                    if (!classNameMatch)
                        continue;
                    // check params-count
                    if (ctx != null && mtd.PARAM_TYPES != null &&
                            mtd.PARAM_TYPES.length != ctx.expression().size())
                        continue;
                    // check params-types
                    if (ctx != null) {
                        String[] argTypes = new String[ctx.expression().size()];
                        for (int i = 0; i < argTypes.length; ++i) {
                            String arg = visit(ctx.expression(i));
                            argTypes[i] = getType(arg);
                        }
                        if (mtd.PARAM_TYPES != null) {
                            for (int i = 0; i < argTypes.length; ++i) {
                                if (argTypes[i] == null)
                                    continue;
                                if (!argTypes[i].equals(mtd.PARAM_TYPES[i]))
                                    continue forEachDefInfo;
                            }
                        }
                    }
                    return mtd;
                }
            } else if (isDefined(callee) > -1) { // has a defined callee
                Logger.debug("DEFINED CALLEE");
                String type = getType(callee);
                JavaClass cls = allClassInfos.get(type);
                if (cls != null && cls.hasMethod(name)) {
                    forEachDefInfo:
                    for (MethodDefInfo mtd : list) {
                        // check package-name
                        if (!mtd.PACKAGE.equals(cls.PACKAGE))
                            continue;
                        // check class-name
                        if (!mtd.CLASS_NAME.equals(cls.NAME))
                            continue;
                        // check params-count
                        if (ctx != null && mtd.PARAM_TYPES != null &&
                                mtd.PARAM_TYPES.length != ctx.expression().size())
                            continue;
                        // check params-types
                        if (ctx != null) {
                            String[] argTypes = new String[ctx.expression().size()];
                            for (int i = 0; i < argTypes.length; ++i) {
                                String arg = visit(ctx.expression(i));
                                argTypes[i] = getType(arg);
                            }
                            if (mtd.PARAM_TYPES != null) {
                                for (int i = 0; i < argTypes.length; ++i) {
                                    if (argTypes[i] == null)
                                        continue;
                                    if (!argTypes[i].equals(mtd.PARAM_TYPES[i]))
                                        continue forEachDefInfo;
                                }
                            }
                        }
                        return mtd;
                    }
                    Logger.debug("METHOD DEF INFO NOT FOUND!");
                } else {
                    Logger.debug((cls == null ?
                            "CLASS OF TYPE " + type + " NOT FOUND!" :
                            "CLASS HAS NO SUCH METHOD!"));
                }
            } else { // has an undefined callee
                Logger.debug("UNDEFINED CALLEE.");
                //
                // TODO: use a global retType for visiting expressions
                //
            }
            return null;
        }

        /**
         * Find and return matching method-definition-info.
         * Returns null if not found.
         */
        private MethodDefInfo findDefInfo(String name, String type, JavaField[] params) {
            List<MethodDefInfo> infoList = methodDEFs.get(name);
            if (infoList.size() > 1) {
                forEachInfo:
                for (MethodDefInfo info : infoList) {
                    if (!info.PACKAGE.equals(activeClasses.peek().PACKAGE))
                        continue;
                    if (!info.CLASS_NAME.equals(activeClasses.peek().NAME))
                        continue;
                    if ((info.RET_TYPE == null && type != null) ||
                            (info.RET_TYPE != null && type == null))
                        continue;
                    if (type != null && !type.startsWith(info.RET_TYPE))
                        continue;
                    if (info.PARAM_TYPES != null) {
                        if (info.PARAM_TYPES.length != params.length)
                            continue;
                        for (int i = 0; i < params.length; ++i)
                            if (!params[i].TYPE.startsWith(info.PARAM_TYPES[i]))
                                continue forEachInfo;
                    } else if (params.length > 0)
                        continue;
                    return info;
                }
            } else if (infoList.size() == 1)
                return infoList.get(0);
            return null;
        }

        /**************************************
         **************************************
         ***          DECLARATIONS          ***
         **************************************
         **************************************/

        @Override
        public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
            for (JavaClass cls : classInfos) {
                if (cls.NAME.equals(ctx.Identifier().getText())) {
                    activeClasses.push(cls);
                    visit(ctx.classBody());
                    activeClasses.pop();
                    break;
                }
            }
            return null;
        }

        @Override
        public String visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
            // Just ignore enums for now ...
            return null;
        }

        @Override
        public String visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
            // Just ignore interfaces for now ...
            return null;
        }

        @Override
        public String visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
            if (ctx.block() != null) {
                localVars.clear();
                methodParams = new JavaField[0];
                methodDefInfo = new MethodDefInfo(null, "static-block", "", activeClasses.peek().NAME, null);
                return null;
            } else {
                return visitChildren(ctx);
            }
        }

        @Override
        public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
            CPGNode entry;
            if (iteration == 1) {
                entry = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                entry.setLineOfCode(ctx.getStart().getLine());
                entry.setCode(ctx.Identifier().getText() + ' ' + getOriginalCodeText(ctx.formalParameters()));
                entry.setProperty("name", ctx.Identifier().getText());
                cpg.addVertex(entry);
                cpgNodes.put(ctx, entry);

                //-------------------------Extract all parameter types and IDs----------------------//
                List<String> paramIDs = new ArrayList<>();
                List<String> paramTypes = new ArrayList<>();
                if (ctx.formalParameters().formalParameterList() != null) {
                    for (JavaParser.FormalParameterContext prm :
                            ctx.formalParameters().formalParameterList().formalParameter()) {
                        paramTypes.add(visitType(prm.typeType()));
                        paramIDs.add(prm.variableDeclaratorId().Identifier().getText());

                    }
                    JavaParser.LastFormalParameterContext lastParam =
                            ctx.formalParameters().formalParameterList().lastFormalParameter();
                    if (lastParam != null) {
                        paramTypes.add(visitType(lastParam.typeType()));
                        paramIDs.add(lastParam.variableDeclaratorId().Identifier().getText());
                    }
                }
                methodParams = new JavaField[paramIDs.size()];
                for (int i = 0; i < methodParams.length; ++i) {
                    methodParams[i] = new JavaField(null, false, paramTypes.get(i), paramIDs.get(i));
                }
                entry.setProperty("params", methodParams);
                for (String var : paramIDs) {
                    changed |= entry.addDEF(var);
                }
            } else {
                entry = (CPGNode) cpgNodes.get(ctx);
                methodParams = (JavaField[]) entry.getProperty("params");
            }
            methodDefInfo = findDefInfo((String) entry.getProperty("name"), null, methodParams);

            if (methodDefInfo == null) {
                Logger.error("Constructor NOT FOUND!");
                Logger.error("NAME = " + (String) entry.getProperty("name"));
                Logger.error("TYPE = null");
                Logger.error("PARAMS = " + Arrays.toString(methodParams));
                Logger.error("CLASS = " + activeClasses.peek().NAME);
                Logger.error("PACKAGE = " + activeClasses.peek().PACKAGE);
                List list = methodDEFs.get((String) entry.getProperty("name"));
                for (int i = 0; i < list.size(); ++i)
                    Logger.error(list.get(i).toString());
            }

            // Now visit method body ...
            localVars.clear();
            visit(ctx.constructorBody());
            //
            localVars.clear();
            methodParams = new JavaField[0];
            return null;
        }

        @Override
        public String visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {

            CPGNode entry;
            if (iteration == 1) {
                entry = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                entry.setLineOfCode(ctx.getStart().getLine());
                String retType = "void";
                if (ctx.typeType() != null)
                    retType = ctx.typeType().getText();
                String args = getOriginalCodeText(ctx.formalParameters());
                entry.setCode(retType + " " + ctx.Identifier().getText() + args);
                entry.setProperty("name", ctx.Identifier().getText());
                entry.setProperty("type", retType);
                cpg.addVertex(entry);
                cpgNodes.put(ctx, entry);
                // Extract all parameter types and IDs
                List<String> paramIDs = new ArrayList<>();
                List<String> paramTypes = new ArrayList<>();
                if (ctx.formalParameters().formalParameterList() != null) {
                    //-----------------------------VARIABLE_NODE-----------------------------------//
                    for (JavaParser.FormalParameterContext paramCtx :
                            ctx.formalParameters().formalParameterList().formalParameter()) {
                        //-------------------------------------------------------------------------//
                        paramTypes.add(visitType(paramCtx.typeType()));
                        paramIDs.add(paramCtx.variableDeclaratorId().Identifier().getText());
                        //---------------------------TYPE------------------------------------------//
                    }
                    JavaParser.LastFormalParameterContext lastParam =
                            ctx.formalParameters().formalParameterList().lastFormalParameter();
                    if (lastParam != null) {
                        paramTypes.add(visitType(lastParam.typeType()));
                        paramIDs.add(lastParam.variableDeclaratorId().Identifier().getText());

                    }
                }
                methodParams = new JavaField[paramIDs.size()];
                for (int i = 0; i < methodParams.length; ++i)
                    methodParams[i] = new JavaField(null, false, paramTypes.get(i), paramIDs.get(i));
                entry.setProperty("params", methodParams);
                //
                // Add initial DEF info: method entry nodes define the input-parameters
                for (String pid : paramIDs)
                    changed |= entry.addDEF(pid);
            } else {
                entry = (CPGNode) cpgNodes.get(ctx);
                methodParams = (JavaField[]) entry.getProperty("params");
            }

            methodDefInfo = findDefInfo((String) entry.getProperty("name"),
                    (String) entry.getProperty("type"), methodParams);
            if (methodDefInfo == null) {
                Logger.error("Method NOT FOUND!");
                Logger.error("NAME = " + (String) entry.getProperty("name"));
                Logger.error("TYPE = " + (String) entry.getProperty("type"));
                Logger.error("PARAMS = " + Arrays.toString(methodParams));
                Logger.error("CLASS = " + activeClasses.peek().NAME);
                Logger.error("PACKAGE = " + activeClasses.peek().PACKAGE);
                List list = methodDEFs.get((String) entry.getProperty("name"));
                for (int i = 0; i < list.size(); ++i)
                    Logger.error(list.get(i).toString());
            }

            // Now visit method body ...
            localVars.clear();
            if (ctx.methodBody() != null)
                visit(ctx.methodBody());
            //
            localVars.clear();
            methodParams = new JavaField[0];
            return null;
        }

        private String visitType(JavaParser.TypeTypeContext ctx) {

            return ctx.getText();
        }

        @Override
        public String visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {

            for (JavaParser.VariableDeclaratorContext varContx : ctx.variableDeclarators().variableDeclarator()){
                localVars.add(new JavaField(null, false, visitType(ctx.typeType()),varContx.variableDeclaratorId().Identifier().getText()));

            }

            if (analysisVisit)
                return visit(ctx.variableDeclarators());
            //------------------------------Declaration Node-------------------------------------//
            CPGNode declr;
            if (iteration == 1) {
                declr = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                declr.setLineOfCode(ctx.getStart().getLine());
                declr.setCode(getOriginalCodeText(ctx));
                cpg.addVertex(declr);
                cpgNodes.put(ctx, declr);
            } else
                declr = (CPGNode) cpgNodes.get(ctx);

            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(declr, ctx.variableDeclarators());
            return null;
        }

        /************************************
         ************************************
         ***          STATEMENTS          ***
         ************************************
         ************************************/

        @Override
        public String visitBlock(JavaParser.BlockContext ctx) {
            // block :  '{' blockStatement* '}'
            // Local vars defined inside a block, are only valid till the end of that block.
            int entrySize = localVars.size();
            //
            visitChildren(ctx);
            //
            if (localVars.size() > entrySize)
                localVars.subList(entrySize, localVars.size()).clear();
            return null;
        }

        @Override
        public String visitStatementExpression(JavaParser.StatementExpressionContext ctx) {

            if (analysisVisit)
                return visit(ctx.expression());
            //
            CPGNode expr;
            if (iteration == 1) {
                expr = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                expr.setLineOfCode(ctx.getStart().getLine());
                expr.setCode(getOriginalCodeText(ctx));
                cpg.addVertex(expr);
                cpgNodes.put(ctx, expr);
            } else
                expr = (CPGNode) cpgNodes.get(ctx);

            analyseDefUse(expr, ctx.expression());
            return null;
        }

        @Override
        public String visitIfStatement(JavaParser.IfStatementContext ctx) {
            // 'if' parExpression statement ('else' statement)?
            CPGNode ifNode;
            if (iteration == 1) {
                ifNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                ifNode.setLineOfCode(ctx.getStart().getLine());
                ifNode.setCode("if " + getOriginalCodeText(ctx.parExpression()));
                cpg.addVertex(ifNode);
                cpgNodes.put(ctx, ifNode);
            } else
                ifNode = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(ifNode, ctx.parExpression().expression());
            //
            for (JavaParser.StatementContext stmnt : ctx.statement())
                visit(stmnt);
            return null;
        }

        @Override
        public String visitForStatement(JavaParser.ForStatementContext ctx) {
            // 'for' '(' forControl ')' statement
            int entrySize = localVars.size();
            //  First, we should check type of for-loop ...
            if (ctx.forControl().enhancedForControl() != null) {
                // This is a for-each loop;
                //   enhancedForControl:
                //     variableModifier* typeType variableDeclaratorId ':' expression
                CPGNode forExpr;
                if (iteration == 1) {
                    forExpr = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                    forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
                    forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl()) + ")");
                    cpg.addVertex(forExpr);
                    cpgNodes.put(ctx.forControl().enhancedForControl(), forExpr);
                } else
                    forExpr = (CPGNode) cpgNodes.get(ctx.forControl().enhancedForControl());
                //
                // Now analyse DEF-USE by visiting the expression ...
                String type = visitType(ctx.forControl().enhancedForControl().typeType());
                String var = ctx.forControl().enhancedForControl().variableDeclaratorId().Identifier().getText();
                localVars.add(new JavaField(null, false, type, var));
                changed |= forExpr.addDEF(var);
                analyseDefUse(forExpr, ctx.forControl().enhancedForControl().expression());
            } else {
                // It's a traditional for-loop:
                //   forInit? ';' expression? ';' forUpdate?
                if (ctx.forControl().forInit() != null) { // non-empty init
                    CPGNode forInit;
                    if (iteration == 1) {
                        forInit = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                        forInit.setLineOfCode(ctx.forControl().forInit().getStart().getLine());
                        forInit.setCode(getOriginalCodeText(ctx.forControl().forInit()));
                        cpg.addVertex(forInit);
                        cpgNodes.put(ctx.forControl().forInit(), forInit);
                    } else
                        forInit = (CPGNode) cpgNodes.get(ctx.forControl().forInit());
                    //
                    // Now analyse DEF-USE by visiting the expression ...
                    if (ctx.forControl().forInit().expressionList() != null)
                        analyseDefUse(forInit, ctx.forControl().forInit().expressionList());
                    else
                        analyseDefUse(forInit, ctx.forControl().forInit().localVariableDeclaration());
                }
                // for-expression
                if (ctx.forControl().expression() != null) { // non-empty predicate-expression
                    CPGNode forExpr;
                    if (iteration == 1) {
                        forExpr = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                        forExpr.setLineOfCode(ctx.forControl().expression().getStart().getLine());
                        forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl().expression()) + ")");
                        cpg.addVertex(forExpr);
                        cpgNodes.put(ctx.forControl().expression(), forExpr);
                    } else
                        forExpr = (CPGNode) cpgNodes.get(ctx.forControl().expression());
                    //
                    // Now analyse DEF-USE by visiting the expression ...
                    analyseDefUse(forExpr, ctx.forControl().expression());
                }
                // for-update
                if (ctx.forControl().forUpdate() != null) { // non-empty for-update
                    CPGNode forUpdate;
                    if (iteration == 1) {
                        forUpdate = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                        forUpdate.setCode(getOriginalCodeText(ctx.forControl().forUpdate()));
                        forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
                        cpg.addVertex(forUpdate);
                        cpgNodes.put(ctx.forControl().forUpdate(), forUpdate);
                    } else
                        forUpdate = (CPGNode) cpgNodes.get(ctx.forControl().forUpdate());
                    //
                    // Now analyse DEF-USE by visiting the expression ...
                    analyseDefUse(forUpdate, ctx.forControl().forUpdate().expressionList());
                }
            }
            // visit for loop body
            String visit = visit(ctx.statement());
            // clear any local vars defined in the for loop
            if (localVars.size() > entrySize)
                localVars.subList(entrySize, localVars.size()).clear();
            return visit;
        }

        @Override
        public String visitWhileStatement(JavaParser.WhileStatementContext ctx) {
            // 'while' parExpression statement
            CPGNode whileNode;
            if (iteration == 1) {
                whileNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                whileNode.setLineOfCode(ctx.getStart().getLine());
                whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
                cpg.addVertex(whileNode);
                cpgNodes.put(ctx, whileNode);
            } else
                whileNode = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(whileNode, ctx.parExpression().expression());
            //
            return visit(ctx.statement());
        }

        @Override
        public String visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
            // 'do' statement 'while' parExpression ';'
            visit(ctx.statement());
            //
            CPGNode whileNode;
            if (iteration == 1) {
                whileNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                whileNode.setLineOfCode(ctx.parExpression().getStart().getLine());
                whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
                cpg.addVertex(whileNode);
                cpgNodes.put(ctx, whileNode);

            } else
                whileNode = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(whileNode, ctx.parExpression().expression());
            return null;
        }

        @Override
        public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
            //  'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
            //  switchBlockStatementGroup :  switchLabel+ blockStatement+
            CPGNode switchNode;
            if (iteration == 1) {
                switchNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                switchNode.setLineOfCode(ctx.getStart().getLine());
                switchNode.setCode("switch " + getOriginalCodeText(ctx.parExpression()));
                cpg.addVertex(switchNode);
                cpgNodes.put(ctx, switchNode);


            } else
                switchNode = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(switchNode, ctx.parExpression().expression());
            //
            for (JavaParser.SwitchBlockStatementGroupContext scx : ctx.switchBlockStatementGroup())
                visit(scx);
            for (JavaParser.SwitchLabelContext scx : ctx.switchLabel())
                visit(scx);
            return null;
        }

        @Override
        public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
            // 'return' expression? ';'
            CPGNode ret;
            if (iteration == 1) {
                ret = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                ret.setLineOfCode(ctx.getStart().getLine());
                ret.setCode(getOriginalCodeText(ctx));
                cpg.addVertex(ret);
                cpgNodes.put(ctx, ret);
            } else
                ret = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            if (ctx.expression() != null)
                analyseDefUse(ret, ctx.expression());
            return null;
        }

        @Override
        public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
            // 'synchronized' parExpression block
            CPGNode syncStmt;
            if (iteration == 1) {
                syncStmt = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                syncStmt.setLineOfCode(ctx.getStart().getLine());
                syncStmt.setCode("synchronized " + getOriginalCodeText(ctx.parExpression()));
                cpg.addVertex(syncStmt);
                cpgNodes.put(ctx, syncStmt);
            } else
                syncStmt = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(syncStmt, ctx.parExpression().expression());
            //
            return visit(ctx.block());
        }

        @Override
        public String visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
            // 'throw' expression ';'
            CPGNode throwNode;
            if (iteration == 1) {
                throwNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                throwNode.setLineOfCode(ctx.getStart().getLine());
                throwNode.setCode("throw " + getOriginalCodeText(ctx.expression()));
                cpg.addVertex(throwNode);
                cpgNodes.put(ctx, throwNode);
            } else
                throwNode = (CPGNode) cpgNodes.get(ctx);
            //
            // Now analyse DEF-USE by visiting the expression ...
            analyseDefUse(throwNode, ctx.expression());
            return null;
        }

        @Override
        public String visitTryStatement(JavaParser.TryStatementContext ctx) {
            // 'try' block (catchClause+ finallyBlock? | finallyBlock)
            //
            // The 'try' block has no DEF-USE effect, so no need for CPGNodes;
            // just visit the 'block'
            CPGNode tryNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
            tryNode.setCode(getOriginalCodeText(ctx));
            visit(ctx.block());
            //
            // But the 'catchClause' define a local exception variable;
            // so we need to visit any available catch clauses
            if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
                // 'catch' '(' variableModifier* catchType Identifier ')' block
                for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
                    CPGNode catchNode;
                    if (iteration == 1) {
                        catchNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                        catchNode.setLineOfCode(cx.getStart().getLine());
                        catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
                        cpg.addVertex(catchNode);
                        cpgNodes.put(cx, catchNode);
                    } else
                        catchNode = (CPGNode) cpgNodes.get(cx);
                    //
                    // Define the exception var
                    String type = cx.catchType().getText();
                    String var = cx.Identifier().getText();
                    JavaField exceptionVar = new JavaField(null, false, type, var);
                    localVars.add(exceptionVar);
                    changed |= catchNode.addDEF(var);
                    //
                    visit(cx.block());
                    localVars.remove(exceptionVar);
                }
            }
            if (ctx.finallyBlock() != null)
                // 'finally' block
                visit(ctx.finallyBlock().block());

            return null;
        }

        @Override
        public String visitTryWithResourceStatement(JavaParser.TryWithResourceStatementContext ctx) {

            int entrySize = localVars.size();
            // Analyze all resources
            for (JavaParser.ResourceContext rsrx : ctx.resourceSpecification().resources().resource()) {
                CPGNode resource;
                if (iteration == 1) {
                    resource = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                    resource.setLineOfCode(rsrx.getStart().getLine());
                    resource.setCode(getOriginalCodeText(rsrx));
                    cpg.addVertex(resource);
                    cpgNodes.put(rsrx, resource);
                } else {
                    resource = (CPGNode) cpgNodes.get(rsrx);
                }
                // Define the resource variable
                String type = rsrx.classOrInterfaceType().getText();
                String var = rsrx.variableDeclaratorId().getText();
                localVars.add(new JavaField(null, false, type, var));
                //
                // Now analyse DEF-USE by visiting the expression ...
                analyseDefUse(resource, rsrx);
            }

            // The 'try' block has no DEF-USE effect, so no need for CPGNodes;
            // just visit the 'block'
            visit(ctx.block());
            //
            // But the 'catchClause' define a local exception variable;
            // so we need to visit any available catch clauses
            if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
                // 'catch' '(' variableModifier* catchType Identifier ')' block
                for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
                    CPGNode catchNode;
                    if (iteration == 1) {
                        catchNode = new CPGNode(CPGNode.NodeTyp.CFG_NODE);
                        catchNode.setLineOfCode(cx.getStart().getLine());
                        catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
                        cpg.addVertex(catchNode);
                        cpgNodes.put(cx, catchNode);
                    } else
                        catchNode = (CPGNode) cpgNodes.get(cx);
                    //
                    // Define the exception var
                    String type = cx.catchType().getText();
                    String var = cx.Identifier().getText();
                    JavaField exception = new JavaField(null, false, type, var);
                    localVars.add(exception);
                    changed |= catchNode.addDEF(var);
                    //
                    visit(cx.block());
                    //
                    localVars.remove(exception);
                }
            }
            if (ctx.finallyBlock() != null)
                // 'finally' block
                visit(ctx.finallyBlock().block());
            //
            // Remove resources from local vars ...
            if (localVars.size() > entrySize)
                localVars.subList(entrySize, localVars.size()).clear();
            return null;
        }

        /***********************************************
         ***********************************************
         ***       NON-DETERMINANT EXPRESSIONS       ***
         ***********************************************
         ***********************************************/

        @Override
        public String visitExprPrimary(JavaParser.ExprPrimaryContext ctx) {
            // primary
            //   :   '(' expression ')'
            //   |   'this'
            //   |   'super'
            //   |   literal
            //   |   Identifier
            //   |   typeType '.' 'class'
            //   |   'void' '.' 'class'
            //   |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | 'this' arguments)
            //
            // literal
            //   :  IntegerLiteral   |  FloatingPointLiteral
            //   |  CharacterLiteral |  StringLiteral
            //   |  BooleanLiteral   |  'null'
            //
            // nonWildcardTypeArguments :  '<' typeList '>'
            //
            // explicitGenericInvocationSuffix :  'super' superSuffix  |  Identifier arguments
            //
            JavaParser.PrimaryContext primary = ctx.primary();
            if (primary.getText().startsWith("(") && primary.getText().endsWith(")"))
                return '(' + visit(primary.expression()) + ')';
            if (primary.getText().equals("this"))
                return "this";
            if (primary.getText().equals("super"))
                return "super";
            if (primary.literal() != null) {
                if (primary.literal().IntegerLiteral() != null)
                    return "$INT";
                if (primary.literal().FloatingPointLiteral() != null)
                    return "$DBL";
                if (primary.literal().CharacterLiteral() != null)
                    return "$CHR";
                if (primary.literal().StringLiteral() != null)
                    return "$STR";
                if (primary.literal().BooleanLiteral() != null)
                    return "$BOL";
                return "$NUL";
            }
            if (primary.Identifier() != null)
                return primary.Identifier().getText();
            if (primary.getText().endsWith(".class"))
                return "$CLS";
            //
            return primary.getText();
        }

        @Override
        public String visitExprDotID(JavaParser.ExprDotIDContext ctx) {
            // expression '.' Identifier
            return visit(ctx.expression()) + '.' + ctx.Identifier().getText();
        }

        @Override
        public String visitExprDotThis(JavaParser.ExprDotThisContext ctx) {
            // expression '.' 'this'
            return visit(ctx.expression()) + ".this";
        }

        @Override
        public String visitExprCasting(JavaParser.ExprCastingContext ctx) {
            // '(' typeType ')' expression
            return "$CAST(" + visitType(ctx.typeType()) + ") " + visit(ctx.expression());
        }

        @Override
        public String visitExpressionList(JavaParser.ExpressionListContext ctx) {
            // expressionList : expression (',' expression)*
            StringBuilder expList = new StringBuilder(visit(ctx.expression(0)));
            for (int i = 1; i < ctx.expression().size(); ++i)
                expList.append(", ").append(visit(ctx.expression(i)));
            return expList.toString();
        }

        /*****************************************************
         *****************************************************
         ***    DETERMINANT EXPRESSIONS (RETURN OBJECT)    ***
         *****************************************************
         *****************************************************/

        /**
         * Check to see if the given expression is USABLE.
         * An expression is usable if we are required to add it to the USE-list.
         * Any expression who is DEFINABLE should be added to the USE-list.
         * An expression is definable, if it holds a value which can be modified in the program.
         * For example, Class names and Class types are not definable.
         * Method invocations are not definable.
         * Literals are also not definable.
         */
        private boolean isUsableExpression(String expr) {
            // must not be a literal or of type 'class'.
            if (expr.startsWith("$"))
                return false;
            // must not be a method-call or parenthesized expression
            if (expr.endsWith(")"))
                return false;
            // must not be an array-indexing expression
            if (expr.endsWith("]"))
                return false;
            // must not be post unary operation expression
            if (expr.endsWith("++") || expr.endsWith("--"))
                return false;
            // must not be a pre unary operation expression
            if (expr.startsWith("+") || expr.startsWith("-") || expr.startsWith("!") || expr.startsWith("~"))
                return false;
            // must not be an array initialization expression
            if (expr.endsWith("}"))
                return false;
            // must not be an explicit generic invocation expression
            if (expr.startsWith("<"))
                return false;
            //
            return true;
        }

        /**
         * Visit the list of arguments of a method call, and return a proper string.
         * This method will also add usable expressions to the USE-list.
         */
        private String visitMethodArgs(JavaParser.ExpressionListContext ctx, MethodDefInfo defInfo) {
            // expressionList :  expression (',' expression)*
            if (ctx != null) {
                StringBuilder args = new StringBuilder();
                List<JavaParser.ExpressionContext> argsList = ctx.expression();
                String arg = visit(argsList.get(0));
                args.append(arg);
                if (isUsableExpression(arg)) {
                    useList.add(arg);
                    if (defInfo != null && defInfo.argDEFs()[0])
                        defList.add(arg);
                }
                for (int i = 1; i < argsList.size(); ++i) {
                    arg = visit(argsList.get(i));
                    args.append(", ").append(arg);
                    if (isUsableExpression(arg)) {
                        useList.add(arg);
                        if (defInfo != null && defInfo.argDEFs()[i])
                            defList.add(arg);
                    }
                }
                return args.toString();
            } else
                return "";
        }

        @Override
        public String visitExprMethodInvocation(JavaParser.ExprMethodInvocationContext ctx) {
            // expression '(' expressionList? ')'
            String callee = null;
            String callExpression = visit(ctx.expression());
            String methodName = callExpression;
            Logger.debug("---");
            Logger.debug("CALL EXPR : " + methodName);
            //
            int start = 0, lastDot = callExpression.lastIndexOf('.');
            if (lastDot > 0) {
                //start = callExpression.substring(0, lastDot).lastIndexOf('.');
                //if (start < 0)
                //	start = 0;
                //else
                //	++start;
                callee = callExpression.substring(start, lastDot);
                Logger.debug("HAS CALLEE : " + callee);
                if (isUsableExpression(callee)) {
                    useList.add(callee);
                    Logger.debug("CALLEE IS USABLE");
                }
                methodName = callExpression.substring(lastDot + 1);
            } else {
                Logger.debug("NO CALLEE");
                methodName = callExpression;
            }
            //
            MethodDefInfo defInfo = findDefInfo(callee, methodName, ctx.expressionList());
            Logger.debug("FIND DEF RESULT: " + defInfo);
            Logger.debug("---");
            if (callee != null && defInfo != null && defInfo.doesStateDEF())
                defList.add(callee);
            return callExpression + '(' + visitMethodArgs(ctx.expressionList(), defInfo) + ')';
        }

        @Override
        public String visitExprNewCreator(JavaParser.ExprNewCreatorContext ctx) {
            // 'new' creator
            //
            // creator
            //   :  nonWildcardTypeArguments createdName classCreatorRest
            //   |  createdName (arrayCreatorRest | classCreatorRest)
            //
            // createdName
            //   :  Identifier typeArgumentsOrDiamond? ('.' Identifier typeArgumentsOrDiamond?)*
            //   |  primitiveType
            //
            // arrayCreatorRest
            //   :  '[' (
            //           ']' ('[' ']')* arrayInitializer
            //          |
            //           expression ']' ('[' expression ']')* ('[' ']')*
            //          )
            //
            // classCreatorRest :  arguments classBody?
            //
            // 1st process 'createdName'
            String creator = null, rest;
            if (ctx.creator().createdName().primitiveType() != null)
                creator = ctx.creator().createdName().primitiveType().getText();
            else {
                for (TerminalNode id : ctx.creator().createdName().Identifier())
                    creator = id.getText();
            }
            // 2nd process '(arrayCreatorRest | classCreatorRest)'
            if (ctx.creator().arrayCreatorRest() != null) {
                // array constructor
                if (ctx.creator().arrayCreatorRest().arrayInitializer() != null) {
                    // process 'ctx.creator().arrayCreatorRest().arrayInitializer()'
                    JavaParser.ArrayInitializerContext arrayInitCtx =
                            ctx.creator().arrayCreatorRest().arrayInitializer();
                    StringBuilder arrayInit = new StringBuilder();
                    for (JavaParser.VariableInitializerContext initCtx :
                            arrayInitCtx.variableInitializer()) {
                        String init = visit(initCtx);
                        if (isUsableExpression(init))
                            useList.add(init);
                        arrayInit.append(init).append(", ");
                    }
                    rest = "{ " + arrayInit.toString() + " }";
                } else {
                    // process '[' expression ']' ('[' expression ']')* ('[' ']')*
                    StringBuilder arrayCreate = new StringBuilder();
                    for (JavaParser.ExpressionContext exprCtx :
                            ctx.creator().arrayCreatorRest().expression()) {
                        String expr = visit(exprCtx);
                        if (isUsableExpression(expr))
                            useList.add(expr);
                        arrayCreate.append('[').append(expr).append(']');
                    }
                    rest = arrayCreate.toString();
                }
            } else {
                // class constructor ...
                JavaParser.ArgumentsContext argsCtx = ctx.creator().classCreatorRest().arguments();
                MethodDefInfo defInfo = findDefInfo(null, creator, argsCtx.expressionList());
                rest = '(' + visitMethodArgs(argsCtx.expressionList(), defInfo) + ')';
            }
            return "$NEW " + creator + rest;
        }

        @Override
        public String visitExprDotNewInnerCreator(JavaParser.ExprDotNewInnerCreatorContext ctx) {
            // expression '.' 'new' nonWildcardTypeArguments? innerCreator
            //
            // innerCreator :  Identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
            //
            // classCreatorRest :  arguments classBody?
            //
            // 1st process 'expression'
            String expression = visit(ctx.expression());
            if (isUsableExpression(expression))
                useList.add(expression);
            // 2nd process 'innerCreator'
            String creator = ctx.innerCreator().Identifier().getText();
            // 3rd process constructor arguments ...
            JavaParser.ArgumentsContext argsCtx = ctx.innerCreator().classCreatorRest().arguments();
            MethodDefInfo defInfo = findDefInfo(null, creator, argsCtx.expressionList());
            String rest = '(' + visitMethodArgs(argsCtx.expressionList(), defInfo) + ')';
            return expression + ".$NEW " + creator + rest;
        }

        @Override
        public String visitExprDotSuper(JavaParser.ExprDotSuperContext ctx) {
            // expression '.' 'super' superSuffix
            //
            // superSuffix :  arguments  |  '.' Identifier arguments?
            //
            StringBuilder result = new StringBuilder();
            String expr = visit(ctx.expression());
            if (isUsableExpression(expr))
                useList.add(expr);
            result.append(expr).append(".super");
            if (ctx.superSuffix().arguments() != null) {
                // add 'expr.super' to USEs for method-call
                useList.add(result.toString());
                if (ctx.superSuffix().getText().startsWith(".")) {
                    // expr.super.method(...) call
                    result.append('.').append(ctx.superSuffix().Identifier().getText()).append('(');
                    // else  expr.super(...) constructor call
                }
                // visit and add arguments to USEs
                result.append(visitMethodArgs(ctx.superSuffix().arguments().expressionList(), null));
                result.append(')');
            } else {
                // expr.super.filed reference
                result.append('.').append(ctx.superSuffix().Identifier().getText());
            }
            return result.toString();
        }

        @Override
        public String visitExprDotGenInvok(JavaParser.ExprDotGenInvokContext ctx) {
            // expression '.' explicitGenericInvocation
            //
            // explicitGenericInvocation :  nonWildcardTypeArguments explicitGenericInvocationSuffix
            //
            // nonWildcardTypeArguments :  '<' typeList '>'
            //
            // explicitGenericInvocationSuffix :  'super' superSuffix  |  Identifier arguments
            //
            String expression = visit(ctx.expression());
            if (isUsableExpression(expression))
                useList.add(expression);
            String invocSuffix;
            JavaParser.ExplicitGenericInvocationSuffixContext suffixContext =
                    ctx.explicitGenericInvocation().explicitGenericInvocationSuffix();
            if (suffixContext.Identifier() != null) {
                invocSuffix = suffixContext.Identifier().getText();
                invocSuffix += '(' + visitMethodArgs(suffixContext.arguments().expressionList(), null) + ')';
            } else {
                invocSuffix = "super";
                if (suffixContext.superSuffix().Identifier() != null)
                    invocSuffix += '.' + suffixContext.superSuffix().Identifier().getText();
                if (suffixContext.superSuffix().arguments() != null)
                    invocSuffix += '(' + visitMethodArgs(suffixContext.superSuffix().arguments().expressionList(), null) + ')';
            }
            return expression + '.' + ctx.explicitGenericInvocation().nonWildcardTypeArguments().getText() + invocSuffix;
        }

        @Override
        public String visitExprArrayIndexing(JavaParser.ExprArrayIndexingContext ctx) {
            // expression '[' expression ']'
            String array = visit(ctx.expression(0));
            if (isUsableExpression(array))
                useList.add(array);
            String index = visit(ctx.expression(1));
            if (isUsableExpression(index))
                useList.add(index);
            return array + '[' + index + ']';
        }

        @Override
        public String visitExprConditional(JavaParser.ExprConditionalContext ctx) {
            // expression '?' expression ':' expression
            String prdct = visit(ctx.expression(0));
            if (isUsableExpression(prdct))
                useList.add(prdct);
            String retTrue = visit(ctx.expression(1));
            if (isUsableExpression(retTrue))
                useList.add(retTrue);
            String retFalse = visit(ctx.expression(2));
            if (isUsableExpression(retFalse))
                useList.add(retFalse);
            return prdct + " ? " + retTrue + " : " + retFalse;
        }

        /*****************************************************
         *****************************************************
         ***      DETERMINANT EXPRESSIONS (NO RETURN)      ***
         *****************************************************
         *****************************************************/

        @Override
        public String visitExprPostUnaryOp(JavaParser.ExprPostUnaryOpContext ctx) {
            // expression ('++' | '--')
            String expr = visit(ctx.expression());
            if (isUsableExpression(expr)) {
                useList.add(expr);
                defList.add(expr);
            }
            if (ctx.getText().endsWith("++"))
                return expr + "++";
            else
                return expr + "--";
        }

        @Override
        public String visitExprPreUnaryOp(JavaParser.ExprPreUnaryOpContext ctx) {
            // ('+'|'-'|'++'|'--') expression
            String expr = visit(ctx.expression());
            if (isUsableExpression(expr)) {
                useList.add(expr);
                if (ctx.getText().startsWith("--") || ctx.getText().startsWith("++")) {
                    defList.add(expr);
                    selfFlowList.add(expr);
                }
            }
            if (ctx.getText().charAt(0) == '+') {
                if (ctx.getText().startsWith("++"))
                    return "++" + expr;
                else
                    return "+" + expr;
            } else {
                if (ctx.getText().startsWith("--"))
                    return "--" + expr;
                else
                    return "-" + expr;
            }
        }

        @Override
        public String visitExprNegation(JavaParser.ExprNegationContext ctx) {
            // ('~'|'!') expression
            String expr = visit(ctx.expression());
            if (isUsableExpression(expr))
                useList.add(expr);
            if (ctx.getText().startsWith("~"))
                return '~' + expr;
            else
                return '!' + expr;
        }

        @Override
        public String visitExprMulDivMod(JavaParser.ExprMulDivModContext ctx) {
            // expression ('*'|'/'|'%') expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            String op;
            switch (ctx.getChild(1).getText().charAt(0)) {
                case '*':
                    op = " $MUL ";
                    break;
                case '/':
                    op = " $DIV ";
                    break;
                default:
                    op = " $MOD ";
                    break;
            }
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + op + expr2 + ')';
        }

        @Override
        public String visitExprAddSub(JavaParser.ExprAddSubContext ctx) {
            // expression ('+'|'-') expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            String op;
            switch (ctx.getChild(1).getText().charAt(0)) {
                case '+':
                    op = " $ADD ";
                    break;
                default:
                    op = " $SUB ";
                    break;
            }
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + op + expr2 + ')';
        }

        @Override
        public String visitExprBitShift(JavaParser.ExprBitShiftContext ctx) {
            // expression ('<' '<' | '>' '>' '>' | '>' '>') expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " $SHIFT " + expr2 + ')';
        }

        @Override
        public String visitExprComparison(JavaParser.ExprComparisonContext ctx) {
            // expression ('<=' | '>=' | '>' | '<') expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " $COMP " + expr2 + ')';
        }

        @Override
        public String visitExprInstanceOf(JavaParser.ExprInstanceOfContext ctx) {
            // expression 'instanceof' typeType
            String expr = visit(ctx.expression());
            // the parethesis are added to mark this expression as used
            return '(' + expr + " $INSTANCE " + ctx.typeType().getText() + ')';
        }

        @Override
        public String visitExprEquality(JavaParser.ExprEqualityContext ctx) {
            // expression ('==' | '!=') expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " $EQL " + expr2 + ')';
        }

        @Override
        public String visitExprBitAnd(JavaParser.ExprBitAndContext ctx) {
            // expression '&' expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " & " + expr2 + ')';
        }

        @Override
        public String visitExprBitXOR(JavaParser.ExprBitXORContext ctx) {
            // expression '^' expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " ^ " + expr2 + ')';
        }

        @Override
        public String visitExprBitOr(JavaParser.ExprBitOrContext ctx) {
            // expression '|' expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " | " + expr2 + ')';
        }

        @Override
        public String visitExprLogicAnd(JavaParser.ExprLogicAndContext ctx) {
            // expression '&&' expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " && " + expr2 + ')';
        }

        @Override
        public String visitExprLogicOr(JavaParser.ExprLogicOrContext ctx) {
            // expression '||' expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1))
                useList.add(expr1);
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " || " + expr2 + ')';
        }

        @Override
        public String visitExprAssignment(JavaParser.ExprAssignmentContext ctx) {
            // expression (  '='   |  '+='  |  '-='  |  '*='  |  '/='  |   '&='  |  '|='
            //            |  '^='  |   '>>='  |  '>>>='  |  '<<='  |  '%=' ) expression
            String expr1 = visit(ctx.expression(0));
            String expr2 = visit(ctx.expression(1));
            if (isUsableExpression(expr1)) {
                if (!ctx.getChild(1).getText().equals("="))
                    useList.add(expr1);
                defList.add(expr1);
            }
            if (isUsableExpression(expr2))
                useList.add(expr2);
            // the parethesis are added to mark this expression as used
            return '(' + expr1 + " $ASSIGN " + expr2 + ')';
        }

        @Override
        public String visitVariableDeclarators(JavaParser.VariableDeclaratorsContext ctx) {
            // variableDeclarators :  variableDeclarator (',' variableDeclarator)*
            StringBuilder vars = new StringBuilder();
            vars.append(visit(ctx.variableDeclarator(0)));
            for (int i = 1; i < ctx.variableDeclarator().size(); ++i)
                vars.append(", ").append(visit(ctx.variableDeclarator(i)));
            return vars.toString();
        }

        @Override
        public String visitVariableDeclarator(JavaParser.VariableDeclaratorContext ctx) {
            // variableDeclarator :  variableDeclaratorId ('=' variableInitializer)?
            //
            // variableDeclaratorId :  Identifier ('[' ']')*
            //
            String init = "";
            String varID = ctx.variableDeclaratorId().Identifier().getText();
            if (ctx.variableInitializer() != null) {
                init = visit(ctx.variableInitializer());
                if (isUsableExpression(init))
                    useList.add(init);
                defList.add(varID);
                init = " $INIT " + init;
            }
            return "$VAR " + varID + init;
        }

        @Override
        public String visitVariableInitializer(JavaParser.VariableInitializerContext ctx) {
            // variableInitializer :  arrayInitializer  |  expression
            //
            // arrayInitializer :  '{' (variableInitializer (',' variableInitializer)* (',')? )? '}'
            //
            if (ctx.expression() != null)
                return visit(ctx.expression());

            StringBuilder arrayInit = new StringBuilder();
            for (JavaParser.VariableInitializerContext initCtx :
                    ctx.arrayInitializer().variableInitializer()) {
                String init = visit(initCtx);
                if (isUsableExpression(init))
                    useList.add(init);
                arrayInit.append(init).append(", ");
            }
            return "{ " + arrayInit.toString() + " }";
        }

        /*****************************************************
         *****************************************************
         *****************************************************/

        /**
         * Get the original program text for the given parser-rule context.
         * This is required for preserving whitespaces.
         */
        private String getOriginalCodeText(ParserRuleContext ctx) {
            int start = ctx.start.getStartIndex();
            int stop = ctx.stop.getStopIndex();
            Interval interval = new Interval(start, stop);
            return ctx.start.getInputStream().getText(interval);
        }

    }

    private static class ICFGVisitor extends JavaBaseVisitor<String> {

        private ArrayList<JavaClass> availableClasses;
        private Deque<JavaClass> activeClasses;
        private LinkedHashMap<String, String> globalVariables;
        private LinkedHashMap<String, JavaClass> localVariables;
        private String currentPackageName;
        private ArrayList<JavaMethod> returnMethod;
        private JavaClass returnType;
        private JavaMethod notImplemented;
        private Map<ParserRuleContext, Object> contextualProperties;
        private ParserRuleContext currentContext;

        public ICFGVisitor() {
            activeClasses = new ArrayDeque<>();
            globalVariables = new LinkedHashMap<>();
            localVariables = new LinkedHashMap<>();
            availableClasses = new ArrayList<>();
            // = new ArrayList<>();
            currentPackageName = "";
            returnMethod = new ArrayList<>();
            returnType = null;
            notImplemented = new JavaMethod("", false, false, "", "NULL", null, 0);
            contextualProperties = new HashMap<>();
            currentContext = null;
            availableClasses.addAll(alwaysAvailableClasses);
            availableClasses.addAll(currentFileClasses);
        }

        @Override
        public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
            //packageDeclaration
            //annotation* 'package' qualifiedName ';'
            currentPackageName = ctx.qualifiedName().getText();
            //All classes of a package can be used in other files in the same package
            for (JavaClass jc : javaClasses) {
                if (jc.PACKAGE.equals(currentPackageName)) {
                    availableClasses.add(jc);
                }
            }
            return null;
        }

        @Override
        public String visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
            //importDeclaration
            //'import' 'static'? qualifiedName ('.' '*')? ';'
            //qualifiedName
            //Identifier ('.' Identifier)*

            String importedPackage = ctx.qualifiedName().getText();

            if (ctx.getText().contains(".*")) {
                for (JavaClass jc : javaClasses) {
                    if (importedPackage.equals(jc.PACKAGE)) {
                        availableClasses.add(jc);
                    }
                }
            } else {
                for (JavaClass jc : javaClasses) {
                    if (importedPackage.equals(jc.PACKAGE + '.' + jc.NAME)) {
                        availableClasses.add(jc);
                    }
                }
            }
            return null;
        }

        @Override
        public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {

            for (JavaClass jc : javaClasses) {
                if (jc.NAME.equals(ctx.Identifier().getText()) && jc.PACKAGE.equals(currentPackageName)) {
                    activeClasses.push(jc);
                    break;
                }
            }

            //Add current class fields to he global variables list
            for (JavaClass cls : activeClasses) {
                for (JavaField jf : cls.getAllFields()) {
                    globalVariables.put(jf.NAME, jf.TYPE);
                }
            }

            //Extract imported class information from Java standard library
            try {
                for (JavaClass jc : JavaClassExtractor.extractImportsInfo(activeClasses.peek().IMPORTS)) {
                    if (!availableClasses.contains(jc)) {
                        availableClasses.add(jc);
                    }
                }

            } catch (IOException ex) {
                System.err.println(ex);
            }

            visit(ctx.classBody());

            //clear the list and regenerate it. (required for inner class)
            globalVariables.clear();
            activeClasses.pop();
            for (JavaClass cls : activeClasses) {
                for (JavaField jf : cls.getAllFields()) {
                    globalVariables.put(jf.NAME, jf.TYPE);
                }
            }
            return null;
        }

        @Override
        public String visitBlock(JavaParser.BlockContext ctx) {
            //block:   '{' blockStatement* '}'
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            visitChildren(ctx);
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
            //'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            currentContext = ctx;
            visit(ctx.parExpression());
            currentContext = null;
            for (JavaParser.SwitchBlockStatementGroupContext cx : ctx.switchBlockStatementGroup()) {
                visit(cx);
            }
            for (JavaParser.SwitchLabelContext cx : ctx.switchLabel()) {
                visit(cx);
            }
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitIfStatement(JavaParser.IfStatementContext ctx) {
            //'if' parExpression statement ('else' statement)?
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            currentContext = ctx;
            visit(ctx.parExpression());
            currentContext = null;
            for (JavaParser.StatementContext cx : ctx.statement()) {
                visit(cx);
            }
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitWhileStatement(JavaParser.WhileStatementContext ctx) {
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            currentContext = ctx;
            visit(ctx.parExpression());
            currentContext = null;
            visit(ctx.statement());
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            visit(ctx.statement());
            currentContext = ctx;
            visit(ctx.parExpression());
            currentContext = null;
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitForStatement(JavaParser.ForStatementContext ctx) {
            // 'for' '(' forControl ')' statement
            //forControl:
            //       enhancedForControl |   forInit? ';' expression? ';' forUpdate?
            //enhancedForControl:variableModifier* typeType variableDeclaratorId ':' expression
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);

            if (ctx.forControl().enhancedForControl() != null) {
                currentContext = ctx.forControl().enhancedForControl();
                visit(ctx.forControl().enhancedForControl());
                currentContext = null;
            } else {
                if (ctx.forControl().forInit() != null) {
                    currentContext = ctx.forControl().forInit();
                    visitChildren(ctx.forControl().forInit());
                    currentContext = null;
                }
                if (ctx.forControl().expression() != null) {
                    currentContext = ctx.forControl().expression();
                    visit(ctx.forControl().expression());
                    currentContext = null;
                }
                if (ctx.forControl().forUpdate() != null) {
                    currentContext = ctx.forControl().expression();
                    visit(ctx.forControl().forUpdate());
                    currentContext = null;
                }
            }
            visit(ctx.statement());

            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
            // statementExpression ';'
            currentContext = ctx;
            visitChildren(ctx);
            currentContext = null;
            return null;
        }

        @Override
        public String visitEnhancedForControl(JavaParser.EnhancedForControlContext ctx) {
            //enhancedForControl:   variableModifier* typeType variableDeclaratorId ':' expression
            String name = ctx.variableDeclaratorId().Identifier().getText();
            StringBuilder type = new StringBuilder(visit(ctx.typeType()));
            int idx = ctx.variableDeclaratorId().getText().indexOf('[');
            if (idx > 0) {
                type.append(ctx.variableDeclaratorId().getText().substring(idx));
            }
            localVariables.put(name, findClassbyName(type.toString()));
            visit(ctx.expression());
            return null;
        }

        @Override
        public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
            // 'return' expression? ';'
            currentContext = ctx;
            if (ctx.expression() != null)
                visit(ctx.expression());
            return null;
        }

        @Override
        public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
            // 'synchronized' parExpression block
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            currentContext = ctx;
            visit(ctx.parExpression());
            currentContext = null;
            visit(ctx.block());
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);
            return null;
        }

        @Override
        public String visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
            // 'throw' expression ';'
            currentContext = ctx;
            visit(ctx.expression());
            currentContext = null;
            return null;
        }

        @Override
        public String visitTryStatement(JavaParser.TryStatementContext ctx) {
            // 'try' block (catchClause+ finallyBlock? | finallyBlock)
            LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
            tempLocalVariables.putAll(localVariables);
            visit(ctx.block());
            localVariables.clear();
            localVariables.putAll(tempLocalVariables);

            if (ctx.finallyBlock() != null) {
                // 'finally' block
                tempLocalVariables.clear();
                tempLocalVariables.putAll(localVariables);
                visit(ctx.finallyBlock().block());
                localVariables.clear();
                localVariables.putAll(tempLocalVariables);
            }

            // Now visit any available catch clauses
            if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
                // 'catch' '(' variableModifier* catchType Identifier ')' block

                for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
                    tempLocalVariables.clear();
                    tempLocalVariables.putAll(localVariables);
                    String name = cx.Identifier().getText();
                    //Assume that the catch type is a simple class name, for now
                    String type = cx.catchType().getText();
                    localVariables.put(name, findClassbyName(type));
                    visit(cx.block());
                    localVariables.clear();
                    localVariables.putAll(tempLocalVariables);
                }
            }

            return null;
        }

        @Override
        public String visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
			/*
			localVariableDeclarationStatement:    localVariableDeclaration ';'
			localVariableDeclaration:		variableModifier* typeType variableDeclarators
			variableDeclarators:   variableDeclarator (',' variableDeclarator)*
			variableDeclarator:   variableDeclaratorId ('=' variableInitializer)?
			variableDeclaratorId:   Identifier ('[' ']')*
			variableInitializer:   arrayInitializer	|   expression
			 */
            //Store variable types in the 'loccalVariables' map
            for (JavaParser.VariableDeclaratorContext var : ctx.variableDeclarators().variableDeclarator()) {
                String name = var.variableDeclaratorId().Identifier().getText();
                StringBuilder type = new StringBuilder(visit(ctx.typeType()));
                int idx = var.variableDeclaratorId().getText().indexOf('[');
                if (idx > 0) {
                    type.append(var.variableDeclaratorId().getText().substring(idx));
                }
                localVariables.put(name, findClassbyName(type.toString()));
            }

            currentContext = ctx;
            visitChildren(ctx);
            currentContext = null;
            return null;
        }

        @Override
        public String visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
            // methodDeclaration
            //   :  (typeType|'void') Identifier formalParameters ('[' ']')*
            //      ('throws' qualifiedNameList)? ( methodBody | ';' )
            //
            // formalParameters
            //   :  '(' formalParameterList? ')'
            //
            // formalParameterList
            //   :  formalParameter (',' formalParameter)* (',' lastFormalParameter)?
            //   |  lastFormalParameter
            //
            // formalParameter
            //   :  variableModifier* typeType variableDeclaratorId
            //
            // lastFormalParameter
            //   :  variableModifier* typeType '...' variableDeclaratorId
            //
            // variableDeclaratorId
            //   :  Identifier ('[' ']')*

            if (ctx.formalParameters().formalParameterList() != null) {
                for (JavaParser.FormalParameterContext param
                        : ctx.formalParameters().formalParameterList().formalParameter()) {
                    localVariables.put(param.variableDeclaratorId().getText(), findClassbyName(visit(param.typeType())));
                }
                if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
                    localVariables.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText(), findClassbyName(visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType())));
                }
            }

            if (ctx.methodBody() != null) {
                visit(ctx.methodBody());
            }

            localVariables.clear();
            return null;
        }

        @Override
        public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
            // constructorDeclaration
            //   :  Identifier formalParameters ('throws' qualifiedNameList)? constructorBody

            if (ctx.formalParameters().formalParameterList() != null) {
                for (JavaParser.FormalParameterContext param
                        : ctx.formalParameters().formalParameterList().formalParameter()) {
                    localVariables.put(param.variableDeclaratorId().getText(), findClassbyName(visit(param.typeType())));
                }
                if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
                    localVariables.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText(), findClassbyName(visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType())));
                }
            }

            visit(ctx.constructorBody());
            localVariables.clear();
            return null;
        }

        @Override
        public String visitExprPrimary(JavaParser.ExprPrimaryContext ctx) {
			/*
			primary  :   '(' expression ')'
			|   'this'
			|   'super'
			|   literal
			|   Identifier
			|   typeType '.' 'class'
			|   'void' '.' 'class'
			|   nonWildcardTypeArguments (explicitGenericInvocationSuffix | 'this' arguments)
			;
			 */

            JavaClass currentClass = activeClasses.peek();

            if (ctx.primary().getText().equals("this")) {
                returnType = currentClass;
                returnMethod = findMethodbyName(currentClass, currentClass.NAME);
                return null;
            }

            if (ctx.primary().getText().equals("super")) {
                returnType = findClassbyName(currentClass.EXTENDS);
                if (returnType == null) {
                    return null;
                }
                returnMethod = findMethodbyName(returnType, returnType.NAME);
                return null;
            }

            //literal
            if (ctx.primary().literal() != null) {
                if (ctx.primary().literal().BooleanLiteral() != null) {
                    returnType = findClassbyName("Boolean");
                }
                //returnType =  new JavaClass("bool", "BuiltinType", "", "");

                if (ctx.primary().literal().CharacterLiteral() != null) {
                    returnType = findClassbyName("Character");
                }
                //returnType = new JavaClass("char", "BuiltinType", "", "");

                if (ctx.primary().literal().FloatingPointLiteral() != null) {
                    returnType = findClassbyName("Float");
                }
                //returnType = new JavaClass("float", "BuiltinType", "", "");

                if (ctx.primary().literal().IntegerLiteral() != null) {
                    returnType = findClassbyName("Integer");
                }
                //returnType = new JavaClass("int", "BuiltinType", "", "");

                if (ctx.primary().literal().StringLiteral() != null) {
                    returnType = findClassbyName("String");
                }
                //returnType = new JavaClass("String", "BuiltinType", "", "");
                return null;
            }

            if (ctx.primary().Identifier() != null) {
                //When the identifier is an object
                returnType = findVariableType(ctx.primary().Identifier().getText());
                //When the identifier is a class name
                //if(returnType != null)
                //	returnType = findClassbyName(ctx.primary().Identifier().getText());
                //returnType = findClassbyNameInAvailableClasses(ctx.primary().Identifier().getText());
                //When the identifier is a local method call
                if (returnType == null) {
                    returnMethod = findMethodbyName(currentClass, ctx.primary().Identifier().getText());
                }
                return null;
            }
            return visitChildren(ctx);
        }

        @Override
        public String visitExprDotID(JavaParser.ExprDotIDContext ctx) {
			/*
			expression '.' Identifier
			 */
            visit(ctx.expression());

            ArrayList<JavaMethod> currentMethod;
            //JavaClass fieldType
            JavaClass fieldType = findFieldType(returnType, ctx.Identifier().getText());
            if (fieldType == null) {
                currentMethod = findMethodbyName(returnType, ctx.Identifier().getText());
                if (!currentMethod.isEmpty()) {
                    returnMethod = currentMethod;
                    //returnType = findClassbyName(returnMethod.get(0).RET_TYPE);
                }

            } else {
                returnType = fieldType;
            }
            return null;
        }

        @Override
        public String visitExprDotNewInnerCreator(JavaParser.ExprDotNewInnerCreatorContext ctx) {
			/*
			expression '.' 'new' nonWildcardTypeArguments? innerCreator
			innerCreator:   Identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
			classCreatorRest:   arguments classBody?
			 */
            visit(ctx.expression());
            String innerClassName = ctx.innerCreator().Identifier().getText();
            JavaClass innerClass = findClassbyName(innerClassName);
            ArrayList<JavaMethod> innerCreatorMethods = findMethodbyName(innerClass, innerClassName);
            ArrayList<JavaClass> actualArguments = new ArrayList<>();
            if (ctx.innerCreator().classCreatorRest().arguments().expressionList() != null) {
                for (JavaParser.ExpressionContext expr
                        : ctx.innerCreator().classCreatorRest().arguments().expressionList().expression()) {
                    visit(expr);
                    actualArguments.add(returnType);
                }
            }
            //JavaMethod actualMethod = createCLGNode(ctx.innerCreator().Identifier().getText(), ctx.getStart().getLine(), creatorMethods, actualArguments);
            if (currentContext == null) {
                currentContext = ctx.expression();
            }
            createCLGNode(innerClass, innerCreatorMethods, actualArguments);
            //createCLGNode(ctx.expression(), innerClass, innerCreatorMethods, actualArguments);
            returnType = innerClass;
            return null;
        }

        @Override
        public String visitExprDotThis(JavaParser.ExprDotThisContext ctx) {
			/*
			expression '.' 'this'
			 */
            visit(ctx.expression());

            if (returnType != null) {
                returnMethod = findMethodbyName(returnType, returnType.NAME);
            }
            return null;
        }

        @Override
        public String visitExprDotSuper(JavaParser.ExprDotSuperContext ctx) {
			/*
			expression '.' 'super' superSuffix
			 */
            visit(ctx.expression());
            JavaClass parent = null;
            //ArrayList<JavaMethod> creators = new ArrayList<>();
            if (returnType != null) {
                parent = findClassbyName(returnType.EXTENDS);
            }
            if (parent != null) {
                returnMethod = findMethodbyName(parent, parent.NAME);
                returnType = parent;
            }
            return null;
        }

        @Override
        public String visitExprDotGenInvok(JavaParser.ExprDotGenInvokContext ctx) {
			/*
			expression '.' explicitGenericInvocation
			 */
            return visitChildren(ctx);
        }

        @Override
        public String visitExprMethodInvocation(JavaParser.ExprMethodInvocationContext ctx) {
			/*
			expression '(' expressionList? ')'
			expressionList:   expression (',' expression)*
			 */

            returnType = null;
            returnMethod.clear();

            visit(ctx.expression());

            ArrayList<JavaMethod> possibleCalls = new ArrayList<>();
            possibleCalls.addAll(returnMethod);
            JavaClass calleeClass = null;

            //For local method calls
            if (!returnMethod.isEmpty()) {
                if (returnType == null) {
                    calleeClass = activeClasses.peek();
                } else {
                    calleeClass = returnType;
                }
            }
            ArrayList<JavaClass> actualTypes = new ArrayList<>();
            returnType = null;
            returnMethod.clear();
            if (ctx.expressionList() != null) {
                for (JavaParser.ExpressionContext expr : ctx.expressionList().expression()) {
                    visit(expr);
                    actualTypes.add(returnType);
                    returnType = null;
                    returnMethod.clear();
                }
            }
            if (currentContext == null) {
                currentContext = ctx.expression();
            }

            JavaMethod actualMethod = createCLGNode(calleeClass, possibleCalls, actualTypes);

            if (actualMethod != null) {
                returnType = findClassbyName(actualMethod.RET_TYPE);
            } else {
                returnType = null;
            }
            return null;
        }

        @Override
        public String visitExprNewCreator(JavaParser.ExprNewCreatorContext ctx) {
			/*
			'new' creator
			creator:   nonWildcardTypeArguments createdName classCreatorRest
					|  createdName (arrayCreatorRest | classCreatorRest)
			nonWildcardTypeArguments:   '<' typeList '>'
			typeList:   typeType (',' typeType)*
			createdName:   Identifier typeArgumentsOrDiamond? ('.' Identifier typeArgumentsOrDiamond?)*
						|  primitiveType
			typeArgumentsOrDiamond:   '<' '>'    |   typeArguments
			typeArguments:   '<' typeArgument (',' typeArgument)* '>'
			classCreatorRest:   arguments classBody?
			arrayCreatorRest:   '['
								(   ']' ('[' ']')* arrayInitializer
								|   expression ']' ('[' expression ']')* ('[' ']')*
								)
			 */
            int last;
            JavaClass lastClass = null;
            if (ctx.creator().createdName().primitiveType() == null) {
                last = ctx.creator().createdName().Identifier().size() - 1;
                String className = ctx.creator().createdName().Identifier(last).getText();
                lastClass = findClassbyName(className);
            }

            ArrayList<JavaMethod> creators = new ArrayList<>();
            ArrayList<JavaClass> actualArguments = new ArrayList<>();
            if (lastClass != null) {
                creators = findMethodbyName(lastClass, lastClass.NAME);
                if (ctx.creator().classCreatorRest() != null) {
                    if (ctx.creator().classCreatorRest().arguments() != null) {
                        if (ctx.creator().classCreatorRest().arguments().expressionList() != null) {
                            for (JavaParser.ExpressionContext expr : ctx.creator().classCreatorRest().arguments().expressionList().expression()) {
                                visit(expr);
                                actualArguments.add(returnType);
                            }
                        }
                    }
                }
                if (currentContext == null) {
                    currentContext = ctx.creator().createdName();
                }
                createCLGNode(lastClass, creators, actualArguments);
            } else {
                if (currentContext == null) {
                    currentContext = ctx.creator().createdName();
                }
                createCLGNode(lastClass, creators, actualArguments);
            }
            returnType = lastClass;
            if (ctx.creator().classCreatorRest() != null) {
                if (ctx.creator().classCreatorRest().classBody() != null) {
                    visitChildren(ctx.creator().classCreatorRest().classBody());
                }
            }
            if (ctx.creator().arrayCreatorRest() != null) {
                visitChildren(ctx.creator().arrayCreatorRest());
            }
            return null;
        }

        @Override
        public String visitExprCasting(JavaParser.ExprCastingContext ctx) {
			/*
			'(' typeType ')' expression
			 */
            visit(ctx.expression());
            returnType = findClassbyName(visitTypeType(ctx.typeType()));
            return null;
        }

        @Override
        public String visitTypeType(JavaParser.TypeTypeContext ctx) {
            // typeType
            //   :  classOrInterfaceType ('[' ']')*  |  primitiveType ('[' ']')*
            StringBuilder type = new StringBuilder(visit(ctx.getChild(0)));
            int idx = ctx.getText().indexOf('[');
            if (idx > 0) {
                type.append(ctx.getText().substring(idx));
            }
            return type.toString();
        }

        @Override
        public String visitClassOrInterfaceType(JavaParser.ClassOrInterfaceTypeContext ctx) {
            // classOrInterfaceType
            //   :  Identifier typeArguments? ('.' Identifier typeArguments? )*
            //typeArguments:   '<' typeArgument (',' typeArgument)* '>'

            StringBuilder typeID = new StringBuilder(ctx.Identifier(0).getText());
            if (ctx.typeArguments(0) != null) {
                typeID.append(ctx.typeArguments(0).getText());
            }
            int i = 1;
            for (TerminalNode id : ctx.Identifier().subList(1, ctx.Identifier().size())) {
                typeID.append(".").append(id.getText());
                if (ctx.typeArguments(i) != null) {
                    typeID.append(ctx.typeArguments(i).getText());
                }
                i++;
            }
            return typeID.toString();
        }

        @Override
        public String visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
            // primitiveType :
            //     'boolean' | 'char' | 'byte' | 'short'
            //     | 'int' | 'long' | 'float' | 'double'

            return ctx.getText();
        }

        public Map<ParserRuleContext, Object> getMap() {
            return contextualProperties;
        }

        private JavaMethod[] MergeArrays(JavaMethod[] jm1, JavaMethod[] jm2) {
            if (jm1 == null) {
                return jm2;
            }
            if (jm2 == null) {
                return jm1;
            }
            int sizeofArray1 = jm1.length;
            int sizeofArray2 = jm2.length;
            JavaMethod[] result = new JavaMethod[sizeofArray1 + sizeofArray2];
            System.arraycopy(jm1, 0, result, 0, sizeofArray1);
            System.arraycopy(jm2, 0, result, sizeofArray1, sizeofArray2);
            return result;
        }

        private ArrayList<JavaMethod> findMethodbyName(JavaClass currentClass, String methodName) {

            ArrayList<JavaMethod> matchedMethods = new ArrayList<>();

            if (currentClass == null) {
                return matchedMethods;
            }
            //currentClass = activeClasses.peek();

            for (JavaMethod jm : currentClass.getAllMethods()) {
                if (jm.NAME.equals(methodName)) {
                    matchedMethods.add(jm);
                }
            }

            if (!matchedMethods.isEmpty()) {
                return matchedMethods;
            }

            if (currentClass.EXTENDS != null) {
                JavaClass jc = findClassbyName(currentClass.EXTENDS);
                if (jc != null) {
                    matchedMethods = findMethodbyName(jc, methodName);
                }
            }

            return matchedMethods;
        }

        private JavaClass findFieldType(JavaClass cls, String fieldName) {
            if (cls == null) {
                return null;
            }
            String fieldType = null;
            for (JavaField jf : cls.getAllFields()) {
                if (jf.NAME.equals(fieldName)) {
                    fieldType = jf.TYPE;
                }
            }
            return findClassbyName(fieldType);

        }

        private JavaClass findVariableType(String var) {
            if (!localVariables.isEmpty()) {
                for (String str : localVariables.keySet()) {
                    if (str.equals(var)) {
                        return localVariables.get(str);
                    }
                }
            }

            if (!globalVariables.isEmpty()) {
                for (String str : globalVariables.keySet()) {
                    if (str.equals(var)) {
                        return findClassbyName(globalVariables.get(str));
                    }
                }
            }
            return null;
        }

        private JavaClass findClassbyName(String className) {

            if (className == null) {
                return null;
            }

            StringBuilder clsName = new StringBuilder(className);
            String genericTypes = null;
            int idx = className.indexOf('<');
            if (idx > 0) {
                genericTypes = className.substring(idx);
                clsName.delete(idx, clsName.length());
            }
            className = clsName.toString();

            for (JavaClass jc : availableClasses) {
                if (jc.NAME.equals(className)) {
                    JavaClass cls = classInstantiate(jc, genericTypes);
                    return cls;
                }
            }
            return null;
        }

        private boolean isActualMethod(JavaMethod possibleMethod, ArrayList<JavaClass> actualTypes) {
            if (possibleMethod.ARG_TYPES == null) {
                if (actualTypes.isEmpty()) {
                    return true;
                } else {
                    return false;
                }
            }
            if (actualTypes.size() != possibleMethod.ARG_TYPES.length) {
                return false;
            }

            for (int i = 0; i < actualTypes.size(); i++) {
                String s1 = possibleMethod.ARG_TYPES[i];
                if (actualTypes.get(i) != null) {
                    String s2 = actualTypes.get(i).NAME;
                    if (!typeIsMatched(s1, s2)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean typeIsMatched(String s1, String s2) {
            return s1.equals(s2) || s1.equals(alias(s2)) || s2.equals(alias(s1)) || s1.equals("Object") || s2.equals("Object");
        }

        private String alias(String s) {
            if (s.equals("Integer"))
                return "int";
            if (s.equals("Character"))
                return "char";
            if (s.equals("Float") || s.equals("Boolean"))
                return s.toLowerCase();
            return s;
        }

        private JavaMethod createCLGNode(JavaClass cls, ArrayList<JavaMethod> possibleCalls, ArrayList<JavaClass> actualTypes) {
            JavaMethod actualMethod = null;
            if (possibleCalls.isEmpty()) {
                actualMethod = notImplemented;
            } else {
                for (JavaMethod jm : possibleCalls) {
                    if (isActualMethod(jm, actualTypes)) {
                        actualMethod = jm;
                        break;
                    }
                }
            }

            addKey(cls, actualMethod);
            return actualMethod;
        }

        private void addKey(JavaClass cls, JavaMethod method) {
            MethodKey key;
            if (method != null && cls != null) {
                key = new MethodKey(cls.PACKAGE, cls.NAME, method.NAME, method.LINE_OF_CODE);
            } else {
                key = new MethodKey("Not Implemented", "Not Implemented", "notImplemented", 0);
            }
            ArrayList<MethodKey> keys = new ArrayList<>();
            keys.add(key);
            if (contextualProperties.containsKey(currentContext)) {
                keys.addAll((ArrayList<MethodKey>) contextualProperties.get(currentContext));
            }
            contextualProperties.put(currentContext, keys);
        }

        private JavaClass classInstantiate(JavaClass cls, String generic) {
            if (generic == null)
                return cls;
            else {
                JavaClass instance = new JavaClass(cls.NAME, cls.PACKAGE, cls.EXTENDS, cls.FILE, cls.IMPORTS);
                String genericTypes = generic.substring(1, generic.length() - 1).trim();
                instance.setTypeParameters(genericTypes);
                String params = cls.getTypeParameters();
                //String[] genericType = genericTypes.split(",");
                String currentGenericType = "";
                String[] genericParam = params.split(",");
                HashMap<String, String> genericMap = new HashMap<>();

                for (int i = 0; i < genericParam.length; i++) {
                    //genericMap.put(genericParam[i].replace('?', ' ').replaceAll("extends", "").trim(), genericType[i].replace('?', ' ').replaceAll("extends", "").trim());
                    if (genericTypes.contains(",")) {
                        if (genericTypes.contains("<")) {
                            int index = genericTypes.indexOf("<");
                            if (genericTypes.indexOf(",") < index) {
                                currentGenericType = genericTypes.substring(0, genericTypes.indexOf(","));
                                genericTypes = genericTypes.substring(genericTypes.indexOf(",") + 1);
                            } else {
                                index += countSteps(genericTypes.substring(genericTypes.indexOf("<")));
                                currentGenericType = genericTypes.substring(0, index);
                                if (genericTypes.length() > index)
                                    genericTypes = genericTypes.substring(index + 1);
                            }
                        } else {
                            currentGenericType = genericTypes.substring(0, genericTypes.indexOf(","));
                            genericTypes = genericTypes.substring(genericTypes.indexOf(",") + 1);
                        }
                    } else
                        currentGenericType = genericTypes;
                    genericMap.put(genericParam[i].replace('?', ' ').replaceAll("extends", "").trim(), currentGenericType.replace('?', ' ').replaceAll("extends", "").trim());
                    //genericMap.put(genericParam[i].trim(), genericType[i].trim());
                }
                for (JavaMethod jm : cls.getAllMethods()) {
                    //instantiate type of arguments for each method
                    String[] args;
                    if (jm.ARG_TYPES != null) {
                        args = new String[jm.ARG_TYPES.length];
                        for (int i = 0; i < jm.ARG_TYPES.length; i++) {
                            if (genericMap.keySet().contains(jm.ARG_TYPES[i]))
                                args[i] = genericMap.get(jm.ARG_TYPES[i]);
                            else
                                args[i] = jm.ARG_TYPES[i];
                        }
                    } else
                        args = null;
                    //instantiate return type for each method
                    if (jm.RET_TYPE == null)
                        instance.addMethod(new JavaMethod(jm.MODIFIER, jm.STATIC, jm.ABSTRACT, jm.RET_TYPE, jm.NAME, args, jm.LINE_OF_CODE));
                    else if (genericMap.keySet().contains(jm.RET_TYPE))
                        instance.addMethod(new JavaMethod(jm.MODIFIER, jm.STATIC, jm.ABSTRACT, genericMap.get(jm.RET_TYPE), jm.NAME, args, jm.LINE_OF_CODE));
                    else
                        instance.addMethod(new JavaMethod(jm.MODIFIER, jm.STATIC, jm.ABSTRACT, jm.RET_TYPE, jm.NAME, args, jm.LINE_OF_CODE));
                }
                //instantiate class fields
                for (JavaField jf : cls.getAllFields()) {
                    if (genericMap.keySet().contains(jf.TYPE))
                        instance.addField(new JavaField(jf.MODIFIER, jf.STATIC, genericMap.get(jf.TYPE), jf.NAME));
                    else
                        instance.addField(jf);
                }
                return instance;
            }
        }

        private int countSteps(String str) {
            int count = 0;
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == '<')
                    count++;
                else if (str.charAt(i) == '>')
                    count--;
                if (count == 0)
                    return i + 1;
            }
            return str.length();
        }


    }
}

class MethodDefInfo {
    // Method ID
    public final String NAME;
    public final String PACKAGE;
    public final String RET_TYPE;
    public final String CLASS_NAME;
    public final String[] PARAM_TYPES;
    // DEF Info
    private boolean stateDEF;
    private boolean[] argDEFs;
    private List<String> fieldDEFs;
    public MethodDefInfo(String ret, String name, String pkg, String cls, String[] args) {
        NAME = name;
        RET_TYPE = ret;
        CLASS_NAME = cls;
        PACKAGE = pkg == null ? "" : pkg;
        PARAM_TYPES = args == null ? new String[0] : args;
        //
        fieldDEFs = new ArrayList<>();
        stateDEF = guessByTypeOrName();
        argDEFs = new boolean[PARAM_TYPES.length];  // all initialized to 'false'
    }
    private boolean guessByTypeOrName() {
        // First check if this method is a constructor ...
        if (RET_TYPE == null)
            return true;
        // If not, then try to guess by method-name ...
        String[] prefixes = { "set", "put", "add", "insert", "push", "append" };
        for (String pre: prefixes)
            if (NAME.toLowerCase().startsWith(pre))
                return true;
        return false;
    }
    public boolean doesStateDEF() {
        return stateDEF;
    }
    public void setStateDEF(boolean stateDef) {
        stateDEF = stateDef;
    }
    public boolean[] argDEFs() {
        return argDEFs;
    }
    public void setArgDEF(int argIndex, boolean def) {
        argDEFs[argIndex] = def;
    }
    public void setAllArgDEFs(boolean[] argDefs) {
        argDEFs = argDefs;
    }
    public String[] fieldDEFs() {
        return fieldDEFs.toArray(new String[fieldDEFs.size()]);
    }
    public void addFieldDEF(String fieldName) {
        if (!fieldDEFs.contains(fieldName)) {
            fieldDEFs.add(fieldName);
            stateDEF = true;
        }
    }
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MethodDefInfo))
            return false;
        MethodDefInfo info = (MethodDefInfo) obj;
        return this.NAME.equals(info.NAME) && this.CLASS_NAME.equals(info.CLASS_NAME)
                && this.PACKAGE.equals(info.PACKAGE) && this.RET_TYPE.equals(info.RET_TYPE)
                && Arrays.equals(this.PARAM_TYPES, info.PARAM_TYPES);
    }
    @Override
    public String toString() {
        String retType = RET_TYPE == null ? "null" : RET_TYPE;
        String args = PARAM_TYPES == null ? "null" : Arrays.toString(PARAM_TYPES);
        StringBuilder str = new StringBuilder();
        str.append("{ TYPE : \"").append(retType).append("\", ");
        str.append("NAME : \"").append(NAME).append("\", ");
        str.append("ARGS : ").append(args).append(", ");
        str.append("CLASS : \"").append(CLASS_NAME).append("\", ");
        str.append("PACKAGE : \"").append(PACKAGE).append("\" }");
        return str.toString();
    }
}





