package tregression.empiricalstudy;

import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.INVOKEINTERFACE;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.Type;

import microbat.codeanalysis.bytecode.ByteCodeParser;
import microbat.codeanalysis.bytecode.MethodFinderByLine;
import microbat.codeanalysis.runtime.PreCheckInformation;
import microbat.model.BreakPoint;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import sav.common.core.utils.SignatureUtils;
import sav.strategies.dto.AppJavaClassPath;

public class RegressionUtil {
	public static List<String> identifyIncludedClassNames(List<TraceNode> stopSteps,
			PreCheckInformation precheckInfo, List<TraceNode> visitedSteps) {
		Repository.clearCache();
		
		List<BreakPoint> parsedBreakPoints = new ArrayList<>();
		List<String> classes = new ArrayList<>();
		
		for(TraceNode stopStep: stopSteps){
			AppJavaClassPath appClassPath = stopStep.getTrace().getAppJavaClassPath();
			
			List<TraceNode> range = identifyEnhanceRange(stopStep, visitedSteps);
			range.add(stopStep);
			
			for(TraceNode rangeStep: range) {
				BreakPoint point = rangeStep.getBreakPoint();
				if(parsedBreakPoints.contains(point)){
					continue;
				}
				parsedBreakPoints.add(point);
				
				String clazz = point.getClassCanonicalName();
				
				MethodFinderByLine finder = new MethodFinderByLine(point);
				ByteCodeParser.parse(clazz, finder, appClassPath);
				Method method = finder.getMethod();
				List<InstructionHandle> insList = finder.getHandles();
				
				List<String> visitedLibClasses = findInvokedLibClasses(rangeStep, insList, method, precheckInfo);
				for(String str: visitedLibClasses){
					if(!classes.contains(str)){
						classes.add(str);
					}
				}
			}
		}
		
		return classes;
	}

	private static TraceNode findClosestStep(TraceNode stopStep, List<TraceNode> visitedSteps) {
		TraceNode closestStep = null;
		int distance = -1;
		for(TraceNode step: visitedSteps) {
			if(step.getOrder()>stopStep.getOrder()) {
				if(closestStep==null) {
					closestStep = step;
					distance = step.getOrder() - stopStep.getOrder();
				}
				else {
					int newDis = step.getOrder() - stopStep.getOrder();
					if(newDis < distance) {
						closestStep = step;
						distance = newDis;
					}
				}
			}
		}
		
		return closestStep;
	}
	
	private static List<TraceNode> identifyEnhanceRange(TraceNode stopStep, List<TraceNode> visitedSteps){
		TraceNode closetStep = findClosestStep(stopStep, visitedSteps);
		List<TraceNode> list = new ArrayList<>();
		
		if(closetStep==null){
			return list;
		}
		
		Trace trace = stopStep.getTrace();
		for(int i=closetStep.getOrder(); i>stopStep.getOrder(); i--) {
			TraceNode step = trace.getTraceNode(i);
			list.add(step);
		}
		
		return list;
	}
	
