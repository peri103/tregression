package tregression.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import baseline.AskingAgent;
import microbat.Activator;
import microbat.baseline.encoders.NodeFeedbackPair;
import microbat.baseline.encoders.ProbabilityEncoder;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.recommendation.ChosenVariableOption;
import microbat.recommendation.UserFeedback;
import microbat.util.JavaUtil;
import tregression.StepChangeType;
import tregression.StepChangeTypeChecker;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.empiricalstudy.Simulator;
import tregression.model.PairList;
import tregression.preference.TregressionPreference;
import tregression.separatesnapshots.DiffMatcher;
import tregression.views.BuggyTraceView;
import tregression.views.CorrectTraceView;

public class BaselineHandler extends AbstractHandler {
	
	private BuggyTraceView buggyView;
	private CorrectTraceView correctView;
	
	public static String MUTATED_PROJECT_PATH = null;
	public static String ORIGINAL_PROJECT_PATH = null;
	
	public static List<TraceNode> rootCause = null;
	
	public static List<VarValue> inputs = null;
	public static List<VarValue> outputs = null;
	
	public static int mutaitonCount = -1;
	public static int testCaseID = -1;
	public static String testCaseMethod = "";
	
	
	private static UserFeedback manualFeedback = null;
	private static TraceNode feedbackNode = null;
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		JavaUtil.sourceFile2CUMap.clear();
		Job job = new Job("Testing Tregression") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				// Call setup before isReady method
				setup();
				
				// Check is all information is ready
				if (!isReady()) {
					throw new RuntimeException("Baseline Handler is not ready");
				}
				
