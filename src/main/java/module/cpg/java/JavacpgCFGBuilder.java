package module.cpg.java;
/**
 * A Control Flow Graph builder with AST nodes for java program
 * Java Parser generated via ANTLRv4
 */

import ghaffarian.graphs.*;
import ghaffarian.nanologger.Logger;
import module.cpg.graphs.cpg.*;
import module.cpg.java.parser.JavaBaseVisitor;
import module.cpg.java.parser.JavaLexer;
import module.cpg.java.parser.JavaParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.misc.Interval;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class JavacpgCFGBuilder{
    /**
    * Control Flow Graph build and return
    * @return
    */
    public static cpgControlFlowGraph build(String javaFile) throws IOException{
        return build(new File(javaFile));

        }

    public static cpgControlFlowGraph build(File javaFile) throws IOException{
        if(!javaFile.getName().endsWith(".java")){
            throw new IOException("Not a Java File");
        }
        InputStream inFile = new FileInputStream(javaFile);
        ANTLRInputStream input = new ANTLRInputStream(inFile);
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        ParseTree tree = parser.compilationUnit();
        return build(javaFile.getName(), tree, null, null);
    }

    public static cpgControlFlowGraph build(String javaFileName, ParseTree tree, String propKey,Map<ParserRuleContext, Object> ctxProps){

        cpgControlFlowGraph cfg = new cpgControlFlowGraph(javaFileName);
        cpgControlFlowGraphVisitor visitor = new cpgControlFlowGraphVisitor(cfg, propKey, ctxProps);
        return visitor.build(tree);

    }

    private static class cpgControlFlowGraphVisitor extends JavaBaseVisitor<String>{
        private cpgControlFlowGraph cfg;
        private String propKey;
        private String typeModifier;
        public String memberModifier;
        private Deque<cpgCFGNode> rootStack;
        private Deque<cpgCFGNode> preNodes;
        private Map<String, String> vars, fields, methods;
        private int varsCounter, fieldsCounter, methodsCounter;
        private Deque<cpgCFGEdge.Type> preEdges;
        private Deque<Block> loopBlocks;
        private List<Block> labeledBlocks;
        private Deque<Block> tryBlocks;
        private Queue<cpgCFGNode> casesQueue;
        private boolean dontPop;
        private Map<ParserRuleContext, Object> contexutalProperties;
        private Deque<String> classNames;
        private Deque<String> packageNames;

        //--------------AST-Components----------------//
        private String ASTpropKey;

        private String ASTtypeModifier;

        private String ASTmemberModifer;

        private Deque<cpgCFGNode> ASTparentStack;

        private Map<String, String> ASTvars;

        private Map<String, String> ASTfields;

        private Map<String, String> ASTmethods;

        //---------------CDG-Components---------------//
        private Deque<cpgCFGNode> ctrlDeps;
        private Deque<cpgCFGNode> negsDeps;
        private Deque<Integer> jmpCounts;
        private Deque<cpgCFGNode> jmpDeps;
        private Deque<cpgCFGNode> cdgPreNode;
        private Deque<cpgCFGEdge.Type> cdgPreEdge;
        private boolean buildRegion;
        private boolean follows;
        private int lastFollowDepth;
        private int regionCounter;
        private int jmpCounter;



        public cpgControlFlowGraphVisitor(cpgControlFlowGraph cfg, String propKey, Map<ParserRuleContext, Object> ctxProps) {
            rootStack = new ArrayDeque<>();
            preNodes = new ArrayDeque<>();
            preEdges = new ArrayDeque<>();
            loopBlocks = new ArrayDeque<>();
            labeledBlocks = new ArrayList<>();
            tryBlocks = new ArrayDeque<>();
            casesQueue = new ArrayDeque<>();
            classNames = new ArrayDeque<>();
            packageNames = new ArrayDeque<>();
            dontPop = false;
            this.cfg = cfg;
            vars = new LinkedHashMap<>();
            fields = new LinkedHashMap<>();
            methods = new LinkedHashMap<>();
            this.propKey = propKey;
            contexutalProperties = ctxProps;
            varsCounter = 0; fieldsCounter = 0; methodsCounter = 0;
            //--------AST-CompInit---------------//
            this.ASTparentStack = new ArrayDeque<>();
            this.ASTpropKey = propKey;
            this.ASTvars = new LinkedHashMap<>();
            this.ASTfields = new LinkedHashMap<>();
            this.ASTmethods = new LinkedHashMap<>();
            //--------CDG-CompInit---------------//
            ctrlDeps = new ArrayDeque<>();
            negsDeps = new ArrayDeque<>();
            jmpDeps = new ArrayDeque<>();
            jmpCounts = new ArrayDeque<>();
            cdgPreNode = new ArrayDeque<>();
            cdgPreEdge = new ArrayDeque<>();
            buildRegion = false;
            follows = true;
            lastFollowDepth = 0;
            regionCounter = 1;
            jmpCounter = 0;
        }

        private void init(){
            preNodes.clear();
            preEdges.clear();
            loopBlocks.clear();
            labeledBlocks.clear();
            tryBlocks.clear();
            dontPop = false;
        }

        //--------method-to-init----------//
        private void cdginit(){
            ctrlDeps.clear();
            negsDeps.clear();
            jmpDeps.clear();
            jmpCounts.clear();
            buildRegion = false;
            follows = true;
            lastFollowDepth = 0;
            regionCounter = 1;
            jmpCounter = 0;
            cdgPreEdge.clear();
            cdgPreNode.clear();
        }



        private void addContexualProperty(cpgCFGNode node, ParserRuleContext ctx){

            if(propKey != null && contexutalProperties != null){
            Object prop = contexutalProperties.get(ctx);
            if(prop != null){
            node.setProperty(propKey, prop);
            }
            }

        }

        public cpgControlFlowGraph build(ParseTree tree){
            JavaParser.CompilationUnitContext rootCntx = (JavaParser.CompilationUnitContext) tree;
            cfg.root.setCode(cfg.fileName.substring(0, cfg.fileName.indexOf('.')));
            cfg.root.setType("root");
            addContexualProperty(cfg.root, rootCntx);
            rootStack.push(cfg.root);
            ASTparentStack.push(cfg.root);
            if(rootCntx.packageDeclaration() != null){
                visit(rootCntx.packageDeclaration());
            }

            if(rootCntx.importDeclaration() != null && rootCntx.importDeclaration().size() > 0){
                cpgCFGNode imports = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                imports.setLineOfCode(rootCntx.importDeclaration(0).getStart().getLine());
                imports.setType("imports");
                Logger.debug("Adding imports");
                cfg.addVertex(imports);
                cfg.addEdge(new Edge(this.ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), imports));
                ASTparentStack.push(imports);
                for(JavaParser.ImportDeclarationContext importCntx : rootCntx.importDeclaration()){
                    visit(importCntx);
                }
                ASTparentStack.pop();
                }
                //
                if(rootCntx.typeDeclaration() != null){
                    for(JavaParser.TypeDeclarationContext typDeclCntx: rootCntx.typeDeclaration()){
                        visit(typDeclCntx);
                    }
                }
                ASTparentStack.pop();
                rootStack.pop();
                vars.clear();
                fields.clear();
                return cfg;

            }

        @Override
        public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx){
            cpgCFGNode node = new cpgCFGNode(cpgCFGNode.NodeTyp.PACKAGE);
            node.setCode(ctx.qualifiedName().getText());
            node.setLineOfCode(ctx.getStart().getLine());
            node.setType("packageDeclaration");
            addContexualProperty(node, ctx);
            packageNames.push(ctx.qualifiedName().getText());
            Logger.debug("Adding the package node");
            cfg.setPackage(ctx.qualifiedName().getText());
            cfg.addVertex(node);
            cfg.addEdge(new Edge<>(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),node));
            return "";
        }

        @Override
        public String visitImportDeclaration(JavaParser.ImportDeclarationContext ctx){
            String qualName = ctx.qualifiedName().getText();
            int last = ctx.getChildCount() - 1;
            if(ctx.getChild(last-1).getText().equals("*") && ctx.getChild(last-2).getText().equals(".")){
                qualName += ".*";
            }
            cpgCFGNode node = new cpgCFGNode(cpgCFGNode.NodeTyp.IMPORT);
            node.setCode(qualName);
            node.setLineOfCode(ctx.getStart().getLine());
            node.setType("importDeclaration");
            addContexualProperty(node, ctx);
            Logger.debug("Adding import" + qualName);
            cfg.addVertex(node);
            cfg.addEdge(new Edge<>(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),node));
            return "";
        }

        @Override
        public String visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx){
            typeModifier = "";
            for(JavaParser.ClassOrInterfaceModifierContext modifierCntx: ctx.classOrInterfaceModifier()){
                typeModifier += modifierCntx.getText() + " ";
            }
            typeModifier = typeModifier.trim();
            visitChildren(ctx);
            return "";
        }

        @Override
        public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx){
            cpgCFGNode classNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CLASS);
            classNode.setCode(typeModifier + " " +ctx.getChild(0).getText()+" "+ctx.getChild(1).getText());
            classNode.setLineOfCode(ctx.getStart().getLine());
            classNode.setType("classDeclaration");
            addContexualProperty(classNode, ctx);
            Logger.debug("Adding class node");
            cfg.addVertex(classNode);
            cfg.addEdge(new Edge<>(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),classNode));

            //modifier node
            cpgCFGNode modifierNode = new cpgCFGNode(cpgCFGNode.NodeTyp.MODIFIER);
            modifierNode.setCode(typeModifier);
            modifierNode.setLineOfCode(ctx.getStart().getLine());
            modifierNode.setType("modifier");
            addContexualProperty(modifierNode, ctx);
            Logger.debug("Adding class modifier node");
            cfg.addVertex(modifierNode);
            cfg.addEdge(new Edge<>(classNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),modifierNode));

            //Name node
            cpgCFGNode nameNode = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
            String className = ctx.Identifier().getText();
            if(ctx.typeParameters() != null){
                className += ctx.typeParameters().getText();
            }
            nameNode.setCode(className);
            nameNode.setLineOfCode(ctx.getStart().getLine());
            nameNode.setType("identifier");
            addContexualProperty(nameNode, ctx);
            Logger.debug("Adding class name:" + className);
            cfg.addVertex(nameNode);
            cfg.addEdge(new Edge<>(classNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),nameNode));

            if(ctx.typeType() != null){
                cpgCFGNode extendsNode = new cpgCFGNode(cpgCFGNode.NodeTyp.EXTENDS);
                extendsNode.setCode(ctx.typeType().getText());
                extendsNode.setLineOfCode(ctx.typeType().getStart().getLine());
                extendsNode.setType("extends");
                addContexualProperty(extendsNode, ctx.typeType());
                Logger.debug("Adding the extends node" + ctx.typeType().getText());
                cfg.addVertex(extendsNode);
                cfg.addEdge(new Edge<>(classNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),extendsNode));
            }
            //
            if(ctx.typeList() != null){
                cpgCFGNode implNode = new cpgCFGNode(cpgCFGNode.NodeTyp.IMPLEMENTS);
                implNode.setLineOfCode(ctx.typeList().getStart().getLine());
                implNode.setType("implements");
                addContexualProperty(implNode, ctx.typeList());
                Logger.debug("Adding implements node");
                cfg.addVertex(implNode);
                cfg.addEdge(new Edge<>(classNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),implNode));
                for(JavaParser.TypeTypeContext type: ctx.typeList().typeType()){
                    cpgCFGNode node = new cpgCFGNode(cpgCFGNode.NodeTyp.INTERFACE);
                    node.setCode(type.getText());
                    node.setLineOfCode(type.getStart().getLine());
                    node.setType("interface");
                    addContexualProperty(node, type);
                    Logger.debug("Adding interface" + type.getText());
                    cfg.addVertex(node);
                    cfg.addEdge(new Edge<>(implNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),node));
                    }
                }
            rootStack.push(classNode);
            ASTparentStack.push(classNode);
            classNames.push(ctx.Identifier().getText());
            visit(ctx.classBody());
            ASTparentStack.pop();
            rootStack.pop();
            classNames.pop();
            return "";
        }

        @Override
        public String visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx){
            if(ctx.block() != null){
                init();
                //
                cpgCFGNode block = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                if(ctx.getChildCount() == 2 && ctx.getChild(0).getText().equals("static")){
                    block.setLineOfCode(ctx.getStart().getLine());
                    block.setCode("static");
                    block.setType("Static");
                }else{
                    block.setLineOfCode(0);
                    block.setCode("block");
                    block.setType("classBlock");
                }
                addContexualProperty(block, ctx);
                cfg.addVertex(block);
                cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),block));
                //
                block.setProperty("name", "static-block");
                block.setProperty("class", classNames.peek());
                cfg.addMethodEntry(block);

                preNodes.push(block);
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                ASTparentStack.push(block);
                pushCtrlDep(block);
                visitChildren(ctx.block());
                ASTparentStack.pop();
            }else if(ctx.memberDeclaration() != null){
                memberModifier = "";
                for(JavaParser.ModifierContext modCtx: ctx.modifier()){
                    memberModifier += modCtx.getText() + " ";
                }
                memberModifier = memberModifier.trim();
                if (ctx.memberDeclaration().fieldDeclaration() != null) {
                    visit(ctx.memberDeclaration().fieldDeclaration());
                } else if (ctx.memberDeclaration().constructorDeclaration() != null) {
                    visit(ctx.memberDeclaration().constructorDeclaration());
                } else if (ctx.memberDeclaration().methodDeclaration() != null) {
                    visit(ctx.memberDeclaration().methodDeclaration());
                } else if (ctx.memberDeclaration().classDeclaration() != null) {
                    visitChildren(ctx.memberDeclaration());
                }

            }
            return "";
        }

        @Override
        public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx){
            init();
            cpgCFGNode entry = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            entry.setCode(ctx.Identifier().getText() + ' '+getOriginalCodeText(ctx.formalParameters()));
            entry.setLineOfCode(ctx.getStart().getLine());
            entry.setType("constructorDeclaration");
            addContexualProperty(entry, ctx);
            cfg.addVertex(entry);
            cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), entry));
            pushCtrlDep(entry);
            //
            entry.setProperty("name", ctx.Identifier().getText());
            entry.setProperty("class", classNames.peek());
            cfg.addMethodEntry(entry);
            preNodes.push(entry);
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);

            //Modifier Node
            cpgCFGNode modNode = new cpgCFGNode(cpgCFGNode.NodeTyp.MODIFIER);
            modNode.setLineOfCode(ctx.getStart().getLine());
            modNode.setCode(memberModifier);
            modNode.setType("modifier");
            cfg.addVertex(modNode);
            cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),modNode));

            //
            if(ctx.formalParameters().formalParameterList() != null){

                cpgCFGNode paramNode = new cpgCFGNode(cpgCFGNode.NodeTyp.PARAMS);
                paramNode.setCode(getOriginalCodeText(ctx.formalParameters()));
                paramNode.setLineOfCode(ctx.formalParameters().getStart().getLine());
                paramNode.setType("formalParameterList");
                cfg.addVertex(paramNode);
                cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),paramNode));
                rootStack.push(paramNode);
                for(JavaParser.FormalParameterContext paramCtx: ctx.formalParameters().formalParameterList().formalParameter()){
                    cpgCFGNode varNode = new cpgCFGNode(cpgCFGNode.NodeTyp.VARIABLE);
                    varNode.setLineOfCode(paramCtx.getStart().getLine());
                    varNode.setCode(getOriginalCodeText(paramCtx));
                    varNode.setType("formalParameter");
                    cfg.addVertex(varNode);
                    cfg.addEdge(new Edge<>(paramNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varNode));

                    //Variable Type Node
                    cpgCFGNode type = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                    type.setCode(paramCtx.typeType().getText());
                    type.setLineOfCode(paramCtx.typeType().getStart().getLine());
                    type.setType("type");
                    cfg.addVertex(type);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),type));

                    //
                    ++varsCounter;
                    cpgCFGNode name = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                    String normalized = "$VARL_" + varsCounter;
                    vars.put(paramCtx.variableDeclaratorId().Identifier().getText(), normalized);
                    name.setCode(paramCtx.variableDeclaratorId().getText());
                    name.setNormalizedCode(normalized);
                    name.setLineOfCode(paramCtx.variableDeclaratorId().getStart().getLine());
                    name.setType("identifier");
                    cfg.addVertex(name);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),name));

                }
                if(ctx.formalParameters().formalParameterList().lastFormalParameter() != null){
                    cpgCFGNode varNode = new cpgCFGNode(cpgCFGNode.NodeTyp.VARIABLE);
                    varNode.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    varNode.setCode(getOriginalCodeText(ctx.formalParameters().formalParameterList().lastFormalParameter()));
                    varNode.setType("lastFormalParameter");
                    cfg.addVertex(varNode);
                    cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varNode));

                    //Type node
                    cpgCFGNode type = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                    type.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getText());
                    type.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    type.setType("type");
                    cfg.addVertex(type);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),type));

                    //Variable Name node
                    ++varsCounter;
                    cpgCFGNode name = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                    String normalized = "$VARL_" + varsCounter;
                    vars.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().Identifier().getText(), normalized);
                    name.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText());
                    name.setNormalizedCode(normalized);
                    name.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getStart().getLine());
                    name.setType("identifier");
                    cfg.addVertex(name);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),name));
                }
                rootStack.pop();
            }
            cpgCFGNode bodyBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
            bodyBlock.setLineOfCode(ctx.constructorBody().block().getStart().getLine());
            bodyBlock.setType("constructorBlock");
            cfg.addVertex(bodyBlock);
            cfg.addEdge(new Edge(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, ""), bodyBlock));
            ASTparentStack.push(bodyBlock);
            visitChildren(ctx.constructorBody().block());
            ASTparentStack.pop();
            return "";
        }

        @Override
        public String visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx){

            String test = ctx.getText();
            cpgCFGNode fieldNode = new cpgCFGNode(cpgCFGNode.NodeTyp.FIELD);
            fieldNode.setLineOfCode(ctx.getStart().getLine());
            fieldNode.setCode(memberModifier + " " + getOriginalCodeText(ctx));
            fieldNode.setType("fieldDeclaration");
            addContexualProperty(fieldNode, ctx);
            Logger.debug("Adding field node");
            cfg.addVertex(fieldNode);
            cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),fieldNode));
            rootStack.push(fieldNode);
            for(JavaParser.VariableDeclaratorContext varCtx: ctx.variableDeclarators().variableDeclarator()){
                cpgCFGNode modifierNode = new cpgCFGNode(cpgCFGNode.NodeTyp.MODIFIER);
                modifierNode.setCode(memberModifier);
                modifierNode.setLineOfCode(ctx.getStart().getLine());
                modifierNode.setType("modifier");
                cfg.addVertex(modifierNode);
                cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),modifierNode));

                //
                cpgCFGNode type = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                type.setCode(ctx.typeType().getText());
                type.setLineOfCode(ctx.typeType().getStart().getLine());
                type.setType("type");
                cfg.addVertex(type);
                cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),type));

                //
                ++fieldsCounter;
                cpgCFGNode name = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                String normalized = "$VARF_" + fieldsCounter;
                fields.put(varCtx.variableDeclaratorId().Identifier().getText(), normalized);
                name.setCode(varCtx.variableDeclaratorId().getText());
                name.setNormalizedCode(normalized);
                name.setLineOfCode(varCtx.variableDeclaratorId().getStart().getLine());
                name.setType("identifier");
                cfg.addVertex(name);
                cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),name));

                //
                if(varCtx.variableInitializer() != null){
                    cpgCFGNode initNode = new cpgCFGNode(cpgCFGNode.NodeTyp.INIT_VALUE);
                    initNode.setCode("= " + getOriginalCodeText(varCtx.variableInitializer()));
                    initNode.setNormalizedCode("= " + visit(varCtx.variableInitializer()));
                    initNode.setLineOfCode(varCtx.variableInitializer().getStart().getLine());
                    initNode.setType("variableInitializer");
                    cfg.addVertex(initNode);
                    cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),initNode));
                    }
                }
            rootStack.pop();
            return "";
        }

        @Override
        public String visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx){
            //METHOD-DECLARATION
            init();

            cpgCFGNode entry = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            entry.setLineOfCode(ctx.getStart().getLine());
            entry.setType("methodDeclaration");
            String retType = "void";
            if(ctx.typeType() != null){
                retType = ctx.typeType().getText();
            }
            String args = getOriginalCodeText(ctx.formalParameters());
            entry.setCode(retType + " " + ctx.Identifier() + args);
            addContexualProperty(entry, ctx);
            cfg.addVertex(entry);
            cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),entry));
            //
            entry.setProperty("name", ctx.Identifier().getText());
            entry.setProperty("class", classNames.peek());
            entry.setProperty("packageName", packageNames.peek());
            cfg.addMethodEntry(entry);
            //
            cpgCFGNode modifierNode = new cpgCFGNode(cpgCFGNode.NodeTyp.MODIFIER);
            modifierNode.setCode(memberModifier);
            modifierNode.setLineOfCode(ctx.getStart().getLine());
            modifierNode.setType("modifier");
            Logger.debug("Adding method modifier");
            cfg.addVertex(modifierNode);
            cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),modifierNode));

            //
            cpgCFGNode retNode = new cpgCFGNode(cpgCFGNode.NodeTyp.RETURN);
            retNode.setCode(ctx.getChild(0).getText());
            retNode.setLineOfCode(ctx.getStart().getLine());
            retNode.setType("return");
            Logger.debug("Adding the method type");
            cfg.addVertex(retNode);
            cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),retNode));

            //
            ++methodsCounter;
            cpgCFGNode nameNode = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
            String methodName = ctx.Identifier().getText();
            String normalized = "$METHOD" + methodsCounter;
            methods.put(methodName, normalized);
            nameNode.setCode(methodName);
            nameNode.setNormalizedCode(normalized);
            nameNode.setLineOfCode(ctx.getStart().getLine());
            nameNode.setType("identifier");
            Logger.debug("Adding method name");
            cfg.addVertex(nameNode);
            cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),nameNode));

            //----------------------THROWS-------------------------------------------------//
            if(ctx.qualifiedNameList() != null){
                cpgCFGNode throwsNode = new cpgCFGNode(cpgCFGNode.NodeTyp.THROWS);
                throwsNode.setLineOfCode(ctx.getStart().getLine());
                throwsNode.setType("qualifiedNameList");
                Logger.debug("Adding the throws node");
                cfg.addVertex(throwsNode);
                cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), throwsNode));

                //
                for(JavaParser.QualifiedNameContext qCtx: ctx.qualifiedNameList().qualifiedName()){
                    cpgCFGNode qualName = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                    qualName.setLineOfCode(qCtx.getStart().getLine());
                    qualName.setCode(qCtx.getText());
                    qualName.setType("qualifiedName");
                    Logger.debug("Adding qualified name list");
                    cfg.addVertex(qualName);
                    cfg.addEdge(new Edge<>(throwsNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), qualName));
                }
            }
            //
            if(ctx.formalParameters().formalParameterList() != null){
                cpgCFGNode paramNode = new cpgCFGNode(cpgCFGNode.NodeTyp.PARAMS);
                paramNode.setLineOfCode(ctx.formalParameters().getStart().getLine());
                paramNode.setCode(getOriginalCodeText(ctx.formalParameters()));
                paramNode.setType("formalParameterList");
                Logger.debug("Adding method parameter node");
                cfg.addVertex(paramNode);
                cfg.addEdge(new Edge<>(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),paramNode));
                rootStack.push(paramNode);
                //
                for(JavaParser.FormalParameterContext paramCtx: ctx.formalParameters().formalParameterList().formalParameter()){
                    cpgCFGNode varNode = new cpgCFGNode(cpgCFGNode.NodeTyp.VARIABLE);
                    varNode.setCode(getOriginalCodeText(paramCtx));
                    varNode.setLineOfCode(ctx.formalParameters().getStart().getLine());
                    varNode.setType("formalParameter");
                    Logger.debug("Adding method parameter node");
                    cfg.addVertex(varNode);
                    cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varNode));

                    //Type
                    cpgCFGNode type = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                    type.setCode(paramCtx.typeType().getText());
                    type.setLineOfCode(paramCtx.typeType().getStart().getLine());
                    type.setType("type");
                    cfg.addVertex(type);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),type));

                    //
                    ++varsCounter;
                    cpgCFGNode name = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                    normalized = "$VARL_" + varsCounter;
                    vars.put(paramCtx.variableDeclaratorId().Identifier().getText(), normalized);
                    name.setCode(paramCtx.variableDeclaratorId().getText());
                    name.setNormalizedCode(normalized);
                    name.setLineOfCode(paramCtx.variableDeclaratorId().getStart().getLine());
                    name.setType("identifier");
                    cfg.addVertex(name);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),name));
                }
                if(ctx.formalParameters().formalParameterList().lastFormalParameter() != null){
                    cpgCFGNode varNode = new cpgCFGNode(cpgCFGNode.NodeTyp.VARIABLE);
                    varNode.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    varNode.setCode(getOriginalCodeText(ctx.formalParameters().formalParameterList().lastFormalParameter()));
                    varNode.setType("lastFormalParameter");
                    cfg.addVertex(varNode);
                    cfg.addEdge(new Edge<>(rootStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varNode));

                    //TYPE node addition
                    cpgCFGNode type = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                    type.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getText());
                    type.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    type.setType("type");
                    cfg.addVertex(type);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),type));

                    //
                    ++varsCounter;
                    cpgCFGNode name = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                    normalized = "$VARL_" + varsCounter;
                    vars.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().Identifier().getText(), normalized);
                    name.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText());
                    name.setNormalizedCode(normalized);
                    name.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    name.setType("identifier");
                    cfg.addVertex(name);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),name));

                }
                rootStack.pop();
            }
            if (ctx.methodBody() != null){
                cpgCFGNode methodBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
                methodBlock.setLineOfCode(ctx.methodBody().getStart().getLine());
                Logger.debug("Adding method block");
                methodBlock.setType("MethodBlock");
                cfg.addVertex(methodBlock);
                cfg.addEdge(new Edge(entry, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), methodBlock));
                ASTparentStack.push(methodBlock);
                pushCtrlDep(entry);
                preNodes.push(entry);
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                visitChildren(ctx.methodBody());
                ASTparentStack.pop();
            }
            return "";
        }

        @Override
        public String visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx){

            cpgCFGNode declr = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            declr.setLineOfCode(ctx.getStart().getLine());
            declr.setCode(getOriginalCodeText(ctx) + ";");
            declr.setType("localVariableDeclaration");
            addContexualProperty(declr, ctx);
            addNodeAndPreEdge(declr);
            //--------------CDG-----------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(declr);
            }else {
                cdgpopAddPreEdgeTo(declr);
            }
            cfg.addEdge(new Edge(this.ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), declr));
            //
            for(JavaParser.VariableDeclaratorContext varContx: ctx.variableDeclarators().variableDeclarator()){
                cpgCFGNode varNode = new cpgCFGNode(cpgCFGNode.NodeTyp.VARIABLE);
                varNode.setLineOfCode(varContx.getStart().getLine());
                varNode.setCode(ctx.typeType().getText() + " " + varContx.variableDeclaratorId().getText() + " ");
                varNode.setType("variableDeclaratorId");
                addContexualProperty(varNode, varContx);
                cfg.addVertex(varNode);
                cfg.addEdge(new Edge<>(declr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varNode));

                //type node
                cpgCFGNode typeNode = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                typeNode.setCode(ctx.typeType().getText());
                typeNode.setLineOfCode(ctx.typeType().getStart().getLine());
                typeNode.setType("type");
                addContexualProperty(typeNode, ctx.typeType());
                cfg.addVertex(typeNode);
                cfg.addEdge(new Edge<>(declr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),typeNode));

                //
                ++varsCounter;
                cpgCFGNode nameNode = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                String normalized = "$VARL_" + varsCounter;
                vars.put(varContx.variableDeclaratorId().Identifier().getText(), normalized);
                nameNode.setCode(varContx.variableDeclaratorId().getText());
                nameNode.setNormalizedCode(normalized);
                nameNode.setLineOfCode(varContx.variableDeclaratorId().getStart().getLine());
                nameNode.setType("identifier");
                addContexualProperty(nameNode, varContx.variableDeclaratorId());
                cfg.addVertex(nameNode);
                cfg.addEdge(new Edge<>(declr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),nameNode));

                //
                if(varContx.variableInitializer() != null){
                    cpgCFGNode initNode = new cpgCFGNode(cpgCFGNode.NodeTyp.INIT_VALUE);
                    initNode.setCode("= " + getOriginalCodeText(varContx.variableInitializer()) + ";");
                    initNode.setNormalizedCode("= " + visit(varContx.variableInitializer()));
                    initNode.setLineOfCode(varContx.variableInitializer().getStart().getLine());
                    initNode.setType("variableInitializer");
                    addContexualProperty(initNode, varContx.variableInitializer());
                    cfg.addVertex(initNode);
                    cfg.addEdge(new Edge<>(varNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),initNode));
                }
                }
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(declr);
            return null;
        }

        //---------------------STATEMENTS-----------------------//

        @Override
        public String visitStatementExpression(JavaParser.StatementExpressionContext ctx){

            cpgCFGNode expr = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            expr.setLineOfCode(ctx.getStart().getLine());
            expr.setCode(getOriginalCodeText(ctx));
            expr.setType("statementExpression");
            //
            Logger.debug(expr.getLineOfCode() + ": " + expr.getCode());
            //
            addContexualProperty(expr, ctx);
            addNodeAndPreEdge(expr);
            //-------------CDG-------------------//
            if(cdgPreEdge.isEmpty() && cdgPreNode.isEmpty()){
                addNodeEdge(expr);
            }else {
                addcdgNodeAndPreEdge(expr);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), expr));
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(expr);
            return null;
        }

        @Override
        public String visitIfStatement(JavaParser.IfStatementContext ctx){
            // 'if'
            cpgCFGNode ifNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            ifNode.setLineOfCode(ctx.getStart().getLine());
            ifNode.setCode("if" + getOriginalCodeText(ctx.parExpression()));
            ifNode.setType("ifStatement");
            addContexualProperty(ifNode, ctx);
            addNodeAndPreEdge(ifNode);
            //-------------------CDG------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(ifNode);
            }else{
                cdgpopAddPreEdgeTo(ifNode);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), ifNode));
            //
            cpgCFGNode condn = new cpgCFGNode(cpgCFGNode.NodeTyp.CONDITION);
            condn.setCode("("+getOriginalCodeText(ctx.parExpression().expression())+")");
            condn.setNormalizedCode(visit(ctx.parExpression().expression()));
            condn.setLineOfCode(ctx.parExpression().getStart().getLine());
            condn.setType("parExpression");
            addContexualProperty(condn, ctx.parExpression().expression());
            cfg.addVertex(condn);
            cfg.addEdge(new Edge<>(ifNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),condn));

            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO_TRUE);
            preNodes.push(ifNode);
            //------------------CDG------------------//
            cdgPreEdge.push(cpgCFGEdge.Type.CDG_TRUE);
            cdgPreNode.push(ifNode);
            pushCtrlDep(ifNode);
            negsDeps.push(ifNode);
            //true
            cpgCFGNode thenNode = new cpgCFGNode(cpgCFGNode.NodeTyp.THEN);
            thenNode.setLineOfCode(ctx.statement(0).getStart().getLine());
            thenNode.setType("then");
            cfg.addVertex(thenNode);
            cfg.addEdge(new Edge(ifNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), thenNode));
            ASTparentStack.push(thenNode);
            visit(ctx.statement(0));
            ASTparentStack.pop();
            negsDeps.pop();
            popCtrlDep(ifNode);
            //----------------CDG---------------------//
            cpgCFGNode endif = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endif.setLineOfCode(0);
            endif.setCode("end if");
            endif.setType("endIf");
            addNodeAndPreEdge(endif);
            //
            //---if without the else condn-----//
            if(ctx.statement().size() == 1){
                cfg.addEdge(new Edge<>(ifNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_FALSE, "False"),endif));
            }else{
                cpgCFGNode elseNode = new cpgCFGNode(cpgCFGNode.NodeTyp.ELSE);
                elseNode.setLineOfCode(ctx.statement(1).getStart().getLine());
                elseNode.setType("else");
                cfg.addVertex(elseNode);
                cfg.addEdge(new Edge(ifNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), elseNode));
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO_FALSE);
                preNodes.push(ifNode);
                //------------------CDG--------------//
                cdgPreEdge.push(cpgCFGEdge.Type.CDG_FALSE);
                cdgPreNode.push(ifNode);
                negsDeps.push(ifNode);
                ASTparentStack.push(elseNode);
                visit(ctx.statement(1));
                ASTparentStack.pop();
                negsDeps.pop();
                popAddPreEdgeTo(endif);
            }
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endif);
            return null;
        }

        @Override
        public String visitForStatement(JavaParser.ForStatementContext ctx){
            if(ctx.forControl().enhancedForControl() != null){
                cpgCFGNode forExpr = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
                forExpr.setCode("for(" + getOriginalCodeText(ctx.forControl()) + ")");
                forExpr.setType("forStatement");
                addContexualProperty(forExpr, ctx.forControl().enhancedForControl());
                addNodeAndPreEdge(forExpr);
                cfg.addEdge(new Edge(this.ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), forExpr));
                //---------------------CDG----------------------------//
                if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                    addNodeEdge(forExpr);
                }else{
                    cdgpopAddPreEdgeTo(forExpr);
                }
                cpgCFGNode loopRegion = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                loopRegion.setLineOfCode(0);
                loopRegion.setType("forLoop");
                cfg.addVertex(loopRegion);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.CDG_TRUE, "CDG_TRUE"), loopRegion));
                //---------------------------------------------------------//
                cpgCFGNode varType = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                varType.setCode(ctx.forControl().enhancedForControl().typeType().getText());
                varType.setLineOfCode(ctx.forControl().enhancedForControl().typeType().getStart().getLine());
                varType.setType("type");
                addContexualProperty(varType, ctx.forControl().enhancedForControl().typeType());
                cfg.addVertex(varType);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varType));
                //
                ++varsCounter;
                cpgCFGNode varID = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                String normalized = "$VARL_" + varsCounter;
                vars.put(ctx.forControl().enhancedForControl().variableDeclaratorId().Identifier().getText(), normalized);
                varID.setCode(ctx.forControl().enhancedForControl().variableDeclaratorId().getText());
                varID.setNormalizedCode(normalized);
                varID.setLineOfCode(ctx.forControl().enhancedForControl().variableDeclaratorId().getStart().getLine());
                varID.setType("identifier");
                addContexualProperty(varID, ctx.forControl().enhancedForControl().variableDeclaratorId());
                cfg.addVertex(varID);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),varID));
                //
                cpgCFGNode expr = new cpgCFGNode(cpgCFGNode.NodeTyp.IN);
                expr.setCode(getOriginalCodeText(ctx.forControl().enhancedForControl().expression()));
                expr.setNormalizedCode(visit(ctx.forControl().enhancedForControl().expression()));
                expr.setLineOfCode(ctx.forControl().enhancedForControl().expression().getStart().getLine());
                expr.setType("expression");
                addContexualProperty(expr, ctx.forControl().enhancedForControl().expression());
                cfg.addVertex(expr);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),expr));
                //
                cpgCFGNode forEnd = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                forEnd.setLineOfCode(0);
                forEnd.setCode("end for");
                forEnd.setType("endFor");
                cfg.addVertex(forEnd);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_FALSE, "FALSE"),forEnd));
                //
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO_TRUE);
                preNodes.push(forExpr);
                //
                loopBlocks.push(new Block(forExpr, forEnd));
                //---------------AST---------------------//
                cpgCFGNode foreachBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
                foreachBlock.setLineOfCode(ctx.statement().getStart().getLine());
                foreachBlock.setType("forBlock");
                cfg.addVertex(foreachBlock);
                cfg.addEdge(new Edge(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), foreachBlock));
                //---------------CDG---------------------//
                cdgPreEdge.push(cpgCFGEdge.Type.CDG_EPSILON);
                cdgPreNode.push(loopRegion);
                ASTparentStack.push(foreachBlock);
                visit(ctx.statement());
                loopBlocks.pop();
                ASTparentStack.pop();
                popAddPreEdgeTo(forExpr);
                //
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(forEnd);
            }else{
                cpgCFGNode forNode = new cpgCFGNode(cpgCFGNode.NodeTyp.FOR);
                forNode.setLineOfCode(ctx.getStart().getLine());
                forNode.setCode("for("+getOriginalCodeText(ctx.forControl())+");");
                forNode.setType("for");
                cfg.addVertex(forNode);
                cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), forNode));
                cpgCFGNode forInit = null;
                if(ctx.forControl().forInit() != null){
                    forInit = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                    forInit.setLineOfCode(ctx.forControl().forInit().getStart().getLine());
                    forInit.setCode(getOriginalCodeText(ctx.forControl().forInit()) + ";");
                    forInit.setType("forControl");
                    addContexualProperty(forInit, ctx.forControl().forInit());
                    addNodeAndPreEdge(forInit);
                    cfg.addEdge(new Edge(forNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), forInit));
                    //
                    if(ctx.forControl().forInit().localVariableDeclaration() != null){
                        preEdges.push(cpgCFGEdge.Type.AST);
                        preNodes.push(forInit);
                        ASTparentStack.push(forInit);
                        visit(ctx.forControl().forInit().localVariableDeclaration());
                        ASTparentStack.pop();
                    }

                }
                // for-expression
                //for-expression
                cpgCFGNode forExpr = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                if(ctx.forControl().expression() == null){
                    forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
                    forExpr.setCode("for ( ; )");
                    forExpr.setType("forExpression");
                }else{
                    forExpr.setLineOfCode(ctx.forControl().expression().getStart().getLine());
                    forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl().expression()) + ")");
                    forExpr.setType("forExpression");
                }
                addContexualProperty(forExpr, ctx.forControl().expression());
                cfg.addVertex(forExpr);
                cfg.addEdge(new Edge(forNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), forExpr));
                addNodeEdge(forExpr);
                if(forInit != null){
//                    addNodeAndPreEdge(forExpr);
                    cfg.addEdge(new Edge<>(forInit, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, "CFG"), forExpr));
                }else{
                    popAddPreEdgeTo(forExpr);
                }
                //Condition

                //------------for-update-------------//
                cpgCFGNode loopRegion = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                loopRegion.setLineOfCode(0);
                loopRegion.setType("loop");
                cfg.addVertex(loopRegion);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.CDG_TRUE, "CDG_TRUE"), loopRegion));
                pushLoopBlockDep(loopRegion);
                //-----------------------------//
                cpgCFGNode forUpdate = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                if(ctx.forControl().forUpdate() == null){
                    forUpdate.setCode(" ; ");
                    forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
                    forUpdate.setType("forUpdate");
                }else{
                    forUpdate.setCode(getOriginalCodeText(ctx.forControl().forUpdate()));
                    forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
                    forUpdate.setType("forUpdate");
                }
                addContexualProperty(forUpdate, ctx.forControl().forUpdate());
                cfg.addVertex(forUpdate);
                cfg.addEdge(new Edge<>(ctrlDeps.peek(), new cpgCFGEdge(cpgCFGEdge.Type.CDG_EPSILON, "CDG_EPSILON"), forUpdate));
                cfg.addEdge(new Edge(forNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), forUpdate));
                //
                cpgCFGNode forEnd = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                forEnd.setLineOfCode(0);
                forEnd.setCode("end for");
                forEnd.setType("endFor");
                cfg.addVertex(forEnd);
                cfg.addEdge(new Edge<>(forExpr, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_FALSE, "False"), forEnd));
                //
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO_TRUE);
                preNodes.push(forExpr);
                loopBlocks.push(new Block(forUpdate, forEnd));
                cpgCFGNode forBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
                forBlock.setLineOfCode(ctx.statement().getStart().getLine());
                forBlock.setType("forBlock");
                cfg.addVertex(forBlock);
                cfg.addEdge(new Edge(forNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), forBlock));
                ASTparentStack.push(forBlock);
                visit(ctx.statement());
                ASTparentStack.pop();
                popLoopBlockDep(loopRegion);
                loopBlocks.pop();
                popAddPreEdgeTo(forUpdate);
                cfg.addEdge(new Edge<>(forUpdate, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), forExpr));
                //
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(forEnd);
                }
                return null;
        }

        @Override
        public String visitWhileStatement(JavaParser.WhileStatementContext ctx){
            //-----while statement------//
            cpgCFGNode whileNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            whileNode.setLineOfCode(ctx.getStart().getLine());
            whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
            whileNode.setType("whileStatement");
            addContexualProperty(whileNode, ctx);
            addNodeAndPreEdge(whileNode);
            //--------CDG-------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(whileNode);
            }else{
                cdgpopAddPreEdgeTo(whileNode);
            }
            cfg.addEdge(new Edge(this.ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), whileNode));
            //---condition---------//
            cpgCFGNode cond = new cpgCFGNode(cpgCFGNode.NodeTyp.CONDITION);
            cond.setCode("("+getOriginalCodeText(ctx.parExpression().expression())+")");
            cond.setNormalizedCode(visit(ctx.parExpression().expression()));
            cond.setLineOfCode(ctx.parExpression().expression().getStart().getLine());
            cond.setType("parExpression");
            addContexualProperty(cond, ctx.parExpression().expression());
            cfg.addVertex(cond);
            cfg.addEdge(new Edge<>(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), cond));
            cpgCFGNode whileBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
            whileBlock.setLineOfCode(ctx.getStart().getLine());
            whileBlock.setType("whileBlock");
            Logger.debug("Adding AST block");
            cfg.addVertex(whileBlock);
            cfg.addEdge(new Edge(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), whileBlock));

            //-----endwhile--------//
            cpgCFGNode endwhile = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endwhile.setLineOfCode(0);
            endwhile.setCode("end while");
            endwhile.setType("endWhile");
            cfg.addVertex(endwhile);
            cfg.addEdge(new Edge<>(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, "CFG"), endwhile));
            //------------CDG----------//
            cpgCFGNode loopRegion = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            loopRegion.setLineOfCode(0);
            loopRegion.setType("loop");
            cfg.addVertex(loopRegion);
            cfg.addEdge(new Edge<>(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.CDG_TRUE, "CDG_TRUE"),loopRegion));
            //------true-------------//
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO_TRUE);
            preNodes.push(whileNode);
            loopBlocks.push(new Block(whileNode, endwhile));
            pushLoopBlockDep(loopRegion);
            ASTparentStack.push(whileBlock);
            visit(ctx.statement());
            ASTparentStack.pop();
            popLoopBlockDep(loopRegion);
            loopBlocks.pop();
            popAddPreEdgeTo(whileNode);
            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endwhile);
            return null;
        }

        @Override
        public String visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx){
            //---------DO WHILE------------//
            cpgCFGNode doNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            doNode.setLineOfCode(ctx.getStart().getLine());
            doNode.setCode("do");
            doNode.setType("doWhileStatement");
            addNodeAndPreEdge(doNode);
            //-------------CDG--------------//
            if(cdgPreEdge.isEmpty() && cdgPreNode.isEmpty()){
                addNodeEdge(doNode);
            }else {
                cdgpopAddPreEdgeTo(doNode);
            }
            //
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), doNode));
            cpgCFGNode dowhileBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
            dowhileBlock.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding method block");
            dowhileBlock.setType("doWhileBlock");
            cfg.addVertex(dowhileBlock);
            cfg.addEdge(new Edge(doNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), dowhileBlock));
            ASTparentStack.push(dowhileBlock);
            //
            cpgCFGNode whileNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            whileNode.setLineOfCode(ctx.parExpression().getStart().getLine());
            whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
            whileNode.setType("whileExpression");
            addContexualProperty(whileNode, ctx);
            cfg.addVertex(whileNode);
            //
            cpgCFGNode condn = new cpgCFGNode(cpgCFGNode.NodeTyp.CONDITION);
            condn.setCode("("+getOriginalCodeText(ctx.parExpression().expression())+")");
            condn.setNormalizedCode(visit(ctx.parExpression().expression()));
            condn.setLineOfCode(ctx.parExpression().expression().getStart().getLine());
            condn.setType("parExpression");
            addContexualProperty(condn, ctx.parExpression().expression());
            cfg.addVertex(condn);
            cfg.addEdge(new Edge(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), condn));
            cfg.addEdge(new Edge(doNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), condn));
            //
            cpgCFGNode doWhileEnd = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            doWhileEnd.setLineOfCode(0);
            doWhileEnd.setCode("end do while");
            doWhileEnd.setType("endDoWhile");
            cfg.addVertex(doWhileEnd);
            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(doNode);
            loopBlocks.push(new Block(whileNode, doWhileEnd));
            pushLoopBlockDep(doNode);
            visit(ctx.statement());
            addNodeEdge(whileNode);
            popLoopBlockDep(doNode);
            loopBlocks.pop();
            popAddPreEdgeTo(whileNode);
            cfg.addEdge(new Edge<>(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_TRUE, "True"), doNode));
            cfg.addEdge(new Edge<>(whileNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_FALSE, "False"), doWhileEnd));
            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(doWhileEnd);
            return null;

        }

        @Override
        public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx){
            //--------------SWITCH STATEMENT--------------------//
            cpgCFGNode switchNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            switchNode.setLineOfCode(ctx.getStart().getLine());
            switchNode.setCode("switch " + getOriginalCodeText(ctx.parExpression()));
            switchNode.setType("switchStatement");
            addContexualProperty(switchNode, ctx);
            addNodeAndPreEdge(switchNode);
            //----------------CDG-------------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(switchNode);
            }else {
                cdgpopAddPreEdgeTo(switchNode);
            }
            cfg.addEdge(new Edge(this.ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), switchNode));
            //----------------NAME------------------------//
            cpgCFGNode varName = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
            varName.setCode(getOriginalCodeText(ctx.parExpression().expression()));
            varName.setNormalizedCode(visit(ctx.parExpression().expression()));
            varName.setLineOfCode(ctx.parExpression().expression().getStart().getLine());
            varName.setType("parExpression");
            addContexualProperty(varName, ctx.parExpression().expression());
            cfg.addVertex(varName);
            cfg.addEdge(new Edge<>(switchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), varName));

            //----------------END-SWITCH------------------//
            cpgCFGNode endSwitch = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endSwitch.setLineOfCode(0);
            endSwitch.setCode("end switch");
            endSwitch.setType("endSwitch");
            cfg.addVertex(endSwitch);
            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(switchNode);
            loopBlocks.push(new Block(switchNode, endSwitch));
            //
            cpgCFGNode preCase = null;
            pushLoopBlockDep(switchNode);
            for(JavaParser.SwitchBlockStatementGroupContext grp: ctx.switchBlockStatementGroup()){
                cpgCFGNode blockNode = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
                blockNode.setLineOfCode(grp.blockStatement(0).getStart().getLine());
                blockNode.setType("switchBlockStatementGroup");
                cfg.addVertex(blockNode);
                ASTparentStack.push(blockNode);
                preCase = visitSwitchLabels(ctx.switchLabel(), preCase, switchNode);
                for(JavaParser.BlockStatementContext blkStatCont: grp.blockStatement()){
                    visit(blkStatCont);
                }
                ASTparentStack.pop();
                popCtrlDep(preCase);
            }
            preCase = visitSwitchLabels(ctx.switchLabel(), preCase, switchNode);
            loopBlocks.pop();
            popAddPreEdgeTo(endSwitch);
            if(preCase != null){
                cfg.addEdge(new Edge<>(preCase, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_FALSE, "False"), endSwitch));
            }
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endSwitch);
            popLoopBlockDep(switchNode);
            return null;

        }

        private cpgCFGNode visitSwitchLabels(List<JavaParser.SwitchLabelContext> list, cpgCFGNode preCase, cpgCFGNode switchNode){
            //------------------SWITCH-LABELS---------------------//
            cpgCFGNode caseStatement = preCase;
            for(JavaParser.SwitchLabelContext ctx: list){
                caseStatement = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                caseStatement.setLineOfCode(ctx.getStart().getLine());
                caseStatement.setCode(getOriginalCodeText(ctx));
                caseStatement.setType("switchStatement");
                cfg.addVertex(caseStatement);
                if(dontPop){
                    dontPop = false;
                }else{
                    cfg.addEdge(new Edge<>(preNodes.pop(), new cpgCFGEdge(preEdges.pop(), ""), caseStatement));
                }
                if(preCase != null){
                    cfg.addEdge(new Edge<>(preCase, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_FALSE, "False"), caseStatement));
                    cfg.addEdge(new Edge<>(preCase, new cpgCFGEdge(cpgCFGEdge.Type.CDG_FALSE,"CDG_FALSE"), caseStatement));
                }
                if(ctx.getStart().getText().equals("default")){
                    preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                    preNodes.push(caseStatement);
                    pushCtrlDep(preCase);
                    cfg.addEdge(new Edge(switchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), caseStatement));
                    cfg.addEdge(new Edge(caseStatement, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), ASTparentStack.peek()));
                    caseStatement = null;
                }else{
                    dontPop = true;
                    casesQueue.add(caseStatement);
                    pushCtrlDep(preCase);
                    preCase = caseStatement;
                }

            }
            if (caseStatement != null){
                cfg.addEdge(new Edge(switchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), caseStatement));
                cfg.addEdge(new Edge(caseStatement, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), ASTparentStack.peek()));
            }
            return caseStatement;
        }

        @Override
        public String visitLabelStatement(JavaParser.LabelStatementContext ctx){
            //------------label statement----------//
            cpgCFGNode labelNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            labelNode.setLineOfCode(ctx.getStart().getLine());
            labelNode.setCode(ctx.Identifier() + ": ");
            labelNode.setType("labelStatement");
            addContexualProperty(labelNode, ctx);
            addNodeAndPreEdge(labelNode);
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), labelNode));
            //-------------CDG--------------------//
            if(cdgPreEdge.isEmpty() && cdgPreNode.isEmpty()){
                addNodeEdge(labelNode);
            }else {
                cdgpopAddPreEdgeTo(labelNode);
            }
            //-----------label name---------------//
            cpgCFGNode labelName = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
            labelName.setCode(ctx.Identifier().getText());
            labelName.setNormalizedCode("$LABEL");
            labelName.setLineOfCode(ctx.getStart().getLine());
            labelName.setType("identifier");
            addContexualProperty(labelNode, ctx);
            cfg.addVertex(labelName);
            cfg.addEdge(new Edge<>(labelName, new cpgCFGEdge(cpgCFGEdge.Type.AST,"AST"),labelName));
            //------------end-label---------------//
            cpgCFGNode endLabelNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endLabelNode.setLineOfCode(0);
            endLabelNode.setCode("end label");
            endLabelNode.setType("endLabel");
            cfg.addVertex(endLabelNode);
            //-----------------------------------//
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(labelNode);
            labeledBlocks.add(new Block(labelNode, endLabelNode, ctx.Identifier().getText()));
            ASTparentStack.push(labelNode);
            visit(ctx.statement());
            ASTparentStack.pop();
            popAddPreEdgeTo(endLabelNode);
            popCtrlDep(labelNode);
            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endLabelNode);
            return null;
        }

        @Override
        public String visitReturnStatement(JavaParser.ReturnStatementContext ctx){
            //--------------RETURN--------------//
            cpgCFGNode ret = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            ret.setLineOfCode(ctx.getStart().getLine());
            ret.setCode(getOriginalCodeText(ctx));
            ret.setType("returnStatement");
            addContexualProperty(ret, ctx);
            addNodeAndPreEdge(ret);
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(ret);
            }else {
                cdgpopAddPreEdgeTo(ret);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), ret));
            if (!negsDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth) {
                jmpDeps.push(negsDeps.peek());
                jmpDeps.peek().setProperty("isExit", Boolean.TRUE);
                jmpDeps.peek().setProperty("isJump", Boolean.FALSE);
                lastFollowDepth = ctrlDeps.size();
                buildRegion = true;
            }
            dontPop = true;
            return null;
        }

        @Override
        public String visitBreakStatement(JavaParser.BreakStatementContext ctx){
            //---------------break------------//
            cpgCFGNode breakNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            breakNode.setLineOfCode(ctx.getStart().getLine());
            breakNode.setCode(getOriginalCodeText(ctx));
            breakNode.setType("breakStatement");
            addContexualProperty(breakNode, ctx);
            addNodeAndPreEdge(breakNode);
            //----------------CDG--------------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(breakNode);
            }
            else {
                cdgpopAddPreEdgeTo(breakNode);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), breakNode));

            if(!negsDeps.isEmpty() && negsDeps.peek().getCode().startsWith("default"))
                return null;

            if(!negsDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth){
                jmpDeps.push(negsDeps.peek());
                jmpDeps.peek().setProperty("isExit", Boolean.FALSE);
                jmpDeps.peek().setProperty("isJump", Boolean.TRUE);
                lastFollowDepth = ctrlDeps.size();
                buildRegion = true;
            }
            //-------------------------------------------------//
            if(ctx.Identifier() != null){
            for(Block block: labeledBlocks){
            if(block.label.equals(ctx.Identifier().getText())){
                cfg.addEdge(new Edge<>(breakNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""),block.end));
                break;
            }
            }
            }else{
                Block block = loopBlocks.peek();
                cfg.addEdge(new Edge<>(breakNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), block.end));
            }
            dontPop = true;
            return null;
        }

        @Override
        public String visitContinueStatement(JavaParser.ContinueStatementContext ctx){
            //----------------CONTINUE_STATEMENT-----------------//
            cpgCFGNode continueNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            continueNode.setLineOfCode(ctx.getStart().getLine());
            continueNode.setCode(getOriginalCodeText(ctx));
            continueNode.setType("continueStatement");
            addContexualProperty(continueNode, ctx);
            addNodeAndPreEdge(continueNode);
            //-----------------CDG--------------------//
            if(cdgPreEdge.isEmpty() && cdgPreNode.isEmpty()){
                addNodeEdge(continueNode);
            }
            else {
                cdgpopAddPreEdgeTo(continueNode);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), continueNode));
            if(!negsDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth){
                jmpDeps.push(negsDeps.peek());
                jmpDeps.peek().setProperty("isExit", Boolean.FALSE);
                jmpDeps.peek().setProperty("isJump", Boolean.TRUE);
                lastFollowDepth = ctrlDeps.size();
                buildRegion = true;
            }
            //--------------------------------------------//
            if(ctx.Identifier() != null){
                for(Block block: labeledBlocks){
                if(block.label.equals(ctx.Identifier().getText())){
                    cfg.addEdge(new Edge<>(continueNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), block.start));
                    break;
                }
                }
            }else{
                Block block = loopBlocks.peek();
                cfg.addEdge(new Edge<>(continueNode, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), block.start));
            }
            dontPop = true;
            return null;
        }

        @Override
        public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx){
            //---------------SYNCHRONIZED BLOCK----------------------//
            cpgCFGNode synBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            synBlock.setLineOfCode(ctx.getStart().getLine());
            synBlock.setCode("synchronized" + getOriginalCodeText(ctx.parExpression()));
            synBlock.setType("synchBlockStatement");
            addContexualProperty(synBlock,ctx);
            addNodeAndPreEdge(synBlock);
            //--------------------CDG--------------------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(synBlock);
            }
            else{
                cdgpopAddPreEdgeTo(synBlock);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), synBlock));
            cpgCFGNode block = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
            block.setLineOfCode(ctx.block().getStart().getLine());
            block.setType("synchBlock");
            cfg.addVertex(block);
            cfg.addEdge(new Edge(synBlock, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), block));
            ASTparentStack.push(block);
            //-----------------------EDGE----------------------------//
            preNodes.push(synBlock);
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            pushCtrlDep(synBlock);
            visit(ctx.block());
            ASTparentStack.pop();
            popCtrlDep(synBlock);
            //---------------END-BLOCK-NODE-------------------------//
            cpgCFGNode endSyncBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endSyncBlock.setLineOfCode(0);
            endSyncBlock.setCode("end-synchronized");
            endSyncBlock.setType("endSynchronized");
            addNodeAndPreEdge(endSyncBlock);
            //
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endSyncBlock);
            return null;
        }

        @Override
        public String visitTryStatement(JavaParser.TryStatementContext ctx){
            //-------------------TRY-BLOCK------------------------//
            cpgCFGNode tryNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            tryNode.setLineOfCode(ctx.getStart().getLine());
            tryNode.setCode("try");
            tryNode.setType("tryStatement");
            addContexualProperty(tryNode, ctx);
            addNodeAndPreEdge(tryNode);
            //-----------------------CDG---------------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(tryNode);
            }
            else{
                cdgpopAddPreEdgeTo(tryNode);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), tryNode));
            pushCtrlDep(tryNode);
            negsDeps.push(tryNode);

            cpgCFGNode tryBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
            tryBlock.setLineOfCode(ctx.block().getStart().getLine());
            tryBlock.setType("tryBlock");
            cfg.addVertex(tryBlock);
            cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), tryBlock));
            ASTparentStack.push(tryBlock);
            //-------------------END-TRY-----------------------//
            cpgCFGNode endTry = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endTry.setLineOfCode(0);
            endTry.setCode("end try");
            endTry.setType("endTry");
            cfg.addVertex(endTry);
            //-----------------PUSHING-NODE-----------------------//
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(tryNode);
            tryBlocks.push(new Block(tryNode, endTry));
            visit(ctx.block());
            ASTparentStack.pop();
            popAddPreEdgeTo(endTry);
            popCtrlDep(tryNode);
            //-----------------FINALLY-BLOCK-----------------------//
            cpgCFGNode finallyNode = null;
            cpgCFGNode endFinally = null;
            if(ctx.finallyBlock() != null){
                finallyNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                finallyNode.setLineOfCode(ctx.finallyBlock().getStart().getLine());
                finallyNode.setCode("finally");
                finallyNode.setType("finallyBlock");
                addContexualProperty(finallyNode, ctx.finallyBlock());
                cfg.addVertex(finallyNode);
                cfg.addEdge(new Edge<>(endTry, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), finallyNode));
                cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), finallyNode));
                //
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(finallyNode);
                addNodeEdge(finallyNode);
                pushCtrlDep(finallyNode);
                ASTparentStack.push(finallyNode);
                visit(ctx.finallyBlock().block());
                ASTparentStack.pop();
                //
                popCtrlDep(finallyNode);
                endFinally = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                endFinally.setCode("end finally");
                endFinally.setType("endFinally");
                endFinally.setLineOfCode(0);
                addNodeAndPreEdge(endFinally);
            }
            //---------------------CATCH-BLOCK--------------------------//
            pushCtrlDep(tryNode);
            if(ctx.catchClause() != null && ctx.catchClause().size() > 0){
            cpgCFGNode catchNode;
            cpgCFGNode endCatch = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endCatch.setLineOfCode(0);
            endCatch.setCode("end catch");
            endCatch.setType("endCatch");
            cfg.addVertex(endCatch);
            for(JavaParser.CatchClauseContext ccContext: ctx.catchClause()){
            catchNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            catchNode.setLineOfCode(ccContext.getStart().getLine());
            catchNode.setCode("catch (" + ccContext.catchType().getText() + " " + ccContext.Identifier().getText() + ")");
            catchNode.setType("Catch");
            catchNode.setType("catchClause");
            addContexualProperty(catchNode, ccContext);
            cfg.addVertex(catchNode);
            cfg.addEdge(new Edge<>(endTry, new cpgCFGEdge(cpgCFGEdge.Type.THROWS, ""), catchNode));
            cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.CDG_THROWS, "CDG_THROWS"), catchNode));
            cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchNode));
            //----------------------TYPE------------------------------------//
            cpgCFGNode catchType = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
            catchType.setCode(ccContext.catchType().getText());
            catchType.setLineOfCode(ccContext.catchType().getStart().getLine());
            catchType.setType("catchType");
            addContexualProperty(catchType, ccContext.catchType());
            cfg.addVertex(catchType);
            cfg.addEdge(new Edge<>(catchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchType));
            //----------------------NAME-------------------------------------//
            ++varsCounter;
            cpgCFGNode catchName = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
            String normalized = "$VARL_" + varsCounter;
            vars.put(ccContext.Identifier().getText(), normalized);
            catchName.setCode(ccContext.Identifier().getText());
            catchName.setNormalizedCode(normalized);
            catchName.setLineOfCode(ccContext.getStart().getLine());
            catchName.setType("identifier");
            addContexualProperty(catchName, ccContext);
            cfg.addVertex(catchName);
            cfg.addEdge(new Edge<>(catchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchName));
            //--------------------PUSH-NODE_EDGE-----------------------------------//
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(catchNode);
            pushCtrlDep(catchNode);
            ASTparentStack.push(catchNode);
            visit(ccContext.block());
            ASTparentStack.pop();
            popCtrlDep(catchNode);
            popAddPreEdgeTo(endCatch);
            }
            negsDeps.pop();
            popCtrlDep(tryNode);
            if(finallyNode != null){
            cfg.addEdge(new Edge<>(endCatch, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), finallyNode));
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endFinally);
            }else{
            cfg.addEdge(new Edge<>(endCatch, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), endTry));
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endTry);
            }

            }else {
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(endFinally);
            }
            return null;
        }

        @Override
        public String visitTryWithResourceStatement(JavaParser.TryWithResourceStatementContext ctx){
            cpgCFGNode tryNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            tryNode.setLineOfCode(ctx.getStart().getLine());
            tryNode.setCode("try");
            tryNode.setType("tryWithResourceStatement");
            addContexualProperty(tryNode, ctx);
            addNodeAndPreEdge(tryNode);
            //----------CDG----------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(tryNode);
            }
            else{
                cdgpopAddPreEdgeTo(tryNode);
            }
            cfg.addEdge(new Edge(ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), tryNode));
            pushCtrlDep(tryNode);
            negsDeps.push(tryNode);
            //--------------------------------------//
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(tryNode);
            cpgCFGNode resNode = new cpgCFGNode(cpgCFGNode.NodeTyp.RESOURCES);
            resNode.setLineOfCode(ctx.resourceSpecification().getStart().getLine());
            resNode.setType("resourceSpecification");
            cfg.addVertex(resNode);
            cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), resNode));
            //--------------------RESOURCES--------------------------------//
            for(JavaParser.ResourceContext resrcCtxt: ctx.resourceSpecification().resources().resource()){
                cpgCFGNode resource = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                resource.setLineOfCode(resrcCtxt.getStart().getLine());
                resource.setCode(getOriginalCodeText(resrcCtxt));
                resource.setType("resource");
                addContexualProperty(resource, resrcCtxt);
                addNodeAndPreEdge(resource);
                //-------------------CDG------------------------------------//
                addNodeEdge(resource);
                cfg.addEdge(new Edge(resNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), resource));
                //-----------------PUSH_Node-Edge---------------------------//
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(resource);
                //------------------TYPE------------------------------------//
                cpgCFGNode rsrcType = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                rsrcType.setCode(resrcCtxt.classOrInterfaceType().getText());
                rsrcType.setLineOfCode(resrcCtxt.classOrInterfaceType().getStart().getLine());
                rsrcType.setType("resourceType");
                addContexualProperty(rsrcType, resrcCtxt.classOrInterfaceType());
                cfg.addVertex(rsrcType);
                cfg.addEdge(new Edge<>(resource, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), rsrcType));
                //-------------------NAME----------------------------------//
                ++varsCounter;
                cpgCFGNode rsrcName = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                String normalized = "$VARL_" + varsCounter;
                vars.put(resrcCtxt.variableDeclaratorId().Identifier().getText(), normalized);
                rsrcName.setCode(resrcCtxt.variableDeclaratorId().getText());
                rsrcName.setNormalizedCode(normalized);
                rsrcName.setLineOfCode(resrcCtxt.variableDeclaratorId().getStart().getLine());
                rsrcName.setType("identifier");
                addContexualProperty(rsrcName, resrcCtxt.variableDeclaratorId());
                cfg.addVertex(rsrcName);
                cfg.addEdge(new Edge<>(resource, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"),rsrcName));
                //---------------------INIT_VALUE-----------------------------//
                cpgCFGNode rsrcInit = new cpgCFGNode(cpgCFGNode.NodeTyp.INIT_VALUE);
                rsrcInit.setCode("= " + getOriginalCodeText(resrcCtxt.expression()));
                rsrcInit.setNormalizedCode("= " + visit(resrcCtxt.expression()));
                rsrcInit.setLineOfCode(resrcCtxt.expression().getStart().getLine());
                rsrcInit.setType("expression");
                addContexualProperty(rsrcInit, resrcCtxt.expression());
                cfg.addVertex(rsrcInit);
                cfg.addEdge(new Edge<>(resource, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), rsrcInit));
            }
            //-----------------------END-TRY---------------------------------------//
            cpgCFGNode endTry = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            endTry.setLineOfCode(0);
            endTry.setCode("end try");
            endTry.setType("endTry");
            cfg.addVertex(endTry);
            //-----------------------TRY-BLOCK-------------------------------------//
            preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
            preNodes.push(tryNode);
            tryBlocks.push(new Block(tryNode, endTry));
            cpgCFGNode tryBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
            tryBlock.setLineOfCode(ctx.block().getStart().getLine());
            tryBlock.setType("tryBlock");
            cfg.addVertex(tryBlock);
            cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), tryBlock));
            ASTparentStack.push(tryBlock);
            visit(ctx.block());
            ASTparentStack.pop();
            popAddPreEdgeTo(endTry);

            //-------------------------FINALLY--------------------------------------//
            cpgCFGNode finallyNode = null;
            cpgCFGNode endFinally = null;
            if(ctx.finallyBlock() != null){
                finallyNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                finallyNode.setLineOfCode(ctx.finallyBlock().getStart().getLine());
                finallyNode.setCode("finally");
                finallyNode.setType("finallyBlock");
                addContexualProperty(finallyNode, ctx.finallyBlock());
                cfg.addVertex(finallyNode);
                cfg.addEdge(new Edge<>(endTry, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), finallyNode));
                cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), finallyNode));

                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(finallyNode);
                addNodeEdge(finallyNode);
                pushCtrlDep(finallyNode);
                ASTparentStack.push(finallyNode);
                visit(ctx.finallyBlock().block());
                ASTparentStack.pop();
                popCtrlDep(finallyNode);

                endFinally = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                endFinally.setLineOfCode(0);
                endFinally.setCode("end finally");
                endFinally.setType("endFinally");
                addNodeAndPreEdge(endFinally);
            }

            //---------------------CATCH----------------------------------------------//
            if(ctx.catchClause() != null && ctx.catchClause().size() > 0){

                cpgCFGNode endCatch = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                endCatch.setLineOfCode(0);
                endCatch.setCode("end catch");
                endCatch.setType("endCatch");
                cfg.addVertex(endCatch);

                for(JavaParser.CatchClauseContext CCContext: ctx.catchClause()){
                    cpgCFGNode catchNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                    catchNode.setLineOfCode(CCContext.getStart().getLine());
                    catchNode.setCode("catch (" + CCContext.catchType().getText() + " " + CCContext.Identifier().getText()+ ")");
                    catchNode.setType("catchClause");
                    addContexualProperty(catchNode, CCContext);
                    cfg.addVertex(catchNode);
                    cfg.addEdge(new Edge(tryNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchNode));
                    cfg.addEdge(new Edge<>(endTry, new cpgCFGEdge(cpgCFGEdge.Type.THROWS, ""), catchNode));
                    cfg.addEdge(new Edge<>(endTry, new cpgCFGEdge(cpgCFGEdge.Type.CDG_THROWS, "CDG_THROWS"), catchNode));

                    //
                    pushCtrlDep(catchNode);
                    preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                    preNodes.push(catchNode);
                    //---------------------TYPE---------------------------------------//
                    cpgCFGNode catchType = new cpgCFGNode(cpgCFGNode.NodeTyp.TYPE);
                    catchType.setCode(CCContext.catchType().getText());
                    catchType.setLineOfCode(CCContext.catchType().getStart().getLine());
                    catchType.setType("catchType");
                    addContexualProperty(catchType, CCContext.catchType());
                    cfg.addVertex(catchType);
                    cfg.addEdge(new Edge<>(catchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchType));
                    //----------------------NAME---------------------------------------//
                    ++varsCounter;
                    cpgCFGNode catchName = new cpgCFGNode(cpgCFGNode.NodeTyp.NAME);
                    String normalized = "$VARL_" + varsCounter;
                    vars.put(CCContext.Identifier().getText(), normalized);
                    catchName.setCode(CCContext.Identifier().getText());
                    catchName.setNormalizedCode(normalized);
                    catchName.setLineOfCode(CCContext.catchType().getStart().getLine());
                    catchName.setType("identifier");
                    addContexualProperty(catchName, CCContext);
                    cfg.addVertex(catchName);
                    cfg.addEdge(new Edge<>(catchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchType));
                    //---------------------PUSH-NODE_EDGE-------------------------------//
                    preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                    preNodes.push(catchNode);
                    cpgCFGNode catchBlock = new cpgCFGNode(cpgCFGNode.NodeTyp.BLOCK);
                    catchBlock.setLineOfCode(CCContext.block().getStart().getLine());
                    catchBlock.setType("catchBlock");
                    cfg.addVertex(catchBlock);
                    cfg.addEdge(new Edge(catchNode, new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), catchBlock));
                    ASTparentStack.push(catchBlock);
                    visit(CCContext.block());
                    ASTparentStack.pop();
                    popCtrlDep(catchNode);
                    popAddPreEdgeTo(endCatch);
                    }
                    if(finallyNode != null){
                        cfg.addEdge(new Edge<>(endCatch, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), finallyNode));
                        preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                        preNodes.push(endFinally);
                    }else{
                        cfg.addEdge(new Edge<>(endCatch, new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO, ""), endTry));
                        preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                        preNodes.push(endTry);
                    }
            }
            else if(finallyNode != null){
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(endFinally);
            }else{
                preEdges.push(cpgCFGEdge.Type.FLOWS_TO);
                preNodes.push(endTry);
            }
            negsDeps.pop();
            popCtrlDep(tryNode);
            return null;
        }

        @Override
        public String visitThrowStatement(JavaParser.ThrowStatementContext ctx){
            cpgCFGNode throwNode = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
            throwNode.setLineOfCode(ctx.getStart().getLine());
            throwNode.setCode("throw" + getOriginalCodeText(ctx.expression()));
            throwNode.setType("throwStatement");
            addContexualProperty(throwNode, ctx);
            addNodeAndPreEdge(throwNode);
            //---------------CDG------------------------//
            if(cdgPreNode.isEmpty() && cdgPreEdge.isEmpty()){
                addNodeEdge(throwNode);
            }
            else{
                cdgpopAddPreEdgeTo(throwNode);
            }
            cfg.addEdge(new Edge(this.ASTparentStack.peek(), new cpgCFGEdge(cpgCFGEdge.Type.AST, "AST"), throwNode));
            if(!negsDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth){
                jmpDeps.push(negsDeps.peek());
                jmpDeps.peek().setProperty("isExit", Boolean.TRUE);
                jmpDeps.peek().setProperty("isJump", Boolean.FALSE);
                lastFollowDepth = ctrlDeps.size();
                buildRegion = true;
            }
            //-----------------------------------------------//
            if(!tryBlocks.isEmpty()){
                Block tryBlock = tryBlocks.peek();
                cfg.addEdge(new Edge<>(throwNode, new cpgCFGEdge(cpgCFGEdge.Type.THROWS, ""), tryBlock.end));
            }else{
                //
            }
            dontPop = true;
            return null;
        }

        //-------------------------GET-CODE_PROPERTY_GRAPH--------------------------//
        public cpgControlFlowGraph getCpg(){
            return cfg;
        }

        //--------------------------ADD_NODE_AND_EDGE--------------------------------//
        private void addNodeAndPreEdge(cpgCFGNode node){
            cfg.addVertex(node);
            popAddPreEdgeTo(node);
        }

        //------------------------ADD_NEW_EDGE---------------------------------------//
        private void popAddPreEdgeTo(cpgCFGNode node){
            if(dontPop){
                dontPop = false;
            }
            else {
                Logger.debug("\nPRE-NODES = " + preNodes.size());
                Logger.debug("PRE-EDGES = " + preEdges.size() + '\n');
                cfg.addEdge(new Edge<>(preNodes.pop(), new cpgCFGEdge(preEdges.pop(), ""), node));
            }
            for(int i = casesQueue.size(); i > 0; --i){
                cfg.addEdge(new Edge<>(casesQueue.remove(), new cpgCFGEdge(cpgCFGEdge.Type.FLOWS_TO_TRUE, "True"), node));
            }
        }

        //------------------------FETCH_THE_CODE_TEXT--------------------------------//
        private String getOriginalCodeText(ParserRuleContext ctx){
            int start = ctx.start.getStartIndex();
            int stop = ctx.stop.getStopIndex();
            Interval interval = new Interval(start, stop);
            return ctx.start.getInputStream().getText(interval);
        }

        //-------------------------CODE_BLOCK----------------------------------------//
        private class Block{
            public final String label;
            public final cpgCFGNode start, end;

            Block(cpgCFGNode start, cpgCFGNode end, String label){
                this.start = start;
                this.end = end;
                this.label = label;
            }
            Block(cpgCFGNode start, cpgCFGNode end){
                this(start, end, "");
            }
        }

        //-------------------------ADD-NODE--------------------------------------//
        private void addNodeEdge(cpgCFGNode node){
            checkBuildFollowRegion();
            cfg.addEdge(new Edge<>(ctrlDeps.peek(), new cpgCFGEdge(cpgCFGEdge.Type.CDG_EPSILON, "CDG_EPSILON"), node));
        }

        //---------------------FOLLOW-REGION CHECK-------------------------------//
        private void checkBuildFollowRegion() {
            Logger.debug("FOLLOWS = " + follows);
            Logger.debug("BUILD-REGION = " + buildRegion);
            if (buildRegion && follows) {
                cpgCFGNode followRegion = new cpgCFGNode(cpgCFGNode.NodeTyp.CFG_NODE);
                followRegion.setLineOfCode(0);
                followRegion.setCode("FOLLOW-" + regionCounter++);
                followRegion.setType("CDG-Follow-Region");
                cfg.addVertex(followRegion);
                // check to see if there are any exit-jumps in the current chain
                followRegion.setProperty("isJump", Boolean.TRUE);
                for (cpgCFGNode dep: jmpDeps)
                    if ((Boolean) dep.getProperty("isExit")) {
                        followRegion.setProperty("isJump", Boolean.FALSE);
                        followRegion.setProperty("isExit", Boolean.TRUE);
                    }
                if ((Boolean) followRegion.getProperty("isJump"))
                    ++jmpCounter;
                // connect the follow-region
                if (Boolean.TRUE.equals(jmpDeps.peek().getProperty("isTry"))) {
                    cpgCFGNode jmpDep = jmpDeps.pop();
                    if (!cfg.containsVertex(jmpDep))
                        cfg.addVertex(jmpDep);
                    cfg.addEdge(new Edge<>(jmpDep, new cpgCFGEdge(cpgCFGEdge.Type.CDG_NOT_THROWS, ""), followRegion));
                } else {
                    cpgCFGNode jmpDep = jmpDeps.pop();
                    if (!cfg.containsVertex(jmpDep))
                        cfg.addVertex(jmpDep);
                    cfg.addEdge(new Edge<>(jmpDep, new cpgCFGEdge(cpgCFGEdge.Type.CDG_FALSE, ""), followRegion));
                }
                // if the jump-chain is not empty, remove all non-exit jumps
                if (!jmpDeps.isEmpty()) {
                    for (Iterator<cpgCFGNode> itr = jmpDeps.iterator(); itr.hasNext(); ) {
                        cpgCFGNode dep = itr.next();
                        if (Boolean.FALSE.equals(dep.getProperty("isExit")))
                            itr.remove();
                    }
                }
                lastFollowDepth = 0;
                pushCtrlDep(followRegion);
            }
        }

        //----------------------------CD-STACK------------------------------------------------//
        private void pushCtrlDep(cpgCFGNode dep){
            ctrlDeps.push(dep);
            buildRegion = false;
        }

        //---------------------------POP-LAST-STACK-------------------------------------------//
        private void popCtrlDep(cpgCFGNode dep){
            ctrlDeps.remove(dep);
            buildRegion = !jmpDeps.isEmpty();
        }

        //---------------------------PUSH-LOOPBLOCK-------------------------------------------//
        private void pushLoopBlockDep(cpgCFGNode region){
            pushCtrlDep(region);
            jmpCounts.push(jmpCounter);
            jmpCounter = 0;
        }

        //---------------------------POP-LOOPBLOCK------------------------------------------//
        private void popLoopBlockDep(cpgCFGNode region){
            for (Iterator<cpgCFGNode> itr = ctrlDeps.iterator(); jmpCounter > 0 && itr.hasNext();){
                cpgCFGNode dep = itr.next();
                if(Boolean.TRUE.equals(dep.getProperty("isJump"))){
                    itr.remove();
                    --jmpCounter;
                }
            }
            jmpCounter = jmpCounts.pop();
            lastFollowDepth = 0;
            popCtrlDep(region);
        }

        //----------------------------CDG-NODE-EDGE------------------------------------------//
        private void addcdgNodeAndPreEdge(cpgCFGNode node){
            cfg.addVertex(node);
            cdgpopAddPreEdgeTo(node);
        }

        //-------------------------------CDG-POP-----------------------------------
        private void cdgpopAddPreEdgeTo(cpgCFGNode node) {
            if (dontPop)
                dontPop = false;
            else {
                Logger.debug("\nPRE-NODES = " + cdgPreNode.size());
                Logger.debug("PRE-EDGES = " + cdgPreEdge.size() + '\n');
                String check = cdgPreEdge.peek().name().toString();
                cfg.addEdge(new Edge<>(cdgPreNode.pop(), new cpgCFGEdge(cdgPreEdge.pop(), check), node));
            }
            //
            for (int i = casesQueue.size(); i > 0; --i)
                cfg.addEdge(new Edge<>(casesQueue.remove(), new cpgCFGEdge(cpgCFGEdge.Type.CDG_TRUE, ""), node));
        }



            }
        }


