package gr.uom.java.ast.decomposition.cfg.mapping;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import gr.uom.java.ast.MethodInvocationObject;
import gr.uom.java.ast.decomposition.ASTNodeDifference;
import gr.uom.java.ast.decomposition.ASTNodeMatcher;
import gr.uom.java.ast.decomposition.AbstractStatement;
import gr.uom.java.ast.decomposition.CompositeStatementObject;
import gr.uom.java.ast.decomposition.StatementObject;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;
import gr.uom.java.ast.decomposition.cfg.BasicBlock;
import gr.uom.java.ast.decomposition.cfg.GraphEdge;
import gr.uom.java.ast.decomposition.cfg.GraphNode;
import gr.uom.java.ast.decomposition.cfg.PDG;
import gr.uom.java.ast.decomposition.cfg.PDGControlDependence;
import gr.uom.java.ast.decomposition.cfg.PDGDataDependence;
import gr.uom.java.ast.decomposition.cfg.PDGDependence;
import gr.uom.java.ast.decomposition.cfg.PDGMethodEntryNode;
import gr.uom.java.ast.decomposition.cfg.PDGNode;
import gr.uom.java.ast.decomposition.cfg.PDGSlice;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;

public class PDGMapper {
	private PDG pdg1;
	private PDG pdg2;
	private ControlDependenceTreeNode controlDependenceTreePDG1;
	private ControlDependenceTreeNode controlDependenceTreePDG2;
	private ICompilationUnit iCompilationUnit1;
	private ICompilationUnit iCompilationUnit2;
	private MappingState maximumStateWithMinimumDifferences;
	private Set<PDGNode> nonMappedNodesG1;
	private Set<PDGNode> nonMappedNodesG2;
	private TreeSet<PDGNode> nonMappedNodesSliceUnionG1;
	private TreeSet<PDGNode> nonMappedNodesSliceUnionG2;
	private Map<String, ArrayList<VariableDeclaration>> commonPassedParameters;
	private Set<VariableDeclaration> passedParametersG1;
	private Set<VariableDeclaration> passedParametersG2;
	private Set<VariableDeclaration> accessedLocalFieldsG1;
	private Set<VariableDeclaration> accessedLocalFieldsG2;
	private Set<MethodInvocationObject> accessedLocalMethodsG1;
	private Set<MethodInvocationObject> accessedLocalMethodsG2;
	private IProgressMonitor monitor;
	
	public PDGMapper(PDG pdg1, PDG pdg2, IProgressMonitor monitor) {
		this.pdg1 = pdg1;
		this.pdg2 = pdg2;
		this.controlDependenceTreePDG1 = new ControlDependenceTreeNode(null, pdg1.getEntryNode());
		this.controlDependenceTreePDG2 = new ControlDependenceTreeNode(null, pdg2.getEntryNode());
		CompilationUnit cu1 = (CompilationUnit)pdg1.getMethod().getMethodDeclaration().getRoot();
		this.iCompilationUnit1 = (ICompilationUnit)cu1.getJavaElement();
		CompilationUnit cu2 = (CompilationUnit)pdg2.getMethod().getMethodDeclaration().getRoot();
		this.iCompilationUnit2 = (ICompilationUnit)cu2.getJavaElement();
		this.nonMappedNodesG1 = new LinkedHashSet<PDGNode>();
		this.nonMappedNodesG2 = new LinkedHashSet<PDGNode>();
		this.nonMappedNodesSliceUnionG1 = new TreeSet<PDGNode>();
		this.nonMappedNodesSliceUnionG2 = new TreeSet<PDGNode>();
		this.commonPassedParameters = new LinkedHashMap<String, ArrayList<VariableDeclaration>>();
		this.passedParametersG1 = new LinkedHashSet<VariableDeclaration>();
		this.passedParametersG2 = new LinkedHashSet<VariableDeclaration>();
		this.accessedLocalFieldsG1 = new LinkedHashSet<VariableDeclaration>();
		this.accessedLocalFieldsG2 = new LinkedHashSet<VariableDeclaration>();
		this.accessedLocalMethodsG1 = new LinkedHashSet<MethodInvocationObject>();
		this.accessedLocalMethodsG2 = new LinkedHashSet<MethodInvocationObject>();
		this.monitor = monitor;
		processPDGNodes();
		findNonMappedNodes(pdg1, maximumStateWithMinimumDifferences.getMappedNodesG1(), nonMappedNodesG1);
		findNonMappedNodes(pdg2, maximumStateWithMinimumDifferences.getMappedNodesG2(), nonMappedNodesG2);
		computeSliceForNonMappedNodes(pdg1, nonMappedNodesG1, nonMappedNodesSliceUnionG1);
		computeSliceForNonMappedNodes(pdg2, nonMappedNodesG2, nonMappedNodesSliceUnionG2);
		findPassedParameters();
		findLocallyAccessedFields(pdg1, maximumStateWithMinimumDifferences.getMappedNodesG1(), accessedLocalFieldsG1, accessedLocalMethodsG1);
		findLocallyAccessedFields(pdg2, maximumStateWithMinimumDifferences.getMappedNodesG2(), accessedLocalFieldsG2, accessedLocalMethodsG2);
	}