	private static List<String> findInvokedLibClasses(TraceNode step, List<InstructionHandle> insList, Method method,
			PreCheckInformation precheckInfo) {
		List<String> list = new ArrayList<>();
		if(step.getInvocationChildren().isEmpty()){
			TraceNode stepOver = step.getStepOverPrevious();
			
			String ignoreMethod = null;
			if(stepOver!=null && stepOver.getBreakPoint().equals(step.getBreakPoint())){
				TraceNode invocationChild = stepOver.getInvocationChildren().get(0);
				ignoreMethod = invocationChild.getMethodSign();
			}
			
			ConstantPoolGen cGen = new ConstantPoolGen(method.getConstantPool());
			for(InstructionHandle handle: insList){
				Instruction ins = handle.getInstruction();
				
				if(isForReadWriteVariable(ins)){
					String className = parseClassName(ins, method, cGen);
					if(className != null){
						if(className.equals("java.lang.Object") || className.equals("java.lang.String")){
							continue;
						}
						
						if(SignatureUtils.isSignature(className)){
							className = SignatureUtils.signatureToName(className);
							className = className.replace("[]", "");
						}
						
						appendSuperClass(className, step.getTrace().getAppJavaClassPath(), list);
						
						if(!list.contains(className)){
							list.add(className);
						}	
						
					}
				}
				else if(ins instanceof InvokeInstruction){
					InvokeInstruction iIns = (InvokeInstruction)ins;
					
					String invokedMethodName = iIns.getMethodName(cGen);
					if(invokedMethodName.equals(method.getName())){
						continue;
					}
					
					if(ignoreMethod!=null && ignoreMethod.contains(invokedMethodName)){
						continue;
					}
					
					String className = iIns.getClassName(cGen);
					if(className.equals("java.lang.Object") || className.equals("java.lang.String")){
						continue;
					}
					
					if(SignatureUtils.isSignature(className)){
						className = SignatureUtils.signatureToName(className);
						className = className.replace("[]", "");
					}
					
					appendSuperClass(className, step.getTrace().getAppJavaClassPath(), list);
					
					if(!list.contains(className)){
						list.add(className);
					}	
					
					//add implementation class
					if(ins instanceof INVOKEINTERFACE) {
						List<String> loadedClassStrings = precheckInfo.getLoadedClasses();
						List<String> implementations = findImplementation(className, 
								loadedClassStrings, step.getTrace().getAppJavaClassPath());
						
						for(String implementation: implementations) {
							list.add(implementation);
							appendSuperClass(className, step.getTrace().getAppJavaClassPath(), list);
						}
					}
					
				}
			}
			
		}
		
		return list;
	}
	
	@SuppressWarnings("deprecation")
	private static String parseClassName(Instruction ins, Method method, ConstantPoolGen cGen) {
		if(ins instanceof LocalVariableInstruction){
			LocalVariableTable table = method.getLocalVariableTable();
			if(table != null){
				LocalVariableInstruction lIns = (LocalVariableInstruction) ins;
				LocalVariable localVar = table.getLocalVariable(lIns.getIndex());
				if(localVar!=null){
					String classSig = localVar.getSignature();
					if(classSig.length()!=1){
						String className = SignatureUtils.signatureToName(classSig);
						return className;				
					}
				}
			}
		}
		else if(ins instanceof FieldInstruction){
			FieldInstruction fIns = (FieldInstruction)ins;
			Type type = fIns.getFieldType(cGen);
			String classSig = type.getSignature();
			if(classSig.length()!=1){
				String className = SignatureUtils.signatureToName(classSig);
				return className;				
			}
		}
		else if(ins instanceof ArrayInstruction){
			ArrayInstruction aIns = (ArrayInstruction)ins;
			Type type = aIns.getType(cGen);
			String classSig = type.getSignature();
			if(classSig.length()!=1){
				String className = SignatureUtils.signatureToName(classSig);
				return className;				
			}
		}
		
		return null;
	}

	private static boolean isForReadWriteVariable(Instruction ins) {
		return ins instanceof FieldInstruction || 
				ins instanceof ArrayInstruction ||
				ins instanceof LocalVariableInstruction;
	}

	private static void appendSuperClass(String className, AppJavaClassPath appPath, List<String> includedClasses){
		JavaClass javaClazz = ByteCodeParser.parse(className, appPath);
		if(javaClazz==null){
			return;
		}
		
		try {
			for(JavaClass superClass: javaClazz.getSuperClasses()){
				if(!superClass.getClassName().equals("java.lang.Object")){
					if(!includedClasses.contains(superClass.getClassName())){
						includedClasses.add(superClass.getClassName());
					}	
				}
			}
		} catch (ClassNotFoundException e) {
		}
	}

	private static List<String> findImplementation(String className, List<String> loadedClassStrings,
			AppJavaClassPath appClassPath) {
		List<String> list = new ArrayList<>();
		for(String loadedClassString: loadedClassStrings) {
			if(loadedClassString.contains("microbat") || loadedClassString.contains("sav.common")
					|| loadedClassString.contains("sun.reflect")
					|| loadedClassString.contains("com.sun")) {
				continue;
			}
			
			JavaClass javaClass = ByteCodeParser.parse(loadedClassString, appClassPath);
			if(javaClass!=null) {
				try {
					for(JavaClass interfaze: javaClass.getAllInterfaces()) {
						if(interfaze.getClassName().equals(className)) {
							list.add(loadedClassString);
							break;
						}
					}
				} catch (ClassNotFoundException e) {
//					e.printStackTrace();
				}
				
			}
		}
		return list;
	}
}
