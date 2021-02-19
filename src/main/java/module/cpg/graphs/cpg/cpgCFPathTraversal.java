package module.cpg.graphs.cpg;

import ghaffarian.graphs.Edge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class cpgCFPathTraversal implements Iterator {

    private final cpgCFGNode start;
    private final cpgControlFlowGraph cfg;
    private final Deque<Edge<cpgCFGNode, cpgCFGEdge>> paths;

    private cpgCFGNode current;
    private boolean continueNextPath;
    private Edge<cpgCFGNode, cpgCFGEdge> nextEdge;

    public cpgCFPathTraversal(cpgControlFlowGraph cfg, cpgCFGNode startNode) {
        this.cfg = cfg;
        start = startNode;
        paths = new ArrayDeque<>();
        continueNextPath = false;
        current = null;
        nextEdge = null;
    }

    private cpgCFGNode start() {
        nextEdge = null;  // new CFEdge(CFEdge.Type.EPSILON);
        current = start;
        return current;
    }

    @Override
    public boolean hasNext() {
        return current == null || (!paths.isEmpty()) ||
                (cfg.getOutDegree(current) > 0 && !continueNextPath);
    }

    @Override
    public cpgCFGNode next() {
        if (current == null)
            return start();
        //
        if (!continueNextPath) {
            Iterator<Edge<cpgCFGNode, cpgCFGEdge>> outEdges = cfg.outgoingEdgesIterator(current);
            while (outEdges.hasNext()) {
                Edge<cpgCFGNode, cpgCFGEdge> out = outEdges.next();
                paths.push(out);
            }
        }
        continueNextPath = false;
        //
        if (paths.isEmpty())
            return null;
        nextEdge = paths.pop();
        current = nextEdge.target;
        return current;
    }

    public void continueNextPath() {
        continueNextPath = true;
    }

}
