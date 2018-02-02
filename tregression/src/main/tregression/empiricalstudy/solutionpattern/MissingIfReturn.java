package tregression.empiricalstudy.solutionpattern;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ReturnStatement;

import tregression.empiricalstudy.DeadEndRecord;
import tregression.empiricalstudy.EmpiricalTrial;
import tregression.empiricalstudy.RootCauseNode;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.diff.DiffChunk;
import tregression.separatesnapshots.diff.FilePairWithDiff;
import tregression.separatesnapshots.diff.LineChange;

public class MissingIfReturn extends PatternDetector{
	@Override
	public boolean detect(DeadEndRecord deadEndRecord, EmpiricalTrial trial) {
		if(deadEndRecord.getType()==DeadEndRecord.DATA){
			return false;
		}
		
		for(RootCauseNode rootCause: trial.getRootCauseFinder().getRealRootCaseList()){
			
			if(!rootCause.isOnBefore()){
				DiffMatcher matcher = trial.getDiffMatcher();
				for(FilePairWithDiff fileDiff: matcher.getFileDiffList()){
					for(DiffChunk chunk: fileDiff.getChunks()){
						boolean ifReturnFound = isIfReturnFound(chunk, rootCause.getRoot().getLineNumber());
						if(ifReturnFound){
							return true;
						}
					}
				}
			}
		}
		
		
		return false;
	}

	public class ReturnFinder extends ASTVisitor{
		
		boolean isFound = false;
		
		@Override
		public boolean visit(ReturnStatement state){
			isFound = true;
			return false;
		}
	}
	
	private boolean isIfReturnFound(DiffChunk chunk, int lineNumber) {
		StringBuffer buffer = new StringBuffer();
		boolean isHit = false;
		for(LineChange lineChange: chunk.getChangeList()){
			if(lineChange.getType()==LineChange.ADD){
				String content = lineChange.getLineContent();
				buffer.append(content.substring(1, content.length())+"\n");
				
				int line = chunk.getLineNumberInTarget(lineChange);
				if(line==lineNumber){
					isHit = true;
				}
			}
		}
		
		if(isHit){
			String code = buffer.toString();
			ASTParser parser = ASTParser.newParser(AST.JLS8); 
			parser.setKind(ASTParser.K_STATEMENTS);
			parser.setSource(code.toCharArray()); // set source
			ASTNode node = parser.createAST(null);
			ReturnFinder finder = new ReturnFinder();
			node.accept(finder);
			boolean isFound = finder.isFound;
			return isFound;
		}
		
		return false;
	}

	@Override
	public SolutionPattern getSolutionPattern() {
		return new SolutionPattern(SolutionPattern.MISSING_IF_RETURN);
	}
}