				// True if users decide to give feedback manually
				final boolean isManualFeedback = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.MANUAL_FEEDBACK).equals("true");
				
				// Set basic information
				final Trace buggyTrace = buggyView.getTrace();
				final Trace correctTrace = correctView.getTrace();
				final PairList pairList = buggyView.getPairList();
				final DiffMatcher matcher = buggyView.getDiffMatcher();

				// Maximum feedback that allowed to give
				final int maxItr = Math.min((int) (buggyTrace.size() * 0.75), 20);
				
				// Number of feedback that is given
				int noOfFeedbacks = 0;
				
				// Set up the probability encoder
				ProbabilityEncoder encoder = new ProbabilityEncoder(buggyTrace);
				encoder.setInputVars(BaselineHandler.inputs);
				encoder.setOutputVars(BaselineHandler.outputs);
				encoder.setup();
				
				// getSlicedExecutionList should be called after encoder.setup() is called
				AskingAgent askingAgent = new AskingAgent(encoder.getSlicedExecutionList());
				
				// Set up type checker and root cause finder for feedback
				StepChangeTypeChecker typeChecker = new StepChangeTypeChecker(buggyTrace, correctTrace);
				Simulator simulator = new Simulator(false, false, 3);
				simulator.prepare(buggyTrace, correctTrace, pairList, matcher);
				RootCauseFinder finder = new RootCauseFinder();
				finder.setRootCauseBasedOnDefects4J(pairList, matcher, buggyTrace, correctTrace);
				finder.checkRootCause(simulator.getObservedFault(), buggyTrace, correctTrace, pairList, matcher);
				
				long startTime = System.currentTimeMillis();
				
				while (noOfFeedbacks <= maxItr) {
					System.out.println("---------------------------------- " + noOfFeedbacks + " iteration");
					
					// Encoder the probability
					encoder.encode();
					
					// Prediction root cause
					TraceNode prediction = encoder.getMostErroneousNode();
					
					// Visualize the prediction
					jumpToNode(prediction);
					
					// Check is root cause is correct
					String rootCauseIDStr = "";
					for (TraceNode rootCause : BaselineHandler.rootCause) {
						rootCauseIDStr += rootCause.getOrder() + ",";
					}
					System.out.println("Ground Truth: " + rootCauseIDStr + " Prediction: " + prediction.getOrder());
					
					if (BaselineHandler.rootCause.contains(prediction)) {
						// Baseline have found the root cause !
						printReport(encoder.getSlicedExecutionList().size(), noOfFeedbacks, startTime);
						break;
					}
					
					// Get the feedback
					NodeFeedbackPair nodeFeedbackPair = null;
					if (isManualFeedback) {
						System.out.println("Please give a feedback manually");
						while (!BaselineHandler.isManualFeedbackReady()) {
							// Wait for the manual feedback
						    try {
						        Thread.sleep(200);
						     } catch(InterruptedException e) {}
						}

						UserFeedback feedback = BaselineHandler.manualFeedback;
						TraceNode feedbackNode = BaselineHandler.feedbackNode;
						nodeFeedbackPair = new NodeFeedbackPair(feedbackNode, feedback);
						BaselineHandler.resetManualFeedback();
						
						System.out.println("Feedback Recieved:");
						System.out.println("Node: " + feedbackNode.getOrder());
						System.out.println("Feedback:" + feedback);
						
					} else {
						
						// Tregression is able to give feedback automatically by
						// comparing the buggy trace and correct trace
						int nextNodeOrder = askingAgent.getNodeOrderToBeAsked(prediction);
						
						if (nextNodeOrder == -1) {
							System.out.println("Cannot find root cause after visiting all node");
							printReport(encoder.getSlicedExecutionList().size(), noOfFeedbacks, startTime);
							break;
						}
						
						TraceNode nextNode = buggyTrace.getTraceNode(nextNodeOrder);
						
						System.out.println("Asking feedback for node: " + nextNode.getOrder());
						
						// Collect feedback from correct trace
						StepChangeType type = typeChecker.getType(nextNode, true, buggyView.getPairList(), buggyView.getDiffMatcher());
						UserFeedback feedback = typeToFeedback(type, nextNode, true, finder);
						System.out.println("Feedback for node: " + nextNode.getOrder() + " is " + feedback);
						
						// Add feedback information into probability encoder
						nodeFeedbackPair = new NodeFeedbackPair(nextNode, feedback);
					}
					
					// Add the feedback as new constraint
					ProbabilityEncoder.addFeedback(nodeFeedbackPair);
					noOfFeedbacks += 1;
				}
				
				BaselineHandler.clearData();
				return Status.OK_STATUS;
			}
			
		};
		
		job.schedule();
		return null;
	}
	
	private void printReport(final int slicedTraceLen, final int noOfFeedbacks, final long startTime) {
		System.out.println("---------------------------------");
		System.out.println("Debug Report: Test Case " + testCaseID);
		System.out.println("---------------------------------");
		System.out.println("Root Cause is found");
		System.out.println("Total Trace Length: " + buggyView.getTrace().getExecutionList().size());
		System.out.println("Sliced Trace Length: " + slicedTraceLen);
		System.out.println("Mutation Count: " + BaselineHandler.mutaitonCount);
		System.out.println("Number of Feedback: " + noOfFeedbacks);
		long endTime = System.currentTimeMillis();
		System.out.println("Time needed: " + Math.floorDiv(endTime - startTime, 1000) + "s");
		System.out.println("---------------------------------");
	}
	
	private UserFeedback typeToFeedback(StepChangeType type, TraceNode node, boolean isOnBefore, RootCauseFinder finder) {
		UserFeedback feedback = new UserFeedback();
		switch(type.getType()) {
		case StepChangeType.IDT:
			feedback.setFeedbackType(UserFeedback.CORRECT);
			break;
		case StepChangeType.CTL:
			feedback.setFeedbackType(UserFeedback.WRONG_PATH);
			break;
		case StepChangeType.DAT:
			feedback.setFeedbackType(UserFeedback.WRONG_VARIABLE_VALUE);
			VarValue wrongVar = type.getWrongVariable(node, isOnBefore, finder);
			feedback.setOption(new ChosenVariableOption(wrongVar, null));
			break;
		case StepChangeType.SRC:
			feedback.setFeedbackType(UserFeedback.UNCLEAR);
			break;
		}
		return feedback;
	}
	
	private void jumpToNode(final TraceNode targetNode) {
		Display.getDefault().asyncExec(new Runnable() {
		    @Override
		    public void run() {
				Trace buggyTrace = buggyView.getTrace();
				buggyView.jumpToNode(buggyTrace, targetNode.getOrder(), true);
		    }
		});
	}
	
	private void setup() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					buggyView = (BuggyTraceView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(BuggyTraceView.ID);
					correctView = (CorrectTraceView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(CorrectTraceView.ID);
				} catch (PartInitException e) {
					buggyView = null;
					correctView = null;
					System.out.println("Fail to get the view");
				}
			}
		});
	}
	
	private boolean isReady() {
		if (BaselineHandler.inputs == null || BaselineHandler.outputs == null || BaselineHandler.rootCause == null) {
			return false;
		}
		
		return 	BaselineHandler.MUTATED_PROJECT_PATH != null &&
				BaselineHandler.ORIGINAL_PROJECT_PATH != null &&
				!BaselineHandler.rootCause.isEmpty() &&
				!BaselineHandler.inputs.isEmpty() &&
				!BaselineHandler.outputs.isEmpty() &&
				this.buggyView != null &&
				this.correctView != null;
	}
	
	public static void setTestCaseID(int testCaseID) {
		BaselineHandler.testCaseID = testCaseID;
	}
	
	public static void setMutatedProPath(String path) {
		BaselineHandler.MUTATED_PROJECT_PATH = path;
	}
	
	public static void setOriginalProPath(String path) {
		BaselineHandler.ORIGINAL_PROJECT_PATH = path;
	}
	
	public static void setRootCause(List<TraceNode> rootCause) {
		BaselineHandler.rootCause = rootCause;
	}
	
	public static void setInputs(List<VarValue> inputs) {
		BaselineHandler.inputs = inputs;
	}
	
	public static void setOutputs(List<VarValue> outputs) {
		BaselineHandler.outputs = outputs;
	}
	
	public static void clearIO() {
		if (BaselineHandler.inputs != null) {
			BaselineHandler.inputs.clear();
		}
		
		if (BaselineHandler.outputs != null) {
			BaselineHandler.outputs.clear();
		}
		
		System.out.println("BaselineHandler: Clear IO");
	}
	
	public static void setMutationCount(int count) {
		BaselineHandler.mutaitonCount = count;
	}
	
	public static void addInputs(List<VarValue> inputs) {
		if (BaselineHandler.inputs == null) {
			BaselineHandler.inputs = new ArrayList<>();
		}
		BaselineHandler.inputs.addAll(inputs);
		
		for (VarValue input : BaselineHandler.inputs) {
			System.out.println("BaselineHandler: Selected Inputs: " + input.getVarID());
		}
	}
	
	public static void printIO() {
		for (VarValue input : BaselineHandler.inputs) {
			System.out.println("BaselineHandler: Selected Inputs: " + input.getVarID());
		}
		for (VarValue output : BaselineHandler.outputs) {
			System.out.println("BaselineHandler: Selected Outputs: " + output.getVarID());
		}
	}
	
	public static void addOutpus(List<VarValue> outputs) {
		if (BaselineHandler.outputs == null) {
			BaselineHandler.outputs = new ArrayList<>();
		}
		BaselineHandler.outputs.addAll(outputs);
		
		for (VarValue output : BaselineHandler.outputs) {
			System.out.println("BaselineHandler: Selected Outputs: " + output.getVarID());
		}
	}
	
	public static void clearData() {
		BaselineHandler.MUTATED_PROJECT_PATH = null;
		BaselineHandler.ORIGINAL_PROJECT_PATH = null;
		
		BaselineHandler.rootCause = null;
		
		BaselineHandler.inputs = null;
		BaselineHandler.outputs = null;
	}
	
	public static void setManualFeedback(UserFeedback manualFeedback, TraceNode node) {
		BaselineHandler.manualFeedback = manualFeedback;
		BaselineHandler.feedbackNode = node;
	}
	
	public static void resetManualFeedback() {
		BaselineHandler.manualFeedback = null;
		BaselineHandler.feedbackNode = null;
	}
	
	public static void setTestCaseMethod(final String testCaseMethod) {
		BaselineHandler.testCaseMethod = testCaseMethod;
	}
	
	public static boolean isManualFeedbackReady() {
		return BaselineHandler.manualFeedback != null && BaselineHandler.feedbackNode != null;
	}

}