	private void computeSliceForNonMappedNodes(PDG pdg, Set<PDGNode> nonMappedNodes, Set<PDGNode> nonMappedNodesSliceUnion) {
		List<BasicBlock> basicBlocks = pdg.getBasicBlocks();
		//we need a strategy to select the appropriate basic block according to the region of the duplicated code
		BasicBlock block = basicBlocks.get(0);
		if(!nonMappedNodes.isEmpty()) {
			PDGSlice subgraph = new PDGSlice(pdg, block);
			for(PDGNode nodeCriterion : nonMappedNodes) {
				nonMappedNodesSliceUnion.addAll(subgraph.computeSlice(nodeCriterion));
			}
		}
	}

	private void findLocallyAccessedFields(PDG pdg, Set<PDGNode> mappedNodes, Set<VariableDeclaration> accessedFields,
			Set<MethodInvocationObject> accessedMethods) {
		Set<PlainVariable> usedLocalFields = new LinkedHashSet<PlainVariable>();
		Set<MethodInvocationObject> accessedLocalMethods = new LinkedHashSet<MethodInvocationObject>();
		Iterator<GraphNode> iterator = pdg.getNodeIterator();
		while(iterator.hasNext()) {
			PDGNode pdgNode = (PDGNode)iterator.next();
			if(mappedNodes.contains(pdgNode)) {
				AbstractStatement abstractStatement = pdgNode.getStatement();
				if(abstractStatement instanceof StatementObject) {
					StatementObject statement = (StatementObject)abstractStatement;
					usedLocalFields.addAll(statement.getUsedFieldsThroughThisReference());
					accessedLocalMethods.addAll(statement.getInvokedMethodsThroughThisReference());
				}
				else if(abstractStatement instanceof CompositeStatementObject) {
					CompositeStatementObject composite = (CompositeStatementObject)abstractStatement;
					usedLocalFields.addAll(composite.getUsedFieldsThroughThisReferenceInExpressions());
					accessedLocalMethods.addAll(composite.getInvokedMethodsThroughThisReferenceInExpressions());
				}
			}
		}
		ITypeBinding declaringClassTypeBinding = pdg.getMethod().getMethodDeclaration().resolveBinding().getDeclaringClass();
		Set<VariableDeclaration> fieldsAccessedInMethod = pdg.getFieldsAccessedInMethod();
		for(PlainVariable variable : usedLocalFields) {
			for(VariableDeclaration fieldDeclaration : fieldsAccessedInMethod) {
				if(variable.getVariableBindingKey().equals(fieldDeclaration.resolveBinding().getKey()) &&
						fieldDeclaration.resolveBinding().getDeclaringClass().isEqualTo(declaringClassTypeBinding)) {
					accessedFields.add(fieldDeclaration);
					break;
				}
			}
		}
		for(MethodInvocationObject invocation : accessedLocalMethods) {
			if(invocation.getMethodInvocation().resolveMethodBinding().getDeclaringClass().isEqualTo(declaringClassTypeBinding)) {
				accessedMethods.add(invocation);
			}
		}
	}

