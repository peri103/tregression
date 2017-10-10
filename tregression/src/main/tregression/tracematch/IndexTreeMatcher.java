package tregression.tracematch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import microbat.algorithm.graphdiff.Matcher;
import microbat.algorithm.graphdiff.MatchingGraphPair;
import microbat.model.BreakPoint;
import microbat.model.value.GraphNode;
import microbat.util.JavaUtil;
import tregression.separatesnapshots.DiffMatcher;

public abstract class IndexTreeMatcher implements Matcher{

	/**
	 * DiffMatcher contains the information of how source code should match with each other.
	 * If this field is null, we assume there is only one-line modification between original
	 * and regression version.
	 */
	protected DiffMatcher diffMatcher;
	
	protected Map<Integer, MatchingGraphPair> pairMap = new HashMap<>();
	protected Map<String, List<String>> lineMap = new HashMap<>();
	
	@Override
	public abstract List<MatchingGraphPair> matchList(List<? extends GraphNode> childrenBefore,
			List<? extends GraphNode> childrenAfter);

	protected IndexTreeNode findMostSimilarNode(GraphNode gNodeBefore, List<? extends GraphNode> childrenAfter, 
			List<MatchingGraphPair> pairList) {
		IndexTreeNode mostSimilarNode = null;
		double sim = -1;
		
		IndexTreeNode itNodeBefore = (IndexTreeNode)gNodeBefore;
		for(GraphNode gNodeAfter: childrenAfter){
			IndexTreeNode itNodeAfter = (IndexTreeNode)gNodeAfter;
			if(hasMatched(pairList, itNodeAfter)){
				continue;
			}
			
			if(isControlPathCompatible(itNodeBefore, itNodeAfter)){
				if(mostSimilarNode==null){
					mostSimilarNode = itNodeAfter;
					sim = sim(itNodeBefore, itNodeAfter);
				}
				else{
					double sim1 = sim(itNodeBefore, itNodeAfter);
					if(sim1 > sim){
						mostSimilarNode = itNodeAfter;
						sim = sim1;
					}
				}
			}
		}
		
		if(sim==0){
			mostSimilarNode = null;
		}
		
		return mostSimilarNode;
	}
	
	private boolean hasMatched(List<MatchingGraphPair> pairList, IndexTreeNode itNodeAfter) {
		MatchingGraphPair p = pairMap.get(itNodeAfter.getTraceNode().getOrder());
		return p!=null;
//		for(MatchingGraphPair pair: pairList){
//			if(pair.getNodeAfter()==itNodeAfter){
//				return true;
//			}
//		}
//		return false;
	}
	
	private boolean isControlPathCompatible(IndexTreeNode itNodeBefore, IndexTreeNode itNodeAfter) {
		List<ControlNode> pathBefore = itNodeBefore.getControlPath();
		List<ControlNode> pathAfter = itNodeAfter.getControlPath();
		
		/**
		 * Here is an optimization:
		 * If parent control node has been recorded in pair list, we do not need to compare their control
		 * path any more. 
		 */
		MatchingGraphPair pair = null;
		int size = pathAfter.size();
		for(int i=size-1; i>=0; i--) {
			ControlNode cNode = pathAfter.get(i);
			MatchingGraphPair p = pairMap.get(cNode.getOrder());
			if(p!=null) {
				pair = p;
				break;
			}
		}
		
		int cursorBefore = 0;
		int cursorAfter = 0;
		if(pair!=null) {
			cursorBefore = ((IndexTreeNode)pair.getNodeBefore()).getOrder();
			cursorAfter = ((IndexTreeNode)pair.getNodeAfter()).getOrder();
		}
		
		
		for(ControlNode nodeBefore: pathBefore){
			if(nodeBefore.getOrder()<=cursorBefore) {
				continue;
			}
			
			if(nodeBefore.getAppearOrder() > 1){
				boolean flag = canFindMatchingNode(nodeBefore, pathAfter);
				if(!flag){
					return false;
				}
			}
		}
		
		for(ControlNode nodeAfter: pathAfter){
			if(nodeAfter.getOrder()<=cursorAfter) {
				continue;
			}
			
			if(nodeAfter.getAppearOrder() > 1){
				boolean flag = canFindMatchingNode(nodeAfter, pathBefore);
				if(!flag){
					return false;
				}
			}
		}
		
		
		return true;
	}
	
	private boolean canFindMatchingNode(ControlNode node, List<ControlNode> path) {
		for(ControlNode thatNode: path){
			if(node.isMatchableWith(thatNode, diffMatcher)){
				return true;
			}
		}
		return false;
	}
	
	private double sim(IndexTreeNode itNodeBefore, IndexTreeNode itNodeAfter) {
		BreakPoint pointBefore = itNodeBefore.getBreakPoint();
		BreakPoint pointAfter = itNodeAfter.getBreakPoint();
		
		try {
			String pathBefore = pointBefore.getFullJavaFilePath();
			List<String> stringLinesBefore = lineMap.get(pathBefore);
			if(stringLinesBefore==null){
				stringLinesBefore = Files.readAllLines(Paths.get(pointBefore.getFullJavaFilePath()), StandardCharsets.ISO_8859_1);
				lineMap.put(pathBefore, stringLinesBefore);
			}
			String textBefore = stringLinesBefore.get(pointBefore.getLineNumber()-1);
			
			String pathAfter = pointAfter.getFullJavaFilePath();
			List<String> stringLinesAfter = lineMap.get(pathAfter); 
			if(stringLinesAfter==null){
				stringLinesAfter = Files.readAllLines(Paths.get(pointAfter.getFullJavaFilePath()), StandardCharsets.ISO_8859_1);
				lineMap.put(pathAfter, stringLinesAfter);
			}
			String textAfter = stringLinesAfter.get(pointAfter.getLineNumber()-1);
			
			if(textBefore.equals(textAfter)) {
				return 1;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		ASTNode nodeBefore = parseASTNodeInBreakpoint(itNodeBefore, pointBefore);
		ASTNode nodeAfter = parseASTNodeInBreakpoint(itNodeAfter, pointAfter);
		
		if(nodeBefore.getNodeType()==nodeAfter.getNodeType()){
			return 1;
		}
		else{
			return 0;			
		}
		
	}
	
	private ASTNode parseASTNodeInBreakpoint(IndexTreeNode itNodeBefore, BreakPoint pointBefore) {
		String compilationUnitName = itNodeBefore.getTraceNode().getDeclaringCompilationUnitName();
		
		CompilationUnit cu = JavaUtil.findCompiltionUnitBySourcePath(pointBefore.getFullJavaFilePath(), 
				compilationUnitName);
		ASTNode node = findSpecificNode(cu, pointBefore);
		
		return node;
	}
	
	private ASTNode findSpecificNode(CompilationUnit cu, BreakPoint point) {
		MinimumASTNodeFinder finder = new MinimumASTNodeFinder(point.getLineNumber(), cu);
		cu.accept(finder);
		return finder.minimumNode;
	}
}