	private void findNonMappedNodes(PDG pdg, Set<PDGNode> mappedNodes, Set<PDGNode> nonMappedNodes) {
		Iterator<GraphNode> iterator = pdg.getNodeIterator();
		while(iterator.hasNext()) {
			PDGNode pdgNode = (PDGNode)iterator.next();
			if(!mappedNodes.contains(pdgNode)) {
				nonMappedNodes.add(pdgNode);
			}
		}
	}

	private Set<AbstractVariable> extractPassedParameters(PDG pdg,  Set<PDGNode> mappedNodes) {
		Set<AbstractVariable> passedParameters = new LinkedHashSet<AbstractVariable>();
		for(GraphEdge edge : pdg.getEdges()) {
			PDGDependence dependence = (PDGDependence)edge;
			PDGNode srcPDGNode = (PDGNode)dependence.getSrc();
			PDGNode dstPDGNode = (PDGNode)dependence.getDst();
			if(dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(!mappedNodes.contains(srcPDGNode) && mappedNodes.contains(dstPDGNode)) {
					passedParameters.add(dataDependence.getData());
				}
			}
		}
		return passedParameters;
	}

	private void findPassedParameters() {
		Set<AbstractVariable> passedParametersG1 = extractPassedParameters(pdg1, maximumStateWithMinimumDifferences.getMappedNodesG1());
		Set<AbstractVariable> passedParametersG2 = extractPassedParameters(pdg2, maximumStateWithMinimumDifferences.getMappedNodesG2());
		Set<VariableDeclaration> variableDeclarationsAndAccessedFieldsInMethod1 = pdg1.getVariableDeclarationsAndAccessedFieldsInMethod();
		Set<VariableDeclaration> variableDeclarationsAndAccessedFieldsInMethod2 = pdg2.getVariableDeclarationsAndAccessedFieldsInMethod();
		/*for(PDGEdgeMapping edgeMapping : maximumStateWithMinimumDifferences.getEdgeMappings()) {
			PDGDependence edgeG1 = edgeMapping.getEdgeG1();
			PDGDependence edgeG2 = edgeMapping.getEdgeG2();
			if(edgeG1 instanceof PDGDataDependence && edgeG2 instanceof PDGDataDependence) {
				PDGDataDependence dataEdgeG1 = (PDGDataDependence)edgeG1;
				PDGDataDependence dataEdgeG2 = (PDGDataDependence)edgeG2;
				if(passedParametersG1.contains(dataEdgeG1.getData()) && passedParametersG2.contains(dataEdgeG2.getData())) {
					ArrayList<VariableDeclaration> variableDeclarations = new ArrayList<VariableDeclaration>();
					for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod1) {
						if(variableDeclaration.resolveBinding().getKey().equals(dataEdgeG1.getData().getVariableBindingKey())) {
							variableDeclarations.add(variableDeclaration);
							break;
						}
					}
					for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod2) {
						if(variableDeclaration.resolveBinding().getKey().equals(dataEdgeG2.getData().getVariableBindingKey())) {
							variableDeclarations.add(variableDeclaration);
							break;
						}
					}
					commonPassedParameters.put(dataEdgeG1.getData().getVariableBindingKey(), variableDeclarations);
					passedParametersG1.remove(dataEdgeG1.getData());
					passedParametersG2.remove(dataEdgeG2.getData());
				}
			}
		}
		for(AbstractVariable variable1 : passedParametersG1) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod1) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable1.getVariableBindingKey())) {
					this.passedParametersG1.add(variableDeclaration);
					break;
				}
			}
		}
		for(AbstractVariable variable2 : passedParametersG2) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod2) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable2.getVariableBindingKey())) {
					this.passedParametersG2.add(variableDeclaration);
					break;
				}
			}
		}*/
	}

	public PDG getPDG1() {
		return pdg1;
	}

	public PDG getPDG2() {
		return pdg2;
	}

	public MappingState getMaximumStateWithMinimumDifferences() {
		return maximumStateWithMinimumDifferences;
	}

	public Set<PDGNode> getNonMappedNodesG1() {
		return nonMappedNodesG1;
	}

	public Set<PDGNode> getNonMappedNodesG2() {
		return nonMappedNodesG2;
	}

	public Map<String, ArrayList<VariableDeclaration>> getCommonPassedParameters() {
		return commonPassedParameters;
	}

	public Set<VariableDeclaration> getPassedParametersG1() {
		return passedParametersG1;
	}

	public Set<VariableDeclaration> getPassedParametersG2() {
		return passedParametersG2;
	}

	public Set<VariableDeclaration> getAccessedLocalFieldsG1() {
		return accessedLocalFieldsG1;
	}

	public Set<VariableDeclaration> getAccessedLocalFieldsG2() {
		return accessedLocalFieldsG2;
	}

	public Set<MethodInvocationObject> getAccessedLocalMethodsG1() {
		return accessedLocalMethodsG1;
	}

	public Set<MethodInvocationObject> getAccessedLocalMethodsG2() {
		return accessedLocalMethodsG2;
	}

	public Set<PDGNode> getRemovableNodesG1() {
		Set<PDGNode> removableNodes = maximumStateWithMinimumDifferences.getMappedNodesG1();
		removableNodes.removeAll(nonMappedNodesSliceUnionG1);
		return removableNodes;
	}

	public Set<PDGNode> getRemovableNodesG2() {
		Set<PDGNode> removableNodes = maximumStateWithMinimumDifferences.getMappedNodesG2();
		removableNodes.removeAll(nonMappedNodesSliceUnionG2);
		return removableNodes;
	}

	public TreeSet<PDGNode> getRemainingNodesG1() {
		return nonMappedNodesSliceUnionG1;
	}

	public TreeSet<PDGNode> getRemainingNodesG2() {
		return nonMappedNodesSliceUnionG2;
	}

	public List<ASTNodeDifference> getNodeDifferences() {
		return maximumStateWithMinimumDifferences.getNodeDifferences();
	}

	private MappingState findMaximumStateWithMinimumDifferences(List<MappingState> states) {
		int max = 0;
		List<MappingState> maximumStates = new ArrayList<MappingState>();
		for(MappingState currentState : states) {
			if(currentState.getSize() > max) {
				max = currentState.getSize();
				maximumStates.clear();
				maximumStates.add(currentState);
			}
			else if(currentState.getSize() == max) {
				maximumStates.add(currentState);
			}
		}
		
		List<MappingState> maximumStatesWithMinimumDifferences = new ArrayList<MappingState>();
		if(maximumStates.size() == 1) {
			maximumStatesWithMinimumDifferences.add(maximumStates.get(0));
		}
		else {
			int minimum = maximumStates.get(0).getDifferenceCount();
			maximumStatesWithMinimumDifferences.add(maximumStates.get(0));
			for(int i=1; i<maximumStates.size(); i++) {
				MappingState currentState = maximumStates.get(i);
				if(currentState.getDifferenceCount() < minimum) {
					minimum = currentState.getDifferenceCount();
					maximumStatesWithMinimumDifferences.clear();
					maximumStatesWithMinimumDifferences.add(currentState);
				}
				else if(currentState.getDifferenceCount() == minimum) {
					maximumStatesWithMinimumDifferences.add(currentState);
				}
			}
		}
		//in the case where we have more than one maximum states with minimum differences, return the state where the node mappings have closer Ids
		if(maximumStatesWithMinimumDifferences.size() == 1) {
			return maximumStatesWithMinimumDifferences.get(0);
		}
		else {
			int minimum = maximumStatesWithMinimumDifferences.get(0).getNodeMappingIdDiff();
			MappingState maximumStateWithMinimumDifferences = maximumStatesWithMinimumDifferences.get(0);
			for(int i=1; i<maximumStatesWithMinimumDifferences.size(); i++) {
				MappingState currentState = maximumStatesWithMinimumDifferences.get(i);
				if(currentState.getNodeMappingIdDiff() < minimum) {
					minimum = currentState.getNodeMappingIdDiff();
					maximumStateWithMinimumDifferences = currentState;
				}
			}
			return maximumStateWithMinimumDifferences;
		}
	}

	private Set<PDGNode> getNodesInRegion(PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInNextLevel) {
		Set<PDGNode> nodesInRegion = new TreeSet<PDGNode>();
		if(!(controlPredicate instanceof PDGMethodEntryNode))
			nodesInRegion.add(controlPredicate);
		Iterator<GraphEdge> edgeIterator = controlPredicate.getOutgoingDependenceIterator();
		while(edgeIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)edgeIterator.next();
			if(dependence instanceof PDGControlDependence) {
				PDGNode pdgNode = (PDGNode)dependence.getDst();
				if(!controlPredicateNodesInNextLevel.contains(pdgNode))
					nodesInRegion.add(pdgNode);
			}
		}
		return nodesInRegion;
	}

	private void processPDGNodes() {
		int maxLevel1 = controlDependenceTreePDG1.getMaxLevel();
		int level1 = maxLevel1;
		int maxLevel2 = controlDependenceTreePDG2.getMaxLevel();
		int level2 = maxLevel2;
		if(monitor != null)
			monitor.beginTask("Mapping Program Dependence Graphs", Math.min(maxLevel1, maxLevel2));
		MappingState finalState = null;
		while(level1 >= 0 && level2 >= 0) {
			Set<PDGNode> controlPredicateNodesG1 = controlDependenceTreePDG1.getControlPredicateNodesInLevel(level1);
			Set<PDGNode> controlPredicateNodesG2 = controlDependenceTreePDG2.getControlPredicateNodesInLevel(level2);
			Set<PDGNode> controlPredicateNodesInNextLevelG1 = new LinkedHashSet<PDGNode>();
			Set<PDGNode> controlPredicateNodesInNextLevelG2 = new LinkedHashSet<PDGNode>();
			if(level1 < maxLevel1) {
				controlPredicateNodesInNextLevelG1.addAll(controlDependenceTreePDG1.getControlPredicateNodesInLevel(level1+1));
			}
			if(level2 < maxLevel2) {
				controlPredicateNodesInNextLevelG2.addAll(controlDependenceTreePDG2.getControlPredicateNodesInLevel(level2+1));
			}
			for(PDGNode predicate1 : controlPredicateNodesG1) {
				Set<PDGNode> nodesG1 = getNodesInRegion(predicate1, controlPredicateNodesInNextLevelG1);
				MappingState.setRestrictedNodesG1(nodesG1);
				List<MappingState> currentStates = new ArrayList<MappingState>();
				for(PDGNode predicate2 : controlPredicateNodesG2) {
					Set<PDGNode> nodesG2 = getNodesInRegion(predicate2, controlPredicateNodesInNextLevelG2);
					MappingState.setRestrictedNodesG2(nodesG2);
					List<MappingState> maxStates = processPDGNodes(finalState, nodesG1, nodesG2);
					for(MappingState temp : maxStates) {
						if(!currentStates.contains(temp)) {
							currentStates.add(temp);
						}
					}
				}
				if(!currentStates.isEmpty()) {
					MappingState best = findMaximumStateWithMinimumDifferences(currentStates);
					List<PDGNodeMapping> nodeMappings = new ArrayList<PDGNodeMapping>(best.getNodeMappings());
					for(PDGNodeMapping mapping : nodeMappings) {
						if(mapping.getNodeG1().equals(predicate1)) {
							controlPredicateNodesG2.remove(mapping.getNodeG2());
							break;
						}
					}
					finalState = best;
				}
			}
			level1--;
			level2--;
			if(monitor != null)
				monitor.worked(1);
		}
		if(monitor != null)
			monitor.done();
		maximumStateWithMinimumDifferences = finalState;
	}
	
	private List<MappingState> processPDGNodes(MappingState parent, Set<PDGNode> nodesG1, Set<PDGNode> nodesG2) {
		List<MappingState> finalStates = new ArrayList<MappingState>();
		for(PDGNode node1 : nodesG1) {
			List<MappingState> currentStates = new ArrayList<MappingState>();
			for(PDGNode node2 : nodesG2) {
				ASTNodeMatcher astNodeMatcher = new ASTNodeMatcher(iCompilationUnit1, iCompilationUnit2);
				boolean match = node1.getASTStatement().subtreeMatch(astNodeMatcher, node2.getASTStatement());
				if(match && astNodeMatcher.isParameterizable()) {
					PDGNodeMapping mapping = new PDGNodeMapping(node1, node2, astNodeMatcher);
					if(finalStates.isEmpty()) {
						MappingState state = new MappingState(parent, mapping);
						state.traverse(mapping);
						List<MappingState> maxStates = state.getMaximumCommonSubGraphs();
						for(MappingState temp : maxStates) {
							if(!currentStates.contains(temp)) {
								currentStates.add(temp);
							}
						}
					}
					else {
						for(MappingState previousState : finalStates) {
							if(!previousState.containsAtLeastOneNodeInMappings(mapping) && previousState.mappedControlParents(node1, node2)) {
								MappingState state = new MappingState(previousState, mapping);
								previousState.addChild(state);
								state.traverse(mapping);
								List<MappingState> maxStates = state.getMaximumCommonSubGraphs();
								for(MappingState temp : maxStates) {
									if(!currentStates.contains(temp)) {
										currentStates.add(temp);
									}
								}
							}
						}
					}
				}
			}
			if(!currentStates.isEmpty())
				finalStates = getMaximumStates(currentStates);
		}
		return finalStates;
	}
	
	private List<MappingState> getMaximumStates(List<MappingState> currentStates) {
		int max = 0;
		List<MappingState> maximumStates = new ArrayList<MappingState>();
		for(MappingState currentState : currentStates) {
			if(currentState.getSize() > max) {
				max = currentState.getSize();
				maximumStates.clear();
				maximumStates.add(currentState);
			}
			else if(currentState.getSize() == max) {
				maximumStates.add(currentState);
			}
		}
		List<MappingState> maximumStatesWithMinimumDifferences = new ArrayList<MappingState>();
		if(maximumStates.size() == 1) {
			maximumStatesWithMinimumDifferences.add(maximumStates.get(0));
		}
		else {
			int minimum = maximumStates.get(0).getDifferenceCount();
			maximumStatesWithMinimumDifferences.add(maximumStates.get(0));
			for(int i=1; i<maximumStates.size(); i++) {
				MappingState currentState = maximumStates.get(i);
				if(currentState.getDifferenceCount() < minimum) {
					minimum = currentState.getDifferenceCount();
					maximumStatesWithMinimumDifferences.clear();
					maximumStatesWithMinimumDifferences.add(currentState);
				}
				else if(currentState.getDifferenceCount() == minimum) {
					maximumStatesWithMinimumDifferences.add(currentState);
				}
			}
		}
		return maximumStatesWithMinimumDifferences;
	}
}
